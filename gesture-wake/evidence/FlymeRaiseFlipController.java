package com.meizu.settings.display;

/* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
/* JADX INFO: loaded from: classes.dex */
public class FlymeRaiseFlipController extends com.android.settings.core.TogglePreferenceController {
    public FlymeRaiseFlipController(android.content.Context context, java.lang.String str) {
        super(context, str);
    }

    @Override // com.android.settings.core.BasePreferenceController
    public int getAvailabilityStatus() {
        return com.meizu.settings.utils.MzUtils.supportRaiseFlipFeature(this.mContext) ? 0 : 3;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.slices.Sliceable
    public /* bridge */ /* synthetic */ java.lang.Class getBackgroundWorkerClass() {
        return null;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.slices.Sliceable
    public /* bridge */ /* synthetic */ android.content.IntentFilter getIntentFilter() {
        return null;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.core.PreferenceControllerMixin
    public /* bridge */ /* synthetic */ com.meizu.settings.search.ResultPayload getResultPayload() {
        return null;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.slices.Sliceable
    public int getSliceHighlightMenuRes() {
        return 0;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.slices.Sliceable
    public /* bridge */ /* synthetic */ boolean hasAsyncUpdate() {
        return false;
    }

    @Override // com.android.settings.core.TogglePreferenceController
    /* JADX INFO: renamed from: isChecked */
    public boolean getThreadEnabled() {
        return (android.provider.Settings.System.getInt(this.mContext.getContentResolver(), "flipwakeup_switch", 0) == 1) || (android.provider.Settings.System.getInt(this.mContext.getContentResolver(), "raisewakeup_switch", 0) == 1);
    }

    @Override // com.android.settings.core.TogglePreferenceController
    public boolean setChecked(boolean z) {
        android.provider.Settings.System.putInt(this.mContext.getContentResolver(), "raisewakeup_switch", z ? 1 : 0);
        android.provider.Settings.System.putInt(this.mContext.getContentResolver(), "flipwakeup_switch", z ? 1 : 0);
        return true;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.slices.Sliceable
    public /* bridge */ /* synthetic */ boolean useDynamicSliceSummary() {
        return false;
    }
}
