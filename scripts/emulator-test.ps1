param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $root '.tools\android-sdk'
$env:JAVA_HOME = (Get-ChildItem (Join-Path $root '.tools\jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = $sdk
$env:ANDROID_AVD_HOME = Join-Path $root '.tools\avds'
$env:ANDROID_SERIAL = 'emulator-5580'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
if (!(Test-Path -LiteralPath $emulator)) { throw 'Install the SDK emulator and system-images;android-35;google_apis;x86_64 first.' }
New-Item -ItemType Directory -Path $env:ANDROID_AVD_HOME -Force | Out-Null
$others = & $adb devices | Select-String '^\S+\s+(device|offline|unauthorized)' |
    Where-Object { $_.Line -notmatch '^emulator-5580\s' }
if ($others) { throw 'Other Android devices are connected. Refusing to run destructive test-app instrumentation outside the isolated emulator.' }
if (Get-NetTCPConnection -LocalPort 5580 -State Listen -ErrorAction SilentlyContinue) { throw 'Emulator port 5580 is already in use.' }
if (!(Test-Path -LiteralPath (Join-Path $env:ANDROID_AVD_HOME 'OneLineJournalTest.avd'))) {
    'no' | & $avdManager create avd --name OneLineJournalTest --package 'system-images;android-35;google_apis;x86_64' --device pixel_6
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated test AVD.' }
}
$stdout = Join-Path $root '.tools\emulator.stdout.log'
$stderr = Join-Path $root '.tools\emulator.stderr.log'
$process = Start-Process -FilePath $emulator -ArgumentList @('-avd', 'OneLineJournalTest', '-port', '5580',
    '-no-window', '-no-audio', '-no-boot-anim', '-gpu', 'swiftshader', '-no-snapshot', '-wipe-data') `
    -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
try {
    $deadline = [DateTime]::UtcNow.AddMinutes(5)
    do {
        if ($process.HasExited) {
            Get-Content -LiteralPath $stderr -Tail 20
            throw 'The emulator exited before booting.'
        }
        Start-Sleep -Seconds 3
        $boot = ((& $adb -s emulator-5580 shell getprop sys.boot_completed 2>$null) -join "`n").Trim()
    } while ($boot -ne '1' -and [DateTime]::UtcNow -lt $deadline)
    if ($boot -ne '1') { throw 'The isolated emulator did not boot within five minutes.' }
    & $adb -s emulator-5580 shell input keyevent 82
    & $adb -s emulator-5580 shell settings put global window_animation_scale 0
    & $adb -s emulator-5580 shell settings put global transition_animation_scale 0
    & $adb -s emulator-5580 shell settings put global animator_duration_scale 0
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks @(':app:assembleDebug', ':app:assembleDebugAndroidTest')
    & $adb -s emulator-5580 install -r (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the app on the test emulator.' }
    & $adb -s emulator-5580 install -r (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the instrumentation APK.' }
    $result = & $adb -s emulator-5580 shell am instrument -w -r `
        'com.lucabol.onelinejournal.test/androidx.test.runner.AndroidJUnitRunner'
    $result | Set-Content -LiteralPath (Join-Path $root '.tools\instrumentation.log')
    $result | Select-Object -Last 35
    if (($result -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Android UI instrumentation did not pass.' }
    $release = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
    if (Test-Path -LiteralPath $release) {
        & $adb -s emulator-5580 uninstall com.lucabol.onelinejournal
        if ($LASTEXITCODE -ne 0) { throw 'Could not remove the test debug installation.' }
        & $adb -s emulator-5580 install $release
        if ($LASTEXITCODE -ne 0) { throw 'Could not install the signed release on the test emulator.' }
        & $adb -s emulator-5580 shell am start -W -n 'com.lucabol.onelinejournal/.MainActivity'
        if ($LASTEXITCODE -ne 0) { throw 'Could not launch the signed release.' }
        Start-Sleep -Seconds 4
        $appPid = & $adb -s emulator-5580 shell pidof com.lucabol.onelinejournal
        if (!$appPid) { throw 'The signed release stopped unexpectedly after launch.' }
        $errors = & $adb -s emulator-5580 logcat -d "--pid=$($appPid.Trim())" '-s' 'AndroidRuntime:E'
        $errors | Set-Content -LiteralPath (Join-Path $root '.tools\release-smoke.log')
        if (($errors -join "`n") -match 'FATAL EXCEPTION') { throw 'The signed release crashed. See release-smoke.log.' }
        Write-Host 'Signed release launched successfully on the isolated Android emulator.'
    }
} finally {
    & $adb -s emulator-5580 emu kill 2>$null
    if (!$process.HasExited) {
        if (!$process.WaitForExit(15000)) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue }
    }
    Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
}
