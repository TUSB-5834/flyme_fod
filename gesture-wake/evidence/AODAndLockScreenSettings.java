package com.meizu.settings.lockscreen_aod;

/* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
/* JADX INFO: loaded from: classes.dex */
public class AODAndLockScreenSettings extends com.android.settings.SettingsPreferenceFragment implements androidx.preference.Preference.OnPreferenceChangeListener {
    public static final com.android.settings.search.BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.AnonymousClass3();
    public androidx.preference.PreferenceScreen mAODAndLockscreen;
    public androidx.preference.Preference mAODComingNoticeAnim;
    public androidx.preference.Preference mAODMode;
    public com.meizu.settings.display.aod.AODPreference mAODSample;
    public androidx.preference.Preference mAODStyle;
    public androidx.preference.SwitchPreference mAODSwitch;
    public androidx.preference.SwitchPreference mAdaptiveKeepBrightScreen;
    public androidx.preference.SwitchPreference mAutoRotateSwitchPreference;
    public java.text.DateFormat mDateFormat;
    public com.android.internal.widget.LockPatternUtils mLockPatternUtils;
    public androidx.preference.Preference mLockScreenLayout;
    public androidx.preference.Preference mLockScreenPoster;
    public androidx.preference.SwitchPreference mLockscreenMusic;
    public flyme.support.v7.app.AlertDialog mOwnerInfoSettingsDialog;
    public androidx.preference.SwitchPreference mQuickWakeupDoubleClickSwitch;
    public androidx.preference.SwitchPreference mRaiseFlip;
    public com.android.settings.display.TimeoutListPreference mScreenTimeoutPreference;
    public androidx.preference.SwitchPreference mToggleDeviceControls;
    public androidx.preference.SwitchPreference mTogglePullDownNotificationbar;
    public com.meizu.settings.datareport.UsageStatsProxy mUsageStatsProxy;
    public final com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.AnonymousClass1 mPowerSaveObserver = new android.database.ContentObserver(new android.os.Handler()) { // from class: com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.1
        @Override // android.database.ContentObserver
        public final void onChange(boolean z, android.net.Uri uri) {
            if (android.provider.Settings.Secure.getUriFor("mz_current_power_mode").equals(uri)) {
                boolean zEquals = android.os.SystemProperties.get("persist.sys.meizu.aod", "0").equals("1");
                com.android.settings.AirplaneModeEnabler$$ExternalSyntheticOutline0.m("On powermode change. Aod on: ", "AODAndLockScreenSettings", zEquals);
                com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.this.mAODSwitch.setChecked(zEquals);
                com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.this.updateStyleAndModeState(zEquals);
            }
        }
    };
    public final com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.AnonymousClass2 mRotationPolicyListener = new com.android.internal.view.RotationPolicy.RotationPolicyListener() { // from class: com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.2
        public final void onChange() {
            androidx.fragment.app.FragmentActivity activity;
            com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings aODAndLockScreenSettings = com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.this;
            if (aODAndLockScreenSettings.mAutoRotateSwitchPreference == null || (activity = aODAndLockScreenSettings.getActivity()) == null) {
                return;
            }
            com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.this.mAutoRotateSwitchPreference.setChecked(!com.android.internal.view.RotationPolicy.isRotationLocked(activity));
        }
    };

