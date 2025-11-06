<#
YunBQ 一键停止部署（Windows PowerShell + Docker Desktop）
说明：在指定部署目录下执行 docker compose down。
#>

param(
  [string]$TargetDir = "C:/opt/yunbq"
)

Push-Location $TargetDir
docker compose down
Pop-Location
Write-Host "已停止 YunBQ Compose 堆栈：$TargetDir" -ForegroundColor Green