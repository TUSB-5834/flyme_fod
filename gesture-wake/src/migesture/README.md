# MiGestureBridge —— 抬起唤醒 / 双击唤醒 桥接服务

把 Flyme 上失效的「双击亮屏」「抬手亮屏」桥接到小米 HyperOS 的硬件接口。
与移植方已有的 mifod（`/system/etc/mifod/fod-bridge.jar` + `/system/etc/init/mifod.rc`）完全同一套模式。

## 它做什么

| 功能 | 做法 |
|---|---|
| 双击亮屏 | `ITouchFeature.setModeValue(touchId=0, mode=14, value=1)`（驱动 feature = `Touch_Doubletap_Mode`）→ Synaptics IC 进入 `DC_ENABLE_WAKEUP_GESTURE_MODE` → 双击时 IC 在 `Xiaomi_Touch_Input_0` 上报 `KEY_WAKEUP(143)` → 系统自行亮屏 |
| 抬手亮屏 | 订阅小米私有传感器 `xiaomi.sensor.pickup(33171036, Wakeup)`；熄屏状态下收到 `value==1.0`（拿起）时调 `PowerManager.wakeUp()` |

**完全跟随 Flyme 设置开关**（与 Settings/SystemUI 同一套 key）：

| 设置项 | 桥的行为 |
|---|---|
| `mz_quick_wakeup_double_click` | `!=0` → `setModeValue(0,14,1)`；`==0` → `setModeValue(0,14,0)` |
| `raisewakeup_switch` / `flipwakeup_switch` | 任一为 1 → 注册 pickup 监听；都为 0 → 注销监听 |

- 设置读取用 `settings get system <key>` CLI：`app_process` 不是注册过的 app，`ContentResolver` 访问 settings provider 会被 AMS 拒绝（`Unable to find app for caller`），且落盘文件是 ABX 二进制。
- 变化检测：轮询 `/data/system/users/0/settings_system.xml` 的 `mtime+size`（2 秒），变了才重新读。

### ⚠️ 开机竞态：power 服务必须等到

KSU 的 `service.sh` 实测在 **uptime 5.6 秒**就被拉起，那时 `system_server` 的 Java 服务还没注册完，
`ContextImpl.getSystemService(POWER_SERVICE)` 会**返回 null 且不会自己恢复**（`ServiceNotFoundException` 被吞掉），
而 `sensorservice` / `touchfeature` 是 init 起的 native 服务，早就可用 → 表现为
**"pickup 事件收得到、但永远不唤醒"**（`wakeUp()` 三个出口一句日志都没有，`screenOn=false` 只是 null 造成的假象）。

两道保险：

1. 桥内 `ensurePower()`：启动时最多等 60 秒；之后每次传感器事件、每 10 分钟的重申任务里再兜底补取，
   补到时顺便**重读一次设置**（开机早期那次读设置也可能只拿到默认值）。
2. `service.sh` 先等 `sys.boot_completed=1`（最多 180 秒）再起桥。

自检：`logcat -s MiGestureBridge:V` 里出现 `power=true` + `wakeUp(time,reason) ok` 才算正常；
只看到 `pickup event value=1.0 screenOn=false power=false` 就是又踩到这个竞态了。

> ⚠️ **DSU 测试环境**：本项目在 DSU 里跑 Flyme，`adb reboot` 会退出 DSU 回到主系统 —— 不要自己发 reboot。
> KernelSU 模块装完先落在 `modules_update/`，**下一次进 DSU 才生效**，重启由使用者自己的 DSU 流程完成。

- `pickup` 语义（实测）：**1.0 = 拿起，2.0 = 放下**；只在 `!PowerManager.isInteractive()` 时唤醒，放下不会误亮屏。
- 双击模式是驱动运行时状态，**重启会丢**，所以桥在「启动时 + 每次熄屏 + 每 10 分钟」重申一次。
- 不需要改 framework / RRO / 键盘映射，不需要 root 之外的额外权限（设备已有 KernelSU；正式 ROM 里 init 以 root 起服务即可）。
- 与 Flyme 的设置项无关：`mz_quick_wakeup_double_click` 只会去写不存在的 `/sys/class/meizu/tp/gesture_control`，改它没有任何效果。

