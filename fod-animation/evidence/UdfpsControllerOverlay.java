package com.android.systemui.biometrics;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.fingerprint.IUdfpsOverlayControllerCallback;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;
import android.provider.Settings;
import android.util.Log;
import android.util.RotationUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.assist.ui.DisplayUtils;
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor;
import com.android.systemui.biometrics.shared.model.UdfpsOverlayParams;
import com.android.systemui.biometrics.ui.binder.UdfpsTouchOverlayBinder;
import com.android.systemui.biometrics.ui.view.UdfpsTouchOverlay;
import com.android.systemui.biometrics.ui.viewmodel.UdfpsTouchOverlayViewModel;
import com.android.systemui.bouncer.domain.interactor.AlternateBouncerInteractor;
import com.android.systemui.bouncer.domain.interactor.PrimaryBouncerInteractor;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor;
import com.android.systemui.keyguard.shared.model.KeyguardState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.power.domain.interactor.PowerInteractor;
import com.android.systemui.power.shared.model.WakefulnessModel;
import com.android.systemui.res.R$dimen;
import com.android.systemui.res.R$layout;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.shade.domain.interactor.ShadeInteractor;
import com.android.systemui.statusbar.LockscreenShadeTransitionController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.SystemUIDialogManager;
import com.android.systemui.statusbar.phone.UnlockedScreenOffAnimationController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.user.domain.interactor.SelectedUserInteractor;
import com.meizu.systemui.shared.compat.DeviceStateManagerCompat;
import dagger.Lazy;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.IntrinsicsKt__IntrinsicsKt;
import kotlin.coroutines.jvm.internal.ContinuationImpl;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowCollector;
import kotlinx.coroutines.flow.SharedFlow;

/*  JADX ERROR: Error in decompile pass: KotlinMetadataDecompile
    java.lang.IllegalArgumentException: Provided Metadata instance does not have metadataVersion in it and therefore is malformed and cannot be read.
    	at kotlin.metadata.jvm.internal.JvmReadUtils.checkMetadataVersionForRead(JvmReadUtils.kt:79)
    	at kotlin.metadata.jvm.internal.JvmReadUtils.readMetadataImpl$kotlin_metadata_jvm(JvmReadUtils.kt:46)
    	at kotlin.metadata.jvm.KotlinClassMetadata$Companion.readLenient(KotlinClassMetadata.kt:418)
    	at jadx.plugins.kotlin.metadata.utils.KotlinMetadataExtKt.getKotlinClassMetadata(KotlinMetadataExt.kt:68)
    	at jadx.plugins.kotlin.metadata.utils.KmClassWrapper$Companion.getWrapper(KmClassWrapper.kt:31)
    	at jadx.plugins.kotlin.metadata.pass.KotlinMetadataDecompilePass.visit(KotlinMetadataDecompilePass.kt:33)
    	at jadx.plugins.kotlin.metadata.pass.KotlinMetadataDecompilePass.visit(KotlinMetadataDecompilePass.kt:31)
    */
/* JADX INFO: compiled from: UdfpsControllerOverlay.kt */
/* JADX INFO: loaded from: classes2.dex */
public final class UdfpsControllerOverlay {
    public final AccessibilityManager accessibilityManager;
    public final ActivityTransitionAnimator activityTransitionAnimator;
    public Runnable addViewRunnable;
    public final AlternateBouncerInteractor alternateBouncerInteractor;
    public int animWidth;
    public final ConfigurationController configurationController;
    public final Context context;
    public UdfpsController controller;
    public final IUdfpsOverlayControllerCallback controllerCallback;
    public final WindowManager.LayoutParams coreLayoutParams;
    public final Flow currentStateUpdatedToOffAodOrDozing;
    public final Lazy defaultUdfpsTouchOverlayViewModel;
    public final Lazy deviceEntryUdfpsTouchOverlayViewModel;
    public final SystemUIDialogManager dialogManager;
    public final DumpManager dumpManager;
    public final LayoutInflater inflater;
    public final boolean isDebuggable;
    public final KeyguardStateController keyguardStateController;
    public final KeyguardUpdateMonitor keyguardUpdateMonitor;
    public Job listenForCurrentKeyguardState;
    public final Function2 onTouch;
    public UdfpsOverlayParams overlayParams;
    public AccessibilityManager.TouchExplorationStateChangeListener overlayTouchListener;
    public UdfpsTouchOverlay overlayTouchView;
    public UdfpsView overlayViewLegacy;
    public final PowerInteractor powerInteractor;
    public final PrimaryBouncerInteractor primaryBouncerInteractor;
    public final Lazy promptUdfpsTouchOverlayViewModel;
    public final long requestId;
    public final int requestReason;
    public final CoroutineScope scope;
    public final SelectedUserInteractor selectedUserInteractor;
    public Rect sensorBounds;
    public ShadeExpansionStateManager shadeExpansionStateManager;
    public final ShadeInteractor shadeInteractor;
    public final StatusBarKeyguardViewManager statusBarKeyguardViewManager;
    public final StatusBarStateController statusBarStateController;
    public boolean touchExplorationEnabled;
    public final LockscreenShadeTransitionController transitionController;
    public final KeyguardTransitionInteractor transitionInteractor;
    public UdfpsDisplayModeProvider udfpsDisplayModeProvider;
    public final UdfpsKeyguardAccessibilityDelegate udfpsKeyguardAccessibilityDelegate;
    public final UdfpsOverlayInteractor udfpsOverlayInteractor;
    public final UnlockedScreenOffAnimationController unlockedScreenOffAnimationController;
    public final WindowManager windowManager;

