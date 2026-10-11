# evidence/ —— 第三方反编译产物（仅作证据）

本目录里的 `.java` **不是本项目的源代码**，而是从设备上提取的 APK 反编译出来的产物，
用来支撑 [`../docs/指纹解锁动画-逆向报告.md`](../docs/指纹解锁动画-逆向报告.md) 与
[`../README.md`](../README.md) 里引用的**行号与调用链**。保留它们是为了让结论可复核。

| 文件 | 来源 | 权利 |
|---|---|---|
| `MzGestureController.java` | Flyme SystemUI（`com.flyme.keyguard`） | Flyme / 魅族，专有 |
| `FlymeFpAuthAnimView.java` | Flyme SystemUI（`com.flyme.systemui.biometrics`） | Flyme / 魅族，专有 |
| `FlymeWideFingerPrintUtils.java` | Flyme SystemUI（`com.flyme.systemui.biometrics`） | Flyme / 魅族，专有 |
| `UdfpsController.java` | AOSP SystemUI | Apache-2.0 |
| `UdfpsControllerOverlay.java` | AOSP SystemUI | Apache-2.0 |
| `UdfpsAnimationViewController.java` | AOSP SystemUI | Apache-2.0 |
| `UdfpsKeyguardViewLegacy.java` | AOSP SystemUI | Apache-2.0 |
| `AuthController.java` | AOSP SystemUI | Apache-2.0 |
| `KeyguardUpdateMonitor.java` | AOSP `com.android.keyguard` | Apache-2.0 |

## 授权说明

**本目录不适用本仓库的 GPL-3.0 授权**，各文件版权归其原始权利人（Google / 魅族）所有：

- AOSP 部分为 Apache-2.0，可依该协议使用；
- Flyme 部分为专有软件，仅为**分析与兼容性研究**之目的、以引用形式保留，
  请勿当作可自由再分发的代码使用。
- 若你是权利人并希望移除，提 issue 即可，会立即删除。

## 怎么用

对照报告里的行号阅读，例如找 `fingerprintUsedAsUnlock` / `isFingerprintDetectionRunning` 这两道静默门：

```sh
grep -n "fingerprintUsedAsUnlock\|isFingerprintDetectionRunning\|showFingerprintDownAnim" MzGestureController.java
grep -n "startDownAnimation\|mFlymeFpAuthAnimView" UdfpsKeyguardViewLegacy.java FlymeFpAuthAnimView.java
```

> 反编译产物与原始 smali/字节码可能有细微出入（内联、去糖、合成方法等），
> 结论以报告里同时给出的 smali 证据与实际日志为准。
