import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * MiGestureBridge —— 把「抬起唤醒 / 双击唤醒」桥接到小米 HyperOS 硬件接口。
 *
 * 背景：Flyme 的抬手/翻转开关依赖魅族私有传感器 65538/65539（本机不存在，开关被隐藏），
 *       双击则只会写不存在的 /sys/class/meizu/tp/gesture_control。
 * 本桥做两件事：
 *   1) 触摸 IC 双击模式：ITouchFeature.setModeValue(touchId=0, mode=14, value=1)
 *      （驱动 feature = Touch_Doubletap_Mode → Synaptics IC 进 DC_ENABLE_WAKEUP_GESTURE_MODE，
 *        双击后 IC 在 Xiaomi_Touch_Input_0 上报 KEY_WAKEUP(143)，系统自行亮屏）
 *   2) 抬手唤醒：订阅 xiaomi.sensor.pickup(33171036, Wakeup)，熄屏状态下收到「拿起」时
 *      调 PowerManager.wakeUp()
 *
 * 运行方式与 mifod 一致（见 migesture.rc）：
 *   service migesture /system/bin/app_process64 /system/bin MiGestureBridge
 *       class late_start
 *       user root
 *       seclabel u:r:su:s0
 *       setenv CLASSPATH /system/etc/migesture/migesture.jar
 *
 * 调试用参数：
 *   MiGestureBridge set14   —— 只下发双击模式后退出
 *   MiGestureBridge probe   —— 只打印传感器状态后退出
 */
public class MiGestureBridge {
    private static final String TAG = "MiGestureBridge";

    /* ---- vendor.xiaomi.hw.touchfeature.ITouchFeature (AIDL, transact 码由
       vendor.xiaomi.hw.touchfeature-V1-ndk.so 反汇编得到；9 = setModeValue) ---- */
    private static final String TF_DESCRIPTOR = "vendor.xiaomi.hw.touchfeature.ITouchFeature";
    private static final String TF_SERVICE = TF_DESCRIPTOR + "/default";
    private static final int TX_setModeValue = 9;
    private static final int TOUCH_ID = 0;
    private static final int MODE_DOUBLETAP = 14;   /* Touch_Doubletap_Mode */
    private static final int DOUBLETAP_ON = 1;

    /* ---- 小米私有传感器（sensors.qsh.so） ---- */
    private static final int TYPE_PICKUP = 33171036;  /* xiaomi.sensor.pickup，报文 1.0 = 拿起 */

    /* ---- Flyme 设置项（与 Settings/SystemUI 完全同一套 key） ---- */
    private static final String KEY_DOUBLETAP = "mz_quick_wakeup_double_click"; /* 双击亮屏开关 */
    private static final String KEY_RAISE = "raisewakeup_switch";               /* 抬手/翻转亮屏开关(1) */
    private static final String KEY_FLIP = "flipwakeup_switch";                 /* 抬手/翻转亮屏开关(2) */
    private static final int USER_CURRENT = -2;
    private static final String SETTINGS_FILE = "/data/system/users/0/settings_system.xml";
    private static final long SETTINGS_POLL_MS = 2000L;

    private static final long REASSERT_INTERVAL_MS = 10 * 60 * 1000L;

    private static Context appContext;
    private static SensorManager sensorManager;
    private static Sensor pickupSensor;
    private static SensorEventListener pickupListener;
    private static IBinder touchFeature;
    private static PowerManager powerManager;
    private static Handler handler;   /* 必须在 Looper.prepareMainLooper() 之后创建 */
    private static long lastSettingsStamp = -1L;

    private static volatile boolean doubleTapOn = true;
    private static volatile boolean raiseOn = true;
    private static volatile boolean pickupRegistered = false;

    private static final Runnable reassertDoubleTap = new Runnable() {
        @Override public void run() {
            ensurePower();   /* 开机早期可能没取到，顺便兜底 */
            if (doubleTapOn) {
                setDoubleTapMode(DOUBLETAP_ON);
            }
            handler.postDelayed(this, REASSERT_INTERVAL_MS);
        }
    };