    public UdfpsControllerOverlay(Context context, LayoutInflater layoutInflater, WindowManager windowManager, AccessibilityManager accessibilityManager, StatusBarStateController statusBarStateController, StatusBarKeyguardViewManager statusBarKeyguardViewManager, KeyguardUpdateMonitor keyguardUpdateMonitor, SystemUIDialogManager systemUIDialogManager, DumpManager dumpManager, LockscreenShadeTransitionController lockscreenShadeTransitionController, ConfigurationController configurationController, KeyguardStateController keyguardStateController, UnlockedScreenOffAnimationController unlockedScreenOffAnimationController, UdfpsDisplayModeProvider udfpsDisplayModeProvider, long j, int i, IUdfpsOverlayControllerCallback iUdfpsOverlayControllerCallback, Function2 function2, ActivityTransitionAnimator activityTransitionAnimator, PrimaryBouncerInteractor primaryBouncerInteractor, AlternateBouncerInteractor alternateBouncerInteractor, UdfpsKeyguardAccessibilityDelegate udfpsKeyguardAccessibilityDelegate, KeyguardTransitionInteractor keyguardTransitionInteractor, SelectedUserInteractor selectedUserInteractor, Lazy lazy, Lazy lazy2, Lazy lazy3, ShadeInteractor shadeInteractor, UdfpsOverlayInteractor udfpsOverlayInteractor, PowerInteractor powerInteractor, CoroutineScope coroutineScope) {
        this(context, layoutInflater, windowManager, accessibilityManager, statusBarStateController, statusBarKeyguardViewManager, keyguardUpdateMonitor, systemUIDialogManager, dumpManager, lockscreenShadeTransitionController, configurationController, keyguardStateController, unlockedScreenOffAnimationController, udfpsDisplayModeProvider, j, i, iUdfpsOverlayControllerCallback, function2, activityTransitionAnimator, primaryBouncerInteractor, alternateBouncerInteractor, false, udfpsKeyguardAccessibilityDelegate, keyguardTransitionInteractor, selectedUserInteractor, lazy, lazy2, lazy3, shadeInteractor, udfpsOverlayInteractor, powerInteractor, coroutineScope, 2097152, null);
    }

