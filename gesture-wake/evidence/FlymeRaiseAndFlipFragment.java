package com.meizu.settings.display;

/* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
/* JADX INFO: loaded from: classes.dex */
public class FlymeRaiseAndFlipFragment extends com.android.settings.SettingsPreferenceFragment {
    public androidx.preference.SwitchPreference mRaiseFlip;

    @Override // com.android.settings.SettingsPreferenceFragment, com.android.settings.core.ObservablePreferenceFragment, com.android.settingslib.preference.PreferenceFragment, androidx.preference.PreferenceFragmentCompat, androidx.fragment.app.Fragment
    public final void onCreate(android.os.Bundle bundle) {
        super.onCreate(bundle);
        addPreferencesFromResource(com.android.settings.R.xml.flyme_raise_flip);
        this.mRaiseFlip = (androidx.preference.SwitchPreference) findPreference("raise_flip");
        this.mRaiseFlip.setChecked((android.provider.Settings.System.getIntForUser(getContentResolver(), "flipwakeup_switch", 0, -2) == 1) | (android.provider.Settings.System.getIntForUser(getContentResolver(), "raisewakeup_switch", 0, -2) == 1));
    }

    @Override // com.android.settings.SettingsPreferenceFragment
    public final boolean onPreferenceTreeClick(androidx.preference.PreferenceScreen preferenceScreen, androidx.preference.Preference preference) {
        if (preference == this.mRaiseFlip) {
            android.provider.Settings.System.putIntForUser(getContentResolver(), "raisewakeup_switch", this.mRaiseFlip.mChecked ? 1 : 0, -2);
            android.provider.Settings.System.putIntForUser(getContentResolver(), "flipwakeup_switch", this.mRaiseFlip.mChecked ? 1 : 0, -2);
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
