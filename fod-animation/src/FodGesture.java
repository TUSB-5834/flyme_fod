package fodgesture;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FodGesture —— 把魅族手势服务的 FOD 按下/抬起事件补上（不依赖任何 hook / 插件）。
 *
 * 根因（详见 安装说明.md）：
 *   动画唯一入口：gesture_manager 派发 161 -> com.flyme.keyguard.MzGestureController.handleGesture()
 *   -> KeyguardUpdateMonitor.showFingerprintDownAnim() -> ... -> FlymeFpAuthAnimView.startDownAnimation()
 *   小米屏不产生魅族的 keyCode 413，所以这条链永远不触发。
 *   本工具直接调 IGestureManager.triggerGesture()（AIDL tx=3）把 413 送进去。
 *
 * 模式：
 *   probe                 只查服务在不在
 *   down | up             注入 413 按下 / 抬起
 *   pulse [ms] [ts]       按下、保持、抬起（一次性验证）
 *   raw [sec]             诊断：打印每个 input 设备上报的 ABS/KEY 取值范围（找触摸坐标空间）
 *   dump [sec]            诊断：实时打印触摸坐标 + 是否落在 FOD 区域
 *   watch [x1,y1,x2,y2] [ts]   常驻：手指落在 FOD 区域 -> 161，抬起 -> 162
 *
 * 日志 tag：NyakoFodGesture
 */
public final class FodGesture {

    static final String TAG = "NyakoFodGesture";

    static final String SERVICE_NAME = "gesture_manager";
    static final String DESCRIPTOR   = "android.view.IGestureManager";
    static final int    TX_TRIGGER   = 3;
    static final int    KEY_FOD      = 413;

    private static final int EV_SYN = 0x00;
    private static final int EV_KEY = 0x01;
    private static final int EV_ABS = 0x03;
    private static final int ABS_MT_SLOT        = 0x2f;
    private static final int ABS_MT_POSITION_X  = 0x35;
    private static final int ABS_MT_POSITION_Y  = 0x36;
    private static final int ABS_MT_TRACKING_ID = 0x39;
    private static final int BTN_TOUCH          = 0x14a;

    private static final int MAX_SLOTS  = 16;
    private static final int EVENT_SIZE = 24;

    /** 默认 FOD 区域：ROM 里 system_server 的 sensorBounds Rect(518,1954 - 682,2118) */
    static final int[] DEFAULT_RECT = {518, 1954, 682, 2118};

    /** TouchReader 的运行模式 */
    private static final int MODE_WATCH = 0;
    private static final int MODE_DUMP  = 1;
    private static final int MODE_RAW   = 2;

    private static final Object INJECT_LOCK = new Object();
    private static boolean sInjected = false;
    private static IBinder sService;
    private static int[] sRect = DEFAULT_RECT;

    private FodGesture() {
    }

