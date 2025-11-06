<#
YunBQ 一键本地运行（Windows PowerShell）
目标：不启用任何开发服务器/热重载，使用生产构建与打包产物在本机启动前后端。

执行前置：
- 必须已安装 Java 17、Maven、Node.js 18+、npm。
- 如需本地 MySQL/Redis，可手动启动或通过 Docker 运行（见文档）。

流程概览：
1) 构建后端 Jar（跳过测试）。
2) 以 prod Profile 启动后端 Jar（端口 6639），写入 PID。
3) 设置 VITE_API_BASE 并构建前端 dist。
4) 使用 npx serve 以静态站点方式提供 dist（端口 5173），写入 PID。
5) 健康检查：后端 /actuator/health 或 /api/health；前端首页。

用法：
  以管理员或普通用户在仓库根目录执行：
    PowerShell> ./scripts/local-run/windows/build_and_run_local.ps1

注意：
- 首次运行会创建 scripts/.pids 目录保存进程 PID；停止请使用 stop_local.ps1。
 - 所有关键参数可在“配置区”按需调整。
#>

param(
  [string]$BackendDir = "backend",
  [string]$FrontendDir = "frontend",
  [int]$BackendPort = 6639,
  [int]$FrontendPort = 5173,
  [string]$ApiBase = "http://localhost:6639/api",
  [string]$JwtSecret = "please_replace_with_a_strong_secret",
  [string]$DbUrl = "jdbc:mysql://localhost:3306/yunbq?useSSL=false&serverTimezone=UTC",
  [string]$DbUser = "yunbq_user",
  [string]$DbPass = "strong_password_here",
  [string]$RedisHost = "localhost",
  [int]$RedisPort = 6379,
  [string]$FrontendBaseUrl = "http://localhost:5173"
)

function Require-Cmd($cmd) {
  if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
    Write-Error "缺少命令：$cmd。请安装后重试。"; exit 1
  }
}

Write-Host "[1/5] 检查工具链..." -ForegroundColor Cyan
Require-Cmd mvn
Require-Cmd java
Require-Cmd node
Require-Cmd npm

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$PidsDir = Join-Path $RepoRoot "scripts/.pids"
New-Item -ItemType Directory -Force -Path $PidsDir | Out-Null

Write-Host "[2/5] 构建后端 Jar..." -ForegroundColor Cyan
Push-Location (Join-Path $RepoRoot $BackendDir)
mvn -q -DskipTests package
if ($LASTEXITCODE -ne 0) { Write-Error "后端构建失败"; Pop-Location; exit 1 }
$JarPath = Join-Path (Join-Path $RepoRoot $BackendDir) "target/backend-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $JarPath)) { Write-Error "未找到 Jar：$JarPath"; Pop-Location; exit 1 }
Pop-Location

Write-Host "[3/5] 启动后端（端口 $BackendPort）..." -ForegroundColor Cyan
$BackendArgs = @(
  "-jar", $JarPath,
  "--spring.profiles.active=prod",
  "--server.port=$BackendPort",
  "--spring.datasource.url=$DbUrl",
  "--spring.datasource.username=$DbUser",
  "--spring.datasource.password=$DbPass",
  "--spring.redis.host=$RedisHost",
  "--spring.redis.port=$RedisPort",
  "--jwt.secret=$JwtSecret",
  "--frontend.base-url=$FrontendBaseUrl"
)
$BackendProc = Start-Process -FilePath "java" -ArgumentList $BackendArgs -NoNewWindow -PassThru
Set-Content -Path (Join-Path $PidsDir "backend.pid") -Value $BackendProc.Id
Start-Sleep -Seconds 3

Write-Host "[4/5] 构建前端并启动静态站点（端口 $FrontendPort）..." -ForegroundColor Cyan
Push-Location (Join-Path $RepoRoot $FrontendDir)
npm ci
$env:VITE_API_BASE = $ApiBase
npm run build
if ($LASTEXITCODE -ne 0) { Write-Error "前端构建失败"; Pop-Location; exit 1 }
$ServeArgs = @("-y", "serve", "-s", "dist", "-l", "$FrontendPort")
$ServeProc = Start-Process -FilePath "npx" -ArgumentList $ServeArgs -NoNewWindow -PassThru
Set-Content -Path (Join-Path $PidsDir "frontend.pid") -Value $ServeProc.Id
Pop-Location

Write-Host "[5/5] 健康检查..." -ForegroundColor Cyan
try {
  $health1 = Invoke-WebRequest -Uri "http://localhost:$BackendPort/actuator/health" -UseBasicParsing -TimeoutSec 5
  Write-Host "后端健康 (actuator)：" $health1.StatusCode
} catch {
  try {
    $health2 = Invoke-WebRequest -Uri "http://localhost:$BackendPort/api/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "后端健康 (api/health)：" $health2.StatusCode
  } catch {
    Write-Warning "后端健康检查未通过，稍后重试或查看日志"
  }
}
try {
  $home = Invoke-WebRequest -Uri "http://localhost:$FrontendPort" -UseBasicParsing -TimeoutSec 5
  Write-Host "前端首页：" $home.StatusCode
} catch { Write-Warning "前端首页检查失败" }

Write-Host "完成：后端 PID=$(Get-Content (Join-Path $PidsDir 'backend.pid'))，前端 PID=$(Get-Content (Join-Path $PidsDir 'frontend.pid'))" -ForegroundColor Green