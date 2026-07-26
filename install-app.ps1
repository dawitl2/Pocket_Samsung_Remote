param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$packageName = "com.enkud.pocketsamsungremote"
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"

$adbCandidates = @(
    (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
    $(if ($env:ANDROID_SDK_ROOT) { Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe" })
) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

$adb = $adbCandidates | Select-Object -First 1
if (-not $adb) {
    throw "adb.exe was not found. Install Android platform-tools or set ANDROID_SDK_ROOT."
}

& (Join-Path $projectRoot "gradlew.bat") assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Android build failed."
}

if ($Clean) {
    & $adb uninstall $packageName
}

& $adb install -r $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "APK installation failed."
}

& $adb shell am start -n "$packageName/.MainActivity"
