$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runningRecord = Join-Path $projectRoot '.runtime\processes.json'
if (Test-Path -LiteralPath $runningRecord) {
    $recorded = Get-Content -LiteralPath $runningRecord -Raw | ConvertFrom-Json
    foreach ($entry in $recorded.processes) {
        $running = Get-CimInstance Win32_Process -Filter "ProcessId = $($entry.id)" -ErrorAction SilentlyContinue
        if ($running -and $running.CommandLine -like "*$projectRoot*") { throw '请先运行 scripts/stop.ps1 再构建，以免 Windows 锁定正在使用的 JAR 或前端依赖。' }
    }
}
$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$maven = if ($env:MAVEN_HOME) { Join-Path $env:MAVEN_HOME 'bin\mvn.cmd' } elseif ($mavenCommand) { $mavenCommand.Source } else { 'D:\IntelliJ IDEA\IntelliJ IDEA 2024.3.7.1\plugins\maven\lib\maven3\bin\mvn.cmd' }
if (!(Test-Path -LiteralPath $maven)) { throw '找不到 Maven 3，请设置 MAVEN_HOME 或把 mvn.cmd 加入 PATH。' }
foreach ($app in @('user-web', 'manage-web')) {
    Push-Location (Join-Path $projectRoot $app)
    try {
        if (Test-Path -LiteralPath 'package-lock.json') { & npm.cmd ci --no-audit --no-fund } else { & npm.cmd install --no-audit --no-fund }
        if ($LASTEXITCODE -ne 0) { throw "$app 依赖安装失败" }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "$app 构建失败" }
    } finally { Pop-Location }
}
Push-Location (Join-Path $projectRoot 'server')
try {
    & $maven --batch-mode package
    if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }
} finally { Pop-Location }
Write-Host '三个工程构建完成。'
