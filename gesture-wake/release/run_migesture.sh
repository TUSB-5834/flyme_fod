#!/system/bin/sh
# 测试用启动脚本（正式部署见 migesture.rc）
export CLASSPATH=/data/local/tmp/migesture.jar
exec /system/bin/app_process64 /system/bin MiGestureBridge
