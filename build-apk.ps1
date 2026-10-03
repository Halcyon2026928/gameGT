# 重新构建「去启动动画」LSPosed 模块 APK（无 Gradle，直接用 Android SDK 工具链）
# 用法：powershell -ExecutionPolicy Bypass -File build-apk.ps1
# 产物：NoStartupAnimation-LSPosed-v1.1.apk（项目根目录）

$ErrorActionPreference = "Stop"
$Project = "D:\Androidstudio\gameGT"
$Build  = Join-Path $Project ".build"
$Sdk    = "D:\sdk"
$Jdk    = Join-Path $Sdk "jdk-17.0.20.1+1"
$BT     = Join-Path $Sdk "build-tools\36.0.0"
$AndroidJar = Join-Path $Sdk "platforms\android-34\android.jar"
$XposedApi  = Join-Path $Build "xposed-api-82.jar"   # 需先下载：https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar
$Src    = Join-Path $Project "app\src\main"

$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"

New-Item -ItemType Directory -Force -Path "$Build\classes","$Build\assets" | Out-Null

# 1) 编译 res（含 xposed_scope 数组）
& "$BT\aapt2.exe" compile --dir "$Src\res" -o "$Build\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile res failed" }

# 2) 编译 Java
& "$Jdk\bin\javac.exe" -source 8 -target 8 -encoding UTF-8 `
    -bootclasspath $AndroidJar -classpath $XposedApi `
    -d "$Build\classes" "$Src\java\com\example\lspempty\MainHook.java"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 3) jar -> dex
& "$Jdk\bin\jar.exe" cf "$Build\classes.jar" -C "$Build\classes" .
& "$BT\d8.bat" --release --lib $AndroidJar --min-api 23 --output $Build "$Build\classes.jar"
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# 4) 打包 manifest + res + assets（--auto-add-overlay 允许新增 xposed_scope 资源）
Copy-Item "$Src\assets\xposed_init" "$Build\assets\xposed_init" -Force
Remove-Item "$Build\unsigned.apk" -Force -ErrorAction SilentlyContinue
& "$BT\aapt2.exe" link --auto-add-overlay -o "$Build\unsigned.apk" -I $AndroidJar `
    -R "$Build\res.zip" --manifest "$Build\manifest\AndroidManifest.xml" -A "$Build\assets"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# 5) 加入 classes.dex
Push-Location $Build
& "$Jdk\bin\jar.exe" uf "$Build\unsigned.apk" "classes.dex"
if ($LASTEXITCODE -ne 0) { throw "jar uf failed" }
Pop-Location

# 6) 对齐 + 签名
& "$BT\zipalign.exe" -f 4 "$Build\unsigned.apk" "$Build\aligned.apk"
$Final = Join-Path $Project "NoStartupAnimation-LSPosed-v1.1.apk"
Remove-Item $Final -Force -ErrorAction SilentlyContinue
& "$BT\apksigner.bat" sign --ks "$Build\debug.keystore" `
    --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android `
    --out $Final "$Build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& "$BT\apksigner.bat" verify --verbose $Final
Write-Host "OK -> $Final" -ForegroundColor Green
