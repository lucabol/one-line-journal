param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (!$env:JAVA_HOME) {
    $jdkRoot = Join-Path $root '.tools\jdk'
    if (Test-Path -LiteralPath $jdkRoot) {
        $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
}
if (!$env:ANDROID_HOME -and (Test-Path (Join-Path $root '.tools\android-sdk'))) {
    $env:ANDROID_HOME = Join-Path $root '.tools\android-sdk'
}
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $root '.tools\gradle-home' }
Push-Location $root
try {
    if (Test-Path '.\gradlew.bat') {
        & .\gradlew.bat --console=plain @Tasks
    } else {
        & '.\.tools\gradle\gradle-8.11.1\bin\gradle.bat' --console=plain @Tasks
    }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }
