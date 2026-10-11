package com.android.systemui.biometrics;

import android.R;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.ColorFilter;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemProperties;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.Property;
import android.view.DisplayInfo;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.asynclayoutinflater.view.AsyncLayoutInflater;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieFrameInfo;
import com.airbnb.lottie.value.SimpleLottieValueCallback;
import com.android.app.animation.Interpolators;
import com.android.systemui.Dependency;
import com.android.systemui.assist.ui.DisplayUtils;
import com.android.systemui.doze.util.BurnInHelperKt;
import com.android.systemui.res.R$dimen;
import com.android.systemui.res.R$drawable;
import com.android.systemui.res.R$id;
import com.android.systemui.res.R$layout;
import com.flyme.systemui.biometrics.FlymeFpAuthAnimView;
import com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils;
import com.flyme.systemui.statusbar.CentralSurfaceUtil;
import com.qualcomm.qti.biometrics.fingerprint.service.QtiFingerprintService;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/* JADX INFO: loaded from: classes2.dex */
public class UdfpsKeyguardViewLegacy extends UdfpsAnimationView {
    public List list;
    public int mAlpha;
    public int mAnimationType;
    public LottieAnimationView mAodFp;
    public AnimatorSet mBackgroundInAnimator;
    public ImageView mBgProtection;
    public int mColor;
    public UdfpsDrawable mFingerprintDrawable;
    public FrameLayout mFlLeft;
    public FrameLayout mFlRight;
    public FlymeFpAuthAnimView mFlymeFpAuthAnimView;
    public SpringAnimation mFlymeFpAuthAnimViewAlpha;
    public ImageView mFlymeFpAuthAnimViewLeft;
    public SpringAnimation mFlymeFpAuthAnimViewLeftX;
    public SpringAnimation mFlymeFpAuthAnimViewLeftY;
    public ImageView mFlymeFpAuthAnimViewRight;
    public SpringAnimation mFlymeFpAuthAnimViewRightAlpha;
    public SpringAnimation mFlymeFpAuthAnimViewRightX;
    public SpringAnimation mFlymeFpAuthAnimViewRightY;
    public ImageView mFlymeIcon;
    public ImageView mFlymeIconBg;
    public ImageView mFlymeIconLeft;
    public ImageView mFlymeIconLeftBg;
    public ImageView mFlymeIconLeftDot;
    public ImageView mFlymeIconRight;
    public ImageView mFlymeIconRightBg;
    public ImageView mFlymeIconRightDot;
    public boolean mFullyInflated;
    public Handler mHandler;
    public Runnable mHideAnimation;
    public AsyncTask mIconDrawableAsycTackLeft;
    public AsyncTask mIconDrawableAsycTackRight;
    public int mIconSize;
    public float mInterpolatedDarkAmount;
    public final AsyncLayoutInflater.OnInflateFinishedListener mLayoutInflaterFinishListener;
    public float mLeftRightIconAlpha;
    public float mLeftRightIconDotAlpha;
    public LottieAnimationView mLockScreenFp;
    public final int mMaxBurnInOffsetX;
    public final int mMaxBurnInOffsetY;
    public int mMaxSensorHeight;
    public int mMaxSensorWidth;
    public Runnable mOnFinishInflateRunnable;
    public float mScaleFactor;
    public Rect mSensorBounds;
    public int mTextColorPrimary;
    public boolean mUdfpsRequested;

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public void onDisplayUnconfigured() {
    }

