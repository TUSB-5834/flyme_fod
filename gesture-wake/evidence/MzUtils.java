package com.meizu.settings.utils;

import com.meizu.common.app.SlideNotice.SlideViewController.SlidingHideListener;
import com.meizu.common.app.SlideNotice.SlideViewController.SlidingShowListener;

/* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
/* JADX INFO: loaded from: classes.dex */
public abstract class MzUtils {
    public static final java.util.List LETTERS_AR = new java.util.ArrayList(java.util.Arrays.asList("ا", "ب", "ت", "ﺙ", "ج", "ح", "خ", "د", "ﺫ", "ر", "ز", "س", "ﺵ", "ص", "ﺽ", "ط", "ﻅ", "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "ه", "و", "ي", androidx.exifinterface.media.ExifInterface.GPS_MEASUREMENT_IN_PROGRESS, "B", "C", "D", androidx.exifinterface.media.ExifInterface.LONGITUDE_EAST, "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", androidx.exifinterface.media.ExifInterface.LATITUDE_SOUTH, androidx.exifinterface.media.ExifInterface.GPS_DIRECTION_TRUE, "U", androidx.exifinterface.media.ExifInterface.GPS_MEASUREMENT_INTERRUPTED, androidx.exifinterface.media.ExifInterface.LONGITUDE_WEST, "X", "Y", "Z"));
    public static final java.util.List LETTERS_TH = new java.util.ArrayList(java.util.Arrays.asList("ก", "ข", "ฃ", "ค", "ฅ", "ฆ", "ง", "จ", "ฉ", "ช", "ซ", "ฌ", "ญ", "ฎ", "ฏ", "ฐ", "ฑ", "ฒ", "ณ", "ด", "ต", "ถ", "ท", "ธ", "น", "บ", "ป", "ผ", "ฝ", "พ", "ฟ", "ภ", "ม", "ย", "ร", "ล", "ว", "ศ", "ษ", "ส", "ห", "ฬ", "อ", "ฮ", androidx.exifinterface.media.ExifInterface.GPS_MEASUREMENT_IN_PROGRESS, "B", "C", "D", androidx.exifinterface.media.ExifInterface.LONGITUDE_EAST, "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", androidx.exifinterface.media.ExifInterface.LATITUDE_SOUTH, androidx.exifinterface.media.ExifInterface.GPS_DIRECTION_TRUE, "U", androidx.exifinterface.media.ExifInterface.GPS_MEASUREMENT_INTERRUPTED, androidx.exifinterface.media.ExifInterface.LONGITUDE_WEST, "X", "Y", "Z"));
    public static final java.util.List LETTERS_TH_VOWEL = new java.util.ArrayList(java.util.Arrays.asList("เ", "แ", "ไ", "ใ", "โ"));
    public static final com.meizu.settings.utils.MzUtils.AnonymousClass1 LOCAL_COMP = new com.meizu.settings.utils.MzUtils.AnonymousClass1();
    public static long sLastClickTime = 0;

    /* JADX INFO: renamed from: com.meizu.settings.utils.MzUtils$1, reason: invalid class name */
    /* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
    public final class AnonymousClass1 implements java.util.Comparator {
        @Override // java.util.Comparator
        public final int compare(java.lang.Object obj, java.lang.Object obj2) {
            com.android.settingslib.applications.ApplicationsState.AppEntry appEntry = (com.android.settingslib.applications.ApplicationsState.AppEntry) obj;
            com.android.settingslib.applications.ApplicationsState.AppEntry appEntry2 = (com.android.settingslib.applications.ApplicationsState.AppEntry) obj2;
            java.lang.String strTrim = appEntry.label.replaceAll("\\u00A0", " ").trim();
            java.lang.String strTrim2 = appEntry2.label.replaceAll("\\u00A0", " ").trim();
            if (com.meizu.settings.utils.MzUtils.isChineseChar(strTrim)) {
                strTrim = appEntry.lableToPinyin;
            }
            if (com.meizu.settings.utils.MzUtils.isChineseChar(strTrim2)) {
                strTrim2 = appEntry2.lableToPinyin;
            }
            return com.meizu.settings.utils.MzUtils.getCompareResult(strTrim, strTrim2);
        }
    }

    /* JADX INFO: renamed from: com.meizu.settings.utils.MzUtils$3, reason: invalid class name */
    /* JADX INFO: compiled from: go/retraceme fc82c50b783252422d16fc7c5e76504a0bf8bb983af2774fa0c6d76cd300d70c */
    public final class AnonymousClass3 implements android.view.View.OnClickListener {
        public final /* synthetic */ androidx.fragment.app.FragmentActivity val$activity;

        public AnonymousClass3(androidx.fragment.app.FragmentActivity fragmentActivity) {
            this.val$activity = fragmentActivity;
        }

        @Override // android.view.View.OnClickListener
        public final void onClick(android.view.View view) {
            this.val$activity.finish();
        }
    }

    public static void checkCloudRealMachine(com.android.settings.SettingsPreferenceFragment settingsPreferenceFragment, int i) {
        if (android.os.BuildExt.isCloudRealMachine()) {
            try {
                android.content.Intent intent = new android.content.Intent();
                intent.setAction("meizu.intent.cloud.password");
                intent.setFlags(536870912);
                settingsPreferenceFragment.startActivityForResult(intent, i);
            } catch (java.lang.Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean checkProductModel(java.lang.String str) {
        return android.os.SystemProperties.get("ro.vendor.product.flyme.model", "unknow").equals(str) || android.os.SystemProperties.get("ro.product.flyme.model", "unknown").equals(str);
    }

    public static void clearDevelopmentValue(android.content.Context context) {
        java.lang.String string = android.provider.Settings.Global.getString(context.getContentResolver(), "display_developer_mode_notification");
        if (string != null ? string.contains("density") : false) {
            int i = android.provider.Settings.System.getInt(context.getContentResolver(), "mz_screen_width", -1);
            if (i == -1 || 1080 == i) {
                try {
                    final int iMyUserId = android.os.UserHandle.myUserId();
                    android.os.AsyncTask.execute(new java.lang.Runnable() { // from class: com.android.settingslib.display.DisplayDensityUtils$$ExternalSyntheticLambda1
                        @Override // java.lang.Runnable
                        public final void run() {
                            try {
                                android.view.WindowManagerGlobal.getWindowManagerService().clearForcedDisplayDensityForUser(0, iMyUserId);
                            } catch (android.os.RemoteException unused) {
                                android.util.Log.w("DisplayDensityUtils", "Unable to clear forced display density setting");
                            }
                        }
                    });
                } catch (java.lang.Exception unused) {
                }
            } else if (checkProductModel("M2181") || checkProductModel("M2182")) {
                setForceDensity(context, 640);
            } else if (checkProductModel("M2191") || checkProductModel("M2192")) {
                setForceDensity(context, 586);
            } else if (checkProductModel("M2391") || checkProductModel("M2392")) {
                try {
                    final int iMyUserId2 = android.os.UserHandle.myUserId();
                    android.os.AsyncTask.execute(new java.lang.Runnable() { // from class: com.android.settingslib.display.DisplayDensityUtils$$ExternalSyntheticLambda1
                        @Override // java.lang.Runnable
                        public final void run() {
                            try {
                                android.view.WindowManagerGlobal.getWindowManagerService().clearForcedDisplayDensityForUser(0, iMyUserId2);
                            } catch (android.os.RemoteException unused2) {
                                android.util.Log.w("DisplayDensityUtils", "Unable to clear forced display density setting");
                            }
                        }
                    });
                } catch (java.lang.Exception e) {
                    e.printStackTrace();
                }
            }
        }
        android.provider.Settings.Global.putString(context.getContentResolver(), "display_developer_mode_notification", "");
    }

    public static int compareVersion(java.lang.String str, java.lang.String str2) {
        if (str.equals(str2)) {
            return 0;
        }
        java.lang.String[] strArrSplit = str.split("\\.");
        java.lang.String[] strArrSplit2 = str2.split("\\.");
        android.util.Log.d("test", "version1Array==" + strArrSplit.length);
        androidx.recyclerview.widget.LinearLayoutManager$LayoutState$$ExternalSyntheticOutline0.m(new java.lang.StringBuilder("version2Array=="), strArrSplit2.length, "test");
        int iMin = java.lang.Math.min(strArrSplit.length, strArrSplit2.length);
        int i = 0;
        int i2 = 0;
        while (i < iMin) {
            i2 = java.lang.Integer.parseInt(strArrSplit[i]) - java.lang.Integer.parseInt(strArrSplit2[i]);
            if (i2 != 0) {
                break;
            }
            i++;
        }
        if (i2 != 0) {
            return i2 > 0 ? 1 : -1;
        }
        for (int i3 = i; i3 < strArrSplit.length; i3++) {
            if (java.lang.Integer.parseInt(strArrSplit[i3]) > 0) {
                return 1;
            }
        }
        while (i < strArrSplit2.length) {
            if (java.lang.Integer.parseInt(strArrSplit2[i]) > 0) {
                return -1;
            }
            i++;
        }
        return 0;
    }

    public static void createShortcut(android.content.Context context, java.lang.String str, int i, android.os.Bundle bundle) {
        android.content.pm.ShortcutInfo next;
        android.content.pm.ShortcutManager shortcutManager = (android.content.pm.ShortcutManager) context.getSystemService(android.content.pm.ShortcutManager.class);
        android.content.Intent intentAddCategory = new android.content.Intent("android.intent.action.MAIN").setClass(context, com.meizu.settings.shopdemo.FlymeShopDemoActivity.class).addCategory("android.intent.category.LAUNCHER");
        intentAddCategory.putExtras(bundle);
        intentAddCategory.putExtra("duplicate", false);
        try {
            android.content.pm.ShortcutInfo shortcutInfoBuild = new android.content.pm.ShortcutInfo.Builder(context, str).setShortLabel(str).setIcon(android.graphics.drawable.Icon.createWithResource(context, i)).setIntent(intentAddCategory).build();
            java.util.List<android.content.pm.ShortcutInfo> pinnedShortcuts = shortcutManager.getPinnedShortcuts();
            if (pinnedShortcuts != null && !pinnedShortcuts.isEmpty()) {
                java.util.Iterator<android.content.pm.ShortcutInfo> it = pinnedShortcuts.iterator();
                do {
                    if (!it.hasNext()) {
                        next = null;
                        break;
                    }
                    next = it.next();
                } while (!str.equals(next.getId()));
            } else {
                next = null;
                break;
            }
            if (next == null) {
                android.util.Log.d("Mzutils", "requestPinShortcut");
                shortcutManager.requestPinShortcut(shortcutInfoBuild, null);
            } else {
                java.util.ArrayList arrayList = new java.util.ArrayList(1);
                arrayList.add(shortcutInfoBuild);
                shortcutManager.updateShortcuts(arrayList);
                android.util.Log.d("Mzutils", "updateShortcuts");
            }
        } catch (java.lang.Exception e) {
            com.android.settings.biometrics.fingerprint2.ui.enrollment.modules.enrolling.rfps.ui.fragment.RFPSEnrollFragment$handleEnrollError$1$$ExternalSyntheticOutline0.m("add short cut exception: ", e, "Mzutils");
        }
    }

    public static void doFromSetupwizardAnimation(androidx.fragment.app.FragmentActivity fragmentActivity, boolean z) {
        if (!z || fragmentActivity == null) {
            return;
        }
        fragmentActivity.overridePendingTransition(com.android.settings.R.anim.setup_activity_in_out_animation, com.android.settings.R.anim.setup_activity_in_out_animation);
    }

    public static void downloadFromMStore(android.content.Context context, java.lang.String str, java.lang.String str2, java.lang.String str3) {
        java.lang.StringBuilder sbM = androidx.activity.result.ActivityResultRegistry$$ExternalSyntheticOutline0.m("mstore://details?package_name=", str3, "&source_apkname=");
        sbM.append(context.getPackageName());
        android.content.Intent intent = new android.content.Intent("android.intent.action.VIEW", android.net.Uri.parse(sbM.toString()));
        boolean z = context instanceof android.app.Activity;
        if (!z) {
            try {
                intent.addFlags(268435456);
            } catch (android.content.ActivityNotFoundException e) {
                e.printStackTrace();
                if (z) {
                    showDownloadAppDialog(context, str, str2, str3);
                    return;
                }
                return;
            }
        }
        context.startActivity(intent);
    }

    public static void downloadFromMarket(android.content.Context context, java.lang.String str) {
        try {
            context.startActivity(new android.content.Intent("android.intent.action.VIEW", android.net.Uri.parse(androidx.compose.ui.platform.AndroidCompositionLocals_androidKt$$ExternalSyntheticOutline0.m("market://details?id=", str))));
        } catch (android.content.ActivityNotFoundException e) {
            e.printStackTrace();
            showDownloadAppDialog(context, "", "", str);
        }
    }

    public static float dpToPx(android.content.Context context, int i) {
        return androidx.slice.widget.ActionRow$$ExternalSyntheticOutline0.m(context, 1, i);
    }

    public static void fitNavigationBarColorWithUiMode(androidx.fragment.app.FragmentActivity fragmentActivity) {
        if (fragmentActivity == null) {
            return;
        }
        boolean zIsUiModeNight = isUiModeNight(fragmentActivity.getResources().getConfiguration());
        com.meizu.common.util.NavigationBarUtils.setNavigationBarColor(fragmentActivity.getWindow(), fragmentActivity.getResources().getColor(zIsUiModeNight ? com.android.settings.R.color.flyme_confirm_password_navigation_bar_dark_color : com.android.settings.R.color.flyme_confirm_password_navigation_bar_light_color, null), zIsUiModeNight);
    }

    public static void fitNavigationBarForBottomBar(android.view.View view) {
        if (view != null) {
            view.setOnApplyWindowInsetsListener(new com.meizu.settings.utils.MzUtils$$ExternalSyntheticLambda0());
        }
    }

    public static void fitStatusBarWithUiMode(android.app.Activity activity) {
        setStatusBarLightText(activity, ((android.app.UiModeManager) activity.getSystemService(android.app.UiModeManager.class)).getNightMode() == 2);
    }

    /* JADX WARN: Code restructure failed: missing block: B:20:0x0049, code lost:
    
        if (r5.lastIndexOf(r0) == (r5.length() - 1)) goto L21;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static java.lang.String formatStringPunctuation(android.content.Context r4, java.lang.String r5) {
        /*
            if (r4 == 0) goto L5c
            boolean r0 = android.text.TextUtils.isEmpty(r5)
            if (r0 == 0) goto L9
            goto L5c
        L9:
            java.util.Locale r0 = java.util.Locale.getDefault()
            java.lang.String r0 = r0.getLanguage()
            java.util.Locale r1 = java.util.Locale.CHINESE
            java.lang.String r1 = r1.getLanguage()
            boolean r0 = r0.equals(r1)
            if (r0 == 0) goto L21
            java.lang.String r0 = "。"
            goto L23
        L21:
            java.lang.String r0 = "."
        L23:
            android.content.res.Resources r4 = r4.getResources()
            android.content.res.Configuration r4 = r4.getConfiguration()
            int r4 = r4.getLayoutDirection()
            r1 = 0
            r2 = 1
            if (r4 != r2) goto L35
            r4 = r2
            goto L36
        L35:
            r4 = r1
        L36:
            if (r4 == 0) goto L40
            int r0 = r5.indexOf(r0)
            if (r0 != 0) goto L3f
            goto L4b
        L3f:
            return r5
        L40:
            int r0 = r5.lastIndexOf(r0)
            int r3 = r5.length()
            int r3 = r3 - r2
            if (r0 != r3) goto L5c
        L4b:
            if (r4 == 0) goto L52
            java.lang.String r4 = r5.substring(r2)
            return r4
        L52:
            int r4 = r5.length()
            int r4 = r4 - r2
            java.lang.String r4 = r5.substring(r1, r4)
            return r4
        L5c:
            return r5
        */
        throw new UnsupportedOperationException("Method not decompiled: com.meizu.settings.utils.MzUtils.formatStringPunctuation(android.content.Context, java.lang.String):java.lang.String");
    }

    public static int[] getArray(android.content.Context context, int i) {
        int[] iArr = null;
        try {
            android.content.res.TypedArray typedArrayObtainTypedArray = context.getResources().obtainTypedArray(i);
            try {
                iArr = new int[10];
                for (int i2 = 0; i2 < 10; i2++) {
                    iArr[i2] = typedArrayObtainTypedArray.getResourceId(i2, 0);
                }
                if (typedArrayObtainTypedArray == null) {
                    return iArr;
                }
                typedArrayObtainTypedArray.close();
                return iArr;
            } catch (java.lang.Throwable th) {
                if (typedArrayObtainTypedArray != null) {
                    try {
                        typedArrayObtainTypedArray.close();
                    } catch (java.lang.Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        } catch (java.lang.Exception e) {
            android.util.Log.d("MzUtils", "getArray error.", e);
            return iArr;
        }
    }

    public static int getAttrColorFromTheme(android.content.Context context, int i) {
        try {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(i, typedValue, true);
            java.lang.String resourceName = context.getResources().getResourceName(typedValue.resourceId);
            int color = context.getResources().getColor(typedValue.resourceId, context.getTheme());
            android.util.Log.d("MzUtils", "attr resource name: " + resourceName + ", value: " + java.lang.Integer.toHexString(color));
            return color;
        } catch (java.lang.Throwable th) {
            android.util.Log.d("MzUtils", "getAttrColorFromTheme error.", th);
            return 0;
        }
    }

    public static android.content.res.ColorStateList getAttrColorStateListFromTheme(android.content.Context context) {
        try {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(com.android.settings.R.attr.colorOnSurface, typedValue, true);
            java.lang.String resourceName = context.getResources().getResourceName(typedValue.resourceId);
            android.content.res.ColorStateList colorStateList = context.getResources().getColorStateList(typedValue.resourceId, context.getTheme());
            android.util.Log.d("MzUtils", "attr resource name: " + resourceName + ", value: " + colorStateList);
            return colorStateList;
        } catch (java.lang.Throwable th) {
            android.util.Log.d("MzUtils", "getAttrColorStateListFromTheme error.", th);
            return null;
        }
    }

    public static int getAttrDimenFromTheme(android.content.Context context, int i) {
        float dimension;
        try {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(i, typedValue, true);
            java.lang.String resourceName = context.getResources().getResourceName(typedValue.resourceId);
            dimension = context.getResources().getDimension(typedValue.resourceId);
            android.util.Log.d("MzUtils", "attr resource name: " + resourceName + ", value: " + dimension);
        } catch (java.lang.Throwable th) {
            android.util.Log.d("MzUtils", "getAttrDimensionFromTheme error.", th);
            dimension = 0.0f;
        }
        return (int) dimension;
    }

    public static int[] getColors(android.content.Context context, int i) {
        java.lang.CharSequence[] textArray = context.getResources().getTextArray(i);
        try {
            int[] iArr = new int[textArray.length];
            for (int i2 = 0; i2 < textArray.length; i2++) {
                iArr[i2] = android.graphics.Color.parseColor(textArray[i2].toString());
            }
            return iArr;
        } catch (java.lang.Exception e) {
            android.util.Log.d("MzUtils", "getColors error", e);
            return null;
        }
    }

    public static int getCompareResult(java.lang.String str, java.lang.String str2) {
        java.text.Collator collator = java.text.Collator.getInstance();
        if (str == null && str2 != null) {
            return -1;
        }
        if (str != null && str2 == null) {
            return 1;
        }
        if (str != null && str2 != null && str.length() > 0 && str2.length() > 0) {
            if (!android.text.TextUtils.isEmpty(str) && !android.text.TextUtils.isEmpty(str2)) {
                return collator.compare(str, str2);
            }
            if (!android.text.TextUtils.isEmpty(str)) {
                return 1;
            }
            android.text.TextUtils.isEmpty(str2);
        }
        return -1;
    }

    public static int getCompareResultWithNumber(java.lang.String str, java.lang.String str2) {
        java.text.Collator collator = java.text.Collator.getInstance();
        if (android.text.TextUtils.isEmpty(str) || android.text.TextUtils.isEmpty(str2)) {
            if (android.text.TextUtils.isEmpty(str)) {
                return !android.text.TextUtils.isEmpty(str2) ? -1 : 0;
            }
            return 1;
        }
        java.lang.String strSubstring = str.substring(0, 1);
        java.lang.String strSubstring2 = str2.substring(0, 1);
        if (!"1234567890".contains(strSubstring) && "1234567890".contains(strSubstring2)) {
            return -1;
        }
        if (!"1234567890".contains(strSubstring) || "1234567890".contains(strSubstring2)) {
            return collator.compare(str, str2);
        }
        return 1;
    }

    public static java.lang.String getCurrentFunction(android.content.Context context) {
        android.hardware.usb.UsbManager usbManager;
        if (context == null || (usbManager = (android.hardware.usb.UsbManager) context.getSystemService("usb")) == null) {
            return "none";
        }
        java.lang.String strUsbFunctionsToString = android.hardware.usb.UsbManager.usbFunctionsToString(usbManager.getCurrentFunctions());
        int iIndexOf = strUsbFunctionsToString.indexOf(44);
        if (iIndexOf > 0) {
            return strUsbFunctionsToString.substring(0, iIndexOf);
        }
        return (android.text.TextUtils.isEmpty(strUsbFunctionsToString) || "adb".equals(strUsbFunctionsToString) || ("mtp".equals(strUsbFunctionsToString) && "yes".equals(android.os.SystemProperties.get("sys.usb.charging", "no")))) ? "none" : strUsbFunctionsToString;
    }

    public static java.util.ArrayList getFastIndexLetter(androidx.fragment.app.FragmentActivity fragmentActivity) {
        java.lang.String string = fragmentActivity.getResources().getString(com.android.settings.R.string.fast_scroll_alphabet);
        java.util.ArrayList arrayList = new java.util.ArrayList();
        for (int i = 0; i < string.length(); i++) {
            arrayList.add(java.lang.String.valueOf(string.charAt(i)));
        }
        return arrayList;
    }

    public static android.content.pm.ShortcutInfo getShortcutInfo(android.content.Context context, java.lang.String str, java.lang.String str2, int i) {
        android.content.pm.LauncherApps launcherApps = (android.content.pm.LauncherApps) context.getSystemService(android.content.pm.LauncherApps.class);
        int i2 = ("com.eg.android.AlipayGphone".equals(str) || "com.tencent.mm".equals(str)) ? 1051 : com.android.settings.core.PreferenceXmlParserUtils.MetadataFlag.FLAG_NEED_PREF_APPEND;
        android.content.pm.LauncherApps.ShortcutQuery shortcutQuery = new android.content.pm.LauncherApps.ShortcutQuery();
        shortcutQuery.setPackage(str).setShortcutIds(java.util.List.of(str2)).setQueryFlags(i2);
        java.util.List<android.content.pm.ShortcutInfo> shortcuts = launcherApps.getShortcuts(shortcutQuery, android.os.UserHandle.getUserHandleForUid(i));
        if (shortcuts == null || shortcuts.size() <= 0) {
            return null;
        }
        return shortcuts.get(0);
    }

    public static java.lang.String getStringFromOtherPackage(android.content.Context context, java.lang.String str) {
        android.content.res.Resources resourcesForApplication;
        int identifier;
        try {
            resourcesForApplication = context.getPackageManager().getResourcesForApplication("com.android.providers.settings");
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            resourcesForApplication = null;
        }
        return (resourcesForApplication == null || (identifier = resourcesForApplication.getIdentifier(str, "string", "com.android.providers.settings")) <= 0) ? "" : resourcesForApplication.getString(identifier);
    }

    public static java.lang.String getVirtualImei() {
        java.lang.String str = (new java.util.Random().nextInt(9000000) + kotlin.time.DurationKt.NANOS_IN_MILLIS) + "" + (new java.util.Random().nextInt(9000000) + kotlin.time.DurationKt.NANOS_IN_MILLIS);
        char[] charArray = str.toCharArray();
        int i = 0;
        int i2 = 0;
        for (int i3 = 0; i3 < charArray.length; i3++) {
            int i4 = java.lang.Integer.parseInt(charArray[i3] + "");
            if (i3 % 2 == 0) {
                i += i4;
            } else {
                int i5 = i4 * 2;
                i2 = (i5 % 10) + (i5 / 10) + i2;
            }
        }
        int i6 = (i + i2) % 10;
        return str + (i6 != 0 ? 10 - i6 : 0);
    }

    public static java.lang.String getVirtualSn() {
        java.util.Random random = new java.util.Random();
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + random.nextInt(10000);
    }

    public static void hideActionbar(androidx.fragment.app.FragmentActivity fragmentActivity) {
        flyme.support.v7.app.ActionBar supportActionBar = fragmentActivity instanceof flyme.support.v7.app.AppCompatActivity ? ((flyme.support.v7.app.AppCompatActivity) fragmentActivity).getSupportActionBar() : null;
        if (supportActionBar != null) {
            supportActionBar.hide();
        }
        fragmentActivity.getWindow().clearFlags(67108864);
    }

    public static void hideNavigationBar(flyme.support.v7.app.AlertDialog alertDialog) {
        alertDialog.getWindow().getDecorView().setSystemUiVisibility(2);
    }

    public static void installAppFromMzStore(android.content.Context context, java.lang.String str) {
        try {
            android.content.Intent intent = new android.content.Intent("android.intent.action.VIEW", android.net.Uri.parse("market://details?id=".concat(str)));
            intent.setPackage(com.meizu.settings.Constants$PackageName.MZ_MSTORE);
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static boolean isActivityActionExist(android.content.Context context, java.lang.String str) {
        java.util.List<android.content.pm.ResolveInfo> listQueryIntentActivities = context.getPackageManager().queryIntentActivities(new android.content.Intent(str), 65536);
        return (listQueryIntentActivities == null || listQueryIntentActivities.size() == 0) ? false : true;
    }

    public static boolean isArabicLanguage() {
        return "ar".equals(java.util.Locale.getDefault().getLanguage());
    }

    public static boolean isAuthorityExist(android.content.Context context, android.net.Uri uri) {
        return context.getContentResolver().acquireProvider(uri.getAuthority()) != null;
    }

    public static boolean isCTA() {
        return android.os.SystemProperties.get("ro.vendor.build.cta", "unknown").equals("cta");
    }

    public static boolean isChineseChar(java.lang.String str) {
        return str.charAt(0) >= 19968 && str.charAt(0) <= 40869;
    }

    public static boolean isChineseOrEnglishLanguage() {
        java.lang.String language = java.util.Locale.getDefault().getLanguage();
        return java.util.Locale.ENGLISH.getLanguage().equals(language) || java.util.Locale.CHINESE.getLanguage().equals(language);
    }

    public static boolean isCtsRunning() {
        try {
            if (com.meizu.framework.FlymeFwkDependency$WifiInfo.m1260getInstance() != null) {
                return com.meizu.framework.FlymeFwkDependency$WifiInfo.m1260getInstance().isCtsRunning();
            }
            return false;
        } catch (android.os.RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isEasyModeOpened(android.content.Context context) {
        return android.provider.Settings.System.getInt(context.getContentResolver(), "mz_easy_mode", 0) == 1;
    }

    public static boolean isFreeformWindow(android.app.Activity activity) {
        if (activity == null) {
            return false;
        }
        return flyme.view.WindowManagerExt.getInstance(activity.getApplicationContext()).isWindowMode(11, activity.getActivityToken());
    }

    public static boolean isGuestUser(android.content.Context context) {
        return ((android.os.UserManager) context.getSystemService("user")).isGuestUser();
    }

    public static boolean isIntentResolvable(android.content.Context context, android.content.Intent intent) {
        return (context == null || intent == null || context.getPackageManager().resolveActivity(intent, 0) == null) ? false : true;
    }

    public static boolean isPackageDataExist(android.content.Context context, java.lang.String str) {
        try {
            context.getPackageManager().getPackageInfo(str, 8192);
            return true;
        } catch (java.lang.Exception unused) {
            return false;
        }
    }

    public static boolean isPackageExist(android.content.Context context, java.lang.String str) {
        try {
            context.getPackageManager().getPackageInfo(str, 0);
            return true;
        } catch (java.lang.Exception unused) {
            return false;
        }
    }

    public static boolean isPackageExistAndHasAction(android.content.Context context, java.lang.String str, java.lang.String str2) {
        if (!isPackageExist(context, str)) {
            return false;
        }
        java.util.Iterator<android.content.pm.ResolveInfo> it = context.getPackageManager().queryIntentActivities(new android.content.Intent(str2), 65536).iterator();
        while (it.hasNext()) {
            android.content.pm.ActivityInfo activityInfo = it.next().activityInfo;
            if (activityInfo != null && android.text.TextUtils.equals(activityInfo.packageName, str)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPinnedWindow(android.app.Activity activity) {
        if (activity == null) {
            return false;
        }
        return flyme.view.WindowManagerExt.getInstance(activity.getApplicationContext()).isWindowMode(12, activity.getActivityToken());
    }

    public static boolean isPolestarPhone() {
        return android.os.SystemProperties.get("ro.flyme.build.channel", "unknown").trim().equals("polestar");
    }

    public static boolean isSystemApp(android.content.pm.ApplicationInfo applicationInfo) {
        return (applicationInfo.flags & 129) != 0 && applicationInfo.uid == 1000;
    }

    public static boolean isSystemPackage(android.content.Context context, java.lang.String str) {
        try {
            android.content.pm.ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(str, 0);
            return applicationInfo != null && (applicationInfo.isSystemApp() || applicationInfo.isPrivilegedApp() || (applicationInfo.privateFlags & 65536) != 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isSystemPackageCanUninstall(android.content.pm.ApplicationInfo applicationInfo) {
        return applicationInfo == null || (applicationInfo.privateFlags & 65536) != 0;
    }

    public static boolean isThaiLanguage() {
        return "th".equals(java.util.Locale.getDefault().getLanguage());
    }

    public static boolean isUiModeNight(android.content.res.Configuration configuration) {
        return (configuration.uiMode & 48) == 32;
    }

    public static android.os.Bundle makeOpenTaskAnimationToNextPageBundle(android.content.Context context) {
        return android.app.ActivityOptions.makeCustomTaskAnimation(context, com.android.settings.R.anim.mz_activity_to_next_open_enter, com.android.settings.R.anim.mz_activity_to_next_open_exit, null, null, null).toBundle();
    }

    public static void reinstallFileManager(androidx.fragment.app.FragmentActivity fragmentActivity) {
        if (fragmentActivity == null || fragmentActivity.isFinishing() || fragmentActivity.isDestroyed()) {
            android.util.Log.d("MzUtils", "reinstall FileManager invalid activity state: " + fragmentActivity);
        } else {
            android.content.Intent intent = new android.content.Intent();
            intent.setAction(com.meizu.settings.Constants$Action.RESTORE_MZ_APP);
            intent.addCategory("android.intent.category.DEFAULT");
            intent.putExtra("package", com.meizu.settings.Constants$PackageName.FILE_MANAGER);
            fragmentActivity.startActivity(intent);
        }
    }

    public static void reinstallMeizuApp(android.content.Context context, android.content.Intent intent, java.lang.String str) {
        boolean z;
        boolean z2 = true;
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            z = false;
            if (activity.isFinishing() || activity.isDestroyed()) {
                z2 = false;
            }
        } else {
            z = true;
        }
        if (!z2) {
            android.util.Log.d("MzUtils", "reinstallMeizuPay invalid activity state: " + context);
            return;
        }
        android.content.Intent intent2 = new android.content.Intent();
        intent2.setAction(com.meizu.settings.Constants$Action.RESTORE_MZ_APP);
        intent2.addCategory("android.intent.category.DEFAULT");
        intent2.putExtra("package", str);
        if (intent != null) {
            intent2.putExtra("mz_app_intent", intent);
        }
        if (z) {
            intent2.addFlags(268435456);
        }
        context.startActivity(intent2);
    }

    public static void sendFactoryResetBroadcast(android.content.Context context) {
        android.content.Intent intent = new android.content.Intent("android.intent.action.FACTORY_RESET");
        if (android.os.BuildExt.IS_M20x1.booleanValue()) {
            intent = new android.content.Intent("flyme.intent.action.MZ_WIPE_ALL");
        }
        intent.setPackage(com.android.settingslib.applications.RecentAppOpsAccess.ANDROID_SYSTEM_PACKAGE_NAME);
        intent.addFlags(268435456);
        intent.putExtra("android.intent.extra.REASON", "MasterClearConfirm");
        context.sendBroadcast(intent);
    }

    public static void setForceDensity(android.content.Context context, int i) {
        com.android.settingslib.display.DisplayDensityUtils displayDensityUtils = new com.android.settingslib.display.DisplayDensityUtils(context, -1, -1, -1);
        android.provider.Settings.Secure.putInt(context.getContentResolver(), "display_density", i);
        com.android.settingslib.display.DisplayDensityUtils displayDensityUtils2 = new com.android.settingslib.display.DisplayDensityUtils(context, 1440, 3200, i);
        int i2 = displayDensityUtils.mCurrentIndex;
        if (i2 == 0) {
            i = displayDensityUtils2.mValues[0];
        } else if (i2 == 2) {
            i = displayDensityUtils2.mValues[2];
        }
        com.android.settingslib.display.DisplayDensityUtils.setForcedDisplayDensity(i);
    }

    public static void setNavigationBarDarkIcon(android.app.Activity activity, boolean z) {
        if (activity == null) {
            return;
        }
        android.view.WindowInsetsController insetsController = activity.getWindow().getInsetsController();
        if (z) {
            insetsController.setSystemBarsAppearance(16, 16);
        } else {
            insetsController.setSystemBarsAppearance(0, 16);
        }
    }

    public static void setOnPowerModeChangeListener(android.content.Context context, android.database.ContentObserver contentObserver, boolean z) {
        if (!z) {
            context.getContentResolver().unregisterContentObserver(contentObserver);
        } else {
            context.getContentResolver().registerContentObserver(android.provider.Settings.Secure.getUriFor("mz_current_power_mode"), false, contentObserver);
        }
    }

    public static void setStatusBarDarkIcon(androidx.fragment.app.FragmentActivity fragmentActivity, boolean z) {
        if (fragmentActivity == null) {
            return;
        }
        android.view.WindowInsetsController insetsController = fragmentActivity.getWindow().getInsetsController();
        if (z) {
            insetsController.setSystemBarsAppearance(8, 8);
        } else {
            insetsController.setSystemBarsAppearance(0, 8);
        }
    }

    public static void setStatusBarLightText(android.app.Activity activity, boolean z) {
        android.view.WindowInsetsController insetsController = activity.getWindow().getInsetsController();
        insetsController.setSystemBarsAppearance(z ? 0 : 8, 8);
        insetsController.setSystemBarsAppearance(z ? 0 : 16, 16);
    }

    public static void setViewPaddingBottom(android.content.Context context, android.view.View view) {
        if (view == null || context == null) {
            return;
        }
        view.setPadding(view.getPaddingStart(), view.getPaddingTop(), view.getPaddingEnd(), context.getResources().getDimensionPixelSize(com.android.settings.R.dimen.listview_padding_bottom));
    }

    public static void showDownloadAppDialog(final android.content.Context context, java.lang.String str, java.lang.String str2, final java.lang.String str3) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context, 5);
        builder.setTitle(str);
        builder.setMessage(str2);
        builder.setNegativeButton(com.android.settings.R.string.cancel, (android.content.DialogInterface.OnClickListener) null);
        builder.setPositiveButton(com.android.settings.R.string.ok, new android.content.DialogInterface.OnClickListener() { // from class: com.meizu.settings.utils.MzUtils.2
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(android.content.DialogInterface dialogInterface, int i) {
                java.lang.String str4;
                if (android.os.BuildExt.isProductInternational()) {
                    str4 = com.meizu.settings.Constants$HTTP.URL_GOOGLEPLAY_APP_DOWNLOAD_BASE_URI;
                } else {
                    str4 = com.meizu.settings.Constants$HTTP.URL_APP_DOWNLOAD_BASE_URI + str3;
                }
                context.startActivity(new android.content.Intent("android.intent.action.VIEW", android.net.Uri.parse(str4)));
            }
        }).show();
    }

    public static void showFlymeDrawableToast(androidx.fragment.app.FragmentActivity fragmentActivity, java.lang.String str, android.graphics.drawable.Drawable drawable) {
        android.view.ContextThemeWrapper contextThemeWrapper = new android.view.ContextThemeWrapper(fragmentActivity.getApplicationContext(), com.android.settings.R.style.Theme_Settings);
        com.meizu.common.widget.CompleteToast completeToast = new com.meizu.common.widget.CompleteToast(contextThemeWrapper);
        completeToast.init(contextThemeWrapper, str, drawable);
        completeToast.setDuration(0);
        completeToast.show();
    }

    public static void showFlymeToast(android.content.Context context, int i, java.lang.String str, int i2) {
        if (context == null) {
            return;
        }
        android.view.ContextThemeWrapper contextThemeWrapper = new android.view.ContextThemeWrapper(context.getApplicationContext(), context.getTheme());
        if (!android.text.TextUtils.isEmpty(str)) {
            com.meizu.common.widget.CompleteToast completeToast = new com.meizu.common.widget.CompleteToast(contextThemeWrapper);
            completeToast.init(contextThemeWrapper, str, null);
            completeToast.setDuration(i2);
            completeToast.show();
            return;
        }
        if (i > 0) {
            com.meizu.common.widget.CompleteToast completeToast2 = new com.meizu.common.widget.CompleteToast(contextThemeWrapper);
            completeToast2.init(contextThemeWrapper, contextThemeWrapper.getResources().getText(i), null);
            completeToast2.setDuration(i2);
            completeToast2.show();
        }
    }

    public static com.meizu.common.app.LoadingDialog showLoadingDialog(android.content.Context context, int i, int i2) {
        com.meizu.common.app.LoadingDialog loadingDialog = new com.meizu.common.app.LoadingDialog(context);
        if (i > 0) {
            loadingDialog.setTitle(i);
        } else if (!android.text.TextUtils.isEmpty(null)) {
            loadingDialog.setTitle((java.lang.CharSequence) null);
        }
        if (i2 > 0) {
            loadingDialog.mMessage = loadingDialog.mContext.getResources().getString(i2);
            loadingDialog.updateMessageView();
        } else if (!android.text.TextUtils.isEmpty(null)) {
            loadingDialog.mMessage = null;
            loadingDialog.updateMessageView();
        }
        loadingDialog.setCancelable(false);
        loadingDialog.setOnCancelListener(null);
        loadingDialog.show();
        return loadingDialog;
    }

    public static void showSlideNotice(android.content.Context context, int i) {
        if (context == null) {
            return;
        }
        showSlideNotice(context, context.getString(i));
    }

    public static void startShakeAnim(android.view.View view) {
        android.animation.ObjectAnimator.ofFloat(view, "translationX", 0.0f, 5.6f, 17.9f, 30.2f, 35.8f, 26.4f, 5.7f, -14.9f, -24.3f, -19.6f, -8.4f, 4.9f, 16.1f, 20.8f, 17.0f, 8.1f, -2.6f, -11.5f, -15.3f, -13.4f, -8.7f, -2.6f, 3.4f, 8.1f, 10.0f, 9.6f, 8.4f, 6.8f, 5.0f, 3.2f, 1.6f, 0.4f, 0.0f).setDuration(550L).start();
    }

    public static boolean supportRaiseFlipFeature(android.content.Context context) {
        android.content.res.Resources resources = context.getResources();
        int integer = resources.getInteger(resources.getIdentifier("config_flipSensorType", "integer", com.android.settingslib.applications.RecentAppOpsAccess.ANDROID_SYSTEM_PACKAGE_NAME));
        int integer2 = resources.getInteger(resources.getIdentifier("config_raiseSensorType", "integer", com.android.settingslib.applications.RecentAppOpsAccess.ANDROID_SYSTEM_PACKAGE_NAME));
        android.hardware.SensorManager sensorManager = (android.hardware.SensorManager) context.getSystemService("sensor");
        androidx.exifinterface.media.ExifInterface$$ExternalSyntheticOutline0.m("supportRaiseFlipFeature flip id= ", integer, integer2, ", raise id= ", "MzUtils");
        return (sensorManager.getDefaultSensor(integer, true) == null || sensorManager.getDefaultSensor(integer2, true) == null) ? false : true;
    }

    public static boolean supportedChangeRefreshRate(android.view.Display.Mode[] modeArr) {
        if (modeArr != null && modeArr.length >= 2) {
            float refreshRate = modeArr[0].getRefreshRate();
            for (android.view.Display.Mode mode : modeArr) {
                if (refreshRate != mode.getRefreshRate()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean supportedChangeResolution(android.view.Display.Mode[] modeArr) {
        if (modeArr != null && modeArr.length >= 2) {
            int physicalWidth = modeArr[0].getPhysicalWidth();
            for (android.view.Display.Mode mode : modeArr) {
                if (physicalWidth != mode.getPhysicalWidth()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void transparentNavigationBar(android.app.Activity activity) {
        if (activity == null || activity.getWindow() == null) {
            return;
        }
        android.view.Window window = activity.getWindow();
        window.setNavigationBarContrastEnforced(false);
        window.setNavigationBarColor(0);
        android.view.View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() | 1792);
    }

    public static void showSlideNotice(android.content.Context context, java.lang.String str) {
        if (context == null || str == null) {
            return;
        }
        com.meizu.common.app.SlideNotice slideNotice = new com.meizu.common.app.SlideNotice();
        slideNotice.mContext = context;
        com.meizu.common.app.SlideNotice.SlideViewController slideViewController = new com.meizu.common.app.SlideNotice.SlideViewController();
        android.view.WindowManager.LayoutParams layoutParams = new android.view.WindowManager.LayoutParams();
        new com.meizu.common.app.SlideNotice.NoticeHandler().mNotice = new java.lang.ref.WeakReference(slideNotice);
        android.view.View viewInflate = android.view.LayoutInflater.from(slideNotice.mContext).inflate(com.android.settings.R.layout.mc_slide_notice_content, (android.view.ViewGroup) null);
        slideViewController.mSlideContent = viewInflate;
        slideViewController.mSlideContent.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, -2));
        com.meizu.common.util.ResourceUtils.getStatusBarHeight(slideNotice.mContext);
        android.content.Context context2 = slideNotice.mContext;
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (context2.getTheme().resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            android.util.TypedValue.complexToDimensionPixelSize(typedValue.data, context2.getResources().getDisplayMetrics());
        }
        slideViewController.new SlidingHideListener();
        slideViewController.new SlidingShowListener();
        android.content.Context applicationContext = slideNotice.mContext.getApplicationContext();
        if (applicationContext != null) {
        }
        layoutParams.height = -2;
        layoutParams.width = -1;
        layoutParams.format = -3;
        layoutParams.setTitle("SlideNotice:" + java.lang.Integer.toHexString(slideViewController.hashCode()));
        layoutParams.flags = 40;
        android.content.Context context3 = slideNotice.mContext;
        if (!(context3 instanceof android.app.Activity)) {
            context3.getResources().getDimensionPixelSize(com.android.settings.R.dimen.mz_action_bar_default_height);
        }
        slideNotice.mSlideViewController = slideViewController;
        android.widget.Toast toastMakeText = android.widget.Toast.makeText(context, str, 1);
        context.getResources().getColor(com.android.settings.R.color.blue);
        slideNotice.mSlideViewController.getClass();
        context.getResources().getColor(com.android.settings.R.color.blue);
        slideNotice.mSlideViewController.getClass();
        if (toastMakeText != null) {
            toastMakeText.show();
        }
    }
}
