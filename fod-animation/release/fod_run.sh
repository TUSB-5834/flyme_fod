#!/system/bin/sh
# jar 已经在 /data/local/tmp/fodgesture/fodgesture.jar 时用这个（省去 base64）
D=/data/local/tmp/fodgesture
MODE="${1:-pulse}"; ARG2="${2:-2500}"; ARG3="${3:-}"
CLASSPATH=$D/fodgesture.jar app_process /system/bin fodgesture.FodGesture "$MODE" "$ARG2" "$ARG3"
echo "------------------------------------------------------------"
logcat -d -t 300 2>/dev/null | grep -aE "NyakoFodGesture|MzGestureHelper|FlymeFpAuthAnimView|startDownAnimation" | tail -25
