package com.android.systemui.biometrics;

import android.animation.ValueAnimator;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import com.android.app.animation.Interpolators;
import com.android.systemui.Dumpable;
import com.android.systemui.biometrics.domain.interactor.UdfpsOverlayInteractor;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.shade.ShadeExpansionChangeEvent;
import com.android.systemui.shade.ShadeExpansionListener;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.shade.domain.interactor.ShadeInteractor;
import com.android.systemui.statusbar.phone.SystemUIDialogManager;
import com.android.systemui.util.ViewController;
import com.flyme.systemui.SystemUIDebugConfig;
import java.io.PrintWriter;

/* JADX INFO: compiled from: UdfpsAnimationViewController.kt */
/* JADX INFO: loaded from: classes2.dex */
public abstract class UdfpsAnimationViewController extends ViewController implements Dumpable {
    public ValueAnimator dialogAlphaAnimator;
    public final SystemUIDialogManager.Listener dialogListener;
    public final SystemUIDialogManager dialogManager;
    public final DumpManager dumpManager;
    public final String dumpTag;
    public boolean expanded;
    public float fraction;
    public boolean notificationShadeVisible;
    public final int paddingX;
    public final int paddingY;
    public final ShadeExpansionListener shadeExpansionListener;
    public ShadeExpansionStateManager shadeExpansionStateManager;
    public final ShadeInteractor shadeInteractor;
    public final StatusBarStateController statusBarStateController;
    public final PointF touchTranslation;
    public final UdfpsOverlayInteractor udfpsOverlayInteractor;

    public abstract String getTag();

    public final StatusBarStateController getStatusBarStateController() {
        return this.statusBarStateController;
    }

    public UdfpsAnimationViewController(final UdfpsAnimationView udfpsAnimationView, StatusBarStateController statusBarStateController, ShadeInteractor shadeInteractor, SystemUIDialogManager systemUIDialogManager, DumpManager dumpManager, UdfpsOverlayInteractor udfpsOverlayInteractor) {
        super(udfpsAnimationView);
        this.statusBarStateController = statusBarStateController;
        this.shadeInteractor = shadeInteractor;
        this.dialogManager = systemUIDialogManager;
        this.dumpManager = dumpManager;
        this.udfpsOverlayInteractor = udfpsOverlayInteractor;
        this.dialogListener = new SystemUIDialogManager.Listener() { // from class: com.android.systemui.biometrics.UdfpsAnimationViewController$dialogListener$1
            @Override // com.android.systemui.statusbar.phone.SystemUIDialogManager.Listener
            public final void shouldHideAffordances(boolean z) {
                this.this$0.runDialogAlphaAnimator();
            }
        };
        this.touchTranslation = new PointF(0.0f, 0.0f);
        this.shadeExpansionListener = new ShadeExpansionListener() { // from class: com.android.systemui.biometrics.UdfpsAnimationViewController$shadeExpansionListener$1
            @Override // com.android.systemui.shade.ShadeExpansionListener
            public final void onPanelExpansionChanged(ShadeExpansionChangeEvent shadeExpansionChangeEvent) {
                if (SystemUIDebugConfig.DEBUG_FINGPRINT) {
                    Log.d("UdfpsAnimation", "fraction=" + shadeExpansionChangeEvent.getFraction() + ", expanded=" + shadeExpansionChangeEvent.getExpanded());
                }
                this.this$0.expanded = shadeExpansionChangeEvent.getExpanded();
                this.this$0.fraction = shadeExpansionChangeEvent.getFraction();
                this.this$0.setNotificationShadeVisible(shadeExpansionChangeEvent.getExpanded() && shadeExpansionChangeEvent.getFraction() > 0.0f);
                udfpsAnimationView.onExpansionChanged(shadeExpansionChangeEvent.getFraction());
                this.this$0.updatePauseAuth();
            }
        };
        this.dumpTag = getTag() + " (" + this + ")";
    }

    public final UdfpsAnimationView getView() {
        View view = this.mView;
        view.getClass();
        return (UdfpsAnimationView) view;
    }

    public final boolean getNotificationShadeVisible() {
        return this.notificationShadeVisible;
    }

    public final void setNotificationShadeVisible(boolean z) {
        this.notificationShadeVisible = z;
    }

    public int getPaddingX() {
        return this.paddingX;
    }

    public int getPaddingY() {
        return this.paddingY;
    }

    public void updateAlpha() {
        getView().updateAlpha();
    }

