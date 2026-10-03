param([ValidateSet('all','app','tv')][string]$Module='all')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$jdkDirectory = Get-ChildItem -LiteralPath (Join-Path $taskRoot 'tools/jdk') -Directory | Select-Object -First 1
if (-not $jdkDirectory) { throw 'JDK absent. Ouvrez android dans Android Studio ou installez les outils locaux.' }
$env:JAVA_HOME = $jdkDirectory.FullName
$env:ANDROID_HOME = Join-Path $taskRoot 'tools/android-sdk'
$gradleExecutable = Join-Path $taskRoot 'tools/gradle/gradle-8.9/bin/gradle.bat'
$artifactDirectory = Join-Path $taskRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
foreach ($module in $(if($Module -eq 'all'){@('app','tv')}else{@($Module)})) {
& $gradleExecutable -p (Join-Path $taskRoot 'android') ":${module}:assembleDebug" --console=plain
if ($LASTEXITCODE -ne 0) { throw "La compilation Android du module $module a échoué." }
$outputDirectory = Join-Path $taskRoot ('android/'+$module+'/build/outputs/apk/debug')
$metadata = Get-Content -LiteralPath (Join-Path $outputDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
$apk = $metadata.elements | Select-Object -First 1
$sourceApk = Join-Path $outputDirectory $apk.outputFile
$version = $apk.versionName -replace '[^a-zA-Z0-9._-]', '-'
$parisTime = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow, 'Romance Standard Time')
$stamp = $parisTime.ToString('yyyy-MM-dd_HH-mm-ss-fff')
$hash = (Get-FileHash -LiteralPath $sourceApk -Algorithm SHA256).Hash.Substring(0, 8).ToLowerInvariant()
$prefix = if ($module -eq 'tv') { 'domora-tv' } else { 'ma-maison' }
$archiveName = "$prefix-v$version-build$($apk.versionCode)-$stamp-paris-debug-$hash.apk"
$archivePath = Join-Path $artifactDirectory $archiveName
if (Test-Path -LiteralPath $archivePath) { throw "Archive déjà présente : $archivePath" }
Copy-Item -LiteralPath $sourceApk -Destination $archivePath
Write-Host "APK conservée : $archivePath"

}
