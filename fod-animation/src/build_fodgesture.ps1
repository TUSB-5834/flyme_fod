$ErrorActionPreference = "Continue"
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin"
$sdk = "C:\Users\TUSB\Desktop\agent\environment\android-sdk"
$py  = "C:\Users\TUSB\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"
$work = "C:\Users\TUSB\Desktop\agent\flyme\fodgesture"
$out  = "C:\Users\TUSB\Desktop\agent\flyme\fodgesture-out"

"--- 工具检查 ---"
Test-Path "$jdk\javac.exe"; Test-Path "$jdk\jar.exe"; Test-Path "$sdk\build-tools\35.0.0\d8.bat"; Test-Path "$sdk\platforms\android-36\android.jar"; Test-Path $py

Remove-Item "$work\build" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$work\build\classes", "$work\build\dex" | Out-Null

"--- javac ---"
& "$jdk\javac.exe" -source 8 -target 8 -bootclasspath "$sdk\platforms\android-36\android.jar" -nowarn -encoding UTF-8 -d "$work\build\classes" "$work\src\fodgesture\FodGesture.java" 2>&1

"--- d8 ---"
$classes = (Get-ChildItem "$work\build\classes\fodgesture\*.class" | ForEach-Object { $_.FullName })
"class 文件数: $($classes.Count)"
& "$sdk\build-tools\35.0.0\d8.bat" --min-api 30 --output "$work\build\dex" @classes 2>&1

"--- 打 jar ---"
Remove-Item "$out\classes.dex" -Force -ErrorAction SilentlyContinue
Copy-Item "$work\build\dex\classes.dex" "$out\classes.dex" -Force
Push-Location $out
Remove-Item "fodgesture.jar" -Force -ErrorAction SilentlyContinue
& "$jdk\jar.exe" cf fodgesture.jar classes.dex 2>&1
Remove-Item "classes.dex" -Force
Pop-Location
Get-Item "$out\fodgesture.jar" | Select-Object Name, Length

"--- 重新打包模块 ---"
& $py "C:\Users\TUSB\Desktop\agent\flyme\make_fodgesture_pkg.py" 2>&1

"--- 校验 jar 内容 ---"
Push-Location $out
& "$jdk\jar.exe" tf fodgesture.jar
Pop-Location
"--- jar sha256 ---"
(Get-FileHash "$out\fodgesture.jar" -Algorithm SHA256).Hash.ToLower()
"--- 模块里 service.sh 的前 12 行 ---"
& $py -c "import zipfile;d=zipfile.ZipFile(r'$out\fodgesture-module.zip').read('service.sh').decode();print('\n'.join(d.splitlines()[:14]))"