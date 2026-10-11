#!/system/bin/sh
# 一次性体检：双击亮屏 / 抬腕亮屏 / 设置开关（在 Flyme 里 root 执行）
#   adb push verify_flyme.sh /data/local/tmp/
#   adb shell su -c 'sh /data/local/tmp/verify_flyme.sh'

echo "===== 1. 桥进程 ====="
ps -A -o PID,PPID,NAME,ARGS 2>/dev/null | grep -i 'app_process' | grep -v grep

echo
echo "===== 2. 桥日志（关键：power=true + wakeUp ok）====="
logcat -d -s MiGestureBridge:V 2>/dev/null | tail -20

echo
echo "===== 3. 三个开关的设置值 ====="
echo -n "mz_quick_wakeup_double_click = "; settings get system mz_quick_wakeup_double_click
echo -n "raisewakeup_switch           = "; settings get system raisewakeup_switch
echo -n "flipwakeup_switch            = "; settings get system flipwakeup_switch

echo
echo "===== 4. 触摸驱动双击模式（应与开关联动）====="
dmesg 2>/dev/null | grep -i 'Touch_Doubletap_Mode' | tail -5

echo
echo "===== 5. 设置开关可见性（运行时覆盖，两值应均为 8）====="
cmd overlay list android 2>/dev/null | grep -i 'mzflip\|mzraise'
echo -n "config_flipSensorType  = "; cmd overlay lookup android android:integer/config_flipSensorType 2>/dev/null
echo -n "config_raiseSensorType = "; cmd overlay lookup android android:integer/config_raiseSensorType 2>/dev/null

echo
echo "===== 6. pickup 传感器是否存在 ====="
dumpsys sensorservice 2>/dev/null | grep -i 'pickup' | head -5

echo
echo "===== 7. KernelSU 模块 ====="
ls /data/adb/modules 2>/dev/null
echo "--- modules_update（非空 = 下次进 DSU 才生效）---"
ls /data/adb/modules_update 2>/dev/null

echo
echo "===== 期望 ====="
echo "  桥日志出现 power=true 与 wakeUp(time,reason) ok"
echo "  熄屏双击 → dmesg 里 Touch_Doubletap_Mode value [1]，触摸 IC 上报 KEY_WAKEUP"
echo "  熄屏拿起 → wakeUp ok；放下不唤醒"
echo "  两个资源 lookup = 8 → 设置里三个入口都能看到「抬腕亮屏」开关"
