/*
 * MiFod Bridge Service - 超声波指纹桥接服务
 * 
 * 用于 ColorOS 移植到小米设备时桥接超声波指纹服务
 * 
 * 核心原理：通过 AIDL 接口通知 HAL 层
 * - ITouchFeature.setModeValue(touchId, mode, value) 通知触摸检测
 * - IXiaomiFingerprint.extCmd(cmd, val) 通知指纹扩展 FOD 模式
 */

import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import java.lang.Process;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MiFodBridge {

    // ==================== 隐藏 API 反射 ====================

    private static IBinder getService(String name) {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = smClass.getMethod("getService", String.class);
            return (IBinder) getServiceMethod.invoke(null, name);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== AIDL 接口常量 ====================

    // TouchFeature AIDL
    private static final String TOUCH_FEATURE_DESCRIPTOR =
            "vendor.xiaomi.hw.touchfeature.ITouchFeature";
    private static final String TOUCH_FEATURE_SERVICE =
            "vendor.xiaomi.hw.touchfeature.ITouchFeature/default";
    private static final int TX_setModeValue = 9;

    // XiaomiFingerprintExtension AIDL
    private static final String FP_EXT_DESCRIPTOR =
            "vendor.xiaomi.hardware.fingerprintextension.IXiaomiFingerprint";
    private static final String FP_EXT_SERVICE =
            "vendor.xiaomi.hardware.fingerprintextension.IXiaomiFingerprint/default";
    private static final int TX_extCmd = 1;

    // 系统服务描述符
    private static final String FP_SERVICE_DESCRIPTOR =
            "android.hardware.fingerprint.IFingerprintService";
    private static final String TRUST_SERVICE_DESCRIPTOR =
            "android.app.trust.ITrustManager";
    private static final String POWER_SERVICE_DESCRIPTOR =
            "android.os.IPowerManager";
    private static final String BIO_STATE_DESCRIPTOR =
            "android.hardware.biometrics.IBiometricStateListener";

    private static final int TX_REGISTER_BIOMETRIC_STATE_LISTENER = 0x29;
    private static final int TX_TRUST_IS_DEVICE_LOCKED = 0x0A;
    private static final int TX_POWER_IS_INTERACTIVE = 0x17;

    // ==================== setModeValue 参数 ====================

    private static final int TOUCH_ID = 0;
    private static final int MODE_TOUCH_MONITOR = 10;
    private static final int VALUE_OFF = 0;
    private static final int VALUE_AUTH = 1;
    private static final int VALUE_ENROLL = 2;

    // ==================== extCmd 参数 ====================

    // cmd=4: FOD 模式通知
    private static final int EXTCMD_FOD_MODE = 4;
    private static final int FOD_MODE_ENROLL = 1;
    private static final int FOD_MODE_ENROLL_DONE = 2;
    private static final int FOD_MODE_AUTH = 3;
    private static final int FOD_MODE_OFF = 4;

    // 注：cmd=7 低亮度 FOD 控制为光学指纹专属（点亮屏幕辅助采图），
    // 超声波指纹无需屏幕补光，已在超声波版本中剔除。

    // ==================== 生物识别状态 ====================

    private static final int BIO_STATE_IDLE = 0;
    private static final int BIO_STATE_ENROLLING = 1;
    private static final int BIO_STATE_KEYGUARD_AUTH = 2;
    private static final int BIO_STATE_BP_AUTH = 3;
    private static final int BIO_STATE_AUTH_OTHER = 4;

    // ==================== 内部状态 ====================

    private static final int STATE_IDLE = 0;
    private static final int STATE_AUTH = 1;
    private static final int STATE_ENROLL = 2;

    // 认证来源
    private static final int SOURCE_NONE = 0;
    private static final int SOURCE_KEYGUARD = 1;
    private static final int SOURCE_APP = 2;

    // ==================== 环境变量 ====================

    private static final String ENV_ALLOW_BP_AUTH = "ALLOW_BP_AUTH";
    private static final String ENV_ALLOW_AUTH_OTHER = "ALLOW_AUTH_OTHER";

    // ==================== 日志 ====================

    private static final File LOG_FILE = new File("/data/vendor/mifod/mifod.log");
    private static final File STATE_DIR = new File("/data/vendor/mifod");

    // ==================== 状态变量 ====================

    private IBinder touchFeatureService;
    private IBinder fpExtService;

    private int currentState = STATE_IDLE;
    private int authSource = SOURCE_NONE;
    private boolean debug = true;

    // ==================== 主入口 ====================

    public static void main(String[] args) {
        MiFodBridge bridge = new MiFodBridge();
        if (args.length > 0) {
            bridge.runCommand(args[0]);
        } else {
            bridge.runDaemon();
        }
    }

    // ==================== 守护进程 ====================

    private void runDaemon() {
        prepareStateDir();

        log("mifod daemon start (ultrasonic), ALLOW_BP_AUTH=" + envInt(ENV_ALLOW_BP_AUTH, 1)
                + ", ALLOW_AUTH_OTHER=" + envInt(ENV_ALLOW_AUTH_OTHER, 0));

        waitForServices();
        startKeyguardGateThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("mifod shutdown");
            fodOff();
        }, "MiFodShutdown"));

        while (true) {
            try {
                monitorBiometricService();
            } catch (Exception e) {
                log("monitor failed: " + e.getMessage());
                sleep(1000);
            }
        }
    }

    private void runCommand(String cmd) {
        try {
            bindServices();
        } catch (Exception e) {
            log("bind services failed: " + e.getMessage());
            return;
        }

        switch (cmd) {
            case "auth-on":    authOn(SOURCE_KEYGUARD); break;
            case "enroll-on":  enrollOn(); break;
            case "enroll-done": enrollDone(); break;
            case "off":        fodOff(); break;
            default:
                System.out.println("usage: MiFodBridge [auth-on|enroll-on|enroll-done|off]");
                break;
        }
    }

    // ==================== 服务绑定 ====================

    private void waitForServices() {
        while (true) {
            try {
                bindServices();
                log("services bound");
                return;
            } catch (Exception e) {
                log("services not ready: " + e.getMessage());
                sleep(1000);
            }
        }
    }

    private void bindServices() throws Exception {
        if (!ensureTouchFeatureService()) {
            throw new IllegalStateException(TOUCH_FEATURE_SERVICE + " not found");
        }

        refreshFingerprintExtensionService();
        if (fpExtService == null) {
            log("FingerprintExtension not available, continuing without extCmd");
        } else {
            log("FingerprintExtension bound");
        }
    }

    private boolean ensureTouchFeatureService() {
        if (touchFeatureService == null || !touchFeatureService.isBinderAlive()) {
            touchFeatureService = getService(TOUCH_FEATURE_SERVICE);
        }
        return touchFeatureService != null;
    }

    private void refreshFingerprintExtensionService() {
        if (fpExtService == null || !fpExtService.isBinderAlive()) {
            fpExtService = getService(FP_EXT_SERVICE);
        }
    }

    // ==================== 核心方法 ====================

    /**
     * 开启认证模式
     */
    private void authOn(int source) {
        currentState = STATE_AUTH;
        authSource = source;
        log((source == SOURCE_KEYGUARD ? "keyguard" : "app") + " auth-on");

        extCmd(EXTCMD_FOD_MODE, FOD_MODE_AUTH);

        int ret = setModeValue(TOUCH_ID, MODE_TOUCH_MONITOR, VALUE_AUTH);
        log("setModeValue(auth) = " + ret);
    }

    private void enrollOn() {
        currentState = STATE_ENROLL;
        authSource = SOURCE_NONE;
        log("ultrasonic enroll-on");

        extCmd(EXTCMD_FOD_MODE, FOD_MODE_ENROLL);

        int ret = setModeValue(TOUCH_ID, MODE_TOUCH_MONITOR, VALUE_ENROLL);
        log("setModeValue(enroll) = " + ret);
    }

    /**
     * 录入完成
     * extCmd(4,2) + setModeValue(0,10,0)
     */
    private void enrollDone() {
        currentState = STATE_IDLE;
        log("enroll-done");

        extCmd(EXTCMD_FOD_MODE, FOD_MODE_ENROLL_DONE);

        int ret = setModeValue(TOUCH_ID, MODE_TOUCH_MONITOR, VALUE_OFF);
        log("setModeValue(off) = " + ret);
    }

    private void fodOff() {
        log("fod-off from " + stateName(currentState));
        currentState = STATE_IDLE;
        authSource = SOURCE_NONE;

        extCmd(EXTCMD_FOD_MODE, FOD_MODE_OFF);

        int ret = setModeValue(TOUCH_ID, MODE_TOUCH_MONITOR, VALUE_OFF);
        log("setModeValue(off) = " + ret);
    }

    // ==================== AIDL 调用 ====================

    /**
     * ITouchFeature.setModeValue(touchId, mode, value)
     */
    private int setModeValue(int touchId, int mode, int value) {
        if (!ensureTouchFeatureService()) {
            log("setModeValue: service not available");
            return -1;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(TOUCH_FEATURE_DESCRIPTOR);
            data.writeInt(touchId);
            data.writeInt(mode);
            data.writeInt(value);
            if (!touchFeatureService.transact(TX_setModeValue, data, reply, 0)) {
                throw new RemoteException("setModeValue transaction rejected");
            }
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            log("setModeValue failed: " + e.getMessage());
            return -1;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /**
     * IXiaomiFingerprint.extCmd(cmd, val) → int
     */
    private int extCmd(int cmd, int val) {
        refreshFingerprintExtensionService();
        if (fpExtService == null) {
            return -1;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(FP_EXT_DESCRIPTOR);
            data.writeInt(cmd);
            data.writeInt(val);
            if (!fpExtService.transact(TX_extCmd, data, reply, 0)) {
                throw new RemoteException("extCmd transaction rejected");
            }
            reply.readException();
            int result = reply.readInt();
            log("extCmd(" + cmd + ", " + val + ") = " + result);
            return result;
        } catch (Exception e) {
            log("extCmd(" + cmd + ", " + val + ") failed: " + e.getMessage());
            return -1;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    // ==================== 生物识别监控 ====================

    private void monitorBiometricService() throws Exception {
        IBinder fpService = getService("fingerprint");
        if (fpService == null) {
            throw new IllegalStateException("fingerprint service not found");
        }

        final boolean[] serviceDead = {false};
        final Object lock = new Object();

        IBinder.DeathRecipient deathRecipient = () -> {
            synchronized (lock) {
                serviceDead[0] = true;
                lock.notifyAll();
            }
        };

        try {
            fpService.linkToDeath(deathRecipient, 0);

            BiometricStateCallback callback = new BiometricStateCallback();
            registerBiometricStateListener(fpService, callback);
            log("biometric state listener registered");

            synchronized (lock) {
                while (!serviceDead[0]) {
                    lock.wait();
                }
            }
        } finally {
            try {
                fpService.unlinkToDeath(deathRecipient, 0);
            } catch (Exception ignored) {
                // The binder may already have died.
            }
        }
    }

    private void registerBiometricStateListener(IBinder service, BiometricStateCallback callback)
            throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(FP_SERVICE_DESCRIPTOR);
            data.writeStrongBinder(callback);
            if (!service.transact(TX_REGISTER_BIOMETRIC_STATE_LISTENER, data, reply, 0)) {
                throw new RemoteException("registerBiometricStateListener transaction rejected");
            }
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private class BiometricStateCallback extends Binder implements IInterface {
        BiometricStateCallback() {
            attachInterface(this, BIO_STATE_DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case 1: // onStateChanged(int)
                    data.enforceInterface(BIO_STATE_DESCRIPTOR);
                    int state = data.readInt();
                    data.enforceNoDataAvail();
                    handleBiometricState(state);
                    return true;
                case 2: // onBiometricAction(int)
                    data.enforceInterface(BIO_STATE_DESCRIPTOR);
                    data.readInt();
                    data.enforceNoDataAvail();
                    return true;
                case 3: // onEnrollmentsChanged(int, int, boolean)
                    data.enforceInterface(BIO_STATE_DESCRIPTOR);
                    data.readInt();
                    data.readInt();
                    data.readBoolean();
                    data.enforceNoDataAvail();
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    private void handleBiometricState(int state) {
        log("biometric-state " + biometricStateName(state));

        synchronized (this) {
            switch (state) {
                case BIO_STATE_KEYGUARD_AUTH:
                    if (isDeviceLocked()) {
                        authOn(SOURCE_KEYGUARD);
                    } else {
                        log("ignore keyguard auth while unlocked");
                    }
                    break;

                case BIO_STATE_AUTH_OTHER:
                    if (envInt(ENV_ALLOW_AUTH_OTHER, 0) == 1) {
                        log("auth-other owner=" + currentFingerprintOwner());
                        authOn(SOURCE_APP);
                    } else {
                        log("ignore auth-other");
                    }
                    break;

                case BIO_STATE_BP_AUTH:
                    if (envInt(ENV_ALLOW_BP_AUTH, 1) == 1) {
                        authOn(SOURCE_APP);
                    } else {
                        log("ignore biometric prompt auth");
                    }
                    break;

                case BIO_STATE_ENROLLING:
                    enrollOn();
                    break;

                case BIO_STATE_IDLE:
                    fodOff();
                    break;

                default:
                    log("ignore unknown biometric state " + state);
                    break;
            }
        }
    }

    // ==================== Keyguard 门控 ====================

    private void startKeyguardGateThread() {
        Thread t = new Thread(() -> {
            while (true) {
                sleep(500);
                enforceKeyguardGate();
            }
        }, "MiFodKeyguardGate");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Keyguard 门控逻辑
     * 
     * - 设备锁定 + 空闲 → authOn(keyguard)
     * - 设备解锁 + 来源是keyguard → fodOff
     * - 设备解锁 + 来源是app → 不干预
     * - 录入中 → 不干预
     */
    private void enforceKeyguardGate() {
        boolean interactive = isInteractive();
        boolean locked = isDeviceLocked();

        synchronized (this) {
            if (currentState == STATE_ENROLL) {
                return;
            }

            if (locked) {
                if (currentState == STATE_IDLE) {
                    log("keyguard gate: " + (interactive ? "screen on" : "screen off")
                            + " + locked → auth-on");
                    authOn(SOURCE_KEYGUARD);
                }
            } else {
                if (currentState != STATE_IDLE && authSource == SOURCE_KEYGUARD) {
                    log("keyguard gate: unlocked → fod-off");
                    fodOff();
                }
            }
        }
    }

    // ==================== 系统状态查询 ====================

    private boolean isDeviceLocked() {
        try {
            IBinder trust = getService("trust");
            if (trust == null) return true;

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(TRUST_SERVICE_DESCRIPTOR);
                data.writeInt(0);
                data.writeInt(0); // deviceId: DEFAULT_DEVICE_ID
                if (!trust.transact(TX_TRUST_IS_DEVICE_LOCKED, data, reply, 0)) {
                    throw new RemoteException("isDeviceLocked transaction rejected");
                }
                reply.readException();
                return reply.readBoolean();
            } finally {
                reply.recycle();
                data.recycle();
            }
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isInteractive() {
        try {
            IBinder power = getService("power");
            if (power == null) return false;

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(POWER_SERVICE_DESCRIPTOR);
                if (!power.transact(TX_POWER_IS_INTERACTIVE, data, reply, 0)) {
                    throw new RemoteException("isInteractive transaction rejected");
                }
                reply.readException();
                return reply.readBoolean();
            } finally {
                reply.recycle();
                data.recycle();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String currentFingerprintOwner() {
        java.lang.Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"/system/bin/dumpsys", "fingerprint"});
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String prefix = "currentFingerprintOwner=";
                    int start = line.indexOf(prefix);
                    if (start >= 0) {
                        return line.substring(start + prefix.length()).trim();
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return "";
    }

    // ==================== 工具方法 ====================

    private String biometricStateName(int state) {
        switch (state) {
            case BIO_STATE_IDLE:        return "idle";
            case BIO_STATE_ENROLLING:   return "enrolling";
            case BIO_STATE_KEYGUARD_AUTH: return "keyguard-auth";
            case BIO_STATE_AUTH_OTHER:  return "auth-other";
            case BIO_STATE_BP_AUTH:     return "biometric-prompt-auth";
            default:                    return "unknown-" + state;
        }
    }

    private String stateName(int state) {
        switch (state) {
            case STATE_IDLE:   return "idle";
            case STATE_AUTH:   return "auth";
            case STATE_ENROLL: return "enroll";
            default:           return "unknown-" + state;
        }
    }

    private int envInt(String name, int def) {
        String val = System.getenv(name);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return def;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void prepareStateDir() {
        if (!STATE_DIR.exists()) {
            STATE_DIR.mkdirs();
        }
    }

    private void log(String msg) {
        if (!debug) return;

        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())
                .format(new Date());
        String logLine = timestamp + " " + msg;

        System.out.println(logLine);

        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            writer.write(logLine + "\n");
        } catch (Exception e) {
            // ignore
        }
    }
}
