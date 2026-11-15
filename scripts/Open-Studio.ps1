. "$PSScriptRoot\Env.ps1"
if (-not $DonghaengStudioPath) {
    $StudioCommand = Get-Command studio64.exe -ErrorAction SilentlyContinue
    if ($StudioCommand) { $DonghaengStudioPath = $StudioCommand.Source }
}
if (-not $DonghaengStudioPath -and $env:ProgramFiles) {
    $StandardStudio = Join-Path $env:ProgramFiles 'Android\Android Studio\bin\studio64.exe'
    if (Test-Path -LiteralPath $StandardStudio) { $DonghaengStudioPath = $StandardStudio }
}
if (-not $DonghaengStudioPath -or -not (Test-Path -LiteralPath $DonghaengStudioPath)) {
    throw 'Open this folder with Android Studio, or set DonghaengStudioPath in local-dev.ps1.'
}
Start-Process -FilePath $DonghaengStudioPath -ArgumentList ('"' + $ProjectRoot + '"') -WindowStyle Normal
