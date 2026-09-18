$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'backup-files.ps1')
$testRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ('..\target\backup-test-' + [guid]::NewGuid())))
$source = Join-Path $testRoot 'source'
$destination = Join-Path $testRoot 'destination'
New-Item -ItemType Directory -Force -Path (Join-Path $source 'nested') | Out-Null
Set-Content -LiteralPath (Join-Path $source 'document[1].pdf') -Value '%PDF-1.7 example'
Set-Content -LiteralPath (Join-Path $source 'nested\notes.docx') -Value 'archive fixture'
$manifest = @(Copy-UploadBackup -Source $source -Destination $destination)
if ($manifest.Count -ne 2) { throw 'Expected both upload files in the backup manifest.' }
foreach ($entry in $manifest) {
    $file = Join-Path $destination $entry.Path
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing backup file: $file" }
    if ((Get-FileHash -LiteralPath $file).Hash -ne $entry.SHA256) { throw 'Manifest checksum mismatch.' }
}
$rejected = $false
try { Copy-UploadBackup -Source $source -Destination (Join-Path $source 'recursive-backup') }
catch { $rejected = $true }
if (-not $rejected) { throw 'Recursive backup destination must be rejected.' }
$missingRejected = $false
try { Copy-UploadBackup -Source (Join-Path $testRoot 'missing') -Destination (Join-Path $testRoot 'missing-destination') }
catch { $missingRejected = $true }
if (-not $missingRejected) { throw 'Missing source must be rejected.' }
Write-Output 'PASS: backup preserves nested files, literal filenames and SHA256; rejects invalid source/destination.'
