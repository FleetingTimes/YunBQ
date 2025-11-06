# YunBQ 项目经验（Java 后端毕业生主导 + AI 工程化）

> 面向校招/社招的 Java 后端岗位，本文以“我主导并运用现代 AI 编程高效完成”的角度，精炼呈现项目介绍、架构与我负责内容，并给出可直接复制到简历的条目与可量化指标占位。

---

## 项目介绍（精炼）

- YunBQ 是一个前后端分离的内容与导航管理平台，支持账号注册/登录、邮箱验证码、头像上传、收藏/点赞、导航分类与站点管理，以及基础的审计/请求/认证/错误日志查询能力。
- 项目价值：以一致的 API 规范与自动化脚本，构建“可快速落地”的本地运行与生产部署路径，降低协作与上线成本。

---

## 项目架构（后端主导）

- 架构：浏览器 → Nginx(HTTP/HTTPS) → 前端静态资源 → 后端 `/api` → MySQL/Redis
- 技术栈：
  - 后端：`Java 17`、`Spring Boot 3.3.4`、`MyBatis-Plus`、`JWT (jjwt)`、`Redis`、`Spring Mail`
  - 前端：`Vue 3`、`Vite`、`Vue Router`、`Axios`（`import.meta.env.VITE_API_BASE` 控制 API 根路径）
  - 运维：`Nginx`、`Docker Compose`；模板与一键脚本位于 `scripts/*`
- 配置与约定：分环境 `application-dev.yml`/`application-prod.yml`；统一 `/api` 前缀与跨域策略；默认域名 `com.linaa.shiyan` 与端口 `6639`（可改）

---

## 核心功能（代码驱动，精炼版）

- 账号与认证：注册/登录、邮箱绑定与验证码、头像上传；`auth_logs` 记录登录与令牌校验行为。
- 导航与内容：`navigation_categories` 与 `navigation_sites` 的增删改查；用户侧收藏与点赞（`note_favorites`、`note_likes`）。
- 管理与审计：管理员对用户与站点的管理，以及 `audit_logs`、`request_logs`、`error_logs` 的查询与导出（导出策略建议分页与限速）。
- 运行与部署：
  - 本地一键运行（不启用开发工具）：构建后端 Jar 与前端 dist，启动服务并健康检查。
  - 一键部署（Docker Compose + Nginx）：准备 `env_file`、拷贝模板、拉起服务并探活；上传目录持久化与静态资源托管。

---

## 我负责的内容（Java 后端 + 工程化主导）

- 后端架构与安全：设计 `/api` 统一风格与跨域策略，实施 JWT 强密钥与有效期、验证码限频与过期、上传类型/体积校验；梳理 MySQL/Redis 使用场景与索引。
- API 文档与注释：统一控制器与核心配置的 Javadoc 与注释风格，确保可生成与可读；明确分页/筛选/排序约定。
- 自动化落地：
  - 本地运行脚本（Windows/Linux）：`scripts/local-run/*`，完成构建、启动与健康检查；支持 PID 管理与停止脚本。
  - 部署脚本（Windows/Linux）：`scripts/deploy/*`，拷贝 Compose/Nginx 模板、生成/使用 `env_file`、启动/停止与探活。
  - 模板与示例：`scripts/templates/docker-compose.yml`、`scripts/templates/nginx/default.conf`、`scripts/env/docker-compose.env.example`
- 文档与清单：主导 `生产上线完整清单.md`、`生产部署-Docker-Nginx.md`、`部署.md`、`本地运行-不启用开发工具.md` 的撰写与互相引用，形成端到端操作手册与 Checklist。

---

## 现代 AI 编程实践（提升交付效率）

- 用链式提示法快速产出上线清单、部署手册与脚本初稿，再结合代码与运行路径进行一致性校验与收敛（端口/域名/API 前缀/健康检查）。
- 批量补充 Javadoc 与脚本注释，统一术语与风格；在控制器/配置/脚本层做可读性与可维护性增强。
- 高效生成 Compose/Nginx 模板与跨平台脚本，并加入安全提示与错误处理策略，减少重复性劳动与新手上手成本。

---

## 影响与指标（占位，投递前填入真实数据）

- 首次完整上线时长：N 小时 → M 分钟（脚本化与模板化）
- Javadoc 覆盖率：核心控制器 100%（生成通过率稳定）
- 可用性：健康检查通过率提升、错误率下降 X%
- 性能：核心列表查询 P95/P99 下降；缓存命中率提升
- 安全：验证码拦截率/上传违规拦截率提升至 P%

---

## 简历条目（可直接复制）

- 作为 Java 后端毕业生主导 YunBQ 项目，统一 `/api` 风格与跨域策略，完善 Javadoc 与注释，建设“非开发模式本地运行 + 一键 Docker/Nginx 部署 + 健康检查”的端到端路径。
- 运用现代 AI 编程高效产出 Compose/Nginx 模板与跨平台一键脚本（Windows/Linux），实现环境变量集中管理、静态资源托管、上传目录持久化与探活重试，显著降低上线复杂度与新人上手成本。
- 设计并实施 JWT 强密钥/有效期、验证码限频与上传校验；结合 `audit_logs`/`request_logs`/`auth_logs`/`error_logs` 提升安全与可观测性；配套上线 Checklist 覆盖域名/证书、Nginx 强化、备份与告警。

> 可在每条后补充真实数据（上线时长、覆盖率、错误率、P95/P99、拦截率）增强说服力。

---

## 参考仓库路径

- 文档：`项目详细介绍.md`、`部署.md`、`生产部署-Docker-Nginx.md`、`生产上线完整清单.md`、`本地运行-不启用开发工具.md`
- 后端：`backend/`（`YunbqBackendApplication.java`、`SecurityConfig.java`、`application*.yml`、`sql/*.sql`）
- 前端：`frontend/`（`vite.config.js`、`index.html`、`src/*`）
- 脚本与模板：`scripts/local-run/*`、`scripts/deploy/*`、`scripts/templates/*`、`scripts/env/docker-compose.env.example`

如需英文版或面向特定 JD（后端/全栈/运维）的不同侧重版本，我可以基于本稿快速生成对应简历与面试材料。