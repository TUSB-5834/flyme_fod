# flyme_fod

修复小米设备超声波指纹移植 Flyme 之后的 **指纹不可用**、**指纹动画不可用** 与 **双击/抬腕亮屏不可用**。

> 验证环境：Xiaomi 15（`dada` / 24129PN74C）· Flyme 12 移植版 · Android 16 · KernelSU

把非小米 ROM 移植到小米设备时，指纹链路上会断两处：

| # | 问题 | 现象 | 方案 | 目录 |
|---|---|---|---|---|
| 1 | **指纹不可用** | 按指纹毫无反应、无法录入、锁屏不解锁 | **MiFodBridge** —— 常驻 root 服务，监听生物识别状态，用 AIDL 通知触摸/指纹 HAL 切换 FOD 模式 | [`mifod-bridge/`](mifod-bridge/) |
| 2 | **指纹动画不可用** | 能解锁，但没有 Flyme 那套 FOD 认证动画 | **FodGesture** —— 监听指纹 HAL 的 FOD 触摸日志，向 `gesture_manager` 注入原生 `keyCode 413` | [`fod-animation/`](fod-animation/) |
| 3 | **双击 / 抬腕亮屏不可用** | 熄屏双击毫无反应；熄屏拿起也没反应，而且设置里连「抬腕亮屏」开关都没有 | **MiGestureBridge + SensorTypesOverlay** —— root 桥下发小米触摸 HAL 的双击模式、订阅小米 `pickup` 传感器自己唤醒，并用 RRO 把设置里的开关找回来 | [`gesture-wake/`](gesture-wake/) |

三者互不依赖，可以只用其中一个。

---

## 一、指纹不可用 → MiFodBridge

**根因**：小米的超声波指纹依赖小米自己的两个 HAL 服务，移植 ROM 里没人去驱动它们：

- `vendor.xiaomi.hw.touchfeature.ITouchFeature` —— 触摸芯片的 FOD 监听模式（`setModeValue(touchId, mode, value)`）
- `vendor.xiaomi.hardware.fingerprintextension.IXiaomiFingerprint` —— 指纹扩展的 FOD 模式（`extCmd(cmd, val)`）

原厂 ROM 里由小米的指纹服务在锁屏/录入时通知它们；移植 ROM 里这一步缺失，所以指纹硬件"没被叫醒"。

**方案**：`MiFodBridge` 以 root 常驻（init 服务 `mifod`），

1. 向 `IFingerprintService` 注册 `IBiometricStateListener`（tx `0x29`）→ 拿到 `keyguard-auth / enroll / bp-auth / idle` 状态；
2. 按状态调用
   - `setModeValue(0, 10, 1|2|0)` —— 认证 / 录入 / 关闭
   - `extCmd(4, 3|1|4)` —— `EXTCMD_FOD_MODE` = `AUTH|ENROLL|OFF`
3. 另起门控线程每 500 ms 用 `ITrustManager.isDeviceLocked` + `IPowerManager.isInteractive` 兜底：锁定且空闲就 `auth-on`，解锁且来源是锁屏就 `fod-off`。

日志：`/data/vendor/mifod/mifod.log`。细节见 [`mifod-bridge/README.md`](mifod-bridge/README.md)。

---

## 二、指纹动画不可用 → FodGesture

**根因**：Flyme 的 FOD 动画由 **TP 固件上报 `keyCode 413`** 触发：

```
IGestureManager.triggerGesture()
  └─ MzGestureController$GestureCallback.onGestureTrigger()   keyCode 413 → action 0/1 映射为 161/162
       └─ MzGestureController.handleGesture()                  → showFingerprintDownAnim()
            └─ ... → UdfpsKeyguardViewLegacy → FlymeFpAuthAnimView.startDownAnimation()
```

小米的 TP 固件不上报这个键，`onGestureTrigger()` **从不被调用**，于是动画代码虽然在 ROM 里，却永远不会跑。

