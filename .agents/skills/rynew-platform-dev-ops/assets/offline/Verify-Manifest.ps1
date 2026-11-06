param([string]$BundleRoot = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$BundleRoot = (Resolve-Path -LiteralPath $BundleRoot).Path
$entries = Get-Content -LiteralPath (Join-Path $BundleRoot 'FILES.sha256.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$checked = 0
foreach ($entry in $entries) {
    if ($entry.path -match '(^|/)\.\.(/|$)' -or [IO.Path]::IsPathRooted($entry.path)) { throw 'Invalid manifest path.' }
    $path = Join-Path $BundleRoot $entry.path
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw ('Missing file: ' + $entry.path) }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw ('Checksum mismatch: ' + $entry.path) }
    $checked++
}
Write-Host ('Verified ' + $checked + ' payload files. Verify the ZIP hash before extracting; run this before caches are used or modified.')
