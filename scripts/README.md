# scripts 目录说明

该目录包含本项目的本地运行与部署相关脚本、环境变量示例、以及 Docker/Nginx 的模板文件。旨在让你在 Windows 或 Linux 环境下快速完成生产构建、本地运行或基于 Docker 的一键部署。

## 目录结构
- `deploy/`
  - `windows/compose_deploy.ps1`：在 Windows 上将已构建产物按模板一键布署到目标目录，并启动 Docker Compose
  - `windows/compose_stop.ps1`：停止并清理已部署的 Compose 服务
  - `linux/compose_deploy.sh`：在 Linux 上进行一键部署（生成目录、拷贝模板、启动 Compose）
  - `linux/compose_stop.sh`：停止并清理 Linux 环境的 Compose 服务
- `local-run/`
  - `windows/build_and_run_local.ps1`：在 Windows 本机进行生产构建并启动前后端（不启用开发服务器/热重载）
  - `windows/stop_local.ps1`：停止本地运行的前后端进程（基于 PID 文件）
  - `linux/build_and_run_local.sh`：在 Linux 本机进行生产构建并启动前后端
  - `linux/stop_local.sh`：停止 Linux 环境中的本地进程
- `templates/`
  - `docker-compose.yml`：标准化 Compose 模板（MySQL、Redis、后端、Nginx）
  - `nginx/default.conf`：Nginx 默认站点配置，将前端静态目录与上传目录映射，并反向代理后端 API（如有）
- `env/`
  - `docker-compose.env.example`：Compose 环境变量示例文件（请复制为 `docker-compose.env` 并填入真实值）

## 前置要求
- 通用工具链：
  - Java 17、Maven、Node.js 18+、npm
- 数据与中间件：
  - MySQL 8（必需）、Redis 7（可选）
- Docker 环境（用于部署场景）：
  - Windows：Docker Desktop
  - Linux：Docker 与 docker compose 插件

## 本地运行（生产构建）
- Windows：在仓库根目录执行 `./scripts/local-run/windows/build_and_run_local.ps1`
  - 行为：
    - 构建后端 Jar（跳过测试）：`backend/target/backend-0.0.1-SNAPSHOT.jar`
    - 启动后端（默认端口 `6639`，Profile `prod`），写入 PID 到 `scripts/.pids/backend.pid`
    - 设置 `VITE_API_BASE` 并构建前端 `dist`，使用 `npx serve` 启动静态站点（默认端口 `5173`），写入 PID
    - 健康检查：后端 `http://localhost:6639/actuator/health` 或 `http://localhost:6639/api/health`，前端首页 `http://localhost:5173/`
  - 关键参数（脚本内可调整）：
    - `--server.port`（后端端口），`VITE_API_BASE`（前端 API 地址）
    - 数据库与 Redis 连接、`jwt.secret`（必须替换为强随机密钥）
  - 停止：执行 `./scripts/local-run/windows/stop_local.ps1`
- Linux：在仓库根目录执行 `./scripts/local-run/linux/build_and_run_local.sh`
  - 行为与参数与 Windows 版本一致；停止脚本为 `./scripts/local-run/linux/stop_local.sh`

## Docker Compose 一键部署
- Windows：在仓库根目录执行 `./scripts/deploy/windows/compose_deploy.ps1 -TargetDir C:/opt/yunbq`
  - 行为：
    - 在目标目录创建结构与数据目录：`nginx/`、`data/mysql`、`data/redis`、`uploads/avatars`
    - 拷贝模板：`templates/docker-compose.yml`、`templates/nginx/default.conf`
    - 准备环境文件：首次运行将复制 `scripts/env/docker-compose.env.example` 到部署目录并提示修改敏感值
    - 校验并拷贝构建产物：后端 Jar 与前端 `dist`
    - 启动：`docker compose --env-file docker-compose.env up -d`
    - 健康检查：前端首页与后端 `api/health`
- Linux：在仓库根目录执行 `./scripts/deploy/linux/compose_deploy.sh /opt/yunbq`
  - 行为与 Windows 版本一致；停止脚本为 `./scripts/deploy/linux/compose_stop.sh`

### Compose 模板（templates/docker-compose.yml）要点
- 服务：
  - `mysql`：`MYSQL_DATABASE/USER/PASSWORD/ROOT_PASSWORD`，挂载 `./data/mysql`
  - `redis`：持久化到 `./data/redis`
  - `backend`：运行 `backend-0.0.1-SNAPSHOT.jar`，挂载 `./uploads` 到容器路径用于静态资源；通过环境变量传递数据库、Redis 与 JWT 配置
  - `nginx`：托管前端 `dist` 与静态 `uploads`，加载 `nginx/default.conf`
- 端口：对外暴露 `EXPOSE_PORT`（默认 `6639`），容器内 Nginx 固定监听 `6639`
- 依赖与网络：`backend` 依赖 `mysql/redis`；所有服务在 `yunbq-net` 网络下

### Nginx 模板（templates/nginx/default.conf）要点
- 将 `frontend/dist` 作为站点根目录
- 将 `uploads`（含 `uploads/avatars`）映射到对应静态路径，供头像等资源访问
- 如需反向代理后端 API，可在该模板中添加 `/api` 的代理规则（根据你的部署拓扑）

### 环境变量（env/docker-compose.env.example）说明
- 端口映射：`EXPOSE_PORT`（如 `6639`）
- 数据库（必填）：`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
- Redis（可选）：`SPRING_DATA_REDIS_HOST/PORT`
- JWT（必填）：`JWT_SECRET`（必须替换为强随机密钥）、`JWT_ISSUER`、`JWT_EXPIRE_MINUTES`
- 自动建表：`SPRING_SQL_INIT_MODE`（首次部署设为 `always`，完成后改为 `never`；模板默认 `never`）

## 首次部署（自动建表）流程
- 在部署目录的 `docker-compose.env` 设置 `SPRING_SQL_INIT_MODE=always`
- 启动后确认表结构创建完成（检查数据库或后端日志），将其改为 `SPRING_SQL_INIT_MODE=never` 并重新执行 `docker compose up -d`
- 如已使用手动脚本 `backend/sql/init_schema.sql` 完成建库与建表，保持 `SPRING_SQL_INIT_MODE=never`，避免重复初始化

## 运行与安全建议
- 生产环境必须替换所有默认敏感值（数据库密码、JWT 密钥）
- 为 `uploads/` 与 `logs/` 配置持久化目录，避免容器重启后数据丢失
- 若经由 CDN/反向代理部署，真实客户端 IP 通过 `CF-Connecting-IP`、`X-Forwarded-For` 或 `X-Real-IP` 解析（后端已支持）
- 建议使用 `systemd` 管理 Compose 服务（Linux），并配置防火墙放行 `EXPOSE_PORT`

## 常见问题
- 前端无法访问后端：检查 `VITE_API_BASE` 是否指向后端可达地址（本地为 `http://localhost:6639/api`）
- 静态资源 404：确认 `uploads/avatars` 目录存在且挂载到 Nginx；后端已将工作目录 `uploads/` 映射为 `/uploads/**`
- 数据库连接失败：核对 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 并确认 MySQL 容器或服务已启动
- 端口占用：调整后端 `--server.port` 或 Compose 的 `EXPOSE_PORT`

如需扩展部署（多实例、CDN、对象存储 OSS 等），可在 `templates/` 中新增模板并配套脚本。欢迎根据你的环境定制并提交改进。
