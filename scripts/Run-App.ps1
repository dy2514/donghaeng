. "$PSScriptRoot\Env.ps1"
& "$PSScriptRoot\Build-App.ps1"
$Adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
function Get-AdbText {
    param([string[]]$Arguments)
    try {
        $Result = (& $Adb -e @Arguments 2>$null | Out-String).Trim()
        if ($LASTEXITCODE -eq 0) { return $Result }
    } catch { }
    return ''
}
$Connected = (& $Adb devices | Out-String)
if ([regex]::Matches($Connected, '(?m)^emulator-\d+\s+device\s*$').Count -gt 1) { throw 'More than one emulator is connected. Close the others and run again.' }
if ($Connected -notmatch 'emulator-\d+\s+') { & "$PSScriptRoot\Start-Emulator.ps1" }
Write-Host 'Waiting for Pleos home and the active driver profile...'
$Deadline = [DateTime]::UtcNow.AddMinutes(4)
$Ready = $false
# sys.boot_completed alone precedes Pleos home/navigation startup.
# Wait for the driver profile, home process and the initial shell startup window.
do {
    $Boot = Get-AdbText @('shell','getprop','sys.boot_completed')
    $AndroidUserId = Get-AdbText @('shell','am','get-current-user')
    if ($Boot -eq '1' -and $AndroidUserId -match '^\d+$' -and [int]$AndroidUserId -gt 0) {
        $UserState = Get-AdbText @('shell','am','get-started-user-state',$AndroidUserId)
        $HomeProcess = Get-AdbText @('shell','pidof','ai.umos.homescreen')
        $UptimeText = Get-AdbText @('shell','cat','/proc/uptime')
        $UptimeSeconds = 0.0
        if ($UptimeText -match '^([0-9.]+)') {
            $UptimeSeconds = [double]::Parse($Matches[1], [Globalization.CultureInfo]::InvariantCulture)
        }
        $Ready = $UserState -eq 'RUNNING_UNLOCKED' -and $HomeProcess -ne '' -and $UptimeSeconds -ge 35
    }
    if ($Ready) { break }
    Start-Sleep -Seconds 2
} while ([DateTime]::UtcNow -lt $Deadline)
if (-not $Ready) { throw 'Pleos home/driver profile is not ready. Check the emulator window and run RUN_APP.cmd again.' }
$Apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not [IO.File]::Exists($Apk)) { & "$PSScriptRoot\Build-App.ps1" }
& $Adb -e install --user $AndroidUserId -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
$Visible = $false
for ($Attempt = 0; $Attempt -lt 3; $Attempt++) {
    & $Adb -e shell am start -W --user $AndroidUserId -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n ai.pleos.coach/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
    Start-Sleep -Seconds 3
    $Activities = Get-AdbText @('shell','dumpsys','activity','activities')
    $Visible = $Activities -match '(?m)^\s*(?:topResumedActivity=|ResumedActivity:)[^\r\n]*ai\.pleos\.coach/'
    $PermissionPrompt = $Activities -match '(?m)^\s*(?:topResumedActivity=|ResumedActivity:)[^\r\n]*permissioncontroller/'
    if ($Visible -or $PermissionPrompt) { break }
    Write-Host 'Pleos startup changed the foreground window; reopening Donghaeng...'
}
if (-not $Visible -and -not $PermissionPrompt) { throw 'Donghaeng is installed but its window did not become visible. Run RUN_APP.cmd again after the home screen finishes loading.' }
& $Adb -e forward tcp:8088 tcp:8088
if ($PermissionPrompt) { Write-Host 'Review the Android permission prompt in the emulator.' }
else { Write-Host 'Donghaeng is open. Use Scenario Replay to try the app.' }
