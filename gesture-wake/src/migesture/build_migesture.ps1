# 编译 MiGestureBridge.java → migesture.jar（dex），并重新打出 KernelSU 模块 zip
#
#   .\build_migesture.ps1
#   .\build_migesture.ps1 -Sdk "D:\android-sdk" -Jdk "C:\jdk-21\bin"
#
# 产物：
#   ..\..\release\migesture.jar         （dex，ROM 内置或手动运行都用它）
#   ..\..\release\migesture-ksu.zip     （KernelSU 模块，含 jar + service.sh + module.prop）
param(
    [string]$Sdk     = "C:\Users\TUSB\Desktop\agent\environment\android-sdk",
    [string]$Jdk     = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin",
    [string]$Release = (Join-Path $PSScriptRoot "..\..\release")
)

$ErrorActionPreference = "Continue"
$src   = Join-Path $PSScriptRoot "MiGestureBridge.java"
$ksu   = Join-Path $PSScriptRoot "ksu"
$build = Join-Path $PSScriptRoot "build"
$api   = "$Sdk\platforms\android-36\android.jar"
$d8    = "$Sdk\build-tools\35.0.0\d8.bat"

"--- 工具检查 ---"
Test-Path "$Jdk\javac.exe"; Test-Path "$Jdk\jar.exe"; Test-Path $d8; Test-Path $api

Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$build\classes" | Out-Null

# 说明：源码是 Java 8 语法；`-classpath android.jar` 保证引用到的是 Android 的类
#（不要用 JDK 自带的 android 无关 API，否则 dex 阶段/真机上会缺类）。
"--- javac ---"
& "$Jdk\javac.exe" -source 8 -target 8 -nowarn -encoding UTF-8 -classpath $api -d "$build\classes" $src 2>&1

"--- jar（class → jar） ---"
Push-Location $build
& "$Jdk\jar.exe" cf classes.jar -C classes .
"--- d8（jar → dex，min-api 34） ---"
& $d8 --min-api 34 --lib $api --output migesture.jar classes.jar 2>&1
"--- 产物 ---"
& "$Jdk\jar.exe" tf migesture.jar
Pop-Location
Get-Item "$build\migesture.jar" | Select-Object Name, Length

"--- 同步进模块目录并重打 zip ---"
New-Item -ItemType Directory -Force -Path $Release | Out-Null
Copy-Item "$build\migesture.jar" "$ksu\migesture.jar" -Force
Copy-Item "$build\migesture.jar" "$Release\migesture.jar" -Force
python (Join-Path $PSScriptRoot "..\make_module_zip.py") $ksu (Join-Path $Release "migesture-ksu.zip")

"--- sha256 ---"
"migesture.jar      " + (Get-FileHash "$Release\migesture.jar" -Algorithm SHA256).Hash.ToLower()
"migesture-ksu.zip  " + (Get-FileHash "$Release\migesture-ksu.zip" -Algorithm SHA256).Hash.ToLower()

"--- 校验：dex 里应当能搜到这些字符串 ---"
python -c "import zipfile;d=zipfile.ZipFile(r'$Release\migesture.jar').read('classes.dex');print({s:(s.encode() in d) for s in ['powerManager acquired (late)','late-power','pickup event value=']})"
