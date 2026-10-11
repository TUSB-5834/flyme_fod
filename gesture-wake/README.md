# 双击亮屏 / 抬腕亮屏 —— MiGestureBridge + SensorTypesOverlay

> 验证环境：Xiaomi 15（`dada` / 24129PN74C）· Flyme 12.6.0.0A 移植版 · Android 16 · KernelSU
>
> 一页汇总：[`docs/双击亮屏与抬腕亮屏-总整理.md`](docs/双击亮屏与抬腕亮屏-总整理.md)
> 完整推导、复现命令与全部实测日志：[`docs/双击亮屏与抬腕亮屏-根因分析.md`](docs/双击亮屏与抬腕亮屏-根因分析.md)

移植之后这两个功能都是**哑的**，而且成因完全不同：

| # | 问题 | 现象 | 根因 | 方案 |
|---|---|---|---|---|
| 1 | **双击亮屏** | 熄屏双击毫无反应 | Flyme 只往 `/sys/class/meizu/tp/gesture_control` 写（小米机器没有这个节点）→ 触摸 IC 从未进入双击模式 | root 桥下发小米触摸 HAL `ITouchFeature.setModeValue(0, 14, 1)`（驱动里 mode 14 = `Touch_Doubletap_Mode`） |
| 2 | **抬腕亮屏** | 熄屏拿起没反应，而且设置里连开关都没有 | Flyme 依赖魅族私有传感器 `65538/65539`（本机不存在）→ `MzUtils.supportRaiseFlipFeature()` 为 false，开关被隐藏、`RaiseFlipperController` 注册 null；另外小米 `pickup` 的语义与魅族**相反**（`1.0=拿起` vs 魅族 `2=拿起`），不能直接复用 Flyme 的控制器 | 桥自己订阅 `xiaomi.sensor.pickup(33171036)`：熄屏收到 `1.0` 就 `PowerManager.wakeUp()`、`2.0`（放下）忽略；再用 RRO 覆盖那两个资源，把设置里的开关找回来 |

两个功能共用一个常驻桥 **MiGestureBridge**，并且**跟随 Flyme 自己的开关**：
`mz_quick_wakeup_double_click`（双击）、`raisewakeup_switch` / `flipwakeup_switch`（抬手）。

---

## 一、机制

### 双击亮屏

```
设置开关 mz_quick_wakeup_double_click
  └─ MiGestureBridge（app_process, root）
       └─ vendor.xiaomi.hw.touchfeature.ITouchFeature.setModeValue(touchId=0, mode=14, value=0/1)
            └─ 驱动 Touch_Doubletap_Mode → IC 进入 DC_ENABLE_WAKEUP_GESTURE_MODE
                 └─ 双击 → 上报 KEY_WAKEUP(143)（/dev/input/event7, Xiaomi_Touch_Input_0）→ 系统点亮
```

（Flyme 原本的通道是写魅族 sysfs 节点，那条路在这台机器上不存在 —— 证据见 `evidence/`。）

### 抬腕亮屏

```
设置开关 raisewakeup_switch / flipwakeup_switch
  └─ MiGestureBridge → SensorManager.registerListener(xiaomi.sensor.pickup = 33171036, wakeup)
       └─ 熄屏收到 value = 1.0（拿起）→ PowerManager.wakeUp()（3 条反射路径 + /system/bin/input keyevent 224 兜底）
          value = 2.0（放下）忽略；屏幕已亮时不重复唤醒
```

### 设置里的「抬腕亮屏」开关为什么消失、怎么找回

`MzUtils.supportRaiseFlipFeature()` 读 `android:integer/config_flipSensorType`（魅族 `65538`）与
`config_raiseSensorType`（`65539`），并要求两者对应的 `getDefaultSensor()` 非 null —— 本机没有这两种传感器
→ 判定"设备不支持" → 开关被 `removePreference` 移除。把这两个资源**覆盖成本机存在的 `proximity(8)`** 即可：
它的取值只有 `0.0 / 5.0`，对 Flyme 那套 `1/2/4/5` 分支是**惰性**的，真正的唤醒由桥负责，不会被 Flyme 误触发。

