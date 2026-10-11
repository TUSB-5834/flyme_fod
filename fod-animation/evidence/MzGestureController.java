package com.flyme.keyguard;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentExt;
import android.database.ContentObserver;
import android.graphics.BitmapFactory;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.BuildExt;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.IGestureCallback;
import android.view.IGestureManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.Dependency;
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.flyme.aod.presenter.AODStateController;
import com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils;
import com.flyme.systemui.statusbar.CentralSurfaceUtil;
import com.flyme.systemui.utils.SystemUICommonUtils;
import com.flyme.systemui.utils.SystemUIMobEventUtils;
import com.meizu.common.widget.MzContactsContract;
import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: classes5.dex */
public class MzGestureController {
    public static ComponentName CAMERA_COMPONENTNAME;
    public static final ComponentName FLASH_LIGHT_COMPONENT = new ComponentName("com.meizu.flashlight", "com.meizu.flashlight");
    public static int mLaunchAppUnlocked;
    public static final BitmapFactory.Options sBitmapOptions;
    public Runnable hideAODAlphaAnimViewRun;
    public boolean isSkipToVibrate;
    public final MediaSessionManager.OnActiveSessionsChangedListener mActiveSessionsListener;
    public Context mContext;
    public FlashlightController mFlashlightController;
    public boolean mIsGestureOpenCenter;
    public KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    public LockPatternUtils mLockPatternUtils;
    public MediaController mMediaController;
    public final MediaDataManager mMediaDataManager;
    public MediaSessionManager mMediaSessionManager;
    public final NotificationShadeWindowController mNotificationShadeWindowController;
    public final PowerManager mPowerManager;
    public PowerManager.WakeLock mRefreshWakeLock;
    public StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    public Vibrator mVibrator;
    public ViewMediatorCallback mViewMediatorCallback;
    public ComponentName mSlideRightComponent = null;
    public Runnable mResetLaunchAppUnlocked = new Runnable() { // from class: com.flyme.keyguard.MzGestureController$$ExternalSyntheticLambda0
        @Override // java.lang.Runnable
        public final void run() {
            MzGestureController.$r8$lambda$Acewigt5zMF2YynFJ39rKQuqe54();
        }
    };
    public boolean mUnlockAndWakeup = false;
    public GestureCallback mGestureCallback = new GestureCallback();
    public Handler mHandler = new Handler(Looper.myLooper(), 0 == true ? 1 : 0, true) { // from class: com.flyme.keyguard.MzGestureController.1
        @Override // android.os.Handler
        public void handleMessage(Message message) {
            if (message.what != 501) {
                return;
            }
            MzGestureController.this.handleGesture(((Integer) message.obj).intValue());
        }
    };

    public final long getMediaSessionKey(int i) {
        if (i != 87) {
            return i != 88 ? -1L : 16L;
        }
        return 32L;
    }

    public void hideAODAlphaAnimViewImmediately() {
    }

    static {
        BitmapFactory.Options options = new BitmapFactory.Options();
        sBitmapOptions = options;
        options.inMutable = true;
        mLaunchAppUnlocked = -1;
        CAMERA_COMPONENTNAME = new ComponentName("com.meizu.media.camera", "com.meizu.media.camera.CameraActivity");
    }

    public static /* synthetic */ void $r8$lambda$Acewigt5zMF2YynFJ39rKQuqe54() {
        Log.d("MzGestureHelper", "mResetLaunchAppUnlocked run ...");
        mLaunchAppUnlocked = -1;
    }

    final class GestureCallback extends IGestureCallback.Stub {
        private GestureCallback() {
        }

