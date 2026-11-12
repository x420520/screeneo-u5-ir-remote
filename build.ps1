[CmdletBinding()]
param(
    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$BuildToolsDir,
    [string]$PlatformJar,
    [string]$JavaBin = $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' })
)

$ErrorActionPreference = 'Stop'
$suffix = if ($IsWindows) { '.exe' } else { '' }
if (!$BuildToolsDir -or !$PlatformJar) {
    if (!$SdkRoot) { throw 'Set ANDROID_HOME or pass -SdkRoot for an Android SDK installation.' }
    if (!$BuildToolsDir) { $BuildToolsDir = Join-Path $SdkRoot 'build-tools/30.0.3' }
    if (!$PlatformJar) { $PlatformJar = Join-Path $SdkRoot 'platforms/android-30/android.jar' }
}
$BuildToolsDir = (Resolve-Path -LiteralPath $BuildToolsDir).Path
$PlatformJar = (Resolve-Path -LiteralPath $PlatformJar).Path

function Java-Tool([string]$Name) {
    if ($JavaBin) { return Join-Path $JavaBin ($Name + $suffix) }
    return (Get-Command ($Name + $suffix) -CommandType Application).Source
}

function Invoke-BuildTool([string]$Executable, [string[]]$ToolArguments) {
    & $Executable @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE." }
}

$java = Java-Tool 'java'
$javac = Java-Tool 'javac'
$jar = Java-Tool 'jar'
$keytool = Java-Tool 'keytool'
$aapt = Join-Path $BuildToolsDir ('aapt' + $suffix)
$zipalign = Join-Path $BuildToolsDir ('zipalign' + $suffix)
$d8 = Join-Path $BuildToolsDir 'lib/d8.jar'
$signer = Join-Path $BuildToolsDir 'lib/apksigner.jar'

$outputDir = Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$debugKey = Join-Path $outputDir 'debug.keystore'
$apk = Join-Path $outputDir 'U5-IR-Remote-debug.apk'
if (!(Test-Path -LiteralPath $debugKey)) {
    Invoke-BuildTool $keytool @('-genkeypair', '-keystore', $debugKey, '-storetype', 'PKCS12',
        '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey',
        '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Android Debug')
}

$tempRoot = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path
$work = Join-Path $tempRoot ('u5-ir-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $work 'classes'
$dex = Join-Path $work 'dex'
New-Item -ItemType Directory -Path $classes,$dex -Force | Out-Null
try {
    $manifest = Join-Path $work 'AndroidManifest.xml'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'AndroidManifest.xml') -Destination $manifest
    $classJar = Join-Path $work 'classes.jar'
    $unsigned = Join-Path $work 'unsigned.apk'
    $aligned = Join-Path $work 'aligned.apk'
    Invoke-BuildTool $javac @('-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-classpath', $PlatformJar, '-d', $classes, (Join-Path $PSScriptRoot 'MainActivity.java'),
        (Join-Path $PSScriptRoot 'IrSignal.java'))
    Invoke-BuildTool $jar @('cf', $classJar, '-C', $classes, '.')
    Invoke-BuildTool $java @('-cp', $d8, 'com.android.tools.r8.D8', '--min-api', '23',
        '--lib', $PlatformJar, '--output', $dex, $classJar)
    Invoke-BuildTool $aapt @('package', '-f', '-M', $manifest, '-I', $PlatformJar, '-F', $unsigned)
    Push-Location -LiteralPath $dex
    try { Invoke-BuildTool $aapt @('add', $unsigned, 'classes.dex') }
    finally { Pop-Location }
    Invoke-BuildTool $zipalign @('-f', '4', $unsigned, $aligned)
    Invoke-BuildTool $java @('-jar', $signer, 'sign', '--v4-signing-enabled', 'false',
        '--ks', $debugKey, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android',
        '--out', $apk, $aligned)
    Invoke-BuildTool $java @('-jar', $signer, 'verify', '--verbose', $apk)
    Write-Output $apk
} finally {
    $resolvedWork = (Resolve-Path -LiteralPath $work).Path
    if ((Split-Path -Parent $resolvedWork) -eq $tempRoot -and
            (Split-Path -Leaf $resolvedWork) -like 'u5-ir-build-*') {
        Remove-Item -LiteralPath $resolvedWork -Recurse -Force
    }
}
