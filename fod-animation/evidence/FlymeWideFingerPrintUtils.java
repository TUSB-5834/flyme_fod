package com.flyme.systemui.biometrics;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import com.android.systemui.Dependency;
import com.android.systemui.assist.ui.DisplayUtils;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.res.R$drawable;
import com.flyme.statusbar.space.MSpaceControllerImpl;
import com.flyme.systemui.statusbar.CentralSurfaceUtil;
import com.flyme.systemuitools.windowmode.IQuickPayHelper;
import com.meizu.common.widget.MzContactsContract;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* JADX INFO: loaded from: classes5.dex */
public abstract class FlymeWideFingerPrintUtils {
    public static Drawable drawableCacheLeft;
    public static Drawable drawableCacheRight;
    public static IQuickPayHelper iQuickPayHelper;
    public static boolean isInCenterSensorBounds;
    public static boolean isInLeftSensorBounds;
    public static boolean isInRightSensorBounds;
    public static boolean isLeftRightIconToGrowing;
    public static Rect mCenterSensorBounds = new Rect();
    public static Rect mLeftSensorBounds = new Rect();
    public static Rect mRightSensorBounds = new Rect();
    public static Rect mBoundsForLockPalmRejection = new Rect();
    public static Handler handler = new Handler(Looper.getMainLooper());

    public interface OnDrawableCallbackListener {
        void onDrawableCallback(Drawable drawable);
    }

    public static boolean isSupportedWideFp() {
        return false;
    }