## 文件

```
migesture/src/MiGestureBridge.java   源码
migesture/dist/migesture.jar         编译产物（含 classes.dex）
migesture/dist/migesture.rc          init 服务定义
migesture/dist/migesture-ksu.zip     KernelSU 模块（开机自启桥 + 每次开机重建运行时覆盖）
```

## 安装方式 A：内置进 ROM（正式方案）

```sh
cp migesture.jar /system/etc/migesture/migesture.jar
cp migesture.rc  /system/etc/init/migesture.rc
chmod 0644 /system/etc/migesture/migesture.jar /system/etc/init/migesture.rc
# SELinux 文件标签沿用 system_file，与 mifod 的 fod-bridge.jar 相同
```

`migesture.rc`：
```
service migesture /system/bin/app_process64 /system/bin MiGestureBridge
    class late_start
    user root
    group root system input graphics readproc
    seclabel u:r:su:s0
    setenv CLASSPATH /system/etc/migesture/migesture.jar
```

## 安装方式 B：KernelSU 模块（不重打包，重启仍生效）

`migesture-ksu.zip` 直接丢进 KernelSU 管理器安装（或 `su -c 'ksud module install /data/local/tmp/migesture-ksu.zip'`）。

写法与移植方已有的 `fodgesture` 模块一致：jar 复制到 `/data/local/tmp/migesture/`，用 supervisor 循环保活（挂了 2 秒后自动重启），日志在 `/data/local/tmp/migesture/watch.log`：

```sh
MODDIR=${0%/*}
D=/data/local/tmp/migesture
cp -f $MODDIR/migesture.jar $D/migesture.jar
pkill -f 'MiGestureBridge'
while true; do
  CLASSPATH=$D/migesture.jar app_process64 /system/bin MiGestureBridge >> $LOG 2>&1
  sleep 2
done &
```

卸载：`uninstall.sh`（`pkill -f MiGestureBridge` + 清理临时目录），或 KernelSU 管理器里移除模块。

> 注意：KernelSU/Magisk 模块**不能**通过 `/system/etc/init/*.rc` 添加 init 服务（模块挂载发生在 init 解析 rc 之后），所以模块版用 `service.sh` 后台拉起，效果等价。
> 如果只想内置进 ROM，用上面方式 A 的两个文件即可，不需要模块。

## 调试参数

```sh
CLASSPATH=/system/etc/migesture/migesture.jar /system/bin/app_process64 /system/bin MiGestureBridge probe    # 打印传感器状态后退出
CLASSPATH=/system/etc/migesture/migesture.jar /system/bin/app_process64 /system/bin MiGestureBridge set14    # 只下发双击模式后退出
```

日志 tag：`MiGestureBridge`（`adb logcat -s MiGestureBridge:V`）。

## 编译

```sh
javac -source 8 -target 8 -classpath <android.jar> -d classes src/MiGestureBridge.java
jar cf classes.jar -C classes .
d8 --min-api 34 --lib <android.jar> --output migesture.jar classes.jar
```

## 已在真机验证（Xiaomi 15 / dada，Flyme 12.6.0.0A）

```
MiGestureBridge: touchfeature service bound
MiGestureBridge: setModeValue(0,14,1) -> 0
MiGestureBridge: register pickup pickup  Wakeup (wakeup=true) -> true
MiGestureBridge: ready
MiGestureBridge: pickup event value=1.0 screenOn=false
MiGestureBridge: wakeUp(time,reason) ok
MiGestureBridge: pickup event value=2.0 screenOn=false      ← 放下，不唤醒
```

双击侧由 `getevent` 独立验证：熄屏双击 → `EV_KEY KEY_WAKEUP DOWN/UP`。
