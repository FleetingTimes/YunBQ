# 项目介绍 Plus（YunBQ 全面版）

本文提供面向产品、研发与运维三方的完整说明：项目架构、项目介绍、详细功能、数据模型、部署与运维、缓存策略、测试验收与路线图，帮助快速上手、稳定迭代与安全上线。

## 1. 项目介绍
- 产品定位：拾言（短内容/便签）创作与浏览 + 导航广场聚合，辅以消息中心与管理后台，构成一个轻量内容社区与站点导航融合平台。
- 主要人群：轻内容创作者、站点收藏/沉淀用户、管理员与运营。
- 关键特性：
  - 拾言：公开/私有、点赞/收藏、标签解析、分页检索。
  - 导航广场：分类树、站点卡片列表、标签过滤、精选排序。
  - 消息中心：系统/互动消息、未读计数、已读/删除管理。
  - 账户：登录注册、个人资料、头像上传、找回密码与验证码。
  - 管理后台：用户/导航/站点管理，审计与日志查询，权限控制。

## 2. 总体架构（Architecture）
- 技术栈：
  - 后端：`Spring Boot`、`MyBatis-Plus`、`JWT`、`Lombok`、`Spring Security`。
  - 前端：`Vue 3`（组合式 API）、`Element Plus`、`Vite`。
  - 数据库：`MySQL`（业务表 + 审计/请求/认证/错误日志表）。
  - 缓存：`Redis`（仅“热门/最近公开拾言”缓存，当前可禁用）。
- 分层设计：Controller（接口）→ Service（业务）→ Mapper（数据访问）→ DB。
- 安全与权限：
  - `JWT` 鉴权，`SecurityContext` 注入当前用户；
  - 管理端路径前缀 + 方法级角色校验双重保护（`hasRole('ADMIN')`）。
- 日志与审计：请求/认证/错误/审计日志记录与保留；定时清理任务。
- 静态资源：头像目录映射 `uploads/avatars` → `/uploads/**`。
- 部署：Docker Compose（可选 Nginx 反向代理）；支持本地快速运行脚本。

## 3. 后端架构与模块
- 控制器（`backend/src/main/java/.../controller`）：
  - `NoteController`（`/api/notes`）：拾言的查询/创建/更新/删除、点赞、收藏、收藏信息。
  - `NavigationController`（`/api/navigation`）：分类与站点查询、标签过滤。
  - `MessageController`（`/api/messages`）：消息列表、已读、删除、未读计数。
  - `AccountController`（`/api/account`）：个人资料、头像上传、当前用户信息。
  - `PasswordResetController`（`/api/auth`）：找回与重置密码接口。
  - `CaptchaController`（`/api/captcha`）：验证码获取与校验（前后端联动）。
  - `AdminController`（`/api/admin`）：管理员后台（用户/导航/站点/日志）。
  - `BackgroundController`（`/api/background`）：随机背景图 URL。
  - `DebugController`：开发联调辅助（生产建议关闭）。
- 服务层（`service`）：
  - `NoteService`：
    - 列表/过滤：公开/私有/归档、作者校验、合并视图；
    - 写操作：创建/更新/删除，点赞/收藏；交互成功生成消息；
    - 标签解析：从正文提取 `#tag`，去重、归一化保存。
  - `MessageService`：按类型生成/读取用户消息，维护未读计数逻辑。
  - `AccountService`：用户信息读取与更新、头像上传路径与校验。
  - `CaptchaService`：验证码生成/存储（当前进程内，建议多实例迁移 Redis）。
  - `PasswordResetService`：重置码生成/校验、邮件发送（防重与TTL建议）。
  - `NoteCacheService`：基于 `StringRedisTemplate` 的“热门/最近公开拾言”缓存（可禁用）。
- 数据访问层（`mapper`）：
  - 基于 `MyBatis-Plus` 实现分页、条件检索与排序（如站点精选/热门）。
- 任务与配置：
  - `LogRetentionScheduler`：定期清理日志表，保留期与间隔可配置。

## 4. 前端架构与模块
- 入口与根组件：
  - `src/main.js`：创建应用、挂载、注册 Element Plus、路由与样式；
  - `src/App.vue`：渲染 `router-view`，统一透明页脚。
- 布局与组件：
  - `TwoPaneLayout.vue`：顶栏吸顶 + 左右两列布局，右侧滚动容器统一；
  - `AppTopBar.vue`：全站统一顶栏（搜索、入口按钮、头像/消息入口）。
- 视图（`src/views`）：
  - `Notes.vue` + `notes/NotesBody.vue`：我的拾言编辑/管理；
  - `Square.vue` + `square/SquareBody.vue`：导航广场浏览与分类联动；
  - `Messages.vue`：消息列表与交互；
  - `Admin.vue`：管理后台（左侧菜单 + 右侧主区）；
  - `Likes.vue`：喜欢视图（弹幕墙 + 喜欢列表，分页与触底加载）；
  - `Search.vue`：搜索视图（服务端分页、移动端优化）；
  - `Login.vue`：登录页（验证码输入、统一顶栏）。
