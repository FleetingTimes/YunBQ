# 拾言（YunBQ）

YunBQ 是一个前后端分离的轻量应用，包含「拾言广场/便签」与「导航系统」两大模块。支持用户注册登录、个人资料管理（头像、昵称、签名）、公开浏览与管理员运维。后端基于 Spring Boot + JWT，前端基于 Vue 3 + Vite，提供本地快速运行与 Docker 一键部署能力。

## 更名公告
- 项目原名 `YunBQ`，现正式更名为 `拾言`。
- 代码与接口中的标识将逐步同步；后端便签主表统一使用 `shiyan`，并提供接口别名 `/api/shiyan` 以便前端访问。
- 文档与脚本已按更名调整，现有功能与使用方式不变。

## 立即上手
- 后端开发启动：`mvn -q -DskipTests package && java -jar backend/target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port=6639`
- 前端开发启动：`cd frontend && npm ci && npm run dev`（将 `VITE_API_BASE` 设为 `http://localhost:6639/api`）
- 数据库初始化：在 MySQL 执行 `backend/sql/init_schema.sql`（已含建库与全部表结构）
- 一键本地运行（生产构建）：Windows 执行 `./scripts/local-run/windows/build_and_run_local.ps1`

## 特性
- 安全认证：Spring Security 6 + JWT 无状态认证
- 数据访问：MyBatis-Plus，内置便签、导航分类与站点模型
- 缓存策略：默认 Caffeine，本地高性能；可选 Redis（`spring.profiles.active=redis`）
- 请求观测：统一 `X-Request-Id`，关键指标入库，日志滚动归档
- 资源与跨域：静态 `uploads/**` 映射，CORS 开发/生产配置分离
- 部署支持：Windows/Linux 脚本与 Compose/Nginx 模板，快速上线

## 目录结构
- `backend/` 后端 Spring Boot 源码与资源
- `frontend/` 前端 Vue 3 应用
- `scripts/` 本地运行与部署脚本、Compose/Nginx 模板、环境变量示例
- `uploads/` 用户上传资源（如 `avatars/` 头像），由后端映射为静态路径
- 文档：`部署.md`、`生产部署-Docker-Nginx.md`、`生产上线完整清单.md`、`本地运行-不启用开发工具.md`

## 环境要求
- `Java 17`、`Maven`、`Node.js 18+`、`npm`
- `MySQL 8`（必需）、`Redis 7`（可选）

## 开发启动
- 后端
  - 数据库：创建 `yunbq` 库或执行 `backend/sql/init_schema.sql`
  - 启动示例：
    ```bash
    cd backend
    mvn -q -DskipTests package
    java -jar target/backend-0.0.1-SNAPSHOT.jar \
      --spring.profiles.active=dev \
      --server.port=6639 \
      --spring.datasource.url="jdbc:mysql://localhost:3306/yunbq?useSSL=false&serverTimezone=UTC" \
      --spring.datasource.username=yunbq_user \
      --spring.datasource.password=strong_password \
      --jwt.secret=please_replace_with_a_strong_secret
    ```
  - 可选 Redis：设置 `spring.profiles.active=redis`，按 `backend/src/main/resources/application-redis.yml` 配置连接
- 前端
  - 启动：
    ```bash
    cd frontend
    npm ci
    npm run dev
    ```
  - 环境变量：`frontend/.env.development` 与 `.env.production` 中 `VITE_API_BASE` 控制后端地址

## 一键本地运行（生产构建）
- Windows：`./scripts/local-run/windows/build_and_run_local.ps1`
- Linux：`./scripts/local-run/linux/build_and_run_local.sh`
- 行为：构建后端 Jar（默认端口 `6639`），按 `VITE_API_BASE` 构建前端 `dist` 并以静态站点提供，写入 PID 文件便于停止

## Docker Compose 部署
- 自动化脚本：
  - Windows：`./scripts/deploy/windows/compose_deploy.ps1 -TargetDir C:/opt/yunbq`
  - Linux：`./scripts/deploy/linux/compose_deploy.sh /opt/yunbq`