    public final void runDialogAlphaAnimator() {
        boolean zShouldHideAffordance = this.dialogManager.shouldHideAffordance();
        ValueAnimator valueAnimator = this.dialogAlphaAnimator;
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        ValueAnimator valueAnimatorOfFloat = ValueAnimator.ofFloat(getView().calculateAlpha() / 255.0f, zShouldHideAffordance ? 0.0f : 1.0f);
        valueAnimatorOfFloat.setDuration(zShouldHideAffordance ? 83L : 200L);
        valueAnimatorOfFloat.setInterpolator(zShouldHideAffordance ? Interpolators.LINEAR : Interpolators.ALPHA_IN);
        valueAnimatorOfFloat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { // from class: com.android.systemui.biometrics.UdfpsAnimationViewController$runDialogAlphaAnimator$1$1
            @Override // android.animation.ValueAnimator.AnimatorUpdateListener
            public final void onAnimationUpdate(ValueAnimator valueAnimator2) {
                this.this$0.getView().setDialogSuggestedAlpha(((Float) valueAnimator2.getAnimatedValue()).floatValue());
                this.this$0.updateAlpha();
                this.this$0.updatePauseAuth();
            }
        });
        valueAnimatorOfFloat.start();
        this.dialogAlphaAnimator = valueAnimatorOfFloat;
    }

    @Override // com.android.systemui.util.ViewController
    public void onViewAttached() {
        ShadeExpansionStateManager shadeExpansionStateManager = this.shadeExpansionStateManager;
        if (shadeExpansionStateManager != null) {
            this.shadeExpansionListener.onPanelExpansionChanged(shadeExpansionStateManager.addExpansionListener(this.shadeExpansionListener));
        }
        this.dialogManager.registerListener(this.dialogListener);
        this.dumpManager.registerNormalDumpable(this.dumpTag, this);
        this.udfpsOverlayInteractor.setHandleTouches(!shouldPauseAuth());
        if (this.dialogManager.shouldHideAffordance()) {
            runDialogAlphaAnimator();
        }
    }

    @Override // com.android.systemui.util.ViewController
    public void onViewDetached() {
        ShadeExpansionStateManager shadeExpansionStateManager = this.shadeExpansionStateManager;
        if (shadeExpansionStateManager != null) {
            shadeExpansionStateManager.removeExpansionListener(this.shadeExpansionListener);
        }
        this.dialogManager.unregisterListener(this.dialogListener);
        this.dumpManager.unregisterDumpable(this.dumpTag);
        this.udfpsOverlayInteractor.setHandleTouches(!shouldPauseAuth());
    }

    @Override // com.android.systemui.Dumpable
    public void dump(PrintWriter printWriter, String[] strArr) {
        printWriter.println("mNotificationShadeVisible=" + this.notificationShadeVisible);
        printWriter.println("shouldPauseAuth()=" + shouldPauseAuth());
        printWriter.println("isPauseAuth=" + getView().isPauseAuth());
        printWriter.println("dialogSuggestedAlpha=" + getView().getDialogSuggestedAlpha());
        printWriter.println("alpha=" + getView().getCalculateAlpha());
        printWriter.println("fraction=" + this.fraction);
        printWriter.println("expanded=" + this.expanded);
    }

    public boolean shouldPauseAuth() {
        Log.d("UdfpsAnimation", "shouldPauseAuth, shouldHideAffordance=" + this.dialogManager.shouldHideAffordance() + ", notificationShadeVisible=" + this.notificationShadeVisible);
        if (this.notificationShadeVisible) {
            Log.d("UdfpsAnimation", "qsExpansion=" + this.shadeInteractor.getQsExpansion().getValue() + ", shadeExpansion=" + this.shadeInteractor.getShadeExpansion().getValue() + ", legacyShadeExpansion=" + this.shadeInteractor.getLegacyShadeExpansion().getValue() + ", state=" + this.statusBarStateController.getState() + ", isQsExpanded=" + this.shadeInteractor.isQsExpanded().getValue() + ", fraction=" + this.fraction + ", expanded=" + this.expanded);
        }
        return this.notificationShadeVisible || this.dialogManager.shouldHideAffordance();
    }

    public final void updatePauseAuth() {
        if (getView().setPauseAuth(shouldPauseAuth())) {
            getView().postInvalidate();
            this.udfpsOverlayInteractor.setHandleTouches(!shouldPauseAuth());
        }
    }

    public final void onSensorRectUpdated(RectF rectF) {
        getView().onSensorRectUpdated(rectF);
    }

    public final void dozeTimeTick() {
        if (getView().dozeTimeTick()) {
            getView().postInvalidate();
        }
    }

    public final void onDisplayUnconfigured() {
        getView().onDisplayUnconfigured();
        getView().postInvalidate();
    }

    public void setShadeExpansionStateManager(ShadeExpansionStateManager shadeExpansionStateManager) {
        this.shadeExpansionStateManager = shadeExpansionStateManager;
    }

    public final void clearAnim() {
        getView().clearAnim();
    }

    public final void updateColor(int i) {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).updateColors(i);
        }
    }

    public final void updateFpIconState(boolean z) {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).updateFpIconState(z);
        }
    }

    public final void startLeftRightIconScaleAnimator() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).startLeftRightIconScaleAnimator();
        }
    }

    public final void startLeftRightIconScaleHideAnimator(long j) {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).startLeftRightIconScaleHideAnimator(j);
        }
    }

    public final void updateLeftRightIconAlpha(float f, float f2) {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).updateLeftRightIconAlpha(f, f2);
        }
    }

    public final void showFingerprintDownAnim() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).showFingerprintDownAnim();
        }
    }

    public final void showFingerprintDownAnimLeft() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).showFingerprintDownAnimLeft();
        }
    }

    public final void showFingerprintDownAnimRight() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).showFingerprintDownAnimRight();
        }
    }

    public final void hideFingerprintDownAnim() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).hideFpsAnimtionView();
        }
    }

    public final void hideFingerprintDownAnimLeft() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).hideFpsAnimtionViewLeft();
        }
    }

    public final void hideFingerprintDownAnimRight() {
        if (getView() instanceof UdfpsKeyguardViewLegacy) {
            ((UdfpsKeyguardViewLegacy) getView()).hideFpsAnimtionViewRight();
        }
    }
}
