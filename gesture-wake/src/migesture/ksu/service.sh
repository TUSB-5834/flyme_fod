#!/system/bin/sh
# 开机启动「双击唤醒 / 抬手唤醒」桥
# 与 fodgesture 模块同一套写法：jar 复制到 /data/local/tmp，supervisor 循环保活。
MODDIR=${0%/*}
D=/data/local/tmp/migesture
LOG=$D/watch.log

# 用绝对路径，避免开机早期 PATH 不完整导致 app_process64 找不到
export PATH=/system/bin:/system/xbin:/system/sbin:/sbin:$PATH
export ANDROID_ROOT=/system
export ANDROID_DATA=/data

mkdir -p $D
cp -f $MODDIR/migesture.jar $D/migesture.jar || exit 1
chmod 644 $D/migesture.jar

pkill -f 'MiGestureBridge' 2>/dev/null

# 等开机完成再起。开机早期（实测 uptime 5s 就被 KSU 拉起来）system_server 的 power 服务
# 还没注册，getSystemService(POWER_SERVICE) 会返回 null 且不会自己恢复 —— 表现为
# "pickup 事件收得到但永远唤不醒"。桥内部也有兜底补取，这里是第一道保险。
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $i -lt 180 ]; do
  sleep 2
  i=$((i + 1))
done

# 重建「抬手/翻转亮屏」开关所需的运行时覆盖。
# `cmd overlay fabricate` 造出来的覆盖不跨重启（实测重启后 config_flipSensorType
# 又回到 65538），而设置里那些开关的可见性依赖它 —— 症状就是"重启后开关又没了"。
# 逻辑与 sensortypeoverlay 模块相同；两个模块都装也无害（fabricate 已存在会报错被忽略，
# enable 幂等）。
(
  j=0
  while [ $j -lt 30 ]; do
    cmd overlay fabricate --target android --name mzflip_test  android:integer/config_flipSensorType  0x10 8 >/dev/null 2>&1
    cmd overlay fabricate --target android --name mzraise_test android:integer/config_raiseSensorType 0x10 8 >/dev/null 2>&1
    for p in $(cmd overlay list android 2>/dev/null | grep mzflip_test | cut -d' ' -f2); do
      cmd overlay enable --user 0 "$p" >/dev/null 2>&1
    done
    for p in $(cmd overlay list android 2>/dev/null | grep mzraise_test | cut -d' ' -f2); do
      cmd overlay enable --user 0 "$p" >/dev/null 2>&1
    done
    if [ "$(cmd overlay lookup android android:integer/config_raiseSensorType 2>/dev/null)" = "8" ]; then
      echo "=== overlay re-applied: flip=$(cmd overlay lookup android android:integer/config_flipSensorType 2>/dev/null) raise=8 (tries=$j) ===" >> $LOG
      am force-stop com.android.settings >/dev/null 2>&1
      break
    fi
    j=$((j + 1))
    sleep 2
  done
) &

(
  echo "=== supervisor start $(date) (uptime $(cut -d' ' -f1 /proc/uptime)s) ===" >> $LOG
  while true; do
    CLASSPATH=$D/migesture.jar /system/bin/app_process64 /system/bin MiGestureBridge >> $LOG 2>&1
    echo "=== daemon exited $(date), restart in 2s ===" >> $LOG
    sleep 2
  done
) &
