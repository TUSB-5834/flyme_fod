#!/system/bin/sh
# 免重启恢复设置里的「抬腕/翻转亮屏」开关（Xiaomi 15 · Flyme 移植）
#
# 原理：两个常规入口（AODAndLockScreenSettings / FlymeRaiseFlipController）
#       用 MzUtils.supportRaiseFlipFeature() 判定能力，该判断读
#       android:integer/config_flipSensorType(65538) / config_raiseSensorType(65539)
#       —— 魅族私有传感器，本机不存在 → 判定不支持 → 隐藏开关。
#       把这两个资源覆盖成 8（proximity，存在）即可恢复。
#       不用 pickup(33171036)：Flyme 拿到它会在"放下"时也唤醒。
#
# ⚠️ 这份覆盖**不跨重启**：AOSP 的 fabricated overlay 重启后不会被重新注册
#    （实测：重启后 cmd overlay lookup 又回到 65538/65539，设置里的抬腕开关随之消失）。
#    长期方案二选一：
#      a) 把 SensorTypesOverlay-signed.apk 放进 ROM 的 product/overlay/（永久）
#      b) 装 migesture / sensortypeoverlay 模块 —— 它们的 service.sh 每次开机自动重建本覆盖

set -u

FLIP=com.android.shell:mzflip_test
RAISE=com.android.shell:mzraise_test

echo "== fabricate =="
cmd overlay fabricate --target android --name mzflip_test  android:integer/config_flipSensorType  0x10 8
cmd overlay fabricate --target android --name mzraise_test android:integer/config_raiseSensorType 0x10 8

echo "== enable =="
cmd overlay enable --user 0 "$FLIP"
cmd overlay enable --user 0 "$RAISE"

echo "== 校验（两个都应为 8）=="
echo -n "config_flipSensorType  = "; cmd overlay lookup android android:integer/config_flipSensorType
echo -n "config_raiseSensorType = "; cmd overlay lookup android android:integer/config_raiseSensorType

echo "== 让设置重读资源 =="
am force-stop com.android.settings

echo
echo "完成。若想直接看独立页（不需要本脚本）："
echo "  am start -n 'com.android.settings/com.meizu.settings.MzSettingsActivity\$FlymeRaiseFlipSettingsActivity'"
echo
echo "撤销："
echo "  cmd overlay disable --user 0 $FLIP"
echo "  cmd overlay disable --user 0 $RAISE"
