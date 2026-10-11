# evidence —— 根因定位用的反编译证据

> ⚠️ 本目录是**第三方程序的反编译产物**（Flyme 的 `Settings.apk` 等），只为说明"为什么开关会消失、
> 可见性到底怎么判"，**不适用**本仓库的 GPL-3.0 授权，版权归各自权利人所有
> （与 `fod-animation/evidence/` 同一性质）。

| 文件 | 证明了什么 |
|---|---|
| `MzUtils.java` | `supportRaiseFlipFeature()`（约 837 行）读 `config_flipSensorType` / `config_raiseSensorType`，并要求两者对应的 `getDefaultSensor()` 非 null → 本机两个传感器都不存在 → 返回 **false**（这就是开关消失的直接原因）。`isEasyModeOpened()`（557 行）= `Settings.System.getInt(cr, "mz_easy_mode", 0) == 1` |
| `FlymeDoubleClickWakeUpController.java` | **「双击亮屏」的可见性**：`getAvailabilityStatus()` 只看 `android.R.bool.config_automatic_brightness_available`（本机解析为 `true`）→ **恒可见**，与上面那两个资源无关；开关读写 `mz_quick_wakeup_double_click`，并在切换时同步「快捷唤醒」总开关 |
| `FlymeRaiseFlipController.java` | 「抬腕亮屏」在「显示与亮度」页的可见性：`supportRaiseFlipFeature(ctx) ? 0 : 3` —— 覆盖一旦丢失（重启后），它就变 3 → 被移除 |
| `FlymeRaiseAndFlipFragment.java` | 独立页「抬腕亮屏」（`MzSettingsActivity$FlymeRaiseFlipSettingsActivity`）**没有任何能力检测**，无条件加载 `flyme_raise_flip.xml` 并直接读写 `raisewakeup_switch` / `flipwakeup_switch` → 覆盖没生效时，这是唯一还能进的地方 |
| `AODAndLockScreenSettings.java` | 「息屏显示和锁屏」页：158–165 行按 `isGuestUser / FlymeLab / supportRaiseFlipFeature` 移除 `raise_flip`；166–168 行读 `quick_wakeup_double_click`；191–193 行**只在简易模式**（`mz_easy_mode == 1`）时才移除双击开关 |
| `res/xml/mz_display_settings.xml` | 「显示与亮度」页里两个开关是同级 preference：`quick_wakeup_double_click`（标题 `subscreen_double_click_wakeup` = 双击亮屏，带 `FlymeDoubleClickWakeUpController`）与 `raise_flip`（标题 `raise_flip_config_title`，带 `FlymeRaiseFlipController`） |
| `res/xml/aod_and_lockscreen_settings.xml` | 「息屏显示和锁屏」页里同样这两个 key（该页在本移植上打不开，见模块 README「已知限制」） |
| `res/xml/flyme_raise_flip.xml` | 独立页只有 `raise_flip` 一个开关 |
| `res/xml/subscreen_settings.xml` | 「副屏」页另有一个「双击亮屏」（key `subscreen_double_click_wakeup`），属于魅族副屏硬件功能，本机没有该硬件 |

对应的实测命令（在 Flyme 里 root 执行）：

```sh
cmd overlay lookup android android:integer/config_flipSensorType    # 覆盖前 65538，覆盖后 8
cmd overlay lookup android android:integer/config_raiseSensorType   # 65539 → 8
settings get system mz_quick_wakeup_double_click                    # 「双击亮屏」开关的值
settings get system mz_easy_mode                                    # 简易模式（会移除双击开关）
dumpsys package com.android.settings | grep -A2 AODAndLock           # AOD 页组件在，但 am start 打不开
```

真机 UI 复现（确认开关到底在不在页面上）：

```sh
am start -a android.settings.DISPLAY_SETTINGS
input swipe 600 2000 600 500 200   # 滚到「系统」那一组
uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
# 期望在文本里看到：双击亮屏 / 抬腕亮屏
```