- 环境文件：复制并修改 `scripts/env/docker-compose.env.example`（敏感值必须替换）
- 关键变量：`EXPOSE_PORT`、`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`SPRING_DATA_REDIS_HOST/PORT`、`JWT_SECRET/ISSUER/EXPIRE_MINUTES`
- 自动建表：`SPRING_SQL_INIT_MODE`（首次部署设为 `always`，表结构创建后改为 `never`）
- 启动：`docker compose --env-file docker-compose.env up -d`
- 组件：`mysql`、`redis`、`backend`（Java 17 JRE）、`nginx`（前端与静态资源）

- 更多脚本与部署细节：见 `scripts/README.md`

## 部署运行环境
- Windows
  - 要求：`Windows 10/11`、`PowerShell 5+`、`Java 17`、`Maven`、`Node.js 18+`、`npm`；可选 `Docker Desktop`
  - 本地运行（生产构建）：`./scripts/local-run/windows/build_and_run_local.ps1`
  - Docker 部署：`./scripts/deploy/windows/compose_deploy.ps1 -TargetDir C:/opt/yunbq`
  - 注意：若端口被占用可调整 `--server.port` 或 `EXPOSE_PORT`；首次执行会生成 `docker-compose.env` 示例需修改敏感值
- Linux
  - 要求：`Ubuntu 20.04+/Debian/RHEL`、`Docker` 与 `docker compose` 插件或 `Java 17 + MySQL 8 + Redis 7 + Nginx`
  - 本地运行（生产构建）：`./scripts/local-run/linux/build_and_run_local.sh`
  - Docker 部署：`./scripts/deploy/linux/compose_deploy.sh /opt/yunbq`
  - 建议：使用 `systemd` 管理 Compose 服务、开启防火墙放行 `EXPOSE_PORT`；为 MySQL/Redis 设置强密码
- Docker
  - 目录：目标目录包含 `docker-compose.yml`、`nginx/default.conf`、`frontend/dist`、`backend/target/*.jar`、`uploads/`
  - 环境：编辑 `docker-compose.env`，设置 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`JWT_SECRET` 等
  - 启动：`docker compose --env-file docker-compose.env up -d`
  - 健康检查：前端首页 `http://<host>:<EXPOSE_PORT>/`，后端 `http://<host>:<EXPOSE_PORT>/api/health` 或 `actuator/health`

## 脚本快速索引
- Windows 本地运行：`scripts/local-run/windows/build_and_run_local.ps1`
- Windows 停止本地运行：`scripts/local-run/windows/stop_local.ps1`
- Windows 一键部署：`scripts/deploy/windows/compose_deploy.ps1`
- Windows 停止部署：`scripts/deploy/windows/compose_stop.ps1`
- Linux 本地运行：`scripts/local-run/linux/build_and_run_local.sh`
- Linux 停止本地运行：`scripts/local-run/linux/stop_local.sh`
- Linux 一键部署：`scripts/deploy/linux/compose_deploy.sh`
- Linux 停止部署：`scripts/deploy/linux/compose_stop.sh`
- Docker/Nginx 模板：`scripts/templates/docker-compose.yml`、`scripts/templates/nginx/default.conf`
- Compose 环境示例：`scripts/env/docker-compose.env.example`

## 配置与约定
- CORS：
  - 开发：`backend/src/main/resources/application-dev.yml`
  - 生产：`backend/src/main/resources/application-prod.yml`
- 静态资源：将工作目录 `uploads/` 映射为 `GET /uploads/**`；头像保存在 `uploads/avatars`
- 日志：
  - 文件滚动：`logs/app.log`（`logback-spring.xml`）
  - 请求日志：响应头 `X-Request-Id`，关键指标入库
- Profile：默认 Caffeine，本地缓存；`redis` Profile 启用分布式缓存

## API 速览
- 认证与账户
  - `POST /api/auth/register` 注册
  - `POST /api/auth/login` 登录（返回 JWT）
  - `GET /api/account/me` 当前用户资料
  - `POST /api/account/avatar` 上传头像
- 导航（公开）
  - `GET /api/navigation/categories` 根分类
  - `GET /api/navigation/categories/all` 启用分类（含一级与二级）
  - `GET /api/navigation/sites/featured?limit=10` 推荐站点
  - `GET /api/navigation/sites/popular?limit=10` 热门站点
- 管理端（`ADMIN`）
  - 分类与站点管理：`/api/navigation/admin/*`
  - 批量导入/导出：`/api/navigation/admin/categories|sites/(import|export)`

## 安全与最佳实践
- 严格区分匿名放行与受保护接口；管理端路径仅 `ADMIN` 可访问
- 生产必须配置强随机 `JWT_SECRET` 与数据库强密码
- 经 CDN/反代时真实客户端 IP 通过 `CF-Connecting-IP`、`X-Forwarded-For`、`X-Real-IP`

## 常见问题
- CORS 403：确认生产 `allowed-origins`/`allowed-origin-patterns` 与方法/头部一致
- 头像无法访问：检查 `uploads/avatars` 存在且后端静态映射生效
- 端口占用：调整 `--server.port` 或 Nginx/Compose 暴露端口
- 数据库初始化：统一执行 `backend/sql/init_schema.sql`

## 许可证
- 暂未设置许可证，如需开放源代码建议添加合适的 LICENSE

更多使用与运维细节见仓库根目录的部署文档与 `scripts/` 下的示例与脚本。
