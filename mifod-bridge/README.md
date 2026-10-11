# MiFodBridge —— 超声波指纹桥接服务

解决 **非小米 ROM（Flyme / ColorOS 等）移植到小米设备后指纹不可用** 的问题。

小米的超声波指纹要靠小米自己的两个 HAL 服务驱动，移植 ROM 里没人通知它们，指纹硬件就一直"没被叫醒"。
本服务以 root 常驻，跟着生物识别状态去通知它们。

```
MiFodBridge (root, app_process)
   │  ① 注册状态监听
   ├──► IFingerprintService.registerBiometricStateListener(cb)      tx = 0x29  → 拿到 idle / enrolling / keyguard-auth / bp-auth
   │
   │  ② 按状态驱动 HAL
   ├──► ITouchFeature.setModeValue(0, 10, 1|2|0)                    tx = 9     → 触摸芯片 FOD 监听 开(认证/录入)/关
   └──► IXiaomiFingerprint.extCmd(4, 3|1|2|4)                       tx = 1     → 指纹扩展 FOD 模式 AUTH/ENROLL/ENROLL_DONE/OFF
   
   ③ 兜底门控线程（500 ms）
   ├──► ITrustManager.isDeviceLocked()                              tx = 0x0A
   └──► IPowerManager.isInteractive()                               tx = 0x17
        锁定 + 空闲 → auth-on ；解锁且来源是锁屏 → fod-off ；录入中 → 不干预
```

---

## 一、涉及的服务与事务号

| 用途 | 服务名 | DESCRIPTOR | tx |
|---|---|---|---|
| 触摸 FOD 监听模式 | `vendor.xiaomi.hw.touchfeature.ITouchFeature/default` | `vendor.xiaomi.hw.touchfeature.ITouchFeature` | `9` |
| 指纹扩展 FOD 模式 | `vendor.xiaomi.hardware.fingerprintextension.IXiaomiFingerprint/default` | `vendor.xiaomi.hardware.fingerprintextension.IXiaomiFingerprint` | `1` |
| 注册生物识别状态监听 | `fingerprint` | `android.hardware.fingerprint.IFingerprintService` | `0x29` |
| 是否锁屏 | `trust` | `android.app.trust.ITrustManager` | `0x0A` |
| 屏幕是否点亮（interactive） | `power` | `android.os.IPowerManager` | `0x17` |

### 参数取值

| 常量 | 值 | 含义 |
|---|---|---|
| `TOUCH_ID` | `0` | 主屏触摸芯片 |
| `MODE_TOUCH_MONITOR` | `10` | FOD 监听模式开关 |
| `VALUE_OFF / AUTH / ENROLL` | `0 / 1 / 2` | 关闭 / 认证 / 录入 |
| `EXTCMD_FOD_MODE` | `4` | 指纹扩展的 FOD 模式命令 |
| `FOD_MODE_ENROLL / ENROLL_DONE / AUTH / OFF` | `1 / 2 / 3 / 4` | 录入开始 / 录入完成 / 认证 / 关闭 |
| 生物识别状态 `IDLE / ENROLLING / KEYGUARD_AUTH / BP_AUTH / AUTH_OTHER` | `0 / 1 / 2 / 3 / 4` | 空闲 / 录入中 / 锁屏认证 / 生物识别弹窗认证 / 其它来源认证 |

> 事务号是**按 AIDL 方法声明顺序**推出来的（`tx=1` 是首个方法）。换 ROM / 换机型前请先用
> `dumpsys`、`service call` 或对比 `framework` 里的 AIDL stub 复核这些编号。

---

## 二、文件说明

| 文件 | 说明 |
|---|---|
| `MiFodBridge.java` | 全部逻辑，单文件 652 行。无第三方依赖，只用 `Parcel` / `IBinder` 手写事务 |
| `build.sh` | `javac`（source/target 11）→ `d8` → `jar cf fod-bridge.jar classes.dex` |
| `mifod` | 启动脚本：设 `CLASSPATH` 后 `exec app_process64 /system/bin MiFodBridge "$@"` |
| `mifod.rc` | init 服务定义（见下） |
| `prebuilt/fod-bridge.jar` | 编译产物（8,215 B） |
| `prebuilt/fod-bridge.zip` | 可直接刷入的三件套：`fod-bridge.jar` + `mifod` + `mifod.rc` |
| `MiFodBridge.java` 的备份 | 仓库里**没有**保留历史 `.bak`，需要时用 git 历史回溯 |