    private static void log(String msg) {
        System.out.println("[fod] " + msg);
        try {
            Log.i(TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    /* ------------------------------------------------------------------ */
    /* binder                                                             */
    /* ------------------------------------------------------------------ */

    private static IBinder binder() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        return (IBinder) sm.getMethod("getService", String.class).invoke(null, SERVICE_NAME);
    }

    /**
     * 复刻 AIDL Proxy.triggerGesture() 的 parcel：
     *   writeInterfaceToken + writeTypedObject(KeyEvent) + writeTypedObject(MotionEvent)
     * MotionEvent 必须非空（WindowMode$GestureCallback 是 Kotlin，非空参数，传 null 会抛 NPE 并中断派发）。
     */
    private static void trigger(IBinder svc, int action, boolean ts) throws Exception {
        long now = android.os.SystemClock.uptimeMillis();
        KeyEvent ev = ts
                ? new KeyEvent(now, now, action, KEY_FOD, 0, 0, -1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN)
                : new KeyEvent(action, KEY_FOD);
        int x = (sRect[0] + sRect[2]) / 2;
        int y = (sRect[1] + sRect[3]) / 2;
        int meAction = (action == KeyEvent.ACTION_DOWN) ? MotionEvent.ACTION_DOWN : MotionEvent.ACTION_UP;
        MotionEvent me = MotionEvent.obtain(now, now, meAction, x, y, 0);

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(1);
            ev.writeToParcel(data, 0);
            data.writeInt(1);
            me.writeToParcel(data, 0);
            svc.transact(TX_TRIGGER, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
            me.recycle();
        }
    }

    /* ------------------------------------------------------------------ */
    /* input 设备                                                          */
    /* ------------------------------------------------------------------ */

    private static List<String> eventNodes() {
        List<String> nodes = new ArrayList<>();
        File[] fs = new File("/dev/input").listFiles();
        if (fs == null) return nodes;
        for (File f : fs) {
            if (f.getName().startsWith("event")) nodes.add(f.getAbsolutePath());
        }
        java.util.Collections.sort(nodes);
        return nodes;
    }

    private static Map<String, String> deviceNames() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            FileInputStream in = new FileInputStream("/proc/bus/input/devices");
            byte[] buf = new byte[131072];
            int n = in.read(buf);
            in.close();
            String cur = null;
            for (String line : new String(buf, 0, n, "UTF-8").split("\n")) {
                if (line.startsWith("N: Name=")) {
                    cur = line.substring(8).trim().replace("\"", "");
                } else if (line.startsWith("H: Handlers=") && cur != null) {
                    for (String tok : line.substring(12).trim().split("\\s+")) {
                        if (tok.startsWith("event")) map.put("/dev/input/" + tok, cur);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private static boolean inFod(float x, float y) {
        int[] r = sRect;
        return x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3];
    }

    private static int[] parseRect(String s) {
        if (s == null) return DEFAULT_RECT;
        String[] p = s.split(",");
        if (p.length != 4) return DEFAULT_RECT;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                             Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())};
        } catch (NumberFormatException e) {
            return DEFAULT_RECT;
        }
    }

    private static String codeName(int type, int code) {
        if (type == EV_ABS) {
            switch (code) {
                case ABS_MT_SLOT: return "MT_SLOT";
                case ABS_MT_POSITION_X: return "MT_X";
                case ABS_MT_POSITION_Y: return "MT_Y";
                case ABS_MT_TRACKING_ID: return "MT_TRACKING_ID";
                case 0x30: return "MT_TOUCH_MAJOR";
                case 0x31: return "MT_TOUCH_MINOR";
                case 0x3a: return "MT_PRESSURE";
                default: return "ABS_" + code;
            }
        }
        if (type == EV_KEY) {
            if (code == BTN_TOUCH) return "BTN_TOUCH";
            return "KEY_" + code;
        }
        return type + "_" + code;
    }

    /* ------------------------------------------------------------------ */
    /* 触摸线程                                                            */
    /* ------------------------------------------------------------------ */

    private static final class TouchReader extends Thread {
        private final String path;
        private final int mode;
        private final long endMs;
        private final boolean ts;
        /** raw 模式：key=(type<<16)|code -> {min,max,count} */
        private final Map<Integer, long[]> ranges = new LinkedHashMap<>();

        TouchReader(String path, int mode, long endMs, boolean ts) {
            this.path = path;
            this.mode = mode;
            this.endMs = endMs;
            this.ts = ts;
            setName("fod-" + new File(path).getName());
            setDaemon(true);
        }

        @Override
        public void run() {
            InputStream in = null;
            try {
                in = new FileInputStream(path);
                byte[] rec = new byte[EVENT_SIZE];
                float[] x = new float[MAX_SLOTS];
                float[] y = new float[MAX_SLOTS];
                boolean[] active = new boolean[MAX_SLOTS];
                int slot = 0;
                boolean btnDown = false;
                boolean sawMt = false;
                long lastLog = 0L;

                while (true) {
                    if (mode != MODE_WATCH && System.currentTimeMillis() > endMs) break;
                    if (readFully(in, rec) < 0) {
                        if (mode != MODE_WATCH) break;
                        Thread.sleep(500);
                        continue;
                    }
                    ByteBuffer b = ByteBuffer.wrap(rec).order(ByteOrder.LITTLE_ENDIAN);
                    b.getLong();
                    b.getLong();
                    int type = b.getShort() & 0xffff;
                    int code = b.getShort() & 0xffff;
                    int value = b.getInt();

                    if (mode == MODE_RAW && (type == EV_ABS || type == EV_KEY)) {
                        int key = (type << 16) | code;
                        long[] r = ranges.get(key);
                        if (r == null) ranges.put(key, new long[]{value, value, 1});
                        else {
                            if (value < r[0]) r[0] = value;
                            if (value > r[1]) r[1] = value;
                            r[2]++;
                        }
                    }

                    if (type == EV_ABS) {
                        if (code == ABS_MT_SLOT) {
                            if (value >= 0 && value < MAX_SLOTS) slot = value;
                        } else if (code == ABS_MT_POSITION_X) {
                            if (!sawMt) {
                                sawMt = true;
                                log(path + " 上报多点触控坐标");
                            }
                            if (slot < MAX_SLOTS) {
                                x[slot] = value;
                                active[slot] = true;      // 协议 A 也可能没有 TRACKING_ID
                            }
                        } else if (code == ABS_MT_POSITION_Y) {
                            if (slot < MAX_SLOTS) {
                                y[slot] = value;
                                active[slot] = true;
                            }
                        } else if (code == ABS_MT_TRACKING_ID) {
                            if (slot < MAX_SLOTS) active[slot] = value >= 0;
                        }
                    } else if (type == EV_KEY && code == BTN_TOUCH) {
                        btnDown = value != 0;
                        if (value == 0) {
                            for (int i = 0; i < MAX_SLOTS; i++) active[i] = false;
                        }
                    } else if (type == EV_SYN && code == 0 && sawMt) {
                        boolean present = btnDown;
                        for (int i = 0; i < MAX_SLOTS; i++) {
                            if (active[i]) { present = true; break; }
                        }
                        if (mode == MODE_WATCH) {
                            boolean onFod = false;
                            for (int i = 0; i < MAX_SLOTS; i++) {
                                if (active[i] && inFod(x[i], y[i])) { onFod = true; break; }
                            }
                            // 每次触摸都打一行（限流 250ms），坐标一目了然
                            long nowMs = System.currentTimeMillis();
                            if (present && nowMs - lastLog > 250) {
                                lastLog = nowMs;
                                for (int i = 0; i < MAX_SLOTS; i++) {
                                    if (active[i]) {
                                        log("触摸 " + path + " x=" + (int) x[i] + " y=" + (int) y[i]
                                                + (inFod(x[i], y[i]) ? "  ✅在FOD区域内" : "  ✗不在FOD区域"));
                                        break;
                                    }
                                }
                            }
                            handleFod(onFod, ts);
                        } else if (mode == MODE_DUMP) {
                            long nowMs = System.currentTimeMillis();
                            if (present && nowMs - lastLog > 250) {
                                lastLog = nowMs;
                                for (int i = 0; i < MAX_SLOTS; i++) {
                                    if (active[i]) {
                                        log(path + " x=" + (int) x[i] + " y=" + (int) y[i]
                                                + (inFod(x[i], y[i]) ? "  ✅在FOD区域内" : "  ✗不在FOD区域"));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                if (mode == MODE_WATCH) log(path + " 读取结束: " + t);
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) { }
            }

            if (mode == MODE_RAW && !ranges.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(path).append(" 上报：");
                for (Map.Entry<Integer, long[]> e : ranges.entrySet()) {
                    int type = e.getKey() >> 16, code = e.getKey() & 0xffff;
                    long[] r = e.getValue();
                    sb.append(codeName(type, code)).append('(').append(type).append('/').append(code).append(')')
                      .append("=[").append(r[0]).append("..").append(r[1]).append("] x").append(r[2]).append("  ");
                }
                log(sb.toString());
            }
        }
    }

    /** 延迟解析 gesture_manager：开机极早期它可能还没注册，等最多 tries 秒。 */
    private static IBinder waitBinder(int tries) {
        for (int i = 0; i < tries; i++) {
            try {
                IBinder b = binder();
                if (b != null) {
                    if (i > 0) log("gesture_manager 已就绪（等待 " + i + "s）");
                    return b;
                }
            } catch (Throwable ignored) {
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException ignored) {
            }
        }
        return null;
    }

    private static void handleFod(boolean onFod, boolean ts) {
        synchronized (INJECT_LOCK) {
            try {
                if (sService == null) sService = waitBinder(10);
                if (sService == null) {
                    log("注入跳过: gesture_manager 尚未就绪");
                    return;
                }
                if (onFod && !sInjected) {
                    sInjected = true;
                    trigger(sService, KeyEvent.ACTION_DOWN, ts);
                    log("FOD 按下 -> 161 (startDownAnimation)");
                } else if (!onFod && sInjected) {
                    sInjected = false;
                    trigger(sService, KeyEvent.ACTION_UP, ts);
                    log("FOD 抬起 -> 162 (stopDownAnimation)");
                }
            } catch (Throwable t) {
                log("注入失败: " + t);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* logcat 触发（最可靠）：指纹 HAL 自己打的 FOD 触摸日志               */
    /*   I QFP: onPointerDown: entry pointerId=0 x=565 y=2027 ...          */
    /*   I QFP: onPointerUp: entry pointerId=0                             */
    /* 这条信号不依赖触摸屏是否上报 FOD 区域，按下/抬起成对出现。          */
    /* ------------------------------------------------------------------ */

    private static void logwatch(boolean ts) throws Exception {
        // 不在这里取 binder：开机极早期 gesture_manager 可能还没注册。
        // 第一次触摸时再取（带重试），这样进程可以在开机后立刻启动，不留空窗。
        log("logwatch: 监听 logcat QFP onPointerDown/onPointerUp -> 注入 161/162");
        Process p = Runtime.getRuntime().exec(
                new String[]{"logcat", "-v", "brief", "-s", "QFP:I", "-T", "1"});
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.contains("onPointerDown")) {
                log("HAL onPointerDown -> 161");
                handleFod(true, ts);
            } else if (line.contains("onPointerUp")) {
                log("HAL onPointerUp -> 162");
                handleFod(false, ts);
            }
        }
        log("logcat 结束，退出");
    }

    /* ------------------------------------------------------------------ */
    /* main                                                               */
    /* ------------------------------------------------------------------ */

    public static void main(String[] args) {
        String mode = (args != null && args.length > 0) ? args[0] : "pulse";
        String arg1 = (args != null && args.length > 1) ? args[1] : null;
        String arg2 = (args != null && args.length > 2) ? args[2] : null;
        boolean ts = "ts".equals(arg2) || "ts".equals(arg1);
        long num = 0L;
        if (arg1 != null) {
            try { num = Long.parseLong(arg1); } catch (NumberFormatException ignored) { }
        }

        log("uid=" + android.os.Process.myUid() + " service=" + SERVICE_NAME
                + " tx=" + TX_TRIGGER + " keyCode=" + KEY_FOD + " mode=" + mode + (ts ? " (ts)" : ""));

        if ("raw".equals(mode) || "dump".equals(mode)) {
            int secs = num > 0 ? (int) num : 15;
            diagnostic(("raw".equals(mode) ? MODE_RAW : MODE_DUMP), secs);
            return;
        }

        IBinder svc;
        try {
            svc = binder();
        } catch (Throwable t) {
            log("FAIL 取不到 binder: " + t);
            return;
        }
        log("binder=" + svc);
        if (svc == null) {
            log("FAIL: " + SERVICE_NAME + " 服务不存在");
            System.exit(2);
            return;
        }
        if ("probe".equals(mode)) {
            log("OK: 服务存在，未注入任何事件");
            return;
        }

        try {
            if ("down".equals(mode)) {
                trigger(svc, KeyEvent.ACTION_DOWN, ts);
                log("已发 DOWN -> 161");
            } else if ("up".equals(mode)) {
                trigger(svc, KeyEvent.ACTION_UP, ts);
                log("已发 UP -> 162");
            } else if ("pulse".equals(mode)) {
                long hold = num > 0 ? num : 2500L;
                try {
                    trigger(svc, KeyEvent.ACTION_DOWN, ts);
                    log("已发 DOWN -> 161，保持 " + hold + "ms");
                } catch (Throwable t) {
                    log("DOWN 调用返回异常（继续发抬起）: " + t);
                }
                Thread.sleep(hold);
                try {
                    trigger(svc, KeyEvent.ACTION_UP, ts);
                    log("已发 UP -> 162");
                } catch (Throwable t) {
                    log("UP 调用返回异常: " + t);
                }
            } else if ("watch".equals(mode)) {
                watch(parseRect(arg1), ts);
            } else if ("logwatch".equals(mode)) {
                logwatch(ts);
            } else {
                log("unknown mode: " + mode);
            }
        } catch (Throwable t) {
            log("FAIL: " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void diagnostic(int mode, int seconds) {
        List<String> nodes = eventNodes();
        Map<String, String> names = deviceNames();
        for (Map.Entry<String, String> e : names.entrySet()) {
            log("设备 " + e.getKey() + " = " + e.getValue());
        }
        log((mode == MODE_RAW ? "raw" : "dump") + " " + nodes.size() + " 个设备 " + seconds
                + "s —— 请反复把手指按在 FOD 指纹区域，也可以按屏幕别处做对比");
        long end = System.currentTimeMillis() + seconds * 1000L;
        for (String n : nodes) {
            new TouchReader(n, mode, end, false).start();
        }
        try {
            Thread.sleep(seconds * 1000L + 800L);
        } catch (InterruptedException ignored) {
        }
        log("诊断结束");
    }

    private static void watch(int[] rect, boolean ts) throws Exception {
        sRect = rect;
        sService = binder();
        List<String> nodes = eventNodes();
        log("watch " + nodes.size() + " 个设备, FOD 区域=[" + rect[0] + "," + rect[1]
                + "," + rect[2] + "," + rect[3] + "]");
        for (String n : nodes) {
            new TouchReader(n, MODE_WATCH, 0L, ts).start();
        }
        while (true) {
            Thread.sleep(60000);
            log("alive, injected=" + sInjected);
        }
    }

    private static int readFully(InputStream in, byte[] buf) {
        int off = 0;
        try {
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) return -1;
                off += r;
            }
            return off;
        } catch (Throwable t) {
            return -1;
        }
    }
}
