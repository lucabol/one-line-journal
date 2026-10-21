param(
    [string]$Destination
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (!$Destination) { $Destination = Join-Path $root 'artifacts' }
$release = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
if (!(Test-Path -LiteralPath $release)) { throw 'Build a signed release with scripts\release.ps1 first.' }
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
Copy-Item -LiteralPath $release -Destination (Join-Path $Destination 'OneLineJournal.apk') -Force
Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination (Join-Path $Destination 'README.md') -Force
if (!$env:JAVA_HOME) {
    $env:JAVA_HOME = (Get-ChildItem (Join-Path $root '.tools\jdk') -Directory | Select-Object -First 1).FullName
}
$signature = & (Join-Path $root '.tools\android-sdk\build-tools\36.0.0\apksigner.bat') verify --print-certs $release
if ($LASTEXITCODE -ne 0) { throw 'The release signature is invalid. Refusing to package it.' }
@('Android package: com.lucabol.onelinejournal',
    'Register the following certificate SHA-1 in your Google Cloud Android OAuth client.',
    'Enable Google Drive API, request drive.file only, and read README.md before connecting.',
    'This is public certificate information, not a private signing key.',
    '') + $signature | Set-Content -LiteralPath (Join-Path $Destination 'OAuth-setup.txt') -Encoding utf8
$zipPath = Join-Path $Destination 'OneLineJournal-source.zip'
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath }
$allowed = @('app\src', 'app\schemas', 'app\build.gradle.kts', 'app\proguard-rules.pro',
    'core\src', 'core\build.gradle.kts', 'gradle', 'scripts', '.gitignore',
    'README.md', 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
    'gradlew', 'gradlew.bat')
$archive = [IO.Compression.ZipFile]::Open($zipPath, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($relative in $allowed) {
        $path = Join-Path $root $relative
        if (!(Test-Path -LiteralPath $path)) { throw "Required source file is missing: $relative" }
        $files = if (Test-Path -LiteralPath $path -PathType Container) {
            Get-ChildItem -LiteralPath $path -Recurse -File
        } else { Get-Item -LiteralPath $path }
        foreach ($file in $files) {
            $name = [IO.Path]::GetRelativePath($root, $file.FullName).Replace('\', '/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $name,
                [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    }
} finally { $archive.Dispose() }
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $Destination 'OneLineJournal.apk'),$zipPath |
    ForEach-Object { '{0}  {1}' -f $_.Hash.ToLowerInvariant(),[IO.Path]::GetFileName($_.Path) } |
    Set-Content -LiteralPath (Join-Path $Destination 'SHA256SUMS.txt') -Encoding ascii
Write-Host "APK, source archive, and setup guide saved to $Destination"
