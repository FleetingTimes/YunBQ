# 拾言（YunBQ）后端 API 使用说明

## 快速启动
- 构建与运行：
  ```bash
  cd backend
  mvn -q -DskipTests package
  java -jar target/backend-0.0.1-SNAPSHOT.jar \
    --spring.profiles.active=dev \
    --server.port=6639
  ```
- 自动建表：首次部署可设置 `spring.sql.init.mode=always`，建表完成后改为 `never`（脚本见 `backend/src/main/resources/schema.sql`）。
- 静态资源：工作目录 `uploads/` 映射为 `GET /uploads/**`，头像位于 `uploads/avatars`。

## 环境配置
- 数据库：`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
- 缓存：本地 `Caffeine`（默认），可选 `Redis`（设置 `spring.profiles.active=redis`）
- JWT：`jwt.secret`、`jwt.issuer`、`jwt.expire-minutes`
- CORS：`application-dev.yml` 与 `application-prod.yml` 分别配置本地/生产跨域

## 模块与端点

### 认证与账户
- 公共端点：
  - `POST /api/auth/register` 注册
  - `POST /api/auth/login` 用户名登录
  - `POST /api/auth/login/email` 邮箱登录
- 账户端点：
  - `GET /api/account/me` 获取当前登录用户资料
  - `POST /api/account/avatar` 上传头像（返回新的 `avatarUrl`）
  - `POST /api/account/bind-email`、`/bind-email/send-code`、`/bind-email/confirm` 邮箱绑定流程
  - `POST /api/account/update-nickname`、`/update-signature` 更新昵称/签名

### 拾言（Shiyan）
- 列表查询（匿名可访问）：
  - `GET /api/notes` 与别名 `GET /api/shiyan`，支持分页与筛选（模型字段：`content/tags/color/archived/isPublic` 等）
- 点赞/收藏：
  - `POST /api/notes/{id}/like`、`POST /api/notes/{id}/unlike`
  - `POST /api/notes/{id}/favorite`、`POST /api/notes/{id}/unfavorite`
- 创建/管理：
  - 登录用户可创建与管理自己的拾言（需携带 `Authorization: Bearer <JWT>`）

### 导航系统
- 公开端点：
  - `GET /api/navigation/categories`、`GET /api/navigation/categories/all`
  - `GET /api/navigation/sites/featured?limit=10`、`GET /api/navigation/sites/popular?limit=10`
  - `GET /api/navigation/sites/category/{categoryId}`、`GET /api/navigation/sites/search`、`GET /api/navigation/sites/tags/{tags}`
- 管理端（`ADMIN`）：
  - 分类：`GET/POST/PUT/DELETE /api/navigation/admin/categories`、`PATCH /admin/categories/{id}/toggle`、`PUT /admin/categories/order`
  - 站点：`GET/POST/PUT/DELETE /api/navigation/admin/sites`、`PATCH /admin/sites/{id}/toggle`、`PATCH /admin/sites/{id}/featured`、`PUT /admin/sites/order`
  - 导入导出：`/api/navigation/admin/categories|sites/(import|export)`

## 健康与安全
- 健康检查：`GET /actuator/health`、`GET /healthz`
- 日志观测：响应头包含 `X-Request-Id`；入站/出站请求指标持久化到 `request_logs`
- 角色与放行：匿名放行公开查询与静态资源；管理端路径仅 `ADMIN` 可访问

## 日志与审计
- 表结构：
  - `audit_logs` 业务审计
  - `request_logs` 请求指标（含 `request_id` 串联）
  - `auth_logs` 认证事件（含 `request_id`）
  - `error_logs` 未处理异常（含 `request_id`）
- 写入路径：
  - 请求日志 `RequestLoggingFilter`、认证 `JwtAuthenticationFilter`、异常 `GlobalExceptionHandler`、业务审计 `LogService`

## 开发提示
- 令牌传递：使用 `Authorization: Bearer <JWT>`
- JSON 时间：统一 `LocalDateTime`，前后端约定 ISO 格式
- 防跨域：CORS 预检需放行 `OPTIONS`，方法列表覆盖 `GET/POST/PUT/DELETE/PATCH`
