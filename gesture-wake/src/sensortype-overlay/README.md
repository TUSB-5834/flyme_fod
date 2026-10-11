# SensorType Overlay —— 恢复设置里的「抬手/翻转亮屏」开关

## 问题

Flyme 设置里看不到「抬手亮屏 / 翻转亮屏」开关，不是设置项缺失，而是**能力探测失败后被主动隐藏**：

```java
// com.meizu.settings.lockscreen_aod.AODAndLockScreenSettings:158-165
this.mRaiseFlip = (SwitchPreference) findPreference("raise_flip");
if (MzUtils.isGuestUser(...) || FlymeLab...isFlymeLabHasFeature("com.android.settings.raiseflip")
        || !MzUtils.supportRaiseFlipFeature(getActivity())) {
    getPreferenceScreen().removePreference(this.mRaiseFlip);   // ← 这里被移除
}

// MzUtils.java:837-844 —— 判断读的是 android 包的资源
int flipType  = resources.getInteger(getIdentifier("config_flipSensorType",  "integer", "android"));
int raiseType = resources.getInteger(getIdentifier("config_raiseSensorType", "integer", "android"));
return sm.getDefaultSensor(flipType, true) != null && sm.getDefaultSensor(raiseType, true) != null;
```

真机上（`aapt2 dump resources /system/framework/framework-res.apk`）：

```
integer/config_flipSensorType   = 65538   ← 魅族私有传感器，小米硬件上不存在
integer/config_raiseSensorType  = 65539   ← 同上
```

→ `getDefaultSensor(65538/65539, true)` 都是 null → `supportRaiseFlipFeature()` 返回 false → 开关被移除。
SystemUI 的 `com.flyme.keyguard.RaiseFlipperController:206-210` 读的是**同一份资源**，所以这两个资源就是唯一的开关。

## 方案

静态 RRO，把这两个 `android` 资源指向本机真实存在、且对 Flyme 判断**惰性**的传感器：

| 资源 | 原值 | 覆盖为 |
|---|---|---|
| `config_flipSensorType` | 65538 | **8（proximity）** |
| `config_raiseSensorType` | 65539 | **8（proximity）** |

为什么选 proximity：`RaiseFlipperController` 的 flip 监听器只在 `i==1 && 熄屏` 时唤醒、`i==2 && 亮屏` 时灭屏；raise 监听器只在 `i==2` 时进接近流程、在 `i==4||5` 且 `mWakeupByRaise/mWakeupByFlip` 为真时灭屏。proximity 只给 `0.0/5.0`，两个标志位又只有 Flyme 自己的监听器唤醒时才会置位 → **SystemUI 全程惰性**，真正的抬手唤醒由 `MiGestureBridge` 负责。开关可见并打开后，`raisewakeup_switch` 会同时驱动桥和 Flyme 两侧。

同时注意（实测）：**运行时动态 RRO 在这台 ROM 上不可用** —— `cmd overlay fabricate` + `enable` 一律
`SecurityException: commit failed`（`logcat: W OverlayManager: service 'idmap' died`，`/data/misc/idmap` 也不存在），
对照实验用一个确定可覆盖的资源同样失败。但**静态 RRO 生效**，证据是魅族自己的 `android.overlay.mz`：

```
cmd overlay list android                        → [x] android.overlay.mz  (targetPackage=android, isStatic, 无 overlayable 声明)
cmd overlay lookup android android:array/config_ntpServers
  → ntp://time-a.nist.gov ... ntp://hshh.org     ← 正是该 RRO 里的值
```

本覆盖包与它形状完全一致（`targetPackage=android`、`isStatic=true`、无 overlayable 声明），只是优先级取 601（高于魅族的 600）。

## 文件与安装

```
AndroidManifest.xml                     RRO 声明（targetPackage=android, isStatic=true, priority=601）
res/values/configs.xml                  两个 integer
build/SensorTypesOverlay-signed.apk     产物（已用 overlay.keystore 签名）
ksu/                                    KernelSU 模块（product/overlay/SensorTypesOverlay.apk）
../dist/sensortypeoverlay-ksu.zip       模块包
```

