package com.android.keyguard;

import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.admin.DevicePolicyManager;
import android.app.trust.TrustManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.hardware.SensorPrivacyManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricSourceType;
import android.hardware.biometrics.IBiometricEnabledOnKeyguardCallback;
import android.hardware.biometrics.SensorPropertiesInternal;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintAuthenticateOptions;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.hardware.fingerprint.IFingerprintAuthenticatorsRegisteredCallback;
import android.os.BatteryManager;
import android.os.BuildExt;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.dreams.IDreamManager;
import android.support.v4.media.session.MediaControllerCompat$Callback$StubCompat$$ExternalSyntheticThrowCCEIfNotNull0;
import android.telephony.CarrierConfigManager;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.FlymeStringSecure;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import androidx.exifinterface.media.ExifInterface;
import com.android.compose.animation.scene.ObservableTransitionState;
import com.android.internal.foldables.FoldGracePeriodProvider;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.InstanceId;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.util.LatencyTracker;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.logging.KeyguardUpdateMonitorLogger;
import com.android.keyguard.logging.SimLogger;
import com.android.settingslib.Utils;
import com.android.settingslib.WirelessUtils;
import com.android.settingslib.fuelgauge.BatteryStatus;
import com.android.systemui.CoreStartable;
import com.android.systemui.Dependency;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.deviceentry.data.repository.FaceWakeUpTriggersConfig;
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryFaceAuthInteractor;
import com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener;
import com.android.systemui.deviceentry.shared.model.AcquiredFaceAuthenticationStatus;
import com.android.systemui.deviceentry.shared.model.ErrorFaceAuthenticationStatus;
import com.android.systemui.deviceentry.shared.model.FaceAuthenticationStatus;
import com.android.systemui.deviceentry.shared.model.FaceDetectionStatus;
import com.android.systemui.deviceentry.shared.model.FailedFaceAuthenticationStatus;
import com.android.systemui.deviceentry.shared.model.HelpFaceAuthenticationStatus;
import com.android.systemui.deviceentry.shared.model.SuccessFaceAuthenticationStatus;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.dump.DumpsysTableLogger;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.KeyguardWmStateRefactor;
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor;
import com.android.systemui.keyguard.domain.interactor.KeyguardServiceShowLockscreenInteractor;
import com.android.systemui.keyguard.domain.interactor.ShowWhileAwakeReason;
import com.android.systemui.keyguard.shared.constants.TrustAgentUiEvent;
import com.android.systemui.log.SessionTracker;
import com.android.systemui.media.controls.ui.controller.KeyguardMediaController;
import com.android.systemui.plugins.clocks.WeatherData;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.res.R$array;
import com.android.systemui.res.R$integer;
import com.android.systemui.res.R$string;
import com.android.systemui.scene.domain.interactor.SceneInteractor;
import com.android.systemui.scene.shared.flag.SceneContainerFlag;
import com.android.systemui.scene.shared.model.Overlays;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.policy.DevicePostureController;
import com.android.systemui.telephony.TelephonyListenerManager;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.android.systemui.util.Assert;
import com.android.systemui.util.kotlin.JavaAdapter;
import com.flyme.keyguard.PalmRejection;
import com.flyme.keyguard.SystemModeUtils;
import com.flyme.keyguard.charging.ChargeAnimationController;
import com.flyme.keyguard.livewallpaper.LiveWallpaperController;
import com.flyme.keyguard.settings.SettingsUpdateMonitor;
import com.flyme.keyguard.settings.lockscreen.LockScreenData;
import com.flyme.keyguard.theme.FlymeDynamicThemeUtils;
import com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils;
import com.flyme.systemui.findphone.FindPhoneMgr;
import com.flyme.systemui.utils.SystemUICommonUtils;
import com.flyme.systemui.utils.SystemUIMobEventUtils;
import com.flyme.systemui.welcome.WelcomePageMgr;
import com.google.android.collect.Lists;
import dalvik.annotation.optimization.NeverCompile;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Provider;

/* JADX INFO: loaded from: classes2.dex */
public class KeyguardUpdateMonitor implements TrustManager.TrustListener, CoreStartable {
    public static final int BIOMETRIC_HELP_FINGERPRINT_NOT_RECOGNIZED = -1;
    protected static final int BIOMETRIC_STATE_CANCELLING = 2;
    protected static final int BIOMETRIC_STATE_CANCELLING_RESTARTING = 3;
    protected static final int BIOMETRIC_STATE_STOPPED = 0;
    protected static final int DEFAULT_CANCEL_SIGNAL_TIMEOUT = 3000;
    protected static final int HAL_POWER_PRESS_TIMEOUT = 50;
    public final int FACE_HIDL_SENSOR_ID;
    public int mActiveMobileDataSubscription;
    public final ActiveUnlockConfig mActiveUnlockConfig;
    public final KeyguardActiveUnlockModel.Buffer mActiveUnlockTriggerBuffer;
    public final IActivityTaskManager mActivityTaskManager;
    public boolean mAllowFingerprintOnCurrentOccludingActivity;
    public final Set mAllowFingerprintOnOccludingActivitiesFromPackage;
    public final Provider mAlternateBouncerInteractor;
    public boolean mAlternateBouncerShowing;
    public boolean mAssistantVisible;
    public final AuthController mAuthController;
    public boolean mAuthInterruptActive;
    public final Executor mBackgroundExecutor;
    BatteryStatus mBatteryStatus;
    public final IBiometricEnabledOnKeyguardCallback mBiometricEnabledCallback;
    public final SparseBooleanArray mBiometricEnabledForUser;
    public final BiometricManager mBiometricManager;
    public boolean mBiometricPromptShowing;
    protected final BroadcastReceiver mBroadcastAllReceiver;
    public final BroadcastDispatcher mBroadcastDispatcher;
    protected final BroadcastReceiver mBroadcastReceiver;
    public final ArrayList mCallbacks;
    public boolean mCameraGestureTrigger;
    public int mCenterState;
    public final Provider mCommunalSceneInteractor;
    public boolean mCommunalShowing;
    protected int mConfigFaceAuthSupportedPosture;
    public final Context mContext;
    public boolean mControlScreenOff;
    public boolean mCredentialAttempted;
    public boolean mDeviceInteractive;
    public final DevicePolicyManager mDevicePolicyManager;
    public final DevicePostureController mDevicePostureController;
    public boolean mDeviceProvisioned;
    public ContentObserver mDeviceProvisionedObserver;
    public final IDreamManager mDreamManager;
    public int mFPAuthSystemMode;
    public DeviceEntryFaceAuthInteractor mFaceAuthInteractor;
    public final FaceAuthenticationListener mFaceAuthenticationListener;
    public final FaceManager mFaceManager;
    public final FaceWakeUpTriggersConfig mFaceWakeUpTriggersConfig;
    final FingerprintManager.AuthenticationCallback mFingerprintAuthenticationCallback;
    CancellationSignal mFingerprintCancelSignal;
    public boolean mFingerprintDetectRunning;
    public final FingerprintManager.FingerprintDetectionCallback mFingerprintDetectionCallback;
    public final KeyguardFingerprintListenModel.Buffer mFingerprintListenBuffer;
    public boolean mFingerprintLockedOut;
    public boolean mFingerprintLockedOutPermanent;
    public final FingerprintManager.LockoutResetCallback mFingerprintLockoutResetCallback;
    protected int mFingerprintRunningState;
    public List mFingerprintSensorProperties;
    protected FoldGracePeriodProvider mFoldGracePeriodProvider;
    public boolean mForceIsDismissible;
    protected final Runnable mFpCancelNotReceived;
    public final FingerprintManager mFpm;
    public boolean mGoingToSleep;
    public final Handler mHandler;
    public int mHardwareFingerprintUnavailableRetryCount;
    public boolean mImeShowing;
    boolean mIncompatibleCharger;
    public final InteractionJankMonitor mInteractionJankMonitor;
    public boolean mIsDreaming;
    public boolean mIsFPVisibleWhenAODAuth;
    public boolean mIsFingerprintFreeze;
    public boolean mIsLaunchUIGroupSelectActivity;
    public boolean mIsSideFpFailFirstTime;
    public final boolean mIsSystemUser;
    public final Provider mJavaAdapter;
    public KeyguardBypassController mKeyguardBypassController;
    public boolean mKeyguardDismissPauseForEditor;
    public boolean mKeyguardGoingAway;
    public KeyguardInteractor mKeyguardInteractor;
    public KeyguardMediaController mKeyguardMediaController;
    public boolean mKeyguardOccluded;
    public final Provider mKeyguardServiceShowLockscreenInteractor;
    public boolean mKeyguardShowing;
    public final LatencyTracker mLatencyTracker;
    public boolean mLaunchingApp;
    public final LockPatternUtils mLockPatternUtils;
    public final KeyguardUpdateMonitorLogger mLogger;
    public final Executor mMainExecutor;
    public boolean mNeedsSlowUnlockTransition;
    public boolean mOccludingAppRequestingFp;
    public final PackageManager mPackageManager;
    public boolean mPasswordMatched;
    public int mPhoneState;
    public TelephonyCallback.ActiveDataSubscriptionIdListener mPhoneStateListener;
    final DevicePostureController.Callback mPostureCallback;
    public boolean mPrimaryBouncerFullyShown;
    public boolean mPrimaryBouncerIsOrWillBeShowing;
    public final Runnable mRetryFingerprintAuthenticationAfterHwUnavailable;
    public final Provider mSceneInteractor;
    public final Map mSecondaryLockscreenRequirement;
    public boolean mSecureCameraLaunched;
    public final SelectedUserInteractor mSelectedUserInteractor;
    public final SensorPrivacyManager mSensorPrivacyManager;
    public HashMap mServiceStates;
    public HashMap mServiceStatesWithSlotid;
    public final Provider mSessionTrackerProvider;
    public final Object mSimDataLockObject;
    public HashMap mSimDatas;
    public HashMap mSimDatasBySlotId;
    public final SimLogger mSimLogger;
    public int mStatusBarState;
    public final StatusBarStateController mStatusBarStateController;
    public final StatusBarStateController.StateListener mStatusBarStateControllerListener;
    public StrongAuthTracker mStrongAuthTracker;
    public List mSubscriptionInfo;
    final SubscriptionManager.OnSubscriptionsChangedListener mSubscriptionListener;
    public final SubscriptionManager mSubscriptionManager;
    public boolean mSwitchingUser;
    public final TaskStackChangeListeners mTaskStackChangeListeners;
    public final TaskStackChangeListener mTaskStackListener;
    protected boolean mTelephonyCapable;
    public final TelephonyListenerManager mTelephonyListenerManager;
    public final TelephonyManager mTelephonyManager;
    public final ContentObserver mTimeFormatChangeObserver;
    public final TrustManager mTrustManager;
    public final UiEventLogger mUiEventLogger;
    public final UserTracker.Callback mUserChangedCallback;
    SparseArray mUserFingerprintAuthenticated;
    public final SparseBooleanArray mUserHasTrust;
    public final SparseBooleanArray mUserIsUnlocked;
    public final UserManager mUserManager;
    public final UserTracker mUserTracker;
    public final SparseBooleanArray mUserTrustIsManaged;
    public final SparseBooleanArray mUserTrustIsUsuallyManaged;
    public boolean mWaitWellcomPage;
    public ContentObserver mWellcomePageObserver;
    public static final ComponentName FALLBACK_HOME_COMPONENT = new ComponentName("com.android.settings", "com.android.settings.FallbackHome");
    public static final List ABSENT_SIM_STATE_LIST = Arrays.asList(1, 0, 6);

    public static /* synthetic */ void $r8$lambda$N_dOIgOJXInuypcPPINtWTAmgbo() {
    }

    public static boolean isSimPinSecure(int i) {
        return i == 2 || i == 3 || i == 7;
    }

    public boolean canExecuteFingerprintError(int i) {
        return (i == 7 || i == 9) ? false : true;
    }

    public final boolean containsFlag(int i, int i2) {
        return (i & i2) != 0;
    }

    public int getBiometricLockoutDelay() {
        return 600;
    }

    public final void invalidateSlot(int i) {
    }

    public boolean isUnderFingerprint() {
        return true;
    }

    public void onIsActiveUnlockRunningChanged(boolean z, int i) {
    }

    public Handler getHandler() {
        return this.mHandler;
    }

    /* JADX INFO: renamed from: com.android.keyguard.KeyguardUpdateMonitor$2, reason: invalid class name */
    class AnonymousClass2 extends IBiometricEnabledOnKeyguardCallback.Stub {
        public AnonymousClass2() {
        }

