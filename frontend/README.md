# 拾言（YunBQ）前端说明

基于 Vue 3 + Vite + Element Plus 的前端应用，包含拾言广场、导航系统、账户与管理员页面。

## 快速开始
- 安装依赖：`npm ci`
- 开发启动：`npm run dev`（默认端口 `5173`）
- 构建产物：`npm run build`（输出 `dist/`）
- 预览构建：`npm run preview`

## 环境变量
- `VITE_API_BASE`：后端 API 根地址（示例：`http://localhost:6639/api`）
  - 开发：`frontend/.env.development`
  - 生产：`frontend/.env.production`

## 目录结构
- `src/main.js` 应用入口
- `src/App.vue` 根组件
- `src/router/` 路由配置（`views/*` 页面）
- `src/api/` 网络请求封装（`axios` 基于 `VITE_API_BASE`）
- `src/views/` 页面视图：
  - `Notes.vue`、`Square.vue`、`UserNotes.vue` 拾言相关
  - `Admin.vue` 及 `views/admin/*` 管理页面（导航分类、站点、日志等）
  - `Login.vue`、`Register.vue`、`Forgot.vue`、`OAuthCallback.vue` 认证相关
  - `Navigation/*` 导航系统页面
- `src/components/` 复用组件（`TwoPaneLayout.vue`、`NavigationSiteList.vue`、`AppTopBar.vue` 等）
- `src/utils/` 工具（`auth.js` 令牌处理、`siteNoteUtils.js` 等）

## 与后端交互
- 认证：登录成功后保存 JWT；请求时在 `Authorization` 头携带 `Bearer <token>`（`utils/auth.js` 封装）
- 静态资源：头像等通过 `/uploads/**` 访问；上传由后端保存于 `uploads/avatars`
- 常用接口：
  - 账户：`/api/account/me`、`/api/account/avatar`、邮箱绑定与资料更新
  - 拾言：`/api/notes`（或 `/api/shiyan`）查询、点赞/收藏
  - 导航：分类/站点相关公开接口与管理员接口

## 部署
- 生产构建后将 `dist/` 交由 Nginx 托管（Compose 模板已挂载 `frontend/dist`）
- 与后端一键部署脚本配合：`scripts/deploy/windows/*` 或 `scripts/deploy/linux/*`

## 其他
- UI 框架：Element Plus
- 打包工具：Vite（ESM）
- 代码组织：`<script setup>` 单文件组件与组合式 API
