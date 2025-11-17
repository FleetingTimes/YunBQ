# Swagger / OpenAPI 完整介绍

## 概览
- Swagger（现为 OpenAPI 规范）用于以机器可读的方式描述 HTTP API。基于同一份接口规范，团队可实现：
  - 可视化文档与交互式调试
  - 自动生成客户端/服务端 SDK 代码
  - 接口契约与联调的统一来源（Single Source of Truth）
- 本项目的接口规范文件为 `backend/openapi.yaml`（OpenAPI 3.0）。

## 生态组件
- Swagger Editor
  - 在线/本地的规范编辑器，支持 YAML/JSON 的语法校验与实时预览。
  - 适合日常编写与校验规范、演示与轻调试。
- Swagger UI
  - 将 `openapi.yaml` 渲染为可交互的接口文档网站，支持 Try it out。
  - 适合部署到内部或外部供产品/测试/开发使用。
- Redoc
  - 接口文档的另一种静态渲染方案，风格偏技术文档，支持大型规范的目录导航与搜索。
- OpenAPI Generator
  - 基于规范生成 SDK 与服务端骨架代码，常用生成目标有：TypeScript Axios/Fetch、Java（Spring）、Go、Python 等。

## 规范结构（OpenAPI 3.x）
- `openapi`: 规范版本号，示例 `3.0.3`
- `info`: 文档元信息（`title/version/description`）
- `servers`: 服务器基址列表（本项目为 `/api`）
- `tags`: 功能分组（Auth/Account/Notes/Messages/Navigation）
- `paths`: 具体接口路径与方法（`get/post/put/delete/patch`）
  - 每个方法包含 `summary/parameters/requestBody/responses/security`
- `components`: 可复用的片段
  - `schemas`: 复用的对象结构（DTO/实体/分页结构）
  - `securitySchemes`: 安全方案（本项目为 `bearerAuth`/JWT）

## 编写与约定建议
- 命名与分组
  - 使用 `tags` 做模块分组，`summary` 采用业务动词（如“分页查询导航站点”）。
- 参数与请求体
  - 路径参数用 `in: path`；查询参数用 `in: query`；表单文件用 `multipart/form-data`；JSON 用 `application/json`。
- 响应与错误
  - 成功码：`200/201/204`；鉴权失败：`401/403`；冲突：`409`；校验失败：`400`。
  - 响应体尽量复用 `components/schemas`，分页返回统一 `PageResult{}` 结构。
- 鉴权
  - 定义 `bearerAuth`，在需要登录/管理员的路径上添加 `security: [{ bearerAuth: [] }]`。
- 上传/下载
  - 上传：`multipart/form-data` + `format: binary`；
  - 下载：`application/octet-stream` 或 `text/csv` 并标注 `format: binary`。

## 与本项目的结合
- 规范位置：`backend/openapi.yaml`
- 服务器基址：`servers.url: /api`（前端 axios 已统一 `/api` 前缀）
- 安全方案：`bearerAuth`（JWT），请求头 `Authorization: Bearer <token>`
- 已覆盖模块：
  - Auth：注册、登录（用户名/邮箱）
  - Account：公开资料、我的资料、上传头像、绑定邮箱（发送/确认）、更新昵称/签名
  - Notes：列表、创建、更新、删除、点赞/取消点赞、收藏/取消收藏、我的收藏、我点赞过、批量导入
  - Messages：分页列表、标记已读、删除、未读计数
  - Navigation：分类与站点的公共查询；管理员的新增/更新/删除/导入/导出/排序；用户创建站点、获取我的站点

## 使用指南
### 1. 在 Swagger Editor 预览与调试
- 打开 `https://editor.swagger.io` → Import File → 选择 `backend/openapi.yaml`
- Authorize（右上角）：输入 `Bearer <你的JWT>`
- 选择某个接口 → Try it out → 填参数并执行
- 注意：如跨域或私网不可达，在线 Editor 的请求可能失败，建议本地或 Postman 调试。

### 2. 部署 Swagger UI（静态版）
- 下载 Swagger UI 发布包，将 `openapi.yaml` 放入同目录；在 `index.html` 设置：
  - `const ui = SwaggerUIBundle({ url: '/api-docs/openapi.yaml', ... })`
- 用任意静态服务器（如 Nginx）托管该目录，即可对外提供交互式文档。

### 3. 生成客户端 SDK（示例）
- 使用 OpenAPI Generator（Node 或 JAR 任一方式）：
- TypeScript Axios（前端）：
  - `openapi-generator-cli generate -i backend/openapi.yaml -g typescript-axios -o frontend/sdk`
- Java（Spring 客户端）：
  - `openapi-generator-cli generate -i backend/openapi.yaml -g java -o backend/sdk-java`
- 常见选项：`-p useSingleRequestParameter=true`（改善方法签名）、`-p withSeparateModelsAndApi=true`。

### 4. 导入到 Postman/Apifox
- 新建集合 → 选择导入 → 选择 `backend/openapi.yaml` → 自动生成接口与示例
- 设置环境变量（如 `baseUrl`、`token`），统一鉴权与服务器地址。

## 常见问题与排错
- 401 未登录：确认在 Authorize 中设置了正确的 JWT；或在工具中为需要鉴权的请求添加 `Authorization` 头。
- 服务器基址错误：确保 `servers.url` 与部署一致；在线 Editor 默认使用规范中的服务器基址。
- CORS 跨域：在线 Editor 调试时需后端允许跨域或使用代理；建议本地或 Postman 调试。
- multipart 上传：确保 `Content-Type` 为 `multipart/form-data` 且字段名与后端匹配（如 `file`）。
- CSV 下载乱码：后端已为 CSV 添加 UTF-8 BOM；若仍有问题检查客户端解析方式。

## 最佳实践
- 规范先行：变更接口前先更新 `openapi.yaml` 并评审，保持契约一致。
- 复用 Schema：避免在 `responses` 中写匿名对象，统一走 `components/schemas`。
- 自动生成：前端/后端尽量使用 SDK，减少手写与类型偏差。
- 文档发布：将 Swagger UI 或 Redoc 文档部署到固定地址，形成团队访问入口。

---
如需我把文档渲染为可直接打开的 HTML（Swagger UI 或 Redoc），或为你的前端生成 TypeScript Axios SDK 目录结构，请告诉我目标位置与偏好。