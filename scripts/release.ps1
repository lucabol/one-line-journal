param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jdkRoot = Join-Path $root '.tools\jdk'
if (!$env:JAVA_HOME -and (Test-Path -LiteralPath $jdkRoot)) {
    $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME or run scripts\bootstrap.ps1 first.' }
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$keys = Join-Path $root '.tools\signing'
New-Item -ItemType Directory -Path $keys -Force | Out-Null
$keystore = Join-Path $keys 'one-line.p12'
$passwordFile = Join-Path $keys 'password.xml'
if ((Test-Path -LiteralPath $keystore) -and !(Test-Path -LiteralPath $passwordFile)) {
    throw 'The signing keystore exists but its Windows-protected password is missing. Recover it before building; do not replace the key.'
}
if (!(Test-Path -LiteralPath $passwordFile)) {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $secret = [Convert]::ToBase64String($bytes) | ConvertTo-SecureString -AsPlainText -Force
    $secret | Export-Clixml -LiteralPath $passwordFile
}
$secure = Import-Clixml -LiteralPath $passwordFile
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:ONE_LINE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $env:ONE_LINE_KEYSTORE = $keystore
    if (!(Test-Path -LiteralPath $keystore)) {
        & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keystore -alias one-line `
            -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 `
            -dname 'CN=One Line Journal Personal' -storepass:env ONE_LINE_STORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Signing key creation failed.' }
    }
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks @(':app:assembleRelease')
    & $keytool -list -v -keystore $keystore -alias one-line -storepass:env ONE_LINE_STORE_PASSWORD |
        Select-String -Pattern 'SHA1:|SHA256:'
    if ($LASTEXITCODE -ne 0) { throw 'Could not print the signing certificate fingerprints.' }
    Write-Host 'Keep .tools\signing safe. Never commit or share its private key or password file.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    Remove-Item Env:ONE_LINE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:ONE_LINE_KEYSTORE -ErrorAction SilentlyContinue
}