    public UdfpsControllerOverlay(Context context, LayoutInflater layoutInflater, WindowManager windowManager, AccessibilityManager accessibilityManager, StatusBarStateController statusBarStateController, StatusBarKeyguardViewManager statusBarKeyguardViewManager, KeyguardUpdateMonitor keyguardUpdateMonitor, SystemUIDialogManager systemUIDialogManager, DumpManager dumpManager, LockscreenShadeTransitionController lockscreenShadeTransitionController, ConfigurationController configurationController, KeyguardStateController keyguardStateController, UnlockedScreenOffAnimationController unlockedScreenOffAnimationController, UdfpsDisplayModeProvider udfpsDisplayModeProvider, long j, int i, IUdfpsOverlayControllerCallback iUdfpsOverlayControllerCallback, Function2 function2, ActivityTransitionAnimator activityTransitionAnimator, PrimaryBouncerInteractor primaryBouncerInteractor, AlternateBouncerInteractor alternateBouncerInteractor, boolean z, UdfpsKeyguardAccessibilityDelegate udfpsKeyguardAccessibilityDelegate, KeyguardTransitionInteractor keyguardTransitionInteractor, SelectedUserInteractor selectedUserInteractor, Lazy lazy, Lazy lazy2, Lazy lazy3, ShadeInteractor shadeInteractor, UdfpsOverlayInteractor udfpsOverlayInteractor, PowerInteractor powerInteractor, CoroutineScope coroutineScope) {
        this.context = context;
        this.inflater = layoutInflater;
        this.windowManager = windowManager;
        this.accessibilityManager = accessibilityManager;
        this.statusBarStateController = statusBarStateController;
        this.statusBarKeyguardViewManager = statusBarKeyguardViewManager;
        this.keyguardUpdateMonitor = keyguardUpdateMonitor;
        this.dialogManager = systemUIDialogManager;
        this.dumpManager = dumpManager;
        this.transitionController = lockscreenShadeTransitionController;
        this.configurationController = configurationController;
        this.keyguardStateController = keyguardStateController;
        this.unlockedScreenOffAnimationController = unlockedScreenOffAnimationController;
        this.udfpsDisplayModeProvider = udfpsDisplayModeProvider;
        this.requestId = j;
        this.requestReason = i;
        this.controllerCallback = iUdfpsOverlayControllerCallback;
        this.onTouch = function2;
        this.activityTransitionAnimator = activityTransitionAnimator;
        this.primaryBouncerInteractor = primaryBouncerInteractor;
        this.alternateBouncerInteractor = alternateBouncerInteractor;
        this.isDebuggable = z;
        this.udfpsKeyguardAccessibilityDelegate = udfpsKeyguardAccessibilityDelegate;
        this.transitionInteractor = keyguardTransitionInteractor;
        this.selectedUserInteractor = selectedUserInteractor;
        this.deviceEntryUdfpsTouchOverlayViewModel = lazy;
        this.defaultUdfpsTouchOverlayViewModel = lazy2;
        this.promptUdfpsTouchOverlayViewModel = lazy3;
        this.shadeInteractor = shadeInteractor;
        this.udfpsOverlayInteractor = udfpsOverlayInteractor;
        this.powerInteractor = powerInteractor;
        this.scope = coroutineScope;
        final SharedFlow currentKeyguardState = keyguardTransitionInteractor.getCurrentKeyguardState();
        final Flow flow = new Flow() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay$special$$inlined$filter$1

            /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsControllerOverlay$special$$inlined$filter$1$2, reason: invalid class name */
            /* JADX INFO: compiled from: Emitters.kt */
            public final class AnonymousClass2 implements FlowCollector {
                public final /* synthetic */ FlowCollector $this_unsafeFlow;

                /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsControllerOverlay$special$$inlined$filter$1$2$1, reason: invalid class name */
                @Metadata
                public final class AnonymousClass1 extends ContinuationImpl {
                    Object L$0;
                    Object L$1;
                    int label;
                    /* synthetic */ Object result;

                    public AnonymousClass1(Continuation continuation) {
                        super(continuation);
                    }

                    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
                    public final Object invokeSuspend(Object obj) {
                        this.result = obj;
                        this.label |= Integer.MIN_VALUE;
                        return AnonymousClass2.this.emit(null, this);
                    }
                }

                public AnonymousClass2(FlowCollector flowCollector) {
                    this.$this_unsafeFlow = flowCollector;
                }

                /* JADX WARN: Code duplicated, block: B:7:0x0013  */
                @Override // kotlinx.coroutines.flow.FlowCollector
                public final Object emit(Object obj, Continuation continuation) {
                    AnonymousClass1 anonymousClass1;
                    if (continuation instanceof AnonymousClass1) {
                        anonymousClass1 = (AnonymousClass1) continuation;
                        int i = anonymousClass1.label;
                        if ((i & Integer.MIN_VALUE) != 0) {
                            anonymousClass1.label = i - Integer.MIN_VALUE;
                        } else {
                            anonymousClass1 = new AnonymousClass1(continuation);
                        }
                    } else {
                        anonymousClass1 = new AnonymousClass1(continuation);
                    }
                    Object obj2 = anonymousClass1.result;
                    Object coroutine_suspended = IntrinsicsKt__IntrinsicsKt.getCOROUTINE_SUSPENDED();
                    int i2 = anonymousClass1.label;
                    if (i2 == 0) {
                        ResultKt.throwOnFailure(obj2);
                        FlowCollector flowCollector = this.$this_unsafeFlow;
                        KeyguardState keyguardState = (KeyguardState) obj;
                        if (keyguardState == KeyguardState.OFF || keyguardState == KeyguardState.AOD || keyguardState == KeyguardState.DOZING) {
                            anonymousClass1.label = 1;
                            if (flowCollector.emit(obj, anonymousClass1) == coroutine_suspended) {
                                return coroutine_suspended;
                            }
                        }
                    } else {
                        if (i2 != 1) {
                            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
                        }
                        ResultKt.throwOnFailure(obj2);
                    }
                    return Unit.INSTANCE;
                }
            }

            @Override // kotlinx.coroutines.flow.Flow
            public Object collect(FlowCollector flowCollector, Continuation continuation) {
                Object objCollect = currentKeyguardState.collect(new AnonymousClass2(flowCollector), continuation);
                return objCollect == IntrinsicsKt__IntrinsicsKt.getCOROUTINE_SUSPENDED() ? objCollect : Unit.INSTANCE;
            }
        };
        this.currentStateUpdatedToOffAodOrDozing = new Flow() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay$special$$inlined$map$1

            /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsControllerOverlay$special$$inlined$map$1$2, reason: invalid class name */
            /* JADX INFO: compiled from: Emitters.kt */
            public final class AnonymousClass2 implements FlowCollector {
                public final /* synthetic */ FlowCollector $this_unsafeFlow;

                /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsControllerOverlay$special$$inlined$map$1$2$1, reason: invalid class name */
                @Metadata
                public final class AnonymousClass1 extends ContinuationImpl {
                    Object L$0;
                    int label;
                    /* synthetic */ Object result;

                    public AnonymousClass1(Continuation continuation) {
                        super(continuation);
                    }

                    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
                    public final Object invokeSuspend(Object obj) {
                        this.result = obj;
                        this.label |= Integer.MIN_VALUE;
                        return AnonymousClass2.this.emit(null, this);
                    }
                }

                public AnonymousClass2(FlowCollector flowCollector) {
                    this.$this_unsafeFlow = flowCollector;
                }

                /* JADX WARN: Code duplicated, block: B:7:0x0013  */
                @Override // kotlinx.coroutines.flow.FlowCollector
                public final Object emit(Object obj, Continuation continuation) {
                    AnonymousClass1 anonymousClass1;
                    if (continuation instanceof AnonymousClass1) {
                        anonymousClass1 = (AnonymousClass1) continuation;
                        int i = anonymousClass1.label;
                        if ((i & Integer.MIN_VALUE) != 0) {
                            anonymousClass1.label = i - Integer.MIN_VALUE;
                        } else {
                            anonymousClass1 = new AnonymousClass1(continuation);
                        }
                    } else {
                        anonymousClass1 = new AnonymousClass1(continuation);
                    }
                    Object obj2 = anonymousClass1.result;
                    Object coroutine_suspended = IntrinsicsKt__IntrinsicsKt.getCOROUTINE_SUSPENDED();
                    int i2 = anonymousClass1.label;
                    if (i2 == 0) {
                        ResultKt.throwOnFailure(obj2);
                        FlowCollector flowCollector = this.$this_unsafeFlow;
                        Unit unit = Unit.INSTANCE;
                        anonymousClass1.label = 1;
                        if (flowCollector.emit(unit, anonymousClass1) == coroutine_suspended) {
                            return coroutine_suspended;
                        }
                    } else {
                        if (i2 != 1) {
                            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
                        }
                        ResultKt.throwOnFailure(obj2);
                    }
                    return Unit.INSTANCE;
                }
            }

