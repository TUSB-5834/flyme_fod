package com.android.systemui.biometrics;

import android.R;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.SensorPrivacyManager;
import android.hardware.biometrics.BiometricStateListener;
import android.hardware.biometrics.IBiometricContextListener;
import android.hardware.biometrics.IBiometricSysuiReceiver;
import android.hardware.biometrics.PromptInfo;
import android.hardware.display.DisplayManager;
import android.hardware.face.FaceManager;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.hardware.face.IFaceAuthenticatorsRegisteredCallback;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.hardware.fingerprint.IFingerprintAuthenticatorsRegisteredCallback;
import android.hardware.fingerprint.IUdfpsRefreshRateRequestCallback;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.RotationUtils;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.WindowManager;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.os.SomeArgs;
import com.android.internal.widget.LockPatternUtils;
import com.android.systemui.CoreStartable;
import com.android.systemui.biometrics.domain.interactor.LogContextInteractor;
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams;
import com.android.systemui.biometrics.ui.viewmodel.PromptViewModel;
import com.android.systemui.doze.DozeReceiver;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.keyguard.data.repository.BiometricType;
import com.android.systemui.res.R$dimen;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.VibratorHelper;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.util.concurrency.Execution;
import com.android.systemui.utils.windowmanager.WindowManagerProvider;
import com.google.android.msdl.domain.MSDLPlayer;
import dagger.Lazy;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Provider;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;

/* JADX INFO: loaded from: classes2.dex */
public class AuthController implements CoreStartable, ConfigurationController.ConfigurationListener, CommandQueue.Callbacks, AuthDialogCallback, DozeReceiver {
    public final ActivityTaskManager mActivityTaskManager;
    public boolean mAllFingerprintAuthenticatorsRegistered;
    public final CoroutineScope mApplicationCoroutineScope;
    public final DelayableExecutor mBackgroundExecutor;
    final BroadcastReceiver mBroadcastReceiver;
    public final CommandQueue mCommandQueue;
    public final Context mContext;
    public final Provider mCredentialViewModelProvider;
    AuthContainerView mCurrentDialog;
    public SomeArgs mCurrentDialogArgs;
    public final Display mDisplay;
    public final DisplayManager mDisplayManager;
    public final Execution mExecution;
    public final SparseBooleanArray mFaceEnrolledForUser;
    public final FaceManager mFaceManager;
    public List mFaceProps;
    public final FingerprintManager mFingerprintManager;
    public Point mFingerprintSensorLocation;
    public List mFpProps;
    public final Handler mHandler;
    public final InteractionJankMonitor mInteractionJankMonitor;
    public final LockPatternUtils mLockPatternUtils;
    public final Lazy mLogContextInteractor;
    public final MSDLPlayer mMSDLPlayer;
    final BiometricDisplayListener mOrientationListener;
    public final Provider mPromptSelectorInteractor;
    public final Provider mPromptViewModelProvider;
    IBiometricSysuiReceiver mReceiver;
    public final SensorPrivacyManager mSensorPrivacyManager;
    public final SparseBooleanArray mSfpsEnrolledForUser;
    public List mSidefpsProps;
    public Rect mUdfpsBounds;
    public UdfpsController mUdfpsController;
    public final Provider mUdfpsControllerFactory;
    public final SparseBooleanArray mUdfpsEnrolledForUser;
    public Lazy mUdfpsLogger;
    public UdfpsOverlayParams mUdfpsOverlayParams;
    public List mUdfpsProps;
    public IUdfpsRefreshRateRequestCallback mUdfpsRefreshRateRequestCallback;
    public final UdfpsUtils mUdfpsUtils;
    public final UserManager mUserManager;
    public final VibratorHelper mVibratorHelper;
    public final WakefulnessLifecycle mWakefulnessLifecycle;
    public final WindowManager mWindowManager;
    public final WindowManagerProvider mWindowManagerProvider;
    public Job mBiometricContextListenerJob = null;
    public float mScaleFactor = 1.0f;
    public final Set mCallbacks = new HashSet();
    public final Map mFpEnrolledForUser = new HashMap();
    public final DisplayInfo mCachedDisplayInfo = new DisplayInfo();
    final TaskStackListener mTaskStackListener = new AnonymousClass1();

    public interface Callback {
        default void onAllAuthenticatorsRegistered(int i) {
        }

        default void onBiometricPromptDismissed() {
        }

        default void onBiometricPromptShown() {
        }

        default void onEnrollmentsChanged(int i) {
        }

        default void onEnrollmentsChanged(BiometricType biometricType, int i, boolean z) {
        }

        default void onFingerprintLocationChanged() {
        }

        default void onUdfpsLocationChanged(UdfpsOverlayParams udfpsOverlayParams) {
        }
    }

    public interface ScaleFactorProvider {
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.AuthController$1, reason: invalid class name */
    class AnonymousClass1 extends TaskStackListener {
        public AnonymousClass1() {
        }

        public void onTaskStackChanged() {
            if (AuthController.this.isOwnerInBackground()) {
                Handler handler = AuthController.this.mHandler;
                final AuthController authController = AuthController.this;
                handler.post(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$1$$ExternalSyntheticLambda0
                    @Override // java.lang.Runnable
                    public final void run() {
                        authController.cancelIfOwnerIsNotInForeground();
                    }
                });
            }
        }
    }

    public final void closeDialog(String str) {
        closeDialog(3, str);
    }

    public final void closeDialog(int i, String str) {
        if (isShowing()) {
            Log.i("AuthController", "Close BP, reason :" + str);
            this.mCurrentDialog.dismissWithoutCallback(true);
            this.mCurrentDialog = null;
            Iterator it = this.mCallbacks.iterator();
            while (it.hasNext()) {
                ((Callback) it.next()).onBiometricPromptDismissed();
            }
            try {
                IBiometricSysuiReceiver iBiometricSysuiReceiver = this.mReceiver;
                if (iBiometricSysuiReceiver != null) {
                    iBiometricSysuiReceiver.onDialogDismissed(i, (byte[]) null);
                    this.mReceiver = null;
                }
            } catch (RemoteException e) {
                Log.e("AuthController", "Remote exception", e);
            }
        }
    }

