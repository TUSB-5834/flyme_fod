#!/system/bin/sh
echo "=== 设备当前时间 / 开机时长 ==="
date
echo "uptime: $(cat /proc/uptime)"
echo
echo "=== logcat 缓冲区最早的一行（可看作本机最近一次清空/开机后最早日志）==="
logcat -d 2>/dev/null | head -2
echo
echo "=== 本次开机后 logwatch 的启动时刻 ==="
logcat -d 2>/dev/null | grep -a "NyakoFodGesture" | head -4
echo
echo "=== 已安装模块的 service.sh ==="
cat /data/adb/modules/fodgesture/service.sh
echo
echo "=== 模块日志 ==="
ls -l /data/local/tmp/fodgesture/ 2>/dev/null
tail -6 /data/local/tmp/fodgesture/watch.log 2>/dev/null
echo
echo "=== system_server / 指纹 HAL 启动时刻（对比开机）==="
logcat -d 2>/dev/null | grep -aE "QFP.*(init|FingerprintHal|qbt|open)|BiometricService|fingerprint" | head -8
