param(
    [switch]$AcceptAndroidSdkLicense
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $root '.tools'
New-Item -ItemType Directory -Path $tools -Force | Out-Null

function Get-Archive($url, $archive, $destination) {
    if (!(Test-Path -LiteralPath $archive)) {
        Invoke-WebRequest -Uri $url -OutFile $archive
    }
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
}

$jdkRoot = Join-Path $tools 'jdk'
if (!(Test-Path -LiteralPath $jdkRoot)) {
    Get-Archive 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' (Join-Path $tools 'jdk.zip') $jdkRoot
}
$jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (!$jdk) { throw 'No JDK found after extraction.' }
$env:JAVA_HOME = $jdk.FullName
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

$gradle = Join-Path $tools 'gradle\gradle-8.11.1\bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradle)) {
    Get-Archive 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' (Join-Path $tools 'gradle.zip') (Join-Path $tools 'gradle')
}

$sdk = Join-Path $tools 'android-sdk'
$manager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (!(Test-Path -LiteralPath $manager)) {
    Get-Archive 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' (Join-Path $tools 'android-commandline.zip') (Join-Path $tools 'sdk-unpacked')
    New-Item -ItemType Directory -Path (Join-Path $sdk 'cmdline-tools') -Force | Out-Null
    Move-Item -LiteralPath (Join-Path $tools 'sdk-unpacked\cmdline-tools') -Destination (Join-Path $sdk 'cmdline-tools\latest')
}
$env:ANDROID_HOME = $sdk
if ($AcceptAndroidSdkLicense) {
    1..20 | ForEach-Object { 'y' } | & $manager "--sdk_root=$sdk" --licenses | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK license setup failed.' }
}
& $manager "--sdk_root=$sdk" 'platforms;android-36' 'build-tools;36.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'SDK setup failed. Review the SDK license and rerun with -AcceptAndroidSdkLicense if you agree.' }
$env:GRADLE_USER_HOME = Join-Path $tools 'gradle-home'
Push-Location $root
try {
    & $gradle wrapper --gradle-version 8.11.1 --distribution-type bin
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed.' }
} finally { Pop-Location }
Write-Host "Build tools ready. JAVA_HOME=$env:JAVA_HOME ANDROID_HOME=$env:ANDROID_HOME"
