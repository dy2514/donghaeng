param([switch]$Offline)
. "$PSScriptRoot\Env.ps1"
$BuildArgs = @('--console=plain', ':app:assembleDebug', ':core:test', ':app:testDebugUnitTest', ':app:lintDebug')
if ($Offline) { $BuildArgs = @('--offline') + $BuildArgs }
& "$ProjectRoot\gradlew.bat" @BuildArgs
if ($LASTEXITCODE -ne 0) { throw 'Build, tests, or lint failed.' }
Write-Host "APK: $ProjectRoot\app\build\outputs\apk\debug\app-debug.apk"