        public void onGestureTrigger(KeyEvent keyEvent, MotionEvent motionEvent) throws RemoteException {
            MotionEvent motionEventObtain = motionEvent != null ? MotionEvent.obtain(motionEvent) : null;
            int keyCode = keyEvent.getKeyCode();
            if (keyCode == 413) {
                keyCode = keyEvent.getAction() == 0 ? 161 : 162;
            } else if (keyEvent.getAction() != 0) {
                return;
            }
            Log.i("MzGestureHelper", "onGestureTrigger data:" + keyCode + " MotionEvent:" + motionEventObtain);
            if (FlymeWideFingerPrintUtils.isSupportedWideFp() && keyCode == 161 && motionEventObtain != null) {
                FlymeWideFingerPrintUtils.updateLeftRightSensorAreaState(motionEventObtain);
            }
            MzGestureController.this.mHandler.sendMessage(MzGestureController.this.mHandler.obtainMessage(501, Integer.valueOf(keyCode)));
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public MzGestureController(Context context, KeyguardUpdateMonitor keyguardUpdateMonitor, NotificationShadeWindowController notificationShadeWindowController, FlashlightController flashlightController, PowerManager powerManager, StatusBarKeyguardViewManager statusBarKeyguardViewManager, MediaDataManager mediaDataManager) {
        MediaSessionManager.OnActiveSessionsChangedListener onActiveSessionsChangedListener = new MediaSessionManager.OnActiveSessionsChangedListener() { // from class: com.flyme.keyguard.MzGestureController.5
            @Override // android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
            public void onActiveSessionsChanged(List list) {
                MzGestureController.this.initMedia(list);
            }
        };
        this.mActiveSessionsListener = onActiveSessionsChangedListener;
        this.hideAODAlphaAnimViewRun = new Runnable() { // from class: com.flyme.keyguard.MzGestureController$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$new$3();
            }
        };
        this.mContext = context;
        this.mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        this.mNotificationShadeWindowController = notificationShadeWindowController;
        this.mFlashlightController = flashlightController;
        this.mPowerManager = powerManager;
        this.mStatusBarKeyguardViewManager = statusBarKeyguardViewManager;
        this.mMediaDataManager = mediaDataManager;
        initGestureState();
        registerGestureObserver();
        registerSmartWallpaperStateObserver();
        MediaSessionManager mediaSessionManager = (MediaSessionManager) this.mContext.getSystemService("media_session");
        this.mMediaSessionManager = mediaSessionManager;
        mediaSessionManager.addOnActiveSessionsChangedListener(onActiveSessionsChangedListener, null);
        IGestureManager iGestureManagerAsInterface = IGestureManager.Stub.asInterface(ServiceManager.getService("gesture_manager"));
        Log.w("MzGestureHelper", "gestureManager = " + iGestureManagerAsInterface);
        try {
            if (iGestureManagerAsInterface != null) {
                Log.w("MzGestureHelper", "gestureManager.registeCallback(mGestureCallback,MzGestureHelper)");
                iGestureManagerAsInterface.registeCallback(this.mGestureCallback, "MzGestureHelper");
            } else {
                Log.w("MzGestureHelper", "can not get gesture service");
            }
        } catch (RemoteException e) {
            Log.d("MzGestureHelper", "IGestureManager threw RemoteException", e);
        }
        this.mVibrator = (Vibrator) this.mContext.getSystemService("vibrator");
        this.mLockPatternUtils = new LockPatternUtils(this.mContext);
    }

    public void setViewMediatorCallback(ViewMediatorCallback viewMediatorCallback) {
        this.mViewMediatorCallback = viewMediatorCallback;
    }

    public static boolean launchFastActivity(Context context, Intent intent, boolean z, boolean z2) {
        intent.addFlags(268566528);
        if (!CAMERA_COMPONENTNAME.equals(intent.getComponent())) {
            intent.addFlags(536870912);
        }
        IntentExt.getIntentExt(intent).addMeizuFlags(16);
        if (!z) {
            IntentExt.getIntentExt(intent).setMeizuFlags(IntentExt.getIntentExt(intent).getMeizuFlags() & (-129));
        } else {
            IntentExt.getIntentExt(intent).addMeizuFlags(128);
        }
        intent.getComponent();
        if (z2) {
            IntentExt.getIntentExt(intent).addMeizuFlags(32);
        }
        try {
            context.startActivityAsUser(intent, UserHandle.CURRENT);
            return false;
        } catch (ActivityNotFoundException unused) {
            Log.w("MzGestureHelper", "Activity not found for intent + " + intent.getAction());
            return true;
        }
    }