- 通用组件与工具：
  - `NavigationSiteList.vue`、`SiteNoteList.vue`：列表与分页；
  - `utils/siteNoteUtils.js`：标签过滤（`hasTag`）、打开站点、内容截断等。
- 可用性与移动端：统一骨架/空态/错误态渲染，滚动哨兵自动加载、BackTop。

## 5. 详细功能说明
### 5.1 拾言（便签）
- 核心流程：
  - 列表检索：支持 `q`（关键词）、`isPublic`、`mineOnly`、`archived`、`page/size`；
  - 创建/更新/删除：作者校验；内容解析标签保存至 `tags` 字段；
  - 点赞/收藏：公开拾言全站可互动，私有仅作者；生成消息通知作者；
  - 收藏信息：返回总数与我是否已收藏，前端用于渲染状态。
- 接口示例：
  - `GET /api/notes`、`POST /api/notes`、`PUT /api/notes/{id}`、`DELETE /api/notes/{id}`；
  - `POST /api/notes/{id}/like`、`DELETE /api/notes/{id}/like`；
  - `POST /api/notes/{id}/favorite`、`DELETE /api/notes/{id}/favorite`；
  - `GET /api/notes/{id}/favorites`。
- 缓存与一致性：若启用“热门/最近”缓存，写操作后失效相关键空间；当前可整体禁用。

### 5.2 导航广场
- 分类树：根分类与全部启用分类（含子级）加载，前端侧栏联动滚动。
- 站点：按分类分页加载、标签搜索（逗号分隔，`LIKE` 模糊），精选/热门排序。
- 接口示例：`GET /api/navigation/categories`、`GET /api/navigation/sites/category/{categoryId}`、`GET /api/navigation/sites/tags/{tags}`。

### 5.3 消息中心
- 列表分页与分组（系统/互动）；支持标记已读与删除，维护未读计数。
- 接口示例：`GET /api/messages`、`POST /api/messages/{id}/read`、`DELETE /api/messages/{id}`、`GET /api/messages/counts`。

### 5.4 账户与安全
- 登录/注册、当前用户信息、公开资料查询、头像上传（静态路径映射）。
- 验证码与找回密码：
  - 现状：进程内存储（单实例适用）；
  - 建议：多实例迁移 Redis，设置 TTL 与频控，接口表单包含 `captchaId/captchaCode`。
- 接口示例：`GET /api/account/me`、`GET /api/account/profile/{username}`、`POST /api/auth/forgot`、`POST /api/auth/reset`、`GET /api/captcha`、`POST /api/captcha/verify`。

### 5.5 管理后台
- 功能：用户管理、导航/站点管理、日志查询与导出、批量导入/导出。
- 权限：路径前缀 `/api/admin/**` + 方法级 `@PreAuthorize('hasRole('ADMIN')')`。

### 5.6 背景与辅助
- 随机背景图：`GET /api/background`（支持宽高与关键词），提升沉浸式体验。
- 调试接口：`DebugController` 用于开发联调（生产关闭避免信息泄露）。

## 6. 数据模型（摘要）
- 用户表（`users`）：`id`、`username`、`email`（唯一）、`role`（`USER/ADMIN`）、`avatar_url`、`signature`、`color` 等。
- 拾言表（`notes`）：`id`、`user_id`、`content`、`tags`、`is_public`、`archived`、交互计数（`likes`/`favorites`）与时间戳。
- 收藏表（`favorites`）：`id`、`note_id`、`user_id`、唯一组合索引避免重复收藏。
- 导航分类表（`navigation_categories`）：`id`、`name`、`parent_id`、`enabled`、排序等。
- 导航站点表（`navigation_sites`）：`id`、`category_id`、`name`、`url`、`intro`、`tags`、点击/排序权重。
- 日志表：
  - 请求日志（`request_logs`）、认证日志（`auth_logs`）、错误日志（`error_logs`）、审计日志（`audit_logs`）。
- 约束与索引：
  - 邮箱唯一（`add_unique_email.sql`）；
  - 角色字段（`add_role_column.sql`）；
  - 性能建议：为高频检索字段（`user_id/is_public/tags`）添加组合索引。

## 7. 部署与运维
- 本地运行：参考根目录 `本地运行-不启用开发工具.md`，快速启动后端/前端。
- Docker Compose：参考 `scripts/templates/docker-compose.yml` 与 `scripts/env/docker-compose.env.example`。
  - 组件：后端、前端、可选 Redis、Nginx。
  - 头像静态资源：宿主机 `uploads/avatars` 映射至容器路径。
