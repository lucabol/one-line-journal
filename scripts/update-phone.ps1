param(
    [string]$Serial
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $root '.tools\android-sdk\platform-tools\adb.exe'
$apk = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
$package = 'com.lucabol.onelinejournal'

function Invoke-Adb {
    param([string[]]$Arguments)
    $output = & $adb @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "ADB failed (exit $LASTEXITCODE): $($Arguments -join ' ')"
    }
    return $output
}

function Get-PhoneSerial {
    param([string[]]$DeviceLines, [string]$RequestedSerial)
    $devices = @(
        foreach ($line in $DeviceLines) {
            if ($line -match '^(\S+)\s+(device|unauthorized|offline)(?:\s|$)') {
                if ($Matches[1] -notlike 'emulator-*') {
                    [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2] }
                }
            }
        }
    )
    if ($RequestedSerial) {
        $selected = @($devices | Where-Object { $_.Serial -eq $RequestedSerial })
        if ($selected.Count -ne 1) {
            throw "Physical phone '$RequestedSerial' not found. Connect it and run adb devices -l."
        }
    } else {
        if ($devices.Count -eq 0) {
            throw 'No physical phone connected. Connect your phone, enable USB debugging, unlock it, and approve its USB-debugging prompt.'
        }
        if ($devices.Count -gt 1) {
            throw "Multiple phones connected. Rerun with -Serial using one of: $($devices.Serial -join ', ')."
        }
        $selected = $devices
    }
    if ($selected[0].State -ne 'device') {
        throw "Phone '$($selected[0].Serial)' is $($selected[0].State). Unlock it, approve USB debugging, and reconnect if necessary."
    }
    return $selected[0].Serial
}

if (!(Test-Path -LiteralPath $adb)) {
    throw 'ADB is missing. Run scripts\bootstrap.ps1 first.'
}
$Serial = Get-PhoneSerial -DeviceLines @(Invoke-Adb -Arguments @('devices', '-l')) -RequestedSerial $Serial

# Never generate a replacement signing identity during an update.
foreach ($file in @('one-line.p12', 'password.xml')) {
    if (!(Test-Path -LiteralPath (Join-Path $root ".tools\signing\$file"))) {
        throw "Existing release signing material is missing: $file. Restore it before updating; a new key cannot update the installed app."
    }
}
Write-Host "Building the signed release for phone $Serial..."
& (Join-Path $PSScriptRoot 'release.ps1')
if (!(Test-Path -LiteralPath $apk)) { throw "Release APK not found: $apk" }

Write-Host 'Updating the app in place; journal data and settings are preserved...'
Invoke-Adb -Arguments @('-s', $Serial, 'install', '-r', $apk)
Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'force-stop', $package)
$launch = @(Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', "$package/.MainActivity"))
$launch | Write-Host
if (!(($launch -join "`n") -match '(?m)^\s*Status:\s*ok\s*$')) {
    throw 'The APK was installed, but Android did not confirm a successful app launch.'
}
Write-Host 'Phone updated and app opened.'
