# 我的前端项目架构

## 项目框架清单
- Vue 3（Composition API）
- Vue Router（Hash 路由）
- Vite（@vitejs/plugin-vue）
- Element Plus（UI 组件库）
- Axios（HTTP 客户端）
- Iconify（SVG 图标资源）


# 前端项目技术栈与框架总览
## 概览
- 核心框架：Vue 3（Composition API）
- 路由：Vue Router（Hash 路由）
- UI 组件库：Element Plus
- 图标：Iconify（CDN SVG）
- 自研基础组件：TwoPaneLayout、AppTopBar、TransparentFooter、DanmuWall、NavigationSiteList、SideNav



## 框架区别与用途

### Vue（应用框架）
- 定位：用于构建前端应用的渐进式框架，负责组件化、响应式数据与视图渲染。
- 用途：
  - 组件化开发（SFC 单文件组件，`template/script/style`）。
  - Composition API 组织业务逻辑（`ref/computed/onMounted`）。
  - 路由导航与视图切换（与 Vue Router 配合）。
- 项目中的使用：
  - 页面与组件几乎全部基于 Vue 3 编写（如 `ShiyanTown.vue`、`Favorites.vue`、`TwoPaneLayout.vue`）。
  - 弹幕墙、站点列表等交互组件均通过响应式状态驱动 UI。

### Vite（开发与构建工具）
- 定位：现代前端开发工具，提供极速 Dev Server 与构建打包能力。
- 用途：
  - 本地开发：按原生 ES 模块提供极速热更新（HMR）。
  - 构建产物：打包优化、别名解析（`@` 指向 `src`）。
  - Dev Server 配置：外网/隧道访问、HMR WebSocket 连接等。
- 项目中的使用：
  - `vite.config.js` 配置了 `@vitejs/plugin-vue`、`resolve.alias`、以及 `server.allowedHosts/host/origin/hmr` 保证在 Cloudflare Tunnel 场景下正常 HMR 与访问。

### Element Plus（UI 组件库）
- 定位：基于 Vue 3 的企业级 UI 组件库，提供丰富的交互控件与样式体系。
- 用途：
  - 表单与基础控件：`ElInput`、`ElSelect`、`ElButton`。
  - 反馈与弹窗：`ElMessage`、`ElMessageBox`、`ElEmpty`、`ElBacktop`。
  - 布局与展示：`ElTimeline`、`ElTag` 等。
- 项目中的使用：
  - 顶栏搜索、列表空态、交互反馈、消息页批量操作等均大量使用。
  - 通过 `:deep(...)` 局部定制子组件内部样式以适配视觉需求。

### 三者关系与差异
- Vue：负责“应用层”，决定你如何写页面与组件、如何管理状态与交互。
- Vite：负责“工程层”，决定开发/构建/热更新与模块解析；不参与页面 UI。
- Element Plus：负责“UI层”，提供现成的可用组件与视觉风格；不负责工程与路由。

## 框架与库
- Vue 3 Composition API
  - 常用 API：`ref`、`computed`、`onMounted`、`onUnmounted`、`defineAsyncComponent`
  - 示例：ShiyanTown 视图 `frontend/src/views/ShiyanTown.vue:212-221`
- Vue Router
  - Hash 路由：`createWebHashHistory`
  - 主要路由：`/user/:username/shiyan`、`/likes`、`/favorites`、`/shiyan` 等
  - 配置：`frontend/src/router/index.js:12-42`
- Element Plus
  - 组件：`ElButton`、`ElInput`、`ElSelect`、`ElEmpty`、`ElTimeline`、`ElMessage`、`ElMessageBox`、`ElBacktop`、`ElTag`
  - 使用示例：喜欢页 `frontend/src/views/Likes.vue:28-33, 66-86`
- Iconify
  - 通过 `https://api.iconify.design/...` 引用 SVG 图标
  - 顶栏图标示例：`frontend/src/components/AppTopBar.vue:10-11, 45-58, 62-64`

## 布局与页面骨架
- TwoPaneLayout（两栏布局）
  - 顶栏吸顶、右侧唯一滚动容器；移动端堆叠并避让顶栏高度变量 `--topbar-height`
  - 组件：`frontend/src/components/TwoPaneLayout.vue:19-37, 66-75`
  - 移动端避让顶栏：`frontend/src/components/TwoPaneLayout.vue:137-142`
- AppTopBar（顶栏）
  - 透明模式、搜索与快捷入口，吸顶由父容器提供
  - 组件：`frontend/src/components/AppTopBar.vue`
- TransparentFooter（底栏）
  - 固定底部、毛玻璃；右侧滚动容器通过 `padding-bottom` 避让
  - 组件：`frontend/src/components/TransparentFooter.vue`

## 动效与体验
- DanmuWall（弹幕墙）
  - 统一速度：`same-speed + uniform-duration`
  - 数量上限：`max-visible`
  - 防重叠：`avoidOverlap + minGapSeconds`
  - 组件：`frontend/src/components/DanmuWall.vue:1-17, 99-133`
- NavigationSiteList（站点列表）
  - 网格布局固定最多 5 列，断点逐级降列（4/3/2/1）
  - 组件：`frontend/src/components/NavigationSiteList.vue:346-355, 466-487`

## 路由与页面
- 主要视图
  - 广场页：`frontend/src/views/Square.vue`（正文在 `frontend/src/views/square/SquareBody.vue`）
  - 喜欢页：`frontend/src/views/Likes.vue`
  - 收藏页：`frontend/src/views/Favorites.vue`
  - 拾言小镇：`frontend/src/views/ShiyanTown.vue`
  - 用户拾言：`frontend/src/views/UserNotes.vue`
  - 消息中心：`frontend/src/views/Messages.vue`
- 用户拾言跳转
  - 统一使用 `router.push('/user/:username/shiyan')`
  - 参考：喜欢页 `frontend/src/views/Likes.vue:159-168`，收藏页 `frontend/src/views/Favorites.vue:148-161`

## 网络访问与认证
- HTTP 客户端（axios 风格）
  - `http.get/post/put/delete`，`params` 与 `suppress401Redirect` 支持
  - 示例：喜欢列表 `frontend/src/views/Likes.vue:200-206`，收藏列表 `frontend/src/views/Favorites.vue:212`
- 登录态工具
  - `getToken()` 读取 JWT；必要场景解析 uid 用于“我的拾言”展示
  - 示例：`frontend/src/views/ShiyanTown.vue:445-468`

## 样式与响应式
- Scoped CSS + `:deep(...)` 穿透定制子组件
- 常用断点
  - 960px：两栏改堆叠、列表降列
  - 768px：广场标题在移动端吸顶：`frontend/src/views/square/SquareBody.vue:739-746`
  - 640px：手机断点样式

## 约定与统一
- 统一接口别名：前端使用 `/shiyan`（后端等价 `/notes`）
- 字段兼容映射：`authorName/authorUsername/userId/avatarUrl` 等
- 统一用户页跳转参数：`nickname/avatar/uid`

## 参考文件位置索引
- 路由：`frontend/src/router/index.js`
- 布局：`frontend/src/components/TwoPaneLayout.vue`
- 顶栏：`frontend/src/components/AppTopBar.vue`
- 底栏：`frontend/src/components/TransparentFooter.vue`
- 弹幕：`frontend/src/components/DanmuWall.vue`
- 站点列表：`frontend/src/components/NavigationSiteList.vue`
- 视图：`frontend/src/views/*` 与 `frontend/src/views/square/*`
