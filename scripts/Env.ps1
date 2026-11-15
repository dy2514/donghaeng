$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$DonghaengJavaHome = ''
$DonghaengSdkRoot = ''
$DonghaengAvdName = ''
$DonghaengStudioPath = ''
$LocalDevConfig = Join-Path $ProjectRoot 'local-dev.ps1'
if (Test-Path -LiteralPath $LocalDevConfig) { . $LocalDevConfig }

if (-not $DonghaengJavaHome) { $DonghaengJavaHome = $env:JAVA_HOME }
if (-not $DonghaengJavaHome) {
    $JavaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($JavaCommand) { $DonghaengJavaHome = Split-Path (Split-Path $JavaCommand.Source -Parent) -Parent }
}
if (-not $DonghaengJavaHome -or -not (Test-Path -LiteralPath (Join-Path $DonghaengJavaHome 'bin\javac.exe'))) {
    throw 'Install JDK 17 and set JAVA_HOME, or copy local-dev.ps1.example to local-dev.ps1 and set DonghaengJavaHome.'
}
$JavaRelease = Join-Path $DonghaengJavaHome 'release'
if (-not (Test-Path -LiteralPath $JavaRelease) -or (Get-Content -LiteralPath $JavaRelease -Raw) -notmatch '(?m)^JAVA_VERSION="17(?:\.|\+|")') {
    throw 'This handoff is verified with JDK 17. Select JDK 17 in local-dev.ps1 and Android Studio Gradle settings.'
}

$LocalProperties = Join-Path $ProjectRoot 'local.properties'
$SdkFromProperties = ''
if (Test-Path -LiteralPath $LocalProperties) {
    $SdkLine = Get-Content -LiteralPath $LocalProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
    if ($SdkLine) { $SdkFromProperties = ($SdkLine -replace '^\s*sdk\.dir\s*=\s*', '').Replace('\:', ':').Replace('\\', '\') }
}
if (-not $DonghaengSdkRoot) { $DonghaengSdkRoot = $SdkFromProperties }
if (-not $DonghaengSdkRoot) { $DonghaengSdkRoot = $env:ANDROID_HOME }
if (-not $DonghaengSdkRoot) { $DonghaengSdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $DonghaengSdkRoot -and $env:LOCALAPPDATA) { $DonghaengSdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $DonghaengSdkRoot -or -not (Test-Path -LiteralPath (Join-Path $DonghaengSdkRoot 'platforms\android-35\android.jar'))) {
    throw 'Install Android SDK Platform 35 and Build Tools 35.0.0. Set the SDK path in local-dev.ps1 or local.properties.'
}
$DonghaengSdkRoot = [IO.Path]::GetFullPath($DonghaengSdkRoot)
if ($SdkFromProperties -and [IO.Path]::GetFullPath($SdkFromProperties) -ne $DonghaengSdkRoot) {
    throw 'local.properties and local-dev.ps1 point to different SDKs. Update sdk.dir to the intended SDK.'
}
if (-not (Test-Path -LiteralPath $LocalProperties)) {
    $SdkProperty = $DonghaengSdkRoot.Replace('\', '/')
    [IO.File]::WriteAllText($LocalProperties, "sdk.dir=$SdkProperty`n", [Text.UTF8Encoding]::new($false))
}
$env:JAVA_HOME = $DonghaengJavaHome
$env:ANDROID_HOME = $DonghaengSdkRoot
$env:ANDROID_SDK_ROOT = $DonghaengSdkRoot
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
Set-Location -LiteralPath $ProjectRoot
