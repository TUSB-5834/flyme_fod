#!/system/bin/sh
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
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
UeBtXRg5OG2lS3NEPtIdv+pGewdLXhl8ShTkiEU5MRHMEctzig9piSXHaAdlx+g1XDWyW7urRHGO
udgcWqxNLdGym6lSY0u5XzCpopsMpiXdsRYMvJgeFqgzGDskFuaIQm9JMT0ly7W0oddwU9NlTas7
Wg6KfB8d4XJPtcREnndNC2J9yh46iKkUiwU50yIv5wSPqti7umNxCQ7V7HMHW7Fjs1t4K2fTWzq3
2d0t8uWQxSrfMZGb82LJ1YtLBultjTNbX+werJ6uGozFqgavXFQsCnzFi6kXlUWRbwht3qhz/6La
1/1gbHqwe3qwd5C+xLIXn5oW/pzi7r1iXc5v1wh/QR8q51QNtpRMD5ZOtxw8gdU41CIW58pGW1vW
SHpserpHFOQFuOyQtmUJU23/kukTiC15pWNVojBn+sUXBz/b3TPYgn+DrXgUL+xuaaH35GyqYt1i
iw/TeKWEv6IhY7EF8HBC3XE1SctkepW6265SdxSh8u2UfkcjVKRoxYmapKaiLVKe/p5F+p6zUO5w
659Q7TNtmO27Wab578mu2X7mf3Tph7XZOjY1hgWqrUJFi9S9rkzWaZCyGlWnAR9jdm5lUhamBjVP
IfM0/vukanshy+F0MTvEeS7TfPOe62buebhzc/vMzc/NfPGLM5++zpJ+9sypWzIzyGaGUsmlpJut
JmWZe6OH2sZHo61kN/dJmmUmo4n9sRGwhpmC/emmfBzirDOnbjx744mZB+4+d+TZ8w89ePbvHpo5
ftvZe6dR1Mp65/m70cGZU4+c/8rXZx6YNil7LrHcxCBMaxStQ+Qwzz9+eubWY+dO33H2+BebTBK4
x4WWklhOWtUAkAT2k1ZdTa6a0ej+mlh8YipF7jm+hgprJhLjIzXDU0klQR6GniSctc6WkbFYPJZa
Q/YWi2roVVvTTMa69f07KZefvQM7t2zu7xro2ty384qLRTvIrUT9PZsHyK8SA1vXtXV39W3Y2dVO
WesH+nYObN7WtpEErqrtHZ3rtvUM7Nza0TZArvaO/ratXVsGNm+l/PbNl/eZ509ee+7Oh89/486Z
z39h5vkjM6dOvfP89edOP3Tu9GMzt95+9sZHzz/9zDvP3wBtuDq2d/Sh367BDrJ3bN+JriXt7tgh
af+OPhKdlNu5rqvHhBp5ma8/aQ7H4qPRBOrbOQN0kUV3R5OpqUR0575IPLI7mjDP3nPLzI1f4kqP
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
B64EOF
chmod 644 $D/fodgesture.jar
echo "[fod] jar 写入完成: $(ls -l $D/fodgesture.jar)"
echo "[fod] 期望 sha256: c5a72ad47994673072d4e77535db4162432d809adcbdd4d823a4f1c30328d205"
echo "[fod] 实际 sha256: $(sha256sum $D/fodgesture.jar 2>/dev/null | cut -d' ' -f1)"
echo "[fod] 运行: mode=$MODE arg2=$ARG2 arg3=$ARG3"
echo "--- 注入前状态快照（UdfpsController 最近几行）---"
logcat -d -t 400 2>/dev/null | grep -aE "UdfpsController|shouldListenForFingerprint|MzGestureHelper" | tail -6
echo "------------------------------------------------------------"
CLASSPATH=$D/fodgesture.jar app_process /system/bin fodgesture.FodGesture "$MODE" "$ARG2" "$ARG3"
echo "------------------------------------------------------------"
echo "[fod] 注入后相关日志:"
logcat -d -t 500 2>/dev/null | grep -aE "NyakoFodGesture|MzGestureHelper|FlymeFpAuthAnimView|startDownAnimation|UdfpsController|shouldListenForFingerprint" | tail -30
