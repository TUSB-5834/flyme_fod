# FodGesture —— 恢复 Flyme 的 FOD 解锁动画

在**小米设备 + Flyme 移植 ROM** 上恢复原生 Flyme 的指纹解锁下滑/上滑动画（`flyme_udfps_auth_anim`）。

- 不改 APK、不 hook、不写 `/system`
- 独立 root 进程 + 一次 binder 调用，走 SystemUI 自己**原本就有**的动画代码
- 支持开机自启（KernelSU 模块），日志可查

> 验证环境：Xiaomi 15（`dada` / 24129PN74C）· Flyme 12 · Android 16 · KernelSU · 1200×2670 @520dpi

---

## 一、根因

Flyme 的动画由 **TP 固件上报 `keyCode 413`** 触发：

```
IGestureManager.triggerGesture(KeyEvent, MotionEvent)          AIDL: DESCRIPTOR=android.view.IGestureManager, SERVICE=gesture_manager, tx=3
  └─ MzGestureController$GestureCallback.onGestureTrigger()    keyCode 413 + action 0 → 161，action 1 → 162
       └─ MzGestureController:83-88  handler → handleGesture(int)
            ├─ :405  fingerprintUsedAsUnlock()                                ← 静默门 1
            ├─ :428  isUdfpsEnrolled() && isFingerprintDetectionRunning()      ← 静默门 2
            └─ :441  showFingerprintDownAnim()
                 └─ KeyguardUpdateMonitor:3698 → AuthController:1182 → UdfpsController:1102
                      └─ UdfpsControllerOverlay:751      isShowing() && getAnimationViewController()!=null   ← 静默门 3
                           └─ UdfpsAnimationViewController:231   getView() instanceof UdfpsKeyguardViewLegacy  ← 静默门 4
                                └─ UdfpsKeyguardViewLegacy:484    mFlymeFpAuthAnimView != null                ← 静默门 5
                                     └─ FlymeFpAuthAnimView.startDownAnimation()   ← 全链唯一会打日志的地方
```

小米的 TP 固件不上报 `413`，所以 `onGestureTrigger()` **从不被调用** —— 动画代码虽然在 ROM 里，
却永远跑不到。整条链上**只有最后一个方法会打日志**，前面任何一道门失败都是静默 `return`。

**方案**：不去补 TP 固件，而是在**触发端**注入同样的手势：

1. 监听指纹 HAL 自己的 FOD 触摸日志（`logcat -s QFP:I` → `onPointerDown` / `onPointerUp`）；
2. 在真手指按下/抬起的**同一刻**，把 `keyCode 413 / action 0|1` 注入 `gesture_manager`；
3. SystemUI 收到后走上面那条原生链路，动画与手指同步。

---

## 二、目录结构

```
fod-animation/
├── src/
│   ├── FodGesture.java           全部逻辑（单文件，无第三方依赖）
│   ├── make_fodgesture_pkg.py    打包：jar + 粘贴脚本 + KernelSU 模块
│   └── build_fodgesture.ps1      Windows 下 javac + d8 + jar 一键编译
├── release/
│   ├── fodgesture-module.zip     ★ KernelSU 模块（开机即启 + 看门狗保活）
│   ├── fodgesture.jar            注入器本体（app_process 运行）
│   ├── fod_paste.sh              免装模块：纯文本整段粘贴即用（自带 jar）
│   └── fod_run.sh                手机上已有 jar 时用
├── docs/
│   ├── 安装说明.md                三种安装方式、验证步骤、开机时序、静默守卫表
│   └── 指纹解锁动画-逆向报告.md     完整逆向过程与证据
├── evidence/                     反编译关键类
│   ├── MzGestureController.java          ← 触发入口与 161/162 映射
│   ├── UdfpsKeyguardViewLegacy.java      ← 动画 view 与 startDownAnimation 调用点
│   ├── UdfpsController.java
│   ├── UdfpsControllerOverlay.java
│   ├── UdfpsAnimationViewController.java
│   ├── AuthController.java
│   ├── KeyguardUpdateMonitor.java
│   ├── FlymeFpAuthAnimView.java           ← 动画实现
│   └── FlymeWideFingerPrintUtils.java
└── tools/
    └── boot_timeline.sh          测量“开机 → 注入器启动”的时间线
```

---

## 三、安装

### 方式 A：KernelSU 模块（推荐，开机自启）

