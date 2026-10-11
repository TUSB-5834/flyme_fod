package com.flyme.systemui.biometrics;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import com.opensource.svgaplayer.SVGAImageView;
import com.opensource.svgaplayer.SVGAParser;
import com.opensource.svgaplayer.SVGAVideoEntity;
import com.opensource.svgaplayer.utils.log.SVGALogger;

/* JADX INFO: loaded from: classes5.dex */
public class FlymeFpAuthAnimView extends SVGAImageView {
    public int mAnimationType;
    public Context mContext;
    public SVGAParser mSvgaParser;

    public FlymeFpAuthAnimView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet, -1);
        this.mContext = context;
        SVGALogger.INSTANCE.setLogEnabled(true);
        SVGAParser.Companion companion = SVGAParser.Companion;
        companion.shareParser().init(this.mContext);
        this.mSvgaParser = companion.shareParser();
        loadAnimationType();
        loadAnimation();
    }

    @Override // android.widget.ImageView, android.view.View
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override // com.opensource.svgaplayer.SVGAImageView, android.widget.ImageView, android.view.View
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public void startDownAnimation() {
        Log.d("FlymeFpAuthAnimView", "startDownAnimation mAnimationType=" + this.mAnimationType);
        if (-1 == this.mAnimationType) {
            return;
        }
        setVisibility(0);
        stepToFrame(0, true);
    }

    public void stopDownAnimation() {
        stopAnimation();
        setVisibility(4);
    }

    public final void loadAnimationType() {
        this.mAnimationType = Settings.Global.getInt(this.mContext.getContentResolver(), "fingerprint_animation", 3);
    }

    private void loadAnimation() {
        this.mSvgaParser.decodeFromAssets(getAnimationFile(this.mAnimationType), new SVGAParser.ParseCompletion() { // from class: com.flyme.systemui.biometrics.FlymeFpAuthAnimView.1
            @Override // com.opensource.svgaplayer.SVGAParser.ParseCompletion
            public void onComplete(SVGAVideoEntity sVGAVideoEntity) {
                Log.d("FlymeFpAuthAnimView", "onComplete");
                FlymeFpAuthAnimView.this.setVideoItem(sVGAVideoEntity);
            }

            @Override // com.opensource.svgaplayer.SVGAParser.ParseCompletion
            public void onError() {
                Log.d("FlymeFpAuthAnimView", "onError");
            }
        }, null);
    }

    public final String getAnimationFile(int i) {
        if (i == 3) {
            return "wave.svga";
        }
        if (i == 2) {
            return "energy_ray.svga";
        }
        if (i == 1) {
            return "void_force.svga";
        }
        if (i != 0) {
            return "wave.svga";
        }
        return "diffusion.svga";
    }
}