**方案**：不 hook、不改 APK、不写 `/system`。用一个独立的 `app_process` 进程：

1. 监听 `logcat -s QFP:I`（指纹 HAL 自己的 FOD 触摸日志）；
2. 在 `onPointerDown` / `onPointerUp` 的同一刻，把 `413/action 0` / `413/action 1` 注入 `android.view.IGestureManager.triggerGesture()`；
3. SystemUI 于是走它**原本就有**的动画代码，帧与真手指同刻。

细节、调用链行号、5 道静默守卫见 [`fod-animation/README.md`](fod-animation/README.md)。

---

## 三、双击亮屏 / 抬腕亮屏不可用 → MiGestureBridge + SensorTypesOverlay

**根因**：这两个功能移植后都是哑的，但断点不同：

- **双击亮屏**：Flyme 只往魅族节点 `/sys/class/meizu/tp/gesture_control` 写，小米机器**没有这个节点** →
  触摸 IC 从未进入双击模式。小米侧真正的开关是 `ITouchFeature.setModeValue(touchId, mode, value)` 里的
  `mode = 14`（驱动里就是 `Touch_Doubletap_Mode`）。
- **抬腕亮屏**：Flyme 的 `RaiseFlipperController` 用魅族私有传感器 `65538/65539`（本机不存在），
  于是 `MzUtils.supportRaiseFlipFeature()` 为 false —— **连设置里的开关都被移除**；而且小米 `pickup`
  的语义与魅族**相反**（`1.0 = 拿起` vs 魅族 `2 = 拿起`），Flyme 那套控制器不能直接复用。

**方案**：一个常驻 `app_process` 进程 `MiGestureBridge`（root），跟随 Flyme 自己的开关：

1. `mz_quick_wakeup_double_click` → `setModeValue(0, 14, 0|1)`：IC 进/出双击模式，双击由固件上报 `KEY_WAKEUP(143)`；
2. `raisewakeup_switch` / `flipwakeup_switch` → 订阅/退订小米 `xiaomi.sensor.pickup(33171036)`，
   熄屏收到 `1.0` 就 `PowerManager.wakeUp()`，`2.0`（放下）忽略；
3. 用一个静态 RRO（或运行时 `cmd overlay fabricate`）把 `config_flipSensorType` / `config_raiseSensorType`
   指向本机存在的 `proximity(8)` → `supportRaiseFlipFeature()` 变 true → 设置里的「抬腕亮屏」开关回来。

> ⚠️ 运行时 `cmd overlay fabricate` 造出来的覆盖**不跨重启**（实测重启后资源又回到 `65538/65539`，
> 开关随之消失）；本目录两个 KernelSU 模块的 `service.sh` 会在**每次开机重建**，也可以直接把静态 RRO
> 放进 ROM 的 `product/overlay/` 永久生效。

机制细节、三次自我更正与全部实测日志见 [`gesture-wake/README.md`](gesture-wake/README.md)
与 [`gesture-wake/docs/`](gesture-wake/docs/)。

---

## 四、目录结构

```
flyme_fod/
├── mifod-bridge/                 超声波指纹桥接（问题 1）
│   ├── README.md                   原理 / 文件 / 部署 / 验证
│   ├── MiFodBridge.java            全部逻辑（单文件，652 行）
│   ├── build.sh                    javac + d8 → fod-bridge.jar
│   ├── mifod                       启动脚本（CLASSPATH + app_process64）
│   ├── mifod.rc                    init 服务定义（root, seclabel su:s0, late_start）
│   └── prebuilt/
│       ├── fod-bridge.jar          编译产物
│       └── fod-bridge.zip          可直接刷入的三件套（jar + mifod + mifod.rc）
├── fod-animation/                FOD 解锁动画（问题 2）
│   ├── README.md                   原理 / 安装 / 验证 / 已知限制
│   ├── src/                        FodGesture.java + 打包脚本 + 编译脚本
│   ├── release/                    KernelSU 模块 / jar / 粘贴脚本
│   ├── docs/                       安装说明 + 完整逆向报告
│   ├── evidence/                   反编译关键类（MzGestureController、Udfps* …）
│   └── tools/                      设备端时间线测量脚本
└── gesture-wake/                 双击亮屏 / 抬腕亮屏（问题 3）
    ├── README.md                   机制 / 部署 / 验证 / 已知限制
    ├── src/                        MiGestureBridge.java + RRO 源 + 两个构建脚本
    ├── release/                    KernelSU 模块 / jar / 静态 RRO / 运行时覆盖脚本 / 整包
    ├── docs/                       总整理 + 完整根因分析 + 修改清单
    ├── evidence/                   反编译关键类（MzUtils、两个 Controller、页面 XML …）
    └── tools/                      一键体检脚本
```

