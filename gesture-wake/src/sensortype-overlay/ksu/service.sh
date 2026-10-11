#!/system/bin/sh
# 开机重建「抬手/翻转亮屏」开关所需的运行时覆盖
#
# 为什么用运行时覆盖而不是把 APK 塞进 /product/overlay：
#   本机 /product/overlay 本身是 mi_ext 的 overlayfs 挂载点
#   （lowerdir=/mnt/vendor/mi_ext/product/overlay/:/product/overlay），
#   KSU 模块挂上去的文件会被这层挂载遮住 —— 实测模块里的 APK 到不了那里。
#   （APK 仍放在模块里，供重新打包 ROM 时直接取用。）
#
# 为什么不装完一次就够：
#   `cmd overlay fabricate` 造出来的 fabricate-overlay **不保证跨重启存活**
#   （实测：重启后 config_flipSensorType 又回到 65538）——
#   所以每次开机重建一次。这也是「设置里开关又消失了」的原因。
#
# 效果：MzUtils.supportRaiseFlipFeature() 变回 true → 设置 → 显示与亮度 /
#       息屏显示和锁屏 / 独立页「抬腕亮屏」里的相关开关重新出现。
# 实际唤醒由 MiGestureBridge 负责（proximity 的 0.0/5.0 对 Flyme 的判断是惰性的）。

export PATH=/system/bin:/system/xbin:/system/sbin:/sbin:$PATH
LOG=/data/local/tmp/sensortypeoverlay.log

exec >> $LOG 2>&1
echo "=== $(date) overlay re-apply start (uptime $(cut -d' ' -f1 /proc/uptime)s) ==="

# 等开机完成 + 给 OMS 一点时间（最多 ~180s）
i=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ $i -lt 90 ]; do
  sleep 2
  i=$((i + 1))
done

# 覆盖包名会随调用者身份变化（com.android.shell:xxx / android:xxx），
# 所以 fabricate 之后按名字发现再 enable。
apply_once() {
  cmd overlay fabricate --target android --name mzflip_test  android:integer/config_flipSensorType  0x10 8
  cmd overlay fabricate --target android --name mzraise_test android:integer/config_raiseSensorType 0x10 8
  for p in $(cmd overlay list android 2>/dev/null | grep mzflip_test  | cut -d' ' -f2); do
    cmd overlay enable --user 0 "$p"
  done
  for p in $(cmd overlay list android 2>/dev/null | grep mzraise_test | cut -d' ' -f2); do
    cmd overlay enable --user 0 "$p"
  done
}

i=0
while [ $i -lt 30 ]; do
  apply_once
  if [ "$(cmd overlay lookup android android:integer/config_raiseSensorType 2>/dev/null)" = "8" ]; then
    break
  fi
  i=$((i + 1))
  sleep 2
done

# 让设置重新读资源
am force-stop com.android.settings

echo "=== done: flip=$(cmd overlay lookup android android:integer/config_flipSensorType 2>/dev/null) raise=$(cmd overlay lookup android android:integer/config_raiseSensorType 2>/dev/null) (tries=$i) ==="
