param([int]$Port = 3307)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.runtime'
$dataDir = Join-Path $runtimeDir 'mysql'
$configPath = Join-Path $runtimeDir 'local-db.json'
$mysqlHome = if ($env:MYSQL_HOME) { $env:MYSQL_HOME } else { 'C:\Program Files\MySQL\MySQL Server 8.0' }
$serverExe = Join-Path $mysqlHome 'bin\mysqld.exe'
$clientExe = Join-Path $mysqlHome 'bin\mysql.exe'
if (!(Test-Path -LiteralPath $serverExe)) { throw '找不到 MySQL 8。设置 MYSQL_HOME，或使用自己的 DB_URL、DB_USERNAME、DB_PASSWORD。' }
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    $running = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener[0].OwningProcess)"
    if ($running.Name -ne 'mysqld.exe' -or $running.CommandLine -notlike "*$dataDir*") { throw "端口 $Port 被其他服务占用，未修改该服务。" }
    if (!(Test-Path -LiteralPath $configPath)) { throw '独立实例已运行，但凭据文件缺失。请恢复 .runtime/local-db.json。' }
    Write-Host "项目 MySQL 已运行：127.0.0.1:$Port"
    return
}
$isNew = !(Test-Path -LiteralPath (Join-Path $dataDir 'mysql'))
if (!$isNew -and !(Test-Path -LiteralPath $configPath)) { throw '已有独立数据库但凭据文件缺失；请恢复 .runtime/local-db.json，未启动或删除已有数据库。' }
if (Test-Path -LiteralPath $configPath) {
    $savedConfig = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    if ($savedConfig.initialized -eq $false) { throw '数据库首次账号配置未完成。凭据已保存在 .runtime/local-db.json，请根据日志恢复账号后将 initialized 改为 true；未自动删除任何数据。' }
}
if ($isNew) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
    & $serverExe --no-defaults --initialize-insecure "--basedir=$mysqlHome" "--datadir=$dataDir" 2> (Join-Path $runtimeDir 'mysql-initialize.log')
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 初始化失败，查看 .runtime/mysql-initialize.log。' }
}
$arguments = @('--no-defaults', "--basedir=`"$mysqlHome`"", "--datadir=`"$dataDir`"", "--port=$Port", '--bind-address=127.0.0.1', '--mysqlx=OFF', '--character-set-server=utf8mb4', '--collation-server=utf8mb4_unicode_ci', '--console')
$databaseProcess = Start-Process -FilePath $serverExe -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'mysql.stdout.log') -RedirectStandardError (Join-Path $runtimeDir 'mysql.stderr.log')
try {
$deadline = (Get-Date).AddSeconds(45)
while (!(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)) {
    if ($databaseProcess.HasExited -or (Get-Date) -gt $deadline) { throw '项目 MySQL 启动失败，查看 .runtime/mysql.stderr.log。' }
    Start-Sleep -Milliseconds 300
}
if ($isNew) {
    $randomBytes = New-Object byte[] 24
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($randomBytes)
    $rootPassword = [BitConverter]::ToString($randomBytes).Replace('-', '')
    $generator.GetBytes($randomBytes)
    $appPassword = [BitConverter]::ToString($randomBytes).Replace('-', '')
    $generator.Dispose()
    # 修改数据库密码前先保存恢复信息，避免写盘失败时丢失新密码。
    @{ port = $Port; user = 'health_app'; password = $appPassword; rootPassword = $rootPassword; mysqlHome = $mysqlHome; initialized = $false } | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
    $sql = @"
CREATE DATABASE IF NOT EXISTS tijian CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS tijian_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'health_app'@'127.0.0.1' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON tijian.* TO 'health_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON tijian_test.* TO 'health_app'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
"@
    $sql | & $clientExe --no-defaults -h 127.0.0.1 "--port=$Port" -u root --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw '专用数据库账号初始化失败。' }
    @{ port = $Port; user = 'health_app'; password = $appPassword; rootPassword = $rootPassword; mysqlHome = $mysqlHome; initialized = $true } | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding utf8
}
$databaseProcess.Id | Set-Content -LiteralPath (Join-Path $runtimeDir 'mysql.pid')
Write-Host "项目 MySQL 已启动：127.0.0.1:$Port；数据与凭据位于 .runtime（已忽略提交）。"
} catch {
    if (!$databaseProcess.HasExited) { Stop-Process -Id $databaseProcess.Id -ErrorAction SilentlyContinue }
    throw
}
