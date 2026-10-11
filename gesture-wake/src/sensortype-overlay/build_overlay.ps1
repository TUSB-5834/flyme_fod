# 编译静态 RRO：把 android:integer/config_flipSensorType / config_raiseSensorType 指向 proximity(8)
# 这样 MzUtils.supportRaiseFlipFeature() 返回 true，设置里的「抬腕亮屏」开关就回来了。
#
#   .\build_overlay.ps1 -Framework "C:\path\framework-res.apk"
#   .\build_overlay.ps1 -Framework "..." -NewKey        # 生成新密钥（口令 flymeport）再签名
#
# 产物：..\..\release\SensorTypesOverlay-signed.apk
#       ..\..\release\sensortypeoverlay-ksu.zip（KernelSU 模块：开机重建运行时覆盖 + 附 APK 供打 ROM）
#
# -Framework 必须是**目标 ROM（或设备上 /system/framework/framework-res.apk）**那一份 ——
# 这两个私有资源在 framework-res 里必须存在，aapt2 link 才认得它们。
param(
    [Parameter(Mandatory = $true)][string]$Framework,
    [string]$Sdk     = "C:\Users\TUSB\Desktop\agent\environment\android-sdk",
    [string]$Jdk     = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin",
    [string]$Release = (Join-Path $PSScriptRoot "..\..\release"),
    [switch]$NewKey
)

$ErrorActionPreference = "Continue"
$build = Join-Path $PSScriptRoot "build"
$ksu   = Join-Path $PSScriptRoot "ksu"
$ks    = Join-Path $PSScriptRoot "overlay.keystore"
$aapt2 = "$Sdk\build-tools\35.0.0\aapt2.exe"
$align = "$Sdk\build-tools\35.0.0\zipalign.exe"
$sign  = "$Sdk\build-tools\35.0.0\apksigner.bat"

"--- 工具检查 ---"
Test-Path $aapt2; Test-Path $align; Test-Path $sign; Test-Path $Framework; Test-Path $ks

Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $build | Out-Null

"--- aapt2 compile ---"
& $aapt2 compile --dir (Join-Path $PSScriptRoot "res") -o "$build\res.zip" 2>&1

"--- aapt2 link（静态覆盖：targetPackage=android, isStatic=true, priority=601） ---"
& $aapt2 link -o "$build\SensorTypesOverlay.apk" --manifest (Join-Path $PSScriptRoot "AndroidManifest.xml") `
    -I $Framework -R "$build\res.zip" --auto-add-overlay --min-sdk-version 34 --target-sdk-version 36 2>&1

"--- 校验 APK 里的两条覆盖 ---"
& $aapt2 dump resources "$build\SensorTypesOverlay.apk" | Select-String -Pattern "config_flipSensorType|config_raiseSensorType|\(\)"

"--- zipalign ---"
& $align -p -f 4 "$build\SensorTypesOverlay.apk" "$build\aligned.apk" 2>&1

if ($NewKey) {
    "--- 生成新密钥（storepass/keypass = flymeport） ---"
    & "$Jdk\keytool.exe" -genkeypair -keystore $ks -alias overlay -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass flymeport -keypass flymeport `
        -dname "CN=flyme-port, OU=port, O=flyme, C=CN" 2>&1
}

"--- apksigner sign ---"
# 口令未随仓库发布；未用 -NewKey 时 apksigner 会交互询问 overlay.keystore 的口令。
& $sign sign --ks $ks --out "$build\SensorTypesOverlay-signed.apk" "$build\aligned.apk" 2>&1
& $sign verify --print-certs "$build\SensorTypesOverlay-signed.apk" 2>&1 | Select-Object -First 4

"--- 同步进发布目录与模块 ---"
New-Item -ItemType Directory -Force -Path $Release, "$ksu\product\overlay" | Out-Null
Copy-Item "$build\SensorTypesOverlay-signed.apk" "$Release\SensorTypesOverlay-signed.apk" -Force
Copy-Item "$build\SensorTypesOverlay-signed.apk" "$ksu\product\overlay\SensorTypesOverlay.apk" -Force
python (Join-Path $PSScriptRoot "..\make_module_zip.py") $ksu (Join-Path $Release "sensortypeoverlay-ksu.zip")

"--- sha256 ---"
"SensorTypesOverlay-signed.apk  " + (Get-FileHash "$Release\SensorTypesOverlay-signed.apk" -Algorithm SHA256).Hash.ToLower()

"--- 提示 ---"
"放进 ROM 的 product/overlay/ 即可永久生效（或 vendor/overlay/）。"
"若某个 ROM 构建不会自动为静态覆盖生成 idmap，把 release\product@overlay@SensorTypesOverlay.apk@idmap"
"放到 /data/resource-cache/（命名规则：APK 全路径把 / 换成 @ 再加 @idmap）。"
