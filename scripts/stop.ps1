$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$processFile = Join-Path $projectRoot '.runtime\processes.json'
if (!(Test-Path -LiteralPath $processFile)) { Write-Host '没有本项目启动记录。'; return }
$records = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json
foreach ($record in $records.processes) {
    $processId = $record.id
    $running = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
    if ($running -and $running.CommandLine -like "*$projectRoot*" -and $running.ExecutablePath -eq $record.executable) {
        $currentProcess = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($currentProcess -and $currentProcess.StartTime.ToUniversalTime().Ticks -eq ([DateTime]$record.startTime).ToUniversalTime().Ticks) { Stop-Process -Id $processId; Write-Host "已停止项目进程 $processId" }
    }
}
Remove-Item -LiteralPath $processFile
Write-Host '项目 Web 服务已停止，MySQL 数据保留。需要停止独立数据库可运行 stop-local-mysql.ps1。'