        public void onChanged(final boolean z, final int i, final int i2) {
            KeyguardUpdateMonitor.this.mHandler.post(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$2$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onChanged$0(i2, i, z);
                }
            });
        }

        /* JADX INFO: Access modifiers changed from: private */
        public /* synthetic */ void lambda$onChanged$0(int i, int i2, boolean z) {
            KeyguardUpdateMonitor.this.mBiometricEnabledForUser.put(i2, z);
            KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
        }
    }

    class BiometricAuthenticated {
        public final boolean mAuthenticated;
        public final boolean mIsStrongBiometric;

        public BiometricAuthenticated(boolean z, boolean z2) {
            this.mAuthenticated = z;
            this.mIsStrongBiometric = z2;
        }
    }

    public void onTrustChanged(boolean z, boolean z2, int i, int i2, List list) {
        Assert.isMainThread();
        boolean z3 = this.mUserHasTrust.get(i, false);
        this.mUserHasTrust.put(i, z);
        if (z3 == z || z) {
            updateFingerprintListeningState(1);
        } else {
            updateFingerprintListeningState(0);
        }
        this.mLogger.logTrustChanged(z3, z, i);
        for (int i3 = 0; i3 < this.mCallbacks.size(); i3++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i3)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTrustChanged(i);
            }
        }
        if (z) {
            String str = null;
            if (this.mSelectedUserInteractor.getSelectedUserId() == i && list != null) {
                Iterator it = list.iterator();
                while (it.hasNext()) {
                    str = (String) it.next();
                    if (!TextUtils.isEmpty(str)) {
                        break;
                    }
                }
            }
            this.mLogger.logTrustGrantedWithFlags(i2, z2, i, str);
            if (i == this.mSelectedUserInteractor.getSelectedUserId()) {
                if (z2) {
                    this.mUiEventLogger.log(TrustAgentUiEvent.TRUST_AGENT_NEWLY_UNLOCKED, getKeyguardSessionId());
                }
                TrustGrantFlags trustGrantFlags = new TrustGrantFlags(i2);
                for (int i4 = 0; i4 < this.mCallbacks.size(); i4++) {
                    KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback2 = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i4)).get();
                    if (keyguardUpdateMonitorCallback2 != null) {
                        keyguardUpdateMonitorCallback2.onTrustGrantedForCurrentUser(shouldDismissKeyguardOnTrustGrantedWithCurrentUser(trustGrantFlags), z2, trustGrantFlags, str);
                    }
                }
            }
        }
    }

    public final boolean shouldDismissKeyguardOnTrustGrantedWithCurrentUser(TrustGrantFlags trustGrantFlags) {
        return (trustGrantFlags.isInitiatedByUser() || trustGrantFlags.dismissKeyguardRequested()) && (this.mDeviceInteractive || trustGrantFlags.temporaryAndRenewable()) && ((isPrimaryBouncerShowingOrWillBeShowing() || isAlternateBouncerShowing()) || trustGrantFlags.dismissKeyguardRequested());
    }

    public void onTrustError(CharSequence charSequence) {
        dispatchErrorMessage(charSequence);
    }

    public void onEnabledTrustAgentsChanged(int i) {
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onEnabledTrustAgentsChanged(i);
            }
        }
    }

    public final void handleSimSubscriptionInfoChanged() {
        this.mSimLogger.v("onSubscriptionInfoChanged()");
        this.mBackgroundExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda16
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$handleSimSubscriptionInfoChanged$1();
            }
        });
    }

    public final /* synthetic */ void lambda$handleSimSubscriptionInfoChanged$1() {
        final List subscriptionInfo = getSubscriptionInfo(true);
        this.mMainExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda17
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$handleSimSubscriptionInfoChanged$0(subscriptionInfo);
            }
        });
    }

    public final /* synthetic */ void lambda$handleSimSubscriptionInfoChanged$0(List list) {
        if (!list.isEmpty()) {
            Iterator it = list.iterator();
            while (it.hasNext()) {
                this.mSimLogger.logSubInfo((SubscriptionInfo) it.next());
            }
        } else {
            this.mSimLogger.v("onSubscriptionInfoChanged: list is null");
        }
        ArrayList arrayList = new ArrayList();
        HashSet hashSet = new HashSet();
        for (int i = 0; i < list.size(); i++) {
            SubscriptionInfo subscriptionInfo = (SubscriptionInfo) list.get(i);
            hashSet.add(Integer.valueOf(subscriptionInfo.getSubscriptionId()));
            if (refreshSimState(subscriptionInfo.getSubscriptionId(), subscriptionInfo.getSimSlotIndex())) {
                arrayList.add(subscriptionInfo);
            }
        }
        synchronized (this.mSimDataLockObject) {
            try {
                Iterator it2 = this.mSimDatasBySlotId.entrySet().iterator();
                while (it2.hasNext()) {
                    SimData simData = (SimData) ((Map.Entry) it2.next()).getValue();
                    if (!hashSet.contains(Integer.valueOf(simData.subId))) {
                        this.mSimLogger.logInvalidSubId(simData.subId, simData.slotId);
                        it2.remove();
                        threadSafeCallSimStateChanged(null, simData.subId, simData.slotId, simData.simState);
                    }
                }
                for (int i2 = 0; i2 < arrayList.size(); i2++) {
                    SimData simData2 = (SimData) this.mSimDatasBySlotId.get(Integer.valueOf(((SubscriptionInfo) arrayList.get(i2)).getSimSlotIndex()));
                    if (simData2 == null) {
                        Log.w("KeyguardUpdateMonitor", "Null SimData for subscription: " + arrayList.get(i2));
                    } else {
                        threadSafeCallSimStateChanged(null, simData2.subId, simData2.slotId, simData2.simState);
                    }
                }
                callbacksRefreshCarrierInfo();
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public final void handleAirplaneModeChanged() {
        callbacksRefreshCarrierInfo();
    }

    public final void callbacksRefreshCarrierInfo() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onRefreshCarrierInfo();
            }
        }
    }

    public List getSubscriptionInfo(boolean z) {
        if (this.mSubscriptionInfo == null || z) {
            this.mSubscriptionInfo = this.mSubscriptionManager.getCompleteActiveSubscriptionInfoList().stream().filter(new Predicate() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda6
                @Override // java.util.function.Predicate
                public final boolean test(Object obj) {
                    return KeyguardUpdateMonitor.$r8$lambda$Es9gst71EvMRPPoKG6XjnNpPKEE((SubscriptionInfo) obj);
                }
            }).toList();
        }
        return new ArrayList(this.mSubscriptionInfo);
    }

    public static /* synthetic */ boolean $r8$lambda$Es9gst71EvMRPPoKG6XjnNpPKEE(SubscriptionInfo subscriptionInfo) {
        return (subscriptionInfo.getProfileClass() == 1 || subscriptionInfo.getSubscriptionType() == 1) ? false : true;
    }

    public List getFilteredSubscriptionInfo() {
        List subscriptionInfo = getSubscriptionInfo(false);
        if (subscriptionInfo.size() == 2) {
            SubscriptionInfo subscriptionInfo2 = (SubscriptionInfo) subscriptionInfo.get(0);
            SubscriptionInfo subscriptionInfo3 = (SubscriptionInfo) subscriptionInfo.get(1);
            if (subscriptionInfo2.getGroupUuid() != null && subscriptionInfo2.getGroupUuid().equals(subscriptionInfo3.getGroupUuid()) && (subscriptionInfo2.isOpportunistic() || subscriptionInfo3.isOpportunistic())) {
                if (CarrierConfigManager.getDefaultConfig().getBoolean("always_show_primary_signal_bar_in_opportunistic_network_boolean")) {
                    if (!subscriptionInfo2.isOpportunistic()) {
                        subscriptionInfo2 = subscriptionInfo3;
                    }
                    subscriptionInfo.remove(subscriptionInfo2);
                    return subscriptionInfo;
                }
                if (subscriptionInfo2.getSubscriptionId() == this.mActiveMobileDataSubscription) {
                    subscriptionInfo2 = subscriptionInfo3;
                }
                subscriptionInfo.remove(subscriptionInfo2);
            }
        }
        return subscriptionInfo;
    }

    public void onTrustManagedChanged(boolean z, int i) {
        Assert.isMainThread();
        this.mUserTrustIsManaged.put(i, z);
        boolean zIsTrustUsuallyManaged = this.mTrustManager.isTrustUsuallyManaged(i);
        this.mLogger.logTrustUsuallyManagedUpdated(i, this.mUserTrustIsUsuallyManaged.get(i), zIsTrustUsuallyManaged, "onTrustManagedChanged");
        this.mUserTrustIsUsuallyManaged.put(i, zIsTrustUsuallyManaged);
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTrustManagedChanged(i);
            }
        }
    }

    public void setCredentialAttempted() {
        this.mCredentialAttempted = true;
        updateFingerprintListeningState(2);
    }

    public void tryForceIsDismissibleKeyguard() {
        setForceIsDismissibleKeyguard(true);
    }

    public void setKeyguardGoingAway(boolean z) {
        this.mKeyguardGoingAway = z;
        if (z) {
            for (int i = 0; i < this.mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (keyguardUpdateMonitorCallback != null) {
                    keyguardUpdateMonitorCallback.onKeyguardGoingAway();
                }
            }
        }
        updateFingerprintListeningState(2);
    }

    public boolean isKeyguardGoingAway() {
        return this.mKeyguardGoingAway;
    }

    public void setKeyguardShowing(boolean z, boolean z2) {
        boolean z3 = this.mKeyguardOccluded;
        boolean z4 = z3 != z2;
        boolean z5 = this.mKeyguardShowing != z;
        if (z3 && !z2 && this.mLaunchingApp) {
            this.mLaunchingApp = false;
        }
        if (z4 || z5) {
            boolean zIsKeyguardVisible = isKeyguardVisible();
            this.mKeyguardShowing = z;
            this.mKeyguardOccluded = z2;
            boolean zIsKeyguardVisible2 = isKeyguardVisible();
            this.mLogger.logKeyguardShowingChanged(z, z2, zIsKeyguardVisible2);
            if (zIsKeyguardVisible2 != zIsKeyguardVisible) {
                if (zIsKeyguardVisible2) {
                    this.mSecureCameraLaunched = false;
                }
                for (int i = 0; i < this.mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                    if (keyguardUpdateMonitorCallback != null) {
                        keyguardUpdateMonitorCallback.onKeyguardVisibilityChanged(zIsKeyguardVisible2);
                    }
                }
            }
            KeyguardMediaController keyguardMediaController = this.mKeyguardMediaController;
            if (keyguardMediaController != null) {
                keyguardMediaController.setMediaPlayerVisible(z);
            }
            if (z4 || z5) {
                updateFingerprintListeningState(2);
            }
        }
    }

    public void requestFingerprintAuthOnOccludingApp(boolean z) {
        this.mOccludingAppRequestingFp = z;
        updateFingerprintListeningState(2);
    }

    public void onCameraLaunched() {
        this.mSecureCameraLaunched = true;
        updateFingerprintListeningState(2);
    }

    public boolean isSecureCameraLaunchedOverKeyguard() {
        return this.mSecureCameraLaunched;
    }

    public boolean isDreaming() {
        return this.mIsDreaming;
    }

    public void awakenFromDream() {
        if (this.mIsDreaming) {
            try {
                this.mDreamManager.awaken();
            } catch (RemoteException e) {
                this.mLogger.logException(e, "Unable to awaken from dream");
            }
        }
    }

    private void onBiometricDetected(int i, BiometricSourceType biometricSourceType, boolean z) {
        Assert.isMainThread();
        Trace.beginSection("KeyGuardUpdateMonitor#onBiometricDetected");
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricDetected(i, biometricSourceType, z);
            }
        }
        Trace.endSection();
    }

    public void onFingerprintAuthenticated(int i, boolean z) {
        Assert.isMainThread();
        Trace.beginSection("KeyGuardUpdateMonitor#onFingerPrintAuthenticated");
        this.mUserFingerprintAuthenticated.put(i, new BiometricAuthenticated(true, z));
        if (getUserCanSkipBouncer(i)) {
            this.mTrustManager.unlockedByBiometricForUser(i, BiometricSourceType.FINGERPRINT);
        }
        this.mFingerprintCancelSignal = null;
        this.mLogger.logFingerprintSuccess(i, z);
        updateFingerprintListeningState(2);
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricAuthenticated(i, BiometricSourceType.FINGERPRINT, z);
            }
        }
        Handler handler = this.mHandler;
        handler.sendMessageDelayed(handler.obtainMessage(336), 500L);
        this.mAssistantVisible = false;
        reportSuccessfulBiometricUnlock(z, i);
        Trace.endSection();
    }

    public final void reportSuccessfulBiometricUnlock(final boolean z, final int i) {
        this.mBackgroundExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$reportSuccessfulBiometricUnlock$3(z, i);
            }
        });
    }

    public final /* synthetic */ void lambda$reportSuccessfulBiometricUnlock$3(boolean z, int i) {
        this.mLogger.logReportSuccessfulBiometricUnlock(z, i);
        this.mLockPatternUtils.reportSuccessfulBiometricUnlock(z, i);
    }

    public final void handleFingerprintAuthFailed() {
        Assert.isMainThread();
        if (this.mHandler.hasCallbacks(this.mFpCancelNotReceived)) {
            this.mLogger.d("handleFingerprintAuthFailed() triggered while waiting for cancellation, removing watchdog");
            this.mHandler.removeCallbacks(this.mFpCancelNotReceived);
        }
        this.mLogger.d("handleFingerprintAuthFailed");
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricAuthFailed(BiometricSourceType.FINGERPRINT);
            }
        }
        Log.d("KeyguardUpdateMonitor", "handleFingerprintAuthFailed: isUdfpsSupported() = " + isUdfpsSupported() + " mIsSideFpFailFirstTime = " + this.mIsSideFpFailFirstTime);
        if (isUdfpsSupported() || !this.mIsSideFpFailFirstTime) {
            handleFingerprintHelp(-1, this.mContext.getString(R$string.keyguard_fingerprint_not_match));
        } else {
            handleFingerprintHelp(-1, this.mContext.getString(R$string.keyguard_side_fingerprint_not_match_first_time));
            this.mIsSideFpFailFirstTime = false;
        }
    }

    public final void handleFingerprintAcquired(int i) {
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricAcquired(BiometricSourceType.FINGERPRINT, i);
            }
        }
    }

    public final void handleBiometricDetected(int i, BiometricSourceType biometricSourceType, boolean z) {
        Trace.beginSection("KeyGuardUpdateMonitor#handlerBiometricDetected");
        onBiometricDetected(i, biometricSourceType, z);
        if (biometricSourceType == BiometricSourceType.FINGERPRINT) {
            this.mLogger.logFingerprintDetected(i, z);
        } else if (biometricSourceType == BiometricSourceType.FACE) {
            this.mLogger.logFaceDetected(i, z);
        }
        Trace.endSection();
    }

    public final void handleFingerprintAuthenticated(int i, boolean z) {
        Trace.beginSection("KeyGuardUpdateMonitor#handlerFingerPrintAuthenticated");
        if (this.mHandler.hasCallbacks(this.mFpCancelNotReceived)) {
            this.mLogger.d("handleFingerprintAuthenticated() triggered while waiting for cancellation, removing watchdog");
            this.mHandler.removeCallbacks(this.mFpCancelNotReceived);
        }
        try {
            int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
            if (selectedUserId != i) {
                this.mLogger.logFingerprintAuthForWrongUser(i);
                setFingerprintRunningState(0);
            } else if (isFingerprintDisabled(selectedUserId)) {
                this.mLogger.logFingerprintDisabledForUser(selectedUserId);
                setFingerprintRunningState(0);
            } else {
                ((LiveWallpaperController) Dependency.get(LiveWallpaperController.class)).onAuthenticationSucceeded();
                onFingerprintAuthenticated(selectedUserId, z);
                setFingerprintRunningState(0);
                Trace.endSection();
            }
        } catch (Throwable th) {
            setFingerprintRunningState(0);
            throw th;
        }
    }

    public final void handleFingerprintHelp(int i, String str) {
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricHelp(i, str, BiometricSourceType.FINGERPRINT);
            }
        }
    }

    public final void onFingerprintCancelNotReceived() {
        this.mLogger.e("Fp cancellation not received, transitioning to STOPPED");
        boolean z = this.mFingerprintRunningState == 3;
        this.mFingerprintRunningState = 0;
        this.mFingerprintDetectRunning = false;
        if (z) {
            updateFingerprintListeningState(2);
        } else {
            updateFingerprintListeningState(1);
        }
    }

    public final void handleFingerprintError(int i, String str) {
        boolean z;
        Assert.isMainThread();
        if (this.mHandler.hasCallbacks(this.mFpCancelNotReceived)) {
            this.mHandler.removeCallbacks(this.mFpCancelNotReceived);
        }
        this.mFingerprintCancelSignal = null;
        if (i == 5 && this.mFingerprintRunningState == 3) {
            setFingerprintRunningState(0);
            updateFingerprintListeningState(2);
        } else {
            setFingerprintRunningState(0);
        }
        if (i == 1) {
            this.mLogger.logRetryAfterFpErrorWithDelay(i, str, LockScreenData.DEFAULT_FONT_WEIGHT);
            this.mHandler.postDelayed(this.mRetryFingerprintAuthenticationAfterHwUnavailable, 500L);
        }
        if (i == 19) {
            this.mLogger.logRetryAfterFpErrorWithDelay(i, str, 50);
            this.mHandler.postDelayed(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda14
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$handleFingerprintError$4();
                }
            }, 50L);
        }
        if (i == 9) {
            z = !this.mFingerprintLockedOutPermanent;
            this.mFingerprintLockedOutPermanent = true;
            this.mLogger.d("Fingerprint permanently locked out - requiring stronger auth");
            this.mLockPatternUtils.requireStrongAuth(8, this.mSelectedUserInteractor.getSelectedUserId());
        } else {
            z = false;
        }
        if (i == 7 || i == 9) {
            z |= !this.mFingerprintLockedOut;
            this.mFingerprintLockedOut = true;
            this.mLogger.d("Fingerprint temporarily locked out - requiring stronger auth");
            this.mIsFingerprintFreeze = true;
            if (isUdfpsEnrolled()) {
                updateFingerprintListeningState(2);
            }
            FlymeWideFingerPrintUtils.startQuickLaunch(this.mContext, true);
        }
        this.mLogger.logFingerprintError(i, str);
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricError(i, str, BiometricSourceType.FINGERPRINT);
            }
        }
        if (z) {
            notifyLockedOutStateChanged(BiometricSourceType.FINGERPRINT);
        }
        if (canExecuteFingerprintError(i)) {
            ((LiveWallpaperController) Dependency.get(LiveWallpaperController.class)).onAuthenticationError(i, str);
        }
    }

    public final /* synthetic */ void lambda$handleFingerprintError$4() {
        this.mLogger.d("Retrying fingerprint listening after power pressed error.");
        updateFingerprintListeningState(0);
    }

    public final void handleFingerprintLockoutReset(int i) {
        this.mLogger.logFingerprintLockoutReset(i);
        boolean z = this.mFingerprintLockedOut;
        boolean z2 = this.mFingerprintLockedOutPermanent;
        boolean z3 = true;
        boolean z4 = i == 1 || i == 2;
        this.mFingerprintLockedOut = z4;
        boolean z5 = i == 2;
        this.mFingerprintLockedOutPermanent = z5;
        if (z4 == z && z5 == z2) {
            z3 = false;
        }
        this.mIsFingerprintFreeze = false;
        this.mAuthController.hideFakeFpIcon();
        if (isUdfpsEnrolled()) {
            this.mHandler.postDelayed(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$handleFingerprintLockoutReset$5();
                }
            }, getBiometricLockoutDelay());
        } else {
            if (z && !this.mFingerprintLockedOut) {
                this.mLogger.d("temporaryLockoutReset - stopListeningForFingerprint() to stop detectFingerprint");
                stopListeningForFingerprint();
            }
            updateFingerprintListeningState(2);
        }
        if (z3) {
            notifyLockedOutStateChanged(BiometricSourceType.FINGERPRINT);
        }
    }

    public final /* synthetic */ void lambda$handleFingerprintLockoutReset$5() {
        updateFingerprintListeningState(2);
    }

    public final void setFingerprintRunningState(int i) {
        boolean z = this.mFingerprintRunningState == 1;
        boolean z2 = i == 1;
        this.mFingerprintRunningState = i;
        if (i == 0) {
            this.mFingerprintDetectRunning = false;
        }
        this.mLogger.logFingerprintRunningState(i);
        if (z != z2) {
            notifyFingerprintRunningStateChanged();
        }
    }

    public final void notifyFingerprintRunningStateChanged() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricRunningStateChanged(isFingerprintDetectionRunning(), BiometricSourceType.FINGERPRINT);
            }
        }
    }

    public void onFaceAuthenticated(int i, boolean z) {
        Trace.beginSection("KeyGuardUpdateMonitor#onFaceAuthenticated");
        Assert.isMainThread();
        Log.i("KeyguardUpdateMonitor", "mFaceAuthenticationCallback,onAuthenticationSucceeded");
        ((SystemModeUtils) Dependency.get(SystemModeUtils.class)).switchSystemMode(0);
        if (getUserCanSkipBouncer(i)) {
            this.mTrustManager.unlockedByBiometricForUser(i, BiometricSourceType.FACE);
        }
        updateFingerprintListeningState(2);
        this.mLogger.d("onFaceAuthenticated");
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricAuthenticated(i, BiometricSourceType.FACE, z);
            }
        }
        this.mAssistantVisible = false;
        reportSuccessfulBiometricUnlock(z, i);
        Trace.endSection();
    }

    public final void handleFaceAuthFailed() {
        String str;
        Assert.isMainThread();
        if (this.mKeyguardBypassController.canBypass()) {
            str = "bypass";
        } else if (isAlternateBouncerShowing()) {
            str = "alternateBouncer";
        } else {
            str = isPrimaryBouncerFullyShown() ? "bouncer" : "udfpsFpDown";
        }
        requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.BIOMETRIC_FAIL, "faceFailure-" + str);
        this.mLogger.d("onFaceAuthFailed");
        Log.i("KeyguardUpdateMonitor", "mFaceAuthenticationCallback,onAuthenticationFailed");
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricAuthFailed(BiometricSourceType.FACE);
            }
        }
        handleFaceHelp(-2, this.mContext.getString(R$string.kg_face_not_recognized));
    }

    public final void handleFaceAcquired(int i) {
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricAcquired(BiometricSourceType.FACE, i);
            }
        }
        if (this.mActiveUnlockConfig.shouldRequestActiveUnlockOnFaceAcquireInfo(i)) {
            requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.BIOMETRIC_FAIL, "faceAcquireInfo-" + i);
        }
    }

    public final void handleFaceAuthenticated(int i, boolean z) {
        Trace.beginSection("KeyGuardUpdateMonitor#handlerFaceAuthenticated");
        if (this.mGoingToSleep) {
            this.mLogger.d("Aborted successful auth because device is going to sleep.");
            return;
        }
        int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
        if (selectedUserId != i) {
            this.mLogger.logFaceAuthForWrongUser(i);
            return;
        }
        this.mLogger.logFaceAuthSuccess(selectedUserId);
        onFaceAuthenticated(selectedUserId, z);
        Trace.endSection();
    }

    public final void handleFaceHelp(int i, String str) {
        Assert.isMainThread();
        Log.d("KeyguardUpdateMonitor", "handleFaceHelp: msgId = " + i + " helpString = " + str);
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricHelp(i, str, BiometricSourceType.FACE);
            }
        }
    }

    public final void handleFaceError(int i, String str) {
        Assert.isMainThread();
        this.mLogger.logFaceAuthError(i, str);
        Log.d("KeyguardUpdateMonitor", "onAuthenticationError: errMsgid = " + i + " errString = " + str);
        boolean zIsSensorPrivacyEnabled = this.mSensorPrivacyManager.isSensorPrivacyEnabled(1, 2);
        boolean z = i == 1;
        if (i == 9 && getFaceAuthInteractor() != null && getFaceAuthInteractor().isFaceAuthStrong()) {
            updateFingerprintListeningState(1);
        }
        if (z && zIsSensorPrivacyEnabled) {
            str = this.mContext.getString(R$string.kg_face_sensor_privacy_enabled);
        }
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricError(i, str, BiometricSourceType.FACE);
            }
        }
        if (this.mActiveUnlockConfig.shouldRequestActiveUnlockOnFaceError(i)) {
            requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.BIOMETRIC_FAIL, "faceError-" + i);
        }
    }

    public final void notifyFaceRunningStateChanged() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricRunningStateChanged(isFaceDetectionRunning(), BiometricSourceType.FACE);
            }
        }
    }

    public boolean isFingerprintDetectionRunning() {
        return this.mFingerprintRunningState == 1;
    }

    public boolean isFaceDetectionRunning() {
        return getFaceAuthInteractor() != null && getFaceAuthInteractor().isRunning();
    }

    public final DeviceEntryFaceAuthInteractor getFaceAuthInteractor() {
        return this.mFaceAuthInteractor;
    }

    public void setFaceAuthInteractor(DeviceEntryFaceAuthInteractor deviceEntryFaceAuthInteractor) {
        DeviceEntryFaceAuthInteractor deviceEntryFaceAuthInteractor2 = this.mFaceAuthInteractor;
        if (deviceEntryFaceAuthInteractor2 != null) {
            deviceEntryFaceAuthInteractor2.unregisterListener(this.mFaceAuthenticationListener);
        }
        this.mFaceAuthInteractor = deviceEntryFaceAuthInteractor;
        deviceEntryFaceAuthInteractor.registerListener(this.mFaceAuthenticationListener);
    }

    public final boolean isTrustDisabled() {
        return isSimPinSecure();
    }

    public final boolean isFingerprintDisabled(int i) {
        return (this.mDevicePolicyManager.getKeyguardDisabledFeatures(null, i) & 32) != 0 || isSimPinSecure();
    }

    public boolean getIsFaceAuthenticated() {
        return getFaceAuthInteractor() != null && ((Boolean) getFaceAuthInteractor().isAuthenticated().getValue()).booleanValue();
    }

    public boolean getUserCanSkipBouncer(int i) {
        return getUserHasTrust(i) || getUserUnlockedWithBiometric(i) || forceIsDismissibleIsKeepingDeviceUnlocked();
    }

    public boolean forceIsDismissibleIsKeepingDeviceUnlocked() {
        return this.mFoldGracePeriodProvider.isEnabled() && this.mForceIsDismissible && isUnlockingWithForceKeyguardDismissibleAllowed();
    }

    public boolean getUserHasTrust(int i) {
        return !isTrustDisabled() && this.mUserHasTrust.get(i) && isUnlockingWithTrustAgentAllowed();
    }

    public boolean getUserUnlockedWithBiometric(int i) {
        BiometricAuthenticated biometricAuthenticated = (BiometricAuthenticated) this.mUserFingerprintAuthenticated.get(i);
        return (biometricAuthenticated != null && biometricAuthenticated.mAuthenticated && isUnlockingWithBiometricAllowed(biometricAuthenticated.mIsStrongBiometric)) || (isCurrentUserUnlockedWithFace() && isUnlockingWithBiometricAllowed(BiometricSourceType.FACE));
    }

    public boolean isCurrentUserUnlockedWithFace() {
        return getFaceAuthInteractor() != null && ((Boolean) getFaceAuthInteractor().isAuthenticated().getValue()).booleanValue();
    }

    public boolean getUserUnlockedWithBiometricAndIsBypassing(int i) {
        BiometricAuthenticated biometricAuthenticated = (BiometricAuthenticated) this.mUserFingerprintAuthenticated.get(i);
        if (biometricAuthenticated != null && biometricAuthenticated.mAuthenticated && isUnlockingWithBiometricAllowed(biometricAuthenticated.mIsStrongBiometric)) {
            return true;
        }
        return isCurrentUserUnlockedWithFace() && this.mKeyguardBypassController.canBypass();
    }

    public boolean getUserTrustIsManaged(int i) {
        return this.mUserTrustIsManaged.get(i) && !isTrustDisabled();
    }

    public final void updateSecondaryLockscreenRequirement(int i) {
        Intent intent = (Intent) this.mSecondaryLockscreenRequirement.get(Integer.valueOf(i));
        boolean zIsSecondaryLockscreenEnabled = this.mDevicePolicyManager.isSecondaryLockscreenEnabled(UserHandle.of(i));
        if (zIsSecondaryLockscreenEnabled && intent == null) {
            ComponentName profileOwnerOrDeviceOwnerSupervisionComponent = this.mDevicePolicyManager.getProfileOwnerOrDeviceOwnerSupervisionComponent(UserHandle.of(i));
            if (profileOwnerOrDeviceOwnerSupervisionComponent == null) {
                this.mLogger.logMissingSupervisorAppError(i);
                return;
            }
            ResolveInfo resolveInfoResolveService = this.mPackageManager.resolveService(new Intent("android.app.action.BIND_SECONDARY_LOCKSCREEN_SERVICE").setPackage(profileOwnerOrDeviceOwnerSupervisionComponent.getPackageName()), 0);
            if (resolveInfoResolveService == null || resolveInfoResolveService.serviceInfo == null) {
                return;
            }
            this.mSecondaryLockscreenRequirement.put(Integer.valueOf(i), new Intent().setComponent(resolveInfoResolveService.serviceInfo.getComponentName()));
        } else if (zIsSecondaryLockscreenEnabled || intent == null) {
            return;
        } else {
            this.mSecondaryLockscreenRequirement.put(Integer.valueOf(i), null);
        }
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onSecondaryLockscreenRequirementChanged(i);
            }
        }
    }

    public Intent getSecondaryLockscreenRequirement(int i) {
        return (Intent) this.mSecondaryLockscreenRequirement.get(Integer.valueOf(i));
    }

    public boolean isTrustUsuallyManaged(int i) {
        Assert.isMainThread();
        return this.mUserTrustIsUsuallyManaged.get(i);
    }

    public final boolean isUnlockingWithTrustAgentAllowed() {
        return isUnlockingWithBiometricAllowed(true);
    }

    public final boolean isUnlockingWithForceKeyguardDismissibleAllowed() {
        return isUnlockingWithBiometricAllowed(false);
    }

    public boolean isUnlockingWithBiometricAllowed(boolean z) {
        if (!this.mStrongAuthTracker.isUnlockingWithBiometricAllowed(z)) {
            return false;
        }
        boolean z2 = getFaceAuthInteractor() != null && ((Boolean) getFaceAuthInteractor().isLockedOut().getValue()).booleanValue();
        boolean z3 = getFaceAuthInteractor() != null && getFaceAuthInteractor().isFaceAuthStrong();
        boolean zIsFingerprintLockedOut = isFingerprintLockedOut();
        if ((isFingerprintClass3() && zIsFingerprintLockedOut) || (z3 && z2)) {
            return false;
        }
        return (z2 && zIsFingerprintLockedOut) ? false : true;
    }

    public boolean isUnlockingWithFingerprintAllowed() {
        return isUnlockingWithBiometricAllowed(BiometricSourceType.FINGERPRINT);
    }

    /* JADX INFO: renamed from: com.android.keyguard.KeyguardUpdateMonitor$21, reason: invalid class name */
    public abstract /* synthetic */ class AnonymousClass21 {
        public static final /* synthetic */ int[] $SwitchMap$android$hardware$biometrics$BiometricSourceType;

        static {
            int[] iArr = new int[BiometricSourceType.values().length];
            $SwitchMap$android$hardware$biometrics$BiometricSourceType = iArr;
            try {
                iArr[BiometricSourceType.FINGERPRINT.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$android$hardware$biometrics$BiometricSourceType[BiometricSourceType.FACE.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
        }
    }

    public boolean isUnlockingWithBiometricAllowed(BiometricSourceType biometricSourceType) {
        int i = AnonymousClass21.$SwitchMap$android$hardware$biometrics$BiometricSourceType[biometricSourceType.ordinal()];
        if (i != 1) {
            return i == 2 && getFaceAuthInteractor() != null && isUnlockingWithBiometricAllowed(getFaceAuthInteractor().isFaceAuthStrong());
        }
        return isUnlockingWithBiometricAllowed(isFingerprintClass3());
    }

    public boolean isUserInLockdown(int i) {
        return containsFlag(this.mStrongAuthTracker.getStrongAuthForUser(i), 32);
    }

    public boolean isEncryptedOrLockdown(int i) {
        int strongAuthForUser = this.mStrongAuthTracker.getStrongAuthForUser(i);
        return containsFlag(strongAuthForUser, 1) || (containsFlag(strongAuthForUser, 2) || containsFlag(strongAuthForUser, 32));
    }

    public boolean isDeviceLockedByAdaptiveAuth(int i) {
        return containsFlag(this.mStrongAuthTracker.getStrongAuthForUser(i), 512);
    }

    public boolean needsSlowUnlockTransition() {
        return this.mNeedsSlowUnlockTransition;
    }

    public StrongAuthTracker getStrongAuthTracker() {
        return this.mStrongAuthTracker;
    }

    public void notifyStrongAuthAllowedChanged(int i) {
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onStrongAuthStateChanged(i);
            }
        }
        if (i == this.mSelectedUserInteractor.getSelectedUserId()) {
            updateFingerprintListeningState(1);
        }
    }

    public final void notifyLockedOutStateChanged(BiometricSourceType biometricSourceType) {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onLockedOutStateChanged(biometricSourceType);
            }
        }
    }

    public void notifyNonStrongBiometricAllowedChanged(int i) {
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onNonStrongBiometricAllowedChanged(i);
            }
        }
        if (i == this.mSelectedUserInteractor.getSelectedUserId()) {
            updateFingerprintListeningState(1);
        }
    }

    public final void dispatchErrorMessage(CharSequence charSequence) {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTrustAgentErrorMessage(charSequence);
            }
        }
    }

    public void setAssistantVisible(boolean z) {
        this.mAssistantVisible = z;
        this.mLogger.logAssistantVisible(z);
        updateFingerprintListeningState(2);
        if (this.mAssistantVisible) {
            if (getFaceAuthInteractor() != null) {
                getFaceAuthInteractor().onAssistantTriggeredOnLockScreen();
            }
            requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.ASSISTANT, "assistant", true);
        }
    }

    public class SimData {
        public int simState;
        public int slotId;
        public int subId;

        public SimData(int i, int i2, int i3) {
            this.simState = i;
            this.slotId = i2;
            this.subId = i3;
        }

        /* JADX WARN: Code duplicated, block: B:17:0x0057  */
        /* JADX WARN: Code duplicated, block: B:40:0x00a3  */
        public static SimData fromIntent(Intent intent) {
            int i;
            if (!"android.intent.action.SIM_STATE_CHANGED".equals(intent.getAction())) {
                throw new IllegalArgumentException("only handles intent ACTION_SIM_STATE_CHANGED");
            }
            String stringExtra = intent.getStringExtra("ss");
            int intExtra = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1);
            int intExtra2 = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
            if ("ABSENT".equals(stringExtra)) {
                i = 1;
            } else if ("LOCKED".equals(stringExtra)) {
                String stringExtra2 = intent.getStringExtra("reason");
                if ("PIN".equals(stringExtra2)) {
                    i = 2;
                } else if ("PUK".equals(stringExtra2)) {
                    i = 3;
                } else if ("PERM_DISABLED".equals(stringExtra2)) {
                    i = 7;
                } else if ("NETWORK".equals(stringExtra2)) {
                    i = 4;
                } else if ("PERM_DISABLED".equals(stringExtra2)) {
                    i = 7;
                } else {
                    i = 0;
                }
            } else if ("CARD_IO_ERROR".equals(stringExtra)) {
                i = 8;
            } else if ("CARD_RESTRICTED".equals(stringExtra)) {
                i = 9;
            } else if ("NOT_READY".equals(stringExtra)) {
                i = 6;
            } else if ("READY".equals(stringExtra) || "LOADED".equals(stringExtra) || "IMSI".equals(stringExtra)) {
                i = 5;
            } else {
                i = 0;
            }
            return new SimData(i, intExtra, intExtra2);
        }

        public String toString() {
            return "SimData{state=" + this.simState + ",slotId=" + this.slotId + ",subId=" + this.subId + "}";
        }
    }

    public class StrongAuthTracker extends LockPatternUtils.StrongAuthTracker {
        public StrongAuthTracker(Context context) {
            super(context);
        }

        public boolean isUnlockingWithBiometricAllowed(boolean z) {
            return isBiometricAllowedForUser(z, KeyguardUpdateMonitor.this.mSelectedUserInteractor.getSelectedUserId());
        }

        public boolean hasUserAuthenticatedSinceBoot() {
            return (getStrongAuthForUser(KeyguardUpdateMonitor.this.mSelectedUserInteractor.getSelectedUserId()) & 1) == 0;
        }

        public void onStrongAuthRequiredChanged(int i) {
            KeyguardUpdateMonitor.this.notifyStrongAuthAllowedChanged(i);
        }

        public void onIsNonStrongBiometricAllowedChanged(int i) {
            KeyguardUpdateMonitor.this.notifyNonStrongBiometricAllowedChanged(i);
        }
    }

    public void handleStartedWakingUp(int i) {
        Trace.beginSection("KeyguardUpdateMonitor#handleStartedWakingUp");
        Assert.isMainThread();
        if (this.mLaunchingApp) {
            this.mLaunchingApp = false;
        }
        updateFingerprintListeningState(2);
        requestActiveUnlockFromWakeReason(i, true);
        this.mAuthController.showFingerprintIcon();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onStartedWakingUp();
            }
        }
        Trace.endSection();
    }

    public void handleStartedGoingToSleep(int i) {
        Assert.isMainThread();
        setForceIsDismissibleKeyguard(false);
        clearFingerprintRecognized();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onStartedGoingToSleep(i);
            }
        }
        this.mGoingToSleep = true;
        this.mAssistantVisible = false;
        this.mLaunchingApp = false;
        this.mPasswordMatched = false;
        this.mLogger.d("Started going to sleep, mGoingToSleep=true, mAssistantVisible=false");
        updateFingerprintListeningState(2);
    }

    public void handleFinishedGoingToSleep(int i) {
        Assert.isMainThread();
        this.mGoingToSleep = false;
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onFinishedGoingToSleep(i);
            }
        }
        updateFingerprintListeningState(2);
    }

    public final void handleScreenTurnedOff() {
        Assert.isMainThread();
        this.mHardwareFingerprintUnavailableRetryCount = 0;
    }

    public final void handleDreamingStateChanged(int i) {
        Assert.isMainThread();
        this.mIsDreaming = i == 1;
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onDreamingStateChanged(this.mIsDreaming);
            }
        }
        updateFingerprintListeningState(2);
    }

    public final void handleUserUnlocked(int i) {
        Assert.isMainThread();
        this.mLogger.logUserUnlocked(i);
        this.mUserIsUnlocked.put(i, true);
        this.mNeedsSlowUnlockTransition = resolveNeedsSlowUnlockTransition();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onUserUnlocked();
            }
        }
    }

    public final void handleUserStopped(int i) {
        Assert.isMainThread();
        boolean zIsUserUnlocked = this.mUserManager.isUserUnlocked(i);
        this.mLogger.logUserStopped(i, zIsUserUnlocked);
        this.mUserIsUnlocked.put(i, zIsUserUnlocked);
    }

    public void handleUserRemoved(int i) {
        Assert.isMainThread();
        this.mLogger.logUserRemoved(i);
        this.mUserIsUnlocked.delete(i);
        this.mUserTrustIsUsuallyManaged.delete(i);
    }

    public final void handleKeyguardGoingAway(boolean z) {
        Assert.isMainThread();
        setKeyguardGoingAway(z);
    }

    public void setStrongAuthTracker(StrongAuthTracker strongAuthTracker) {
        StrongAuthTracker strongAuthTracker2 = this.mStrongAuthTracker;
        if (strongAuthTracker2 != null) {
            this.mLockPatternUtils.unregisterStrongAuthTracker(strongAuthTracker2);
        }
        this.mStrongAuthTracker = strongAuthTracker;
        this.mLockPatternUtils.registerStrongAuthTracker(strongAuthTracker);
    }

    public void resetBiometricListeningState() {
        this.mFingerprintRunningState = 0;
        this.mFingerprintDetectRunning = false;
    }

    public KeyguardUpdateMonitor(Context context, UserTracker userTracker, Looper looper, BroadcastDispatcher broadcastDispatcher, DumpManager dumpManager, Executor executor, Executor executor2, StatusBarStateController statusBarStateController, LockPatternUtils lockPatternUtils, AuthController authController, TelephonyListenerManager telephonyListenerManager, InteractionJankMonitor interactionJankMonitor, LatencyTracker latencyTracker, ActiveUnlockConfig activeUnlockConfig, KeyguardUpdateMonitorLogger keyguardUpdateMonitorLogger, SimLogger simLogger, UiEventLogger uiEventLogger, Provider provider, TrustManager trustManager, SubscriptionManager subscriptionManager, UserManager userManager, IDreamManager iDreamManager, DevicePolicyManager devicePolicyManager, SensorPrivacyManager sensorPrivacyManager, TelephonyManager telephonyManager, PackageManager packageManager, FaceManager faceManager, FingerprintManager fingerprintManager, BiometricManager biometricManager, FaceWakeUpTriggersConfig faceWakeUpTriggersConfig, DevicePostureController devicePostureController, Optional optional, TaskStackChangeListeners taskStackChangeListeners, SelectedUserInteractor selectedUserInteractor, IActivityTaskManager iActivityTaskManager, Provider provider2, Provider provider3, Provider provider4, Provider provider5, Provider provider6) {
        StatusBarStateController.StateListener stateListener = new StatusBarStateController.StateListener() { // from class: com.android.keyguard.KeyguardUpdateMonitor.1
            @Override // com.android.systemui.plugins.statusbar.StatusBarStateController.StateListener
            public void onStateChanged(int i) {
                KeyguardUpdateMonitor.this.mStatusBarState = i;
            }

            @Override // com.android.systemui.plugins.statusbar.StatusBarStateController.StateListener
            public void onExpandedChanged(boolean z) {
                for (int i = 0; i < KeyguardUpdateMonitor.this.mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) KeyguardUpdateMonitor.this.mCallbacks.get(i)).get();
                    if (keyguardUpdateMonitorCallback != null) {
                        keyguardUpdateMonitorCallback.onShadeExpandedChanged(z);
                    }
                }
            }
        };
        this.mStatusBarStateControllerListener = stateListener;
        this.mSimDataLockObject = new Object();
        this.mSimDatas = new HashMap();
        this.mSimDatasBySlotId = new HashMap();
        this.mServiceStates = new HashMap();
        this.mServiceStatesWithSlotid = new HashMap();
        this.mBatteryStatus = null;
        this.mCallbacks = Lists.newArrayList();
        this.mFoldGracePeriodProvider = new FoldGracePeriodProvider();
        this.mFingerprintRunningState = 0;
        this.mActiveMobileDataSubscription = -1;
        this.mHardwareFingerprintUnavailableRetryCount = 0;
        this.mFpCancelNotReceived = new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.onFingerprintCancelNotReceived();
            }
        };
        this.mBiometricEnabledCallback = new AnonymousClass2();
        this.mPhoneStateListener = new TelephonyCallback.ActiveDataSubscriptionIdListener() { // from class: com.android.keyguard.KeyguardUpdateMonitor.3
            @Override // android.telephony.TelephonyCallback.ActiveDataSubscriptionIdListener
            public void onActiveDataSubscriptionIdChanged(int i) {
                KeyguardUpdateMonitor.this.mActiveMobileDataSubscription = i;
                KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(328);
            }
        };
        this.mSubscriptionListener = new SubscriptionManager.OnSubscriptionsChangedListener() { // from class: com.android.keyguard.KeyguardUpdateMonitor.4
            @Override // android.telephony.SubscriptionManager.OnSubscriptionsChangedListener
            public void onSubscriptionsChanged() {
                KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(328);
            }
        };
        this.mUserIsUnlocked = new SparseBooleanArray();
        this.mUserHasTrust = new SparseBooleanArray();
        this.mUserTrustIsManaged = new SparseBooleanArray();
        this.mUserTrustIsUsuallyManaged = new SparseBooleanArray();
        this.mBiometricEnabledForUser = new SparseBooleanArray();
        this.mSecondaryLockscreenRequirement = new HashMap();
        this.mFingerprintListenBuffer = new KeyguardFingerprintListenModel.Buffer();
        this.mActiveUnlockTriggerBuffer = new KeyguardActiveUnlockModel.Buffer();
        this.mUserFingerprintAuthenticated = new SparseArray();
        this.mRetryFingerprintAuthenticationAfterHwUnavailable = new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor.5
            @Override // java.lang.Runnable
            public void run() {
                KeyguardUpdateMonitor.this.mLogger.logRetryAfterFpHwUnavailable(KeyguardUpdateMonitor.this.mHardwareFingerprintUnavailableRetryCount);
                if (!KeyguardUpdateMonitor.this.mFingerprintSensorProperties.isEmpty()) {
                    KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
                } else if (KeyguardUpdateMonitor.this.mHardwareFingerprintUnavailableRetryCount < 20) {
                    KeyguardUpdateMonitor.this.mHardwareFingerprintUnavailableRetryCount++;
                    KeyguardUpdateMonitor.this.mHandler.postDelayed(KeyguardUpdateMonitor.this.mRetryFingerprintAuthenticationAfterHwUnavailable, 500L);
                }
            }
        };
        this.mFaceAuthenticationListener = new FaceAuthenticationListener() { // from class: com.android.keyguard.KeyguardUpdateMonitor.6
            @Override // com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener
            public void onAuthenticatedChanged(boolean z) {
                if (z) {
                    return;
                }
                for (int i = 0; i < KeyguardUpdateMonitor.this.mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) KeyguardUpdateMonitor.this.mCallbacks.get(i)).get();
                    if (keyguardUpdateMonitorCallback != null) {
                        keyguardUpdateMonitorCallback.onFacesCleared();
                    }
                }
            }

            @Override // com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener
            public void onAuthEnrollmentStateChanged(boolean z) {
                KeyguardUpdateMonitor.this.notifyAboutEnrollmentChange(8);
            }

            @Override // com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener
            public void onRunningStateChanged(boolean z) {
                KeyguardUpdateMonitor.this.notifyFaceRunningStateChanged();
            }

            @Override // com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener
            public void onLockoutStateChanged(boolean z) {
                KeyguardUpdateMonitor.this.notifyLockedOutStateChanged(BiometricSourceType.FACE);
            }

            @Override // com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener
            public void onAuthenticationStatusChanged(FaceAuthenticationStatus faceAuthenticationStatus) {
                if (faceAuthenticationStatus instanceof AcquiredFaceAuthenticationStatus) {
                    KeyguardUpdateMonitor.this.handleFaceAcquired(((AcquiredFaceAuthenticationStatus) faceAuthenticationStatus).getAcquiredInfo());
                } else if (faceAuthenticationStatus instanceof ErrorFaceAuthenticationStatus) {
                    ErrorFaceAuthenticationStatus errorFaceAuthenticationStatus = (ErrorFaceAuthenticationStatus) faceAuthenticationStatus;
                    KeyguardUpdateMonitor.this.handleFaceError(errorFaceAuthenticationStatus.getMsgId(), errorFaceAuthenticationStatus.getMsg());
                } else if (faceAuthenticationStatus instanceof FailedFaceAuthenticationStatus) {
                    KeyguardUpdateMonitor.this.handleFaceAuthFailed();
                } else if (faceAuthenticationStatus instanceof HelpFaceAuthenticationStatus) {
                    HelpFaceAuthenticationStatus helpFaceAuthenticationStatus = (HelpFaceAuthenticationStatus) faceAuthenticationStatus;
                    KeyguardUpdateMonitor.this.handleFaceHelp(helpFaceAuthenticationStatus.getMsgId(), helpFaceAuthenticationStatus.getMsg());
                } else if (faceAuthenticationStatus instanceof SuccessFaceAuthenticationStatus) {
                    FaceManager.AuthenticationResult successResult = ((SuccessFaceAuthenticationStatus) faceAuthenticationStatus).getSuccessResult();
                    KeyguardUpdateMonitor.this.handleFaceAuthenticated(successResult.getUserId(), successResult.isStrongBiometric());
                }
                KeyguardUpdateMonitor.this.notifyAuthenticationStatusChanged(faceAuthenticationStatus);
            }

            @Override // com.android.systemui.deviceentry.domain.interactor.FaceAuthenticationListener
            public void onDetectionStatusChanged(FaceDetectionStatus faceDetectionStatus) {
                KeyguardUpdateMonitor.this.handleBiometricDetected(faceDetectionStatus.getUserId(), BiometricSourceType.FACE, faceDetectionStatus.isStrongBiometric());
            }
        };
        this.mBroadcastReceiver = new BroadcastReceiver() { // from class: com.android.keyguard.KeyguardUpdateMonitor.7
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String action = intent.getAction();
                KeyguardUpdateMonitor.this.mLogger.logBroadcastReceived(action);
                if ("android.intent.action.TIME_TICK".equals(action) || "android.intent.action.TIME_SET".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(301);
                    return;
                }
                if ("android.intent.action.TIMEZONE_CHANGED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(339, intent.getStringExtra("time-zone")));
                    return;
                }
                if ("android.intent.action.BATTERY_CHANGED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(302, new BatteryStatus(intent, KeyguardUpdateMonitor.this.mIncompatibleCharger)));
                    return;
                }
                if ("android.hardware.usb.action.USB_PORT_COMPLIANCE_CHANGED".equals(action)) {
                    KeyguardUpdateMonitor.this.mIncompatibleCharger = Utils.containsIncompatibleChargers(context2, "KeyguardUpdateMonitor");
                    BatteryStatus batteryStatusCreate = BatteryStatus.create(context2, KeyguardUpdateMonitor.this.mIncompatibleCharger);
                    if (batteryStatusCreate != null) {
                        KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(302, batteryStatusCreate));
                        return;
                    }
                    return;
                }
                if ("android.intent.action.SIM_STATE_CHANGED".equals(action)) {
                    SimData simDataFromIntent = SimData.fromIntent(intent);
                    if (intent.getBooleanExtra("rebroadcastOnUnlock", false)) {
                        if (simDataFromIntent.simState == 1) {
                            KeyguardUpdateMonitor.this.mHandler.obtainMessage(338, Boolean.TRUE).sendToTarget();
                            return;
                        }
                        return;
                    } else {
                        KeyguardUpdateMonitor.this.mSimLogger.logSimStateFromIntent(action, intent.getStringExtra("ss"), simDataFromIntent.slotId, simDataFromIntent.subId);
                        if (simDataFromIntent.slotId == -1) {
                            return;
                        }
                        KeyguardUpdateMonitor.this.mHandler.obtainMessage(304, simDataFromIntent.subId, simDataFromIntent.slotId, Integer.valueOf(simDataFromIntent.simState)).sendToTarget();
                        return;
                    }
                }
                if ("android.intent.action.PHONE_STATE".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(306, intent.getStringExtra(WeatherData.STATE_KEY)));
                    return;
                }
                if ("android.telephony.action.SERVICE_PROVIDERS_UPDATED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.obtainMessage(347, intent).sendToTarget();
                    return;
                }
                if ("android.intent.action.AIRPLANE_MODE".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(329);
                    return;
                }
                if ("android.intent.action.SERVICE_STATE".equals(action)) {
                    ServiceState serviceStateNewFromBundle = ServiceState.newFromBundle(intent.getExtras());
                    int intExtra = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
                    int intExtra2 = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1);
                    KeyguardUpdateMonitor.this.mSimLogger.logServiceStateIntent(action, serviceStateNewFromBundle, intExtra, intExtra2);
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(330, intExtra, intExtra2, serviceStateNewFromBundle));
                    return;
                }
                if ("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(328);
                    return;
                }
                if ("com.meizu.HAS_FACE_ENROLL_CHANGED".equals(action)) {
                    Log.i("KeyguardUpdateMonitor", " HAS_FACE_ENROLL_CHANGED_BROADCAST" + KeyguardUpdateMonitor.this.mFaceManager);
                    try {
                        if (KeyguardUpdateMonitor.this.mFaceManager != null) {
                            int size = KeyguardUpdateMonitor.this.mFaceManager.getEnrolledFaces(KeyguardUpdateMonitor.this.getCurrentUser()).size();
                            Log.i("KeyguardUpdateMonitor", " count-" + size);
                            if (size > 0) {
                                Settings.Secure.putInt(KeyguardUpdateMonitor.this.mContext.getContentResolver(), "lock_screen_allow_private_notifications", 1);
                                Log.i("KeyguardUpdateMonitor", " Face count " + size + ", Reset LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS -> 1");
                                return;
                            }
                            if (Settings.Secure.getInt(KeyguardUpdateMonitor.this.mContext.getContentResolver(), "lock_screen_allow_private_notifications") == 1) {
                                Settings.Secure.putInt(KeyguardUpdateMonitor.this.mContext.getContentResolver(), "lock_screen_allow_private_notifications", 2);
                                Log.i("KeyguardUpdateMonitor", " Face count " + size + ", Reset LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS -> 2");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        this.mBroadcastAllReceiver = new BroadcastReceiver() { // from class: com.android.keyguard.KeyguardUpdateMonitor.8
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context2, Intent intent) {
                String action = intent.getAction();
                if ("android.app.action.NEXT_ALARM_CLOCK_CHANGED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(301);
                    return;
                }
                if ("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(309, getSendingUserId(), 0));
                    return;
                }
                if ("android.intent.action.USER_UNLOCKED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(334, getSendingUserId(), 0));
                    return;
                }
                if ("android.intent.action.USER_STOPPED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(340, intent.getIntExtra("android.intent.extra.user_handle", -1), 0));
                } else if ("android.intent.action.USER_REMOVED".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(341, intent.getIntExtra("android.intent.extra.user_handle", -1), 0));
                } else if ("android.nfc.action.REQUIRE_UNLOCK_FOR_NFC".equals(action)) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(345);
                }
            }
        };
        this.mFingerprintLockoutResetCallback = new FingerprintManager.LockoutResetCallback() { // from class: com.android.keyguard.KeyguardUpdateMonitor.9
            public void onLockoutReset(int i) {
                KeyguardUpdateMonitor.this.handleFingerprintLockoutReset(0);
            }
        };
        this.mFingerprintAuthenticationCallback = new FingerprintManager.AuthenticationCallback() { // from class: com.android.keyguard.KeyguardUpdateMonitor.10
            @Override // android.hardware.fingerprint.FingerprintManager.AuthenticationCallback
            public void onAuthenticationFailed() {
                Log.d("KeyguardUpdateMonitor", "Fingerprint ====onAuthenticationFailed====");
                if (!KeyguardUpdateMonitor.this.isRealInteractive()) {
                    if (KeyguardUpdateMonitor.this.mIsFPVisibleWhenAODAuth) {
                        SystemUIMobEventUtils.collectAODFingerprintUnlockInfo(KeyguardUpdateMonitor.this.mContext, ExifInterface.GPS_MEASUREMENT_3D);
                    } else {
                        SystemUIMobEventUtils.collectAODFingerprintUnlockInfo(KeyguardUpdateMonitor.this.mContext, "1");
                    }
                }
                KeyguardUpdateMonitor.this.requestActiveUnlockDismissKeyguard(ActiveUnlockConfig.ActiveUnlockRequestOrigin.BIOMETRIC_FAIL, "fingerprintFailure");
                KeyguardUpdateMonitor.this.handleFingerprintAuthFailed();
            }

            @Override // android.hardware.fingerprint.FingerprintManager.AuthenticationCallback
            public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult authenticationResult) {
                Trace.beginSection("KeyguardUpdateMonitor#onAuthenticationSucceeded");
                Log.d("KeyguardUpdateMonitor", "Fingerprint  ======onAuthenticationSucceeded=======");
                if (!KeyguardUpdateMonitor.this.isRealInteractive()) {
                    if (KeyguardUpdateMonitor.this.mIsFPVisibleWhenAODAuth) {
                        SystemUIMobEventUtils.collectAODFingerprintUnlockInfo(KeyguardUpdateMonitor.this.mContext, ExifInterface.GPS_MEASUREMENT_2D);
                    } else {
                        SystemUIMobEventUtils.collectAODFingerprintUnlockInfo(KeyguardUpdateMonitor.this.mContext, "0");
                    }
                }
                Log.i("KeyguardUpdateMonitor", "onAuthenticationSucceeded: result = " + authenticationResult + " fp = " + authenticationResult.getFingerprint());
                if (authenticationResult.getFingerprint() == null) {
                    return;
                }
                KeyguardUpdateMonitor keyguardUpdateMonitor = KeyguardUpdateMonitor.this;
                keyguardUpdateMonitor.mFPAuthSystemMode = keyguardUpdateMonitor.getSystemModeByFingerId(authenticationResult.getFingerprint().getBiometricId());
                if (KeyguardUpdateMonitor.this.mFPAuthSystemMode == 2 && ((SettingsUpdateMonitor) Dependency.get(SettingsUpdateMonitor.class)).isMzPowerSaveMode()) {
                    KeyguardUpdateMonitor.this.handleFingerprintAuthFailed();
                    return;
                }
                KeyguardUpdateMonitor.this.handleFingerprintAuthenticated(authenticationResult.getUserId(), authenticationResult.isStrongBiometric());
                FlymeWideFingerPrintUtils.startQuickLaunch(KeyguardUpdateMonitor.this.mContext, false);
                SystemUIMobEventUtils.collectUnlockMode(KeyguardUpdateMonitor.this.mContext, KeyguardUpdateMonitor.this.getCurrentSystemMode(), 0);
                Trace.endSection();
            }

            @Override // android.hardware.fingerprint.FingerprintManager.AuthenticationCallback
            public void onAuthenticationHelp(int i, CharSequence charSequence) {
                Log.d("KeyguardUpdateMonitor", "Fingerprint  ======onAuthenticationHelp======helpMsgId=" + i + ", helpString=" + ((Object) charSequence));
                if (i == 1000 || i == 1001 || i == 1002 || i == 1003 || i == 5 || TextUtils.isEmpty(charSequence)) {
                    return;
                }
                Trace.beginSection("KeyguardUpdateMonitor#onAuthenticationHelp");
                KeyguardUpdateMonitor.this.handleFingerprintHelp(i, charSequence.toString());
                Trace.endSection();
            }

            @Override // android.hardware.fingerprint.FingerprintManager.AuthenticationCallback
            public void onAuthenticationError(int i, CharSequence charSequence) {
                if (charSequence == null) {
                    charSequence = "";
                }
                Log.d("KeyguardUpdateMonitor", "Fingerprint  ======onAuthenticationError=======errMsgId=" + i + ", errString=" + charSequence.toString());
                Trace.beginSection("KeyguardUpdateMonitor#onAuthenticationError");
                KeyguardUpdateMonitor.this.handleFingerprintError(i, charSequence.toString());
                Trace.endSection();
            }

            public void onAuthenticationAcquired(int i) {
                Trace.beginSection("KeyguardUpdateMonitor#onAuthenticationAcquired");
                KeyguardUpdateMonitor.this.mLogger.logFingerprintAcquired(i);
                KeyguardUpdateMonitor.this.handleFingerprintAcquired(i);
                Trace.endSection();
            }

            public void onUdfpsPointerDown(int i) {
                Log.d("KeyguardUpdateMonitor", "onUdfpsPointerDown, sensorId: " + i);
                KeyguardUpdateMonitor.this.mLogger.logUdfpsPointerDown(i);
            }

            public void onUdfpsPointerUp(int i) {
                KeyguardUpdateMonitor.this.mLogger.logUdfpsPointerUp(i);
            }
        };
        this.mFingerprintDetectionCallback = new FingerprintManager.FingerprintDetectionCallback() { // from class: com.android.keyguard.KeyguardUpdateMonitor.11
            public void onDetectionError(int i) {
                KeyguardUpdateMonitor.this.handleFingerprintError(i, "");
            }

            public void onFingerprintDetected(int i, int i2, boolean z) {
                if (KeyguardUpdateMonitor.this.mHandler.hasCallbacks(KeyguardUpdateMonitor.this.mFpCancelNotReceived)) {
                    KeyguardUpdateMonitor.this.mLogger.d("onFingerprintDetected() triggered while waiting for cancellation, removing watchdog");
                    KeyguardUpdateMonitor.this.mHandler.removeCallbacks(KeyguardUpdateMonitor.this.mFpCancelNotReceived);
                }
                KeyguardUpdateMonitor keyguardUpdateMonitor = KeyguardUpdateMonitor.this;
                keyguardUpdateMonitor.mFingerprintCancelSignal = null;
                keyguardUpdateMonitor.setFingerprintRunningState(0);
                KeyguardUpdateMonitor.this.handleBiometricDetected(i2, BiometricSourceType.FINGERPRINT, z);
            }
        };
        this.mPostureCallback = new DevicePostureController.Callback() { // from class: com.android.keyguard.KeyguardUpdateMonitor.12
            @Override // com.android.systemui.statusbar.policy.DevicePostureController.Callback
            public void onPostureChanged(int i) {
                if (i == 3) {
                    KeyguardUpdateMonitor.this.mLogger.d("Posture changed to open - attempting to request active unlock and run face auth");
                    if (KeyguardUpdateMonitor.this.getFaceAuthInteractor() != null) {
                        KeyguardUpdateMonitor.this.getFaceAuthInteractor().onDeviceUnfolded();
                    }
                    KeyguardUpdateMonitor.this.requestActiveUnlockFromWakeReason(12, false);
                }
            }
        };
        this.mFingerprintSensorProperties = Collections.EMPTY_LIST;
        this.mUserChangedCallback = new UserTracker.Callback() { // from class: com.android.keyguard.KeyguardUpdateMonitor.17
            @Override // com.android.systemui.settings.UserTracker.Callback
            public void onUserChanging(int i, Context context2, Runnable runnable) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(310, i, 0, runnable));
            }

            @Override // com.android.systemui.settings.UserTracker.Callback
            public void onUserChanged(int i, Context context2) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(314, i, 0));
            }
        };
        this.mTaskStackListener = new TaskStackChangeListener() { // from class: com.android.keyguard.KeyguardUpdateMonitor.19
            @Override // com.android.systemui.shared.system.TaskStackChangeListener
            public void onTaskStackChangedBackground() {
                ComponentName componentName;
                try {
                    boolean z = true;
                    ActivityTaskManager.RootTaskInfo rootTaskInfo = KeyguardUpdateMonitor.this.mActivityTaskManager.getRootTaskInfo(1, 1);
                    boolean z2 = KeyguardUpdateMonitor.this.mAllowFingerprintOnCurrentOccludingActivity;
                    KeyguardUpdateMonitor keyguardUpdateMonitor = KeyguardUpdateMonitor.this;
                    if (rootTaskInfo == null || (componentName = rootTaskInfo.topActivity) == null || TextUtils.isEmpty(componentName.getPackageName()) || !KeyguardUpdateMonitor.this.mAllowFingerprintOnOccludingActivitiesFromPackage.contains(rootTaskInfo.topActivity.getPackageName()) || !rootTaskInfo.visible) {
                        z = false;
                    }
                    keyguardUpdateMonitor.mAllowFingerprintOnCurrentOccludingActivity = z;
                    if (KeyguardUpdateMonitor.this.mAllowFingerprintOnCurrentOccludingActivity != z2) {
                        KeyguardUpdateMonitor.this.mLogger.allowFingerprintOnCurrentOccludingActivityChanged(KeyguardUpdateMonitor.this.mAllowFingerprintOnCurrentOccludingActivity);
                        KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
                    }
                    ActivityTaskManager.RootTaskInfo rootTaskInfo2 = KeyguardUpdateMonitor.this.mActivityTaskManager.getRootTaskInfo(0, 4);
                    if (rootTaskInfo2 == null) {
                        return;
                    }
                    KeyguardUpdateMonitor.this.mLogger.logTaskStackChangedForAssistant(rootTaskInfo2.visible);
                    KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(335, Boolean.valueOf(rootTaskInfo2.visible)));
                } catch (RemoteException e) {
                    KeyguardUpdateMonitor.this.mLogger.logException(e, "unable to check task stack ");
                }
            }
        };
        this.mControlScreenOff = false;
        this.mKeyguardDismissPauseForEditor = false;
        this.mIsLaunchUIGroupSelectActivity = false;
        this.mCenterState = 0;
        this.mIsFPVisibleWhenAODAuth = false;
        this.mFPAuthSystemMode = 0;
        this.mCameraGestureTrigger = false;
        this.mIsSideFpFailFirstTime = true;
        this.FACE_HIDL_SENSOR_ID = 1;
        this.mContext = context;
        this.mSubscriptionManager = subscriptionManager;
        this.mUserTracker = userTracker;
        this.mTelephonyListenerManager = telephonyListenerManager;
        this.mDeviceProvisioned = isDeviceProvisionedInSettingsDb();
        this.mStrongAuthTracker = new StrongAuthTracker(context);
        this.mBackgroundExecutor = executor;
        this.mMainExecutor = executor2;
        this.mBroadcastDispatcher = broadcastDispatcher;
        this.mInteractionJankMonitor = interactionJankMonitor;
        this.mLatencyTracker = latencyTracker;
        this.mStatusBarStateController = statusBarStateController;
        statusBarStateController.addCallback(stateListener);
        this.mStatusBarState = statusBarStateController.getState();
        this.mLockPatternUtils = lockPatternUtils;
        this.mAuthController = authController;
        dumpManager.registerDumpable(this);
        this.mSensorPrivacyManager = sensorPrivacyManager;
        this.mActiveUnlockConfig = activeUnlockConfig;
        this.mLogger = keyguardUpdateMonitorLogger;
        this.mSimLogger = simLogger;
        this.mUiEventLogger = uiEventLogger;
        this.mSessionTrackerProvider = provider;
        this.mTrustManager = trustManager;
        this.mUserManager = userManager;
        this.mDreamManager = iDreamManager;
        this.mTelephonyManager = telephonyManager;
        this.mDevicePolicyManager = devicePolicyManager;
        this.mPackageManager = packageManager;
        this.mFpm = fingerprintManager;
        this.mFaceManager = faceManager;
        this.mBiometricManager = biometricManager;
        this.mConfigFaceAuthSupportedPosture = context.getResources().getInteger(R$integer.config_face_auth_supported_posture);
        this.mFaceWakeUpTriggersConfig = faceWakeUpTriggersConfig;
        this.mAllowFingerprintOnOccludingActivitiesFromPackage = (Set) Arrays.stream(context.getResources().getStringArray(R$array.config_fingerprint_listen_on_occluding_activity_packages)).collect(Collectors.toSet());
        this.mDevicePostureController = devicePostureController;
        this.mTaskStackChangeListeners = taskStackChangeListeners;
        this.mActivityTaskManager = iActivityTaskManager;
        this.mSelectedUserInteractor = selectedUserInteractor;
        MediaControllerCompat$Callback$StubCompat$$ExternalSyntheticThrowCCEIfNotNull0.m(optional.orElse(null));
        this.mIsSystemUser = userManager.isSystemUser();
        this.mAlternateBouncerInteractor = provider2;
        this.mJavaAdapter = provider3;
        this.mSceneInteractor = provider4;
        this.mCommunalSceneInteractor = provider5;
        this.mKeyguardServiceShowLockscreenInteractor = provider6;
        Handler handler = new Handler(looper) { // from class: com.android.keyguard.KeyguardUpdateMonitor.13
            @Override // android.os.Handler
            public void handleMessage(Message message) {
                int i = message.what;
                if (i == 301) {
                    KeyguardUpdateMonitor.this.handleTimeUpdate();
                    return;
                }
                if (i == 302) {
                    KeyguardUpdateMonitor.this.handleBatteryUpdate((BatteryStatus) message.obj);
                    return;
                }
                if (i == 304) {
                    KeyguardUpdateMonitor.this.handleSimStateChange(message.arg1, message.arg2, ((Integer) message.obj).intValue());
                    return;
                }
                if (i == 306) {
                    KeyguardUpdateMonitor.this.handlePhoneStateChanged((String) message.obj);
                    return;
                }
                if (i == 312) {
                    KeyguardUpdateMonitor.this.handleKeyguardReset();
                    return;
                }
                if (i == 314) {
                    KeyguardUpdateMonitor.this.handleUserSwitchComplete(message.arg1);
                    return;
                }
                if (i == 530) {
                    KeyguardUpdateMonitor.this.handleWelcomePageChange();
                    return;
                }
                if (i != 532) {
                    switch (i) {
                        case 308:
                            KeyguardUpdateMonitor.this.handleDeviceProvisioned();
                            break;
                        case 309:
                            KeyguardUpdateMonitor.this.handleDevicePolicyManagerStateChanged(message.arg1);
                            break;
                        case 310:
                            KeyguardUpdateMonitor.this.handleUserSwitching(message.arg1, (Runnable) message.obj);
                            break;
                        default:
                            switch (i) {
                                case 318:
                                    KeyguardUpdateMonitor.this.handleReportEmergencyCallAction();
                                    break;
                                case 319:
                                    Trace.beginSection("KeyguardUpdateMonitor#handler MSG_STARTED_WAKING_UP");
                                    KeyguardUpdateMonitor.this.handleStartedWakingUp(message.arg1);
                                    Trace.endSection();
                                    break;
                                case 320:
                                    KeyguardUpdateMonitor.this.handleFinishedGoingToSleep(message.arg1);
                                    break;
                                case 321:
                                    KeyguardUpdateMonitor.this.handleStartedGoingToSleep(message.arg1);
                                    break;
                                case 322:
                                    KeyguardUpdateMonitor.this.handlePrimaryBouncerChanged(message.arg1, message.arg2);
                                    break;
                                default:
                                    switch (i) {
                                        case 328:
                                            KeyguardUpdateMonitor.this.handleSimSubscriptionInfoChanged();
                                            break;
                                        case 329:
                                            KeyguardUpdateMonitor.this.handleAirplaneModeChanged();
                                            break;
                                        case 330:
                                            KeyguardUpdateMonitor.this.handleServiceStateChange(message.arg1, message.arg2, (ServiceState) message.obj);
                                            break;
                                        default:
                                            switch (i) {
                                                case 332:
                                                    Trace.beginSection("KeyguardUpdateMonitor#handler MSG_SCREEN_TURNED_OFF");
                                                    KeyguardUpdateMonitor.this.handleScreenTurnedOff();
                                                    Trace.endSection();
                                                    break;
                                                case 333:
                                                    KeyguardUpdateMonitor.this.handleDreamingStateChanged(message.arg1);
                                                    break;
                                                case 334:
                                                    KeyguardUpdateMonitor.this.handleUserUnlocked(message.arg1);
                                                    break;
                                                case 335:
                                                    KeyguardUpdateMonitor.this.setAssistantVisible(((Boolean) message.obj).booleanValue());
                                                    break;
                                                case 336:
                                                    KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
                                                    break;
                                                default:
                                                    switch (i) {
                                                        case 338:
                                                            KeyguardUpdateMonitor.this.updateTelephonyCapable(((Boolean) message.obj).booleanValue());
                                                            break;
                                                        case 339:
                                                            KeyguardUpdateMonitor.this.handleTimeZoneUpdate((String) message.obj);
                                                            break;
                                                        case 340:
                                                            KeyguardUpdateMonitor.this.handleUserStopped(message.arg1);
                                                            break;
                                                        case 341:
                                                            KeyguardUpdateMonitor.this.handleUserRemoved(message.arg1);
                                                            break;
                                                        case 342:
                                                            KeyguardUpdateMonitor.this.handleKeyguardGoingAway(((Boolean) message.obj).booleanValue());
                                                            break;
                                                        default:
                                                            switch (i) {
                                                                case 344:
                                                                    KeyguardUpdateMonitor.this.handleTimeFormatUpdate((String) message.obj);
                                                                    break;
                                                                case 345:
                                                                    KeyguardUpdateMonitor.this.handleRequireUnlockForNfc();
                                                                    break;
                                                                case 346:
                                                                    KeyguardUpdateMonitor.this.handleKeyguardDismissAnimationFinished();
                                                                    break;
                                                                case 347:
                                                                    KeyguardUpdateMonitor.this.handleServiceProvidersUpdated((Intent) message.obj);
                                                                    break;
                                                                case 348:
                                                                    KeyguardUpdateMonitor.this.notifyAboutEnrollmentChange(message.arg1);
                                                                    break;
                                                                default:
                                                                    super.handleMessage(message);
                                                                    break;
                                                            }
                                                            break;
                                                    }
                                                    break;
                                            }
                                            break;
                                    }
                                    break;
                            }
                            break;
                    }
                    return;
                }
                KeyguardUpdateMonitor keyguardUpdateMonitor = KeyguardUpdateMonitor.this;
                keyguardUpdateMonitor.handleControlScreenOffChanged(keyguardUpdateMonitor.mControlScreenOff);
            }
        };
        this.mHandler = handler;
        this.mTimeFormatChangeObserver = new ContentObserver(handler) { // from class: com.android.keyguard.KeyguardUpdateMonitor.14
            @Override // android.database.ContentObserver
            public void onChange(boolean z) {
                KeyguardUpdateMonitor.this.mHandler.sendMessage(KeyguardUpdateMonitor.this.mHandler.obtainMessage(344, Settings.System.getString(KeyguardUpdateMonitor.this.mContext.getContentResolver(), "time_12_24")));
            }
        };
        initFlyme();
    }

    @Override // com.android.systemui.CoreStartable
    public void start() {
        if (!this.mDeviceProvisioned) {
            watchForDeviceProvisioning();
        }
        this.mBackgroundExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$start$7();
            }
        });
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.TIME_TICK");
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.BATTERY_CHANGED");
        intentFilter.addAction("android.intent.action.TIMEZONE_CHANGED");
        intentFilter.addAction("android.intent.action.AIRPLANE_MODE");
        intentFilter.addAction("android.intent.action.SIM_STATE_CHANGED");
        intentFilter.addAction("android.intent.action.SERVICE_STATE");
        intentFilter.addAction("android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
        intentFilter.addAction("android.intent.action.PHONE_STATE");
        intentFilter.addAction("android.telephony.action.SERVICE_PROVIDERS_UPDATED");
        intentFilter.addAction("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        intentFilter.addAction("com.meizu.HAS_FACE_ENROLL_CHANGED");
        intentFilter.addAction("android.hardware.usb.action.USB_PORT_COMPLIANCE_CHANGED");
        this.mBroadcastDispatcher.registerReceiverWithHandler(this.mBroadcastReceiver, intentFilter, this.mHandler);
        this.mBackgroundExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$start$8();
            }
        });
        IntentFilter intentFilter2 = new IntentFilter();
        intentFilter2.addAction("android.app.action.NEXT_ALARM_CLOCK_CHANGED");
        intentFilter2.addAction("android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED");
        intentFilter2.addAction("android.intent.action.USER_UNLOCKED");
        intentFilter2.addAction("android.intent.action.USER_STOPPED");
        intentFilter2.addAction("android.intent.action.USER_REMOVED");
        intentFilter2.addAction("android.nfc.action.REQUIRE_UNLOCK_FOR_NFC");
        this.mBroadcastDispatcher.registerReceiverWithHandler(this.mBroadcastAllReceiver, intentFilter2, this.mHandler, UserHandle.ALL);
        this.mSubscriptionManager.addOnSubscriptionsChangedListener(this.mSubscriptionListener);
        this.mUserTracker.addCallback(this.mUserChangedCallback, this.mMainExecutor);
        this.mTrustManager.registerTrustListener(this);
        setStrongAuthTracker(this.mStrongAuthTracker);
        FingerprintManager fingerprintManager = this.mFpm;
        if (fingerprintManager != null) {
            fingerprintManager.addAuthenticatorsRegisteredCallback(new IFingerprintAuthenticatorsRegisteredCallback.Stub() { // from class: com.android.keyguard.KeyguardUpdateMonitor.15
                public void onAllAuthenticatorsRegistered(List list) throws RemoteException {
                    KeyguardUpdateMonitor.this.mFingerprintSensorProperties = list;
                    KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
                    KeyguardUpdateMonitor.this.mLogger.d("FingerprintManager onAllAuthenticatorsRegistered");
                }
            });
            this.mFpm.addLockoutResetCallback(this.mFingerprintLockoutResetCallback);
        }
        BiometricManager biometricManager = this.mBiometricManager;
        if (biometricManager != null) {
            biometricManager.registerEnabledOnKeyguardCallback(this.mBiometricEnabledCallback);
        }
        this.mAuthController.addCallback(new AnonymousClass16());
        this.mDevicePostureController.addCallback(this.mPostureCallback);
        updateFingerprintListeningState(2);
        this.mTaskStackChangeListeners.registerTaskStackListener(this.mTaskStackListener);
        int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
        boolean zIsUserUnlocked = this.mUserManager.isUserUnlocked(selectedUserId);
        this.mLogger.logUserUnlockedInitialState(selectedUserId, zIsUserUnlocked);
        this.mUserIsUnlocked.put(selectedUserId, zIsUserUnlocked);
        updateSecondaryLockscreenRequirement(selectedUserId);
        for (UserInfo userInfo : this.mUserManager.getUsers()) {
            boolean zIsTrustUsuallyManaged = this.mTrustManager.isTrustUsuallyManaged(userInfo.id);
            KeyguardUpdateMonitorLogger keyguardUpdateMonitorLogger = this.mLogger;
            int i = userInfo.id;
            keyguardUpdateMonitorLogger.logTrustUsuallyManagedUpdated(i, this.mUserTrustIsUsuallyManaged.get(i), zIsTrustUsuallyManaged, "init from constructor");
            this.mUserTrustIsUsuallyManaged.put(userInfo.id, zIsTrustUsuallyManaged);
        }
        updateAirplaneModeState();
        this.mTelephonyListenerManager.addActiveDataSubscriptionIdListener(this.mPhoneStateListener);
        initializeSimState();
        this.mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("time_12_24"), false, this.mTimeFormatChangeObserver, -1);
        if (SceneContainerFlag.isEnabled()) {
            ((JavaAdapter) this.mJavaAdapter.get()).alwaysCollectFlow(((AlternateBouncerInteractor) this.mAlternateBouncerInteractor.get()).isVisible(), new Consumer() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda9
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    this.f$0.onAlternateBouncerVisibilityChange(((Boolean) obj).booleanValue());
                }
            });
            ((JavaAdapter) this.mJavaAdapter.get()).alwaysCollectFlow(((SceneInteractor) this.mSceneInteractor.get()).getTransitionState(), new Consumer() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda10
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    this.f$0.onTransitionStateChanged((ObservableTransitionState) obj);
                }
            });
        }
        if (KeyguardWmStateRefactor.isEnabled()) {
            ((JavaAdapter) this.mJavaAdapter.get()).alwaysCollectFlow(((KeyguardServiceShowLockscreenInteractor) this.mKeyguardServiceShowLockscreenInteractor.get()).getShowNowEvents(), new Consumer() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda11
                @Override // java.util.function.Consumer
                public final void accept(Object obj) {
                    this.f$0.onKeyguardServiceShowLockscreenNowEvents((ShowWhileAwakeReason) obj);
                }
            });
        }
        if (this.mUserTracker.isUserSwitching()) {
            handleUserSwitching(this.mUserTracker.getUserId(), new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda12
                @Override // java.lang.Runnable
                public final void run() {
                    KeyguardUpdateMonitor.$r8$lambda$N_dOIgOJXInuypcPPINtWTAmgbo();
                }
            });
        }
        this.mBackgroundExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda13
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$start$10();
            }
        });
    }

    public final /* synthetic */ void lambda$start$7() {
        int intProperty;
        BatteryManager batteryManager = (BatteryManager) this.mContext.getSystemService(BatteryManager.class);
        if (batteryManager == null || (intProperty = batteryManager.getIntProperty(4)) < 0 || intProperty > 100) {
            intProperty = -1;
        }
        final BatteryStatus batteryStatus = new BatteryStatus(1, intProperty, 0, 1, 0, true);
        this.mMainExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda18
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$start$6(batteryStatus);
            }
        });
    }

    public final /* synthetic */ void lambda$start$6(BatteryStatus batteryStatus) {
        if (this.mBatteryStatus == null) {
            handleBatteryUpdate(batteryStatus);
        }
    }

    public final /* synthetic */ void lambda$start$8() {
        int defaultSubscriptionId = SubscriptionManager.getDefaultSubscriptionId();
        ServiceState serviceStateForSubscriber = this.mTelephonyManager.getServiceStateForSubscriber(defaultSubscriptionId);
        int slotIndex = SubscriptionManager.getSlotIndex(defaultSubscriptionId);
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(330, defaultSubscriptionId, slotIndex, serviceStateForSubscriber));
    }

    /* JADX INFO: renamed from: com.android.keyguard.KeyguardUpdateMonitor$16, reason: invalid class name */
    public class AnonymousClass16 implements AuthController.Callback {
        public AnonymousClass16() {
        }

        @Override // com.android.systemui.biometrics.AuthController.Callback
        public void onAllAuthenticatorsRegistered(int i) {
            KeyguardUpdateMonitor.this.mMainExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$16$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onAllAuthenticatorsRegistered$0();
                }
            });
        }

        public final /* synthetic */ void lambda$onAllAuthenticatorsRegistered$0() {
            KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
        }

        @Override // com.android.systemui.biometrics.AuthController.Callback
        public void onEnrollmentsChanged(int i) {
            KeyguardUpdateMonitor.this.mHandler.obtainMessage(348, i, 0).sendToTarget();
            KeyguardUpdateMonitor.this.mMainExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$16$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.lambda$onEnrollmentsChanged$1();
                }
            });
        }

        public final /* synthetic */ void lambda$onEnrollmentsChanged$1() {
            KeyguardUpdateMonitor.this.updateFingerprintListeningState(2);
        }

        @Override // com.android.systemui.biometrics.AuthController.Callback
        public void onBiometricPromptShown() {
            KeyguardUpdateMonitor.this.mBiometricPromptShowing = true;
        }

        @Override // com.android.systemui.biometrics.AuthController.Callback
        public void onBiometricPromptDismissed() {
            KeyguardUpdateMonitor.this.mBiometricPromptShowing = false;
            KeyguardUpdateMonitor.this.updateFingerprintListeningState(0);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public /* synthetic */ void lambda$start$10() {
        getSubscriptionInfo(true);
    }

    public void onAlternateBouncerVisibilityChange(boolean z) {
        setAlternateBouncerShowing(z);
    }

    public void onTransitionStateChanged(ObservableTransitionState observableTransitionState) {
        boolean zIsPrimaryBouncerFullyShown = isPrimaryBouncerFullyShown(observableTransitionState);
        boolean zIsPrimaryBouncerShowingOrWillBeShowing = isPrimaryBouncerShowingOrWillBeShowing(observableTransitionState);
        handlePrimaryBouncerChanged(zIsPrimaryBouncerShowingOrWillBeShowing ? 1 : 0, zIsPrimaryBouncerFullyShown ? 1 : 0);
    }

    public void onKeyguardServiceShowLockscreenNowEvents(ShowWhileAwakeReason showWhileAwakeReason) {
        if (showWhileAwakeReason == ShowWhileAwakeReason.FOLDED_WITH_SWIPE_UP_TO_CONTINUE) {
            this.mMainExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda19
                @Override // java.lang.Runnable
                public final void run() {
                    this.f$0.tryForceIsDismissibleKeyguard();
                }
            });
        }
    }

    public final void initializeSimState() {
        for (int i = 0; i < this.mTelephonyManager.getActiveModemCount(); i++) {
            int simState = this.mTelephonyManager.getSimState(i);
            int[] subscriptionIds = this.mSubscriptionManager.getSubscriptionIds(i);
            if (subscriptionIds != null) {
                for (int i2 : subscriptionIds) {
                    this.mHandler.obtainMessage(304, i2, i, Integer.valueOf(simState)).sendToTarget();
                }
            }
        }
    }

    public final boolean isFingerprintSupported() {
        return (this.mFpm == null || this.mFingerprintSensorProperties.isEmpty()) ? false : true;
    }

    public boolean isUdfpsEnrolled() {
        return this.mAuthController.isUdfpsEnrolled(this.mSelectedUserInteractor.getSelectedUserId());
    }

    public boolean isUdfpsSupported() {
        return this.mAuthController.isUdfpsSupported();
    }

    public boolean isOpticalUdfpsSupported() {
        return this.mAuthController.isOpticalUdfpsSupported();
    }

    public boolean isSfpsEnrolled() {
        return this.mAuthController.isSfpsEnrolled(this.mSelectedUserInteractor.getSelectedUserId());
    }

    public boolean isSfpsSupported() {
        return this.mAuthController.isSfpsSupported();
    }

    public boolean isFaceEnabledAndEnrolled() {
        return getFaceAuthInteractor() != null && getFaceAuthInteractor().isFaceAuthEnabledAndEnrolled();
    }

    public final void updateAirplaneModeState() {
        if (!WirelessUtils.isAirplaneModeOn(this.mContext) || this.mHandler.hasMessages(329)) {
            return;
        }
        this.mHandler.sendEmptyMessage(329);
    }

    public final void updateFingerprintListeningState(int i) {
        if (this.mHandler.hasMessages(336)) {
            this.mLogger.logHandlerHasAuthContinueMsgs(i);
            return;
        }
        if (!this.mAuthController.areAllFingerprintAuthenticatorsRegistered()) {
            this.mLogger.d("All FP authenticators not registered, skipping FP listening state update");
            return;
        }
        boolean zShouldListenForFingerprint = shouldListenForFingerprint(isUdfpsSupported());
        int i2 = this.mFingerprintRunningState;
        boolean z = i2 == 1;
        boolean z2 = z || i2 == 3;
        boolean zIsUnlockingWithFingerprintAllowed = isUnlockingWithFingerprintAllowed();
        boolean z3 = !zIsUnlockingWithFingerprintAllowed;
        Log.d("KeyguardUpdateMonitor", "updateFingerprintListeningState: runningOrRestarting = " + z2 + "   shouldListenForFingerprint = " + zShouldListenForFingerprint);
        if (!z2 || zShouldListenForFingerprint) {
            if (z2 || !zShouldListenForFingerprint) {
                if (z && z3 != this.mFingerprintDetectRunning) {
                    if (i == 1) {
                        if (!zIsUnlockingWithFingerprintAllowed) {
                            this.mLogger.v("Allowing startListeningForFingerprint(detect) despite BIOMETRIC_ACTION_STOP since auth was running before.");
                        } else {
                            this.mLogger.v("Ignoring startListeningForFingerprint() switch detect -> auth");
                            return;
                        }
                    }
                    startListeningForFingerprint(z3);
                }
            } else {
                if (i == 1) {
                    this.mLogger.v("Ignoring startListeningForFingerprint()");
                    return;
                }
                startListeningForFingerprint(z3);
            }
        } else {
            if (i == 0) {
                this.mLogger.v("Ignoring stopListeningForFingerprint()");
                return;
            }
            stopListeningForFingerprint();
        }
        updateFakeFingerprintListeningState(zShouldListenForFingerprint);
    }

    public boolean isUserUnlocked(int i) {
        return this.mUserManager.isUserUnlocked(i);
    }

    public void onAuthInterruptDetected(boolean z) {
        this.mLogger.logAuthInterruptDetected(z);
        if (this.mAuthInterruptActive == z) {
            return;
        }
        this.mAuthInterruptActive = z;
        requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.WAKE, "onReach");
    }

    public final InstanceId getKeyguardSessionId() {
        return ((SessionTracker) this.mSessionTrackerProvider.get()).getSessionId(1);
    }

    public final void initiateActiveUnlock(String str) {
        if (!this.mHandler.hasMessages(336) && shouldTriggerActiveUnlock()) {
            this.mLogger.logActiveUnlockTriggered(str);
            this.mTrustManager.reportUserMayRequestUnlock(this.mSelectedUserInteractor.getSelectedUserId());
        }
    }

    public final void requestActiveUnlockFromWakeReason(int i, boolean z) {
        ActiveUnlockConfig.ActiveUnlockRequestOrigin activeUnlockRequestOrigin;
        if (!this.mFaceWakeUpTriggersConfig.shouldTriggerFaceAuthOnWakeUpFrom(i)) {
            this.mLogger.logActiveUnlockRequestSkippedForWakeReasonDueToFaceConfig(i);
            return;
        }
        if (this.mActiveUnlockConfig.isWakeupConsideredUnlockIntent(i)) {
            activeUnlockRequestOrigin = ActiveUnlockConfig.ActiveUnlockRequestOrigin.UNLOCK_INTENT;
        } else {
            activeUnlockRequestOrigin = ActiveUnlockConfig.ActiveUnlockRequestOrigin.WAKE;
        }
        String str = "wakingUp - " + PowerManager.wakeReasonToString(i) + " powerManagerWakeup=" + z;
        if (this.mActiveUnlockConfig.shouldWakeupForceDismissKeyguard(i)) {
            requestActiveUnlockDismissKeyguard(activeUnlockRequestOrigin, str);
        } else {
            requestActiveUnlock(activeUnlockRequestOrigin, str);
        }
    }

    public final void requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin activeUnlockRequestOrigin, String str, boolean z) {
        if (this.mHandler.hasMessages(336)) {
            return;
        }
        boolean zShouldAllowActiveUnlockFromOrigin = this.mActiveUnlockConfig.shouldAllowActiveUnlockFromOrigin(activeUnlockRequestOrigin);
        if (activeUnlockRequestOrigin == ActiveUnlockConfig.ActiveUnlockRequestOrigin.WAKE && !zShouldAllowActiveUnlockFromOrigin && this.mActiveUnlockConfig.isActiveUnlockEnabled()) {
            initiateActiveUnlock(str);
        } else if (zShouldAllowActiveUnlockFromOrigin && shouldTriggerActiveUnlock()) {
            this.mLogger.logUserRequestedUnlock(activeUnlockRequestOrigin, str, z);
            this.mTrustManager.reportUserRequestedUnlock(this.mSelectedUserInteractor.getSelectedUserId(), z);
        }
    }

    public void requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin activeUnlockRequestOrigin, String str) {
        KeyguardBypassController keyguardBypassController;
        requestActiveUnlock(activeUnlockRequestOrigin, str, (isFaceEnabledAndEnrolled() && (keyguardBypassController = this.mKeyguardBypassController) != null && keyguardBypassController.canBypass()) || isAlternateBouncerShowing() || isPrimaryBouncerFullyShown() || this.mAuthController.isUdfpsFingerDown());
    }

    public void requestActiveUnlockDismissKeyguard(ActiveUnlockConfig.ActiveUnlockRequestOrigin activeUnlockRequestOrigin, String str) {
        requestActiveUnlock(activeUnlockRequestOrigin, str + "-dismissKeyguard", true);
    }

    public void onCommunalShowingChanged(boolean z) {
        this.mCommunalShowing = z;
        updateFingerprintListeningState(2);
    }

    public void setAlternateBouncerShowing(boolean z) {
        this.mAlternateBouncerShowing = z;
        if (isAlternateBouncerShowing()) {
            requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.UNLOCK_INTENT, "alternateBouncer");
        }
        updateFingerprintListeningState(2);
    }

    public final boolean isAlternateBouncerShowing() {
        if (SceneContainerFlag.isEnabled()) {
            return ((AlternateBouncerInteractor) this.mAlternateBouncerInteractor.get()).isVisibleState();
        }
        return this.mAlternateBouncerShowing;
    }

    public final boolean isPrimaryBouncerShowingOrWillBeShowing() {
        if (SceneContainerFlag.isEnabled()) {
            return isPrimaryBouncerShowingOrWillBeShowing((ObservableTransitionState) ((SceneInteractor) this.mSceneInteractor.get()).getTransitionState().getValue());
        }
        return this.mPrimaryBouncerIsOrWillBeShowing;
    }

    public final boolean isPrimaryBouncerFullyShown() {
        if (SceneContainerFlag.isEnabled()) {
            return isPrimaryBouncerFullyShown((ObservableTransitionState) ((SceneInteractor) this.mSceneInteractor.get()).getTransitionState().getValue());
        }
        return this.mPrimaryBouncerFullyShown;
    }

    public final boolean isPrimaryBouncerShowingOrWillBeShowing(ObservableTransitionState observableTransitionState) {
        SceneContainerFlag.unsafeAssertInNewMode();
        return isPrimaryBouncerFullyShown(observableTransitionState) || observableTransitionState.isTransitioning(null, Overlays.Bouncer);
    }

    public final boolean isPrimaryBouncerFullyShown(ObservableTransitionState observableTransitionState) {
        SceneContainerFlag.unsafeAssertInNewMode();
        return observableTransitionState.isIdle(Overlays.Bouncer);
    }

    public final boolean shouldTriggerActiveUnlock() {
        return shouldTriggerActiveUnlock(true);
    }

    public final boolean shouldTriggerActiveUnlock(boolean z) {
        boolean zShouldTriggerActiveUnlockForAssistant = shouldTriggerActiveUnlockForAssistant();
        boolean z2 = isPrimaryBouncerFullyShown() || isAlternateBouncerShowing() || !(!isKeyguardVisible() || this.mGoingToSleep || this.mStatusBarState == 2);
        int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
        boolean z3 = getUserCanSkipBouncer(selectedUserId) || !this.mLockPatternUtils.isSecure(selectedUserId);
        boolean zIsFingerprintLockedOut = isFingerprintLockedOut();
        boolean zIsUnlockingWithTrustAgentAllowed = isUnlockingWithTrustAgentAllowed();
        boolean z4 = !zIsUnlockingWithTrustAgentAllowed;
        boolean z5 = ((!this.mAuthInterruptActive && !zShouldTriggerActiveUnlockForAssistant && !z2) || this.mSwitchingUser || z3 || zIsFingerprintLockedOut || !zIsUnlockingWithTrustAgentAllowed || this.mKeyguardGoingAway || this.mSecureCameraLaunched) ? false : true;
        if (z) {
            logListenerModelData(new KeyguardActiveUnlockModel(System.currentTimeMillis(), selectedUserId, z5, z2, this.mAuthInterruptActive, zIsFingerprintLockedOut, z4, this.mSwitchingUser, zShouldTriggerActiveUnlockForAssistant, z3));
        }
        return z5;
    }

    public final boolean shouldListenForFingerprintAssistant() {
        BiometricAuthenticated biometricAuthenticated = (BiometricAuthenticated) this.mUserFingerprintAuthenticated.get(this.mSelectedUserInteractor.getSelectedUserId());
        return this.mAssistantVisible && this.mKeyguardOccluded && (biometricAuthenticated == null || !biometricAuthenticated.mAuthenticated) && !this.mUserHasTrust.get(this.mSelectedUserInteractor.getSelectedUserId(), false);
    }

    public final boolean shouldTriggerActiveUnlockForAssistant() {
        return this.mAssistantVisible && this.mKeyguardOccluded && !this.mUserHasTrust.get(this.mSelectedUserInteractor.getSelectedUserId(), false);
    }

    public boolean shouldListenForFingerprint(boolean z) {
        boolean z2;
        boolean z3;
        boolean z4;
        int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
        boolean userHasTrust = getUserHasTrust(selectedUserId);
        boolean z5 = !userHasTrust;
        boolean zShouldListenForFingerprintAssistant = shouldListenForFingerprintAssistant();
        boolean z6 = isKeyguardVisible() || !this.mDeviceInteractive || (isPrimaryBouncerShowingOrWillBeShowing() && !this.mKeyguardGoingAway) || ((!SystemUICommonUtils.isUnderGlassOpticalFingerprint() && this.mGoingToSleep) || zShouldListenForFingerprintAssistant || (((z4 = this.mKeyguardOccluded) && this.mIsDreaming) || (z4 && !userHasTrust && this.mKeyguardShowing && (this.mOccludingAppRequestingFp || isAlternateBouncerShowing() || this.mAllowFingerprintOnCurrentOccludingActivity))));
        Log.i("KeyguardUpdateMonitor", "1.listenKeyguardState:" + z6 + "\n mKeyguardIsVisible=" + isKeyguardVisible() + "，mDeviceInteractive=" + this.mDeviceInteractive + ", mPrimaryBouncerIsOrWillBeShowing=" + this.mPrimaryBouncerIsOrWillBeShowing + ", mKeyguardGoingAway=" + this.mKeyguardGoingAway + ", isUnderGlassOpticalFingerprint=" + SystemUICommonUtils.isUnderGlassOpticalFingerprint() + ", mGoingToSleep=" + this.mGoingToSleep + ", shouldListenForFingerprintAssistant=" + zShouldListenForFingerprintAssistant + ", mKeyguardOccluded=" + this.mKeyguardOccluded + ", mIsDreaming=" + this.mIsDreaming + ", mKeyguardShowing=" + this.mKeyguardShowing + ", userDoesNotHaveTrust" + z5 + ", mOccludingAppRequestingFp=" + this.mOccludingAppRequestingFp + ", isUdfps=" + z + ", mStatusBarState=" + this.mStatusBarState);
        boolean z7 = this.mBiometricEnabledForUser.get(selectedUserId);
        boolean userCanSkipBouncer = getUserCanSkipBouncer(selectedUserId);
        boolean zIsFingerprintDisabled = isFingerprintDisabled(selectedUserId);
        boolean z8 = (this.mSwitchingUser || zIsFingerprintDisabled || (this.mKeyguardGoingAway && this.mDeviceInteractive) || !this.mIsSystemUser || !z7 || isUserInLockdown(selectedUserId)) ? false : true;
        boolean zIsUnlockingWithFingerprintAllowed = isUnlockingWithFingerprintAllowed();
        boolean z9 = !zIsUnlockingWithFingerprintAllowed;
        boolean z10 = zIsUnlockingWithFingerprintAllowed || !isPrimaryBouncerShowingOrWillBeShowing();
        if (this.mCommunalShowing) {
            isAlternateBouncerShowing();
        }
        boolean z11 = (z && (userCanSkipBouncer || !zIsUnlockingWithFingerprintAllowed || userHasTrust)) ? false : true;
        Log.d("KeyguardUpdateMonitor", "shouldListenUdfpsState:" + z11 + " {isUdfps=" + z + ", userCanSkipBouncer=" + userCanSkipBouncer + ", userHasTrust=" + getUserHasTrust(selectedUserId) + ", userUnlockedWithBiometric=" + getUserUnlockedWithBiometric(selectedUserId) + ", strongerAuthRequired=" + z9 + ", userDoesNotHaveTrust=" + z5 + "}");
        boolean zIsTipsViewAdded = ((PalmRejection) Dependency.get(PalmRejection.class)).isTipsViewAdded();
        boolean zIsChargeViewAttached = ChargeAnimationController.getInstance(this.mContext).isChargeViewAttached();
        boolean zIsMzDialogShowing = ChargeAnimationController.getInstance(this.mContext).isMzDialogShowing();
        boolean zIsMzPowerSaveMode = ((SettingsUpdateMonitor) Dependency.get(SettingsUpdateMonitor.class)).isMzPowerSaveMode();
        if (z6 && z8 && z10 && z11) {
            z2 = zShouldListenForFingerprintAssistant;
            if (!this.mBiometricPromptShowing && fingerprintUsedAsUnlock() && !zIsTipsViewAdded && !this.mImeShowing && !zIsMzPowerSaveMode && !this.mLaunchingApp && !isNeedStrongAuth() && this.mCenterState == 0 && !zIsChargeViewAttached && !zIsMzDialogShowing && !this.mKeyguardDismissPauseForEditor && !getIsFaceAuthenticated()) {
                z3 = true;
            }
            Log.i("KeyguardUpdateMonitor", "shouldListenForFingerprint:" + z3 + " {shouldListenKeyguardState=" + z6 + ", shouldListenUserState=" + z8 + ", shouldListenBouncerState=" + z10 + ", shouldListenUdfpsState=" + z11 + ", mBiometricPromptShowing=" + this.mBiometricPromptShowing + ", mFingerprintUsedAsUnlock=" + fingerprintUsedAsUnlock() + ", palmRejectWindowAdd=" + zIsTipsViewAdded + ", mImeShowing=" + this.mImeShowing + ", mLaunchingApp=" + this.mLaunchingApp + ", isMzSaveMode=" + zIsMzPowerSaveMode + ", isNeedStrongAuth=" + isNeedStrongAuth() + ", mCenterState=" + this.mCenterState + ", chargingViewAttached=" + zIsChargeViewAttached + ", mzDialogShowing=" + zIsMzDialogShowing + ", mKeyguardDismissPauseForEditor=" + this.mKeyguardDismissPauseForEditor + ", getIsFaceAuthenticated=" + getIsFaceAuthenticated() + "}");
            logListenerModelData(new KeyguardFingerprintListenModel(System.currentTimeMillis(), selectedUserId, z3, this.mAllowFingerprintOnCurrentOccludingActivity, isAlternateBouncerShowing(), z7, this.mBiometricPromptShowing, isPrimaryBouncerShowingOrWillBeShowing(), userCanSkipBouncer, this.mCredentialAttempted, this.mDeviceInteractive, this.mIsDreaming, zIsFingerprintDisabled, this.mFingerprintLockedOut, this.mGoingToSleep, this.mKeyguardGoingAway, isKeyguardVisible(), this.mKeyguardOccluded, this.mOccludingAppRequestingFp, z2, z9, this.mSwitchingUser, this.mIsSystemUser, z, z5, this.mCommunalShowing));
            return z3;
        }
        z2 = zShouldListenForFingerprintAssistant;
        z3 = false;
        Log.i("KeyguardUpdateMonitor", "shouldListenForFingerprint:" + z3 + " {shouldListenKeyguardState=" + z6 + ", shouldListenUserState=" + z8 + ", shouldListenBouncerState=" + z10 + ", shouldListenUdfpsState=" + z11 + ", mBiometricPromptShowing=" + this.mBiometricPromptShowing + ", mFingerprintUsedAsUnlock=" + fingerprintUsedAsUnlock() + ", palmRejectWindowAdd=" + zIsTipsViewAdded + ", mImeShowing=" + this.mImeShowing + ", mLaunchingApp=" + this.mLaunchingApp + ", isMzSaveMode=" + zIsMzPowerSaveMode + ", isNeedStrongAuth=" + isNeedStrongAuth() + ", mCenterState=" + this.mCenterState + ", chargingViewAttached=" + zIsChargeViewAttached + ", mzDialogShowing=" + zIsMzDialogShowing + ", mKeyguardDismissPauseForEditor=" + this.mKeyguardDismissPauseForEditor + ", getIsFaceAuthenticated=" + getIsFaceAuthenticated() + "}");
        logListenerModelData(new KeyguardFingerprintListenModel(System.currentTimeMillis(), selectedUserId, z3, this.mAllowFingerprintOnCurrentOccludingActivity, isAlternateBouncerShowing(), z7, this.mBiometricPromptShowing, isPrimaryBouncerShowingOrWillBeShowing(), userCanSkipBouncer, this.mCredentialAttempted, this.mDeviceInteractive, this.mIsDreaming, zIsFingerprintDisabled, this.mFingerprintLockedOut, this.mGoingToSleep, this.mKeyguardGoingAway, isKeyguardVisible(), this.mKeyguardOccluded, this.mOccludingAppRequestingFp, z2, z9, this.mSwitchingUser, this.mIsSystemUser, z, z5, this.mCommunalShowing));
        return z3;
    }

    public boolean shouldListenForFace() {
        return getFaceAuthInteractor() != null && getFaceAuthInteractor().canFaceAuthRun();
    }

    public final void logListenerModelData(KeyguardListenModel keyguardListenModel) {
        this.mLogger.logKeyguardListenerModel(keyguardListenModel);
        if (keyguardListenModel instanceof KeyguardFingerprintListenModel) {
            this.mFingerprintListenBuffer.insert((KeyguardFingerprintListenModel) keyguardListenModel);
        } else if (keyguardListenModel instanceof KeyguardActiveUnlockModel) {
            this.mActiveUnlockTriggerBuffer.insert((KeyguardActiveUnlockModel) keyguardListenModel);
        }
    }

    public final void startListeningForFingerprint(boolean z) {
        int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
        boolean zIsUnlockWithFingerprintPossible = isUnlockWithFingerprintPossible(selectedUserId);
        if (this.mFingerprintCancelSignal != null) {
            this.mLogger.logUnexpectedFpCancellationSignalState(this.mFingerprintRunningState, zIsUnlockWithFingerprintPossible);
        }
        int i = this.mFingerprintRunningState;
        if (i == 2) {
            setFingerprintRunningState(3);
            return;
        }
        if (i == 3) {
            return;
        }
        Log.i("KeyguardUpdateMonitor", "startListeningForFingerprint  - detect = " + z + " unlockPossible = " + zIsUnlockWithFingerprintPossible);
        if (zIsUnlockWithFingerprintPossible) {
            this.mFingerprintCancelSignal = new CancellationSignal();
            FingerprintAuthenticateOptions fingerprintAuthenticateOptionsBuild = new FingerprintAuthenticateOptions.Builder().setUserId(selectedUserId).build();
            if (z) {
                this.mLogger.v("startListeningForFingerprint - detect");
                this.mFpm.detectFingerprint(this.mFingerprintCancelSignal, this.mFingerprintDetectionCallback, fingerprintAuthenticateOptionsBuild);
                this.mFingerprintDetectRunning = true;
            } else {
                this.mLogger.v("startListeningForFingerprint");
                this.mFpm.authenticate((FingerprintManager.CryptoObject) null, this.mFingerprintCancelSignal, this.mFingerprintAuthenticationCallback, (Handler) null, fingerprintAuthenticateOptionsBuild);
                this.mFingerprintDetectRunning = false;
            }
            setFingerprintRunningState(1);
        }
    }

    public boolean isFingerprintLockedOut() {
        return this.mFingerprintLockedOut || this.mFingerprintLockedOutPermanent;
    }

    public boolean isFaceLockedOut() {
        return getFaceAuthInteractor() != null && ((Boolean) getFaceAuthInteractor().isLockedOut().getValue()).booleanValue();
    }

    public boolean isUnlockingWithBiometricsPossible(int i) {
        return isUnlockWithFacePossible() || isUnlockWithFingerprintPossible(i);
    }

    public boolean isUnlockingWithNonStrongBiometricsPossible(int i) {
        if (getFaceAuthInteractor() == null || getFaceAuthInteractor().isFaceAuthStrong() || !isUnlockWithFacePossible()) {
            return isFingerprintClass3() && isUnlockWithFingerprintPossible(i);
        }
        return true;
    }

    public boolean isUnlockWithFingerprintPossible(int i) {
        return isFingerprintSupported() && fingerprintUsedAsUnlock() && !isFingerprintDisabled(i) && this.mAuthController.isFingerprintEnrolled(i);
    }

    @Deprecated
    public boolean isUnlockWithFacePossible() {
        return getFaceAuthInteractor() != null && getFaceAuthInteractor().isFaceAuthEnabledAndEnrolled();
    }

    public final void notifyAboutEnrollmentChange(int i) {
        BiometricSourceType biometricSourceType;
        if (i == 2) {
            biometricSourceType = BiometricSourceType.FINGERPRINT;
        } else if (i != 8) {
            return;
        } else {
            biometricSourceType = BiometricSourceType.FACE;
        }
        this.mLogger.notifyAboutEnrollmentsChanged(biometricSourceType);
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onBiometricEnrollmentStateChanged(biometricSourceType);
            }
        }
    }

    public final void stopListeningForFingerprint() {
        this.mLogger.v("stopListeningForFingerprint()");
        Log.d("KeyguardUpdateMonitor", "stopListeningForFingerprint mFingerprintRunningState:" + this.mFingerprintRunningState + " mFingerprintCancelSignal:" + this.mFingerprintCancelSignal);
        if (this.mFingerprintRunningState == 1) {
            CancellationSignal cancellationSignal = this.mFingerprintCancelSignal;
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
                this.mFingerprintCancelSignal = null;
                this.mHandler.removeCallbacks(this.mFpCancelNotReceived);
                this.mHandler.postDelayed(this.mFpCancelNotReceived, 3000L);
            }
            setFingerprintRunningState(2);
        }
        if (this.mFingerprintRunningState == 3) {
            setFingerprintRunningState(2);
        }
    }

    public final boolean isDeviceProvisionedInSettingsDb() {
        return Settings.Global.getInt(this.mContext.getContentResolver(), "device_provisioned", 0) != 0;
    }

    public final void watchForDeviceProvisioning() {
        this.mDeviceProvisionedObserver = new ContentObserver(this.mHandler) { // from class: com.android.keyguard.KeyguardUpdateMonitor.18
            @Override // android.database.ContentObserver
            public void onChange(boolean z) {
                super.onChange(z);
                KeyguardUpdateMonitor keyguardUpdateMonitor = KeyguardUpdateMonitor.this;
                keyguardUpdateMonitor.mDeviceProvisioned = keyguardUpdateMonitor.isDeviceProvisionedInSettingsDb();
                if (KeyguardUpdateMonitor.this.mDeviceProvisioned) {
                    KeyguardUpdateMonitor.this.mHandler.sendEmptyMessage(308);
                }
                KeyguardUpdateMonitor.this.mLogger.logDeviceProvisionedState(KeyguardUpdateMonitor.this.mDeviceProvisioned);
            }
        };
        this.mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor("device_provisioned"), false, this.mDeviceProvisionedObserver);
        boolean zIsDeviceProvisionedInSettingsDb = isDeviceProvisionedInSettingsDb();
        if (zIsDeviceProvisionedInSettingsDb != this.mDeviceProvisioned) {
            this.mDeviceProvisioned = zIsDeviceProvisionedInSettingsDb;
            if (zIsDeviceProvisionedInSettingsDb) {
                this.mHandler.sendEmptyMessage(308);
            }
        }
    }

    public final void handleDevicePolicyManagerStateChanged(int i) {
        Assert.isMainThread();
        updateFingerprintListeningState(2);
        updateSecondaryLockscreenRequirement(i);
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onDevicePolicyManagerStateChanged();
            }
        }
    }

    public void handleUserSwitching(int i, Runnable runnable) {
        this.mLogger.logUserSwitching(i, "from UserTracker");
        Assert.isMainThread();
        setForceIsDismissibleKeyguard(false);
        clearFingerprintRecognized();
        boolean zIsTrustUsuallyManaged = this.mTrustManager.isTrustUsuallyManaged(i);
        this.mLogger.logTrustUsuallyManagedUpdated(i, this.mUserTrustIsUsuallyManaged.get(i), zIsTrustUsuallyManaged, "userSwitching");
        this.mUserTrustIsUsuallyManaged.put(i, zIsTrustUsuallyManaged);
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onUserSwitching(i);
            }
        }
        runnable.run();
    }

    public void handleUserSwitchComplete(int i) {
        this.mLogger.logUserSwitchComplete(i, "from UserTracker");
        Assert.isMainThread();
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onUserSwitchComplete(i);
            }
        }
        if (isFingerprintSupported()) {
            stopListeningForFingerprint();
            handleFingerprintLockoutReset(this.mFpm.getLockoutModeForUser(((FingerprintSensorPropertiesInternal) this.mFingerprintSensorProperties.get(0)).sensorId, i));
        }
        this.mInteractionJankMonitor.end(37);
        this.mLatencyTracker.onActionEnd(12);
    }

    public final void handleDeviceProvisioned() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onDeviceProvisioned();
            }
        }
        if (this.mDeviceProvisionedObserver != null) {
            this.mContext.getContentResolver().unregisterContentObserver(this.mDeviceProvisionedObserver);
            this.mDeviceProvisionedObserver = null;
        }
    }

    public final void handlePhoneStateChanged(String str) {
        Assert.isMainThread();
        this.mLogger.logPhoneStateChanged(str);
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(str)) {
            this.mPhoneState = 0;
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(str)) {
            this.mPhoneState = 2;
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(str)) {
            this.mPhoneState = 1;
        }
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onPhoneStateChanged(this.mPhoneState);
            }
        }
    }

    public void triggerTimeUpdate() {
        this.mHandler.sendEmptyMessage(301);
    }

    public final void handleTimeUpdate() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTimeChanged();
            }
        }
    }

    public final void handleTimeZoneUpdate(String str) {
        Assert.isMainThread();
        this.mLogger.d("handleTimeZoneUpdate");
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTimeZoneChanged(TimeZone.getTimeZone(str));
                keyguardUpdateMonitorCallback.onTimeChanged();
            }
        }
    }

    public final void handleTimeFormatUpdate(String str) {
        Assert.isMainThread();
        this.mLogger.logTimeFormatChanged(str);
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTimeFormatChanged(str);
            }
        }
    }

    public void sendWeatherData(final WeatherData weatherData) {
        this.mHandler.post(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda15
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$sendWeatherData$11(weatherData);
            }
        });
    }

    /* JADX INFO: renamed from: handleWeatherDataUpdate, reason: merged with bridge method [inline-methods] */
    public final void lambda$sendWeatherData$11(WeatherData weatherData) {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onWeatherDataChanged(weatherData);
            }
        }
    }

    public final void handleBatteryUpdate(BatteryStatus batteryStatus) {
        Assert.isMainThread();
        boolean zIsBatteryUpdateInteresting = isBatteryUpdateInteresting(this.mBatteryStatus, batteryStatus);
        this.mBatteryStatus = batteryStatus;
        if (zIsBatteryUpdateInteresting) {
            this.mLogger.logHandleBatteryUpdate(batteryStatus);
            for (int i = 0; i < this.mCallbacks.size(); i++) {
                KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                if (keyguardUpdateMonitorCallback != null) {
                    keyguardUpdateMonitorCallback.onRefreshBatteryInfo(batteryStatus);
                }
            }
        }
    }

    public void updateTelephonyCapable(boolean z) {
        Assert.isMainThread();
        if (z == this.mTelephonyCapable) {
            return;
        }
        this.mTelephonyCapable = z;
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onTelephonyCapable(this.mTelephonyCapable);
            }
        }
    }

    public void handleSimStateChange(int i, int i2, int i3) {
        boolean z;
        Assert.isMainThread();
        this.mSimLogger.logSimState(i, i2, TelephonyManager.simStateToString(i3));
        boolean zContains = ABSENT_SIM_STATE_LIST.contains(Integer.valueOf(i3));
        boolean z2 = true;
        if (SubscriptionManager.isValidSubscriptionId(i)) {
            z = false;
        } else {
            this.mSimLogger.w("invalid subId in handleSimStateChange()");
            if (i3 == 1 || i3 == 8) {
                updateTelephonyCapable(true);
            } else {
                if (i3 == 6) {
                    for (SimData simData : this.mSimDatasBySlotId.values()) {
                        if (simData.slotId == i2) {
                            simData.simState = 6;
                        }
                    }
                    z = true;
                }
                invalidateSlot(i2);
            }
            z = false;
            invalidateSlot(i2);
        }
        synchronized (this.mSimDataLockObject) {
            try {
                SimData simData2 = (SimData) this.mSimDatasBySlotId.get(Integer.valueOf(i2));
                if (simData2 == null) {
                    this.mSimDatasBySlotId.put(Integer.valueOf(i2), new SimData(i3, i2, i));
                } else {
                    if (simData2.simState == i3 && simData2.subId == i && simData2.slotId == i2) {
                        z2 = false;
                    }
                    simData2.simState = i3;
                    simData2.subId = i;
                    simData2.slotId = i2;
                }
                if (z2 || zContains || z) {
                    threadSafeCallSimStateChanged(null, i, i2, i3);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void handleServiceStateChange(int i, int i2, ServiceState serviceState) {
        this.mSimLogger.logServiceStateChange(i, i2, serviceState);
        if (serviceState != null) {
            this.mServiceStatesWithSlotid.put(Integer.valueOf(i2), serviceState);
        }
        int i3 = 0;
        if (!SubscriptionManager.isValidSubscriptionId(i)) {
            this.mSimLogger.w("invalid subId in handleServiceStateChange()");
            while (i3 < this.mCallbacks.size()) {
                KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i3)).get();
                if (keyguardUpdateMonitorCallback != null) {
                    keyguardUpdateMonitorCallback.onServiceStateChanged(i, serviceState);
                }
                i3++;
            }
            return;
        }
        updateTelephonyCapable(true);
        this.mServiceStates.put(Integer.valueOf(i), serviceState);
        while (i3 < this.mCallbacks.size()) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback2 = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i3)).get();
            if (keyguardUpdateMonitorCallback2 != null) {
                keyguardUpdateMonitorCallback2.onRefreshCarrierInfo();
                keyguardUpdateMonitorCallback2.onServiceStateChanged(i, serviceState);
            }
            i3++;
        }
    }

    public final void handleServiceProvidersUpdated(Intent intent) {
        this.mLogger.logServiceProvidersUpdated(intent);
        callbacksRefreshCarrierInfo();
    }

    public boolean isKeyguardVisible() {
        return this.mKeyguardShowing && !this.mKeyguardOccluded;
    }

    public void handleKeyguardReset() {
        this.mLogger.d("handleKeyguardReset");
        updateFingerprintListeningState(2);
        this.mNeedsSlowUnlockTransition = resolveNeedsSlowUnlockTransition();
    }

    public final boolean resolveNeedsSlowUnlockTransition() {
        if (isUserUnlocked(this.mSelectedUserInteractor.getSelectedUserId())) {
            return false;
        }
        ResolveInfo resolveInfoResolveActivityAsUser = this.mPackageManager.resolveActivityAsUser(new Intent("android.intent.action.MAIN").addCategory("android.intent.category.HOME"), 0, this.mSelectedUserInteractor.getSelectedUserId());
        if (resolveInfoResolveActivityAsUser == null) {
            this.mLogger.w("resolveNeedsSlowUnlockTransition: returning false since activity could not be resolved.");
            return false;
        }
        return FALLBACK_HOME_COMPONENT.equals(resolveInfoResolveActivityAsUser.getComponentInfo().getComponentName());
    }

    public final void handlePrimaryBouncerChanged(int i, int i2) {
        Assert.isMainThread();
        boolean z = this.mPrimaryBouncerIsOrWillBeShowing;
        boolean z2 = this.mPrimaryBouncerFullyShown;
        boolean z3 = i == 1;
        this.mPrimaryBouncerIsOrWillBeShowing = z3;
        boolean z4 = i2 == 1;
        this.mPrimaryBouncerFullyShown = z4;
        this.mLogger.logPrimaryKeyguardBouncerChanged(z3, z4);
        if (this.mPrimaryBouncerFullyShown) {
            this.mSecureCameraLaunched = false;
        } else {
            this.mCredentialAttempted = false;
        }
        if (z != this.mPrimaryBouncerIsOrWillBeShowing) {
            for (int i3 = 0; i3 < this.mCallbacks.size(); i3++) {
                KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i3)).get();
                if (keyguardUpdateMonitorCallback != null) {
                    keyguardUpdateMonitorCallback.onKeyguardBouncerStateChanged(this.mPrimaryBouncerIsOrWillBeShowing);
                }
            }
            updateFingerprintListeningState(2);
        }
        boolean z5 = this.mPrimaryBouncerFullyShown;
        if (z2 != z5) {
            if (z5) {
                requestActiveUnlock(ActiveUnlockConfig.ActiveUnlockRequestOrigin.UNLOCK_INTENT, "bouncerFullyShown");
            }
            for (int i4 = 0; i4 < this.mCallbacks.size(); i4++) {
                KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback2 = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i4)).get();
                if (keyguardUpdateMonitorCallback2 != null) {
                    keyguardUpdateMonitorCallback2.onKeyguardBouncerFullyShowingChanged(this.mPrimaryBouncerFullyShown);
                }
            }
        }
    }

    public final void handleRequireUnlockForNfc() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onRequireUnlockForNfc();
            }
        }
    }

    public final void handleKeyguardDismissAnimationFinished() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onKeyguardDismissAnimationFinished();
            }
        }
        this.mIsSideFpFailFirstTime = true;
    }

    public final void handleReportEmergencyCallAction() {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onEmergencyCallAction();
            }
        }
    }

    public final boolean isBatteryUpdateInteresting(BatteryStatus batteryStatus, BatteryStatus batteryStatus2) {
        if (batteryStatus == null) {
            return true;
        }
        boolean zIsPluggedIn = batteryStatus2.isPluggedIn();
        boolean zIsPluggedIn2 = batteryStatus.isPluggedIn();
        boolean z = zIsPluggedIn2 && zIsPluggedIn && batteryStatus.status != batteryStatus2.status;
        if (zIsPluggedIn2 == zIsPluggedIn && !z && batteryStatus.level == batteryStatus2.level) {
            return ((!zIsPluggedIn || batteryStatus2.maxChargingWattage == batteryStatus.maxChargingWattage) && batteryStatus.present == batteryStatus2.present && batteryStatus.incompatibleCharger.equals(batteryStatus2.incompatibleCharger) && batteryStatus2.chargingStatus == batteryStatus.chargingStatus) ? false : true;
        }
        return true;
    }

    public void removeCallback(final KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback) {
        Assert.isMainThread();
        this.mLogger.logUnregisterCallback(keyguardUpdateMonitorCallback);
        this.mCallbacks.removeIf(new Predicate() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda2
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                return KeyguardUpdateMonitor.$r8$lambda$JjPqYEZ_ZMprhghYiw0DtCO8Njc(keyguardUpdateMonitorCallback, (WeakReference) obj);
            }
        });
    }

    public static /* synthetic */ boolean $r8$lambda$JjPqYEZ_ZMprhghYiw0DtCO8Njc(KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback, WeakReference weakReference) {
        return weakReference.get() == keyguardUpdateMonitorCallback;
    }

    public void registerCallback(KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback) {
        Assert.isMainThread();
        this.mLogger.logRegisterCallback(keyguardUpdateMonitorCallback);
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            if (((WeakReference) this.mCallbacks.get(i)).get() == keyguardUpdateMonitorCallback) {
                this.mLogger.logException(new Exception("Called by"), "Object tried to add another callback");
                return;
            }
        }
        this.mCallbacks.add(new WeakReference(keyguardUpdateMonitorCallback));
        removeCallback(null);
        sendUpdates(keyguardUpdateMonitorCallback);
    }

    public void setKeyguardBypassController(KeyguardBypassController keyguardBypassController) {
        this.mKeyguardBypassController = keyguardBypassController;
    }

    public boolean isSwitchingUser() {
        return this.mSwitchingUser;
    }

    public void setSwitchingUser(boolean z) {
        if (z) {
            this.mLogger.logUserSwitching(this.mSelectedUserInteractor.getSelectedUserId(), "from setSwitchingUser");
        } else {
            this.mLogger.logUserSwitchComplete(this.mSelectedUserInteractor.getSelectedUserId(), "from setSwitchingUser");
        }
        this.mSwitchingUser = z;
        this.mHandler.post(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$setSwitchingUser$13();
            }
        });
    }

    public final /* synthetic */ void lambda$setSwitchingUser$13() {
        updateFingerprintListeningState(2);
    }

    public final void sendUpdates(KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback) {
        BatteryStatus batteryStatus = this.mBatteryStatus;
        if (batteryStatus != null) {
            keyguardUpdateMonitorCallback.onRefreshBatteryInfo(batteryStatus);
        }
        keyguardUpdateMonitorCallback.onTimeChanged();
        keyguardUpdateMonitorCallback.onPhoneStateChanged(this.mPhoneState);
        keyguardUpdateMonitorCallback.onRefreshCarrierInfo();
        keyguardUpdateMonitorCallback.onKeyguardVisibilityChanged(isKeyguardVisible());
        keyguardUpdateMonitorCallback.onTelephonyCapable(this.mTelephonyCapable);
        synchronized (this.mSimDataLockObject) {
            try {
                Iterator it = this.mSimDatasBySlotId.entrySet().iterator();
                while (it.hasNext()) {
                    SimData simData = (SimData) ((Map.Entry) it.next()).getValue();
                    threadSafeCallSimStateChanged(keyguardUpdateMonitorCallback, simData.subId, simData.slotId, simData.simState);
                }
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public void sendKeyguardReset() {
        this.mHandler.obtainMessage(312).sendToTarget();
    }

    public void sendPrimaryBouncerChanged(boolean z, boolean z2) {
        SceneContainerFlag.assertInLegacyMode();
        this.mLogger.logSendPrimaryBouncerChanged(z, z2);
        Message messageObtainMessage = this.mHandler.obtainMessage(322);
        messageObtainMessage.arg1 = z ? 1 : 0;
        messageObtainMessage.arg2 = z2 ? 1 : 0;
        messageObtainMessage.sendToTarget();
    }

    public void reportSimUnlocked(int i) {
        this.mSimLogger.logSimUnlocked(i);
        handleSimStateChange(i, getSlotId(i), 5);
    }

    public void reportEmergencyCallAction(boolean z) {
        if (!z) {
            this.mHandler.obtainMessage(318).sendToTarget();
        } else {
            Assert.isMainThread();
            handleReportEmergencyCallAction();
        }
    }

    public boolean isDeviceProvisioned() {
        return this.mDeviceProvisioned;
    }

    public ServiceState getServiceState(int i) {
        return (ServiceState) this.mServiceStates.get(Integer.valueOf(i));
    }

    public ServiceState getServiceStateWithSlotid(int i) {
        return (ServiceState) this.mServiceStatesWithSlotid.get(Integer.valueOf(i));
    }

    public void clearFingerprintRecognized() {
        clearFingerprintRecognized(-10000);
    }

    public void clearFingerprintRecognizedWhenKeyguardDone(int i) {
        clearFingerprintRecognized(i);
    }

    public final void clearFingerprintRecognized(int i) {
        Assert.isMainThread();
        this.mUserFingerprintAuthenticated.clear();
        this.mTrustManager.clearAllBiometricRecognized(BiometricSourceType.FINGERPRINT, i);
        this.mLogger.d("clearFingerprintRecognized");
        Log.d("KeyguardUpdateMonitor", "clearFingerprintRecognized");
        for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onFingerprintsCleared();
            }
        }
    }

    public final void setForceIsDismissibleKeyguard(boolean z) {
        Assert.isMainThread();
        if (this.mFoldGracePeriodProvider.isEnabled()) {
            if (this.mKeyguardShowing && z) {
                this.mLogger.d("Skip setting forceIsDismissibleKeyguard to true. Keyguard already showing.");
                return;
            }
            if (this.mForceIsDismissible != z) {
                this.mForceIsDismissible = z;
                this.mLogger.logForceIsDismissibleKeyguard(z);
                for (int i = 0; i < this.mCallbacks.size(); i++) {
                    KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
                    if (keyguardUpdateMonitorCallback != null) {
                        keyguardUpdateMonitorCallback.onForceIsDismissibleChanged(forceIsDismissibleIsKeepingDeviceUnlocked());
                    }
                }
            }
        }
    }

    public boolean isSimPinVoiceSecure() {
        return isSimPinSecure();
    }

    public boolean isSimPinSecure() {
        synchronized (this.mSimDataLockObject) {
            try {
                Iterator it = this.mSimDatasBySlotId.values().iterator();
                while (it.hasNext()) {
                    if (isSimPinSecure(((SimData) it.next()).simState)) {
                        return true;
                    }
                }
                return false;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public int getSimState(int i) {
        throw new UnsupportedOperationException("Method not supported with flag simPinUseSlotId");
    }

    public int getSimStateForSlotId(int i) {
        synchronized (this.mSimDataLockObject) {
            try {
                if (!this.mSimDatasBySlotId.containsKey(Integer.valueOf(i))) {
                    return 0;
                }
                return ((SimData) this.mSimDatasBySlotId.get(Integer.valueOf(i))).simState;
            } catch (Throwable th) {
                throw th;
            }
        }
    }

    public final int getSlotId(int i) {
        int i2;
        synchronized (this.mSimDataLockObject) {
            try {
                HashMap map = this.mSimDatasBySlotId;
                int slotIndex = SubscriptionManager.getSlotIndex(i);
                if (!map.containsKey(Integer.valueOf(slotIndex))) {
                    refreshSimState(i, slotIndex);
                }
                SimData simData = (SimData) map.get(Integer.valueOf(slotIndex));
                i2 = simData != null ? simData.slotId : -1;
            } catch (Throwable th) {
                throw th;
            }
        }
        return i2;
    }

    public final boolean refreshSimState(int i, int i2) {
        boolean z;
        synchronized (this.mSimDataLockObject) {
            try {
                int simState = this.mTelephonyManager.getSimState(i2);
                if (!SubscriptionManager.isValidSubscriptionId(i)) {
                    invalidateSlot(i2);
                }
                SimData simData = (SimData) this.mSimDatasBySlotId.get(Integer.valueOf(i2));
                z = true;
                if (simData == null) {
                    this.mSimDatasBySlotId.put(Integer.valueOf(i2), new SimData(simState, i2, i));
                } else {
                    if (simData.simState == simState && simData.slotId == i2 && simData.subId == i) {
                        z = false;
                    }
                    simData.simState = simState;
                    simData.slotId = i2;
                    simData.subId = i;
                }
            } catch (Throwable th) {
                throw th;
            }
        }
        return z;
    }

    public void dispatchStartedWakingUp(int i) {
        synchronized (this) {
            this.mDeviceInteractive = true;
        }
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(319, i, 0));
    }

    public void dispatchStartedGoingToSleep(int i) {
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(321, i, 0));
    }

    public void dispatchFinishedGoingToSleep(int i) {
        synchronized (this) {
            this.mDeviceInteractive = false;
        }
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(320, i, 0));
    }

    public void dispatchScreenTurnedOff() {
        this.mHandler.sendEmptyMessage(332);
    }

    public void dispatchDreamingStarted() {
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(333, 1, 0));
    }

    public void dispatchDreamingStopped() {
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(333, 0, 0));
    }

    public void dispatchKeyguardGoingAway(boolean z) {
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(342, Boolean.valueOf(z)));
    }

    public void dispatchKeyguardDismissAnimationFinished() {
        this.mHandler.sendEmptyMessage(346);
    }

    public boolean isDeviceInteractive() {
        return this.mDeviceInteractive;
    }

    public boolean isGoingToSleep() {
        return this.mGoingToSleep;
    }

    public int getNextSubIdForState(int i) {
        List subscriptionInfo = getSubscriptionInfo(false);
        int i2 = -1;
        int i3 = Integer.MAX_VALUE;
        for (int i4 = 0; i4 < subscriptionInfo.size(); i4++) {
            SubscriptionInfo subscriptionInfo2 = (SubscriptionInfo) subscriptionInfo.get(i4);
            int subscriptionId = subscriptionInfo2.getSubscriptionId();
            int simSlotIndex = subscriptionInfo2.getSimSlotIndex();
            if (i == getSimStateForSlotId(simSlotIndex) && i3 > simSlotIndex) {
                i3 = simSlotIndex;
                i2 = subscriptionId;
            }
        }
        return i2;
    }

    public int getUnlockedSubIdForState(int i) {
        List subscriptionInfo = getSubscriptionInfo(false);
        for (int i2 = 0; i2 < subscriptionInfo.size(); i2++) {
            int subscriptionId = ((SubscriptionInfo) subscriptionInfo.get(i2)).getSubscriptionId();
            int slotIndex = SubscriptionManager.getSlotIndex(subscriptionId);
            if (i == getSimStateForSlotId(slotIndex) && KeyguardViewMediator.getUnlockTrackSimState(slotIndex) != 5) {
                return subscriptionId;
            }
        }
        return -1;
    }

    public SubscriptionInfo getSubscriptionInfoForSubId(int i) {
        List subscriptionInfo = getSubscriptionInfo(false);
        for (int i2 = 0; i2 < subscriptionInfo.size(); i2++) {
            SubscriptionInfo subscriptionInfo2 = (SubscriptionInfo) subscriptionInfo.get(i2);
            if (i == subscriptionInfo2.getSubscriptionId()) {
                return subscriptionInfo2;
            }
        }
        return null;
    }

    public boolean isFingerprintClass3() {
        return isFingerprintSupported() && isClass3Biometric((SensorPropertiesInternal) this.mFingerprintSensorProperties.get(0));
    }

    public final boolean isClass3Biometric(SensorPropertiesInternal sensorPropertiesInternal) {
        return sensorPropertiesInternal.sensorStrength == 2;
    }

    public int getActiveSlots() {
        return this.mTelephonyManager.getActiveModemCount();
    }

    @Override // com.android.systemui.CoreStartable, com.android.systemui.Dumpable
    @NeverCompile
    public void dump(PrintWriter printWriter, String[] strArr) {
        printWriter.println("KeyguardUpdateMonitor state:");
        printWriter.println("  forceIsDismissible=" + this.mForceIsDismissible);
        printWriter.println("  forceIsDismissibleIsKeepingDeviceUnlocked=" + forceIsDismissibleIsKeepingDeviceUnlocked());
        printWriter.println("  getUserHasTrust()=" + getUserHasTrust(this.mSelectedUserInteractor.getSelectedUserId()));
        printWriter.println("  getUserUnlockedWithBiometric()=" + getUserUnlockedWithBiometric(this.mSelectedUserInteractor.getSelectedUserId()));
        printWriter.println("  SIM States:");
        Iterator it = this.mSimDatas.values().iterator();
        while (it.hasNext()) {
            printWriter.println("    " + ((SimData) it.next()).toString());
        }
        printWriter.println("  Subs:");
        if (this.mSubscriptionInfo != null) {
            for (int i = 0; i < this.mSubscriptionInfo.size(); i++) {
                printWriter.println(FlymeStringSecure.securePhoneNum("    " + this.mSubscriptionInfo.get(i)));
            }
        }
        printWriter.println("  Current active data subId=" + this.mActiveMobileDataSubscription);
        printWriter.println("  Service states:");
        for (Integer num : this.mServiceStates.keySet()) {
            printWriter.println("    " + num.intValue() + "=" + this.mServiceStates.get(num));
        }
        if (isFingerprintSupported()) {
            int selectedUserId = this.mSelectedUserInteractor.getSelectedUserId();
            int strongAuthForUser = this.mStrongAuthTracker.getStrongAuthForUser(selectedUserId);
            BiometricAuthenticated biometricAuthenticated = (BiometricAuthenticated) this.mUserFingerprintAuthenticated.get(selectedUserId);
            printWriter.println("  Fingerprint state (user=" + selectedUserId + ")");
            StringBuilder sb = new StringBuilder();
            sb.append("    isFingerprintClass3=");
            sb.append(isFingerprintClass3());
            printWriter.println(sb.toString());
            printWriter.println("    areAllFpAuthenticatorsRegistered=" + this.mAuthController.areAllFingerprintAuthenticatorsRegistered());
            StringBuilder sb2 = new StringBuilder();
            sb2.append("    allowed=");
            sb2.append(biometricAuthenticated != null && isUnlockingWithBiometricAllowed(biometricAuthenticated.mIsStrongBiometric));
            printWriter.println(sb2.toString());
            StringBuilder sb3 = new StringBuilder();
            sb3.append("    auth'd=");
            sb3.append(biometricAuthenticated != null && biometricAuthenticated.mAuthenticated);
            printWriter.println(sb3.toString());
            printWriter.println("    authSinceBoot=" + getStrongAuthTracker().hasUserAuthenticatedSinceBoot());
            printWriter.println("    disabled(DPM)=" + isFingerprintDisabled(selectedUserId));
            printWriter.println("    possible=" + isUnlockWithFingerprintPossible(selectedUserId));
            printWriter.println("    listening: actual=" + this.mFingerprintRunningState + " expected=" + (shouldListenForFingerprint(isUdfpsEnrolled()) ? 1 : 0));
            StringBuilder sb4 = new StringBuilder();
            sb4.append("    strongAuthFlags=");
            sb4.append(Integer.toHexString(strongAuthForUser));
            printWriter.println(sb4.toString());
            printWriter.println("    trustManaged=" + getUserTrustIsManaged(selectedUserId));
            printWriter.println("    mFingerprintLockedOut=" + this.mFingerprintLockedOut);
            printWriter.println("    mFingerprintLockedOutPermanent=" + this.mFingerprintLockedOutPermanent);
            printWriter.println("    enabledByUser=" + this.mBiometricEnabledForUser.get(selectedUserId));
            printWriter.println("    mKeyguardOccluded=" + this.mKeyguardOccluded);
            printWriter.println("    mIsDreaming=" + this.mIsDreaming);
            printWriter.println("    mFingerprintListenOnOccludingActivitiesFromPackage=" + this.mAllowFingerprintOnOccludingActivitiesFromPackage);
            if (isUdfpsSupported()) {
                printWriter.println("        udfpsEnrolled=" + isUdfpsEnrolled());
                printWriter.println("        shouldListenForUdfps=" + shouldListenForFingerprint(true));
                printWriter.println("        isPrimaryBouncerShowingOrWillBeShowing=" + isPrimaryBouncerShowingOrWillBeShowing());
                printWriter.println("        mStatusBarState=" + StatusBarState.toString(this.mStatusBarState));
                printWriter.println("        isAlternateBouncerShowing=" + isAlternateBouncerShowing());
            } else if (isSfpsSupported()) {
                printWriter.println("        sfpsEnrolled=" + isSfpsEnrolled());
                printWriter.println("        shouldListenForSfps=" + shouldListenForFingerprint(false));
            }
            new DumpsysTableLogger("KeyguardFingerprintListen", KeyguardFingerprintListenModel.TABLE_HEADERS, this.mFingerprintListenBuffer.toList()).printTableData(printWriter);
        } else if (this.mFpm != null && this.mFingerprintSensorProperties.isEmpty()) {
            printWriter.println("  Fingerprint state (user=" + this.mSelectedUserInteractor.getSelectedUserId() + ")");
            StringBuilder sb5 = new StringBuilder();
            sb5.append("    mFingerprintSensorProperties.isEmpty=");
            sb5.append(this.mFingerprintSensorProperties.isEmpty());
            printWriter.println(sb5.toString());
            printWriter.println("    mFpm.isHardwareDetected=" + this.mFpm.isHardwareDetected());
            new DumpsysTableLogger("KeyguardFingerprintListen", KeyguardFingerprintListenModel.TABLE_HEADERS, this.mFingerprintListenBuffer.toList()).printTableData(printWriter);
        }
        int selectedUserId2 = this.mSelectedUserInteractor.getSelectedUserId();
        int strongAuthForUser2 = this.mStrongAuthTracker.getStrongAuthForUser(selectedUserId2);
        printWriter.println("    authSinceBoot=" + getStrongAuthTracker().hasUserAuthenticatedSinceBoot());
        printWriter.println("    strongAuthFlags=" + Integer.toHexString(strongAuthForUser2));
        printWriter.println("ActiveUnlockRunning=" + this.mTrustManager.isActiveUnlockRunning(this.mSelectedUserInteractor.getSelectedUserId()));
        printWriter.println("userUnlockedCache[userid=" + selectedUserId2 + "]=" + this.mUserIsUnlocked.get(selectedUserId2));
        printWriter.println("actualUserUnlocked[userid=" + selectedUserId2 + "]=" + this.mUserManager.isUserUnlocked(selectedUserId2));
        new DumpsysTableLogger("KeyguardActiveUnlockTriggers", KeyguardActiveUnlockModel.TABLE_HEADERS, this.mActiveUnlockTriggerBuffer.toList()).printTableData(printWriter);
        ArrayList arrayList = this.mCallbacks;
        if (arrayList != null && !arrayList.isEmpty()) {
            printWriter.println("KeyguardUpdateMonitorCallback:");
            for (int i2 = 0; i2 < this.mCallbacks.size(); i2++) {
                printWriter.println("" + ((KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i2)).get()));
            }
            return;
        }
        printWriter.println("KeyguardUpdateMonitorCallback is empty");
    }

    public void startBiometricWatchdog() {
        this.mBackgroundExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda20
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$startBiometricWatchdog$14();
            }
        });
    }

    public final /* synthetic */ void lambda$startBiometricWatchdog$14() {
        Trace.beginSection("#startBiometricWatchdog");
        if (this.mFpm != null) {
            this.mLogger.scheduleWatchdog("fingerprint");
            this.mFpm.scheduleWatchdog();
        }
        Trace.endSection();
    }

    public final void initFlyme() {
        boolean zShouldWaitForWellcomePage = shouldWaitForWellcomePage();
        this.mWaitWellcomPage = zShouldWaitForWellcomePage;
        if (zShouldWaitForWellcomePage) {
            watchForWellcomePage();
        }
    }

    public void setKeyguardInteractor(KeyguardInteractor keyguardInteractor) {
        this.mKeyguardInteractor = keyguardInteractor;
    }

    private KeyguardInteractor getKeyguardInteractor() {
        return this.mKeyguardInteractor;
    }

    public boolean getShouldInterruptShowLock() {
        return getKeyguardInteractor() != null && ((Boolean) getKeyguardInteractor().getShouldInterruptShowLock().getValue()).booleanValue();
    }

    public void setCameraGestureTrigger(boolean z) {
        this.mCameraGestureTrigger = z;
    }

    public boolean isKeyguardOccluded() {
        return this.mKeyguardOccluded;
    }

    public void setKeyguardMediaController(KeyguardMediaController keyguardMediaController) {
        this.mKeyguardMediaController = keyguardMediaController;
    }

    public boolean isEasyMode() {
        return ((SettingsUpdateMonitor) Dependency.get(SettingsUpdateMonitor.class)).isEasyMode();
    }

    public boolean usedDefaultColor() {
        return FlymeDynamicThemeUtils.getInstance(this.mContext).usedSystemUITheme() && !FlymeDynamicThemeUtils.getInstance(this.mContext).useSystemUIAutoColor();
    }

    public boolean isRealInteractive() {
        return !this.mGoingToSleep && this.mDeviceInteractive;
    }

    public BatteryStatus getBatteryStatus() {
        return this.mBatteryStatus;
    }

    public boolean isFpShowInAOD() {
        return Settings.System.getInt(this.mContext.getContentResolver(), "fp_show_in_aod", 1) == 1;
    }

    public final boolean fingerprintUsedAsUnlock() {
        return ((SettingsUpdateMonitor) Dependency.get(SettingsUpdateMonitor.class)).fingerprintUsedAsUnlock();
    }

    public boolean fingerprintMakeEnterAODFirstTime() {
        return isUdfpsEnrolled() && fingerprintUsedAsUnlock();
    }

    public Rect getUdfpsBound() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            return authController.getUdfpsBound();
        }
        return null;
    }

    public Rect getUdfpsSensorBound() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            return authController.getUdfpsSensorBounds();
        }
        return null;
    }

    public boolean fingerprintMakeEnterAOD() {
        return isUdfpsEnrolled() && fingerprintUsedAsUnlock() && isFpShowInAOD();
    }

    public boolean isUserUnlocked() {
        UserManager userManager = this.mUserManager;
        if (userManager == null) {
            return false;
        }
        return userManager.isUserUnlocked();
    }

    public void setPasswordMatched(boolean z) {
        this.mPasswordMatched = z;
    }

    public boolean isPasswordMatched() {
        return this.mPasswordMatched;
    }

    public void resetFaceLockout() {
        Log.d("KeyguardUpdateMonitor", "resetFaceLockout: ");
        FaceManager faceManager = this.mFaceManager;
        if (faceManager != null) {
            faceManager.resetLockout(1, getCurrentUser(), new byte[69]);
        }
    }

    public void onControlScreenOffChanged(boolean z) {
        if (z != this.mControlScreenOff) {
            this.mControlScreenOff = z;
            this.mHandler.obtainMessage(532).sendToTarget();
        }
    }

    public boolean isFingerprintLockedOutPermanent() {
        return this.mFingerprintLockedOutPermanent;
    }

    public final void handleControlScreenOffChanged(boolean z) {
        Assert.isMainThread();
        Log.d("KeyguardUpdateMonitor", "handleControlScreenOffChanged");
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onControlScreenOffChanged(z);
            }
        }
    }

    public boolean isKeyguardExit() {
        return this.mStatusBarState == 0;
    }

    public int getFPAuthSystemMode() {
        return this.mFPAuthSystemMode;
    }

    public void setFPAuthSystemMode(int i) {
        this.mFPAuthSystemMode = i;
    }

    public boolean isNeedStrongAuth() {
        StrongAuthTracker strongAuthTracker = this.mStrongAuthTracker;
        if (strongAuthTracker == null) {
            return false;
        }
        return !strongAuthTracker.isUnlockingWithBiometricAllowed(true);
    }

    public void resetNeedShowFakeFpIcon() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.resetNeedShowFakeFpIcon();
        }
    }

    public boolean isFakeFingerprintVisible() {
        AuthController authController = this.mAuthController;
        if (authController == null) {
            return false;
        }
        return authController.isFakeFpIconShow();
    }

    public final void updateFakeFingerprintListeningState(boolean z) {
        if (isUdfpsSupported() && isUdfpsEnrolled() && fingerprintUsedAsUnlock() && !this.mGoingToSleep && this.mDeviceInteractive) {
            boolean zIsChargeViewAttached = ChargeAnimationController.getInstance(this.mContext).isChargeViewAttached();
            boolean zIsMzDialogShowing = ChargeAnimationController.getInstance(this.mContext).isMzDialogShowing();
            boolean zIsTipsViewAdded = ((PalmRejection) Dependency.get(PalmRejection.class)).isTipsViewAdded();
            boolean z2 = (this.mStatusBarState != 1 || z || this.mPrimaryBouncerIsOrWillBeShowing || this.mCenterState != 0 || zIsTipsViewAdded || this.mImeShowing || this.mLaunchingApp || !(this.mFingerprintLockedOut || ((getIsFaceAuthenticated() && isKeyguardVisible()) || !isUserUnlocked() || isNeedStrongAuth() || this.mKeyguardDismissPauseForEditor)) || zIsChargeViewAttached || this.mKeyguardGoingAway || zIsMzDialogShowing) ? false : true;
            Log.d("KeyguardUpdateMonitor", "updateFakeFingerprintListeningState mStatusBarState=" + this.mStatusBarState + ", shouldListenForFakeFingerprint=" + z2 + ", showFakeFpIcon shouldListenForFingerprint=" + z + ", mPrimaryBouncerIsOrWillBeShowing=" + this.mPrimaryBouncerIsOrWillBeShowing + ", PalmRejection.tipsViewAdded=" + zIsTipsViewAdded + ", mImeShowing=" + this.mImeShowing + ", mFaceRecognitionSucceeded=" + getIsFaceAuthenticated() + ", isKeyguardVisible=" + isKeyguardVisible() + ", isUserUnlocked=" + isUserUnlocked() + ", isNeedStrongAuth=" + isNeedStrongAuth() + ", chargingViewAttached=" + zIsChargeViewAttached + ", mKeyguardGoingAway=" + this.mKeyguardGoingAway + ", chargingMzDialogShowing=" + zIsMzDialogShowing);
            if (z2) {
                this.mAuthController.showFakeFpIcon();
                boolean z3 = FlymeWideFingerPrintUtils.isLeftRightIconToGrowing;
                this.mAuthController.updateLeftRightIconAlphaForFakeFp(z3 ? 1.0f : 0.0f, z3 ? 0.0f : 1.0f);
                this.mAuthController.startLeftRightIconHideAnimForFakeFp(2000L);
                return;
            }
            this.mAuthController.hideFakeFpIcon();
        }
    }

    public boolean shouldShowFakeFPIcon() {
        boolean z = true;
        if (this.mStatusBarState != 1 || !isUdfpsSupported() || !isUdfpsEnrolled() || !fingerprintUsedAsUnlock() || shouldListenForFingerprint(isUdfpsSupported()) || ((this.mPrimaryBouncerFullyShown && !this.mGoingToSleep && this.mDeviceInteractive) || ((PalmRejection) Dependency.get(PalmRejection.class)).isTipsViewAdded() || this.mImeShowing || (!this.mFingerprintLockedOut && !getIsFaceAuthenticated() && !isNeedStrongAuth() && isUserUnlocked() && !this.mKeyguardDismissPauseForEditor))) {
            z = false;
        }
        Log.d("KeyguardUpdateMonitor", "shouldShowFakeFPIcon=" + z + ", isUdfpsSupported()=" + isUdfpsSupported() + ", isUdfpsEnrolled()=" + isUdfpsEnrolled() + ", fingerprintUsedAsUnlock()=" + fingerprintUsedAsUnlock() + ", shouldListenForFingerprint=" + shouldListenForFingerprint(isUdfpsSupported()) + ", mPrimaryBouncerFullyShown=" + this.mPrimaryBouncerFullyShown + ", mGoingToSleep=" + this.mGoingToSleep + ", mDeviceInteractive=" + this.mDeviceInteractive + ", PalmRejection.tipsViewAdded=" + ((PalmRejection) Dependency.get(PalmRejection.class)).isTipsViewAdded() + ", mImeShowing=" + this.mImeShowing + ", IsFaceAuthenticated=" + getIsFaceAuthenticated() + ", mFingerprintLockedOut=" + this.mFingerprintLockedOut + ", isNeedStrongAuth=" + isNeedStrongAuth() + ", isUserUnlocked=" + isUserUnlocked() + ", mKeyguardDismissPauseForEditor=" + this.mKeyguardDismissPauseForEditor);
        return z;
    }

    public void setImeShowing(boolean z) {
        Log.d("KeyguardUpdateMonitor", "setImeShowing: isImeShowing = " + z);
        this.mImeShowing = z;
        updateFingerprintListeningState(2);
    }

    public void setLaunchingApp(boolean z, String str) {
        Log.d("KeyguardUpdateMonitor", "setLaunchingApp: isLaunchingApp = " + z + ", reason=" + str);
        this.mLaunchingApp = z;
        updateFingerprintListeningState(2);
    }

    public void onCenterStateChanged(int i) {
        if (i != this.mCenterState) {
            this.mCenterState = i;
            updateFingerprintListeningState(2);
            updateFpIconForApp();
        }
    }

    public void updateFpIconForApp() {
        Log.d("KeyguardUpdateMonitor", "updateFpIconForApp mCenterState=" + this.mCenterState);
        AuthController authController = this.mAuthController;
        if (authController != null) {
            int i = this.mCenterState;
            if (i == 2 || i == 1) {
                authController.hideFpIcon();
            } else if (i == 0) {
                authController.showFpIcon();
            }
        }
    }

    public boolean isKeyguardDismissPauseForEditor() {
        return this.mKeyguardDismissPauseForEditor;
    }

    public void setKeyguardDismissPauseForEditor(boolean z) {
        this.mKeyguardDismissPauseForEditor = z;
    }

    public boolean isLaunchUIGroupSelectActivity() {
        return this.mIsLaunchUIGroupSelectActivity;
    }

    public void setIsLaunchUIGroupSelectActivity(boolean z) {
        this.mIsLaunchUIGroupSelectActivity = z;
    }

    public void showFingerprintDownAnim() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.showFingerprintDownAnim();
        }
    }

    public void showFingerprintDownAnimLeft() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.showFingerprintDownAnimLeft();
        }
    }

    public void showFingerprintDownAnimRight() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.showFingerprintDownAnimRight();
        }
    }

    public void hideFingerprintDownAnim() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.hideFingerprintDownAnim();
        }
    }

    public void hideFingerprintDownAnimLeft() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.hideFingerprintDownAnimLeft();
        }
    }

    public void hideFingerprintDownAnimRight() {
        AuthController authController = this.mAuthController;
        if (authController != null) {
            authController.hideFingerprintDownAnimRight();
        }
    }

    public void setIsFPVisibleWhenAODAuth(boolean z) {
        this.mIsFPVisibleWhenAODAuth = z;
    }

    public int getCurrentSystemMode() {
        return ((SystemModeUtils) Dependency.get(SystemModeUtils.class)).getCurrentSystemMode();
    }

    public final boolean hasLockPassword() {
        LockPatternUtils lockPatternUtils = this.mLockPatternUtils;
        if (lockPatternUtils != null) {
            return lockPatternUtils.isSecure(this.mSelectedUserInteractor.getSelectedUserId());
        }
        return false;
    }

    public boolean isPrivateModeEnable() {
        return ((SystemModeUtils) Dependency.get(SystemModeUtils.class)).isPrivateModeEnable();
    }

    public boolean isPrivateMode() {
        return ((SystemModeUtils) Dependency.get(SystemModeUtils.class)).isPrivateMode();
    }

    public boolean getUserUnlockedWithFingerprint() {
        BiometricAuthenticated biometricAuthenticated = (BiometricAuthenticated) this.mUserFingerprintAuthenticated.get(this.mSelectedUserInteractor.getSelectedUserId());
        return biometricAuthenticated != null && biometricAuthenticated.mAuthenticated && isUnlockingWithBiometricAllowed(biometricAuthenticated.mIsStrongBiometric);
    }

    public int getSystemModeByFingerId(int i) {
        int i2 = Settings.Secure.getInt(this.mContext.getContentResolver(), "mz_fp_id_secrets_mode", 0);
        Log.d("KeyguardUpdateMonitor", "fingerId == " + i + ", saveFpId == " + i2);
        if (i2 == i) {
            Log.d("KeyguardUpdateMonitor", "isPrivateModeEnable()== " + isPrivateModeEnable());
            if (isPrivateModeEnable()) {
                return 1;
            }
        } else {
            int i3 = Settings.Secure.getInt(this.mContext.getContentResolver(), "mz_fp_id_guest_mode", 0);
            Log.d("KeyguardUpdateMonitor", "saveFpId 2 == " + i3 + ", isGuestModeEnable() == " + isGuestModeEnable());
            if (i3 == i && isGuestModeEnable()) {
                return 2;
            }
        }
        return 0;
    }

    public boolean isGuestModeEnable() {
        return Settings.System.getInt(this.mContext.getContentResolver(), "mz_guard_mode", 0) == 1 && this.mLockPatternUtils.isLockPasswordEnabled(getCurrentUser());
    }

    public boolean shouldFaceRecognitionRun() {
        boolean zIsFaceRecognitionEnable = ((SettingsUpdateMonitor) Dependency.get(SettingsUpdateMonitor.class)).isFaceRecognitionEnable();
        ((SystemModeUtils) Dependency.get(SystemModeUtils.class)).lossMode();
        return (zIsFaceRecognitionEnable && this.mLockPatternUtils.isSecure(0)) & ((((SettingsUpdateMonitor) Dependency.get(SettingsUpdateMonitor.class)).isMzPowerSaveMode() || ((FindPhoneMgr) Dependency.get(FindPhoneMgr.class)).isFindPhoneLocked() || isSimPinSecure()) ? false : true);
    }

    public boolean getCanSetFingerprintState() {
        return isUnlockWithFingerprintPossible(getCurrentUser()) && isUnderFingerprint();
    }

    public boolean getIsFingerprintFreeze() {
        return this.mIsFingerprintFreeze;
    }

    public void notifyUpdateFingerprintListeningState() {
        updateFingerprintListeningState(2);
    }

    public int getCurrentUser() {
        return this.mSelectedUserInteractor.getSelectedUserId();
    }

    public final void notifyAuthenticationStatusChanged(FaceAuthenticationStatus faceAuthenticationStatus) {
        Assert.isMainThread();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onAuthenticationStatusChanged(faceAuthenticationStatus);
            }
        }
    }

    public final boolean shouldWaitForWellcomePage() {
        return (BuildExt.isProductInternational() || BuildExt.IS_SHOPDEMO.booleanValue() || (WelcomePageMgr.getWelcomePageVersion(this.mContext) != 0 && !WelcomePageMgr.shouldEnableWelcomePage(this.mContext))) ? false : true;
    }

    public final void handleWelcomePageChange() {
        Assert.isMainThread();
        this.mWaitWellcomPage = shouldWaitForWellcomePage();
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onWelcomePageChange(this.mWaitWellcomPage);
            }
        }
    }

    public final void watchForWellcomePage() {
        this.mWellcomePageObserver = new ContentObserver(this.mHandler) { // from class: com.android.keyguard.KeyguardUpdateMonitor.20
            @Override // android.database.ContentObserver
            public void onChange(boolean z) {
                super.onChange(z);
                KeyguardUpdateMonitor.this.mHandler.obtainMessage(530).sendToTarget();
            }
        };
        this.mContext.getContentResolver().registerContentObserver(Settings.System.getUriFor("wellcome_page_version"), false, this.mWellcomePageObserver);
    }

    public void notifyLockWallpaperColorChanged() {
        Log.d("KeyguardUpdateMonitor", "notifyLockWallpaperColorChanged");
        for (int i = 0; i < this.mCallbacks.size(); i++) {
            KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i)).get();
            if (keyguardUpdateMonitorCallback != null) {
                keyguardUpdateMonitorCallback.onLockWallpaperColorChanged();
            }
        }
    }

    public void setViewAlphaWhenCharge(float f) {
        KeyguardInteractor keyguardInteractor = this.mKeyguardInteractor;
        if (keyguardInteractor != null) {
            keyguardInteractor.setViewAlphaWhenCharge(f);
        }
    }

    public float getViewAlphaWhenCharge() {
        KeyguardInteractor keyguardInteractor = this.mKeyguardInteractor;
        if (keyguardInteractor != null) {
            return ((Float) keyguardInteractor.getViewAlphaWhenCharge().getValue()).floatValue();
        }
        return 0.0f;
    }

    public final void threadSafeCallSimStateChanged(final KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback, final int i, final int i2, final int i3) {
        this.mMainExecutor.execute(new Runnable() { // from class: com.android.keyguard.KeyguardUpdateMonitor$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$threadSafeCallSimStateChanged$15(keyguardUpdateMonitorCallback, i, i2, i3);
            }
        });
    }

    public final /* synthetic */ void lambda$threadSafeCallSimStateChanged$15(KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback, int i, int i2, int i3) {
        if (keyguardUpdateMonitorCallback == null) {
            for (int i4 = 0; i4 < this.mCallbacks.size(); i4++) {
                KeyguardUpdateMonitorCallback keyguardUpdateMonitorCallback2 = (KeyguardUpdateMonitorCallback) ((WeakReference) this.mCallbacks.get(i4)).get();
                if (keyguardUpdateMonitorCallback2 != null) {
                    keyguardUpdateMonitorCallback2.onSimStateChanged(i, i2, i3);
                }
            }
            return;
        }
        keyguardUpdateMonitorCallback.onSimStateChanged(i, i2, i3);
    }
}
