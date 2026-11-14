$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $projectRoot '.runtime\local-db.json'
if (!(Test-Path -LiteralPath $configPath)) { Write-Host '没有项目数据库配置。'; return }
$db = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$connection = Get-NetTCPConnection -LocalPort $db.port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$connection) { Write-Host '项目 MySQL 未运行。'; return }
$running = Get-CimInstance Win32_Process -Filter "ProcessId = $($connection.OwningProcess)"
$expectedDir = Join-Path $projectRoot '.runtime\mysql'
if ($running.Name -ne 'mysqld.exe' -or $running.CommandLine -notlike "*$expectedDir*") { throw '目标不是本项目独立数据库，拒绝停止。' }
$clientConfig = Join-Path $projectRoot '.runtime\mysql-admin.cnf'
@("[client]",'user=root',"password=$($db.rootPassword)",'host=127.0.0.1',"port=$($db.port)") | Set-Content -LiteralPath $clientConfig -Encoding ascii
& (Join-Path $db.mysqlHome 'bin\mysqladmin.exe') "--defaults-extra-file=$clientConfig" shutdown
if ($LASTEXITCODE -ne 0) { throw '数据库正常停止失败，请查看日志。' }
Write-Host '项目 MySQL 已正常停止，数据保留。'
