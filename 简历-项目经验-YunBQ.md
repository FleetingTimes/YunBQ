# YunBQ 项目经验（Java 后端，工程化与可观测性主导）

> 面向校招/社招的 Java 后端岗位。本稿聚焦“可交付 + 可维护 + 可观测”三要素，凝练项目亮点、职责与量化指标，支持直接嵌入简历与面试表达。

---

## 项目概述

- 前后端分离的“拾言内容 + 导航广场”平台：支持账号注册/登录、头像上传、点赞/收藏、导航分类与站点管理、消息中心与管理后台。
- 可观测性内建：请求/认证/错误/审计日志通路完整，支持保留期与定时清理；跨链路 `X-Request-Id` 串联排障。
- 交付路径清晰：本地一键运行、Docker Compose + Nginx 一键部署、域名与证书、静态资源映射与健康检查。

---

## 架构与技术栈

- 访问链路：浏览器 → Nginx/Cloudflare → 前端静态页（Vite/Vue） → 后端 `/api`（Spring Boot） → MySQL/Redis。
- 后端：`Java 17`、`Spring Boot 3.3.x`、`Spring Security (JWT)`、`MyBatis-Plus`、`Lombok`、`Spring Mail`、`Spring Cache`（Caffeine/Redis）。
- 前端：`Vue 3`、`Vite`、`Vue Router`、`Axios`（`VITE_API_BASE` + 公网域名自动回退）。
- 运维：`Nginx`（反向代理与静态资源）、`Docker Compose`、Cloudflare Tunnel（公网联调）。

---

## 核心功能（代码对齐）

- 内容与导航：拾言（公开/私有、点赞/收藏、标签解析）、导航分类树与站点卡片（分页、标签搜索、精选/热门）。
- 账户与安全：注册/登录（JWT）、个人资料与头像上传、验证码与找回密码、角色 `ADMIN` 的方法级权限保护。
- 消息与管理：互动/系统消息中心（未读计数）、管理后台（用户/导航/站点/日志）。
- 日志与审计：`RequestLoggingFilter` 持久化请求指标；认证与错误日志统一入库；保留与定时清理可配置。
- 缓存与性能：
  - 拾言页：`NoteCacheService` 基于 `StringRedisTemplate` 的“热门/最近公开”缓存（可开关）。
  - 导航页：`Spring Cache` 支持分类与站点列表缓存；Redis 通过 `spring.profiles.active=redis` 激活，TTL 30–60s。

---

## 职责与贡献

- 设计与实现后端安全边界：JWT 强密钥与过期策略、验证码限频与过期、上传类型/体积校验、方法级 `@PreAuthorize`。
- 可观测性建设：请求/认证/错误日志统一入库；跨链路 `requestId` 贯穿；日志保留与清理任务实现。
- 实用工程化：
  - 本地一键运行（Windows/Linux）：构建后端 Jar 与前端 dist、启动与健康检查、PID 管理与停止脚本。
  - 一键部署（Docker Compose + Nginx）：模板拷贝、`env_file` 管理、探活重试、静态资源托管与持久化。
- 文档体系：撰写上线清单、部署手册与本地运行指南，形成端到端操作手册与 Checklist。

---

## AI 辅助与效能

- 借助 AI 生成部署模板与脚本初稿，结合运行路径校验端口/域名/API 前缀/健康检查，快速收敛为可执行文档与脚本。
- 批量补注 Javadoc/脚本注释与统一术语；控制器/配置/脚本层可读性与可维护性增强。

---

## 影响与指标（占位，投递前填实）

- 首次完整上线时长：N 小时 → M 分钟（脚本化与模板化）。
- Javadoc 覆盖率：核心控制器 100%，生成通过率稳定。
- 可用性与性能：错误率下降 X%，核心列表查询 P95/P99 下降，缓存命中率提升 Y%。
- 安全与风控：验证码拦截率/上传违规拦截率提升至 P%。

---

## 简历要点（可直接复制）

- 主导 YunBQ 后端架构与工程化交付，统一 `/api` 风格与跨域策略，建设“本地一键运行 + Docker/Nginx 一键部署 + 健康检查”的端到端路径。
- 实施 JWT 强密钥与过期策略、验证码限频与上传校验；搭建请求/认证/错误/审计日志通路，`X-Request-Id` 串联跨模块排障。
- 产出 Compose/Nginx 模板与跨平台一键脚本（Windows/Linux），实现环境变量集中管理、静态资源托管、持久化与探活重试，降低上线复杂度与新人上手成本。

> 可在每条后补充真实数据（上线时长、覆盖率、错误率、P95/P99、拦截率）增强说服力。

---

## 参考仓库路径

- 文档：`项目详细介绍.md`、`部署.md`、`生产部署-Docker-Nginx.md`、`生产上线完整清单.md`、`本地运行-不启用开发工具.md`
- 后端：`backend/`（`YunbqBackendApplication.java`、`SecurityConfig.java`、`application*.yml`、`sql/*.sql`）
- 前端：`frontend/`（`vite.config.js`、`index.html`、`src/*`）
- 脚本与模板：`scripts/local-run/*`、`scripts/deploy/*`、`scripts/templates/*`、`scripts/env/docker-compose.env.example`

如需英文版或针对不同 JD（后端/全栈/运维）的侧重版本，我可快速生成对应稿件与面试材料。