    /* 开机早期启动时，system_server 的 power 服务可能还没注册：
       ContextImpl.getSystemService(POWER_SERVICE) 会把 ServiceNotFoundException 吞掉返回 null，
       而 sensorservice/touchfeature 是 init 起的 native 服务，早就可用 ——
       于是出现"pickup 事件收得到，却永远不会唤醒"。所以补取一次（顺便补读一次设置）。 */
    private static boolean ensurePower() {
        if (powerManager != null) {
            return true;
        }
        if (appContext == null) {
            return false;
        }
        try {
            Object pm = appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                powerManager = (PowerManager) pm;
                Log.i(TAG, "powerManager acquired (late)");
                readSettings("late-power");   /* 早期那次读设置可能只拿到默认值 */
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "acquire powerManager failed: " + t);
        }
        return false;
    }

    public static void main(String[] args) {
        Looper.prepareMainLooper();
        handler = new Handler(Looper.getMainLooper());
        String arg = (args != null && args.length > 0) ? args[0] : "";

        Log.i(TAG, "start, arg=" + arg);
        appContext = systemContext();
        if (appContext != null) {
            sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
            /* 等 power 服务就绪（最多 60s）：开机早期拿到的 null 会永久失效 */
            for (int i = 0; i < 30; i++) {
                if (ensurePower()) {
                    break;
                }
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ignored) {
                }
            }
            if (powerManager == null) {
                Log.w(TAG, "power service still not ready, will retry on first pickup event");
            }
        }

        if ("set14".equals(arg)) {
            bindTouchFeature();
            Log.i(TAG, "setModeValue(0,14,1) -> " + setDoubleTapMode(DOUBLETAP_ON));
            System.exit(0);
        }
        if ("probe".equals(arg)) {
            probeSensors(appContext);
            System.exit(0);
        }
        if ("settings".equals(arg)) {
            readSettings("settings-dump");
            System.exit(0);
        }
        if ("res".equals(arg)) {
            /* 复刻 MzUtils.supportRaiseFlipFeature()：进程内读 android 包的两个资源 + getDefaultSensor */
            try {
                android.content.res.Resources r = appContext.getResources();
                int flip = r.getInteger(0x010e01db);   /* android:integer/config_flipSensorType */
                int raise = r.getInteger(0x010e01f3);  /* android:integer/config_raiseSensorType */
                boolean flipOk = sensorManager != null && sensorManager.getDefaultSensor(flip, true) != null;
                boolean raiseOk = sensorManager != null && sensorManager.getDefaultSensor(raise, true) != null;
                Log.i(TAG, "RES flip=" + flip + " raise=" + raise
                        + " flipSensor=" + flipOk + " raiseSensor=" + raiseOk
                        + " => supportRaiseFlipFeature=" + (flipOk && raiseOk));
                /* 再走两条"应用视角"的取资源路径，判断 overlay 是否对进程可见 */
                try {
                    android.content.res.Resources r2 = appContext.getPackageManager()
                            .getResourcesForApplication("android");
                    Log.i(TAG, "RES(pm.getResourcesForApplication) flip=" + r2.getInteger(0x010e01db)
                            + " raise=" + r2.getInteger(0x010e01f3));
                } catch (Throwable t) {
                    Log.w(TAG, "RES(pm) failed: " + t);
                }
                try {
                    Log.i(TAG, "RES(Resources.getSystem) flip="
                            + android.content.res.Resources.getSystem().getInteger(0x010e01db)
                            + " raise=" + android.content.res.Resources.getSystem().getInteger(0x010e01f3));
                } catch (Throwable t) {
                    Log.w(TAG, "RES(system) failed: " + t);
                }
            } catch (Throwable t) {
                Log.e(TAG, "res probe failed: " + t);
            }
            System.exit(0);
        }

        /* 1) 双击亮屏：IC 双击模式（重启后驱动状态会丢，所以开机 + 每次熄屏 + 每 10 分钟重申）
              是否启用完全跟随设置项 mz_quick_wakeup_double_click */
        bindTouchFeature();
        readSettings("boot");
        applyDoubleTap();
        handler.postDelayed(reassertDoubleTap, REASSERT_INTERVAL_MS);

        /* 2) 抬手亮屏：pickup 传感器（跟随 raisewakeup_switch / flipwakeup_switch） */
        pickupSensor = findPickupSensor();
        if (pickupSensor != null) {
            Log.i(TAG, "pickup sensor = " + pickupSensor.getName()
                    + " (wakeup=" + pickupSensor.isWakeUpSensor() + ")");
        }
        applyRaise();

        /* 3) 设置变化即时生效（用户在设置里拨开关，桥立刻响应） */
        lastSettingsStamp = settingsStamp();
        handler.postDelayed(pollSettings, SETTINGS_POLL_MS);

        if (appContext != null) {
            try {
                /* 熄屏时重申一次双击模式（驱动可能在熄屏流程里重置） */
                appContext.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) {
                        if (doubleTapOn) {
                            Log.i(TAG, "screen off -> re-assert doubletap mode");
                            setDoubleTapMode(DOUBLETAP_ON);
                        }
                    }
                }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
                Log.i(TAG, "screen-off receiver registered");
            } catch (Throwable t) {
                Log.w(TAG, "screen-off receiver registration failed: " + t);
            }
        }

        Log.i(TAG, "ready (doubleTap=" + doubleTapOn + " raise=" + raiseOn + ")");
        Looper.loop();
    }

    /* ================= 设置项 ================= */

    private static void readSettings(String why) {
        try {
            int dt = cliGetInt(KEY_DOUBLETAP, 1);
            int rs = cliGetInt(KEY_RAISE, 1);
            int fl = cliGetInt(KEY_FLIP, 1);
            doubleTapOn = (dt != 0);
            raiseOn = (rs == 1) || (fl == 1);
            Log.i(TAG, "[" + why + "] " + KEY_DOUBLETAP + "=" + dt
                    + " " + KEY_RAISE + "=" + rs + " " + KEY_FLIP + "=" + fl
                    + " -> doubleTap=" + doubleTapOn + " raise=" + raiseOn);
        } catch (Throwable t) {
            Log.w(TAG, "readSettings failed: " + t);
        }
    }

    /* app_process 不是注册过的 app，ContentResolver 访问 settings provider 会被 AMS 拒
       ("Unable to find app for caller")，所以走 settings CLI。 */
    private static int cliGetInt(String key, int def) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] { "/system/bin/settings", "get", "system", key });
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            if (line != null) {
                line = line.trim();
                if (line.length() > 0 && !"null".equals(line)) {
                    return Integer.parseInt(line);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "settings get " + key + " failed: " + t);
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Throwable ignored) { }
            }
        }
        return def;
    }

    /* 设置变化检测：settings_system.xml 是 ABX 二进制，直接比对 时间戳+长度 即可 */
    private static long settingsStamp() {
        try {
            File f = new File(SETTINGS_FILE);
            return f.lastModified() * 1000L + f.length();
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static final Runnable pollSettings = new Runnable() {
        @Override public void run() {
            long stamp = settingsStamp();
            if (stamp != lastSettingsStamp) {
                lastSettingsStamp = stamp;
                readSettings("changed");
                applyDoubleTap();
                applyRaise();
            }
            handler.postDelayed(this, SETTINGS_POLL_MS);
        }
    };

    private static void applyDoubleTap() {
        int want = doubleTapOn ? DOUBLETAP_ON : 0;
        Log.i(TAG, "apply doubletap mode=" + want);
        setDoubleTapMode(want);
    }

    private static void applyRaise() {
        if (sensorManager == null || pickupSensor == null) {
            return;
        }
        if (raiseOn && !pickupRegistered) {
            pickupListener = new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent e) {
                    float v = (e.values != null && e.values.length > 0) ? e.values[0] : -1f;
                    boolean hasPower = ensurePower();
                    boolean screenOn = hasPower && powerManager.isInteractive();
                    Log.i(TAG, "pickup event value=" + v + " screenOn=" + screenOn + " power=" + hasPower);
                    /* 1.0 = 拿起；只在熄屏时唤醒，避免放下手机也亮屏 */
                    if (v == 1.0f && hasPower && !screenOn) {
                        wakeUp();
                    }
                }
                @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
            };
            pickupRegistered = sensorManager.registerListener(
                    pickupListener, pickupSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "register pickup -> " + pickupRegistered);
        } else if (!raiseOn && pickupRegistered) {
            sensorManager.unregisterListener(pickupListener);
            pickupRegistered = false;
            Log.i(TAG, "unregister pickup (raise switched off)");
        }
    }

    private static void wakeUp() {
        long now = SystemClock.uptimeMillis();
        /* wakeUp() 在 android.jar 里是 @hide(@UnsupportedAppUsage)，只能反射调用：
           优先 wakeUp(long,String)，其次 wakeUp(long)，最后退到 input keyevent 224。 */
        if (call("wakeUp", new Class<?>[] { long.class, String.class },
                new Object[] { now, "MiGestureBridge:pickup" })) {
            Log.i(TAG, "wakeUp(time,reason) ok");
            return;
        }
        if (call("wakeUp", new Class<?>[] { long.class }, new Object[] { now })) {
            Log.i(TAG, "wakeUp(time) ok");
            return;
        }
        try {
            Runtime.getRuntime().exec(new String[] { "/system/bin/input", "keyevent", "224" });
            Log.i(TAG, "fallback input keyevent 224 issued");
        } catch (Throwable t) {
            Log.e(TAG, "fallback failed: " + t);
        }
    }

    private static boolean call(String name, Class<?>[] sig, Object[] arg) {
        try {
            java.lang.reflect.Method m = PowerManager.class.getMethod(name, sig);
            m.invoke(powerManager, arg);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, name + "(" + sig.length + " args) unavailable: " + t);
            return false;
        }
    }

    /* ================= 双击 ================= */

    private static void bindTouchFeature() {
        for (int i = 0; i < 60; i++) {
            try {
                Class<?> sm = Class.forName("android.os.ServiceManager");
                Object b = sm.getMethod("getService", String.class).invoke(null, TF_SERVICE);
                if (b instanceof IBinder) {
                    touchFeature = (IBinder) b;
                    Log.i(TAG, "touchfeature service bound");
                    return;
                }
            } catch (Throwable t) {
                /* service 还没起来，重试 */
            }
            sleep(1000);
        }
        Log.e(TAG, "touchfeature service not available");
    }

    private static int setDoubleTapMode(int value) {
        if (touchFeature == null || !touchFeature.isBinderAlive()) {
            touchFeature = null;
            bindTouchFeature();
        }
        if (touchFeature == null) {
            return -1;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TF_DESCRIPTOR);
            data.writeInt(TOUCH_ID);
            data.writeInt(MODE_DOUBLETAP);
            data.writeInt(value);
            if (!touchFeature.transact(TX_setModeValue, data, reply, 0)) {
                return -1;
            }
            reply.readException();
            int ret = reply.readInt();
            Log.i(TAG, "setModeValue(0," + MODE_DOUBLETAP + "," + value + ") -> " + ret);
            return ret;
        } catch (Throwable t) {
            Log.w(TAG, "setModeValue failed: " + t);
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /* ================= 工具 ================= */

    private static Sensor findPickupSensor() {
        if (sensorManager == null) {
            return null;
        }
        Sensor s = sensorManager.getDefaultSensor(TYPE_PICKUP, true);
        if (s == null) {
            s = sensorManager.getDefaultSensor(TYPE_PICKUP, false);
        }
        if (s == null) {
            Log.e(TAG, "pickup sensor(" + TYPE_PICKUP + ") not found");
        }
        return s;
    }

    private static Context systemContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("systemMain").invoke(null);
            return (Context) at.getMethod("getSystemContext").invoke(thread);
        } catch (Throwable t) {
            Log.e(TAG, "systemContext failed: " + t);
            return null;
        }
    }

    private static void probeSensors(Context ctx) {
        if (ctx == null) {
            return;
        }
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        int[] types = { TYPE_PICKUP, 33171037, 33171038, 33171045, 65538, 65539 };
        for (int type : types) {
            Sensor w = sm.getDefaultSensor(type, true);
            Sensor n = sm.getDefaultSensor(type, false);
            Log.i(TAG, "type " + type + ": wakeup=" + (w == null ? "null" : w.getName())
                    + " nonwake=" + (n == null ? "null" : n.getName()));
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