---

## 五、快速开始

### 1. 指纹不可用

```sh
# 把 mifod-bridge/prebuilt/ 里的三个文件放到 /system：
#   fod-bridge.jar → /system/etc/mifod/fod-bridge.jar
#   mifod          → /system/bin/mifod          (chmod 755)
#   mifod.rc       → /system/etc/init/mifod.rc
# 重启；或手动起一次看日志：
su -c "CLASSPATH=/system/etc/mifod/fod-bridge.jar app_process64 /system/bin MiFodBridge"
su -c "tail -f /data/vendor/mifod/mifod.log"
```

### 2. 指纹动画不可用

```sh
# 推荐：KernelSU 管理器安装 fod-animation/release/fodgesture-module.zip，重启
# 或：把 fod-animation/release/fod_paste.sh 整段粘到 root 终端执行（不用装模块）
# 验证：
su -c "tail -20 /data/local/tmp/fodgesture/watch.log"
```

### 3. 双击 / 抬腕亮屏不可用

```sh
# 推荐：KernelSU 管理器安装 gesture-wake/release/migesture-ksu.zip（+ 可选 sensortypeoverlay-ksu.zip），重启
# 模块会在每次开机自动起桥并重建运行时覆盖 —— 重启后开关不会再消失
# 验证：
su -c "tail -20 /data/local/tmp/migesture/watch.log"
su -c "dmesg | grep Touch_Doubletap_Mode | tail -3"
```

也可以只救急（当次开机有效）：`su -c 'sh gesture-wake/release/apply_overlay.sh'`，
或直接打开独立页「抬腕亮屏」：`su -c 'sh gesture-wake/tools/start_raise.sh'`。

---

## 六、开源协议

本项目采用 **GNU General Public License v3.0**（`GPL-3.0-or-later`），全文见 [`LICENSE`](LICENSE)。

你可以自由使用、修改、再分发（含商用）；但**分发衍生作品时必须同样以 GPL 开源，并提供完整源码**。
本软件不提供任何担保。

> **例外说明**：`fod-animation/evidence/` 下是**第三方程序的反编译产物**
> （AOSP SystemUI / Flyme 组件），仅为说明问题定位过程而保留，**不适用**本项目的 GPL 授权，
> 版权归各自权利人所有。其余全部内容（源码、脚本、文档、构建产物）均按 GPL-3.0 授权。

---

## 七、免责声明

- 三者都需要 **root**，都会读写系统分区或常驻后台，操作前请自行备份。
- 已在自己机器（Xiaomi 15 / Flyme 12 移植版 / Android 16）实测可用；**不同机型、不同 ROM 的
  binder 事务号、服务名、类名可能不同**，请先按各自 `README` 里的验证步骤确认再长期使用。
- 代码按"现状"提供，因使用本仓库造成的任何后果由使用者自行承担。

---

*关键词：Flyme 移植 · 超声波指纹 · 小米 15 · FOD · UDFPS · 指纹解锁动画 · IGestureManager · MiFod Bridge · 双击亮屏 · 抬腕亮屏 · ITouchFeature · pickup · RRO · overlayable*
