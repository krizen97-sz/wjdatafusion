param([string]$BundleRoot = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This initializer targets Windows, not the preparation host.' }
if (-not [Environment]::Is64BitOperatingSystem) { throw 'Windows x64 is required.' }
$BundleRoot = (Resolve-Path -LiteralPath $BundleRoot).Path
$manifestPath = Join-Path $BundleRoot 'toolchains/verified-toolchains.json'
$tools = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$expanded = Join-Path $BundleRoot 'toolchains/expanded'
New-Item -ItemType Directory -Path $expanded -Force | Out-Null
foreach ($tool in $tools) {
    $archive = Join-Path $BundleRoot ('toolchains/' + $tool.archive)
    $hasSha512 = $null -ne $tool.PSObject.Properties['sha512']
    $algorithm = if ($hasSha512) { 'SHA512' } else { 'SHA256' }
    $expected = if ($hasSha512) { $tool.sha512 } else { $tool.sha256 }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $algorithm).Hash.ToLowerInvariant() -ne $expected) {
        throw ('Toolchain checksum mismatch: ' + $tool.name)
    }
    $target = Join-Path $expanded $tool.name
    $receipt = Join-Path $target '.offline-extracted'
    if (Test-Path -LiteralPath $receipt) {
        if ((Get-Content -LiteralPath $receipt -Raw).Trim() -ne $expected) { throw ('Existing extraction differs: ' + $target) }
        continue
    }
    if (Test-Path -LiteralPath $target) { throw ('Incomplete extraction exists; inspect it before retrying: ' + $target) }
    New-Item -ItemType Directory -Path $target | Out-Null
    if ($tool.archive.EndsWith('.zip')) {
        Expand-Archive -LiteralPath $archive -DestinationPath $target
    } elseif ($tool.archive.EndsWith('.tgz')) {
        & tar.exe -xf $archive -C $target
        if ($LASTEXITCODE -ne 0) { throw ('Archive extraction failed: ' + $tool.name) }
    } else { throw ('Unsupported tool archive: ' + $tool.archive) }
    Set-Content -LiteralPath $receipt -Value $expected -Encoding ASCII
}
function Find-OfflineTool([string]$Name, [string]$RelativeExecutable) {
    $record = $tools | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if (-not $record) { throw ('Missing tool record: ' + $Name) }
    $base = Join-Path $expanded $Name
    if ($record.PSObject.Properties['archiveRoots'] -and @($record.archiveRoots).Count -eq 1) { $base = Join-Path $base $record.archiveRoots[0] }
    $file = Join-Path $base $RelativeExecutable
    if (-not (Test-Path -LiteralPath $file)) { throw ('Missing executable: ' + $file) }
    return $file
}
$java17 = Find-OfflineTool 'jdk17' 'bin/java.exe'
$java21 = Find-OfflineTool 'jdk21' 'bin/java.exe'
$node = Find-OfflineTool 'node24' 'node.exe'
$maven = Find-OfflineTool 'maven363' 'bin/mvn.cmd'
$git = Find-OfflineTool 'mingit' 'cmd/git.exe'
$python = Find-OfflineTool 'python314' 'python.exe'
$npmCli = Join-Path $expanded 'npm11121/package/bin/npm-cli.js'
if (-not (Test-Path -LiteralPath $npmCli)) { throw 'Standalone npm is missing.' }
$env:JAVA_HOME = Split-Path (Split-Path $java17 -Parent) -Parent
$env:RYNEW_JAVA21_HOME = Split-Path (Split-Path $java21 -Parent) -Parent
$env:RYNEW_OFFLINE_HOME = $BundleRoot
$env:RYNEW_NODE_EXE = $node
$env:RYNEW_NPM_CLI = $npmCli
$env:RYNEW_NPX_CLI = Join-Path $expanded 'npm11121/package/bin/npx-cli.js'
$env:npm_config_cache = Join-Path $BundleRoot 'npm-cache'
$env:npm_config_offline = 'true'
$env:npm_config_audit = 'false'
$env:npm_config_fund = 'false'
$env:npm_config_userconfig = Join-Path $BundleRoot 'npm-cache/empty-user.npmrc'
$env:npm_config_globalconfig = Join-Path $BundleRoot 'npm-cache/empty-global.npmrc'
$shimDir = Join-Path $BundleRoot 'windows-shims'
$env:Path = ($shimDir + ';' + (Split-Path $node -Parent) + ';' + (Split-Path $maven -Parent) + ';' + (Split-Path $git -Parent) + ';' + (Split-Path $python -Parent) + ';' + (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path)
Write-Host 'Offline toolchain loaded in this PowerShell session. No machine-wide settings were changed.'
& $java17 -version
& $node --version
& $node $npmCli --version
& $git --version
& $python --version