    /* JADX INFO: renamed from: com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings$3, reason: invalid class name */
    /* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
    public final class AnonymousClass3 extends com.android.settings.search.BaseSearchIndexProvider {
        @Override // com.android.settings.search.BaseSearchIndexProvider, com.android.settingslib.search.Indexable.SearchIndexProvider
        public final java.util.List getNonIndexableKeys(android.content.Context context) {
            java.util.List nonIndexableKeys = super.getNonIndexableKeys(context);
            if (!com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings.isAppExist(context, "com.meizu.net.nativelockscreen") || android.os.BuildExt.isProductInternational()) {
                nonIndexableKeys.add("lockscreen_poster");
                nonIndexableKeys.add("adaptive_keep_bright_screen_switch");
            }
            if (!com.meizu.settings.utils.MzUtils.isCtsRunning()) {
                nonIndexableKeys.add("owner_info_settings");
            }
            return nonIndexableKeys;
        }

        @Override // com.android.settings.search.BaseSearchIndexProvider, com.android.settingslib.search.Indexable.SearchIndexProvider
        public final java.util.List getXmlResourcesToIndex(android.content.Context context) {
            java.util.ArrayList arrayList = new java.util.ArrayList();
            android.provider.SearchIndexableResource searchIndexableResource = new android.provider.SearchIndexableResource(context);
            searchIndexableResource.xmlResId = com.android.settings.R.xml.aod_and_lockscreen_settings;
            arrayList.add(searchIndexableResource);
            return arrayList;
        }
    }

    public static boolean isAppExist(android.content.Context context, java.lang.String str) {
        if (android.text.TextUtils.isEmpty(str)) {
            return false;
        }
        try {
            return context.getPackageManager().getApplicationInfo(str, 8192) != null;
        } catch (android.content.pm.PackageManager.NameNotFoundException unused) {
            return false;
        }
    }

    public final java.lang.String getFormatTime(java.lang.String str) {
        int i = java.lang.Integer.parseInt(str.substring(0, 2));
        int i2 = java.lang.Integer.parseInt(str.substring(3, 5));
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(11, i);
        calendar.set(12, i2);
        return this.mDateFormat.format(calendar.getTime());
    }

    public final void handleOwnerInfoPrefInCts() {
        com.android.settingslib.RestrictedPreference restrictedPreference = (com.android.settingslib.RestrictedPreference) findPreference("owner_info_settings");
        if (restrictedPreference != null) {
            if (!com.meizu.settings.utils.MzUtils.isCtsRunning()) {
                this.mAODAndLockscreen.removePreference(restrictedPreference);
            } else if (this.mLockPatternUtils.isDeviceOwnerInfoEnabled()) {
                restrictedPreference.setSummary(this.mLockPatternUtils.getDeviceOwnerInfo());
                restrictedPreference.setDisabledByAdmin(com.android.settingslib.RestrictedLockUtilsInternal.getDeviceOwner(getActivity()));
            }
        }
    }

    @Override // com.android.settings.SettingsPreferenceFragment, com.android.settings.core.ObservablePreferenceFragment, com.android.settingslib.preference.PreferenceFragment, androidx.preference.PreferenceFragmentCompat, androidx.fragment.app.Fragment
    public final void onCreate(android.os.Bundle bundle) {
        com.android.settingslib.RestrictedLockUtils.EnforcedAdmin enforcedAdminCheckIfMaximumTimeToLockIsSet;
        super.onCreate(bundle);
        androidx.fragment.app.FragmentActivity activity = getActivity();
        activity.setTitle(com.android.settings.R.string.aod_and_lockscreen_title);
        this.mUsageStatsProxy = com.meizu.settings.datareport.UsageStatsProxy.getInstance(activity);
        this.mDateFormat = android.text.format.DateFormat.getTimeFormat(activity);
        addPreferencesFromResource(com.android.settings.R.xml.aod_and_lockscreen_settings);
        this.mLockPatternUtils = new com.android.internal.widget.LockPatternUtils(getActivity());
        boolean zIsProductInternational = android.os.BuildExt.isProductInternational();
        boolean zEquals = android.os.SystemProperties.get("persist.sys.meizu.aod", "0").equals("1");
        this.mAODAndLockscreen = (androidx.preference.PreferenceScreen) findPreference("aod_and_lockscreen");
        androidx.preference.SwitchPreference switchPreference = (androidx.preference.SwitchPreference) findPreference("aod_switch");
        this.mAODSwitch = switchPreference;
        switchPreference.setChecked(zEquals);
        this.mAODStyle = findPreference("aod_style");
        this.mAODMode = findPreference("aod_mode");
        this.mAODComingNoticeAnim = findPreference("aod_coming_notification_anim");
        this.mAODSample = (com.meizu.settings.display.aod.AODPreference) findPreference("aod_image");
        updateStyleAndModeState(zEquals);
        com.meizu.settings.utils.MzUtils.setOnPowerModeChangeListener(getActivity(), this.mPowerSaveObserver, true);
        this.mLockScreenPoster = findPreference("lockscreen_poster");
        this.mLockScreenLayout = findPreference("lockscreen_layout");
        android.content.ContentResolver contentResolver = getActivity().getContentResolver();
        androidx.preference.SwitchPreference switchPreference2 = (androidx.preference.SwitchPreference) findPreference("lockscreen_pull_down_notibar");
        this.mTogglePullDownNotificationbar = switchPreference2;
        switchPreference2.setChecked(android.provider.Settings.System.getInt(contentResolver, "lockscreen_pull_notificationbar", 0) != 0);
        androidx.preference.SwitchPreference switchPreference3 = (androidx.preference.SwitchPreference) findPreference("device_controls");
        this.mToggleDeviceControls = switchPreference3;
        if (switchPreference3 != null) {
            switchPreference3.setChecked(android.provider.Settings.System.getInt(contentResolver, "device_controls", 0) != 0);
        }
        androidx.preference.SwitchPreference switchPreference4 = (androidx.preference.SwitchPreference) findPreference("lockscreen_show_music_key");
        this.mLockscreenMusic = switchPreference4;
        switchPreference4.setChecked(android.provider.Settings.System.getInt(getContentResolver(), "lockscreen_music", 0) == 1);
        androidx.preference.SwitchPreference switchPreference5 = (androidx.preference.SwitchPreference) findPreference("adaptive_keep_bright_screen_switch");
        this.mAdaptiveKeepBrightScreen = switchPreference5;
        switchPreference5.setChecked(android.provider.Settings.Global.getInt(getContentResolver(), "adaptive_keep_bright_screen", 1) == 1);
        this.mScreenTimeoutPreference = (com.android.settings.display.TimeoutListPreference) findPreference("screen_timeout");
        long j = android.provider.Settings.System.getLong(contentResolver, "screen_off_timeout", 30000L);
        this.mScreenTimeoutPreference.setValue(java.lang.String.valueOf(j));
        this.mScreenTimeoutPreference.mOnChangeListener = this;
        if (android.provider.Settings.Secure.getInt(getActivity().getContentResolver(), "mz_current_power_mode", 0) == 3) {
            this.mScreenTimeoutPreference.setEnabled(false);
        }
        android.app.admin.DevicePolicyManager devicePolicyManager = (android.app.admin.DevicePolicyManager) getActivity().getSystemService("device_policy");
        if (devicePolicyManager != null && (enforcedAdminCheckIfMaximumTimeToLockIsSet = com.android.settingslib.RestrictedLockUtilsInternal.checkIfMaximumTimeToLockIsSet(getActivity())) != null) {
            this.mScreenTimeoutPreference.removeUnusableTimeouts(devicePolicyManager.getMaximumTimeToLock(enforcedAdminCheckIfMaximumTimeToLockIsSet.component), enforcedAdminCheckIfMaximumTimeToLockIsSet);
        }
        updateTimeoutPreferenceDescription(j);
        this.mRaiseFlip = (androidx.preference.SwitchPreference) findPreference("raise_flip");
        boolean zIsFlymeLabHasFeature = com.meizu.settings.utils.FlymeLabHelper.getInstance(getActivity()).isFlymeLabHasFeature("com.android.settings.raiseflip");
        android.util.Log.d("AODAndLockScreenSettings", "initRaiseFlip: " + com.meizu.settings.utils.MzUtils.supportRaiseFlipFeature(getActivity()));
        if (com.meizu.settings.utils.MzUtils.isGuestUser(getActivity()) || zIsFlymeLabHasFeature || !com.meizu.settings.utils.MzUtils.supportRaiseFlipFeature(getActivity())) {
            getPreferenceScreen().removePreference(this.mRaiseFlip);
        } else {
            this.mRaiseFlip.setChecked((android.provider.Settings.System.getIntForUser(getContentResolver(), "flipwakeup_switch", 0, -2) == 1) | (android.provider.Settings.System.getIntForUser(getContentResolver(), "raisewakeup_switch", 0, -2) == 1));
        }
        androidx.preference.SwitchPreference switchPreference6 = (androidx.preference.SwitchPreference) findPreference("quick_wakeup_double_click");
        this.mQuickWakeupDoubleClickSwitch = switchPreference6;
        switchPreference6.setChecked(android.provider.Settings.System.getInt(contentResolver, "mz_quick_wakeup_double_click", 1) != 0);
        androidx.fragment.app.FragmentActivity activity2 = getActivity();
        if (com.android.internal.view.RotationPolicy.isRotationLockToggleVisible(activity2)) {
            androidx.preference.SwitchPreference switchPreference7 = (androidx.preference.SwitchPreference) findPreference("auto_rotate");
            this.mAutoRotateSwitchPreference = switchPreference7;
            switchPreference7.setChecked(!com.android.internal.view.RotationPolicy.isRotationLocked(activity2));
        } else {
            removePreference("auto_rotate");
        }
        removePreference("auto_rotate");
        if (com.meizu.settings.utils.MzUtils.isGuestUser(getActivity())) {
            getPreferenceScreen().removePreference(this.mTogglePullDownNotificationbar);
        }
        if (!com.meizu.settings.utils.MzUtils.isCtsRunning()) {
            getPreferenceScreen().removePreference(this.mToggleDeviceControls);
        }
        getPreferenceScreen().removePreference(this.mLockscreenMusic);
        if (!isAppExist(getContext(), "com.meizu.net.nativelockscreen") || zIsProductInternational) {
            getPreferenceScreen().removePreference(this.mLockScreenPoster);
        }
        if (zIsProductInternational) {
            getPreferenceScreen().removePreference(this.mAdaptiveKeepBrightScreen);
        }
        if (com.meizu.settings.utils.MzUtils.isEasyModeOpened(getContext())) {
            getPreferenceScreen().removePreference(this.mQuickWakeupDoubleClickSwitch);
        }
    }

    @Override // com.android.settings.SettingsPreferenceFragment, com.android.settings.core.ObservablePreferenceFragment, com.android.settingslib.preference.PreferenceFragment, androidx.fragment.app.Fragment
    public final void onDestroy() {
        super.onDestroy();
        com.android.settings.display.TimeoutListPreference timeoutListPreference = this.mScreenTimeoutPreference;
        if (timeoutListPreference != null) {
            timeoutListPreference.mOnChangeListener = null;
        }
        if (getActivity() != null) {
            com.android.internal.view.RotationPolicy.unregisterRotationPolicyListener(getActivity(), this.mRotationPolicyListener);
            com.meizu.settings.utils.MzUtils.setOnPowerModeChangeListener(getActivity(), this.mPowerSaveObserver, false);
        }
    }

    @Override // androidx.preference.Preference.OnPreferenceChangeListener
    public final boolean onPreferenceChange(androidx.preference.Preference preference, java.lang.Object obj) {
        java.lang.String str = preference.mKey;
        com.meizu.settings.datareport.UsageStatsProxy usageStatsProxy = this.mUsageStatsProxy;
        usageStatsProxy.getClass();
        usageStatsProxy.reportData(str, "AODAndLockScreenSettings", preference.mKey, obj);
        if (!"screen_timeout".equals(str)) {
            return true;
        }
        try {
            int i = java.lang.Integer.parseInt((java.lang.String) obj);
            android.provider.Settings.System.putInt(getContentResolver(), "screen_off_timeout", i);
            updateTimeoutPreferenceDescription(i);
            return true;
        } catch (java.lang.NumberFormatException e) {
            android.util.Log.e("AODAndLockScreenSettings", "could not persist screen timeout setting", e);
            return true;
        }
    }

    @Override // com.android.settings.SettingsPreferenceFragment
    public final boolean onPreferenceTreeClick(androidx.preference.PreferenceScreen preferenceScreen, androidx.preference.Preference preference) {
        if (this.mAODSample != preference) {
            if (this.mAODSwitch == preference) {
                if (android.provider.Settings.Secure.getInt(getContentResolver(), "accessibility_display_inversion_enabled", 0) != 1) {
                    android.os.SystemProperties.set("persist.sys.meizu.aod", this.mAODSwitch.mChecked ? "1" : "0");
                    android.provider.Settings.Secure.putInt(getContentResolver(), "doze_always_on", this.mAODSwitch.mChecked ? 1 : 0);
                    updateStyleAndModeState(this.mAODSwitch.mChecked);
                    return true;
                }
                flyme.support.v7.app.AlertDialog.Builder builder = new flyme.support.v7.app.AlertDialog.Builder(getActivity());
                builder.setTitle(com.android.settings.R.string.open_aod_in_display_inversion_tips);
                builder.setNegativeButton(com.android.settings.R.string.i_know, (android.content.DialogInterface.OnClickListener) null);
                builder.create().show();
                this.mAODSwitch.setChecked(false);
                android.os.SystemProperties.set("persist.sys.meizu.aod", "0");
                android.provider.Settings.Secure.putInt(getContentResolver(), "doze_always_on", 0);
                return true;
            }
            if (this.mAODStyle == preference) {
                try {
                    android.content.Intent intent = new android.content.Intent();
                    if (isAppExist(getContext(), "com.flyme.systemuieditor")) {
                        intent.putExtra("enter_pager", "aod");
                        intent.setAction("com.flyme.systemuieditor.wallpaper.export");
                    } else {
                        intent.setAction("com.meizu.settings.AOD_STYLE_SETTING_ACTIVITY");
                    }
                    startActivity(intent);
                    return true;
                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                    return true;
                }
            }
            if (this.mAODMode != preference) {
                if (this.mAODComingNoticeAnim == preference) {
                    try {
                        android.content.Intent intent2 = new android.content.Intent();
                        if (isAppExist(getContext(), "com.flyme.systemuieditor")) {
                            intent2.setAction("com.flyme.systemuieditor.notification.effect");
                        } else {
                            intent2.setAction("com.meizu.settings.AOD_DETAIL_SETTING_ACTIVITY");
                        }
                        intent2.putExtra("is_aod_notification_settings", true);
                        startActivity(intent2);
                    } catch (java.lang.Exception e2) {
                        e2.printStackTrace();
                    }
                } else if (preference == this.mLockScreenLayout) {
                    try {
                        android.content.Intent intent3 = new android.content.Intent();
                        if (isAppExist(getContext(), "com.flyme.systemuieditor")) {
                            intent3.putExtra("enter_pager", "lock");
                            intent3.setAction("com.flyme.systemuieditor.wallpaper.export");
                        } else {
                            intent3.setAction("com.flyme.action.settings.LOCKSCREEN_LAYOUT");
                        }
                        startActivity(intent3);
                    } catch (java.lang.Exception e3) {
                        e3.printStackTrace();
                    }
                } else if (preference == this.mLockScreenPoster) {
                    try {
                        android.content.Intent intent4 = new android.content.Intent();
                        intent4.setAction("com.meizu.net.nativelockscreen.locksettingpage");
                        startActivity(intent4);
                    } catch (java.lang.Exception e4) {
                        e4.printStackTrace();
                    }
                } else {
                    if (preference == this.mTogglePullDownNotificationbar) {
                        android.provider.Settings.System.putInt(getActivity().getContentResolver(), "lockscreen_pull_notificationbar", this.mTogglePullDownNotificationbar.mChecked ? 1 : 0);
                        return true;
                    }
                    if (preference == this.mToggleDeviceControls) {
                        android.provider.Settings.System.putInt(getActivity().getContentResolver(), "device_controls", this.mToggleDeviceControls.mChecked ? 1 : 0);
                        return true;
                    }
                    if (preference == this.mRaiseFlip) {
                        android.provider.Settings.System.putIntForUser(getContentResolver(), "raisewakeup_switch", this.mRaiseFlip.mChecked ? 1 : 0, -2);
                        android.provider.Settings.System.putIntForUser(getContentResolver(), "flipwakeup_switch", this.mRaiseFlip.mChecked ? 1 : 0, -2);
                        return true;
                    }
                    if (preference == this.mQuickWakeupDoubleClickSwitch) {
                        android.provider.Settings.System.putInt(getActivity().getContentResolver(), "mz_quick_wakeup_double_click", this.mQuickWakeupDoubleClickSwitch.mChecked ? 1 : 0);
                        com.meizu.settings.accessibility.FlymeQuickWakeupFragment.updateQuickWakeupSwitchValue(getActivity().getContentResolver());
                        return true;
                    }
                    if (preference == this.mAutoRotateSwitchPreference) {
                        com.android.internal.view.RotationPolicy.setRotationLock(getActivity(), !this.mAutoRotateSwitchPreference.mChecked, "AODAndLockScreenSettings#setChecked");
                        return true;
                    }
                    if (preference == this.mLockscreenMusic) {
                        android.provider.Settings.System.putInt(getContentResolver(), "lockscreen_music", this.mLockscreenMusic.mChecked ? 1 : 0);
                    } else if ("owner_info_settings".equals(preference.mKey)) {
                        final android.view.View viewInflate = android.view.LayoutInflater.from(getActivity()).inflate(com.android.settings.R.layout.ownerinfo, (android.view.ViewGroup) null);
                        if (this.mOwnerInfoSettingsDialog == null) {
                            flyme.support.v7.app.AlertDialog.Builder builder2 = new flyme.support.v7.app.AlertDialog.Builder(getActivity());
                            builder2.setTitle(com.android.settings.R.string.owner_info_settings_title);
                            builder2.P.mView = viewInflate;
                            builder2.setPositiveButton(com.android.settings.R.string.save, new android.content.DialogInterface.OnClickListener() { // from class: com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings$$ExternalSyntheticLambda0
                                @Override // android.content.DialogInterface.OnClickListener
                                public final void onClick(android.content.DialogInterface dialogInterface, int i) {
                                    com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings aODAndLockScreenSettings = this.f$0;
                                    android.view.View view = viewInflate;
                                    aODAndLockScreenSettings.getClass();
                                    android.widget.EditText editText = (android.widget.EditText) view.findViewById(com.android.settings.R.id.owner_info_edit_text);
                                    aODAndLockScreenSettings.mLockPatternUtils.setDeviceOwnerInfo(editText.getText().toString());
                                    editText.setText("");
                                    aODAndLockScreenSettings.handleOwnerInfoPrefInCts();
                                }
                            });
                            builder2.setNegativeButton(com.android.settings.R.string.cancel, new com.android.settings.accessibility.AccessibilityDialogUtils$$ExternalSyntheticLambda0());
                            this.mOwnerInfoSettingsDialog = builder2.create();
                        }
                        this.mOwnerInfoSettingsDialog.show();
                    } else if (preference == this.mAdaptiveKeepBrightScreen) {
                        android.provider.Settings.Global.putInt(getContentResolver(), "adaptive_keep_bright_screen", this.mAdaptiveKeepBrightScreen.mChecked ? 1 : 0);
                    }
                }
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override // com.android.settings.SettingsPreferenceFragment, com.android.settings.core.InstrumentedPreferenceFragment, com.android.settings.core.ObservablePreferenceFragment, com.android.settingslib.preference.PreferenceFragment, androidx.fragment.app.Fragment
    public final void onResume() {
        super.onResume();
        if (com.android.internal.view.RotationPolicy.isRotationLockToggleVisible(getActivity())) {
            com.android.internal.view.RotationPolicy.registerRotationPolicyListener(getActivity(), this.mRotationPolicyListener);
            this.mAutoRotateSwitchPreference.setChecked(!com.android.internal.view.RotationPolicy.isRotationLocked(getActivity()));
        }
        int i = android.provider.Settings.System.getInt(getContentResolver(), "aod_display_mode", 1);
        java.lang.String string = android.provider.Settings.System.getString(getContentResolver(), "aod_timing_mode_start_time");
        java.lang.String string2 = android.provider.Settings.System.getString(getContentResolver(), "aod_timing_mode_end_time");
        if (string == null) {
            string = "08:00";
        }
        if (string2 == null) {
            string2 = "22:00";
        }
        if (i == 0) {
            this.mAODMode.setSummary(getString(com.android.settings.R.string.aod_display_mode_always));
        } else if (i == 1) {
            java.lang.String formatTime = getFormatTime(string);
            java.lang.String formatTime2 = getFormatTime(string2);
            this.mAODMode.setSummary(formatTime + " - " + formatTime2);
        } else if (i == 2) {
            this.mAODMode.setSummary(getString(com.android.settings.R.string.aod_display_mode_touch));
        }
        int i2 = android.provider.Settings.System.getInt(getContentResolver(), "notification_wakeup_screen", 0);
        if (i2 == 0) {
            this.mAODComingNoticeAnim.setSummary(getString(com.android.settings.R.string.aod_noti_anim_none));
        } else if (i2 == 1) {
            this.mAODComingNoticeAnim.setSummary(getString(com.android.settings.R.string.aod_noti_anim_wake_up));
        } else if (i2 == 2) {
            this.mAODComingNoticeAnim.setSummary(getString(com.android.settings.R.string.aod_noti_anim_circle));
        } else if (i2 == 3) {
            this.mAODComingNoticeAnim.setSummary(getString(com.android.settings.R.string.aod_noti_anim_light));
        }
        handleOwnerInfoPrefInCts();
        com.meizu.settings.display.aod.AODPreference aODPreference = this.mAODSample;
        if (aODPreference != null) {
            aODPreference.updateImage();
        }
    }

    public final void updateStyleAndModeState(boolean z) {
        if (z) {
            this.mAODStyle.setEnabled(true);
            this.mAODMode.setEnabled(true);
            this.mAODSample.setEnabled(false);
        } else {
            this.mAODStyle.setEnabled(false);
            this.mAODMode.setEnabled(false);
            this.mAODSample.setEnabled(false);
        }
    }

    public final void updateTimeoutPreferenceDescription(long j) {
        java.lang.String string;
        com.android.settings.display.TimeoutListPreference timeoutListPreference = this.mScreenTimeoutPreference;
        if (timeoutListPreference.mHelper.mDisabledByAdmin) {
            string = getString(com.android.settings.R.string.disabled_by_policy_title);
        } else {
            java.lang.CharSequence[] charSequenceArr = timeoutListPreference.mEntries;
            java.lang.CharSequence[] charSequenceArr2 = timeoutListPreference.mEntryValues;
            if (charSequenceArr == null || charSequenceArr.length == 0) {
                string = "";
            } else {
                int i = 0;
                for (int i2 = 0; i2 < charSequenceArr2.length; i2++) {
                    if (j >= java.lang.Long.parseLong(charSequenceArr2[i2].toString())) {
                        i = i2;
                    }
                }
                string = i == charSequenceArr2.length + (-1) ? charSequenceArr[i].toString() : timeoutListPreference.mContext.getString(com.android.settings.R.string.screen_timeout_summary, charSequenceArr[i]);
            }
        }
        timeoutListPreference.setSummary(string);
    }
}
