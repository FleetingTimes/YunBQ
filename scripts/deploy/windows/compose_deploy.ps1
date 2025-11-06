<#
YunBQ 一键部署（Windows PowerShell + Docker Desktop）
说明：使用仓库内模板与本地构建产物，通过 Docker Compose 在本机或远端路径部署。
注意：Windows 环境通常用于本地验证，生产建议使用 Linux。
#>

param(
  [string]$TargetDir = "C:/opt/yunbq"
)

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)

Write-Host "[1/6] 准备目标目录：$TargetDir" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path "$TargetDir" | Out-Null
New-Item -ItemType Directory -Force -Path "$TargetDir/nginx","$TargetDir/data/mysql","$TargetDir/data/redis","$TargetDir/uploads/avatars" | Out-Null

Write-Host "[2/6] 拷贝 Compose 与 Nginx 模板..." -ForegroundColor Cyan
Copy-Item -Force "$RepoRoot/scripts/templates/docker-compose.yml" "$TargetDir/docker-compose.yml"
Copy-Item -Force "$RepoRoot/scripts/templates/nginx/default.conf" "$TargetDir/nginx/default.conf"

Write-Host "[3/6] 准备 env 文件..." -ForegroundColor Cyan
if (-not (Test-Path "$TargetDir/docker-compose.env")) {
  Copy-Item -Force "$RepoRoot/scripts/env/docker-compose.env.example" "$TargetDir/docker-compose.env"
  Write-Host "已生成示例 env：$TargetDir/docker-compose.env，请根据注释修改敏感值" -ForegroundColor Yellow
}

Write-Host "[4/6] 校验构建产物..." -ForegroundColor Cyan
if (-not (Test-Path "$RepoRoot/backend/target/backend-0.0.1-SNAPSHOT.jar")) { Write-Error "后端 Jar 未构建：请先在 backend 执行 mvn -q -DskipTests package"; exit 1 }
if (-not (Test-Path "$RepoRoot/frontend/dist")) { Write-Error "前端未构建：请先设置 VITE_API_BASE 并执行 npm ci && npm run build"; exit 1 }

Write-Host "[5/6] 拷贝前后端产物到部署目录..." -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path "$TargetDir/backend/target","$TargetDir/frontend" | Out-Null
Copy-Item -Force "$RepoRoot/backend/target/backend-0.0.1-SNAPSHOT.jar" "$TargetDir/backend/target/"
Copy-Item -Recurse -Force "$RepoRoot/frontend/dist" "$TargetDir/frontend/dist"

Write-Host "[6/6] 启动 Compose..." -ForegroundColor Cyan
Push-Location "$TargetDir"
docker compose --env-file "$TargetDir/docker-compose.env" up -d
Pop-Location

Write-Host "部署完成，进行健康检查..." -ForegroundColor Cyan
try { Invoke-WebRequest -Uri "http://com.linaa.shiyan:6639/" -UseBasicParsing -TimeoutSec 5 | Out-Null; Write-Host "前端 OK" -ForegroundColor Green } catch { Write-Warning "前端检查失败" }
try { Invoke-WebRequest -Uri "http://com.linaa.shiyan:6639/api/health" -UseBasicParsing -TimeoutSec 5 | Out-Null; Write-Host "后端 OK" -ForegroundColor Green } catch { Write-Warning "后端检查失败（尝试访问 actuator/health）" }

Write-Host "提示：生产建议使用 Linux + systemd 管理 Compose，详见生产部署文档。" -ForegroundColor Yellow