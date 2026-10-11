package com.android.systemui.biometrics;

import android.R;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.hardware.biometrics.BiometricFingerprintConstants;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.display.DisplayManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.hardware.fingerprint.IUdfpsOverlayController;
import android.hardware.fingerprint.IUdfpsOverlayControllerCallback;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Trace;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.PathInterpolator;
import androidx.emoji2.text.ConcurrencyHelpers$$ExternalSyntheticLambda0;
import com.android.internal.logging.InstanceId;
import com.android.internal.util.LatencyTracker;
import com.android.internal.util.Preconditions;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.UserActivityNotifier;
import com.android.systemui.Dependency;
import com.android.systemui.Dumpable;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.assist.ui.DisplayUtils;
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor;
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams;
import com.android.systemui.biometrics.udfps.InteractionEvent;
import com.android.systemui.biometrics.udfps.NormalizedTouchData;
import com.android.systemui.biometrics.udfps.SinglePointerTouchProcessor;
import com.android.systemui.biometrics.udfps.TouchProcessor;
import com.android.systemui.biometrics.udfps.TouchProcessorResult;
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor;
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.keyguard.ScreenLifecycle;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.log.SessionTracker;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.shade.domain.interactor.ShadeInteractor;
import com.android.systemui.shared.system.SysUiStatsLog;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.SystemUIDialogManager;
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.concurrency.Execution;
import com.android.systemui.util.time.SystemClock;
import com.flyme.aod.FlymeAODConfig;
import com.flyme.aod.presenter.AODStateController;
import com.flyme.aod.utils.CommonUtils;
import com.flyme.keyguard.livewallpaper.LiveWallpaperController;
import com.flyme.systemui.SystemUIDebugConfig;
import com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils;
import com.flyme.systemui.statusbar.CentralSurfaceUtil;
import com.meizu.systemui.shared.compat.DeviceStateManagerCompat;
import dagger.Lazy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;

/* JADX INFO: loaded from: classes2.dex */
public class UdfpsController implements DozeReceiver, Dumpable {
    public PathInterpolator alphaPathIterator;
    public final AccessibilityManager mAccessibilityManager;
    public boolean mAcquiredReceived;
    public final ActivityTransitionAnimator mActivityTransitionAnimator;
    public final AlternateBouncerInteractor mAlternateBouncerInteractor;
    public Runnable mAodInterruptRunnable;
    public boolean mAttemptedToDismissKeyguard;
    public Runnable mAuthControllerUpdateUdfpsLocation;
    public final Executor mBiometricExecutor;
    public final BroadcastReceiver mBroadcastReceiver;
    public Runnable mCancelAodFingerUpAction;
    public int mColor;
    public final ConfigurationController mConfigurationController;
    public final Context mContext;
    public final Lazy mDefaultUdfpsTouchOverlayViewModel;
    public final DeviceEntryFaceAuthInteractor mDeviceEntryFaceAuthInteractor;
    public final Lazy mDeviceEntryUdfpsTouchOverlayViewModel;
    public final SystemUIDialogManager mDialogManager;
    public final Runnable mDismissFingerprintIconRunnable;
    public final Runnable mDismissFpIconWithoutAnimRunnable;
    public final DumpManager mDumpManager;
    public final Execution mExecution;
    public IUdfpsOverlayControllerCallback mFakeIconCallback;
    public final FalsingManager mFalsingManager;
    public final DelayableExecutor mFgExecutor;
    public final FingerprintManager mFingerprintManager;
    public Handler mHandler;
    public final Runnable mHideFakeFpIconRunnable;
    public final boolean mIgnoreRefreshRate;
    public final LayoutInflater mInflater;
    public final InputManager mInputManager;
    public boolean mIsAodInterruptActive;
    public boolean mIsIconAlphaVisible;
    public final KeyguardStateController mKeyguardStateController;
    public final KeyguardTransitionInteractor mKeyguardTransitionInteractor;
    public final KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    public final StatusBarKeyguardViewManager mKeyguardViewManager;
    public long mLastTouchInteractionTime;
    public final LatencyTracker mLatencyTracker;
    public final LockscreenShadeTransitionController mLockscreenShadeTransitionController;
    public boolean mNeedShowFakeFpIcon;
    public boolean mOnFingerDown;
    final BiometricDisplayListener mOrientationListener;
    public UdfpsControllerOverlay mOverlay;
    public final PowerInteractor mPowerInteractor;
    public final PowerManager mPowerManager;
    public Rect mPreventRect;
    public final PrimaryBouncerInteractor mPrimaryBouncerInteractor;
    public final Lazy mPromptUdfpsTouchOverlayViewModel;
    public final CoroutineScope mScope;
    public final ScreenLifecycle.Observer mScreenObserver;
    public boolean mScreenOn;
    public final SelectedUserInteractor mSelectedUserInteractor;
    FingerprintSensorPropertiesInternal mSensorProps;
    public final SessionTracker mSessionTracker;
    public final ShadeExpansionStateManager mShadeExpansionStateManager;
    public final ShadeInteractor mShadeInteractor;
    public final Runnable mShowFakeFpIconRunnable;
    public final Runnable mShowFingerprintIconRunnable;
    public boolean mStartHideUdfpsOverlay;
    public final StatusBarStateController mStatusBarStateController;
    public final SystemClock mSystemClock;
    public final TouchProcessor mTouchProcessor;
    public UdfpsDisplayModeProvider mUdfpsDisplayMode;
    public final UdfpsKeyguardAccessibilityDelegate mUdfpsKeyguardAccessibilityDelegate;
    public final UdfpsOverlayInteractor mUdfpsOverlayInteractor;
    public final UnlockedScreenOffAnimationController mUnlockedScreenOffAnimationController;
    public final UserActivityNotifier mUserActivityNotifier;
    public final VibratorHelper mVibrator;
    public final WindowManager mWindowManager;
    public static final VibrationAttributes UDFPS_VIBRATION_ATTRIBUTES = new VibrationAttributes.Builder().setUsage(65).build();
    public static final VibrationAttributes LOCK_ICON_VIBRATION_ATTRIBUTES = new VibrationAttributes.Builder().setUsage(18).build();
    public static final VibrationEffect EFFECT_CLICK = VibrationEffect.get(0);
    UdfpsOverlayParams mOverlayParams = new UdfpsOverlayParams();
    public int mActivePointerId = -1;
    public boolean mPointerPilfered = false;
    public final Set mCallbacks = new HashSet();

    public interface Callback {
        void onFingerDown();

        void onFingerUp();
    }

    @Override // com.android.systemui.doze.DozeReceiver
    public void dozeTimeTick() {
    }

    public void playStartHaptic() {
    }

    @Override // com.android.systemui.Dumpable
    public void dump(PrintWriter printWriter, String[] strArr) {
        int integer = this.mContext.getResources().getInteger(R.integer.config_settingsKeyBehavior);
        printWriter.println("mSensorProps=(" + this.mSensorProps + ")");
        StringBuilder sb = new StringBuilder();
        sb.append("touchConfigId: ");
        sb.append(integer);
        printWriter.println(sb.toString());
    }

