[CmdletBinding()]
param(
    [string]$Database = $env:DB_NAME,
    [string]$DbUser = $env:DB_USERNAME,
    [string]$UploadDir = $env:UPLOAD_DIR,
    [string]$BackupRoot = (Join-Path $PSScriptRoot "..\backups")
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Database)) { $Database = "document_management" }
if ([string]::IsNullOrWhiteSpace($DbUser)) { $DbUser = "root" }
if ([string]::IsNullOrWhiteSpace($UploadDir)) {
    throw 'Specify -UploadDir or UPLOAD_DIR using the same absolute upload directory as the running application.'
}
if (-not (Test-Path -LiteralPath $UploadDir -PathType Container)) { throw "Upload directory does not exist: $UploadDir" }
. (Join-Path $PSScriptRoot 'backup-files.ps1')

$stamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss-fff"
$destination = Join-Path $BackupRoot $stamp
$uploadDestination = Join-Path $destination "uploads"
New-Item -ItemType Directory -Force -Path $uploadDestination | Out-Null

$securePassword = Read-Host "MySQL password for $DbUser (leave blank if none)" -AsSecureString
$previousMysqlPassword = $env:MYSQL_PWD
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $sqlFile = Join-Path $destination "$Database.sql"
    & mysqldump --single-transaction --routines --events --default-character-set=utf8mb4 -u $DbUser $Database "--result-file=$sqlFile"
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE" }
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    if ($null -eq $previousMysqlPassword) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    else { $env:MYSQL_PWD = $previousMysqlPassword }
}

$fileManifest = @(Copy-UploadBackup -Source $UploadDir -Destination $uploadDestination)
ConvertTo-Json -InputObject $fileManifest -Depth 3 | Set-Content -LiteralPath (Join-Path $destination 'files.json') -Encoding UTF8

Set-Content -LiteralPath (Join-Path $destination "manifest.txt") -Value @(
    "Created: $(Get-Date -Format o)", "Database: $Database", "Uploads source: $UploadDir", "Verified files: $($fileManifest.Count)"
)
Write-Host "Backup completed: $destination"
