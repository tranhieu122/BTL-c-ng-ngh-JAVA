param([string]$MysqlHome = 'C:\Program Files\MySQL\MySQL Server 8.0', [int]$Port = 13367)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runDirectory = Join-Path $workspace ('target\mysql-profile-' + [Guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $runDirectory 'data'
New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$serverExe = Join-Path $MysqlHome 'bin\mysqld.exe'
$clientExe = Join-Path $MysqlHome 'bin\mysql.exe'
if (!(Test-Path -LiteralPath $serverExe)) { throw 'MySQL Server binary not found.' }
$portProbe = [Net.Sockets.TcpClient]::new()
try {
    $portProbe.Connect('127.0.0.1', $Port)
    throw 'Test port already in use; choose another port.'
} catch [Net.Sockets.SocketException] {
    # Connection refused means the isolated test port is available.
} finally {
    $portProbe.Dispose()
}
# This instance reads no machine configuration and uses only the newly created test datadir.
$ErrorActionPreference = 'Continue'
& $serverExe --no-defaults --initialize-insecure "--basedir=$MysqlHome" "--datadir=$dataDirectory" --console 2> (Join-Path $runDirectory 'initialize.log')
$ErrorActionPreference = 'Stop'
if ($LASTEXITCODE -ne 0) { throw 'Could not initialize isolated MySQL; see initialize.log.' }
$arguments = @('--no-defaults', ('--basedir="' + $MysqlHome + '"'), ('--datadir="' + $dataDirectory + '"'), "--port=$Port", '--bind-address=127.0.0.1', '--mysqlx=0', '--skip-log-bin', '--console')
$serverProcess = Start-Process -FilePath $serverExe -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runDirectory 'server.out.log') -RedirectStandardError (Join-Path $runDirectory 'server.err.log')
try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        $probe = [Net.Sockets.TcpClient]::new()
        try { $probe.Connect('127.0.0.1', $Port); $ready = $true; break } catch { Start-Sleep -Milliseconds 500 } finally { $probe.Dispose() }
    }
    if (!$ready -or $serverProcess.HasExited) { throw 'Isolated MySQL did not start.' }
    & $clientExe --no-defaults --host=127.0.0.1 "--port=$Port" --user=root --execute='CREATE DATABASE edurepo_profile_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
    if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated test schema.' }
    Push-Location $workspace
    try {
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd -o '-Dmaven.repo.local=.m2/repository' "-Dprofile.mysql.port=$Port" -Dtest=MysqlProfileMigrationTest test *> (Join-Path $runDirectory 'test.log')
        $testExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
    } finally { Pop-Location }
    Write-Output "MySQL test log: $runDirectory\test.log"
    if ($testExit -ne 0) { throw "MySQL migration/profile tests failed ($testExit)." }
} finally {
    # Stop only the exact process object started by this script.
    if (!$serverProcess.HasExited) {
        & (Join-Path $MysqlHome 'bin\mysqladmin.exe') --no-defaults --host=127.0.0.1 "--port=$Port" --user=root shutdown 2> (Join-Path $runDirectory 'shutdown.log')
        if (!$serverProcess.WaitForExit(10000)) { Stop-Process -Id $serverProcess.Id }
    }
}
