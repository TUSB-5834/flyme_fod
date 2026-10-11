import base64, hashlib, os, zipfile

OUT = r"C:\Users\TUSB\Desktop\agent\flyme\fodgesture-out"
JAR = os.path.join(OUT, "fodgesture.jar")
jar_bytes = open(JAR, "rb").read()
sha = hashlib.sha256(jar_bytes).hexdigest()
b64 = base64.b64encode(jar_bytes).decode()
b64_lines = [b64[i:i + 76] for i in range(0, len(b64), 76)]

# ---------------------------------------------------------------- 一次性脚本
header = """#!/system/bin/sh
# ============================================================
#  FOD 指纹解锁动画 —— 补上缺失的"魅族 TP 固件手势"
#  原理：直接调用 android.view.IGestureManager.triggerGesture()
#        （AIDL tx=3）注入 keyCode 413 按下/抬起，
#        让飞我原生链路自己跑：
#          MzGestureController.onGestureTrigger(413) -> 161
#            -> handleGesture(161) -> showFingerprintDownAnim()
#            -> FlymeFpAuthAnimView.startDownAnimation()
#  不 hook、不改 APK、不依赖 NyakoHook 插件。
#
#  用法（锁屏状态下，root）：
#     sh /data/local/tmp/fod_paste.sh logwatch              # ★推荐：监听指纹HAL的FOD触摸，自动注入
#     sh /data/local/tmp/fod_paste.sh                       # 一次性：按下 2.5s 再抬起
#     sh /data/local/tmp/fod_paste.sh pulse 4000            # 保持 4 秒
#     sh /data/local/tmp/fod_paste.sh probe                 # 只查服务在不在
#     sh /data/local/tmp/fod_paste.sh raw 15                # 诊断：各 input 设备的坐标取值范围
#     sh /data/local/tmp/fod_paste.sh dump 15               # 诊断：实时打印触摸坐标
#     sh /data/local/tmp/fod_paste.sh watch                 # 按触摸屏坐标判断（部分机型 FOD 不上报）
#     sh /data/local/tmp/fod_paste.sh pulse 2500 ts         # 换触摸屏形态的 KeyEvent
# ============================================================
MODE="${1:-pulse}"
ARG2="${2:-2500}"
ARG3="${3:-}"
D=/data/local/tmp/fodgesture
mkdir -p $D || exit 1
base64 -d > $D/fodgesture.jar <<'B64EOF'
"""

footer = """B64EOF
chmod 644 $D/fodgesture.jar
echo "[fod] jar 写入完成: $(ls -l $D/fodgesture.jar)"
echo "[fod] 期望 sha256: %s"
echo "[fod] 实际 sha256: $(sha256sum $D/fodgesture.jar 2>/dev/null | cut -d' ' -f1)"
echo "[fod] 运行: mode=$MODE arg2=$ARG2 arg3=$ARG3"
echo "--- 注入前状态快照（UdfpsController 最近几行）---"
logcat -d -t 400 2>/dev/null | grep -aE "UdfpsController|shouldListenForFingerprint|MzGestureHelper" | tail -6
echo "------------------------------------------------------------"
CLASSPATH=$D/fodgesture.jar app_process /system/bin fodgesture.FodGesture "$MODE" "$ARG2" "$ARG3"
echo "------------------------------------------------------------"
echo "[fod] 注入后相关日志:"
logcat -d -t 500 2>/dev/null | grep -aE "NyakoFodGesture|MzGestureHelper|FlymeFpAuthAnimView|startDownAnimation|UdfpsController|shouldListenForFingerprint" | tail -30
""" % sha

with open(os.path.join(OUT, "fod_paste.sh"), "w", newline="\n", encoding="utf-8") as f:
    f.write(header + "\n".join(b64_lines) + "\n" + footer)

# ---------------------------------------------------------------- 短脚本（jar 已在手机上时用）
short = """#!/system/bin/sh
# jar 已经在 /data/local/tmp/fodgesture/fodgesture.jar 时用这个（省去 base64）
D=/data/local/tmp/fodgesture
MODE="${1:-pulse}"; ARG2="${2:-2500}"; ARG3="${3:-}"
CLASSPATH=$D/fodgesture.jar app_process /system/bin fodgesture.FodGesture "$MODE" "$ARG2" "$ARG3"
echo "------------------------------------------------------------"
logcat -d -t 300 2>/dev/null | grep -aE "NyakoFodGesture|MzGestureHelper|FlymeFpAuthAnimView|startDownAnimation" | tail -25
"""
with open(os.path.join(OUT, "fod_run.sh"), "w", newline="\n", encoding="utf-8") as f:
    f.write(short)

# ---------------------------------------------------------------- KernelSU 模块
module_prop = """id=fodgesture
name=FOD Unlock Animation (Meizu gesture injection)
version=v1.0
versionCode=1
author=Nyako
description=Restores the Flyme FOD unlock animation on Xiaomi 15 (dada). Injects the missing TP gesture (keyCode 413 -> 161/162) into android.view.IGestureManager so SystemUI's MzGestureController runs the native SVGA animation. No hooks, no APK patching.
"""

service_sh = """#!/system/bin/sh
# 开机启动 FOD 手势注入器
# 不再等 boot_completed +20s：注入器越早开始监听越好（logcat 未就绪时由看门狗重试），
# binder 在第一次触摸时才解析，所以开机极早期启动是安全的。
MODDIR=${0%/*}
D=/data/local/tmp/fodgesture
LOG=$D/watch.log
mkdir -p $D
cp -f $MODDIR/fodgesture.jar $D/fodgesture.jar || exit 1
chmod 644 $D/fodgesture.jar
export ANDROID_ROOT=/system
export ANDROID_DATA=/data
pkill -f 'fodgesture.FodGesture' 2>/dev/null
(
  echo "=== supervisor start $(date) (uptime $(cut -d' ' -f1 /proc/uptime)s) ===" >> $LOG
  while true; do
    CLASSPATH=$D/fodgesture.jar app_process /system/bin fodgesture.FodGesture logwatch >> $LOG 2>&1
    echo "=== daemon exited $(date), restart in 2s ===" >> $LOG
    sleep 2
  done
) &
"""

uninstall_sh = """#!/system/bin/sh
pkill -f 'fodgesture.FodGesture' 2>/dev/null
rm -rf /data/local/tmp/fodgesture
"""

zip_path = os.path.join(OUT, "fodgesture-module.zip")
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("module.prop", module_prop)
    z.writestr("service.sh", service_sh)
    z.writestr("uninstall.sh", uninstall_sh)
    z.writestr("fodgesture.jar", jar_bytes)

print("jar    :", len(jar_bytes), "B  sha256", sha)
print("paste  :", os.path.getsize(os.path.join(OUT, "fod_paste.sh")), "B")
print("run    :", os.path.getsize(os.path.join(OUT, "fod_run.sh")), "B")
print("module :", os.path.getsize(zip_path), "B")