    public final void registerGestureObserver() {
        final ContentResolver contentResolver = this.mContext.getContentResolver();
        contentResolver.registerContentObserver(Settings.System.getUriFor("mz_quick_wakeup_slide_right_package_detail"), false, new ContentObserver(null) { // from class: com.flyme.keyguard.MzGestureController.2
            @Override // android.database.ContentObserver
            public void onChange(boolean z) {
                MzGestureController.this.mSlideRightComponent = null;
                String string = Settings.System.getString(contentResolver, "mz_quick_wakeup_slide_right_package_detail");
                if (string == null || string.length() <= 0) {
                    return;
                }
                Log.d("MzGestureHelper", "onChange componentStr = " + string);
                String[] strArrSplit = string.split(MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA);
                if (strArrSplit == null || strArrSplit.length != 2) {
                    return;
                }
                MzGestureController.this.mSlideRightComponent = new ComponentName(strArrSplit[0], strArrSplit[1]);
                Log.d("MzGestureHelper", "onChange mSlideRightComponent = " + MzGestureController.this.mSlideRightComponent);
            }
        });
    }

    public final void initGestureState() {
        String[] strArrSplit;
        String string = Settings.System.getString(this.mContext.getContentResolver(), "mz_quick_wakeup_slide_right_package_detail");
        if (string == null || string.length() <= 0 || (strArrSplit = string.split(MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA)) == null || strArrSplit.length != 2) {
            return;
        }
        this.mSlideRightComponent = new ComponentName(strArrSplit[0], strArrSplit[1]);
    }

    public ComponentName getSlideRightComponent() {
        return this.mSlideRightComponent;
    }

    public ComponentName getDrawCComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_c_package_detail");
    }

