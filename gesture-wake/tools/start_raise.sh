#!/system/bin/sh
# 直接打开「抬腕亮屏」独立页（这个页面没有任何能力检测，一直可用）
#
# 注意：必须用 $ 写嵌套类，且从脚本里执行（避免 shell 把 $ 吃掉）
#   adb push start_raise.sh /data/local/tmp/
#   adb shell su -c 'sh /data/local/tmp/start_raise.sh'

am start -n 'com.android.settings/com.meizu.settings.MzSettingsActivity$FlymeRaiseFlipSettingsActivity'
# 等价写法（Activity 声明了 action）：
#   am start -a com.meizu.settings.RAISE_FLIP_SETTINGS