            @Override // kotlinx.coroutines.flow.Flow
            public Object collect(FlowCollector flowCollector, Continuation continuation) {
                Object objCollect = flow.collect(new AnonymousClass2(flowCollector), continuation);
                return objCollect == IntrinsicsKt__IntrinsicsKt.getCOROUTINE_SUSPENDED() ? objCollect : Unit.INSTANCE;
            }
        };
        this.overlayParams = new UdfpsOverlayParams(null, null, 0, 0, 0.0f, 0, 0, 127, null);
        this.sensorBounds = new Rect();
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(2024, 0, -3);
        layoutParams.setTitle("UdfpsControllerOverlay");
        layoutParams.setFitInsetsTypes(0);
        layoutParams.gravity = 51;
        layoutParams.layoutInDisplayCutoutMode = 3;
        layoutParams.flags = 16777512;
        layoutParams.privateFlags = 538968064;
        layoutParams.accessibilityTitle = " ";
        if (DeviceStateManagerCompat.isCtsRunningStatic()) {
            layoutParams.inputFeatures = 4;
        }
        this.coreLayoutParams = layoutParams;
        this.animWidth = context.getResources().getDimensionPixelSize(R$dimen.fingerprint_animation_width);
    }

    public final long getRequestId() {
        return this.requestId;
    }

    public final int getRequestReason() {
        return this.requestReason;
    }

    public /* synthetic */ UdfpsControllerOverlay(Context context, LayoutInflater layoutInflater, WindowManager windowManager, AccessibilityManager accessibilityManager, StatusBarStateController statusBarStateController, StatusBarKeyguardViewManager statusBarKeyguardViewManager, KeyguardUpdateMonitor keyguardUpdateMonitor, SystemUIDialogManager systemUIDialogManager, DumpManager dumpManager, LockscreenShadeTransitionController lockscreenShadeTransitionController, ConfigurationController configurationController, KeyguardStateController keyguardStateController, UnlockedScreenOffAnimationController unlockedScreenOffAnimationController, UdfpsDisplayModeProvider udfpsDisplayModeProvider, long j, int i, IUdfpsOverlayControllerCallback iUdfpsOverlayControllerCallback, Function2 function2, ActivityTransitionAnimator activityTransitionAnimator, PrimaryBouncerInteractor primaryBouncerInteractor, AlternateBouncerInteractor alternateBouncerInteractor, boolean z, UdfpsKeyguardAccessibilityDelegate udfpsKeyguardAccessibilityDelegate, KeyguardTransitionInteractor keyguardTransitionInteractor, SelectedUserInteractor selectedUserInteractor, Lazy lazy, Lazy lazy2, Lazy lazy3, ShadeInteractor shadeInteractor, UdfpsOverlayInteractor udfpsOverlayInteractor, PowerInteractor powerInteractor, CoroutineScope coroutineScope, int i2, DefaultConstructorMarker defaultConstructorMarker) {
        this(context, layoutInflater, windowManager, accessibilityManager, statusBarStateController, statusBarKeyguardViewManager, keyguardUpdateMonitor, systemUIDialogManager, dumpManager, lockscreenShadeTransitionController, configurationController, keyguardStateController, unlockedScreenOffAnimationController, udfpsDisplayModeProvider, j, i, iUdfpsOverlayControllerCallback, function2, activityTransitionAnimator, primaryBouncerInteractor, alternateBouncerInteractor, (i2 & 2097152) != 0 ? Build.IS_DEBUGGABLE : z, udfpsKeyguardAccessibilityDelegate, keyguardTransitionInteractor, selectedUserInteractor, lazy, lazy2, lazy3, shadeInteractor, udfpsOverlayInteractor, powerInteractor, coroutineScope);
    }

    public final UdfpsView getOverlayViewLegacy() {
        return this.overlayViewLegacy;
    }

    public final View getTouchOverlay() {
        return this.overlayViewLegacy;
    }

    public final boolean isShowing() {
        return getTouchOverlay() != null;
    }

    public final boolean isHiding() {
        return getTouchOverlay() == null;
    }

    public final UdfpsAnimationViewController getAnimationViewController() {
        UdfpsView udfpsView = this.overlayViewLegacy;
        if (udfpsView != null) {
            return udfpsView.getAnimationViewController();
        }
        return null;
    }

    public final boolean shouldRemoveEnrollmentUi() {
        return this.isDebuggable && Settings.Global.getInt(this.context.getContentResolver(), "udfps_overlay_remove_enrollment_ui", 0) != 0;
    }

    public final boolean show(UdfpsOverlayParams udfpsOverlayParams) {
        if (getTouchOverlay() == null) {
            this.overlayParams = udfpsOverlayParams;
            this.sensorBounds = new Rect(udfpsOverlayParams.getSensorBounds());
            try {
                if (DeviceStateManagerCompat.isCtsRunningStatic()) {
                    UdfpsTouchOverlay udfpsTouchOverlay = (UdfpsTouchOverlay) this.inflater.inflate(R$layout.udfps_touch_overlay, (ViewGroup) null, false);
                    if (UdfpsControllerOverlayKt.isImportantForAccessibility(this.requestReason)) {
                        udfpsTouchOverlay.setImportantForAccessibility(2);
                    }
                    addViewNowOrLater(udfpsTouchOverlay, null);
                    int i = this.requestReason;
                    if (i == 3) {
                        UdfpsTouchOverlayBinder.bind(udfpsTouchOverlay, (UdfpsTouchOverlayViewModel) this.promptUdfpsTouchOverlayViewModel.get(), this.udfpsOverlayInteractor);
                    } else if (i == 4) {
                        UdfpsTouchOverlayBinder.bind(udfpsTouchOverlay, (UdfpsTouchOverlayViewModel) this.deviceEntryUdfpsTouchOverlayViewModel.get(), this.udfpsOverlayInteractor);
                    } else {
                        UdfpsTouchOverlayBinder.bind(udfpsTouchOverlay, (UdfpsTouchOverlayViewModel) this.defaultUdfpsTouchOverlayViewModel.get(), this.udfpsOverlayInteractor);
                    }
                    this.overlayTouchView = udfpsTouchOverlay;
                } else {
                    UdfpsView udfpsView = (UdfpsView) this.inflater.inflate(R$layout.udfps_view, (ViewGroup) null, false);
                    udfpsView.setOverlayParams(udfpsOverlayParams);
                    udfpsView.setUdfpsDisplayModeProvider(this.udfpsDisplayModeProvider);
                    UdfpsAnimationViewController udfpsAnimationViewControllerInflateUdfpsAnimation = inflateUdfpsAnimation(udfpsView);
                    if (udfpsAnimationViewControllerInflateUdfpsAnimation != null) {
                        udfpsAnimationViewControllerInflateUdfpsAnimation.init();
                        udfpsView.setAnimationViewController(udfpsAnimationViewControllerInflateUdfpsAnimation);
                        udfpsAnimationViewControllerInflateUdfpsAnimation.setShadeExpansionStateManager(this.shadeExpansionStateManager);
                    }
                    if (UdfpsControllerOverlayKt.isImportantForAccessibility(this.requestReason)) {
                        udfpsView.setImportantForAccessibility(2);
                    }
                    addViewNowOrLater(udfpsView, udfpsAnimationViewControllerInflateUdfpsAnimation);
                    udfpsView.setSensorRect(this.sensorBounds);
                    this.overlayViewLegacy = udfpsView;
                }
                final View touchOverlay = getTouchOverlay();
                if (touchOverlay != null) {
                    this.touchExplorationEnabled = this.accessibilityManager.isTouchExplorationEnabled();
                    AccessibilityManager.TouchExplorationStateChangeListener touchExplorationStateChangeListener = new AccessibilityManager.TouchExplorationStateChangeListener() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay$show$3$1
                        @Override // android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener
                        public final void onTouchExplorationStateChanged(boolean z) {
                            if (this.this$0.accessibilityManager.isTouchExplorationEnabled()) {
                                View view = touchOverlay;
                                final UdfpsControllerOverlay udfpsControllerOverlay = this.this$0;
                                view.setOnHoverListener(new View.OnHoverListener() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay$show$3$1.1
                                    @Override // android.view.View.OnHoverListener
                                    public final boolean onHover(View view2, MotionEvent motionEvent) {
                                        Function2 function2 = udfpsControllerOverlay.onTouch;
                                        view2.getClass();
                                        motionEvent.getClass();
                                        return ((Boolean) function2.invoke(view2, motionEvent)).booleanValue();
                                    }
                                });
                                touchOverlay.setOnTouchListener(null);
                                this.this$0.touchExplorationEnabled = true;
                                return;
                            }
                            touchOverlay.setOnHoverListener(null);
                            View view2 = touchOverlay;
                            final UdfpsControllerOverlay udfpsControllerOverlay2 = this.this$0;
                            view2.setOnTouchListener(new View.OnTouchListener() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay$show$3$1.2
                                @Override // android.view.View.OnTouchListener
                                public final boolean onTouch(View view3, MotionEvent motionEvent) {
                                    Function2 function2 = udfpsControllerOverlay2.onTouch;
                                    view3.getClass();
                                    motionEvent.getClass();
                                    return ((Boolean) function2.invoke(view3, motionEvent)).booleanValue();
                                }
                            });
                            this.this$0.touchExplorationEnabled = false;
                        }
                    };
                    this.overlayTouchListener = touchExplorationStateChangeListener;
                    this.accessibilityManager.addTouchExplorationStateChangeListener(touchExplorationStateChangeListener);
                    AccessibilityManager.TouchExplorationStateChangeListener touchExplorationStateChangeListener2 = this.overlayTouchListener;
                    if (touchExplorationStateChangeListener2 != null) {
                        touchExplorationStateChangeListener2.onTouchExplorationStateChanged(true);
                    }
                }
            } catch (RuntimeException e) {
                Log.e("UdfpsControllerOverlay", "showUdfpsOverlay | failed to add window", e);
            }
            return true;
        }
        Log.d("UdfpsControllerOverlay", "showUdfpsOverlay | the overlay is already showing");
        return false;
    }

    public final void addViewNowOrLater(final View view, final UdfpsAnimationViewController udfpsAnimationViewController) {
        if (!DeviceStateManagerCompat.isCtsRunningStatic()) {
            Trace.setCounter("UdfpsAddView", 1L);
            this.windowManager.addView(view, updateDimensions(this.coreLayoutParams, udfpsAnimationViewController));
            Log.d("UdfpsControllerOverlay", "add udfp window");
            return;
        }
        this.addViewRunnable = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay.addViewNowOrLater.1
            @Override // java.lang.Runnable
            public final void run() {
                Trace.setCounter("UdfpsAddView", 1L);
                WindowManager windowManager = UdfpsControllerOverlay.this.windowManager;
                View view2 = view;
                UdfpsControllerOverlay udfpsControllerOverlay = UdfpsControllerOverlay.this;
                windowManager.addView(view2, udfpsControllerOverlay.updateDimensions(udfpsControllerOverlay.coreLayoutParams, udfpsAnimationViewController));
                Log.d("UdfpsControllerOverlay", "add udfp window");
            }
        };
        if (((WakefulnessModel) this.powerInteractor.getDetailedWakefulness().getValue()).isAwake()) {
            addViewIfPending();
            return;
        }
        Job job = this.listenForCurrentKeyguardState;
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, null, 1, null);
        }
        this.listenForCurrentKeyguardState = BuildersKt__Builders_commonKt.launch$default(this.scope, null, null, new AnonymousClass2(null), 3, null);
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsControllerOverlay$addViewNowOrLater$2, reason: invalid class name */
    /* JADX INFO: compiled from: UdfpsControllerOverlay.kt */
    @Metadata
    final class AnonymousClass2 extends SuspendLambda implements Function2 {
        int label;

        public AnonymousClass2(Continuation continuation) {
            super(2, continuation);
        }

        @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
        public final Continuation create(Object obj, Continuation continuation) {
            return UdfpsControllerOverlay.this.new AnonymousClass2(continuation);
        }

        @Override // kotlin.jvm.functions.Function2
        public final Object invoke(CoroutineScope coroutineScope, Continuation continuation) {
            return ((AnonymousClass2) create(coroutineScope, continuation)).invokeSuspend(Unit.INSTANCE);
        }

        @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
        public final Object invokeSuspend(Object obj) {
            Object coroutine_suspended = IntrinsicsKt__IntrinsicsKt.getCOROUTINE_SUSPENDED();
            int i = this.label;
            if (i == 0) {
                ResultKt.throwOnFailure(obj);
                Flow flow = UdfpsControllerOverlay.this.currentStateUpdatedToOffAodOrDozing;
                final UdfpsControllerOverlay udfpsControllerOverlay = UdfpsControllerOverlay.this;
                FlowCollector flowCollector = new FlowCollector() { // from class: com.android.systemui.biometrics.UdfpsControllerOverlay.addViewNowOrLater.2.1
                    @Override // kotlinx.coroutines.flow.FlowCollector
                    public final Object emit(Unit unit, Continuation continuation) {
                        udfpsControllerOverlay.addViewIfPending();
                        return Unit.INSTANCE;
                    }
                };
                this.label = 1;
                if (flow.collect(flowCollector, this) == coroutine_suspended) {
                    return coroutine_suspended;
                }
            } else {
                if (i != 1) {
                    throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
                }
                ResultKt.throwOnFailure(obj);
            }
            return Unit.INSTANCE;
        }
    }

    public final void addViewIfPending() {
        Runnable runnable = this.addViewRunnable;
        if (runnable != null) {
            Job job = this.listenForCurrentKeyguardState;
            if (job != null) {
                Job.DefaultImpls.cancel$default(job, null, 1, null);
            }
            runnable.run();
        }
        this.addViewRunnable = null;
    }

    public final void updateOverlayParams(UdfpsOverlayParams udfpsOverlayParams) {
        this.overlayParams = udfpsOverlayParams;
        this.sensorBounds = udfpsOverlayParams.getSensorBounds();
        View touchOverlay = getTouchOverlay();
        if (touchOverlay == null || this.addViewRunnable != null) {
            return;
        }
        this.windowManager.updateViewLayout(touchOverlay, updateDimensions(this.coreLayoutParams, null));
    }

    public final UdfpsAnimationViewController inflateUdfpsAnimation(UdfpsView udfpsView) {
        int i = this.requestReason;
        switch (((i == 1 || i == 2) && shouldRemoveEnrollmentUi()) ? 5 : this.requestReason) {
            case 1:
            case 2:
                View viewInflate = this.inflater.inflate(R$layout.udfps_fpm_empty_view, (ViewGroup) null);
                if (viewInflate == null) {
                    throw new NullPointerException("null cannot be cast to non-null type com.android.systemui.biometrics.UdfpsFpmEmptyView");
                }
                UdfpsFpmEmptyView udfpsFpmEmptyView = (UdfpsFpmEmptyView) viewInflate;
                udfpsView.addView(udfpsFpmEmptyView);
                udfpsFpmEmptyView.updateAccessibilityViewLocation(this.sensorBounds);
                udfpsFpmEmptyView.updateVisibility(false);
                return new UdfpsFpmEmptyViewController(udfpsFpmEmptyView, this.statusBarStateController, this.shadeInteractor, this.dialogManager, this.dumpManager, this.udfpsOverlayInteractor);
            case 3:
            case 5:
            case 6:
                View viewInflate2 = this.inflater.inflate(R$layout.udfps_fpm_empty_view, (ViewGroup) null);
                if (viewInflate2 == null) {
                    throw new NullPointerException("null cannot be cast to non-null type com.android.systemui.biometrics.UdfpsFpmEmptyView");
                }
                UdfpsFpmEmptyView udfpsFpmEmptyView2 = (UdfpsFpmEmptyView) viewInflate2;
                udfpsView.addView(udfpsFpmEmptyView2);
                udfpsFpmEmptyView2.updateAccessibilityViewLocation(this.sensorBounds);
                udfpsFpmEmptyView2.updateVisibility(true);
                return new UdfpsFpmEmptyViewController(udfpsFpmEmptyView2, this.statusBarStateController, this.shadeInteractor, this.dialogManager, this.dumpManager, this.udfpsOverlayInteractor);
            case 4:
                View viewInflate3 = this.inflater.inflate(R$layout.udfps_keyguard_view_legacy, (ViewGroup) null);
                if (viewInflate3 == null) {
                    throw new NullPointerException("null cannot be cast to non-null type com.android.systemui.biometrics.UdfpsKeyguardViewLegacy");
                }
                UdfpsKeyguardViewLegacy udfpsKeyguardViewLegacy = (UdfpsKeyguardViewLegacy) viewInflate3;
                udfpsView.addView(udfpsKeyguardViewLegacy);
                udfpsKeyguardViewLegacy.updateSensorLocation(this.sensorBounds);
                UdfpsController udfpsController = this.controller;
                udfpsKeyguardViewLegacy.updateColors(udfpsController != null ? udfpsController.getColor() : -1);
                return new UdfpsKeyguardViewControllerLegacy(udfpsKeyguardViewLegacy, this.statusBarStateController, this.statusBarKeyguardViewManager, this.keyguardUpdateMonitor, this.dumpManager, this.transitionController, this.configurationController, this.keyguardStateController, this.unlockedScreenOffAnimationController, this.dialogManager, this.controller, this.activityTransitionAnimator, this.primaryBouncerInteractor, this.alternateBouncerInteractor, this.udfpsKeyguardAccessibilityDelegate, this.selectedUserInteractor, this.transitionInteractor, this.shadeInteractor, this.udfpsOverlayInteractor);
            default:
                Log.e("UdfpsControllerOverlay", "Animation for reason " + this.requestReason + " not supported yet");
                return null;
        }
    }

    public final boolean hide() {
        boolean zIsShowing = isShowing();
        UdfpsView udfpsView = this.overlayViewLegacy;
        if (udfpsView != null) {
            udfpsView.clearAnim();
        }
        UdfpsView udfpsView2 = this.overlayViewLegacy;
        if (udfpsView2 != null) {
            if (udfpsView2.isDisplayConfigured()) {
                udfpsView2.unconfigureDisplay();
            }
            udfpsView2.setAnimationViewController(null);
        }
        this.udfpsDisplayModeProvider.disable(null);
        View touchOverlay = getTouchOverlay();
        if (touchOverlay != null) {
            if (touchOverlay.getParent() != null) {
                this.windowManager.removeView(touchOverlay);
                Log.d("UdfpsControllerOverlay", "remove udfp window");
            }
            Trace.setCounter("UdfpsAddView", 0L);
            touchOverlay.setOnTouchListener(null);
            touchOverlay.setOnHoverListener(null);
            AccessibilityManager.TouchExplorationStateChangeListener touchExplorationStateChangeListener = this.overlayTouchListener;
            if (touchExplorationStateChangeListener != null) {
                this.accessibilityManager.removeTouchExplorationStateChangeListener(touchExplorationStateChangeListener);
            }
        }
        this.overlayTouchView = null;
        this.overlayViewLegacy = null;
        this.overlayTouchListener = null;
        Job job = this.listenForCurrentKeyguardState;
        if (job != null) {
            Job.DefaultImpls.cancel$default(job, null, 1, null);
        }
        return zIsShowing;
    }

    public final void cancel() {
        try {
            this.controllerCallback.onUserCanceled();
        } catch (RemoteException e) {
            Log.e("UdfpsControllerOverlay", "Remote exception", e);
        }
    }

    public final boolean matchesRequestId(long j) {
        long j2 = this.requestId;
        return j2 == -1 || j2 == j;
    }

    public final WindowManager.LayoutParams updateDimensions(WindowManager.LayoutParams layoutParams, UdfpsAnimationViewController udfpsAnimationViewController) {
        int paddingX = udfpsAnimationViewController != null ? udfpsAnimationViewController.getPaddingX() : 0;
        int paddingY = udfpsAnimationViewController != null ? udfpsAnimationViewController.getPaddingY() : 0;
        if (!DeviceStateManagerCompat.isCtsRunningStatic() && this.requestReason == 4) {
            layoutParams.flags |= 16;
        }
        Rect rect = new Rect(this.overlayParams.getSensorBounds());
        int rotation = this.overlayParams.getRotation();
        if (rotation == 1 || rotation == 3) {
            if (!shouldRotate(udfpsAnimationViewController)) {
                Log.v("UdfpsControllerOverlay", "Skip rotating UDFPS bounds " + Surface.rotationToString(rotation) + " animation=" + udfpsAnimationViewController + " isGoingToSleep=" + this.keyguardUpdateMonitor.isGoingToSleep() + " isOccluded=" + this.keyguardStateController.isOccluded());
            } else {
                Log.v("UdfpsControllerOverlay", "Rotate UDFPS bounds " + Surface.rotationToString(rotation));
                RotationUtils.rotateBounds(rect, this.overlayParams.getNaturalDisplayWidth(), this.overlayParams.getNaturalDisplayHeight(), rotation);
                RotationUtils.rotateBounds(this.sensorBounds, this.overlayParams.getNaturalDisplayWidth(), this.overlayParams.getNaturalDisplayHeight(), rotation);
            }
        }
        int i = this.requestReason;
        if (1 == i || 2 == i || 5 == i || 6 == i || 3 == i) {
            layoutParams.x = rect.left - paddingX;
            layoutParams.y = rect.top - paddingY;
            layoutParams.height = rect.height() + (paddingX * 2);
            int iWidth = rect.width() + (paddingY * 2);
            layoutParams.width = iWidth;
            Log.i("UdfpsControllerOverlay", "updateDimensions requestReason=" + this.requestReason + ", bounds x=" + layoutParams.x + ", y=" + layoutParams.y + ", width=" + iWidth + ", height=" + layoutParams.height + ", overlayParams.sensorBounds=" + this.overlayParams.getSensorBounds() + ", sensorBoundsWidth=" + this.overlayParams.getSensorBounds().width() + ", paddingX=" + paddingX);
            return layoutParams;
        }
        Log.i("UdfpsControllerOverlay", "updateDimensions Old UDFPS bounds x=" + layoutParams.x + ", y=" + layoutParams.y + ", width=" + layoutParams.width + ", height=" + layoutParams.height + ", overlayParams.sensorBounds=" + this.overlayParams.getSensorBounds() + ", sensorBoundsWidth=" + this.overlayParams.getSensorBounds().width() + ", animWidth=" + this.animWidth);
        int iWidth2 = (this.animWidth - this.overlayParams.getSensorBounds().width()) / 2;
        if (rect.bottom + iWidth2 > DisplayUtils.getHeight(this.context)) {
            iWidth2 = DisplayUtils.getHeight(this.context) - rect.bottom;
        }
        layoutParams.x = rect.left - iWidth2;
        layoutParams.y = rect.top - iWidth2;
        int i2 = iWidth2 * 2;
        layoutParams.height = rect.height() + i2;
        int iWidth3 = rect.width() + i2;
        layoutParams.width = iWidth3;
        Log.i("UdfpsControllerOverlay", "updateDimensions UDFPS bounds x=" + layoutParams.x + ", y=" + layoutParams.y + ", width=" + iWidth3 + ", height=" + layoutParams.height + ", overlayParams.sensorBounds=" + this.overlayParams.getSensorBounds());
        return layoutParams;
    }

    public final boolean shouldRotate(UdfpsAnimationViewController udfpsAnimationViewController) {
        if (udfpsAnimationViewController instanceof UdfpsKeyguardViewControllerLegacy) {
            return !this.keyguardUpdateMonitor.isGoingToSleep() && this.keyguardStateController.isOccluded();
        }
        return true;
    }

    public final void setShadeExpansionStateManager(ShadeExpansionStateManager shadeExpansionStateManager) {
        this.shadeExpansionStateManager = shadeExpansionStateManager;
    }

    public final void setController(UdfpsController udfpsController) {
        this.controller = udfpsController;
    }

    public final void updateUdfpsIconColor(int i) {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.updateColor(i);
    }

    public final void updateUdfpsIconState(boolean z) {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.updateFpIconState(z);
    }

    public final void startLeftRightIconScaleAnimator() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.startLeftRightIconScaleAnimator();
    }

    public final void startLeftRightIconScaleHideAnimator(long j) {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.startLeftRightIconScaleHideAnimator(j);
    }

    public final void updateLeftRightIconAlpha(float f, float f2) {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.updateLeftRightIconAlpha(f, f2);
    }

    public final void showFingerprintDownAnim() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.showFingerprintDownAnim();
    }

    public final void showFingerprintDownAnimLeft() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.showFingerprintDownAnimLeft();
    }

    public final void showFingerprintDownAnimRight() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.showFingerprintDownAnimRight();
    }

    public final void hideFingerprintDownAnim() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.hideFingerprintDownAnim();
    }

    public final void hideFingerprintDownAnimLeft() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.hideFingerprintDownAnimLeft();
    }

    public final void hideFingerprintDownAnimRight() {
        UdfpsAnimationViewController animationViewController;
        if (!isShowing() || (animationViewController = getAnimationViewController()) == null) {
            return;
        }
        animationViewController.hideFingerprintDownAnimRight();
    }
}