- **永久**：ROM 内静态 RRO —— `release/SensorTypesOverlay-signed.apk` 放进 `product/overlay/`
- **root / 模块**：运行时 `cmd overlay fabricate` + `enable`；两个 KernelSU 模块的 `service.sh` 会在**每次开机重建**
- ⚠️ `cmd overlay fabricate` 造出来的覆盖**不跨重启**（实测：重启后 `lookup` 又回到 `65538/65539`，
  `cmd overlay list` 里也不见了）—— "重启后设置里的开关又没了"就是这个原因，**不能只跑一次**。

---

## 二、文件

```
gesture-wake/
├── README.md                          本文
├── src/
│   ├── make_module_zip.py             打包 KernelSU 模块 zip（正斜杠路径 + 0755/0644 权限）
│   ├── migesture/
│   │   ├── MiGestureBridge.java        全部逻辑（单文件）
│   │   ├── README.md                   设置联动 / 开机 power 服务竞态 / 编译说明
│   │   ├── build_migesture.ps1         javac + jar + d8 → migesture.jar（再重打模块 zip）
│   │   └── ksu/                        KernelSU 模块骨架（module.prop / service.sh / uninstall.sh）
│   └── sensortype-overlay/
│       ├── AndroidManifest.xml         RRO 声明（targetPackage=android, isStatic=true, priority=601）
│       ├── res/values/configs.xml      两个 integer = 8
│       ├── overlay.keystore            签名用自签密钥（口令未随仓库发布，重签可自建，见下）
│       ├── build_overlay.ps1           aapt2 + zipalign + apksigner → SensorTypesOverlay-signed.apk
│       ├── ksu/                        KernelSU 模块骨架（只做开机重建覆盖）
│       └── README.md
├── release/
│   ├── migesture-ksu.zip               KernelSU 模块：起桥 + 每次开机重建覆盖
│   ├── sensortypeoverlay-ksu.zip       KernelSU 模块：只做覆盖重建（APK 也附在里面供打 ROM）
│   ├── migesture.jar                   桥（dex）
│   ├── migesture.rc                    ROM init 服务定义（class late_start / user root / seclabel su:s0）
│   ├── run_migesture.sh                手动起一次（调试）
│   ├── SensorTypesOverlay-signed.apk   静态 RRO（放进 ROM 的 product/overlay，永久）
│   ├── product@overlay@SensorTypesOverlay.apk@idmap
│   │                                   预生成 idmap（万一 ROM 不自动生成时放进 /data/resource-cache/）
│   ├── apply_overlay.sh                当次开机生效的运行时覆盖
│   └── 双击亮屏与抬腕亮屏-修复包.zip     整包（文档 + 源码 + 产物，一次拿走）
├── docs/
│   ├── 双击亮屏与抬腕亮屏-总整理.md      一页汇总（含三次自我更正）
│   ├── 双击亮屏与抬腕亮屏-根因分析.md    完整分析 + 全部实测证据 + 复现命令
│   └── 修改清单.md                      每个产物改了什么 / SHA-256 / 怎么验的
├── evidence/                          反编译证据（第三方产物，不适用本仓库 GPL，见文末）
└── tools/
    ├── verify_flyme.sh                一键体检（7 项，含期望输出）
    └── start_raise.sh                 直接打开「抬腕亮屏」独立页（不需要任何覆盖）
```

---

## 三、部署

### 1. KernelSU 模块（推荐）

```sh
adb push release/migesture-ksu.zip release/sensortypeoverlay-ksu.zip /data/local/tmp/
adb shell su -c 'ksud module install /data/local/tmp/migesture-ksu.zip'
adb shell su -c 'ksud module install /data/local/tmp/sensortypeoverlay-ksu.zip'
# 重启（模块先落到 modules_update/，下一次开机才生效）
```

两个模块的开机脚本都会：等 `sys.boot_completed` → 起桥（`supervisor` 循环保活）→ 重建运行时覆盖 →
`am force-stop com.android.settings`（让设置重新读资源）。**装任一个就够**，两个都装也无害（幂等）。

日志：`/data/local/tmp/migesture/watch.log`（桥）、`/data/local/tmp/sensortypeoverlay.log`（覆盖重建）。

### 2. ROM 内置（永久，不依赖 root）