- 环境变量：
  - 数据库：`DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD`；
  - JWT：`JWT_SECRET/JWT_EXPIRES_IN`；
  - Redis（可选）：`SPRING_DATA_REDIS_HOST/PORT`（禁用时不配置或关闭服务）。
- 日志保留与清理：`LogRetentionScheduler` 通过 `logdb.retention-sweep-interval-ms` 控制清理频率；依据业务量配置保留天数与定期归档。

## 8. 缓存策略（Redis，可选）
- 组件与键空间：`NoteCacheService` 使用 `StringRedisTemplate`，键：
  - `notes:hot:size:{size}`：热门公开拾言列表；
  - `notes:recent:size:{size}`：最近公开拾言列表。
- 失效：写操作（新增/更新/删除/点赞/收藏）后失效相关键空间。
- 当前状态：若前端已取消“热门/最近”页面且后端不再调用相关接口/失效方法，可视为已停用 Redis。
- 建议的开关化：
  - 配置 `cache.notes.enabled=false`；
  - 注入改为可选（`@Autowired(required=false)` 或 `ObjectProvider`）；
  - 分支逻辑：启用时读写缓存，禁用时走 DB 并不触发失效操作。
- 多实例一致性：如未来扩展到多实例，考虑改用 Spring Cache（`@Cacheable/@CacheEvict`）+ Redis，或在失效上使用模式匹配删除与批处理，避免 `keys` 扫描。

## 9. 安全策略
- 鉴权：`JWT`；敏感接口要求登录态；管理端严格 `ADMIN` 角色。
- 输入与校验：对正文、标签、URL 等进行长度与格式校验；避免 SQL 注入与脚本注入。
- 验证码与频控：登录/找回密码建议启用验证码与请求频率限制（多实例使用 Redis 存储与 TTL）。
- CORS 与 CSRF：前后端分离场景配置允许来源；对有状态接口谨慎评估 CSRF 风险。

## 10. 测试与验收
- 冒烟用例：
  - 拾言：创建→编辑→公开/私有切换→点赞/收藏→删除；
  - 导航：分类加载→站点分页→标签搜索→精选排序稳定性；
  - 消息：生成消息→未读计数→标记已读→删除；
  - 账户：登录→资料更新→头像上传→找回密码流程。
- 回归场景：公开/私有边界、分页与触底加载一致性、移动端滚动哨兵触发。
- 性能建议：列表页分页限制、关键词检索索引优化；热门/最近功能关闭后应减少 Redis 交互。

## 11. 路线图（Roadmap）
- 缓存开关化与配置化：实现 `cache.notes.enabled`，保持未来可恢复能力。
- 验证码/密码找回迁移 Redis：提升多实例一致性与风控能力。
- 日志与监控：引入结构化日志与聚合（ELK/Prometheus + Grafana），完善告警。
- 前端体验：长列表虚拟化、图片懒加载、错误重试策略、PWA。
- CI/CD：增加自动化测试、静态检查与构建发布流水线。

## 12. FAQ（常见问题）
- 前端取消“热门/最近”，后端是否仍操作 Redis？
  - 若仍保留失效调用（`noteCache.evictHotAll/evictRecentAll`），写操作仍会触发 Redis；建议同步移除或开关化禁用。
- 头像无法显示？
  - 检查静态映射 `/uploads/**` 与宿主机目录 `uploads/avatars` 权限与路径配置。
- 找回密码邮件未达？
  - 检查 SMTP 配置、发信限流与黑名单；记录错误日志以便定位。

## 13. 目录结构（摘要）
```
YunBQ/
├── backend/
│   ├── sql/                 # 表结构与增量 SQL
│   ├── src/main/java/com/yunbq/backend/
│   │   ├── controller/      # Note/Navigation/Account/Admin 等
│   │   ├── service/         # 业务逻辑：Note/Message/Account/Captcha/PasswordReset
│   │   ├── mapper/          # MyBatis-Plus Mapper
│   │   ├── job/             # LogRetentionScheduler 定时清理
│   │   └── config/          # 配置与迁移辅助
│   └── uploads/avatars/     # 头像静态目录
├── frontend/
│   ├── src/views/           # Admin/Square/Notes/Messages/Login 等视图
│   ├── src/components/      # TwoPaneLayout/AppTopBar/列表组件等
│   ├── src/utils/           # 站点便签工具、认证辅助
│   └── src/main.js          # 应用入口
├── scripts/                 # 部署模板（Docker/Nginx）、本地运行脚本
└── 项目介绍plus.md           # 本文档（全面版说明）
```

---

如需“开关化禁用 Redis”或“迁移验证码/找回密码到 Redis（含详细注释）”，我可以在后端提交相应改动并完善文档，确保生产/开发切换安全与可控。