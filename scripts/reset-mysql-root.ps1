[CmdletBinding()]
param(
    [string]$ServiceName = "MySQL80",
    [string]$DatabaseName = "document_management",
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
$mysqlRoot = "C:\Program Files\MySQL\MySQL Server 8.0"
$mysqlExe = Join-Path $mysqlRoot "bin\mysql.exe"
$mysqlAdminExe = Join-Path $mysqlRoot "bin\mysqladmin.exe"
$mysqldExe = Join-Path $mysqlRoot "bin\mysqld.exe"
$defaultsFile = "C:\ProgramData\MySQL\MySQL Server 8.0\my.ini"
$dataDir = "C:\ProgramData\MySQL\MySQL Server 8.0\Data"
$initFile = "C:\ProgramData\MySQL\mysql-init-edurepo.sql"
$stamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backupDir = Join-Path $ProjectRoot "backups\before-mysql-password-reset_$stamp"
$physicalBackup = Join-Path $backupDir "mysql-data"
$uploadSource = Join-Path $ProjectRoot "uploads"
$manualServer = $null
$plainPassword = $null
$serverWasStopped = $false

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Wait-ForPort([bool]$ExpectedOpen, [int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $open = $null -ne (Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue |
            Select-Object -First 1)
        if ($open -eq $ExpectedOpen) { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "MySQL port 3306 did not reach the expected state within $TimeoutSeconds seconds."
}

function Invoke-MySqlCheck {
    & $mysqlExe --connect-timeout=5 -uroot --batch --skip-column-names -e "SELECT CURRENT_USER(); SHOW DATABASES;"
    if ($LASTEXITCODE -ne 0) { throw "Could not authenticate with the new MySQL root password." }
}

if (-not (Test-Administrator)) {
    throw "Run this script from an Administrator PowerShell window."
}

foreach ($requiredPath in @($mysqlExe, $mysqlAdminExe, $mysqldExe, $defaultsFile, $dataDir)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required MySQL path was not found: $requiredPath"
    }
}

$firstPassword = Read-Host "Nhap mat khau root MySQL moi" -AsSecureString
$secondPassword = Read-Host "Nhap lai mat khau moi" -AsSecureString
$firstPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($firstPassword)
$secondPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secondPassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPointer)
    $confirmation = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPointer)
    if ([string]::IsNullOrWhiteSpace($plainPassword)) { throw "Password must not be empty." }
    if ($plainPassword -ne $confirmation) { throw "The two passwords do not match." }
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPointer)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPointer)
    $confirmation = $null
}

try {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

    Write-Host "Stopping $ServiceName for a consistent physical backup..."
    Stop-Service -Name $ServiceName -Force
    (Get-Service -Name $ServiceName).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(60))
    Wait-ForPort -ExpectedOpen $false
    $serverWasStopped = $true

    Write-Host "Copying the complete MySQL data directory..."
    Copy-Item -LiteralPath $dataDir -Destination $physicalBackup -Recurse -Force
    if (Test-Path -LiteralPath $uploadSource) {
        Copy-Item -LiteralPath $uploadSource -Destination (Join-Path $backupDir "uploads") -Recurse -Force
    }

    $escapedPassword = $plainPassword.Replace("\", "\\").Replace("'", "\'")
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$escapedPassword';"
    [IO.File]::WriteAllText($initFile, $sql, (New-Object Text.UTF8Encoding($false)))
    # Administrators need delete permission so the password-bearing file can be
    # removed immediately after MySQL consumes it.
    & icacls.exe $initFile /inheritance:r /grant:r "SYSTEM:(R)" "Administrators:(F)" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not restrict access to the MySQL init file." }

    Write-Host "Starting MySQL once with the password reset statement..."
    $arguments = @(
        "--defaults-file=`"$defaultsFile`"",
        "--init-file=`"$initFile`"",
        "--console"
    )
    $manualServer = Start-Process -FilePath $mysqldExe -ArgumentList $arguments -PassThru -WindowStyle Hidden
    Wait-ForPort -ExpectedOpen $true
    Start-Sleep -Seconds 2
    if ($manualServer.HasExited) { throw "The temporary MySQL server exited unexpectedly." }

    Remove-Item -LiteralPath $initFile -Force
    $env:MYSQL_PWD = $plainPassword
    Invoke-MySqlCheck

    Write-Host "Stopping the temporary server cleanly..."
    & $mysqlAdminExe -uroot shutdown
    if ($LASTEXITCODE -ne 0) { throw "Could not stop the temporary MySQL server cleanly." }
    $manualServer.WaitForExit(60000)
    Wait-ForPort -ExpectedOpen $false

    Write-Host "Starting the normal Windows service..."
    Start-Service -Name $ServiceName
    (Get-Service -Name $ServiceName).WaitForStatus('Running', [TimeSpan]::FromSeconds(60))
    Wait-ForPort -ExpectedOpen $true
    $serverWasStopped = $false
    Invoke-MySqlCheck

    $databaseExists = (& $mysqlExe -uroot --batch --skip-column-names -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DatabaseName';")
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect the database list after reset." }
    if ($databaseExists -eq $DatabaseName) {
        $dumpFile = Join-Path $backupDir "$DatabaseName.sql"
        & (Join-Path $mysqlRoot "bin\mysqldump.exe") --single-transaction --routines --events --default-character-set=utf8mb4 -uroot $DatabaseName "--result-file=$dumpFile"
        if ($LASTEXITCODE -ne 0) { throw "The logical database backup failed." }
        Write-Host "Database $DatabaseName was found and dumped successfully."
    }
    else {
        Write-Warning "Database $DatabaseName was not found. The full physical MySQL backup is still available."
    }

    Write-Host "MySQL root password reset completed." -ForegroundColor Green
    Write-Host "Backup: $backupDir" -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $initFile) {
        Remove-Item -LiteralPath $initFile -Force -ErrorAction SilentlyContinue
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    $plainPassword = $null

    if ($serverWasStopped) {
        $portOpen = $null -ne (Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue |
            Select-Object -First 1)
        if (-not $portOpen) {
            Start-Service -Name $ServiceName -ErrorAction SilentlyContinue
        }
    }
}