| 文件 | 去处 |
|---|---|
| `release/migesture.jar` | `/system/etc/migesture/migesture.jar` |
| `release/migesture.rc` | `/system/etc/init/migesture.rc` |
| `release/SensorTypesOverlay-signed.apk` | `product/overlay/`（或 `vendor/overlay/`） |

> ROM 里用 init 起服务时**不要**在 `on boot` 早期就起 —— `system_server` 的 power 服务那时还没注册，
> `getSystemService(POWER_SERVICE)` 会返回 null 且不会自恢复（症状：pickup 事件收得到但永远不唤醒）。
> 用 `class late_start`；桥内部另有 60 秒等待与兜底补取。

### 3. 只救急（当次开机有效）

```sh
adb shell su -c 'sh release/apply_overlay.sh'    # 立刻恢复设置里的抬腕/翻转开关
adb shell su -c 'sh tools/start_raise.sh'        # 直接打开独立页「抬腕亮屏」（无需任何覆盖）
```

---

## 四、验证

```sh
adb push tools/verify_flyme.sh /data/local/tmp/
adb shell su -c 'sh /data/local/tmp/verify_flyme.sh'
```

| 看什么 | 期望 |
|---|---|
| `logcat -s MiGestureBridge:V` | `powerManager acquired (late)` → `ready (…)` → `pickup event value=1.0 screenOn=false power=true` → `wakeUp(time,reason) ok` |
| `dmesg \| grep Touch_Doubletap_Mode` | 与开关联动 `value [1]` / `[0]` |
| `cmd overlay lookup android android:integer/config_raiseSensorType` | `8`（flip 那个同样 = 8） |
| 设置 → 显示与亮度 | 「双击亮屏」与「抬腕亮屏」都在 |
| 实测 | 熄屏双击亮屏；熄屏拿起亮屏；**把开关关掉后两者都失效** |

---

## 五、已知限制

1. **翻转亮屏没做**：本机没有 `flip_status` 传感器，只有 `screen_down(33171037)`，而它**注册即上报 `1.0`** ——
   映射给 Flyme 会被当成"翻到正面"立刻亮屏。要做得另找信号源。
2. **「息屏显示和锁屏」页在本移植上打不开**：`dumpsys package` 里能看到那个组件，但 `am start` 直接报
   `Activity class ... does not exist`，`pm enable` 之后再试仍然如此。「双击亮屏」请到 **显示与亮度** 看；
   「副屏」页里的同名开关属于魅族副屏硬件，本机没有。
3. **运行时覆盖不跨重启**（见上），所以别只跑一次 `apply_overlay.sh` 就以为好了 —— 用模块或静态 RRO。
4. **静态 RRO 在真实 ROM 启动后的最终效果**需在重打包 ROM 时确认一次（在 DSU 里没法重启验证这条路）。
   已经验到的是：APK 内容与资源映射正确（`idmap2` 映射 `0x010e01db/0x010e01f3 → 0x7f010000/0x7f010001`），
   并且设备上 OMS 会给 `/product/overlay` 的静态覆盖正常生成 idmap（对照魅族自己的包，`Constraints: None`）。
5. `overlay.keystore` 的口令**未随仓库发布**。重签用自己的密钥即可 —— 静态覆盖不校验平台签名
   （只有 ROM 另有校验时才需要平台签名）；`build_overlay.ps1 -NewKey` 会生成一把新密钥并把口令设成
   `flymeport`。
6. 需要 **root**。桥是常驻 `app_process` 进程；`settings` 的读取走 `/system/bin/settings` CLI +
   轮询 `/data/system/users/0/settings_system.xml` 的 `mtime+size`（`app_process` 访问 settings provider
   会被 AMS 拒，且该文件是 ABX 二进制，不能直接解析）。

---

## 六、开源协议

与本仓库一致：**GPL-3.0-or-later**，全文见 [`../LICENSE`](../LICENSE)。

> **例外**：`evidence/` 下是第三方程序的反编译产物（Flyme `Settings.apk` 等），仅为说明问题定位过程而保留，
> **不适用**本项目的 GPL 授权，版权归各自权利人所有（与 `fod-animation/evidence/` 同一性质）。