    public final boolean isOwnerInBackground() {
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView == null) {
            return false;
        }
        String opPackageName = authContainerView.getOpPackageName();
        boolean zIsSystemAppOrInBackground = Utils.isSystemAppOrInBackground(this.mActivityTaskManager, this.mContext, opPackageName, this.mCurrentDialog.getClassNameIfItIsConfirmDeviceCredentialActivity());
        if (zIsSystemAppOrInBackground) {
            Log.w("AuthController", "Evicting client due to top activity is not : " + opPackageName);
        }
        return zIsSystemAppOrInBackground;
    }

    public final void cancelIfOwnerIsNotInForeground() {
        this.mExecution.assertIsMainThread();
        closeDialog("owner not in foreground");
    }

    public boolean areAllFingerprintAuthenticatorsRegistered() {
        return this.mAllFingerprintAuthenticatorsRegistered;
    }

    public final void handleAllFingerprintAuthenticatorsRegistered(List list) {
        this.mExecution.assertIsMainThread();
        Log.d("AuthController", "handleAllFingerprintAuthenticatorsRegistered | sensors: " + Arrays.toString(list.toArray()));
        this.mAllFingerprintAuthenticatorsRegistered = true;
        this.mFpProps = list;
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        for (FingerprintSensorPropertiesInternal fingerprintSensorPropertiesInternal : this.mFpProps) {
            if (fingerprintSensorPropertiesInternal.isAnyUdfpsType()) {
                arrayList.add(fingerprintSensorPropertiesInternal);
            }
            if (fingerprintSensorPropertiesInternal.isAnySidefpsType()) {
                arrayList2.add(fingerprintSensorPropertiesInternal);
            }
        }
        if (arrayList.isEmpty()) {
            arrayList = null;
        }
        this.mUdfpsProps = arrayList;
        if (arrayList != null) {
            UdfpsController udfpsController = (UdfpsController) this.mUdfpsControllerFactory.get();
            this.mUdfpsController = udfpsController;
            udfpsController.addCallback(new UdfpsController.Callback() { // from class: com.android.systemui.biometrics.AuthController.3
                @Override // com.android.systemui.biometrics.UdfpsController.Callback
                public void onFingerUp() {
                }

                @Override // com.android.systemui.biometrics.UdfpsController.Callback
                public void onFingerDown() {
                    AuthContainerView authContainerView = AuthController.this.mCurrentDialog;
                    if (authContainerView != null) {
                        authContainerView.onPointerDown();
                    }
                }
            });
            this.mUdfpsController.setAuthControllerUpdateUdfpsLocation(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.updateUdfpsLocation();
                }
            });
            this.mUdfpsController.setUdfpsDisplayMode(new UdfpsDisplayMode(this.mContext, this.mExecution, this, (UdfpsLogger) this.mUdfpsLogger.get()));
            this.mUdfpsBounds = ((FingerprintSensorPropertiesInternal) this.mUdfpsProps.get(0)).getLocation().getRect();
        }
        if (arrayList2.isEmpty()) {
            arrayList2 = null;
        }
        this.mSidefpsProps = arrayList2;
        this.mFingerprintManager.registerBiometricStateListener(new AnonymousClass4());
        updateSensorLocations();
        Iterator it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            ((Callback) it.next()).onAllAuthenticatorsRegistered(2);
        }
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.AuthController$4, reason: invalid class name */
    class AnonymousClass4 extends BiometricStateListener {
        public AnonymousClass4() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onEnrollmentsChanged$0(int i, int i2, boolean z) {
            AuthController.this.handleEnrollmentsChanged(2, i, i2, z);
        }

        public void onEnrollmentsChanged(final int i, final int i2, final boolean z) {
            AuthController.this.mHandler.post(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$4$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onEnrollmentsChanged$0(i, i2, z);
                }
            });
        }
    }

    public final void handleAllFaceAuthenticatorsRegistered(List list) {
        this.mExecution.assertIsMainThread();
        Log.d("AuthController", "handleAllFaceAuthenticatorsRegistered | sensors: " + Arrays.toString(list.toArray()));
        List list2 = this.mFaceProps;
        if (list2 == null || list2.isEmpty()) {
            this.mFaceProps = list;
        }
        Log.d("AuthController", "handleAllFaceAuthenticatorsRegistered | mFaceProps: " + Arrays.toString(list.toArray()));
        this.mFaceManager.registerBiometricStateListener(new AnonymousClass5());
        Iterator it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            ((Callback) it.next()).onAllAuthenticatorsRegistered(8);
        }
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.AuthController$5, reason: invalid class name */
    class AnonymousClass5 extends BiometricStateListener {
        public AnonymousClass5() {
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onEnrollmentsChanged$0(int i, int i2, boolean z) {
            AuthController.this.handleEnrollmentsChanged(8, i, i2, z);
        }

        public void onEnrollmentsChanged(final int i, final int i2, final boolean z) {
            AuthController.this.mHandler.post(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$5$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onEnrollmentsChanged$0(i, i2, z);
                }
            });
        }
    }

    public final void handleEnrollmentsChanged(int i, int i2, int i3, boolean z) {
        this.mExecution.assertIsMainThread();
        Log.d("AuthController", "handleEnrollmentsChanged, userId: " + i2 + ", sensorId: " + i3 + ", hasEnrollments: " + z);
        BiometricType biometricType = BiometricType.UNKNOWN;
        List<FingerprintSensorPropertiesInternal> list = this.mFpProps;
        if (list != null) {
            for (FingerprintSensorPropertiesInternal fingerprintSensorPropertiesInternal : list) {
                if (fingerprintSensorPropertiesInternal.sensorId == i3) {
                    this.mFpEnrolledForUser.put(Integer.valueOf(i2), Boolean.valueOf(z));
                    if (fingerprintSensorPropertiesInternal.isAnyUdfpsType()) {
                        biometricType = BiometricType.UNDER_DISPLAY_FINGERPRINT;
                        this.mUdfpsEnrolledForUser.put(i2, z);
                        break;
                    } else if (fingerprintSensorPropertiesInternal.isAnySidefpsType()) {
                        biometricType = BiometricType.SIDE_FINGERPRINT;
                        this.mSfpsEnrolledForUser.put(i2, z);
                        break;
                    } else {
                        if (fingerprintSensorPropertiesInternal.sensorType != 1) {
                            break;
                        }
                        biometricType = BiometricType.REAR_FINGERPRINT;
                        break;
                    }
                }
            }
        }
        List<FaceSensorPropertiesInternal> list2 = this.mFaceProps;
        if (list2 == null) {
            Log.d("AuthController", "handleEnrollmentsChanged, mFaceProps is null");
        } else {
            for (FaceSensorPropertiesInternal faceSensorPropertiesInternal : list2) {
                Log.d("AuthController", "isUnlockWithFacePossible: prop.sensorId=" + faceSensorPropertiesInternal.sensorId + ", sensorId=" + i3);
                if (faceSensorPropertiesInternal.sensorId == i3) {
                    this.mFaceEnrolledForUser.put(i2, z);
                    biometricType = BiometricType.FACE;
                    break;
                }
            }
        }
        for (Callback callback : this.mCallbacks) {
            callback.onEnrollmentsChanged(i);
            callback.onEnrollmentsChanged(biometricType, i2, z);
        }
    }

    public void addCallback(Callback callback) {
        this.mCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        this.mCallbacks.remove(callback);
    }

    @Override // com.android.systemui.doze.DozeReceiver
    public void dozeTimeTick() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.dozeTimeTick();
        }
    }

    @Override // com.android.systemui.biometrics.AuthDialogCallback
    public void onTryAgainPressed(long j) {
        IBiometricSysuiReceiver currentReceiver = getCurrentReceiver(j);
        if (currentReceiver == null) {
            Log.w("AuthController", "Skip onTryAgainPressed");
            return;
        }
        try {
            currentReceiver.onTryAgainPressed();
        } catch (RemoteException e) {
            Log.e("AuthController", "RemoteException when handling try again", e);
        }
    }

    @Override // com.android.systemui.biometrics.AuthDialogCallback
    public void onDeviceCredentialPressed(long j) {
        IBiometricSysuiReceiver currentReceiver = getCurrentReceiver(j);
        if (currentReceiver == null) {
            Log.w("AuthController", "Skip onDeviceCredentialPressed");
            return;
        }
        try {
            currentReceiver.onDeviceCredentialPressed();
        } catch (RemoteException e) {
            Log.e("AuthController", "RemoteException when handling credential button", e);
        }
    }

    @Override // com.android.systemui.biometrics.AuthDialogCallback
    public void onSystemEvent(int i, long j) {
        IBiometricSysuiReceiver currentReceiver = getCurrentReceiver(j);
        if (currentReceiver == null) {
            Log.w("AuthController", "Skip onSystemEvent");
            return;
        }
        try {
            currentReceiver.onSystemEvent(i);
        } catch (RemoteException e) {
            Log.e("AuthController", "RemoteException when sending system event", e);
        }
    }

    @Override // com.android.systemui.biometrics.AuthDialogCallback
    public void onDialogAnimatedIn(long j, boolean z) {
        IBiometricSysuiReceiver currentReceiver = getCurrentReceiver(j);
        if (currentReceiver == null) {
            Log.w("AuthController", "Skip onDialogAnimatedIn");
            return;
        }
        try {
            currentReceiver.onDialogAnimatedIn(z);
        } catch (RemoteException e) {
            Log.e("AuthController", "RemoteException when sending onDialogAnimatedIn", e);
        }
    }

    @Override // com.android.systemui.biometrics.AuthDialogCallback
    public void onStartFingerprintNow(long j) {
        IBiometricSysuiReceiver currentReceiver = getCurrentReceiver(j);
        if (currentReceiver == null) {
            Log.e("AuthController", "onStartUdfpsNow: Receiver is null");
            return;
        }
        try {
            currentReceiver.onStartFingerprintNow();
        } catch (RemoteException e) {
            Log.e("AuthController", "RemoteException when sending onDialogAnimatedIn", e);
        }
    }

    public final IBiometricSysuiReceiver getCurrentReceiver(long j) {
        if (!isRequestIdValid(j)) {
            return null;
        }
        if (this.mReceiver == null) {
            Log.w("AuthController", "getCurrentReceiver: Receiver is null");
        }
        return this.mReceiver;
    }

    public final boolean isRequestIdValid(long j) {
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView == null) {
            Log.w("AuthController", "shouldNotifyReceiver: dialog already gone");
            return false;
        }
        if (j == authContainerView.getRequestId()) {
            return true;
        }
        Log.w("AuthController", "shouldNotifyReceiver: requestId doesn't match");
        return false;
    }

    @Override // com.android.systemui.biometrics.AuthDialogCallback
    public void onDismissed(int i, byte[] bArr, long j) {
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null && j != authContainerView.getRequestId()) {
            Log.w("AuthController", "requestId doesn't match, skip onDismissed");
        } else {
            sendResultAndCleanUp(i, bArr);
        }
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void handleShowGlobalActionsMenu() {
        closeDialog("PowerMenu shown");
    }

    public Point getUdfpsLocation() {
        if (this.mUdfpsController == null || this.mUdfpsBounds == null) {
            return null;
        }
        return new Point(this.mUdfpsBounds.centerX(), this.mUdfpsBounds.centerY());
    }

    public float getUdfpsRadius() {
        Rect rect;
        if (this.mUdfpsController == null || (rect = this.mUdfpsBounds) == null) {
            return -1.0f;
        }
        return rect.height() / 2.0f;
    }

    public float getScaleFactor() {
        return this.mScaleFactor;
    }

    public final void updateSensorLocations() {
        this.mDisplay.getDisplayInfo(this.mCachedDisplayInfo);
        this.mScaleFactor = this.mUdfpsUtils.getScaleFactor(this.mCachedDisplayInfo);
        updateUdfpsLocation();
        updateFingerprintLocation();
    }

    public final Point getFingerprintSensorLocationInNaturalOrientation() {
        if (getUdfpsLocation() != null) {
            return getUdfpsLocation();
        }
        int naturalWidth = this.mCachedDisplayInfo.getNaturalWidth() / 2;
        try {
            naturalWidth = this.mContext.getResources().getDimensionPixelSize(R$dimen.physical_fingerprint_sensor_center_screen_location_x);
        } catch (Resources.NotFoundException unused) {
        }
        return new Point((int) (naturalWidth * this.mScaleFactor), (int) (this.mContext.getResources().getDimensionPixelSize(R$dimen.physical_fingerprint_sensor_center_screen_location_y) * this.mScaleFactor));
    }

    public Point getFingerprintSensorLocation() {
        return this.mFingerprintSensorLocation;
    }

    public final void updateFingerprintLocation() {
        if (this.mFpProps == null) {
            this.mFingerprintSensorLocation = null;
        } else {
            this.mFingerprintSensorLocation = rotateToCurrentOrientation(getFingerprintSensorLocationInNaturalOrientation(), this.mCachedDisplayInfo);
        }
        Iterator it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            ((Callback) it.next()).onFingerprintLocationChanged();
        }
    }

    public Point rotateToCurrentOrientation(Point point, DisplayInfo displayInfo) {
        RotationUtils.rotatePoint(point, displayInfo.rotation, displayInfo.getNaturalWidth(), displayInfo.getNaturalHeight());
        return point;
    }

    public void onAodInterrupt(int i, int i2, float f, float f2) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController == null) {
            return;
        }
        udfpsController.onAodInterrupt(i, i2, f, f2);
    }

    public final void sendResultAndCleanUp(int i, byte[] bArr) {
        IBiometricSysuiReceiver iBiometricSysuiReceiver = this.mReceiver;
        if (iBiometricSysuiReceiver == null) {
            Log.e("AuthController", "sendResultAndCleanUp: Receiver is null");
            return;
        }
        try {
            iBiometricSysuiReceiver.onDialogDismissed(i, bArr);
        } catch (RemoteException e) {
            Log.w("AuthController", "Remote exception", e);
        }
        onDialogDismissed(i);
    }

    public AuthController(Context context, CoroutineScope coroutineScope, Execution execution, CommandQueue commandQueue, ActivityTaskManager activityTaskManager, WindowManager windowManager, FingerprintManager fingerprintManager, FaceManager faceManager, Optional optional, Provider provider, DisplayManager displayManager, WakefulnessLifecycle wakefulnessLifecycle, UserManager userManager, LockPatternUtils lockPatternUtils, Lazy lazy, Lazy lazy2, Provider provider2, Provider provider3, Provider provider4, InteractionJankMonitor interactionJankMonitor, Handler handler, DelayableExecutor delayableExecutor, UdfpsUtils udfpsUtils, VibratorHelper vibratorHelper, KeyguardManager keyguardManager, MSDLPlayer mSDLPlayer, WindowManagerProvider windowManagerProvider) {
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { // from class: com.android.systemui.biometrics.AuthController.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                if ("android.intent.action.CLOSE_SYSTEM_DIALOGS".equals(intent.getAction())) {
                    String stringExtra = intent.getStringExtra("reason");
                    if (stringExtra == null) {
                        stringExtra = "unknown";
                    }
                    AuthController.this.closeDialog(stringExtra);
                }
            }
        };
        this.mBroadcastReceiver = broadcastReceiver;
        this.mContext = context;
        this.mExecution = execution;
        this.mUserManager = userManager;
        this.mLockPatternUtils = lockPatternUtils;
        this.mHandler = handler;
        this.mBackgroundExecutor = delayableExecutor;
        this.mCommandQueue = commandQueue;
        this.mActivityTaskManager = activityTaskManager;
        this.mFingerprintManager = fingerprintManager;
        this.mFaceManager = faceManager;
        this.mUdfpsControllerFactory = provider;
        this.mUdfpsLogger = lazy;
        this.mDisplayManager = displayManager;
        this.mWindowManager = windowManager;
        this.mInteractionJankMonitor = interactionJankMonitor;
        this.mUdfpsEnrolledForUser = new SparseBooleanArray();
        this.mSfpsEnrolledForUser = new SparseBooleanArray();
        this.mFaceEnrolledForUser = new SparseBooleanArray();
        this.mUdfpsUtils = udfpsUtils;
        this.mApplicationCoroutineScope = coroutineScope;
        this.mVibratorHelper = vibratorHelper;
        this.mMSDLPlayer = mSDLPlayer;
        this.mLogContextInteractor = lazy2;
        this.mPromptSelectorInteractor = provider2;
        this.mPromptViewModelProvider = provider4;
        this.mCredentialViewModelProvider = provider3;
        keyguardManager.addKeyguardLockedStateListener(context.getMainExecutor(), new KeyguardManager.KeyguardLockedStateListener() { // from class: com.android.systemui.biometrics.AuthController$$ExternalSyntheticLambda2
            @Override // android.app.KeyguardManager.KeyguardLockedStateListener
            public final void onKeyguardLockedStateChanged(boolean z) {
                this.f$0.lambda$new$0(z);
            }
        });
        this.mOrientationListener = new BiometricDisplayListener(context, displayManager, handler, BiometricDisplayListener.SensorType.Generic.INSTANCE, new Function0() { // from class: com.android.systemui.biometrics.AuthController$$ExternalSyntheticLambda3
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                return this.f$0.lambda$new$1();
            }
        });
        this.mWakefulnessLifecycle = wakefulnessLifecycle;
        this.mFaceProps = faceManager != null ? faceManager.getSensorPropertiesInternal() : null;
        this.mDisplay = context.getDisplay();
        updateSensorLocations();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.CLOSE_SYSTEM_DIALOGS");
        context.registerReceiver(broadcastReceiver, intentFilter, 2);
        this.mSensorPrivacyManager = (SensorPrivacyManager) context.getSystemService(SensorPrivacyManager.class);
        this.mWindowManagerProvider = windowManagerProvider;
    }

    public final /* synthetic */ void lambda$new$0(boolean z) {
        if (z) {
            closeDialog("Device lock");
        }
    }

    public final /* synthetic */ Unit lambda$new$1() {
        onOrientationChanged();
        return Unit.INSTANCE;
    }

    public final void updateUdfpsLocation() {
        if (this.mUdfpsController != null) {
            FingerprintSensorPropertiesInternal fingerprintSensorPropertiesInternal = (FingerprintSensorPropertiesInternal) this.mUdfpsProps.get(0);
            Rect rect = this.mUdfpsBounds;
            UdfpsOverlayParams udfpsOverlayParams = this.mUdfpsOverlayParams;
            Rect rect2 = fingerprintSensorPropertiesInternal.getLocation().getRect();
            this.mUdfpsBounds = rect2;
            rect2.scale(this.mScaleFactor);
            this.mUdfpsBounds = this.mFingerprintManager.getFingerprintIconLocation();
            this.mUdfpsOverlayParams = new UdfpsOverlayParams(this.mUdfpsBounds, new Rect(0, this.mCachedDisplayInfo.getNaturalHeight() / 2, this.mCachedDisplayInfo.getNaturalWidth(), this.mCachedDisplayInfo.getNaturalHeight()), this.mCachedDisplayInfo.getNaturalWidth(), this.mCachedDisplayInfo.getNaturalHeight(), this.mScaleFactor, this.mCachedDisplayInfo.rotation, fingerprintSensorPropertiesInternal.sensorType);
            Log.i("AuthController", "updateUdfpsLocation X=" + fingerprintSensorPropertiesInternal.getLocation().sensorLocationX + ", Y=" + fingerprintSensorPropertiesInternal.getLocation().sensorLocationY + ", R=" + fingerprintSensorPropertiesInternal.getLocation().sensorRadius);
            StringBuilder sb = new StringBuilder();
            sb.append("updateUdfpsLocation mUdfpsBounds=");
            sb.append(this.mUdfpsBounds);
            Log.i("AuthController", sb.toString());
            Log.i("AuthController", "updateUdfpsLocation mUdfpsOverlayParams=" + this.mUdfpsOverlayParams);
            this.mUdfpsController.updateOverlayParams(fingerprintSensorPropertiesInternal, this.mUdfpsOverlayParams);
            if (Objects.equals(rect, this.mUdfpsBounds) && Objects.equals(udfpsOverlayParams, this.mUdfpsOverlayParams)) {
                return;
            }
            Iterator it = this.mCallbacks.iterator();
            while (it.hasNext()) {
                ((Callback) it.next()).onUdfpsLocationChanged(this.mUdfpsOverlayParams);
            }
        }
    }

    @Override // com.android.systemui.CoreStartable
    public void start() {
        this.mCommandQueue.addCallback((CommandQueue.Callbacks) this);
        FingerprintManager fingerprintManager = this.mFingerprintManager;
        if (fingerprintManager != null) {
            fingerprintManager.addAuthenticatorsRegisteredCallback(new AnonymousClass6());
        }
        FaceManager faceManager = this.mFaceManager;
        if (faceManager != null) {
            faceManager.addAuthenticatorsRegisteredCallback(new AnonymousClass7());
        }
        this.mActivityTaskManager.registerTaskStackListener(this.mTaskStackListener);
        this.mOrientationListener.enable();
        updateSensorLocations();
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.AuthController$6, reason: invalid class name */
    class AnonymousClass6 extends IFingerprintAuthenticatorsRegisteredCallback.Stub {
        public AnonymousClass6() {
        }

        public void onAllAuthenticatorsRegistered(final List list) {
            AuthController.this.mHandler.post(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$6$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onAllAuthenticatorsRegistered$0(list);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAllAuthenticatorsRegistered$0(List list) {
            AuthController.this.handleAllFingerprintAuthenticatorsRegistered(list);
        }
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.AuthController$7, reason: invalid class name */
    class AnonymousClass7 extends IFaceAuthenticatorsRegisteredCallback.Stub {
        public AnonymousClass7() {
        }

        public void onAllAuthenticatorsRegistered(final List list) {
            AuthController.this.mHandler.post(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$7$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onAllAuthenticatorsRegistered$0(list);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onAllAuthenticatorsRegistered$0(List list) {
            AuthController.this.handleAllFaceAuthenticatorsRegistered(list);
        }
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void setBiometricContextListener(IBiometricContextListener iBiometricContextListener) {
        Job job = this.mBiometricContextListenerJob;
        if (job != null) {
            job.cancel(null);
        }
        this.mBiometricContextListenerJob = ((LogContextInteractor) this.mLogContextInteractor.get()).addBiometricContextListener(iBiometricContextListener);
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void setUdfpsRefreshRateCallback(IUdfpsRefreshRateRequestCallback iUdfpsRefreshRateRequestCallback) {
        this.mUdfpsRefreshRateRequestCallback = iUdfpsRefreshRateRequestCallback;
    }

    public IUdfpsRefreshRateRequestCallback getUdfpsRefreshRateCallback() {
        return this.mUdfpsRefreshRateRequestCallback;
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void showAuthenticationDialog(PromptInfo promptInfo, IBiometricSysuiReceiver iBiometricSysuiReceiver, int[] iArr, boolean z, boolean z2, int i, long j, String str, long j2) {
        int authenticators = promptInfo.getAuthenticators();
        StringBuilder sb = new StringBuilder();
        boolean z3 = false;
        for (int i2 : iArr) {
            sb.append(i2);
            sb.append(" ");
        }
        Log.d("AuthController", "showAuthenticationDialog, authenticators: " + authenticators + ", sensorIds: " + sb.toString() + ", credentialAllowed: " + z + ", requireConfirmation: " + z2 + ", operationId: " + j + ", requestId: " + j2);
        SomeArgs someArgsObtain = SomeArgs.obtain();
        someArgsObtain.arg1 = promptInfo;
        someArgsObtain.arg2 = iBiometricSysuiReceiver;
        someArgsObtain.arg3 = iArr;
        someArgsObtain.arg4 = Boolean.valueOf(z);
        someArgsObtain.arg5 = Boolean.valueOf(z2);
        someArgsObtain.argi1 = i;
        someArgsObtain.arg6 = str;
        someArgsObtain.argl1 = j;
        someArgsObtain.argl2 = j2;
        if (this.mCurrentDialog != null) {
            Log.w("AuthController", "mCurrentDialog: " + this.mCurrentDialog);
            z3 = true;
        }
        showDialog(someArgsObtain, z3, (PromptViewModel) this.mPromptViewModelProvider.get());
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void onBiometricAuthenticated(int i) {
        Log.d("AuthController", "onBiometricAuthenticated: ");
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null) {
            authContainerView.onAuthenticationSucceeded(i);
        } else {
            Log.w("AuthController", "onBiometricAuthenticated callback but dialog gone");
        }
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void onBiometricHelp(int i, String str) {
        Log.d("AuthController", "onBiometricHelp: " + str);
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null) {
            authContainerView.onHelp(i, str);
        } else {
            Log.w("AuthController", "onBiometricHelp callback but dialog gone");
        }
    }

    public List getUdfpsProps() {
        return this.mUdfpsProps;
    }

    public List getSfpsProps() {
        return this.mSidefpsProps;
    }

    public boolean isUdfpsSupported() {
        return (getUdfpsProps() == null || getUdfpsProps().isEmpty()) ? false : true;
    }

    public boolean isOpticalUdfpsSupported() {
        return (getUdfpsProps() == null || getUdfpsProps().isEmpty() || !((FingerprintSensorPropertiesInternal) getUdfpsProps().get(0)).isOpticalUdfps()) ? false : true;
    }

    public boolean isSfpsSupported() {
        return (getSfpsProps() == null || getSfpsProps().isEmpty()) ? false : true;
    }

    public boolean isRearFpsSupported() {
        List list = this.mFpProps;
        if (list == null) {
            return false;
        }
        Iterator it = list.iterator();
        while (it.hasNext()) {
            if (((FingerprintSensorPropertiesInternal) it.next()).sensorType == 1) {
                return true;
            }
        }
        return false;
    }

    public final String getNotRecognizedString(int i) {
        int i2;
        int i3 = this.mCurrentDialogArgs.argi1;
        if (isFaceAuthEnrolled(i3) && isFingerprintEnrolled(i3)) {
            i2 = i == 8 ? R.string.fingerprint_error_canceled : R.string.fingerprint_error_security_update_required;
        } else {
            i2 = R.string.biometric_not_recognized;
        }
        return this.mContext.getString(i2);
    }

    public final String getErrorString(int i, int i2, int i3) {
        if (i == 2) {
            return FingerprintManager.getErrorString(this.mContext, i2, i3);
        }
        if (i == 8) {
            return FaceManager.getErrorString(this.mContext, i2, i3);
        }
        return "";
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void onBiometricError(final int i, int i2, int i3) {
        String notRecognizedString;
        Log.d("AuthController", String.format("onBiometricError(%d, %d, %d)", Integer.valueOf(i), Integer.valueOf(i2), Integer.valueOf(i3)));
        boolean z = i2 == 7 || i2 == 9;
        boolean z2 = i2 == 1 && this.mSensorPrivacyManager.isSensorPrivacyEnabled(1, 2);
        boolean z3 = i2 == 100 || i2 == 3 || i2 == 16 || z2;
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null) {
            if (authContainerView.isAllowDeviceCredentials() && z) {
                Log.d("AuthController", "onBiometricError, lockout");
                this.mCurrentDialog.animateToCredentialUI(true);
                return;
            }
            if (z3) {
                if (i2 == 100 || i2 == 3) {
                    notRecognizedString = getNotRecognizedString(i);
                } else {
                    notRecognizedString = getErrorString(i, i2, i3);
                }
                Log.d("AuthController", "onBiometricError, soft error: " + notRecognizedString);
                if (z2) {
                    this.mHandler.postDelayed(new Runnable() { // from class: com.android.systemui.biometrics.AuthController$$ExternalSyntheticLambda4
                        @Override // java.lang.Runnable
                        public final void run() {
                            this.f$0.lambda$onBiometricError$2(i);
                        }
                    }, 500L);
                    return;
                } else {
                    this.mCurrentDialog.onAuthenticationFailed(i, notRecognizedString);
                    return;
                }
            }
            String errorString = getErrorString(i, i2, i3);
            Log.d("AuthController", "onBiometricError, hard error: " + errorString);
            this.mCurrentDialog.onError(i, errorString);
            return;
        }
        Log.w("AuthController", "onBiometricError callback but dialog is gone");
    }

    public final /* synthetic */ void lambda$onBiometricError$2(int i) {
        this.mCurrentDialog.onAuthenticationFailed(i, this.mContext.getString(R.string.factory_reset_message));
    }

    @Override // com.android.systemui.statusbar.CommandQueue.Callbacks
    public void hideAuthenticationDialog(long j) {
        Log.d("AuthController", "hideAuthenticationDialog: " + this.mCurrentDialog);
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView == null) {
            Log.d("AuthController", "dialog already gone");
            return;
        }
        if (j != authContainerView.getRequestId()) {
            Log.w("AuthController", "ignore - ids do not match: " + j + " current: " + this.mCurrentDialog.getRequestId());
            return;
        }
        this.mCurrentDialog.dismissFromSystemServer();
        Iterator it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            ((Callback) it.next()).onBiometricPromptDismissed();
        }
        this.mCurrentDialog = null;
    }

    public boolean isUdfpsFingerDown() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController == null) {
            return false;
        }
        return udfpsController.isFingerDown();
    }

    public boolean isFaceAuthEnrolled(int i) {
        Log.d("AuthController", "isUnlockWithFacePossible: isFaceAuthEnrolled mFaceProps=" + this.mFaceProps);
        if (this.mFaceProps == null) {
            return false;
        }
        Log.d("AuthController", "isUnlockWithFacePossible: isFaceAuthEnrolled mFaceEnrolledForUser=" + this.mFaceEnrolledForUser.get(i));
        return this.mFaceEnrolledForUser.get(i);
    }

    public boolean isOpticalUdfpsEnrolled(int i) {
        List list;
        return isUdfpsEnrolled(i) && (list = this.mUdfpsProps) != null && ((FingerprintSensorPropertiesInternal) list.get(0)).sensorType == 3;
    }

    public boolean isUdfpsEnrolled(int i) {
        if (this.mUdfpsController == null) {
            return false;
        }
        return this.mUdfpsEnrolledForUser.get(i);
    }

    public boolean isSfpsEnrolled(int i) {
        if (this.mSidefpsProps == null) {
            return false;
        }
        return this.mSfpsEnrolledForUser.get(i);
    }

    public boolean isShowing() {
        return this.mCurrentDialog != null;
    }

    public boolean isFingerprintEnrolled(int i) {
        return ((Boolean) this.mFpEnrolledForUser.getOrDefault(Integer.valueOf(i), Boolean.FALSE)).booleanValue();
    }

    public final void showDialog(SomeArgs someArgs, boolean z, PromptViewModel promptViewModel) {
        this.mCurrentDialogArgs = someArgs;
        PromptInfo promptInfo = (PromptInfo) someArgs.arg1;
        int[] iArr = (int[]) someArgs.arg3;
        ((Boolean) someArgs.arg4).getClass();
        boolean zBooleanValue = ((Boolean) someArgs.arg5).booleanValue();
        int i = someArgs.argi1;
        AuthContainerView authContainerViewBuildDialog = buildDialog(this.mBackgroundExecutor, promptInfo, zBooleanValue, i, iArr, (String) someArgs.arg6, z, someArgs.argl1, someArgs.argl2, this.mWakefulnessLifecycle, this.mUserManager, this.mLockPatternUtils, promptViewModel);
        if (authContainerViewBuildDialog == null) {
            Log.e("AuthController", "Unsupported type configuration");
            return;
        }
        Log.d("AuthController", "userId: " + i + " mCurrentDialog: " + this.mCurrentDialog + " newDialog: " + authContainerViewBuildDialog);
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null) {
            authContainerView.dismissWithoutCallback(false);
        }
        this.mReceiver = (IBiometricSysuiReceiver) someArgs.arg2;
        Iterator it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            ((Callback) it.next()).onBiometricPromptShown();
        }
        this.mCurrentDialog = authContainerViewBuildDialog;
        if (!promptInfo.isAllowBackgroundAuthentication() && isOwnerInBackground()) {
            cancelIfOwnerIsNotInForeground();
            return;
        }
        WindowManager windowManagerForUser = getWindowManagerForUser(i);
        if (windowManagerForUser != null) {
            this.mCurrentDialog.show(windowManagerForUser);
        } else {
            closeDialog(9, "unable to get WM instance for user");
        }
    }

    public final WindowManager getWindowManagerForUser(int i) {
        if (!this.mUserManager.isVisibleBackgroundUsersSupported()) {
            return this.mWindowManager;
        }
        UserManager userManager = (UserManager) this.mContext.createContextAsUser(UserHandle.of(i), 0).getSystemService(UserManager.class);
        if (userManager == null) {
            Log.e("AuthController", "unable to get UserManager for user=" + i);
            return null;
        }
        if (!userManager.isUserVisible()) {
            return this.mWindowManager;
        }
        int mainDisplayIdAssignedToUser = userManager.getMainDisplayIdAssignedToUser();
        if (mainDisplayIdAssignedToUser == -1) {
            Log.e("AuthController", "unable to get display assigned to user=" + i);
            return null;
        }
        Display display = this.mDisplayManager.getDisplay(mainDisplayIdAssignedToUser);
        if (display == null) {
            Log.e("AuthController", "unable to get Display for user=" + i);
            return null;
        }
        return this.mWindowManagerProvider.getWindowManager(this.mContext.createDisplayContext(display));
    }

    public final void onDialogDismissed(int i) {
        Log.d("AuthController", "onDialogDismissed: " + i);
        if (this.mCurrentDialog == null) {
            Log.w("AuthController", "Dialog already dismissed");
        }
        Iterator it = this.mCallbacks.iterator();
        while (it.hasNext()) {
            ((Callback) it.next()).onBiometricPromptDismissed();
        }
        this.mReceiver = null;
        this.mCurrentDialog = null;
    }

    @Override // com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener
    public void onConfigChanged(Configuration configuration) {
        updateSensorLocations();
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null) {
            PromptViewModel viewModel = authContainerView.getViewModel();
            this.mCurrentDialog.dismissWithoutCallback(false);
            this.mCurrentDialog = null;
            showDialog(this.mCurrentDialogArgs, true, viewModel);
        }
    }

    private void onOrientationChanged() {
        updateSensorLocations();
        AuthContainerView authContainerView = this.mCurrentDialog;
        if (authContainerView != null) {
            authContainerView.onOrientationChanged();
        }
    }

    public AuthContainerView buildDialog(DelayableExecutor delayableExecutor, PromptInfo promptInfo, boolean z, int i, int[] iArr, String str, boolean z2, long j, long j2, WakefulnessLifecycle wakefulnessLifecycle, UserManager userManager, LockPatternUtils lockPatternUtils, PromptViewModel promptViewModel) {
        AuthContainerView.Config config = new AuthContainerView.Config();
        config.mContext = this.mContext;
        config.mCallback = this;
        config.mPromptInfo = promptInfo;
        config.mRequireConfirmation = z;
        config.mUserId = i;
        config.mOpPackageName = str;
        config.mSkipIntro = z2;
        config.mOperationId = j;
        config.mRequestId = j2;
        config.mSensorIds = iArr;
        config.mScaleProvider = new ScaleFactorProvider() { // from class: com.android.systemui.biometrics.AuthController$$ExternalSyntheticLambda0
        };
        return new AuthContainerView(config, this.mApplicationCoroutineScope, this.mFpProps, this.mFaceProps, wakefulnessLifecycle, userManager, null, lockPatternUtils, this.mInteractionJankMonitor, this.mPromptSelectorInteractor, promptViewModel, this.mCredentialViewModelProvider, delayableExecutor, this.mVibratorHelper, this.mMSDLPlayer);
    }

    @Override // com.android.systemui.CoreStartable, com.android.systemui.Dumpable
    public void dump(PrintWriter printWriter, String[] strArr) {
        AuthContainerView authContainerView = this.mCurrentDialog;
        printWriter.println("  mCachedDisplayInfo=" + this.mCachedDisplayInfo);
        printWriter.println("  mScaleFactor=" + this.mScaleFactor);
        printWriter.println("  fingerprintSensorLocationInNaturalOrientation=" + getFingerprintSensorLocationInNaturalOrientation());
        printWriter.println("  fingerprintSensorLocation=" + getFingerprintSensorLocation());
        printWriter.println("  udfpsBounds=" + this.mUdfpsBounds);
        printWriter.println("  allFingerprintAuthenticatorsRegistered=" + this.mAllFingerprintAuthenticatorsRegistered);
        printWriter.println("  currentDialog=" + authContainerView);
        printWriter.println("  mFaceProps=" + this.mFaceProps);
        if (authContainerView != null) {
            authContainerView.dump(printWriter, strArr);
        }
    }

    public boolean shouldDismissFingerprintIcon() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            return udfpsController.shouldDismissFingerprintIcon();
        }
        return false;
    }

    public void dismissFingerprintIcon() {
        Log.d("AuthController", "dismissFingerprintIcon: ");
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.dismissFingerprintIcon();
        }
    }

    public void dismissFpIconWithoutAnim() {
        Log.d("AuthController", "dismissFpIconWithoutAnim: ");
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.dismissFpIconWithoutAnim();
        }
    }

    public void showFingerprintIcon() {
        Log.d("AuthController", "showFingerprintIcon: ");
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.showFingerprintIcon();
        }
    }

    public boolean isIconAlphaVisible() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            return udfpsController.isIconAlphaVisible();
        }
        return false;
    }

    public void updateColorsAndUdfpsLocation(int i) {
        updateColorsOnly(i);
        updateUdfpsLocation();
    }

    public void updateColorsOnly(int i) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.updateFakeFpAndFingerprintIconColor(i);
        }
    }

    public void updateWideFpIconState(boolean z) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.updateFakeFpAndFingerprintIconState(z);
        }
    }

    public void startLeftRightIconScaleAnimator() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.startLeftRightIconScaleAnimator();
        }
    }

    public boolean isWithinSensorArea(float f, float f2) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            return udfpsController.isWithinSensorArea(f, f2);
        }
        return false;
    }

    public boolean isWithinPalmRejectionArea(float f, float f2) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            return udfpsController.isWithinPalmRejectionArea(f, f2);
        }
        return false;
    }

    public void showFingerprintDownAnim() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.showFingerprintDownAnim();
        }
    }

    public void showFingerprintDownAnimLeft() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.showFingerprintDownAnimLeft();
        }
    }

    public void showFingerprintDownAnimRight() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.showFingerprintDownAnimRight();
        }
    }

    public void hideFingerprintDownAnim() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.hideFingerprintDownAnim();
        }
    }

    public void hideFingerprintDownAnimLeft() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.hideFingerprintDownAnimLeft();
        }
    }

    public void hideFingerprintDownAnimRight() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.hideFingerprintDownAnimRight();
        }
    }

    public void hideFpIcon() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.hideFpIcon();
        }
    }

    public void showFpIcon() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.showFpIcon();
        }
    }

    public Rect getUdfpsBound() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            return udfpsController.getUdfpsBound();
        }
        return null;
    }

    public Rect getUdfpsSensorBounds() {
        return this.mUdfpsBounds;
    }

    public void resetNeedShowFakeFpIcon() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.resetNeedShowFakeFpIcon();
        }
    }

    public void showFakeFpIcon() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.showFakeFpIcon();
        }
    }

    public void hideFakeFpIcon() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.hideFakeFpIcon();
        }
    }

    public boolean isFakeFpIconShow() {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            return udfpsController.isFakeFpIconShow();
        }
        return false;
    }

    public void updateFakeFpIconAlpha(float f) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.updateFakeFpIconAlpha(f);
        }
    }

    public void startLeftRightIconHideAnimForFakeFp(long j) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.startLeftRightIconHideAnimForFakeFp(j);
        }
    }

    public void updateLeftRightIconAlphaForFakeFp(float f, float f2) {
        UdfpsController udfpsController = this.mUdfpsController;
        if (udfpsController != null) {
            udfpsController.updateLeftRightIconAlphaForFakeFp(f, f2);
        }
    }
}