    public static boolean isCommonUnLockMode(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), "fingerprint_unlock_mode", 0) == 0;
    }

    public static boolean isFingerprintLargeMode(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), "fingerprint_icon_size", 1) == 1;
    }

    public static boolean isFingerprintMaxMode(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), "fingerprint_icon_size", 1) == 2;
    }

    public static boolean shouldShowLeftRightIcon(Context context) {
        boolean zIsSupportedWideFp = isSupportedWideFp();
        boolean zIsCommonUnLockMode = isCommonUnLockMode(context);
        boolean zIsBouncerShowing = ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().isBouncerShowing();
        boolean zIsUserUnlocked = ((UserManager) context.getSystemService(UserManager.class)).isUserUnlocked();
        boolean z = zIsSupportedWideFp && !isCommonUnLockMode(context) && !((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().isBouncerShowing() && zIsUserUnlocked;
        Log.d("FlymeWideFingerPrintUtils", "shouldShowLeftRightIcon():" + z + " supportedWideFp:" + zIsSupportedWideFp + " isCommonUnLockMode:" + zIsCommonUnLockMode + " bouncerShowing:" + zIsBouncerShowing + " isUserUnlocked:" + zIsUserUnlocked);
        return z;
    }

    public static boolean shouldShowLargeFpIcon(Context context) {
        boolean zIsSupportedWideFp = isSupportedWideFp();
        boolean zIsCommonUnLockMode = isCommonUnLockMode(context);
        boolean zIsFingerprintLargeMode = isFingerprintLargeMode(context);
        boolean zIsBouncerShowing = ((CentralSurfaceUtil) Dependency.get(CentralSurfaceUtil.class)).getCentralSurfacesImpl().isBouncerShowing();
        boolean z = zIsSupportedWideFp && ((zIsCommonUnLockMode && zIsFingerprintLargeMode) || (!zIsCommonUnLockMode && zIsBouncerShowing));
        Log.d("FlymeWideFingerPrintUtils", "shouldShowLargeFpIcon():" + z + " supportedWideFp:" + zIsSupportedWideFp + " isCommonUnLockMode:" + zIsCommonUnLockMode + " isLargeMode:" + zIsFingerprintLargeMode + " bouncerShowing:" + zIsBouncerShowing);
        return z;
    }

    public static boolean shouldShowMaxFpIcon(Context context) {
        boolean zIsSupportedWideFp = isSupportedWideFp();
        boolean zIsFingerprintMaxMode = isFingerprintMaxMode(context);
        boolean z = zIsSupportedWideFp && zIsFingerprintMaxMode;
        Log.d("FlymeWideFingerPrintUtils", "shouldShowMaxFpIcon():" + z + " supportedWideFp:" + zIsSupportedWideFp + " isMaxMode:" + zIsFingerprintMaxMode);
        return z;
    }

    public static boolean shouldShowLargeFpIconForSetting(Context context) {
        boolean zIsSupportedWideFp = isSupportedWideFp();
        boolean zIsCommonUnLockMode = isCommonUnLockMode(context);
        boolean zIsFingerprintLargeMode = isFingerprintLargeMode(context);
        boolean z = zIsSupportedWideFp && ((zIsCommonUnLockMode && zIsFingerprintLargeMode) || !zIsCommonUnLockMode);
        Log.d("FlymeWideFingerPrintUtils", "shouldShowLargeFpIconForSetting():" + z + " supportedWideFp:" + zIsSupportedWideFp + " isCommonUnLockMode:" + zIsCommonUnLockMode + " isLargeMode:" + zIsFingerprintLargeMode);
        return z;
    }

    public static void initSensorBounds(final View view, final View view2, final View view3, final Context context) {
        handler.post(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda11
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.m14672$r8$lambda$cv71J6ebLSanP6f1DYf5G_EkDk(view, context, view2, view3);
            }
        });
    }

    /* JADX INFO: renamed from: $r8$lambda$cv71J6ebLSanP6f1-DYf5G_EkDk, reason: not valid java name */
    public static /* synthetic */ void m14672$r8$lambda$cv71J6ebLSanP6f1DYf5G_EkDk(View view, Context context, View view2, View view3) {
        int iConvertDpToPx;
        int iConvertDpToPx2;
        if (view != null) {
            int[] iArr = new int[2];
            view.getLocationOnScreen(iArr);
            int i = iArr[0];
            int i2 = iArr[1];
            mCenterSensorBounds.set(i, i2, view.getWidth() + i, view.getHeight() + i2);
            int iCenterX = mCenterSensorBounds.centerX();
            int iCenterY = mCenterSensorBounds.centerY();
            if (isCommonUnLockMode(context)) {
                if (isFingerprintMaxMode(context)) {
                    iConvertDpToPx = DisplayUtils.convertDpToPx(104.0f, context);
                    iConvertDpToPx2 = DisplayUtils.convertDpToPx(70.0f, context);
                } else if (isFingerprintLargeMode(context)) {
                    iConvertDpToPx = DisplayUtils.convertDpToPx(75.0f, context);
                    iConvertDpToPx2 = DisplayUtils.convertDpToPx(60.0f, context);
                } else {
                    int iConvertDpToPx3 = DisplayUtils.convertDpToPx(50.0f, context);
                    iConvertDpToPx2 = DisplayUtils.convertDpToPx(50.0f, context);
                    iConvertDpToPx = iConvertDpToPx3;
                }
            } else {
                iConvertDpToPx = DisplayUtils.convertDpToPx(90.0f, context);
                iConvertDpToPx2 = DisplayUtils.convertDpToPx(60.0f, context);
            }
            mBoundsForLockPalmRejection.set(iCenterX - iConvertDpToPx, iCenterY - iConvertDpToPx2, iCenterX + iConvertDpToPx, iCenterY + iConvertDpToPx2);
            Log.d("FlymeWideFingerPrintUtils", "initSensorBounds: mBoundsForLockPalmRejection = " + mBoundsForLockPalmRejection);
        }
        if (view2 != null) {
            int[] iArr2 = new int[2];
            view2.getLocationOnScreen(iArr2);
            int i3 = iArr2[0];
            int i4 = iArr2[1];
            mLeftSensorBounds.set(i3, i4, view2.getWidth() + i3, view2.getHeight() + i4);
        }
        if (view3 != null) {
            int[] iArr3 = new int[2];
            view3.getLocationOnScreen(iArr3);
            int i5 = iArr3[0];
            int i6 = iArr3[1];
            mRightSensorBounds.set(i5, i6, view3.getWidth() + i5, view3.getHeight() + i6);
        }
        Log.d("FlymeWideFingerPrintUtils", "initSensorBounds() mCenterSensorBounds:" + mCenterSensorBounds + " mLeftSensorBounds:" + mLeftSensorBounds + " mRightSensorBounds:" + mRightSensorBounds);
    }

    public static void updateLeftRightSensorAreaState(MotionEvent motionEvent) {
        isInCenterSensorBounds = false;
        isInLeftSensorBounds = false;
        isInRightSensorBounds = false;
        int x = (int) motionEvent.getX();
        int y = (int) motionEvent.getY();
        if (mCenterSensorBounds.contains(x, y)) {
            isInCenterSensorBounds = true;
        }
        if (mLeftSensorBounds.contains(x, y)) {
            isInLeftSensorBounds = true;
        }
        if (mRightSensorBounds.contains(x, y)) {
            isInRightSensorBounds = true;
        }
        Log.e("FlymeWideFingerPrintUtils", "updateLeftRightSensorAreaState() MotionEvent(x,y) = (" + x + MzContactsContract.MzGroups.GROUP_SPLIT_MARK_EXTRA + y + ")\nmCenterSensorBounds=" + mCenterSensorBounds + " isInCenterSensorBounds=" + isInCenterSensorBounds + "\nmLeftSensorBounds=" + mLeftSensorBounds + " isInLeftSensorBounds=" + isInLeftSensorBounds + "\nmRightSensorBounds=" + mRightSensorBounds + " isInRightSensorBounds=" + isInRightSensorBounds);
    }

    public static boolean isInLeftOrRightSensorArea() {
        return isInLeftSensorBounds || isInRightSensorBounds;
    }

    public static boolean isInLeftOrRightOrCenterSensorArea() {
        return isInLeftSensorBounds || isInRightSensorBounds || isInCenterSensorBounds;
    }

    public static boolean isInLeftOrRightOrCenterSensorArea(int i, int i2) {
        return mLeftSensorBounds.contains(i, i2) || mRightSensorBounds.contains(i, i2) || mCenterSensorBounds.contains(i, i2);
    }

    public static boolean isInLockPalmRejectionArea(int i, int i2) {
        return mBoundsForLockPalmRejection.contains(i, i2);
    }

    public static boolean isInLeftSensorArea() {
        return isInLeftSensorBounds;
    }

    public static boolean isInRightSensorArea() {
        return isInRightSensorBounds;
    }

    public static boolean isInCenterSensorArea() {
        return isInCenterSensorBounds;
    }

    public static Rect getCenterBounds() {
        return mCenterSensorBounds;
    }

    public static String getFingerPrintQuickLaunchData(Context context, boolean z) {
        if (z) {
            return Settings.Global.getString(context.getContentResolver(), "fingerprint_quick_launch_left");
        }
        return Settings.Global.getString(context.getContentResolver(), "fingerprint_quick_launch_right");
    }

    public static AsyncTask loadIconDrawable(Context context, boolean z, boolean z2, OnDrawableCallbackListener onDrawableCallbackListener) {
        Log.d("FlymeWideFingerPrintUtils", "loadIconDrawable() isLeftQuickLaunch:" + z + " isReadCache:" + z2);
        if (z2) {
            Drawable drawable = z ? drawableCacheLeft : drawableCacheRight;
            if (drawable != null) {
                Log.d("FlymeWideFingerPrintUtils", "loadIconDrawable from cache. isLeftQuickLaunch:" + z + " drawableCache:" + drawable);
                if (onDrawableCallbackListener != null) {
                    onDrawableCallbackListener.onDrawableCallback(drawable);
                }
            }
        }
        return new IconDrawableAsycTack(context, onDrawableCallbackListener).execute(Boolean.valueOf(z));
    }

    public static void clearDrawableCache(boolean z) {
        Log.d("FlymeWideFingerPrintUtils", "clearDrawableCache() isLeftQuickLaunch:" + z);
        if (z) {
            drawableCacheLeft = null;
        } else {
            drawableCacheRight = null;
        }
    }

    public static Drawable getIconDrawable(Context context, boolean z) {
        String fingerPrintQuickLaunchData = getFingerPrintQuickLaunchData(context, z);
        Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() 原始数据：isLeftQuickLaunch=" + z + " quickLaunchData=" + fingerPrintQuickLaunchData);
        if (TextUtils.isEmpty(fingerPrintQuickLaunchData)) {
            return getWechatDrawable(context, z);
        }
        String packageName = getPackageName(fingerPrintQuickLaunchData);
        int uid = getUid(fingerPrintQuickLaunchData);
        String shortCutId = getShortCutId(fingerPrintQuickLaunchData);
        Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() 解析数据后： packageName=" + packageName + " uid=" + uid + " shortCutId=" + shortCutId);
        if (isPrivateForPkg(context, packageName, uid)) {
            Log.e("FlymeWideFingerPrintUtils", "getIconDrawable(): app is private. isLeftQuickLaunch=" + z);
            return null;
        }
        if ("com.tencent.mm".equals(packageName) && "launch_type_scan_qrcode".equals(shortCutId)) {
            Log.e("FlymeWideFingerPrintUtils", "getIconDrawable(): launch_type_scan_qrcode isLeftQuickLaunch=" + z);
            return getWechatDrawable(context, true);
        }
        if ("com.tencent.mm".equals(packageName) && "launch_type_offline_wallet".equals(shortCutId)) {
            Log.e("FlymeWideFingerPrintUtils", "getIconDrawable(): launch_type_offline_wallet isLeftQuickLaunch=" + z);
            return getWechatDrawable(context, false);
        }
        if (!isInstallForPkg(context, packageName)) {
            Log.e("FlymeWideFingerPrintUtils", "getIconDrawable(): app is uninstalled. isLeftQuickLaunch=" + z);
            if ("com.flyme.aggregatepayment".equals(packageName)) {
                return context.getResources().getDrawable(R$drawable.ic_aggregate_payment);
            }
        } else {
            if ("com.meizu.picker".equals(packageName)) {
                return getAicyDrawable(context);
            }
            if ("com.eg.android.AlipayGphone".equals(packageName) && !TextUtils.isEmpty(shortCutId)) {
                ShortcutInfo shortcut = getShortcut(context, packageName, uid, shortCutId);
                Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() alipayFun:" + shortcut);
                if (shortcut != null) {
                    return ((LauncherApps) context.getSystemService(LauncherApps.class)).getShortcutBadgedIconDrawable(shortcut, 0);
                }
            } else if ("com.tencent.mm".equals(packageName) && !TextUtils.isEmpty(shortCutId)) {
                ShortcutInfo shortcut2 = getShortcut(context, packageName, uid, shortCutId);
                Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() wechatFun: " + shortcut2);
                if (shortcut2 != null) {
                    return ((LauncherApps) context.getSystemService(LauncherApps.class)).getShortcutBadgedIconDrawable(shortcut2, 0);
                }
            } else if (!TextUtils.isEmpty(shortCutId)) {
                ShortcutInfo shortcut3 = getShortcut(context, packageName, uid, shortCutId, 1024);
                Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() normalFun:" + shortcut3);
                if (shortcut3 != null) {
                    Drawable drawable = context.getPackageManager().getDrawable(shortcut3.getPackage(), shortcut3.getIconResourceId(), null);
                    return drawable == null ? ((LauncherApps) context.getSystemService(LauncherApps.class)).getShortcutBadgedIconDrawable(shortcut3, 0) : drawable;
                }
            } else if (isFlymeParallelSpace(uid)) {
                LauncherActivityInfo launcherActivityInfo = getLauncherActivityInfo(context, packageName, 999);
                Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() launcherActivityInfo=" + launcherActivityInfo);
                if (launcherActivityInfo != null) {
                    return launcherActivityInfo.getBadgedIcon(0);
                }
            } else {
                PackageInfo packageInfo = getPackageInfo(context, packageName);
                Log.d("FlymeWideFingerPrintUtils", "getIconDrawable() packageInfo=" + packageInfo);
                if (packageInfo != null) {
                    return packageInfo.applicationInfo.loadIcon(context.getPackageManager());
                }
            }
        }
        return null;
    }

    public static ShortcutInfo getShortcut(Context context, String str, int i, String str2) {
        return getShortcut(context, str, i, str2, 27);
    }

    public static ShortcutInfo getShortcut(Context context, String str, int i, String str2, int i2) {
        List<ShortcutInfo> shortcuts;
        if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2)) {
            return null;
        }
        LauncherApps launcherApps = (LauncherApps) context.getSystemService(LauncherApps.class);
        LauncherApps.ShortcutQuery shortcutQuery = new LauncherApps.ShortcutQuery();
        shortcutQuery.setPackage(str).setShortcutIds(List.of(str2)).setQueryFlags(i2);
        try {
            shortcuts = launcherApps.getShortcuts(shortcutQuery, UserHandle.getUserHandleForUid(i));
        } catch (IllegalStateException unused) {
            Log.e("FlymeWideFingerPrintUtils", "User is locked or not running.");
            shortcuts = null;
        }
        if (shortcuts == null || shortcuts.size() <= 0) {
            return null;
        }
        return shortcuts.get(0);
    }

    public static void startQuickLaunch(Context context, boolean z) {
        ShortcutInfo shortcut;
        if (!isInLeftOrRightSensorArea() || !shouldShowLeftRightIcon(context)) {
            Log.e("FlymeWideFingerPrintUtils", "startQuickLaunch() return");
            return;
        }
        boolean z2 = isInLeftSensorBounds;
        String fingerPrintQuickLaunchData = getFingerPrintQuickLaunchData(context, z2);
        if (TextUtils.isEmpty(fingerPrintQuickLaunchData)) {
            Log.e("FlymeWideFingerPrintUtils", "startQuickLaunch() no setting data");
            if (z) {
                launchShopForPkgDismissingKeyguard(context, "com.tencent.mm");
                return;
            } else {
                launchShopForPkg(context, "com.tencent.mm");
                return;
            }
        }
        String packageName = getPackageName(fingerPrintQuickLaunchData);
        int uid = getUid(fingerPrintQuickLaunchData);
        String shortCutId = getShortCutId(fingerPrintQuickLaunchData);
        Log.d("FlymeWideFingerPrintUtils", "startQuickLaunch() isLeftQuickLaunch=" + z2 + " quickLaunchData=" + fingerPrintQuickLaunchData + " shouldShowBouncer:" + z);
        if (isPrivateForPkg(context, packageName, uid)) {
            Log.e("FlymeWideFingerPrintUtils", "startQuickLaunch(): app is private. isLeftQuickLaunch=" + z2);
            if (z) {
                launchSettingForQuickDismissingKeyguard(context);
                return;
            } else {
                launchSettingForQuick(context);
                return;
            }
        }
        if (!isInstallForPkg(context, packageName)) {
            Log.e("FlymeWideFingerPrintUtils", "startQuickLaunch(): app is uninstalled. isLeftQuickLaunch=" + z2);
            if (isWechatScanOrWallet(packageName, shortCutId)) {
                if (z) {
                    launchShopForPkgDismissingKeyguard(context, packageName);
                    return;
                } else {
                    launchShopForPkg(context, packageName);
                    return;
                }
            }
            if ("com.flyme.aggregatepayment".equals(packageName)) {
                if (z) {
                    launchQuickPayDismissingKeyguard(context);
                    return;
                } else {
                    launchQuickPay(context);
                    return;
                }
            }
            if (z) {
                launchSettingForQuickDismissingKeyguard(context);
                return;
            } else {
                launchSettingForQuick(context);
                return;
            }
        }
        if ("com.meizu.picker".equals(packageName)) {
            if (z) {
                launchAicyDismissingKeyguard(context);
                return;
            } else {
                launchAicy(context);
                return;
            }
        }
        if (!TextUtils.isEmpty(shortCutId)) {
            if ("com.eg.android.AlipayGphone".equals(packageName) || "com.tencent.mm".equals(packageName)) {
                shortcut = getShortcut(context, packageName, uid, shortCutId);
            } else {
                shortcut = getShortcut(context, packageName, uid, shortCutId, 1024);
            }
            Log.d("FlymeWideFingerPrintUtils", "startQuickLaunch() shortcutInfo=" + shortcut);
            if (shortcut != null) {
                if (z) {
                    launchFunWithMiniDismissingKeyguard(context, packageName, uid, shortCutId);
                    return;
                } else {
                    launchFunWithMini(context, packageName, uid, shortCutId);
                    return;
                }
            }
            if (isWechatScanOrWallet(packageName, shortCutId) && isInstallForPkg(context, packageName)) {
                if (z) {
                    launchActivityDismissingKeyguard(context, packageName);
                    return;
                } else {
                    launchActivity(context, packageName);
                    return;
                }
            }
            if (z) {
                launchSettingForQuickDismissingKeyguard(context);
                return;
            } else {
                launchSettingForQuick(context);
                return;
            }
        }
        if (isFlymeParallelSpace(uid)) {
            if (z) {
                launchSpaceActivityDismissingKeyguard(context, packageName, 999);
                return;
            } else {
                launchSpaceActivity(context, packageName, 999);
                return;
            }
        }
        Intent launchIntentForPackage = getLaunchIntentForPackage(context, packageName);
        Log.d("FlymeWideFingerPrintUtils", "startQuickLaunch() launchIntent：" + launchIntentForPackage);
        if (launchIntentForPackage != null) {
            if (z) {
                ((ActivityStarter) Dependency.get(ActivityStarter.class)).startPendingIntentDismissingKeyguard(PendingIntent.getActivity(context, 0, launchIntentForPackage, 67108864));
            } else {
                context.startActivity(launchIntentForPackage);
            }
        }
    }

    public static String getPackageName(String str) {
        String[] strArrSplit;
        if (TextUtils.isEmpty(str) || (strArrSplit = str.split(":")) == null || strArrSplit.length <= 1) {
            return null;
        }
        return strArrSplit[0];
    }

    public static int getUid(String str) {
        String[] strArrSplit;
        if (!TextUtils.isEmpty(str) && (strArrSplit = str.split(":")) != null && strArrSplit.length > 1) {
            int iIndexOf = strArrSplit[1].indexOf("{") + 1;
            int iIndexOf2 = strArrSplit[1].indexOf("}");
            if (iIndexOf >= 0 && iIndexOf2 >= 0 && iIndexOf2 > iIndexOf) {
                return Integer.valueOf(strArrSplit[1].substring(iIndexOf, iIndexOf2)).intValue();
            }
        }
        return 0;
    }

    public static String getShortCutId(String str) {
        String[] strArrSplit;
        if (TextUtils.isEmpty(str) || (strArrSplit = str.split(":")) == null || strArrSplit.length <= 2) {
            return null;
        }
        if (strArrSplit.length == 3) {
            return strArrSplit[2];
        }
        return str.substring(str.indexOf(strArrSplit[2]));
    }

    public static PackageInfo getPackageInfo(Context context, String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        try {
            return context.getPackageManager().getPackageInfo(str, 1);
        } catch (Exception e) {
            Log.e("FlymeWideFingerPrintUtils", "getPackageInfo Exception:" + e);
            e.printStackTrace();
            return null;
        }
    }

    public static LauncherActivityInfo getLauncherActivityInfo(Context context, String str, int i) {
        List<LauncherActivityInfo> activityList = ((LauncherApps) context.getSystemService(LauncherApps.class)).getActivityList(str, UserHandle.of(i));
        if (activityList.size() > 0) {
            return activityList.get(0);
        }
        return null;
    }

    public static Intent getLaunchIntentForPackage(Context context, String str) {
        if (TextUtils.isEmpty(str)) {
            return null;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            if (packageManager.getPackageInfo(str, 1) != null) {
                return packageManager.getLaunchIntentForPackage(str);
            }
        } catch (Exception e) {
            Log.e("FlymeWideFingerPrintUtils", "getLaunchIntentForPackage Exception:" + e);
            e.printStackTrace();
        }
        return null;
    }

    public static void launchQuickPayDismissingKeyguard(final Context context) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchQuickPay(context);
            }
        }, null, false, true, false);
    }

    public static void launchQuickPay(final Context context) {
        handler.postDelayed(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.m14674$r8$lambda$nx3R8fqzvNS7WdKHWFY5xW_96E(context);
            }
        }, 200L);
    }

    /* JADX INFO: renamed from: $r8$lambda$nx3R8fqzvNS7WdKH-WFY5xW_96E, reason: not valid java name */
    public static /* synthetic */ void m14674$r8$lambda$nx3R8fqzvNS7WdKHWFY5xW_96E(Context context) {
        Log.d("FlymeWideFingerPrintUtils", "launchQuickPay() iQuickPayHelper:" + iQuickPayHelper);
        IQuickPayHelper iQuickPayHelper2 = iQuickPayHelper;
        if (iQuickPayHelper2 == null) {
            Intent intent = new Intent();
            intent.setAction("com.flyme.systemuitools.windowmode.quickpay.launch");
            intent.setPackage("com.flyme.systemuitools");
            context.bindService(intent, new ServiceConnection() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils.1
                @Override // android.content.ServiceConnection
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    Log.d("FlymeWideFingerPrintUtils", "launchQuickPay() onServiceConnected name:" + componentName);
                    FlymeWideFingerPrintUtils.iQuickPayHelper = IQuickPayHelper.Stub.asInterface(iBinder);
                    try {
                        FlymeWideFingerPrintUtils.iQuickPayHelper.launchQuickPay();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }

                @Override // android.content.ServiceConnection
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.d("FlymeWideFingerPrintUtils", "launchQuickPay() onServiceDisconnected name:" + componentName);
                    FlymeWideFingerPrintUtils.iQuickPayHelper = null;
                }
            }, 1);
            return;
        }
        try {
            iQuickPayHelper2.launchQuickPay();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static void launchAicyDismissingKeyguard(final Context context) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchAicy(context);
            }
        }, null, false, true, false);
    }

    public static void launchAicy(final Context context) {
        handler.postDelayed(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda9
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.$r8$lambda$pzfvn0H3lpvbNLOrkwPI2MPW6eI(context);
            }
        }, 200L);
    }

    public static /* synthetic */ void $r8$lambda$pzfvn0H3lpvbNLOrkwPI2MPW6eI(Context context) {
        Log.d("FlymeWideFingerPrintUtils", "launchAicy()");
        ResolveInfo aicyResolveInfo = getAicyResolveInfo(context);
        Intent intent = new Intent("com.flyme.action.WindowMode");
        ServiceInfo serviceInfo = aicyResolveInfo.serviceInfo;
        intent.setComponent(new ComponentName(serviceInfo.packageName, serviceInfo.name));
        context.startForegroundService(intent);
    }

    public static Drawable getAicyDrawable(Context context) {
        ResolveInfo aicyResolveInfo = getAicyResolveInfo(context);
        int i = aicyResolveInfo.serviceInfo.metaData.getInt("com.flyme.shortcut.icon", -1);
        if (i != -1) {
            return context.getPackageManager().getDrawable(aicyResolveInfo.serviceInfo.packageName, i, null);
        }
        return null;
    }

    public static Drawable getWechatDrawable(Context context, boolean z) {
        if (z) {
            return context.getResources().getDrawable(R$drawable.ic_lockshortcut_w_scan);
        }
        return context.getResources().getDrawable(R$drawable.ic_lockshortcut_w_pay);
    }

    public static ResolveInfo getAicyResolveInfo(Context context) {
        List<ResolveInfo> listQueryIntentServices = context.getPackageManager().queryIntentServices(new Intent("com.flyme.action.WindowMode"), 131200);
        if (listQueryIntentServices == null || listQueryIntentServices.isEmpty()) {
            return null;
        }
        return listQueryIntentServices.get(0);
    }

    public static void launchFunWithMiniDismissingKeyguard(final Context context, final String str, final int i, final String str2) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchFunWithMini(context, str, i, str2);
            }
        }, null, false, true, false);
    }

    public static void launchFunWithMini(final Context context, final String str, final int i, final String str2) {
        handler.postDelayed(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda10
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.$r8$lambda$GKck54Ac1mJLM1g0O4cns6nzp34(context, str, str2, i);
            }
        }, 200L);
    }

    public static /* synthetic */ void $r8$lambda$GKck54Ac1mJLM1g0O4cns6nzp34(Context context, String str, String str2, int i) {
        Log.d("FlymeWideFingerPrintUtils", "launchFunWithMini()");
        Bundle bundle = new Bundle();
        bundle.putBoolean("start_windowmode", true);
        ((LauncherApps) context.getSystemService(LauncherApps.class)).startShortcut(str, str2, null, bundle, UserHandle.getUserHandleForUid(i));
    }

    public static void launchActivityDismissingKeyguard(final Context context, final String str) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda6
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchActivity(context, str);
            }
        }, null, false, true, false);
    }

    public static void launchActivity(Context context, String str) {
        Intent launchIntentForPackage = getLaunchIntentForPackage(context, str);
        Log.d("FlymeWideFingerPrintUtils", "launchActivity() launchIntent:" + launchIntentForPackage);
        if (launchIntentForPackage != null) {
            context.startActivity(launchIntentForPackage);
        }
    }

    public static void launchSpaceActivityDismissingKeyguard(final Context context, final String str, final int i) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchSpaceActivity(context, str, i);
            }
        }, null, false, true, false);
    }

    public static void launchSpaceActivity(Context context, String str, int i) {
        LauncherActivityInfo launcherActivityInfo = getLauncherActivityInfo(context, str, i);
        Log.d("FlymeWideFingerPrintUtils", "launchSpaceActivity() launcherActivityInfo：" + launcherActivityInfo);
        if (launcherActivityInfo != null) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("start_spacemode", true);
            Intent intent = new Intent();
            intent.setAction("android.intent.action.MAIN");
            intent.addFlags(270532608);
            intent.setComponent(launcherActivityInfo.getComponentName());
            Log.d("FlymeWideFingerPrintUtils", "startQuickLaunch() launcherActivityInfo intent：" + intent);
            context.startActivityAsUser(intent, bundle, new UserHandle(i));
        }
    }

    public static void launchShopForPkgDismissingKeyguard(final Context context, final String str) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchShopForPkg(context, str);
            }
        }, null, false, true, false);
    }

    public static void launchShopForPkg(Context context, String str) {
        Log.d("FlymeWideFingerPrintUtils", "launchShopForPkg() packageName:" + str);
        Uri uri = Uri.parse(String.format("mstore://details?package_name=%s&source_apkname=%s", str, context.getApplicationContext().getPackageName()));
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setData(uri);
        intent.addFlags(268435456);
        context.startActivity(intent);
    }

    public static void launchSettingForQuickDismissingKeyguard(final Context context) {
        ((ActivityStarter) Dependency.get(ActivityStarter.class)).executeRunnableDismissingKeyguard(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.launchSettingForQuick(context);
            }
        }, null, false, true, false);
    }

    public static void launchSettingForQuick(final Context context) {
        handler.postDelayed(new Runnable() { // from class: com.flyme.systemui.biometrics.FlymeWideFingerPrintUtils$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                FlymeWideFingerPrintUtils.$r8$lambda$899Rqt21rIoq1HVtQn0CCwFB24w(context);
            }
        }, 50L);
    }

    public static /* synthetic */ void $r8$lambda$899Rqt21rIoq1HVtQn0CCwFB24w(Context context) {
        Log.d("FlymeWideFingerPrintUtils", "launchSettingForQuick()");
        Intent intent = new Intent();
        intent.addFlags(268435456);
        intent.setAction("meizu.intent.action.FINGERPRINT_QUICK_LAUNCH");
        context.startActivity(intent);
    }

    public static boolean isInstallForPkg(Context context, String str) {
        boolean z;
        try {
            z = true;
            context.getPackageManager().getApplicationInfo(str, 1);
        } catch (PackageManager.NameNotFoundException unused) {
            z = false;
        }
        Log.d("FlymeWideFingerPrintUtils", "isInstallForPkg():" + z);
        return z;
    }

    public static boolean isPrivateForPkg(Context context, String str, int i) {
        boolean zIsPrivacyPackage = MSpaceControllerImpl.getController(context).isPrivacyPackage(str, i);
        Log.d("FlymeWideFingerPrintUtils", "isPrivateForPkg() :" + zIsPrivacyPackage);
        return zIsPrivacyPackage;
    }

    public static boolean isWechatScanOrWallet(String str, String str2) {
        if ("com.tencent.mm".equals(str)) {
            return "launch_type_scan_qrcode".equals(str2) || "launch_type_offline_wallet".equals(str2);
        }
        return false;
    }

    public static boolean isFlymeParallelSpace(int i) {
        return String.valueOf(i).startsWith(String.valueOf(999));
    }

    public class IconDrawableAsycTack extends AsyncTask {
        public Context mContext;
        public OnDrawableCallbackListener onDrawableCallbackListener;

        public IconDrawableAsycTack(Context context, OnDrawableCallbackListener onDrawableCallbackListener) {
            this.mContext = context;
            this.onDrawableCallbackListener = onDrawableCallbackListener;
        }

        @Override // android.os.AsyncTask
        public HashMap doInBackground(Boolean... boolArr) {
            Log.d("FlymeWideFingerPrintUtils", "doInBackground booleans[0]:" + boolArr[0]);
            HashMap map = new HashMap();
            if (boolArr[0].booleanValue()) {
                Drawable iconDrawable = FlymeWideFingerPrintUtils.getIconDrawable(this.mContext, true);
                boolean zIsSameDrawable = FlymeWideFingerPrintUtils.isSameDrawable(FlymeWideFingerPrintUtils.drawableCacheLeft, iconDrawable);
                Log.d("FlymeWideFingerPrintUtils", "doInBackground sameDrawableLeft:" + zIsSameDrawable);
                if (!zIsSameDrawable) {
                    FlymeWideFingerPrintUtils.drawableCacheLeft = iconDrawable;
                    map.put(Boolean.TRUE, iconDrawable);
                    return map;
                }
                map.put(Boolean.FALSE, iconDrawable);
                return map;
            }
            Drawable iconDrawable2 = FlymeWideFingerPrintUtils.getIconDrawable(this.mContext, false);
            boolean zIsSameDrawable2 = FlymeWideFingerPrintUtils.isSameDrawable(FlymeWideFingerPrintUtils.drawableCacheRight, iconDrawable2);
            Log.d("FlymeWideFingerPrintUtils", "doInBackground sameDrawableRight:" + zIsSameDrawable2);
            if (!zIsSameDrawable2) {
                FlymeWideFingerPrintUtils.drawableCacheRight = iconDrawable2;
                map.put(Boolean.TRUE, iconDrawable2);
                return map;
            }
            map.put(Boolean.FALSE, iconDrawable2);
            return map;
        }

        @Override // android.os.AsyncTask
        public void onPostExecute(HashMap map) {
            OnDrawableCallbackListener onDrawableCallbackListener;
            super.onPostExecute(map);
            for (Map.Entry entry : map.entrySet()) {
                Log.d("FlymeWideFingerPrintUtils", "onPostExecute key:" + entry.getKey() + " value:" + entry.getValue());
                if (((Boolean) entry.getKey()).booleanValue() && (onDrawableCallbackListener = this.onDrawableCallbackListener) != null) {
                    onDrawableCallbackListener.onDrawableCallback((Drawable) entry.getValue());
                }
            }
        }
    }

    /* JADX WARN: Code duplicated, block: B:27:0x0088  */
    public static synchronized boolean isSameDrawable(Drawable drawable, Drawable drawable2) {
        boolean z;
        Bitmap bitmap;
        Bitmap bitmap2;
        z = false;
        if (drawable != drawable2) {
            if (drawable == null || drawable2 == null) {
                z = true;
            } else {
                try {
                    if (drawable.getIntrinsicWidth() == drawable2.getIntrinsicWidth() && drawable.getIntrinsicHeight() == drawable2.getIntrinsicHeight()) {
                        if (drawable instanceof BitmapDrawable) {
                            bitmap = ((BitmapDrawable) drawable).getBitmap();
                            bitmap2 = ((BitmapDrawable) drawable2).getBitmap();
                        } else if (drawable instanceof VectorDrawable) {
                            int intrinsicWidth = drawable.getIntrinsicWidth();
                            int intrinsicHeight = drawable.getIntrinsicHeight();
                            Bitmap.Config config = Bitmap.Config.ARGB_8888;
                            Bitmap bitmapCreateBitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, config);
                            Canvas canvas = new Canvas(bitmapCreateBitmap);
                            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                            drawable.draw(canvas);
                            Bitmap bitmapCreateBitmap2 = Bitmap.createBitmap(drawable2.getIntrinsicWidth(), drawable2.getIntrinsicHeight(), config);
                            Canvas canvas2 = new Canvas(bitmapCreateBitmap2);
                            drawable2.setBounds(0, 0, drawable2.getIntrinsicWidth(), drawable2.getIntrinsicHeight());
                            drawable2.draw(canvas2);
                            bitmap2 = bitmapCreateBitmap2;
                            bitmap = bitmapCreateBitmap;
                        } else {
                            bitmap = null;
                            bitmap2 = null;
                        }
                        if (bitmap == null || bitmap2 == null || !bitmap.sameAs(bitmap2)) {
                            z = true;
                        }
                    } else {
                        z = true;
                    }
                } catch (Throwable th) {
                    throw th;
                }
            }
        }
        return !z;
    }
}
