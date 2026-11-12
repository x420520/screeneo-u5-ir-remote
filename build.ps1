[CmdletBinding()]
param(
    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }),
    [string]$BuildToolsDir,
    [string]$PlatformJar,
    [string]$JavaBin = $(if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' }),
    [string]$SigningKeystore,
    [string]$SigningAlias,
    [string]$SigningPasswordFile,
    [string]$OutputApk
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
if ($SigningKeystore -or $SigningAlias -or $SigningPasswordFile) {
    if (!$SigningKeystore -or !$SigningAlias -or !$SigningPasswordFile) {
        throw 'Pass -SigningKeystore, -SigningAlias, and -SigningPasswordFile together.'
    }
    $keystore = (Resolve-Path -LiteralPath $SigningKeystore).Path
    $keystorePass = 'file:' + (Resolve-Path -LiteralPath $SigningPasswordFile).Path
    $alias = $SigningAlias
    $defaultApk = Join-Path $outputDir 'U5-IR-Remote.apk'
} else {
    $keystore = Join-Path $outputDir 'debug.keystore'
    $keystorePass = 'pass:android'
    $alias = 'androiddebugkey'
    $defaultApk = Join-Path $outputDir 'U5-IR-Remote-debug.apk'
    if (!(Test-Path -LiteralPath $keystore)) {
        Invoke-BuildTool $keytool @('-genkeypair', '-keystore', $keystore, '-storetype', 'PKCS12',
            '-storepass', 'android', '-keypass', 'android', '-alias', $alias,
            '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650', '-dname', 'CN=Android Debug')
    }
}
$apk = if ($OutputApk) { $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputApk) } else { $defaultApk }

$tempRoot = (Resolve-Path -LiteralPath ([IO.Path]::GetTempPath())).Path
$work = Join-Path $tempRoot ('u5-ir-build-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $work 'classes'
$dex = Join-Path $work 'dex'
$generated = Join-Path $work 'generated'
New-Item -ItemType Directory -Path $classes,$dex,$generated -Force | Out-Null
try {
    $manifest = Join-Path $work 'AndroidManifest.xml'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'AndroidManifest.xml') -Destination $manifest
    $resources = Join-Path $work 'res'
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'res') -Destination $resources -Recurse
    $classJar = Join-Path $work 'classes.jar'
    $unsigned = Join-Path $work 'unsigned.apk'
    $aligned = Join-Path $work 'aligned.apk'
    Invoke-BuildTool $aapt @('package', '-f', '-m', '-J', $generated,
        '-M', $manifest, '-S', $resources, '-I', $PlatformJar, '-F', $unsigned)
    $sources = @((Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' -File).FullName)
    $sources += @((Get-ChildItem -LiteralPath $generated -Filter '*.java' -File -Recurse).FullName)
    Invoke-BuildTool $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8',
        '-classpath', $PlatformJar, '-d', $classes) + $sources)
    Invoke-BuildTool $jar @('cf', $classJar, '-C', $classes, '.')
    Invoke-BuildTool $java @('-cp', $d8, 'com.android.tools.r8.D8', '--min-api', '23',
        '--lib', $PlatformJar, '--output', $dex, $classJar)
    Push-Location -LiteralPath $dex
    try { Invoke-BuildTool $aapt @('add', $unsigned, 'classes.dex') }
    finally { Pop-Location }
    Invoke-BuildTool $zipalign @('-f', '4', $unsigned, $aligned)
    Invoke-BuildTool $java @('-jar', $signer, 'sign', '--v4-signing-enabled', 'false',
        '--ks', $keystore, '--ks-key-alias', $alias, '--ks-pass', $keystorePass,
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
