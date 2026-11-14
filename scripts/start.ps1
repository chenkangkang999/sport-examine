param([int]$ServerPort = 8080, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Path $runtimeDir -Force | Out-Null
if (!$env:DB_URL) {
    & (Join-Path $PSScriptRoot 'start-local-mysql.ps1')
    $db = Get-Content -LiteralPath (Join-Path $runtimeDir 'local-db.json') -Raw | ConvertFrom-Json
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$($db.port)/tijian?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    $env:DB_USERNAME = $db.user
    $env:DB_PASSWORD = $db.password
}
if (Get-NetTCPConnection -LocalPort $ServerPort -State Listen -ErrorAction SilentlyContinue) {
    if ($ServerPort -eq 8080 -and !(Get-NetTCPConnection -LocalPort 18080 -State Listen -ErrorAction SilentlyContinue)) { $ServerPort = 18080 } else { throw "端口 $ServerPort 已占用；可通过 -ServerPort 指定。" }
}
foreach ($port in @(8081,8089)) { if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "前端端口 $port 已占用。请先使用 stop.ps1 停止本项目或调整占用。" } }
if (!$SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
$jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'server\target') -Filter '*.jar' | Where-Object { $_.Name -notlike '*original*' } | Select-Object -First 1
if (!$jar) { throw '后端构建产物缺失，请先运行 scripts/build.ps1。' }
$env:SERVER_PORT = "$ServerPort"
$env:VITE_API_TARGET = "http://127.0.0.1:$ServerPort"
$javaExe = (Get-Command java.exe).Source
$nodeExe = (Get-Command node.exe).Source
$processes = @()
try {
$processes += Start-Process -FilePath $javaExe -ArgumentList @('-jar', "`"$($jar.FullName)`"") -WorkingDirectory (Join-Path $projectRoot 'server') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir 'server.stdout.log') -RedirectStandardError (Join-Path $runtimeDir 'server.stderr.log')
$deadline = (Get-Date).AddSeconds(75)
do {
    try { $null = Invoke-RestMethod "http://127.0.0.1:$ServerPort/health/ready" -TimeoutSec 2; break } catch {
        if ($processes[0].HasExited -or (Get-Date) -gt $deadline) { throw '后端启动失败，查看 .runtime/server.stdout.log 和 server.stderr.log。' }
        Start-Sleep -Milliseconds 500
    }
} while ($true)
foreach ($app in @('user-web','manage-web')) {
    $vite = Join-Path $projectRoot "$app\node_modules\vite\bin\vite.js"
    $processes += Start-Process -FilePath $nodeExe -ArgumentList @("`"$vite`"", '--host', '127.0.0.1') -WorkingDirectory (Join-Path $projectRoot $app) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeDir "$app.stdout.log") -RedirectStandardError (Join-Path $runtimeDir "$app.stderr.log")
}
foreach ($port in @(8081,8089)) {
    $frontendDeadline = (Get-Date).AddSeconds(30)
    do {
        try { $null = Invoke-WebRequest "http://127.0.0.1:$port/" -TimeoutSec 2; break } catch {
            if (($processes | Where-Object { $_.HasExited }).Count -gt 0 -or (Get-Date) -gt $frontendDeadline) { throw "前端 $port 启动失败，请查看 .runtime 中的日志。" }
            Start-Sleep -Milliseconds 400
        }
    } while ($true)
}
@{ processes = @($processes | ForEach-Object { @{ id=$_.Id; startTime=$_.StartTime.ToUniversalTime().ToString('o'); executable=$_.Path } }); serverPort = $ServerPort } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runtimeDir 'processes.json') -Encoding utf8
Write-Host "用户端：http://127.0.0.1:8081/"
Write-Host "医生/管理员端：http://127.0.0.1:8089/"
Write-Host "后端：http://127.0.0.1:$ServerPort/；日志位于 .runtime。"
} catch {
    # 仅回收本次调用创建、且命令行确实属于本项目的进程；不处理既有服务。
    foreach ($started in $processes) {
        $running = Get-CimInstance Win32_Process -Filter "ProcessId = $($started.Id)" -ErrorAction SilentlyContinue
        if ($running -and $running.CommandLine -like "*$projectRoot*") { Stop-Process -Id $started.Id -ErrorAction SilentlyContinue }
    }
    throw
}