    public class UdfpsOverlayController extends IUdfpsOverlayController.Stub {
        public void onEnrollmentHelp(int i) {
        }

        public void onEnrollmentProgress(int i, int i2) {
        }

        public UdfpsOverlayController() {
        }

        public void showUdfpsOverlay(final long j, int i, final int i2, final IUdfpsOverlayControllerCallback iUdfpsOverlayControllerCallback) {
            Log.i("UdfpsController", "showUdfpsOverlay from framework. requestId=" + j + ", sensorId=" + i + ", reason=" + i2);
            UdfpsController.this.setNeedShowFakeFpIcon(false);
            UdfpsController.this.mUdfpsOverlayInteractor.setRequestId(j);
            UdfpsController.this.mFgExecutor.execute(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$showUdfpsOverlay$1(j, i2, iUdfpsOverlayControllerCallback);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$showUdfpsOverlay$1(final long j, int i, IUdfpsOverlayControllerCallback iUdfpsOverlayControllerCallback) {
            UdfpsController.this.showUdfpsOverlay(new UdfpsControllerOverlay(UdfpsController.this.mContext, UdfpsController.this.mInflater, UdfpsController.this.mWindowManager, UdfpsController.this.mAccessibilityManager, UdfpsController.this.mStatusBarStateController, UdfpsController.this.mKeyguardViewManager, UdfpsController.this.mKeyguardUpdateMonitor, UdfpsController.this.mDialogManager, UdfpsController.this.mDumpManager, UdfpsController.this.mLockscreenShadeTransitionController, UdfpsController.this.mConfigurationController, UdfpsController.this.mKeyguardStateController, UdfpsController.this.mUnlockedScreenOffAnimationController, UdfpsController.this.mUdfpsDisplayMode, j, i, iUdfpsOverlayControllerCallback, new Function2() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda3
                @Override // kotlin.jvm.functions.Function2
                public final Object invoke(Object obj, Object obj2) {
                    return this.f$0.lambda$showUdfpsOverlay$0(j, (View) obj, (MotionEvent) obj2);
                }
            }, UdfpsController.this.mActivityTransitionAnimator, UdfpsController.this.mPrimaryBouncerInteractor, UdfpsController.this.mAlternateBouncerInteractor, UdfpsController.this.mUdfpsKeyguardAccessibilityDelegate, UdfpsController.this.mKeyguardTransitionInteractor, UdfpsController.this.mSelectedUserInteractor, UdfpsController.this.mDeviceEntryUdfpsTouchOverlayViewModel, UdfpsController.this.mDefaultUdfpsTouchOverlayViewModel, UdfpsController.this.mPromptUdfpsTouchOverlayViewModel, UdfpsController.this.mShadeInteractor, UdfpsController.this.mUdfpsOverlayInteractor, UdfpsController.this.mPowerInteractor, UdfpsController.this.mScope));
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ Boolean lambda$showUdfpsOverlay$0(long j, View view, MotionEvent motionEvent) {
            return Boolean.valueOf(UdfpsController.this.onTouch(j, motionEvent));
        }

        public void hideUdfpsOverlay(int i) {
            Log.i("UdfpsController", "hideUdfpsOverlay from framework.sensorId=" + i + ", mNeedShowFakeFpIcon=" + UdfpsController.this.mNeedShowFakeFpIcon);
            UdfpsController.this.mStartHideUdfpsOverlay = true;
            if (UdfpsController.this.mNeedShowFakeFpIcon) {
                Log.d("UdfpsController", "hideUdfpsOverlay cancel due to NeedShowFakeFpIcon");
            } else if (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).getIsFaceAuthenticated() && FlymeAODConfig.checkProductModel("M2531")) {
                UdfpsController.this.mFgExecutor.executeDelayed(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$hideUdfpsOverlay$2();
                    }
                }, 125L);
            } else {
                UdfpsController.this.mFgExecutor.execute(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$hideUdfpsOverlay$3();
                    }
                });
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$hideUdfpsOverlay$2() {
            if (UdfpsController.this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning()) {
                Log.d("UdfpsController", "hiding udfps overlay when mKeyguardUpdateMonitor.isFingerprintDetectionRunning()=true");
            }
            Log.i("UdfpsController", "hideUdfpsOverlay delay for m2531");
            UdfpsController.this.hideUdfpsOverlay();
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$hideUdfpsOverlay$3() {
            if (UdfpsController.this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning()) {
                Log.d("UdfpsController", "hiding udfps overlay when mKeyguardUpdateMonitor.isFingerprintDetectionRunning()=true");
            }
            UdfpsController.this.hideUdfpsOverlay();
        }

        public void onAcquired(final int i, final int i2) {
            if (UdfpsController.this.isUltrasonic()) {
                if (i2 == 7) {
                    UdfpsController.this.mFgExecutor.execute(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda4
                        @Override // java.lang.Runnable
                        public final void run() {
                            this.f$0.lambda$onAcquired$4();
                        }
                    });
                } else {
                    UdfpsController.this.mFgExecutor.execute(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda5
                        @Override // java.lang.Runnable
                        public final void run() {
                            this.f$0.lambda$onAcquired$5();
                        }
                    });
                }
            }
            if (BiometricFingerprintConstants.shouldDisableUdfpsDisplayMode(i2)) {
                UdfpsController.this.mFgExecutor.execute(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda6
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$onAcquired$6(i, i2);
                    }
                });
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAcquired$4() {
            Iterator it = UdfpsController.this.mCallbacks.iterator();
            while (it.hasNext()) {
                ((Callback) it.next()).onFingerDown();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAcquired$5() {
            Iterator it = UdfpsController.this.mCallbacks.iterator();
            while (it.hasNext()) {
                ((Callback) it.next()).onFingerUp();
            }
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAcquired$6(int i, int i2) {
            UdfpsController udfpsController = UdfpsController.this;
            if (udfpsController.mOverlay == null) {
                Log.e("UdfpsController", "Null request when onAcquired for sensorId: " + i + " acquiredInfo=" + i2);
                return;
            }
            udfpsController.mAcquiredReceived = true;
            UdfpsController.this.unconfigureDisplay(UdfpsController.this.mOverlay.getTouchOverlay());
            UdfpsController.this.tryAodSendFingerUp();
        }

        public void setDebugMessage(int i, String str) {
            UdfpsController.this.mFgExecutor.execute(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$UdfpsOverlayController$$ExternalSyntheticLambda7
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$setDebugMessage$7();
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$setDebugMessage$7() {
            UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
            if (udfpsControllerOverlay != null) {
                udfpsControllerOverlay.isHiding();
            }
        }

        public Rect getSensorBounds() {
            return UdfpsController.this.mOverlayParams.getSensorBounds();
        }

        public void debugOnTouch(MotionEvent motionEvent) {
            UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
            UdfpsController.this.onTouch(udfpsControllerOverlay != null ? udfpsControllerOverlay.getRequestId() : 0L, motionEvent);
        }

        public void debugOnUiReady(int i) {
            UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
            UdfpsController.this.mFingerprintManager.onUdfpsUiEvent(2, udfpsControllerOverlay != null ? udfpsControllerOverlay.getRequestId() : 0L, i);
        }

        public void debugBiometricPrompt() {
            BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback(this) { // from class: com.android.systemui.biometrics.UdfpsController.UdfpsOverlayController.1
            };
            new BiometricPrompt.Builder(UdfpsController.this.mContext).setTitle("Test").setDeviceCredentialAllowed(true).setAllowBackgroundAuthentication(true).build().authenticate(new CancellationSignal(), new ConcurrencyHelpers$$ExternalSyntheticLambda0(new Handler(Looper.getMainLooper())), authenticationCallback);
        }

        public void debugSetIgnoreDisplayTouches(boolean z) {
            UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
            UdfpsController.this.mFingerprintManager.setIgnoreDisplayTouches(udfpsControllerOverlay != null ? udfpsControllerOverlay.getRequestId() : 0L, UdfpsController.this.mSensorProps.sensorId, z);
        }
    }

    public void updateOverlayParams(FingerprintSensorPropertiesInternal fingerprintSensorPropertiesInternal, UdfpsOverlayParams udfpsOverlayParams) {
        if (this.mSensorProps.sensorId != fingerprintSensorPropertiesInternal.sensorId) {
            this.mSensorProps = fingerprintSensorPropertiesInternal;
            Log.w("UdfpsController", "updateUdfpsParams | sensorId has changed");
        }
        Log.d("UdfpsController", "updateSensorLocation updateOverlayParams  overlayParams==" + udfpsOverlayParams);
        if (this.mOverlayParams.equals(udfpsOverlayParams)) {
            return;
        }
        this.mOverlayParams = udfpsOverlayParams;
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null && udfpsControllerOverlay.getRequestReason() == 4) {
            this.mOverlay.updateOverlayParams(this.mOverlayParams);
        } else {
            redrawOverlay();
        }
        boolean zIsVisibleState = this.mAlternateBouncerInteractor.isVisibleState();
        redrawOverlay();
        if (zIsVisibleState) {
            this.mKeyguardViewManager.showBouncer(true, "UdfpsController-wasShowingAlternateBouncer");
        }
    }

    public void setAuthControllerUpdateUdfpsLocation(Runnable runnable) {
        this.mAuthControllerUpdateUdfpsLocation = runnable;
    }

    public void setUdfpsDisplayMode(UdfpsDisplayModeProvider udfpsDisplayModeProvider) {
        this.mUdfpsDisplayMode = udfpsDisplayModeProvider;
    }

    public final void tryDismissingKeyguard() {
        if (!this.mOnFingerDown) {
            playStartHaptic();
        }
        this.mKeyguardViewManager.notifyKeyguardAuthenticated(false);
        this.mAttemptedToDismissKeyguard = true;
    }

    public final int getBiometricSessionType() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay == null) {
            return -1;
        }
        int requestReason = udfpsControllerOverlay.getRequestReason();
        if (requestReason == 1 || requestReason == 2) {
            return 4;
        }
        if (requestReason != 3) {
            return requestReason != 4 ? -1 : 1;
        }
        return 2;
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsController$8, reason: invalid class name */
    public abstract /* synthetic */ class AnonymousClass8 {
        public static final /* synthetic */ int[] $SwitchMap$com$android$systemui$biometrics$udfps$InteractionEvent;

        static {
            int[] iArr = new int[InteractionEvent.values().length];
            $SwitchMap$com$android$systemui$biometrics$udfps$InteractionEvent = iArr;
            try {
                iArr[InteractionEvent.DOWN.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$android$systemui$biometrics$udfps$InteractionEvent[InteractionEvent.UP.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                $SwitchMap$com$android$systemui$biometrics$udfps$InteractionEvent[InteractionEvent.CANCEL.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
        }
    }

    public static int toBiometricTouchReportedTouchType(InteractionEvent interactionEvent) {
        int i = AnonymousClass8.$SwitchMap$com$android$systemui$biometrics$udfps$InteractionEvent[interactionEvent.ordinal()];
        int i2 = 1;
        if (i != 1) {
            i2 = 2;
            if (i != 2) {
                i2 = 3;
                if (i != 3) {
                    return 0;
                }
            }
        }
        return i2;
    }

    public final void logBiometricTouch(InteractionEvent interactionEvent, NormalizedTouchData normalizedTouchData) {
        if (interactionEvent != InteractionEvent.UNCHANGED || this.mSystemClock.elapsedRealtime() - this.mLastTouchInteractionTime >= 50) {
            this.mLastTouchInteractionTime = this.mSystemClock.elapsedRealtime();
            int biometricTouchReportedTouchType = toBiometricTouchReportedTouchType(interactionEvent);
            InstanceId sessionId = this.mSessionTracker.getSessionId(getBiometricSessionType());
            int id = sessionId != null ? sessionId.getId() : -1;
            int integer = this.mContext.getResources().getInteger(R.integer.config_settingsKeyBehavior);
            SysUiStatsLog.write(577, biometricTouchReportedTouchType, integer, id, normalizedTouchData.getX(), normalizedTouchData.getY(), normalizedTouchData.getMinor(), normalizedTouchData.getMajor(), normalizedTouchData.getOrientation(), normalizedTouchData.getTime(), normalizedTouchData.getGestureStart(), this.mStatusBarStateController.isDozing());
            if (Build.isDebuggable()) {
                Log.d("UdfpsController", normalizedTouchData.toPrettyString(interactionEvent.toString()));
                Log.d("UdfpsController", "sessionId: " + id + ", isAod: " + this.mStatusBarStateController.isDozing() + ", touchConfigId: " + integer);
            }
        }
    }

    public final boolean onTouch(long j, MotionEvent motionEvent) {
        NormalizedTouchData normalizedTouchData;
        boolean z;
        int i;
        boolean z2;
        boolean z3;
        if (motionEvent.getActionMasked() == 0) {
            Log.d("UdfpsController", "onTouch down");
        }
        if (!DeviceStateManagerCompat.isCtsRunningStatic()) {
            return true;
        }
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay == null) {
            Log.w("UdfpsController", "ignoring onTouch with null overlay");
            return false;
        }
        if (!udfpsControllerOverlay.matchesRequestId(j)) {
            Log.w("UdfpsController", "ignoring stale touch event: " + j + " current: " + this.mOverlay.getRequestId());
            return false;
        }
        if (motionEvent.getAction() == 0 || motionEvent.getAction() == 9) {
            this.mPointerPilfered = false;
            if (this.mActivePointerId != -1) {
                Log.w("UdfpsController", "onTouch down received without a preceding up");
            }
            this.mActivePointerId = -1;
            if (!this.mIsAodInterruptActive) {
                this.mOnFingerDown = false;
            }
        }
        TouchProcessorResult touchProcessorResultProcessTouch = this.mTouchProcessor.processTouch(motionEvent, this.mActivePointerId, this.mOverlayParams);
        if (touchProcessorResultProcessTouch instanceof TouchProcessorResult.Failure) {
            Log.w("UdfpsController", ((TouchProcessorResult.Failure) touchProcessorResultProcessTouch).getReason());
            return false;
        }
        TouchProcessorResult.ProcessedTouch processedTouch = (TouchProcessorResult.ProcessedTouch) touchProcessorResultProcessTouch;
        NormalizedTouchData touchData = processedTouch.getTouchData();
        this.mActivePointerId = processedTouch.getPointerOnSensorId();
        int i2 = AnonymousClass8.$SwitchMap$com$android$systemui$biometrics$udfps$InteractionEvent[processedTouch.getEvent().ordinal()];
        if (i2 != 1) {
            if (i2 == 2 || i2 == 3) {
                if (InteractionEvent.CANCEL.equals(processedTouch.getEvent())) {
                    Log.w("UdfpsController", "This is a CANCEL event that's reported as an UP event!");
                }
                this.mAttemptedToDismissKeyguard = false;
                z = false;
                normalizedTouchData = touchData;
                i = -1;
                onFingerUp(j, this.mOverlay.getTouchOverlay(), touchData.getPointerId(), touchData.getX(), touchData.getY(), touchData.getMinor(), touchData.getMajor(), touchData.getOrientation(), touchData.getTime(), touchData.getGestureStart(), this.mStatusBarStateController.isDozing());
            } else {
                normalizedTouchData = touchData;
                z = false;
                i = -1;
            }
            z2 = z;
        } else {
            normalizedTouchData = touchData;
            z = false;
            i = -1;
            if (shouldTryToDismissKeyguard()) {
                tryDismissingKeyguard();
            }
            if (!this.mOnFingerDown) {
                onFingerDown(j, normalizedTouchData.getPointerId(), normalizedTouchData.getX(), normalizedTouchData.getY(), normalizedTouchData.getMinor(), normalizedTouchData.getMajor(), normalizedTouchData.getOrientation(), normalizedTouchData.getTime(), normalizedTouchData.getGestureStart(), this.mStatusBarStateController.isDozing());
            }
            this.mFalsingManager.isFalseTouch(13);
            z2 = true;
        }
        logBiometricTouch(processedTouch.getEvent(), normalizedTouchData);
        if (this.mActivePointerId != i) {
            z2 = true;
        }
        if (!z2 || this.mPointerPilfered || getBiometricSessionType() == 2) {
            z3 = true;
        } else {
            this.mInputManager.pilferPointers(this.mOverlay.getTouchOverlay().getViewRootImpl().getInputToken());
            z3 = true;
            this.mPointerPilfered = true;
        }
        return this.mActivePointerId != i ? z3 : z;
    }

    public final boolean shouldTryToDismissKeyguard() {
        return this.mKeyguardStateController.isShowing() && this.mKeyguardStateController.canDismissLockScreen() && !this.mAttemptedToDismissKeyguard;
    }

    public UdfpsController(Context context, Execution execution, LayoutInflater layoutInflater, FingerprintManager fingerprintManager, WindowManager windowManager, StatusBarStateController statusBarStateController, DelayableExecutor delayableExecutor, ShadeExpansionStateManager shadeExpansionStateManager, StatusBarKeyguardViewManager statusBarKeyguardViewManager, DumpManager dumpManager, KeyguardUpdateMonitor keyguardUpdateMonitor, FalsingManager falsingManager, PowerManager powerManager, AccessibilityManager accessibilityManager, LockscreenShadeTransitionController lockscreenShadeTransitionController, ScreenLifecycle screenLifecycle, VibratorHelper vibratorHelper, UdfpsHapticsSimulator udfpsHapticsSimulator, UdfpsShell udfpsShell, KeyguardStateController keyguardStateController, DisplayManager displayManager, Handler handler, ConfigurationController configurationController, SystemClock systemClock, UnlockedScreenOffAnimationController unlockedScreenOffAnimationController, SystemUIDialogManager systemUIDialogManager, LatencyTracker latencyTracker, ActivityTransitionAnimator activityTransitionAnimator, Executor executor, PrimaryBouncerInteractor primaryBouncerInteractor, ShadeInteractor shadeInteractor, SinglePointerTouchProcessor singlePointerTouchProcessor, SessionTracker sessionTracker, AlternateBouncerInteractor alternateBouncerInteractor, InputManager inputManager, DeviceEntryFaceAuthInteractor deviceEntryFaceAuthInteractor, UdfpsKeyguardAccessibilityDelegate udfpsKeyguardAccessibilityDelegate, SelectedUserInteractor selectedUserInteractor, KeyguardTransitionInteractor keyguardTransitionInteractor, Lazy lazy, Lazy lazy2, Lazy lazy3, UdfpsOverlayInteractor udfpsOverlayInteractor, PowerInteractor powerInteractor, CoroutineScope coroutineScope, UserActivityNotifier userActivityNotifier) {
        ScreenLifecycle.Observer observer = new ScreenLifecycle.Observer() { // from class: com.android.systemui.biometrics.UdfpsController.1
            @Override // com.android.systemui.keyguard.ScreenLifecycle.Observer
            public void onScreenTurnedOn() {
                UdfpsController.this.mScreenOn = true;
                if (UdfpsController.this.mAodInterruptRunnable != null) {
                    UdfpsController.this.mAodInterruptRunnable.run();
                    UdfpsController.this.mAodInterruptRunnable = null;
                }
            }

            @Override // com.android.systemui.keyguard.ScreenLifecycle.Observer
            public void onScreenTurnedOff() {
                UdfpsController.this.mScreenOn = false;
            }
        };
        this.mScreenObserver = observer;
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { // from class: com.android.systemui.biometrics.UdfpsController.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
                if (udfpsControllerOverlay == null || udfpsControllerOverlay.getRequestReason() == 4 || !"android.intent.action.CLOSE_SYSTEM_DIALOGS".equals(intent.getAction())) {
                    return;
                }
                if (UdfpsController.this.mStartHideUdfpsOverlay) {
                    Log.d("UdfpsController", "receive ACTION_CLOSE_SYSTEM_DIALOGS: not need to cancel the fingerprint service due to it already stop");
                    return;
                }
                String stringExtra = intent.getStringExtra("reason");
                if (stringExtra == null) {
                    stringExtra = "unknown";
                }
                Log.d("UdfpsController", "ACTION_CLOSE_SYSTEM_DIALOGS received, reason: " + stringExtra + ", mRequestReason: " + UdfpsController.this.mOverlay.getRequestReason());
                UdfpsController.this.mOverlay.cancel();
                UdfpsController.this.hideUdfpsOverlay();
            }
        };
        this.mBroadcastReceiver = broadcastReceiver;
        this.mColor = -1;
        this.alphaPathIterator = new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
        this.mShowFingerprintIconRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController.3
            @Override // java.lang.Runnable
            public void run() {
                Log.i("UdfpsController", "updateOverlay from mShowFingerprintIconRunnable, mOverlay=" + UdfpsController.this.mOverlay);
                UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
                if (udfpsControllerOverlay == null || udfpsControllerOverlay.getOverlayViewLegacy() == null) {
                    return;
                }
                UdfpsController.this.mIsIconAlphaVisible = true;
                UdfpsController.this.mOverlay.getOverlayViewLegacy().animate().cancel();
                UdfpsController.this.mOverlay.getOverlayViewLegacy().animate().alpha(1.0f).setDuration(500L).setInterpolator(UdfpsController.this.alphaPathIterator).start();
            }
        };
        this.mDismissFingerprintIconRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController.4
            @Override // java.lang.Runnable
            public void run() {
                Log.i("UdfpsController", "hideUdfpsOverlay from mDismissFingerprintIconRunnable " + UdfpsController.this.mOverlay);
                UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
                if (udfpsControllerOverlay == null || udfpsControllerOverlay.getOverlayViewLegacy() == null) {
                    return;
                }
                UdfpsController.this.mIsIconAlphaVisible = false;
                UdfpsController.this.mOverlay.getOverlayViewLegacy().animate().cancel();
                UdfpsController.this.mOverlay.getOverlayViewLegacy().animate().alpha(0.0f).setDuration(500L).setInterpolator(UdfpsController.this.alphaPathIterator).start();
            }
        };
        this.mDismissFpIconWithoutAnimRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController.5
            @Override // java.lang.Runnable
            public void run() {
                Log.i("UdfpsController", "hideUdfpsOverlay from mDismissFpIconWithoutAnimRunnable " + UdfpsController.this.mOverlay);
                UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
                if (udfpsControllerOverlay == null || udfpsControllerOverlay.getOverlayViewLegacy() == null) {
                    return;
                }
                UdfpsController.this.mIsIconAlphaVisible = false;
                UdfpsController.this.mOverlay.getOverlayViewLegacy().setAlpha(0.0f);
            }
        };
        this.mNeedShowFakeFpIcon = false;
        this.mFakeIconCallback = new IUdfpsOverlayControllerCallback.Default();
        this.mShowFakeFpIconRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController.6
            @Override // java.lang.Runnable
            public void run() {
                Log.d("UdfpsController", "mShowFakeFpIconRunnable: mOverlay=" + UdfpsController.this.mOverlay);
                UdfpsControllerOverlay udfpsControllerOverlay = UdfpsController.this.mOverlay;
                if (udfpsControllerOverlay != null && udfpsControllerOverlay.getOverlayViewLegacy() != null) {
                    UdfpsController.this.mOverlay.getOverlayViewLegacy().setAlpha(1.0f);
                } else {
                    UdfpsController udfpsController = UdfpsController.this;
                    udfpsController.showUdfpsOverlayForFake(-100L, 4, udfpsController.mFakeIconCallback);
                }
            }
        };
        this.mHideFakeFpIconRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController.7
            @Override // java.lang.Runnable
            public void run() {
                Log.d("UdfpsController", "mHideFakeFpIconRunnable: mOverlay=" + UdfpsController.this.mOverlay + ", isFakeFpIconShow=" + UdfpsController.this.isFakeFpIconShow());
                if (UdfpsController.this.isFakeFpIconShow()) {
                    if (UdfpsController.this.mOverlay.getOverlayViewLegacy() != null) {
                        UdfpsController.this.mOverlay.getOverlayViewLegacy().setAlpha(0.0f);
                    }
                    UdfpsController.this.mOverlay.hide();
                    UdfpsController.this.mOverlay = null;
                }
            }
        };
        this.mContext = context;
        this.mExecution = execution;
        this.mVibrator = vibratorHelper;
        this.mInflater = layoutInflater;
        this.mUserActivityNotifier = userActivityNotifier;
        this.mIgnoreRefreshRate = context.getResources().getBoolean(R.bool.config_ignoreVibrationsOnWirelessCharger);
        FingerprintManager fingerprintManager2 = (FingerprintManager) Preconditions.checkNotNull(fingerprintManager);
        this.mFingerprintManager = fingerprintManager2;
        this.mWindowManager = windowManager;
        this.mFgExecutor = delayableExecutor;
        this.mShadeExpansionStateManager = shadeExpansionStateManager;
        this.mStatusBarStateController = statusBarStateController;
        this.mKeyguardStateController = keyguardStateController;
        this.mKeyguardViewManager = statusBarKeyguardViewManager;
        this.mDumpManager = dumpManager;
        this.mDialogManager = systemUIDialogManager;
        this.mKeyguardUpdateMonitor = keyguardUpdateMonitor;
        this.mFalsingManager = falsingManager;
        this.mPowerManager = powerManager;
        this.mAccessibilityManager = accessibilityManager;
        this.mLockscreenShadeTransitionController = lockscreenShadeTransitionController;
        screenLifecycle.addObserver(observer);
        this.mScreenOn = screenLifecycle.getScreenState() == 2;
        this.mConfigurationController = configurationController;
        this.mSystemClock = systemClock;
        this.mUnlockedScreenOffAnimationController = unlockedScreenOffAnimationController;
        this.mLatencyTracker = latencyTracker;
        this.mActivityTransitionAnimator = activityTransitionAnimator;
        this.mSensorProps = new FingerprintSensorPropertiesInternal(-1, 0, 0, new ArrayList(), 0, false);
        this.mBiometricExecutor = executor;
        this.mPrimaryBouncerInteractor = primaryBouncerInteractor;
        this.mShadeInteractor = shadeInteractor;
        this.mAlternateBouncerInteractor = alternateBouncerInteractor;
        this.mUdfpsOverlayInteractor = udfpsOverlayInteractor;
        this.mPowerInteractor = powerInteractor;
        this.mScope = coroutineScope;
        this.mInputManager = inputManager;
        this.mUdfpsKeyguardAccessibilityDelegate = udfpsKeyguardAccessibilityDelegate;
        this.mSelectedUserInteractor = selectedUserInteractor;
        this.mKeyguardTransitionInteractor = keyguardTransitionInteractor;
        this.mTouchProcessor = singlePointerTouchProcessor;
        this.mSessionTracker = sessionTracker;
        this.mDeviceEntryUdfpsTouchOverlayViewModel = lazy;
        this.mDefaultUdfpsTouchOverlayViewModel = lazy2;
        this.mPromptUdfpsTouchOverlayViewModel = lazy3;
        dumpManager.registerDumpable("UdfpsController", this);
        this.mOrientationListener = new BiometricDisplayListener(context, displayManager, handler, BiometricDisplayListener.SensorType.UnderDisplayFingerprint.INSTANCE, new Function0() { // from class: com.android.systemui.biometrics.UdfpsController$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                return this.f$0.lambda$new$0();
            }
        });
        this.mDeviceEntryFaceAuthInteractor = deviceEntryFaceAuthInteractor;
        UdfpsOverlayController udfpsOverlayController = new UdfpsOverlayController();
        fingerprintManager2.setUdfpsOverlayController(udfpsOverlayController);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.CLOSE_SYSTEM_DIALOGS");
        context.registerReceiver(broadcastReceiver, intentFilter, 2);
        udfpsHapticsSimulator.setUdfpsController(this);
        udfpsShell.setUdfpsOverlayController(udfpsOverlayController);
        this.mHandler = handler;
    }

    public final /* synthetic */ Unit lambda$new$0() {
        Runnable runnable = this.mAuthControllerUpdateUdfpsLocation;
        if (runnable != null) {
            runnable.run();
        }
        return Unit.INSTANCE;
    }

    public final void redrawOverlay() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            hideUdfpsOverlay();
            showUdfpsOverlay(udfpsControllerOverlay);
        }
    }

    public final void showUdfpsOverlay(UdfpsControllerOverlay udfpsControllerOverlay) {
        this.mExecution.assertIsMainThread();
        if (this.mOverlay != null) {
            Log.d("UdfpsController", "showUdfpsOverlay | the overlay is already showing");
            return;
        }
        this.mOverlay = udfpsControllerOverlay;
        udfpsControllerOverlay.setShadeExpansionStateManager(this.mShadeExpansionStateManager);
        this.mOverlay.setController(this);
        Log.d("UdfpsController", "showUdfpsOverlay: mOverlay = " + this.mOverlay);
        this.mStartHideUdfpsOverlay = false;
        int requestReason = udfpsControllerOverlay.getRequestReason();
        if (requestReason == 4 && !this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning() && udfpsControllerOverlay.getRequestId() != -100) {
            Log.d("UdfpsController", "Attempting to showUdfpsOverlay when fingerprint detection isn't running on keyguard. Skip show.");
            return;
        }
        if (udfpsControllerOverlay.show(this.mOverlayParams)) {
            Log.d("UdfpsController", "showUdfpsOverlay | adding window reason=" + requestReason);
            if (shouldDismissFingerprintIcon()) {
                dismissFpIconWithoutAnim();
            }
            this.mOnFingerDown = false;
            this.mAttemptedToDismissKeyguard = false;
            this.mOrientationListener.enable();
            FingerprintManager fingerprintManager = this.mFingerprintManager;
            if (fingerprintManager != null) {
                fingerprintManager.onUdfpsUiEvent(1, udfpsControllerOverlay.getRequestId(), this.mSensorProps.sensorId);
                return;
            }
            return;
        }
        Log.d("UdfpsController", "showUdfpsOverlay | the overlay is already showing");
    }

    public final void hideUdfpsOverlay() {
        this.mExecution.assertIsMainThread();
        Log.d("UdfpsController", "hideUdfpsOverlay: mOverLay = " + this.mOverlay);
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            View touchOverlay = udfpsControllerOverlay.getTouchOverlay();
            if (touchOverlay != null) {
                onFingerUp(this.mOverlay.getRequestId(), touchOverlay);
            }
            boolean zHide = this.mOverlay.hide();
            this.mKeyguardViewManager.hideAlternateBouncer(true);
            Log.v("UdfpsController", "hideUdfpsOverlay | removing window: " + zHide);
        } else {
            Log.v("UdfpsController", "hideUdfpsOverlay | the overlay is already hidden");
        }
        this.mOverlay = null;
        this.mOrientationListener.disable();
    }

    public final void unconfigureDisplay(View view) {
        UdfpsDisplayModeProvider udfpsDisplayModeProvider;
        if (isOptical() && (udfpsDisplayModeProvider = this.mUdfpsDisplayMode) != null) {
            udfpsDisplayModeProvider.disable(null);
        }
    }

    public void onAodInterrupt(final int i, final int i2, final float f, final float f2) {
        if (this.mIsAodInterruptActive) {
            return;
        }
        if (!this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning()) {
            if (this.mFalsingManager.isFalseLongTap(1)) {
                Log.v("UdfpsController", "aod lock icon long-press rejected by the falsing manager.");
                return;
            }
            this.mKeyguardViewManager.showPrimaryBouncer(true, "UdfpsController#onAodInterrupt");
            UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
            if (udfpsControllerOverlay != null && udfpsControllerOverlay.getTouchOverlay() != null) {
                this.mVibrator.performHapticFeedback(this.mOverlay.getTouchOverlay(), 0);
                return;
            } else {
                Log.e("UdfpsController", "No haptics played. Could not obtain overlay view to performvibration. Either the controller overlay is null or has no view");
                return;
            }
        }
        UdfpsControllerOverlay udfpsControllerOverlay2 = this.mOverlay;
        final long requestId = udfpsControllerOverlay2 != null ? udfpsControllerOverlay2.getRequestId() : -1L;
        this.mAodInterruptRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$onAodInterrupt$1(requestId, i, i2, f2, f);
            }
        };
        if (isScreenOffUnlockEnabled() || this.mScreenOn) {
            this.mAodInterruptRunnable.run();
            this.mAodInterruptRunnable = null;
        }
    }

    public final /* synthetic */ void lambda$onAodInterrupt$1(long j, int i, int i2, float f, float f2) {
        this.mIsAodInterruptActive = true;
        this.mCancelAodFingerUpAction = this.mFgExecutor.executeDelayed(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.tryAodSendFingerUp();
            }
        }, 1000L);
        onFingerDown(j, -1, i, i2, f, f2, 0.0f, 0L, 0L, true);
    }

    public final boolean isScreenOffUnlockEnabled() {
        return this.mContext.getResources().getBoolean(R.bool.config_secondaryBuiltInDisplayIsRound) && Settings.Secure.getIntForUser(this.mContext.getContentResolver(), "screen_off_udfps_enabled", 0, this.mContext.getUserId()) != 0;
    }

    public void addCallback(Callback callback) {
        this.mCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        this.mCallbacks.remove(callback);
    }

    public void tryAodSendFingerUp() {
        if (this.mIsAodInterruptActive) {
            cancelAodSendFingerUpAction();
            UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
            if (udfpsControllerOverlay == null || udfpsControllerOverlay.getTouchOverlay() == null) {
                return;
            }
            onFingerUp(this.mOverlay.getRequestId(), this.mOverlay.getTouchOverlay());
        }
    }

    public void cancelAodSendFingerUpAction() {
        this.mIsAodInterruptActive = false;
        Runnable runnable = this.mCancelAodFingerUpAction;
        if (runnable != null) {
            runnable.run();
            this.mCancelAodFingerUpAction = null;
        }
    }

    public final boolean isOptical() {
        return this.mSensorProps.sensorType == 3;
    }

    public final boolean isUltrasonic() {
        return this.mSensorProps.sensorType == 2;
    }

    public boolean isFingerDown() {
        return this.mOnFingerDown;
    }

    /* JADX INFO: renamed from: dispatchOnUiReady, reason: merged with bridge method [inline-methods] */
    public final void lambda$onFingerDown$2(long j) {
        this.mFingerprintManager.onUdfpsUiEvent(2, j, this.mSensorProps.sensorId);
        this.mLatencyTracker.onActionEnd(14);
    }

    public final void onFingerDown(final long j, int i, float f, float f2, float f3, float f4, float f5, long j2, long j3, boolean z) {
        this.mExecution.assertIsMainThread();
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay == null) {
            Log.w("UdfpsController", "Null request in onFingerDown");
            return;
        }
        if (!udfpsControllerOverlay.matchesRequestId(j)) {
            Log.w("UdfpsController", "Mismatched fingerDown: " + j + " current: " + this.mOverlay.getRequestId());
            return;
        }
        if (isOptical()) {
            this.mLatencyTracker.onActionStart(14);
        }
        this.mPowerManager.userActivity(this.mSystemClock.uptimeMillis(), 2, 0);
        if (!this.mOnFingerDown) {
            playStartHaptic();
            this.mDeviceEntryFaceAuthInteractor.onUdfpsSensorTouched();
        }
        this.mOnFingerDown = true;
        this.mFingerprintManager.onPointerDown(j, this.mSensorProps.sensorId, i, f, f2, f3, f4, f5, j2, j3, z);
        Trace.endAsyncSection("UdfpsController.e2e.onPointerDown", 0);
        if (this.mOverlay.getTouchOverlay() != null && isOptical()) {
            if (this.mIgnoreRefreshRate) {
                lambda$onFingerDown$2(j);
            } else {
                this.mUdfpsDisplayMode.enable(new Runnable() { // from class: com.android.systemui.biometrics.UdfpsController$$ExternalSyntheticLambda1
                    @Override // java.lang.Runnable
                    public final void run() {
                        this.f$0.lambda$onFingerDown$2(j);
                    }
                });
            }
        }
        if (isOptical()) {
            Iterator it = this.mCallbacks.iterator();
            while (it.hasNext()) {
                ((Callback) it.next()).onFingerDown();
            }
        }
    }

    public final void onFingerUp(long j, View view) {
        onFingerUp(j, view, -1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0L, 0L, false);
    }

    public final void onFingerUp(long j, View view, int i, float f, float f2, float f3, float f4, float f5, long j2, long j3, boolean z) {
        this.mExecution.assertIsMainThread();
        this.mActivePointerId = -1;
        this.mAcquiredReceived = false;
        if (this.mOnFingerDown) {
            this.mFingerprintManager.onPointerUp(j, this.mSensorProps.sensorId, i, f, f2, f3, f4, f5, j2, j3, z);
            if (isOptical()) {
                Iterator it = this.mCallbacks.iterator();
                while (it.hasNext()) {
                    ((Callback) it.next()).onFingerUp();
                }
            }
        }
        this.mOnFingerDown = false;
        unconfigureDisplay(view);
        cancelAodSendFingerUpAction();
    }

    public boolean shouldDismissFingerprintIcon() {
        if (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isGoingToSleep()) {
            if (!((CommonUtils) Dependency.get(CommonUtils.class)).supportAndEnterAmbientMode() && ((!((LiveWallpaperController) Dependency.get(LiveWallpaperController.class)).hasMeizuLiveWallpaper() || ((CommonUtils) Dependency.get(CommonUtils.class)).supportsAmbientMode()) && !((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().isAODUseNewClockStyle())) {
                return true;
            }
            if (!((AODStateController) Dependency.get(AODStateController.class)).shouldShowAODView() && ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).fingerprintMakeEnterAOD()) {
                return true;
            }
        }
        return (((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isFpShowInAOD() || ((KeyguardUpdateMonitor) Dependency.get(KeyguardUpdateMonitor.class)).isRealInteractive()) ? false : true;
    }

    public void dismissFingerprintIcon() {
        Log.d("UdfpsController", "dismissFingerprintIcon: ", new Exception());
        Handler handler = this.mHandler;
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(this.mShowFingerprintIconRunnable);
        this.mHandler.removeCallbacks(this.mDismissFpIconWithoutAnimRunnable);
        this.mHandler.post(this.mDismissFingerprintIconRunnable);
    }

    public void dismissFpIconWithoutAnim() {
        Log.d("UdfpsController", "dismissFpIconWithoutAnim: ");
        Handler handler = this.mHandler;
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(this.mShowFingerprintIconRunnable);
        this.mHandler.removeCallbacks(this.mDismissFingerprintIconRunnable);
        this.mHandler.post(this.mDismissFpIconWithoutAnimRunnable);
    }

    public void showFingerprintIcon() {
        Handler handler = this.mHandler;
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(this.mDismissFingerprintIconRunnable);
        this.mHandler.post(this.mShowFingerprintIconRunnable);
    }

    public boolean isIconAlphaVisible() {
        return this.mIsIconAlphaVisible;
    }

    public void updateFakeFpAndFingerprintIconColor(int i) {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.updateUdfpsIconColor(i);
        }
        this.mColor = i;
    }

    public void updateFakeFpAndFingerprintIconState(boolean z) {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.updateUdfpsIconState(z);
        }
    }

    public void startLeftRightIconScaleAnimator() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.startLeftRightIconScaleAnimator();
        }
    }

    public void startLeftRightIconHideAnimForFakeFp(long j) {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.startLeftRightIconScaleHideAnimator(j);
        }
    }

    public void updateLeftRightIconAlphaForFakeFp(float f, float f2) {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.updateLeftRightIconAlpha(f, f2);
        }
    }

    public boolean isWithinSensorArea(float f, float f2) {
        boolean zContains;
        boolean zIsSupportedWideFp = FlymeWideFingerPrintUtils.isSupportedWideFp();
        if (zIsSupportedWideFp) {
            zContains = FlymeWideFingerPrintUtils.isInLeftOrRightOrCenterSensorArea((int) f, (int) f2);
        } else {
            zContains = this.mOverlayParams.getSensorBounds().contains((int) f, (int) f2);
        }
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        boolean z = false;
        if (udfpsControllerOverlay != null && udfpsControllerOverlay.getAnimationViewController() != null && !this.mOverlay.getAnimationViewController().shouldPauseAuth() && zContains) {
            z = true;
        }
        if (!z && isFakeFpIconShow() && this.mOverlay.getAnimationViewController() != null) {
            z = zContains;
        }
        Log.d("UdfpsController", "isWithinSensorArea() isWithinArea:" + z + " isContainXY:" + zContains + " supportedWideFp:" + zIsSupportedWideFp + " x:" + f + " y:" + f2 + " sensorBounds:" + this.mOverlayParams.getSensorBounds());
        return z;
    }

    public boolean isWithinPalmRejectionArea(float f, float f2) {
        boolean zContains;
        boolean zIsSupportedWideFp = FlymeWideFingerPrintUtils.isSupportedWideFp();
        if (zIsSupportedWideFp) {
            zContains = FlymeWideFingerPrintUtils.isInLockPalmRejectionArea((int) f, (int) f2);
        } else {
            if (this.mPreventRect == null) {
                int iCenterX = this.mOverlayParams.getSensorBounds().centerX();
                int iCenterY = this.mOverlayParams.getSensorBounds().centerY();
                int iConvertDpToPx = DisplayUtils.convertDpToPx(50.0f, this.mContext);
                this.mPreventRect = new Rect(iCenterX - iConvertDpToPx, iCenterY - iConvertDpToPx, iCenterX + iConvertDpToPx, iCenterY + iConvertDpToPx);
            }
            Log.d("UdfpsController", "isWithinPalmRejectionArea: preventRect = " + this.mPreventRect);
            zContains = this.mPreventRect.contains((int) f, (int) f2);
        }
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        boolean z = false;
        if (udfpsControllerOverlay != null && udfpsControllerOverlay.getAnimationViewController() != null && !this.mOverlay.getAnimationViewController().shouldPauseAuth() && zContains) {
            z = true;
        }
        if (!z && isFakeFpIconShow() && this.mOverlay.getAnimationViewController() != null) {
            z = zContains;
        }
        Log.d("UdfpsController", "isWithinPalmRejectionArea() isWithinArea:" + z + " isContainXY:" + zContains + " supportedWideFp:" + zIsSupportedWideFp + " x:" + f + " y:" + f2 + " sensorBounds:" + this.mOverlayParams.getSensorBounds());
        return z;
    }

    public void showFingerprintDownAnim() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.showFingerprintDownAnim();
        }
    }

    public void showFingerprintDownAnimLeft() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.showFingerprintDownAnimLeft();
        }
    }

    public void showFingerprintDownAnimRight() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.showFingerprintDownAnimRight();
        }
    }

    public void hideFingerprintDownAnim() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.hideFingerprintDownAnim();
        }
    }

    public void hideFingerprintDownAnimLeft() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.hideFingerprintDownAnimLeft();
        }
    }

    public void hideFingerprintDownAnimRight() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            udfpsControllerOverlay.hideFingerprintDownAnimRight();
        }
    }

    public void hideFpIcon() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            int requestReason = udfpsControllerOverlay.getRequestReason();
            Log.d("UdfpsController", "hideFpIcon() requestReason:" + requestReason);
            if (requestReason == 3 || requestReason == 5) {
                Log.d("UdfpsController", "hideFpIcon");
                udfpsControllerOverlay.hide();
            }
        }
    }

    public void showFpIcon() {
        UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
        if (udfpsControllerOverlay != null) {
            int requestReason = udfpsControllerOverlay.getRequestReason();
            Log.d("UdfpsController", "showFpIcon() requestReason:" + requestReason);
            if (requestReason == 3 || requestReason == 5) {
                Log.d("UdfpsController", "showFpIcon");
                hideUdfpsOverlay();
                showUdfpsOverlay(udfpsControllerOverlay);
            }
        }
    }

    public int getColor() {
        return this.mColor;
    }

    public Rect getUdfpsBound() {
        if (FlymeWideFingerPrintUtils.isSupportedWideFp()) {
            return FlymeWideFingerPrintUtils.getCenterBounds();
        }
        return this.mOverlayParams.getSensorBounds();
    }

    public final void setNeedShowFakeFpIcon(boolean z) {
        Log.d("UdfpsController", "setNeedShowFakeFpIcon=" + z);
        this.mNeedShowFakeFpIcon = z;
    }

    public void resetNeedShowFakeFpIcon() {
        setNeedShowFakeFpIcon(false);
    }

    public boolean isFakeFpIconShow() {
        return (this.mOverlay == null || this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning() || this.mOverlay.getRequestReason() != 4) ? false : true;
    }

    public void showFakeFpIcon() {
        if (this.mHandler == null) {
            return;
        }
        Log.d("UdfpsController", "showFakeFpIcon, mOverlay=" + this.mOverlay + ", isFingerprintDetectionRunning=" + this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning());
        if (this.mOverlay != null) {
            setNeedShowFakeFpIcon(true);
        }
        this.mHandler.removeCallbacks(this.mHideFakeFpIconRunnable);
        if (this.mHandler.hasCallbacks(this.mShowFakeFpIconRunnable)) {
            return;
        }
        this.mHandler.post(this.mShowFakeFpIconRunnable);
    }

    public void hideFakeFpIcon() {
        if (!isFakeFpIconShow() || this.mHandler == null) {
            return;
        }
        Log.d("UdfpsController", "hideFakeFpIcon, mOverlay=" + this.mOverlay + ", isFingerprintDetectionRunning=" + this.mKeyguardUpdateMonitor.isFingerprintDetectionRunning());
        this.mHandler.removeCallbacks(this.mShowFakeFpIconRunnable);
        if (this.mHandler.hasCallbacks(this.mHideFakeFpIconRunnable)) {
            return;
        }
        this.mHandler.post(this.mHideFakeFpIconRunnable);
    }

    public final void showUdfpsOverlayForFake(final long j, int i, IUdfpsOverlayControllerCallback iUdfpsOverlayControllerCallback) {
        Log.i("UdfpsController", "showUdfpsOverlayForFake");
        this.mUdfpsOverlayInteractor.setRequestId(j);
        showUdfpsOverlay(new UdfpsControllerOverlay(this.mContext, this.mInflater, this.mWindowManager, this.mAccessibilityManager, this.mStatusBarStateController, this.mKeyguardViewManager, this.mKeyguardUpdateMonitor, this.mDialogManager, this.mDumpManager, this.mLockscreenShadeTransitionController, this.mConfigurationController, this.mKeyguardStateController, this.mUnlockedScreenOffAnimationController, this.mUdfpsDisplayMode, j, i, iUdfpsOverlayControllerCallback, new Function2() { // from class: com.android.systemui.biometrics.UdfpsController$$ExternalSyntheticLambda2
            @Override // kotlin.jvm.functions.Function2
            public final Object invoke(Object obj, Object obj2) {
                return this.f$0.lambda$showUdfpsOverlayForFake$3(j, (View) obj, (MotionEvent) obj2);
            }
        }, this.mActivityTransitionAnimator, this.mPrimaryBouncerInteractor, this.mAlternateBouncerInteractor, this.mUdfpsKeyguardAccessibilityDelegate, this.mKeyguardTransitionInteractor, this.mSelectedUserInteractor, this.mDeviceEntryUdfpsTouchOverlayViewModel, this.mDefaultUdfpsTouchOverlayViewModel, this.mPromptUdfpsTouchOverlayViewModel, this.mShadeInteractor, this.mUdfpsOverlayInteractor, this.mPowerInteractor, this.mScope));
    }

    public final /* synthetic */ Boolean lambda$showUdfpsOverlayForFake$3(long j, View view, MotionEvent motionEvent) {
        return Boolean.valueOf(onTouch(j, motionEvent));
    }

    public void updateFakeFpIconAlpha(float f) {
        if (isFakeFpIconShow() && this.mStatusBarStateController.getState() == 1) {
            if (f == 0.0f || f == 1.0f) {
                if (SystemUIDebugConfig.DEBUG_FINGPRINT) {
                    Log.d("UdfpsController", "updateFakeFpIconAlpha: " + f);
                } else if (f == 0.0f) {
                    Log.d("UdfpsController", "updateFakeFpIconAlpha: " + f);
                }
            }
            UdfpsControllerOverlay udfpsControllerOverlay = this.mOverlay;
            if (udfpsControllerOverlay != null && udfpsControllerOverlay.getOverlayViewLegacy() != null && this.mKeyguardUpdateMonitor.shouldShowFakeFPIcon()) {
                this.mOverlay.getOverlayViewLegacy().setAlpha(f);
            } else {
                hideFakeFpIcon();
            }
        }
    }
}
