package com.meizu.settings.display;

/* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
/* JADX INFO: loaded from: classes.dex */
public class FlymeDoubleClickWakeUpController extends com.android.settings.core.TogglePreferenceController implements androidx.lifecycle.DefaultLifecycleObserver {
    public FlymeDoubleClickWakeUpController(android.content.Context context, java.lang.String str) {
        super(context, str);
    }

    @Override // com.android.settings.core.BasePreferenceController
    public int getAvailabilityStatus() {
        return this.mContext.getResources().getBoolean(android.R.bool.config_automatic_brightness_available) ? 0 : 3;
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
        return android.provider.Settings.System.getInt(this.mContext.getContentResolver(), "mz_quick_wakeup_double_click", 1) == 1;
    }

    @Override // androidx.lifecycle.DefaultLifecycleObserver
    public void onCreate(@org.jetbrains.annotations.NotNull androidx.lifecycle.LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getClass();
    }

    @Override // androidx.lifecycle.DefaultLifecycleObserver
    public void onDestroy(@org.jetbrains.annotations.NotNull androidx.lifecycle.LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getClass();
    }

    @Override // androidx.lifecycle.DefaultLifecycleObserver
    public void onPause(@org.jetbrains.annotations.NotNull androidx.lifecycle.LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getClass();
    }

    @Override // androidx.lifecycle.DefaultLifecycleObserver
    public void onResume(@org.jetbrains.annotations.NotNull androidx.lifecycle.LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getClass();
    }

    @Override // androidx.lifecycle.DefaultLifecycleObserver
    public void onStart(@org.jetbrains.annotations.NotNull androidx.lifecycle.LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getClass();
    }

    @Override // androidx.lifecycle.DefaultLifecycleObserver
    public void onStop(@org.jetbrains.annotations.NotNull androidx.lifecycle.LifecycleOwner lifecycleOwner) {
        lifecycleOwner.getClass();
    }

    @Override // com.android.settings.core.TogglePreferenceController
    public boolean setChecked(boolean z) {
        android.provider.Settings.System.putInt(this.mContext.getContentResolver(), "mz_quick_wakeup_double_click", z ? 1 : 0);
        com.meizu.settings.accessibility.FlymeQuickWakeupFragment.updateQuickWakeupSwitchValue(this.mContext.getContentResolver());
        return true;
    }

    @Override // com.android.settings.core.TogglePreferenceController, com.android.settings.slices.Sliceable
    public /* bridge */ /* synthetic */ boolean useDynamicSliceSummary() {
        return false;
    }
}
