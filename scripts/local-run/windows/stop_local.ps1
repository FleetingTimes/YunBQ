<#
YunBQ 一键停止本地运行（Windows PowerShell）
说明：读取 scripts/.pids 下记录的后端与前端进程 PID 并停止。
用法：
  PowerShell> ./scripts/local-run/windows/stop_local.ps1
#>

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$PidsDir = Join-Path $RepoRoot "scripts/.pids"

function Stop-ByPidFile($name, $file) {
  $path = Join-Path $PidsDir $file
  if (-not (Test-Path $path)) { Write-Warning "未发现 $name PID 文件：$path"; return }
  $pid = Get-Content $path | Select-Object -First 1
  try {
    Stop-Process -Id $pid -ErrorAction Stop
    Write-Host "已停止 $name 进程 (PID=$pid)" -ForegroundColor Green
  } catch {
    Write-Warning "停止 $name 失败或进程不存在 (PID=$pid)"
  }
  Remove-Item $path -ErrorAction SilentlyContinue
}

Stop-ByPidFile -name "后端" -file "backend.pid"
Stop-ByPidFile -name "前端" -file "frontend.pid"

Write-Host "完成：本地运行进程已尝试停止。" -ForegroundColor Green