**方式 A（ROM 内置，正式）**：把 `build/SensorTypesOverlay-signed.apk` 复制到 ROM 的 `product/overlay/`（与
`FrameworksResCommon_Mz.apk` 同目录）或 `vendor/overlay/`，重打包即可。

**方式 B（运行时，免重启，DSU 测试首选）**：

```sh
su -c 'cmd overlay fabricate --target android --name mzflip_test  android:integer/config_flipSensorType  0x10 8'
su -c 'cmd overlay fabricate --target android --name mzraise_test android:integer/config_raiseSensorType 0x10 8'
su -c 'cmd overlay enable --user 0 com.android.shell:mzflip_test'
su -c 'cmd overlay enable --user 0 com.android.shell:mzraise_test'
su -c 'am force-stop com.android.settings'     # 让设置重新读资源
```
⚠️ **fabricated overlay 不跨重启**（实测：重启后 `cmd overlay lookup` 又回到 `65538/65539`，`cmd overlay list` 里也不见了）。
所以本模块的 `service.sh` 会在**每次开机**（`sys.boot_completed=1` 之后）重建一遍，带重试且幂等 —— 装模块即自愈。
要永久生效（不依赖模块/root）就把 `build/SensorTypesOverlay-signed.apk` 放进 ROM 的 `product/overlay/`（方式 A）。
撤销：`cmd overlay disable --user 0 com.android.shell:mzflip_test`。

**方式 C（KernelSU 模块）：在本机不生效**。`/product/overlay` 本身就是 overlayfs 挂载点
（`lowerdir=/mnt/vendor/mi_ext/product/overlay/:/product/overlay`，`/mnt/vendor/mi_ext` 是只读 erofs dm-7），
KSU 挂进去的文件会被这层挂载遮住 —— 实测模块文件在 `/data/adb/modules/sensortypeoverlay/product/overlay/`，
但 `/product/overlay/SensorTypesOverlay.apk` 不存在、`cmd overlay list` 里也没有它。要免重启就用方式 B，要持久就进 ROM。

## 另一个入口：独立页「抬腕亮屏」（一直存在）

`com.meizu.settings.MzSettingsActivity$FlymeRaiseFlipSettingsActivity`
（fragment = `com.meizu.settings.display.FlymeRaiseAndFlipFragment`，label = `raise_flip_config_title` = 抬腕亮屏）
**没有任何能力检测**，无条件加载 `res/xml/flyme_raise_flip.xml` 并直接读写 `raisewakeup_switch`/`flipwakeup_switch`：

```sh
am start -n 'com.android.settings/com.meizu.settings.MzSettingsActivity$FlymeRaiseFlipSettingsActivity'
# 或：am start -a com.meizu.settings.RAISE_FLIP_SETTINGS
```

常规两个入口才带检测：`AODAndLockScreenSettings:158-165`（不满足则 `removePreference`）与
`FlymeRaiseFlipController.getAvailabilityStatus()`（`supportRaiseFlipFeature(ctx) ? 0 : 3`）。
另有一个 OR 条件 `FlymeLabHelper.isFlymeLabHasFeature("com.android.settings.raiseflip")`（查
`content://com.meizu.flymelab.features/packageNameList`）—— 本机没装 FlymeLab，provider 不存在，该条件为 false。

验证：

```sh
adb shell cmd overlay list android | grep sensortype
adb shell cmd overlay lookup android android:integer/config_raiseSensorType   # 期望 8
# 然后打开 设置 → 显示与亮度 → 息屏显示/锁屏，应能看到「抬手亮屏/翻转亮屏」开关
```

## 编译

```sh
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/SensorTypesOverlay.apk --manifest AndroidManifest.xml \
    -I framework-res.apk -R build/res.zip --auto-add-overlay \
    --min-sdk-version 34 --target-sdk-version 36
zipalign -p -f 4 build/SensorTypesOverlay.apk build/aligned.apk
apksigner sign --ks overlay.keystore --out build/SensorTypesOverlay-signed.apk build/aligned.apk
```

> `-I` 必须在**从本机拉下来的** `framework-res.apk`（本仓库 `_dev_test/fw/framework-res.apk`）上执行，
> 保证资源 ID 空间与本 ROM 一致。
