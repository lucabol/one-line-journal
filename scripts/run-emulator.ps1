param(
    [string]$Apk,
    [int]$X = 20,
    [int]$Y = 20,
    [switch]$WipeData,
    [string]$AvdName = 'GalaxyS21'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $root '.tools\android-sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$serial = 'emulator-5580'
$title = "Android Emulator - ${AvdName}:5580"
$package = 'com.lucabol.onelinejournal'
$env:ANDROID_HOME = $sdk
$env:ANDROID_AVD_HOME = Join-Path $root '.tools\avds'

if (!$Apk) {
    $release = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
    $debug = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    $Apk = if (Test-Path -LiteralPath $release) { $release } else { $debug }
}
if (!(Test-Path -LiteralPath $Apk)) { throw "APK not found: $Apk. Build it with scripts\release.ps1 first." }
if (!(Test-Path -LiteralPath $emulator)) { throw 'Android emulator is not installed. See README.md.' }

Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;

public static class OneLineEmulatorWindow
{
    private delegate bool WindowVisitor(IntPtr handle, IntPtr parameter);
    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool EnumWindows(WindowVisitor visitor, IntPtr parameter);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr handle, StringBuilder title, int capacity);
    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr handle, int command);
    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool SetWindowPos(IntPtr handle, IntPtr after, int x, int y, int w, int h, uint flags);
    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr handle);

    public static IntPtr Find(string exactTitle)
    {
        IntPtr found = IntPtr.Zero;
        EnumWindows(delegate(IntPtr handle, IntPtr parameter)
        {
            var text = new StringBuilder(1024);
            GetWindowText(handle, text, text.Capacity);
            if (text.ToString() == exactTitle) { found = handle; return false; }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static void MoveTo(IntPtr handle, int x, int y)
    {
        ShowWindow(handle, 9);
        // SWP_NOSIZE | SWP_NOZORDER | SWP_SHOWWINDOW: move only, keep the device size.
        if (!SetWindowPos(handle, IntPtr.Zero, x, y, 0, 0, 0x0001 | 0x0004 | 0x0040))
            throw new Win32Exception(Marshal.GetLastWin32Error());
        SetForegroundWindow(handle);
    }
}
'@

function Show-EmulatorWindow {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    do {
        $handle = [OneLineEmulatorWindow]::Find($title)
        if ($handle -ne [IntPtr]::Zero) {
            [OneLineEmulatorWindow]::MoveTo($handle, $X, $Y)
            return
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "The emulator window '$title' did not appear."
}

function Test-Running {
    $state = (& $adb devices | Select-String "^$serial\s+device") -ne $null
    return $state
}

& $adb start-server | Out-Null
$started = $null
if (Test-Running) {
    Write-Host 'Reusing the running emulator.'
} else {
    if (Get-NetTCPConnection -LocalPort 5580 -State Listen -ErrorAction SilentlyContinue) {
        throw 'Port 5580 is in use by something that is not a ready emulator. Close it and retry.'
    }
    $avdDir = Join-Path $env:ANDROID_AVD_HOME "$AvdName.avd"
    if (!(Test-Path -LiteralPath $avdDir)) {
        if ($AvdName -ne 'GalaxyS21') { throw "The virtual device '$AvdName' does not exist." }
        Write-Host 'Creating the Galaxy S21 virtual device...'
        $env:JAVA_HOME = (Get-ChildItem (Join-Path $root '.tools\jdk') -Directory | Select-Object -First 1).FullName
        $avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
        'no' | & $avdManager create avd -n $AvdName -k 'system-images;android-35;google_apis;x86_64' -d pixel_6 --force | Out-Null
        if (!(Test-Path -LiteralPath $avdDir)) { throw 'avdmanager failed to create the Galaxy S21 device.' }
        # Galaxy S21 profile: 6.2" 1080x2400 at ~421 dpi, 8 GB RAM.
        $config = Join-Path $avdDir 'config.ini'
        $values = @{
            'hw.device.manufacturer' = 'samsung'
            'hw.device.name'         = 'galaxy_s21'
            'hw.lcd.width'           = '1080'
            'hw.lcd.height'          = '2400'
            'hw.lcd.density'         = '421'
            'hw.ramSize'             = '4096'
            'hw.keyboard'            = 'yes'
        }
        $lines = [System.Collections.Generic.List[string]](Get-Content -LiteralPath $config)
        foreach ($key in $values.Keys) {
            $index = $lines.FindIndex([Predicate[string]]{ param($l) $l -match ('^' + [regex]::Escape($key) + '\s*=') })
            $entry = "$key = $($values[$key])"
            if ($index -ge 0) { $lines[$index] = $entry } else { $lines.Add($entry) }
        }
        Set-Content -LiteralPath $config -Value $lines
    }
    $arguments = @('-avd', $AvdName, '-port', '5580', '-gpu', 'swiftshader', '-no-snapshot', '-no-audio',
        '-prop', 'ro.product.manufacturer=samsung', '-prop', 'ro.product.brand=samsung',
        '-prop', 'ro.product.model=SM-G991B', '-prop', 'ro.product.name=o1sxxx')
    if ($WipeData) { $arguments += '-wipe-data' }
    Write-Host 'Starting the emulator...'
    $started = Start-Process -FilePath $emulator -ArgumentList $arguments -PassThru `
        -RedirectStandardOutput (Join-Path $root '.tools\emulator.stdout.log') `
        -RedirectStandardError (Join-Path $root '.tools\emulator.stderr.log')
}

Show-EmulatorWindow
Write-Host "Emulator window moved to $X,$Y."

$deadline = [DateTime]::UtcNow.AddMinutes(5)
do {
    if ($started -and $started.HasExited) { throw 'The emulator exited before booting. See .tools\emulator.stderr.log.' }
    $boot = ''
    if (Test-Running) { $boot = ((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join '').Trim() }
    if ($boot -eq '1') { break }
    Start-Sleep -Seconds 3
} while ([DateTime]::UtcNow -lt $deadline)
if ($boot -ne '1') { throw 'Android did not finish booting within five minutes.' }

Write-Host "Installing $Apk ..."
& $adb -s $serial install -r $Apk
if ($LASTEXITCODE -ne 0) {
    throw 'Install failed. If the error is a signature mismatch, run: adb -s emulator-5580 uninstall com.lucabol.onelinejournal (this erases emulator test data only).'
}
& $adb -s $serial shell input keyevent 82
& $adb -s $serial shell pm grant $package android.permission.POST_NOTIFICATIONS 2>$null
& $adb -s $serial shell am start -n "$package/.MainActivity" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'The app could not be launched.' }

Show-EmulatorWindow
Write-Host 'One Line Journal is running in the emulator. Close the emulator window when finished.'