管理器里安装 `release/fodgesture-module.zip` 并启用，重启。模块的 `service.sh` 会在
`late_start` 一执行就拉起注入器（不等 `boot_completed`），并用 `while true` 看门狗保活。

```sh
su -c "grep -a 'supervisor start' /data/local/tmp/fodgesture/watch.log | tail -2"
```

### 方式 B：粘贴脚本（不装模块，一次性）

把 `release/fod_paste.sh` 的内容整段粘到手机的 root 终端里执行。

### 方式 C：已有 jar

```sh
su -c "sh /data/local/tmp/fod_run.sh logwatch"
```

三种方式都会启动 `logwatch` 常驻：`onPointerDown` → 注入 161，`onPointerUp` → 注入 162。

---

## 四、验证

```sh
# 1) 注入器在跑
su -c "ps -A | grep fodgesture"

# 2) 锁屏按指纹，看注入日志
su -c "tail -20 /data/local/tmp/fodgesture/watch.log"
#   期望：HAL onPointerDown -> 161 / FOD 按下 -> 161 (startDownAnimation)

# 3) 动画侧唯一日志点
adb logcat -d | grep -a "FlymeFpAuthAnimView"
#   期望：startDownAnimation mAnimationType=3
```

`FodGesture` 的运行模式：

| 模式 | 用途 |
|---|---|
| **`logwatch`** | **常驻推荐**：监听指纹 HAL 日志，自动注入 161/162 |
| `probe` | 只探测 `gesture_manager` 服务在不在（tx=3 是否可达） |
| `down` / `up` | 手动注入 161 / 162 |
| `pulse [ms] [ts]` | 注入一对 DOWN/UP（DOWN/UP 分开 try/catch，保证 162 一定发出） |
| `raw [sec]` | 打印各 input 设备原始事件（排查触摸上报） |
| `dump [sec]` | 实时打印触摸坐标（对比按 FOD 与按别处） |
| `watch [rect] [ts]` | 按坐标判断 FOD 触摸（**本机型不可用**，见下） |

---

## 五、已知限制

1. **必须用 `logwatch`**：本机型的触摸屏（`event7 Xiaomi_Touch_Input_0`）**不上报 FOD 区域的触摸**，
   FOD 触摸只进指纹 HAL，所以按坐标判断的 `watch` 模式拿不到信号；指纹 HAL 日志是唯一可靠触发源。
2. **不要用震动当判据**：`shouldVibrate(161|162)` 返回 `false`（`MzGestureController`），注入不产生震动。
3. **注入必须在锁屏显示期间**（浮层 `isShowing()` + 指纹检测运行中）：`logwatch` 在真手指按下的同一刻
   注入，天然满足静默门 2/3。
4. **开机后有一小段没有动画**：注入器从 `late_start` 就启动（不再等待），但动画还要求 ROM 自己的
   `isFingerprintDetectionRunning()`（静默门 2）为真 —— 那要等锁屏开始监听指纹。正确预期：
   **锁屏第一次能响应指纹时，动画就应该在**。详见 `docs/安装说明.md` 第 2.4 节。
5. 注入的是**手势事件**，不是"绕过认证"。解锁仍然完全由系统指纹认证决定。

---

## 六、重新编译

```powershell
# Windows + JDK 21 + Android SDK（build-tools 35 / platforms android-36）
# 先按本机路径改 build_fodgesture.ps1 里的 $jdk / $sdk / $py
powershell -File src/build_fodgesture.ps1
# 产物：fodgesture.jar、fod_paste.sh、fod_run.sh、fodgesture-module.zip
```

---

## 七、版本

| 版本 | 变更 |
|---|---|
| v4 | `logwatch` 模式：以指纹 HAL 的 `QFP onPointerDown/onPointerUp` 为触发源；pulse 的 DOWN/UP 分开 try/catch 保证 162 一定发出 |
| v4-r2 | 开机时序修复：`service.sh` 开机即启 + 看门狗保活；binder 解析推迟到第一次触摸（带 10 秒重试），极早期启动不再失败 |

---

## 八、开源协议

[GNU General Public License v3.0](../LICENSE)（`GPL-3.0-or-later`）· 不提供任何担保。

> 例外：`evidence/` 下是第三方程序的反编译产物，仅为说明定位过程而保留，
> **不适用**本项目的 GPL 授权，版权归各自权利人所有（见 [`evidence/README.md`](evidence/README.md)）。