    public UdfpsKeyguardViewLegacy(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.mBackgroundInAnimator = new AnimatorSet();
        this.mScaleFactor = 1.0f;
        this.mSensorBounds = new Rect();
        this.mAnimationType = 0;
        this.mLayoutInflaterFinishListener = new AnonymousClass2();
        this.mIconSize = 0;
        this.mHandler = new Handler();
        this.mFlymeFpAuthAnimViewLeftX = null;
        this.mFlymeFpAuthAnimViewLeftY = null;
        this.mFlymeFpAuthAnimViewAlpha = null;
        this.mFlymeFpAuthAnimViewRightX = null;
        this.mFlymeFpAuthAnimViewRightY = null;
        this.mFlymeFpAuthAnimViewRightAlpha = null;
        this.mIconDrawableAsycTackLeft = null;
        this.mIconDrawableAsycTackRight = null;
        this.list = new ArrayList();
        this.mLeftRightIconAlpha = 0.0f;
        this.mLeftRightIconDotAlpha = 1.0f;
        this.mMaxSensorWidth = 0;
        this.mMaxSensorHeight = 0;
        this.mHideAnimation = new Runnable() { // from class: com.android.systemui.biometrics.UdfpsKeyguardViewLegacy$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.lambda$new$2();
            }
        };
        this.mFingerprintDrawable = new UdfpsFpDrawable(context);
        this.mMaxBurnInOffsetX = context.getResources().getDimensionPixelSize(R$dimen.udfps_burn_in_offset_x);
        this.mMaxBurnInOffsetY = context.getResources().getDimensionPixelSize(R$dimen.udfps_burn_in_offset_y);
    }

    public void startIconAsyncInflate(Runnable runnable) {
        this.mOnFinishInflateRunnable = runnable;
        new AsyncLayoutInflater(((FrameLayout) this).mContext).inflate(R$layout.udfps_keyguard_view_internal, this, this.mLayoutInflaterFinishListener);
    }

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public UdfpsDrawable getDrawable() {
        return this.mFingerprintDrawable;
    }

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public void onSensorRectUpdated(RectF rectF) {
        super.onSensorRectUpdated(rectF);
        rectF.round(this.mSensorBounds);
        postInvalidate();
    }

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public boolean dozeTimeTick() {
        updateBurnInOffsets();
        return true;
    }

    /* JADX WARN: Code duplicated, block: B:25:0x00bb  */
    public final void updateBurnInOffsets() {
        boolean z;
        if (this.mFullyInflated) {
            float f = this.mAnimationType == 2 ? 1.0f : this.mInterpolatedDarkAmount;
            float fLerp = MathUtils.lerp(0.0f, BurnInHelperKt.getBurnInOffset(this.mMaxBurnInOffsetX * 2, true) - this.mMaxBurnInOffsetX, f);
            float fLerp2 = MathUtils.lerp(0.0f, BurnInHelperKt.getBurnInOffset(this.mMaxBurnInOffsetY * 2, false) - this.mMaxBurnInOffsetY, f);
            float fLerp3 = MathUtils.lerp(0.0f, BurnInHelperKt.getBurnInProgressOffset(), f);
            if (this.mAnimationType == 1 && !this.mPauseAuth) {
                this.mLockScreenFp.setTranslationX(fLerp);
                this.mLockScreenFp.setTranslationY(fLerp2);
                this.mBgProtection.setAlpha(1.0f - this.mInterpolatedDarkAmount);
                this.mLockScreenFp.setAlpha(1.0f - this.mInterpolatedDarkAmount);
            } else if (f == 0.0f) {
                this.mLockScreenFp.setTranslationX(0.0f);
                this.mLockScreenFp.setTranslationY(0.0f);
                this.mBgProtection.setAlpha(this.mAlpha / 255.0f);
                this.mLockScreenFp.setAlpha(this.mAlpha / 255.0f);
            } else {
                this.mBgProtection.setAlpha(0.0f);
                this.mLockScreenFp.setAlpha(0.0f);
            }
            this.mLockScreenFp.setProgress(1.0f - this.mInterpolatedDarkAmount);
            this.mAodFp.setTranslationX(fLerp);
            this.mAodFp.setTranslationY(fLerp2);
            this.mAodFp.setProgress(fLerp3);
            this.mAodFp.setAlpha(this.mInterpolatedDarkAmount);
            int i = this.mAnimationType;
            if (i == 1) {
                float f2 = this.mInterpolatedDarkAmount;
                if (f2 == 0.0f || f2 == 1.0f) {
                    z = true;
                } else {
                    z = false;
                }
            } else {
                z = false;
            }
            boolean z2 = i == 2 && this.mInterpolatedDarkAmount == 1.0f;
            if (z || z2) {
                this.mAnimationType = 0;
            }
        }
    }

    public void requestUdfps(boolean z, int i) {
        this.mUdfpsRequested = z;
    }

    public void updateColor() {
        if (this.mFullyInflated) {
            this.mTextColorPrimary = com.android.settingslib.Utils.getColorAttrDefaultColor(((FrameLayout) this).mContext, R.color.materialColorOnSurface);
            this.mBgProtection.setImageTintList(ColorStateList.valueOf(com.android.settingslib.Utils.getColorAttrDefaultColor(getContext(), R.color.materialColorSurfaceContainerHigh)));
            this.mLockScreenFp.invalidate();
        }
    }

    public void setScaleFactor(float f) {
        this.mScaleFactor = f;
    }

    public void updatePadding() {
        if (this.mLockScreenFp == null || this.mAodFp == null) {
            return;
        }
        int dimensionPixelSize = (int) (getResources().getDimensionPixelSize(R$dimen.lock_icon_padding) * this.mScaleFactor);
        this.mLockScreenFp.setPadding(dimensionPixelSize, dimensionPixelSize, dimensionPixelSize, dimensionPixelSize);
        this.mAodFp.setPadding(dimensionPixelSize, dimensionPixelSize, dimensionPixelSize, dimensionPixelSize);
    }

    public void setUnpausedAlpha(int i) {
        this.mAlpha = i;
        updateAlpha();
    }

    public int getUnpausedAlpha() {
        return this.mAlpha;
    }

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public int updateAlpha() {
        int iUpdateAlpha = super.updateAlpha();
        updateBurnInOffsets();
        return iUpdateAlpha;
    }

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public int calculateAlpha() {
        if (this.mPauseAuth) {
            return 0;
        }
        return this.mAlpha;
    }

    public void onDozeAmountChanged(float f, float f2, int i) {
        this.mAnimationType = i;
        this.mInterpolatedDarkAmount = f2;
        updateAlpha();
    }

    public void updateSensorLocation(Rect rect) {
        this.mSensorBounds.set(rect);
        Log.e("UdfpsKeyguardView", "updateSensorLocation() sensorBounds=" + rect);
        updateFpIconState(false);
    }

    public void animateInUdfpsBouncer(final Runnable runnable) {
        if (this.mBackgroundInAnimator.isRunning() || !this.mFullyInflated) {
            return;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        this.mBackgroundInAnimator = animatorSet;
        animatorSet.playTogether(ObjectAnimator.ofFloat(this.mBgProtection, (Property<ImageView, Float>) View.ALPHA, 0.0f, 1.0f), ObjectAnimator.ofFloat(this.mBgProtection, (Property<ImageView, Float>) View.SCALE_X, 0.0f, 1.0f), ObjectAnimator.ofFloat(this.mBgProtection, (Property<ImageView, Float>) View.SCALE_Y, 0.0f, 1.0f));
        this.mBackgroundInAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        this.mBackgroundInAnimator.setDuration(500L);
        this.mBackgroundInAnimator.addListener(new AnimatorListenerAdapter(this) { // from class: com.android.systemui.biometrics.UdfpsKeyguardViewLegacy.1
            @Override // android.animation.AnimatorListenerAdapter, android.animation.Animator.AnimatorListener
            public void onAnimationEnd(Animator animator) {
                Runnable runnable2 = runnable;
                if (runnable2 != null) {
                    runnable2.run();
                }
            }
        });
        this.mBackgroundInAnimator.start();
    }

    public void dump(PrintWriter printWriter) {
        printWriter.println("UdfpsKeyguardView (" + this + ")");
        StringBuilder sb = new StringBuilder();
        sb.append("    mPauseAuth=");
        sb.append(this.mPauseAuth);
        printWriter.println(sb.toString());
        printWriter.println("    mUnpausedAlpha=" + getUnpausedAlpha());
        printWriter.println("    mUdfpsRequested=" + this.mUdfpsRequested);
        printWriter.println("    mInterpolatedDarkAmount=" + this.mInterpolatedDarkAmount);
        printWriter.println("    mAnimationType=" + this.mAnimationType);
    }

    /* JADX INFO: renamed from: com.android.systemui.biometrics.UdfpsKeyguardViewLegacy$2, reason: invalid class name */
    public class AnonymousClass2 implements AsyncLayoutInflater.OnInflateFinishedListener {
        public AnonymousClass2() {
        }

        @Override // androidx.asynclayoutinflater.view.AsyncLayoutInflater.OnInflateFinishedListener
        public void onInflateFinished(View view, int i, ViewGroup viewGroup) {
            UdfpsKeyguardViewLegacy.this.mFullyInflated = true;
            UdfpsKeyguardViewLegacy.this.mAodFp = (LottieAnimationView) view.findViewById(R$id.udfps_aod_fp);
            UdfpsKeyguardViewLegacy.this.mLockScreenFp = (LottieAnimationView) view.findViewById(R$id.udfps_lockscreen_fp);
            UdfpsKeyguardViewLegacy.this.mBgProtection = (ImageView) view.findViewById(R$id.udfps_keyguard_fp_bg);
            UdfpsKeyguardViewLegacy.this.mFlymeIcon = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon);
            UdfpsKeyguardViewLegacy.this.mFlymeIconBg = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_bg);
            UdfpsKeyguardViewLegacy.this.mFlLeft = (FrameLayout) view.findViewById(R$id.fl_left);
            UdfpsKeyguardViewLegacy.this.mFlymeIconLeft = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_left);
            UdfpsKeyguardViewLegacy.this.mFlymeIconLeftBg = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_left_bg);
            UdfpsKeyguardViewLegacy.this.mFlymeIconLeftDot = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_left_dot);
            UdfpsKeyguardViewLegacy.this.mFlRight = (FrameLayout) view.findViewById(R$id.fl_right);
            UdfpsKeyguardViewLegacy.this.mFlymeIconRight = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_right);
            UdfpsKeyguardViewLegacy.this.mFlymeIconRightBg = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_right_bg);
            UdfpsKeyguardViewLegacy.this.mFlymeIconRightDot = (ImageView) view.findViewById(R$id.flyme_udfps_lockscreen_fp_icon_right_dot);
            UdfpsKeyguardViewLegacy.this.mFlymeFpAuthAnimView = (FlymeFpAuthAnimView) view.findViewById(R$id.flyme_udfps_auth_anim);
            UdfpsKeyguardViewLegacy.this.mFlymeFpAuthAnimViewLeft = (ImageView) view.findViewById(R$id.flyme_udfps_auth_anim_left);
            UdfpsKeyguardViewLegacy.this.mFlymeFpAuthAnimViewRight = (ImageView) view.findViewById(R$id.flyme_udfps_auth_anim_right);
            UdfpsKeyguardViewLegacy.this.updatePadding();
            UdfpsKeyguardViewLegacy.this.updateColor();
            UdfpsKeyguardViewLegacy.this.updateAlpha();
            viewGroup.addView(view);
            UdfpsKeyguardViewLegacy.this.mLockScreenFp.addValueCallback(new KeyPath("**"), LottieProperty.COLOR_FILTER, new SimpleLottieValueCallback() { // from class: com.android.systemui.biometrics.UdfpsKeyguardViewLegacy$2$$ExternalSyntheticLambda0
                @Override // com.airbnb.lottie.value.SimpleLottieValueCallback
                public final Object getValue(LottieFrameInfo lottieFrameInfo) {
                    return this.f$0.lambda$onInflateFinished$0(lottieFrameInfo);
                }
            });
            UdfpsKeyguardViewLegacy.this.mOnFinishInflateRunnable.run();
            UdfpsKeyguardViewLegacy.this.mLockScreenFp.setVisibility(8);
            UdfpsKeyguardViewLegacy.this.mBgProtection.setVisibility(8);
            UdfpsKeyguardViewLegacy.this.mAodFp.setVisibility(8);
            UdfpsKeyguardViewLegacy udfpsKeyguardViewLegacy = UdfpsKeyguardViewLegacy.this;
            udfpsKeyguardViewLegacy.updateColors(udfpsKeyguardViewLegacy.mColor);
            UdfpsKeyguardViewLegacy.this.updateFpIconState(false);
        }

        public final /* synthetic */ ColorFilter lambda$onInflateFinished$0(LottieFrameInfo lottieFrameInfo) {
            return new PorterDuffColorFilter(UdfpsKeyguardViewLegacy.this.mTextColorPrimary, PorterDuff.Mode.SRC_ATOP);
        }
    }

    /* JADX WARN: Code duplicated, block: B:20:0x00c1  */
    /* JADX WARN: Code duplicated, block: B:23:0x00fb  */
    /* JADX WARN: Code duplicated, block: B:24:0x0103  */
    public void updateFpIconState(boolean z) {
        float f;
        int iWidth;
        int iWidth2;
        int i;
        int i2;
        Log.d("UdfpsKeyguardView", "updateFpIconSize mFlymeIcon=" + this.mFlymeIcon + ", mFlymeIconLeft=" + this.mFlymeIconLeft + ", mFlymeIconRight=" + this.mFlymeIconRight + ", mSensorBounds.width():" + this.mSensorBounds.width() + " mSensorBounds:" + this.mSensorBounds);
        if (this.mFlymeIcon != null) {
            if (FlymeWideFingerPrintUtils.isSupportedWideFp()) {
                if (FlymeWideFingerPrintUtils.shouldShowMaxFpIcon(getContext())) {
                    updateMaxIconLocation();
                    iWidth = (int) (this.mMaxSensorWidth * 0.98f);
                    i = this.mMaxSensorHeight;
                } else if (FlymeWideFingerPrintUtils.shouldShowLargeFpIcon(getContext())) {
                    f = 0.498f;
                    iWidth = (int) (this.mSensorBounds.width() * 0.498f);
                    iWidth2 = this.mSensorBounds.width();
                } else {
                    f = 0.341f;
                    iWidth = (int) (this.mSensorBounds.width() * 0.341f);
                    iWidth2 = this.mSensorBounds.width();
                }
                if (!z && (i2 = this.mIconSize) != 0 && iWidth != i2) {
                    startCenterIconScaleAnimator(this.mFlymeIcon.getWidth(), iWidth);
                } else {
                    RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.mFlymeIcon.getLayoutParams();
                    layoutParams.width = iWidth;
                    layoutParams.height = i;
                    this.mFlymeIcon.setLayoutParams(layoutParams);
                }
                this.mIconSize = iWidth;
                Log.d("UdfpsKeyguardView", "updateFpIconState iconSize:" + iWidth + " iconSizeHeight:" + i);
                if (FlymeWideFingerPrintUtils.shouldShowMaxFpIcon(getContext())) {
                    this.mFlymeIcon.setImageResource(R$drawable.flyme_fingerprint_max_white_bg);
                } else {
                    this.mFlymeIcon.setImageResource(R$drawable.flyme_fingerprint_bg);
                }
                updateColors(this.mColor);
            } else {
                f = 0.833f;
                iWidth = (int) (this.mSensorBounds.width() * 0.833f);
                iWidth2 = this.mSensorBounds.width();
            }
            i = (int) (iWidth2 * f);
            if (!z) {
                RelativeLayout.LayoutParams layoutParams2 = (RelativeLayout.LayoutParams) this.mFlymeIcon.getLayoutParams();
                layoutParams2.width = iWidth;
                layoutParams2.height = i;
                this.mFlymeIcon.setLayoutParams(layoutParams2);
            } else {
                RelativeLayout.LayoutParams layoutParams3 = (RelativeLayout.LayoutParams) this.mFlymeIcon.getLayoutParams();
                layoutParams3.width = iWidth;
                layoutParams3.height = i;
                this.mFlymeIcon.setLayoutParams(layoutParams3);
            }
            this.mIconSize = iWidth;
            Log.d("UdfpsKeyguardView", "updateFpIconState iconSize:" + iWidth + " iconSizeHeight:" + i);
            if (FlymeWideFingerPrintUtils.shouldShowMaxFpIcon(getContext())) {
                this.mFlymeIcon.setImageResource(R$drawable.flyme_fingerprint_max_white_bg);
            } else {
                this.mFlymeIcon.setImageResource(R$drawable.flyme_fingerprint_bg);
            }
            updateColors(this.mColor);
        }
        if (this.mFlLeft != null && this.mFlRight != null) {
            if (FlymeWideFingerPrintUtils.shouldShowLeftRightIcon(getContext())) {
                this.mFlLeft.setVisibility(0);
                this.mFlRight.setVisibility(0);
                if (!z) {
                    this.mIconDrawableAsycTackLeft = FlymeWideFingerPrintUtils.loadIconDrawable(getContext(), true, true, new FlymeWideFingerPrintUtils.OnDrawableCallbackListener() { // from class: com.android.systemui.biometrics.UdfpsKeyguardViewLegacy$$ExternalSyntheticLambda1
                        @Override // com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils.OnDrawableCallbackListener
                        public final void onDrawableCallback(Drawable drawable) {
                            this.f$0.lambda$updateFpIconState$0(drawable);
                        }
                    });
                    this.mIconDrawableAsycTackRight = FlymeWideFingerPrintUtils.loadIconDrawable(getContext(), false, true, new FlymeWideFingerPrintUtils.OnDrawableCallbackListener() { // from class: com.android.systemui.biometrics.UdfpsKeyguardViewLegacy$$ExternalSyntheticLambda2
                        @Override // com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils.OnDrawableCallbackListener
                        public final void onDrawableCallback(Drawable drawable) {
                            this.f$0.lambda$updateFpIconState$1(drawable);
                        }
                    });
                    updateLeftRightIconAlpha();
                }
            } else {
                this.mFlLeft.setVisibility(8);
                this.mFlRight.setVisibility(8);
            }
        }
        if (this.mFlymeIconBg != null && FlymeWideFingerPrintUtils.isSupportedWideFp()) {
            ViewGroup.LayoutParams layoutParams4 = this.mFlymeIconBg.getLayoutParams();
            if (!FlymeWideFingerPrintUtils.isCommonUnLockMode(((FrameLayout) this).mContext)) {
                Log.d("UdfpsKeyguardView", "updateFpIconState()  unLockMode:Quick");
                if (((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().isBouncerShowing()) {
                    layoutParams4.width = DisplayUtils.convertDpToPx(120.0f, getContext());
                    layoutParams4.height = DisplayUtils.convertDpToPx(100.0f, getContext());
                    QtiFingerprintService.getInstance(((FrameLayout) this).mContext).setToLargeAreas();
                } else {
                    layoutParams4.width = DisplayUtils.convertDpToPx(70.0f, getContext());
                    layoutParams4.height = DisplayUtils.convertDpToPx(70.0f, getContext());
                    QtiFingerprintService.getInstance(((FrameLayout) this).mContext).setToQuickLaunchAreas();
                }
            } else if (FlymeWideFingerPrintUtils.isFingerprintMaxMode(((FrameLayout) this).mContext)) {
                Log.d("UdfpsKeyguardView", "updateFpIconState() unLockMode:Max");
                layoutParams4.width = this.mMaxSensorWidth;
                layoutParams4.height = this.mMaxSensorHeight;
            } else if (FlymeWideFingerPrintUtils.isFingerprintLargeMode(((FrameLayout) this).mContext)) {
                Log.d("UdfpsKeyguardView", "updateFpIconState() unLockMode:Large");
                layoutParams4.width = DisplayUtils.convertDpToPx(120.0f, getContext());
                layoutParams4.height = DisplayUtils.convertDpToPx(100.0f, getContext());
            } else {
                Log.d("UdfpsKeyguardView", "updateFpIconState()  unLockMode:Standard");
                layoutParams4.width = DisplayUtils.convertDpToPx(70.0f, getContext());
                layoutParams4.height = DisplayUtils.convertDpToPx(70.0f, getContext());
            }
            this.mFlymeIconBg.setLayoutParams(layoutParams4);
        }
        FlymeWideFingerPrintUtils.initSensorBounds(this.mFlymeIconBg, this.mFlymeIconLeftBg, this.mFlymeIconRightBg, ((FrameLayout) this).mContext);
    }

    public final /* synthetic */ void lambda$updateFpIconState$0(Drawable drawable) {
        this.mFlymeIconLeft.setImageDrawable(drawable);
    }

    public final /* synthetic */ void lambda$updateFpIconState$1(Drawable drawable) {
        this.mFlymeIconRight.setImageDrawable(drawable);
    }

    public void showFingerprintDownAnim() {
        FlymeFpAuthAnimView flymeFpAuthAnimView = this.mFlymeFpAuthAnimView;
        if (flymeFpAuthAnimView != null) {
            flymeFpAuthAnimView.startDownAnimation();
        }
    }

    public void showFingerprintDownAnimLeft() {
        if (this.mFlymeFpAuthAnimViewLeft != null) {
            startLeftRippleAnimator(1.1f, 0.2f);
        }
    }

    public void showFingerprintDownAnimRight() {
        if (this.mFlymeFpAuthAnimViewRight != null) {
            startRightRippleAnimator(1.1f, 0.2f);
        }
    }

    public void hideFpsAnimtionView() {
        FlymeFpAuthAnimView flymeFpAuthAnimView = this.mFlymeFpAuthAnimView;
        if (flymeFpAuthAnimView != null) {
            flymeFpAuthAnimView.stopDownAnimation();
        }
    }

    public void hideFpsAnimtionViewLeft() {
        if (this.mFlymeFpAuthAnimViewLeft != null) {
            startLeftRippleAnimator(1.0f, 0.0f);
        }
    }

    public void hideFpsAnimtionViewRight() {
        if (this.mFlymeFpAuthAnimViewRight != null) {
            startRightRippleAnimator(1.0f, 0.0f);
        }
    }

    public void updateColors(int i) {
        this.mColor = i;
        PorterDuffColorFilter porterDuffColorFilter = new PorterDuffColorFilter(i, PorterDuff.Mode.SRC_ATOP);
        ImageView imageView = this.mFlymeIcon;
        if (imageView != null) {
            imageView.getDrawable().setColorFilter(porterDuffColorFilter);
        }
        ImageView imageView2 = this.mFlymeIconLeftDot;
        if (imageView2 != null) {
            imageView2.getDrawable().setColorFilter(porterDuffColorFilter);
        }
        ImageView imageView3 = this.mFlymeIconRightDot;
        if (imageView3 != null) {
            imageView3.getDrawable().setColorFilter(porterDuffColorFilter);
        }
        ImageView imageView4 = this.mFlymeFpAuthAnimViewLeft;
        if (imageView4 != null) {
            imageView4.getDrawable().setColorFilter(porterDuffColorFilter);
        }
        ImageView imageView5 = this.mFlymeFpAuthAnimViewRight;
        if (imageView5 != null) {
            imageView5.getDrawable().setColorFilter(porterDuffColorFilter);
        }
    }

    @Override // com.android.systemui.biometrics.UdfpsAnimationView
    public void clearAnim() {
        Log.i("UdfpsKeyguardView", "clearAnim mFlymeFpAuthAnimView=" + this.mFlymeFpAuthAnimView);
        FlymeFpAuthAnimView flymeFpAuthAnimView = this.mFlymeFpAuthAnimView;
        if (flymeFpAuthAnimView != null) {
            flymeFpAuthAnimView.setVideoItem(null);
        }
        this.mFlymeFpAuthAnimView = null;
        for (SpringAnimation springAnimation : this.list) {
            if (springAnimation != null && springAnimation.isRunning()) {
                springAnimation.cancel();
            }
        }
        this.list.clear();
        AsyncTask asyncTask = this.mIconDrawableAsycTackLeft;
        if (asyncTask != null) {
            asyncTask.cancel(true);
            this.mIconDrawableAsycTackLeft = null;
        }
        AsyncTask asyncTask2 = this.mIconDrawableAsycTackRight;
        if (asyncTask2 != null) {
            asyncTask2.cancel(true);
            this.mIconDrawableAsycTackRight = null;
        }
        this.mHandler.removeCallbacks(this.mHideAnimation);
    }

    public void startLeftRightIconScaleAnimator() {
        ImageView imageView;
        Log.d("UdfpsKeyguardView", "startLeftRightIconScaleAnimator() mFlymeIconLeft:" + this.mFlymeIconLeft + " mFlymeIconRight:" + this.mFlymeIconRight);
        if (!FlymeWideFingerPrintUtils.shouldShowLeftRightIcon(getContext()) || this.mFlymeIconLeft == null || this.mFlymeIconRight == null || (imageView = this.mFlymeIconLeftDot) == null || this.mFlymeIconRightDot == null) {
            return;
        }
        FlymeWideFingerPrintUtils.isLeftRightIconToGrowing = true;
        startScaleAlphaAnimator(imageView, 2.4f, 0.0f);
        startScaleAlphaAnimator(this.mFlymeIconLeft, 1.0f, 1.0f);
        startScaleAlphaAnimator(this.mFlymeIconRightDot, 2.4f, 0.0f);
        startScaleAlphaAnimator(this.mFlymeIconRight, 1.0f, 1.0f);
        this.mHandler.removeCallbacks(this.mHideAnimation);
        this.mHandler.postDelayed(this.mHideAnimation, 2350L);
    }

    public void startLeftRightIconScaleHideAnimator(long j) {
        this.mHandler.removeCallbacks(this.mHideAnimation);
        this.mHandler.postDelayed(this.mHideAnimation, j);
    }

    /* JADX INFO: renamed from: startLeftRightIconScaleHideAnimator, reason: merged with bridge method [inline-methods] */
    public void lambda$new$2() {
        FlymeWideFingerPrintUtils.isLeftRightIconToGrowing = false;
        startScaleAlphaAnimator(this.mFlymeIconLeftDot, 1.0f, 1.0f);
        startScaleAlphaAnimator(this.mFlymeIconLeft, 0.416f, 0.0f);
        startScaleAlphaAnimator(this.mFlymeIconRightDot, 1.0f, 1.0f);
        startScaleAlphaAnimator(this.mFlymeIconRight, 0.416f, 0.0f);
    }

    public final void startCenterIconScaleAnimator(float f, float f2) {
        ImageView imageView = this.mFlymeIcon;
        if (imageView != null) {
            imageView.animate().cancel();
            this.mFlymeIcon.animate().setDuration(400L);
            float f3 = f2 / f;
            this.mFlymeIcon.animate().setInterpolator(new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)).scaleX(f3).scaleY(f3).start();
        }
    }

    public final void startScaleAlphaAnimator(View view, float f, float f2) {
        SpringForce finalPosition = new SpringForce().setDampingRatio(1.0f).setStiffness(300.0f).setFinalPosition(f);
        SpringForce finalPosition2 = new SpringForce().setDampingRatio(1.0f).setStiffness(300.0f).setFinalPosition(f2);
        SpringAnimation spring = new SpringAnimation(view, DynamicAnimation.SCALE_X).setSpring(finalPosition);
        SpringAnimation spring2 = new SpringAnimation(view, DynamicAnimation.SCALE_Y).setSpring(finalPosition);
        SpringAnimation spring3 = new SpringAnimation(view, DynamicAnimation.ALPHA).setSpring(finalPosition2);
        spring.start();
        spring2.start();
        spring3.start();
        this.list.add(spring);
        this.list.add(spring2);
        this.list.add(spring3);
    }

    public final void initLeftRippleSpringAnimator() {
        if (this.mFlymeFpAuthAnimViewLeftX == null || this.mFlymeFpAuthAnimViewLeftY == null || this.mFlymeFpAuthAnimViewAlpha == null) {
            Log.d("UdfpsKeyguardView", "initLeftRippleSpringAnimator()");
            SpringForce stiffness = new SpringForce().setDampingRatio(0.85f).setStiffness(300.0f);
            SpringForce stiffness2 = new SpringForce().setDampingRatio(0.85f).setStiffness(300.0f);
            this.mFlymeFpAuthAnimViewLeftX = new SpringAnimation(this.mFlymeFpAuthAnimViewLeft, DynamicAnimation.SCALE_X).setSpring(stiffness);
            this.mFlymeFpAuthAnimViewLeftY = new SpringAnimation(this.mFlymeFpAuthAnimViewLeft, DynamicAnimation.SCALE_Y).setSpring(stiffness);
            this.mFlymeFpAuthAnimViewAlpha = new SpringAnimation(this.mFlymeFpAuthAnimViewLeft, DynamicAnimation.ALPHA).setSpring(stiffness2);
        }
    }

    public final void initRightRippleSpringAnimator() {
        SpringAnimation springAnimation = this.mFlymeFpAuthAnimViewRightX;
        if (springAnimation == null || springAnimation == null || this.mFlymeFpAuthAnimViewRightAlpha == null) {
            Log.d("UdfpsKeyguardView", "initRightRippleSpringAnimator()");
            SpringForce stiffness = new SpringForce().setDampingRatio(0.85f).setStiffness(300.0f);
            SpringForce stiffness2 = new SpringForce().setDampingRatio(0.85f).setStiffness(300.0f);
            this.mFlymeFpAuthAnimViewRightX = new SpringAnimation(this.mFlymeFpAuthAnimViewRight, DynamicAnimation.SCALE_X).setSpring(stiffness);
            this.mFlymeFpAuthAnimViewRightY = new SpringAnimation(this.mFlymeFpAuthAnimViewRight, DynamicAnimation.SCALE_Y).setSpring(stiffness);
            this.mFlymeFpAuthAnimViewRightAlpha = new SpringAnimation(this.mFlymeFpAuthAnimViewRight, DynamicAnimation.ALPHA).setSpring(stiffness2);
        }
    }

    public final void startLeftRippleAnimator(float f, float f2) {
        Log.d("UdfpsKeyguardView", "startLeftRippleAnimator() scaleTo:" + f + " alphaTo:" + f2);
        initLeftRippleSpringAnimator();
        this.mFlymeFpAuthAnimViewLeftX.getSpring().setFinalPosition(f);
        this.mFlymeFpAuthAnimViewLeftY.getSpring().setFinalPosition(f);
        this.mFlymeFpAuthAnimViewAlpha.getSpring().setFinalPosition(f2);
        this.mFlymeFpAuthAnimViewLeftX.start();
        this.mFlymeFpAuthAnimViewLeftY.start();
        this.mFlymeFpAuthAnimViewAlpha.start();
        this.list.add(this.mFlymeFpAuthAnimViewLeftX);
        this.list.add(this.mFlymeFpAuthAnimViewLeftY);
        this.list.add(this.mFlymeFpAuthAnimViewAlpha);
    }

    public final void startRightRippleAnimator(float f, float f2) {
        Log.d("UdfpsKeyguardView", "startRightRippleAnimator() scaleTo:" + f + " alphaTo:" + f2);
        initRightRippleSpringAnimator();
        this.mFlymeFpAuthAnimViewRightX.getSpring().setFinalPosition(f);
        this.mFlymeFpAuthAnimViewRightY.getSpring().setFinalPosition(f);
        this.mFlymeFpAuthAnimViewRightAlpha.getSpring().setFinalPosition(f2);
        this.mFlymeFpAuthAnimViewRightX.start();
        this.mFlymeFpAuthAnimViewRightY.start();
        this.mFlymeFpAuthAnimViewRightAlpha.start();
        this.list.add(this.mFlymeFpAuthAnimViewRightX);
        this.list.add(this.mFlymeFpAuthAnimViewRightY);
        this.list.add(this.mFlymeFpAuthAnimViewRightAlpha);
    }

    public void updateLeftRightIconAlpha(float f, float f2) {
        this.mLeftRightIconAlpha = f;
        this.mLeftRightIconDotAlpha = f2;
        updateLeftRightIconAlpha();
    }

    public final void updateLeftRightIconAlpha() {
        ImageView imageView = this.mFlymeIconLeft;
        if (imageView != null && this.mFlymeIconRight != null) {
            imageView.setAlpha(this.mLeftRightIconAlpha);
            this.mFlymeIconRight.setAlpha(this.mLeftRightIconAlpha);
        }
        ImageView imageView2 = this.mFlymeIconLeftDot;
        if (imageView2 == null || this.mFlymeIconRightDot == null) {
            return;
        }
        imageView2.setAlpha(this.mLeftRightIconDotAlpha);
        this.mFlymeIconRightDot.setAlpha(this.mLeftRightIconDotAlpha);
    }

    public void updateMaxIconLocation() {
        int i = Integer.parseInt(SystemProperties.get("vendor.meizu.fingerprint.detect.width", "0"));
        int i2 = Integer.parseInt(SystemProperties.get("vendor.meizu.fingerprint.detect.height", "0"));
        Point stableDisplaySize = ((DisplayManager) ((FrameLayout) this).mContext.getSystemService(DisplayManager.class)).getStableDisplaySize();
        DisplayInfo displayInfo = new DisplayInfo();
        ((FrameLayout) this).mContext.getDisplay().getDisplayInfo(displayInfo);
        float physicalPixelDisplaySizeRatio = android.util.DisplayUtils.getPhysicalPixelDisplaySizeRatio(stableDisplaySize.x, stableDisplaySize.y, displayInfo.getNaturalWidth(), displayInfo.getNaturalHeight());
        this.mMaxSensorWidth = (int) (i * physicalPixelDisplaySizeRatio);
        this.mMaxSensorHeight = (int) (i2 * physicalPixelDisplaySizeRatio);
        Log.d("UdfpsKeyguardView", "updateMaxIconLocation: width = " + i + " height = " + i2 + " scaleFactor = " + physicalPixelDisplaySizeRatio + " mMaxSensorWidth = " + this.mMaxSensorWidth + " mMaxSensorHeight = " + this.mMaxSensorHeight);
    }
}