    public ComponentName getDrawEComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_e_package_detail");
    }

    public ComponentName getDrawMComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_m_package_detail");
    }

    public ComponentName getDrawOComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_o_package_detail");
    }

    public ComponentName getDrawSComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_s_package_detail");
    }

    public ComponentName getDrawVComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_v_package_detail");
    }

    public ComponentName getDrawWComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_w_package_detail");
    }

    public ComponentName getDrawZComponent() {
        return getComponentNameInternal("mz_quick_wakeup_draw_z_package_detail");
    }

    public final ComponentName getComponentNameInternal(String str) {
        String[] strArrSplit;
        String string = Settings.System.getString(this.mContext.getContentResolver(), str);
        if (string == null || string.length() <= 0) {
            return null;
        }
        String[] strArrSplit2 = string.split("\\|");
        if (strArrSplit2 != null && strArrSplit2.length == 2) {
            strArrSplit2[0].split(MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA);
            strArrSplit = (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isPrivateModeEnable() && ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isPrivateMode()) ? strArrSplit2[1].split(MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA) : strArrSplit2[0].split(MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA);
        } else {
            strArrSplit = string.split(MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA);
        }
        Log.d("MzGestureHelper", "getComponentNameInternal component = " + Arrays.toString(strArrSplit));
        if (strArrSplit == null || strArrSplit.length != 2) {
            return null;
        }
        return new ComponentName(strArrSplit[0], strArrSplit[1]);
    }

    public final void registerSmartWallpaperStateObserver() {
        final ContentResolver contentResolver = this.mContext.getContentResolver();
        contentResolver.registerContentObserver(Settings.System.getUriFor("set_wallpaper_simultaneously"), false, new ContentObserver(null) { // from class: com.flyme.keyguard.MzGestureController.3
            @Override // android.database.ContentObserver
            public void onChange(boolean z) {
                int i = Settings.System.getInt(contentResolver, "set_wallpaper_simultaneously", 0);
                SystemUIMobEventUtils.collectSmartWallpaperState(MzGestureController.this.mContext, i == 1);
                Log.d("MzGestureHelper", "onChange smart wallpaper state = " + i);
            }
        });
    }

    /* JADX WARN: Code duplicated, block: B:58:0x0136  */
    public final void handleGesture(int i) {
        Log.d("MzGestureHelper", "handleGesture gestureType = " + i);
        boolean zIsEasyMode = this.mKeyguardUpdateMonitor.isEasyMode();
        Log.d("MzGestureHelper", "isEasyMode = " + zIsEasyMode);
        if (!zIsEasyMode || i == 161 || i == 162 || i == 412) {
            this.isSkipToVibrate = false;
            if (i != 161) {
                if (i != 162) {
                    if (i == 411) {
                        wakeUp();
                        SystemUIMobEventUtils.collectGestures(this.mContext, i, null);
                    } else if (i == 412) {
                        ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().onScreenOffGestureOnceTap();
                    } else if (i != 428) {
                        if (i != 432 && i != 433) {
                            switch (i) {
                                case 414:
                                    if (!this.mMediaDataManager.hasActiveMediaOrRecommendation()) {
                                        this.isSkipToVibrate = true;
                                    }
                                    sendMediaAction(isProductInternationalAndRtl() ? 88 : 87);
                                    SystemUIMobEventUtils.collectGestures(this.mContext, i, null);
                                    break;
                                case 415:
                                    if (!this.mMediaDataManager.hasActiveMediaOrRecommendation()) {
                                        this.isSkipToVibrate = true;
                                    }
                                    sendMediaAction(isProductInternationalAndRtl() ? 87 : 88);
                                    SystemUIMobEventUtils.collectGestures(this.mContext, i, null);
                                    break;
                                case 416:
                                    if (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isGoingToSleep() && !this.mLockPatternUtils.isSecure(this.mKeyguardUpdateMonitor.getCurrentUser())) {
                                        this.isSkipToVibrate = true;
                                    }
                                    if (!this.mKeyguardUpdateMonitor.hasLockPassword() && !this.mKeyguardUpdateMonitor.isSimPinSecure()) {
                                        this.mUnlockAndWakeup = true;
                                        this.mViewMediatorCallback.keyguardDone(this.mKeyguardUpdateMonitor.getCurrentUser());
                                        if (SystemUICommonUtils.isPowerEnterSleep(this.mContext)) {
                                            wakeUp();
                                        }
                                    } else {
                                        wakeUp();
                                        this.mStatusBarKeyguardViewManager.delayShowBouncer("swipe_y_up", 700);
                                    }
                                    SystemUIMobEventUtils.collectGestures(this.mContext, i, null);
                                    break;
                                case 417:
                                    this.mIsGestureOpenCenter = true;
                                    wakeUp();
                                    if (SystemUICommonUtils.getInstance(this.mContext).isLockScreenDisabled(this.mKeyguardUpdateMonitor.getCurrentUser())) {
                                        this.mHandler.postDelayed(new Runnable(this) { // from class: com.flyme.keyguard.MzGestureController.4
                                            @Override // java.lang.Runnable
                                            public void run() {
                                                ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().instantExpand();
                                            }
                                        }, 100L);
                                    } else {
                                        ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().instantExpand();
                                    }
                                    SystemUIMobEventUtils.collectGestures(this.mContext, i, null);
                                    break;
                                case 418:
                                case 419:
                                case 420:
                                case 421:
                                case 422:
                                case 423:
                                case 424:
                                    handleCustomGesture(i, false);
                                    break;
                            }
                        } else {
                            this.mViewMediatorCallback.userActivity();
                        }
                    } else {
                        handleCustomGesture(i, false);
                    }
                } else {
                    if (!((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).fingerprintUsedAsUnlock()) {
                        return;
                    }
                    ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().onFingerUp();
                    if (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isUdfpsEnrolled() && ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isFingerprintDetectionRunning()) {
                        if (FlymeWideFingerPrintUtils.isSupportedWideFp()) {
                            if (FlymeWideFingerPrintUtils.shouldShowLeftRightIcon(this.mContext)) {
                                if (FlymeWideFingerPrintUtils.isInLeftSensorArea()) {
                                    ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).hideFingerprintDownAnimLeft();
                                } else if (FlymeWideFingerPrintUtils.isInRightSensorArea()) {
                                    ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).hideFingerprintDownAnimRight();
                                }
                            }
                            if (FlymeWideFingerPrintUtils.isInCenterSensorArea()) {
                                ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).hideFingerprintDownAnim();
                            }
                        } else {
                            ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).hideFingerprintDownAnim();
                        }
                    }
                }
            } else {
                if (!((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).fingerprintUsedAsUnlock()) {
                    return;
                }
                boolean zIsUserUnlocked = this.mKeyguardUpdateMonitor.isUserUnlocked();
                boolean zIsFingerprintLockedOut = this.mKeyguardUpdateMonitor.isFingerprintLockedOut();
                boolean zIsNeedStrongAuth = this.mKeyguardUpdateMonitor.isNeedStrongAuth();
                boolean isFaceAuthenticated = this.mKeyguardUpdateMonitor.getIsFaceAuthenticated();
                boolean zIsFakeFingerprintVisible = this.mKeyguardUpdateMonitor.isFakeFingerprintVisible();
                if (!((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isDeviceInteractive()) {
                    aquireFPWakeLock();
                }
                if ((this.mStatusBarKeyguardViewManager != null && (zIsFingerprintLockedOut || isFaceAuthenticated)) || !zIsUserUnlocked || zIsNeedStrongAuth || this.mKeyguardUpdateMonitor.isKeyguardDismissPauseForEditor()) {
                    if (zIsFakeFingerprintVisible && FlymeWideFingerPrintUtils.isSupportedWideFp() && !FlymeWideFingerPrintUtils.isInLeftOrRightOrCenterSensorArea()) {
                        Log.e("MzGestureHelper", "GESTURE_FOD_DOWN is out of WideSensorArea");
                    } else {
                        wakeUp();
                        if (zIsFakeFingerprintVisible) {
                            FlymeWideFingerPrintUtils.startQuickLaunch(this.mContext, !isFaceAuthenticated);
                        }
                        this.mStatusBarKeyguardViewManager.showBouncer(true, "GESTURE_FOD_DOWN");
                    }
                }
                ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().onFingerDown();
                if (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isUdfpsEnrolled() && ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isFingerprintDetectionRunning()) {
                    if (FlymeWideFingerPrintUtils.isSupportedWideFp()) {
                        if (FlymeWideFingerPrintUtils.shouldShowLeftRightIcon(this.mContext)) {
                            if (FlymeWideFingerPrintUtils.isInLeftSensorArea()) {
                                ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).showFingerprintDownAnimLeft();
                            } else if (FlymeWideFingerPrintUtils.isInRightSensorArea()) {
                                ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).showFingerprintDownAnimRight();
                            }
                        }
                        if (FlymeWideFingerPrintUtils.isInCenterSensorArea()) {
                            ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).showFingerprintDownAnim();
                        }
                    } else {
                        ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).showFingerprintDownAnim();
                    }
                }
                if (isFaceAuthenticated) {
                    SystemUIMobEventUtils.collectUnlockMode(this.mContext, ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).getCurrentSystemMode(), 6);
                }
            }
            if (shouldVibrate(i)) {
                this.mVibrator.vibrate(Process.myUid(), this.mContext.getPackageName(), VibrationEffect.get(30800), "gesture_vibrate", null);
            }
        }
    }

    public final void handleCustomGesture(int i, boolean z) {
        ComponentName slideRightComponent;
        Log.d("MzGestureHelper", "gestureType = " + i + " ,done" + z);
        if (i == 415) {
            slideRightComponent = getSlideRightComponent();
        } else if (i != 428) {
            switch (i) {
                case 418:
                    slideRightComponent = getDrawEComponent();
                    break;
                case 419:
                    slideRightComponent = getDrawCComponent();
                    break;
                case 420:
                    slideRightComponent = getDrawWComponent();
                    break;
                case 421:
                    slideRightComponent = getDrawMComponent();
                    break;
                case 422:
                    slideRightComponent = getDrawOComponent();
                    break;
                case 423:
                    slideRightComponent = getDrawSComponent();
                    break;
                case 424:
                    slideRightComponent = getDrawVComponent();
                    break;
                default:
                    slideRightComponent = null;
                    break;
            }
        } else {
            slideRightComponent = getDrawZComponent();
        }
        if (slideRightComponent != null) {
            Log.d("MzGestureHelper", "component " + slideRightComponent.toString());
            ComponentName componentName = FLASH_LIGHT_COMPONENT;
            if (componentName.equals(slideRightComponent)) {
                Log.d("MzGestureHelper", "FLASH_LIGHT_COMPONENT.equals(component) " + componentName.equals(slideRightComponent) + " ,mFlashlightController" + this.mFlashlightController);
                if (this.mFlashlightController != null) {
                    Log.w("MzGestureHelper", "isEnabled() = " + this.mFlashlightController.isEnabled());
                    FlashlightController flashlightController = this.mFlashlightController;
                    flashlightController.setFlashlight(flashlightController.isEnabled() ^ true);
                } else {
                    Log.w("MzGestureHelper", "flash light controller null ...");
                }
                SystemUIMobEventUtils.collectGestures(this.mContext, i, slideRightComponent.toShortString());
            }
        }
    }

    public final void wakeUp() {
        this.mPowerManager.wakeUp(SystemClock.uptimeMillis(), 4, "com.android.systemui:MzGestureController");
        Log.d("MzGestureHelper", "wakeUp");
    }

    public final void initMedia(List list) {
        if (list == null || list.size() <= 0) {
            return;
        }
        this.mMediaController = (MediaController) list.get(0);
    }

    public final void sendMediaKeyEvent(int i) {
        MediaController mediaController = this.mMediaController;
        if (mediaController != null) {
            mediaController.dispatchMediaButtonEvent(new KeyEvent(0, i));
            this.mMediaController.dispatchMediaButtonEvent(new KeyEvent(1, i));
        }
    }

    public final boolean checkActionSupported(PlaybackState playbackState, long j) {
        Log.d("MzGestureHelper", "checkActionSupported: action " + j + " state " + playbackState);
        return j > 0 && playbackState != null && playbackState.getActions() == 0;
    }

    public final void sendSessionControl(int i) {
        MediaController mediaController;
        if (i != 87) {
            if (i == 88 && (mediaController = this.mMediaController) != null) {
                mediaController.getTransportControls().skipToPrevious();
                return;
            }
            return;
        }
        MediaController mediaController2 = this.mMediaController;
        if (mediaController2 != null) {
            mediaController2.getTransportControls().skipToNext();
        }
    }

    public final void sendMediaAction(int i) {
        Log.d("MzGestureHelper", "sendMediaAction: key " + KeyEvent.actionToString(i));
        if (this.mMediaController == null) {
            return;
        }
        if (checkActionSupported(this.mMediaController.getPlaybackState(), getMediaSessionKey(i))) {
            Log.d("MzGestureHelper", "sendMediaAction: sendSessionControl");
            sendSessionControl(i);
        } else {
            Log.d("MzGestureHelper", "sendMediaAction: sendMediaKeyEvent");
            sendMediaKeyEvent(i);
        }
    }

    public final boolean shouldVibrate(int i) {
        return (i == 161 || i == 162 || i == 412 || this.isSkipToVibrate) ? false : true;
    }

    public final boolean isProductInternationalAndRtl() {
        return BuildExt.isProductInternational() && this.mContext.getResources().getConfiguration().getLayoutDirection() == 1;
    }

    public final /* synthetic */ void lambda$new$3() {
        Log.d("MzGestureHelper", "hideAODAlphaAnimViewRun unlockedWithFingerprint=" + ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).getUserUnlockedWithFingerprint());
        if (((AODStateController) Dependency.get(AODStateController.class)).isFollowLockAOD()) {
            this.mHandler.post(new Runnable() { // from class: com.flyme.keyguard.MzGestureController$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().setFullScreenAODAnimViewVisible(false);
                }
            });
        }
        ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().setAODAlphaAnimViewVisible(false);
    }

    public final void aquireFPWakeLock() {
        Log.d("MzGestureHelper", "aquireFPWakeLock");
        if (this.mRefreshWakeLock == null) {
            PowerManager.WakeLock wakeLockNewWakeLock = ((PowerManager) this.mContext.getSystemService("power")).newWakeLock(1, "com.flyme.finger.down.wake.lock");
            this.mRefreshWakeLock = wakeLockNewWakeLock;
            wakeLockNewWakeLock.setReferenceCounted(false);
        }
        if (this.mRefreshWakeLock.isHeld()) {
            this.mRefreshWakeLock.release();
        }
        this.mRefreshWakeLock.acquire(3000L);
    }
}
