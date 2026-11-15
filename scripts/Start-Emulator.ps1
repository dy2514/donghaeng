param([string]$AvdName = '', [switch]$Headless)
. "$PSScriptRoot\Env.ps1"
$EmulatorPath = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
if (-not (Test-Path -LiteralPath $EmulatorPath)) { throw 'Install Android Emulator using Android Studio SDK Manager.' }
$AvailableAvds = @(& $EmulatorPath -list-avds | Where-Object { $_.Trim() })
if ($LASTEXITCODE -ne 0) { throw 'Could not list Android virtual devices.' }
if (-not $AvdName) { $AvdName = $DonghaengAvdName }
if (-not $AvdName) {
    $PleosAvds = @($AvailableAvds | Where-Object { $_ -match 'Pleos' })
    if ($PleosAvds.Count -eq 1) { $AvdName = $PleosAvds[0] }
    else { throw 'Create a Pleos API 34 AVD and set DonghaengAvdName in local-dev.ps1, or start it from Android Studio first.' }
}
if ($AvdName -notin $AvailableAvds) { throw "AVD not found: $AvdName. Check emulator -list-avds and local-dev.ps1." }
$EmulatorArgs = @('-avd', ('"' + $AvdName + '"'), '-no-snapshot', '-no-boot-anim', '-gpu', 'host', '-timezone', 'Asia/Seoul')
if ($Headless) { $EmulatorArgs += '-no-window' }
# This is the interactive emulator the recipient explicitly opens with RUN_APP.cmd.
$EmulatorWindowStyle = if ($Headless) { 'Hidden' } else { 'Normal' }
Start-Process -FilePath $EmulatorPath -ArgumentList $EmulatorArgs -WindowStyle $EmulatorWindowStyle