### `mifod.rc`

```
on post-fs-data
    mkdir /data/vendor/mifod 0770 root root

service mifod /system/bin/app_process64 /system/bin MiFodBridge
    class late_start
    user root
    group root system input graphics readproc
    seclabel u:r:su:s0
    setenv CLASSPATH /system/etc/mifod/fod-bridge.jar
    setenv ALLOW_BP_AUTH 1
    setenv ALLOW_AUTH_OTHER 0
```

### 环境变量开关

| 变量 | 默认 | 作用 |
|---|---|---|
| `ALLOW_BP_AUTH` | `1` | 允许生物识别弹窗（`bp-auth`）时开启 FOD 模式 |
| `ALLOW_AUTH_OTHER` | `0` | 允许其它来源（`auth-other`）时开启 FOD 模式 |

---

## 三、部署

把 `prebuilt/fod-bridge.zip` 里的三个文件放到 `/system`：

| 文件 | 目标 | 权限 |
|---|---|---|
| `fod-bridge.jar` | `/system/etc/mifod/fod-bridge.jar` | `644` |
| `mifod` | `/system/bin/mifod` | `755` |
| `mifod.rc` | `/system/etc/init/mifod.rc` | `644` |

然后重启（init 服务在 `late_start` 启动）。也可以先手动跑一次，看它到底能不能绑上 HAL：

```sh
su -c "mkdir -p /data/vendor/mifod"
su -c "CLASSPATH=/system/etc/mifod/fod-bridge.jar app_process64 /system/bin MiFodBridge"
```

不带参数 = 常驻模式；带参数 = 单次命令：

```sh
su -c "CLASSPATH=/system/etc/mifod/fod-bridge.jar app_process64 /system/bin MiFodBridge auth-on"
# 可选：auth-on | enroll-on | enroll-done | off
```

---

## 四、验证

```sh
# 1) 服务在跑
su -c "ps -A | grep MiFodBridge"

# 2) 日志（也可 /data/vendor/mifod/mifod.log）
su -c "tail -30 /data/vendor/mifod/mifod.log"
```

日志正常长这样：

```
mifod daemon start (ultrasonic), ALLOW_BP_AUTH=1, ALLOW_AUTH_OTHER=0
services bound
FingerprintExtension bound
biometric state listener registered
biometric-state keyguard-auth
keyguard auth-on
extCmd(4, 3) = 0
setModeValue(auth) = 0
```

判读：

| 日志 | 含义 |
|---|---|
| `services bound` | 触摸 HAL 找到了（这一步失败指纹一定不工作） |
| `FingerprintExtension not available, continuing without extCmd` | 只有扩展服务缺失，`setModeValue` 仍会执行；部分机型够用 |
| `setModeValue(...) = 0` | HAL 接受（非 0 要查 HAL 侧） |
| 按指纹时没有 `biometric-state keyguard-auth` | 状态监听没注册上，检查 tx `0x29` 是否对 |
| `keyguard gate: screen on + locked → auth-on` | 兜底门控生效（说明状态监听那条路没走通也没关系） |

---

## 五、已知限制

1. **事务号与服务名是机型/ROM 相关的**：本文数值来自 Xiaomi 15（`dada`）+ Android 16 移植 ROM。
2. 源码注释里写的是 ColorOS 移植场景，机制对 Flyme 移植同样成立（都是"缺了小米指纹服务"）。
3. `debug` 为编译期常量 `true`，日志会一直写 `/data/vendor/mifod/mifod.log`；长期使用可自行改小或加日志轮转。
4. 只处理 FOD（屏下）链路；侧边/后置指纹机型无关。

---

## 六、编译

```sh
# 需要 JDK 11+ 与 Android SDK（platforms/android-35、build-tools/35.0.0）
export ANDROID_HOME=/path/to/android-sdk
./build.sh          # 产物：./fod-bridge.jar
```

---

## 七、开源协议

[GNU General Public License v3.0](../LICENSE)（`GPL-3.0-or-later`）· 不提供任何担保。
