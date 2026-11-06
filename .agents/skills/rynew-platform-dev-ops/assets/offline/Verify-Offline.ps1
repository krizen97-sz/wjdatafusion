param(
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [ValidateSet('All', 'Platform', 'Frontend', 'Legacy', 'Compatibility')][string]$Mode = 'All',
    [string]$BundleRoot = $PSScriptRoot,
    [switch]$RunTests
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Initialize-Offline.ps1') -BundleRoot $BundleRoot
$SourceRoot = (Resolve-Path -LiteralPath $SourceRoot).Path
$settings = Join-Path $BundleRoot 'maven/settings-offline.xml'
$repository = Join-Path $BundleRoot 'maven/repository'
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw ('Offline command failed: ' + $Executable + ' (exit ' + $LASTEXITCODE + ')') }
}
function Install-FrozenLock([string]$Project, [string]$Prefix) {
    $expectedPackage = Join-Path $BundleRoot ('locks/' + $Prefix + '.package.json')
    $projectPackage = Join-Path $Project 'package.json'
    $check = Join-Path $BundleRoot 'scripts/compare-packages.cjs'
    Invoke-Checked $env:RYNEW_NODE_EXE @($check, $expectedPackage, $projectPackage)
    $frozen = Join-Path $BundleRoot ('locks/' + $Prefix + '.package-lock.json')
    $destination = Join-Path $Project 'package-lock.json'
    if (Test-Path -LiteralPath $destination) {
        # Normalize CRLF for Git checkouts; do not silently replace another dependency graph.
        $existing = (Get-Content -LiteralPath $destination -Raw).Replace("`r`n", "`n").Trim()
        $expected = (Get-Content -LiteralPath $frozen -Raw).Replace("`r`n", "`n").Trim()
        if ($existing -ne $expected) { throw ('Different lockfile exists; preserve it and choose the matching source/lock explicitly: ' + $destination) }
    } else { Copy-Item -LiteralPath $frozen -Destination $destination }
}
$originalJava = $env:JAVA_HOME
$originalNodeOptions = $env:NODE_OPTIONS
try {
    if ($Mode -in @('All', 'Platform')) {
        $buildArgs = @('-B', '-o', '-gs', $settings, '-s', $settings, ('-Dmaven.repo.local=' + $repository), '-f', (Join-Path $SourceRoot 'WDF100.0/pom.xml'), 'clean', 'verify')
        if (-not $RunTests) { $buildArgs += '-DskipTests' }
        Invoke-Checked 'mvn.cmd' $buildArgs
    }
    if ($Mode -in @('All', 'Frontend')) {
        $project = Join-Path $SourceRoot 'RuoYi-Vue3-master'
        Install-FrozenLock $project 'vue3'
        Push-Location $project
        try {
            Invoke-Checked 'npm.cmd' @('ci', '--offline', '--no-audit', '--no-fund')
            Invoke-Checked 'npm.cmd' @('run', 'verify:frontend')
        } finally { Pop-Location }
    }
    if ($Mode -in @('All', 'Legacy')) {
        $project = Join-Path $SourceRoot 'WDF100.0/wjdatafusion-ui'
        Install-FrozenLock $project 'vue2'
        $env:NODE_OPTIONS = '--openssl-legacy-provider'
        Push-Location $project
        try {
            Invoke-Checked 'npm.cmd' @('ci', '--offline', '--legacy-peer-deps', '--no-audit', '--no-fund')
            Invoke-Checked 'npm.cmd' @('run', 'build:prod')
        } finally { Pop-Location; $env:NODE_OPTIONS = $originalNodeOptions }
    }
    if ($Mode -in @('All', 'Compatibility')) {
        $env:JAVA_HOME = $env:RYNEW_JAVA21_HOME
        $buildArgs = @('-B', '-o', '-gs', $settings, '-s', $settings, ('-Dmaven.repo.local=' + $repository), '-f', (Join-Path $SourceRoot 'data-governance/compatibility/pom.xml'), 'clean', 'verify')
        if (-not $RunTests) { $buildArgs += '-DskipTests' }
        Invoke-Checked 'mvn.cmd' $buildArgs
    }
} finally { $env:JAVA_HOME = $originalJava; $env:NODE_OPTIONS = $originalNodeOptions }
Write-Host ('Offline validation completed: ' + $Mode + '. Commands above determine what was actually tested.')
