# QQ 登录实现详解（含最新增强与完整注释）

本文档系统讲解项目中的 QQ 登录实现与近期增强改动，覆盖后端控制器、JWT 颁发与前端回跳、配置要求、联调步骤、安全与常见问题等，配套完整示例代码与详细注释，便于快速理解与维护。

---

## 一、功能概览

- 支持 QQ OAuth2.0 登录，后端完成授权码换 token、解析 openid/unionid、拉取用户资料（昵称/头像），并在站内创建或补充用户信息。
- 颁发站内 JWT，302 重定向到前端回调页 `/#/oauth/callback`，前端读取并存储 token，完成登录流程。
- 安全增强：发起登录时生成随机 `state` 并在回调校验（一次性、防重放、含 TTL）；支持 `unionid` 作为跨应用更稳定的标识；默认角色设为 `USER` 避免授权异常。
- CORS 与回跳：针对生产环境（Cloudflare Tunnel）配置 `allowed-origin-patterns`（含 HTTP/HTTPS 子域临时兼容），确保跨域与回跳一致。

---

## 二、后端实现（SocialAuthController.java）

文件：`backend/src/main/java/com/yunbq/backend/controller/SocialAuthController.java`

- 路径前缀：`/api/auth`
- 关键配置（通过 `@Value` 注入；默认值详见 `application.yml`）：
  - `oauth.qq.app-id`：QQ 应用 `App ID`
  - `oauth.qq.app-key`：QQ 应用 `App Key`
  - `oauth.qq.redirect-uri`：QQ 授权回调地址（指向本控制器 `qqCallback`）
  - `frontend.base-url`：前端站点基地址（用于回跳携带站内 `token`）

### 2.0 新增：state 防护（CSRF）与内存 TTL 存储

为防止第三方回调被 CSRF 攻击，在发起授权时生成随机 `state` 并在回调阶段进行强校验；校验通过后一次性移除。示例字段与 TTL：

```java
// QQ OAuth state 存储（简易内存版，含 TTL）。
// 说明：用于在发起授权时生成随机 state 并在回调中校验，防止 CSRF。
// 生产环境建议替换为：Redis（设置过期）或带签名的 JWT state，以支持多实例与持久化。
private final java.util.concurrent.ConcurrentMap<String, Long> qqStateStore = new java.util.concurrent.ConcurrentHashMap<>();
private final long qqStateTtlMillis = 5 * 60 * 1000L; // 5 分钟有效期
```

### 2.1 跳转到 QQ 授权页：`GET /api/auth/qq/login`

核心逻辑（节选并加注释）：

```java
@GetMapping("/qq/login")
public ResponseEntity<?> qqLogin() {
  // 1) 配置项校验：缺少 app-id/app-key 时直接返回 501
  if (qqAppId == null || qqAppId.isBlank() || qqAppKey == null || qqAppKey.isBlank()) {
    return ResponseEntity.status(501).body(Map.of("message", "未配置QQ登录，请在 application.yml 配置 oauth.qq.app-id/app-key"));
  }
  // 2) 生成随机 state 并缓存（用于 CSRF 防护，回调时校验且一次性使用）
  String state = "qq-" + System.currentTimeMillis() + "-" + new java.util.Random().nextInt(1000000);
  qqStateStore.put(state, System.currentTimeMillis());
  // 3) 构造 QQ OAuth 授权页 URL，附带随机 state
  URI authUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/authorize")
      .queryParam("response_type", "code")
      .queryParam("client_id", qqAppId)
      .queryParam("redirect_uri", qqRedirectUri)
      .queryParam("state", state)
      .build(true).toUri();
  // 4) 以 302 跳转到 QQ 授权页
  return ResponseEntity.status(302).location(authUrl).build();
}
```

### 2.2 QQ 回调处理：`GET /api/auth/qq/callback?code=...`

核心逻辑（节选并加注释）：

```java
@GetMapping("/qq/callback")
public ResponseEntity<?> qqCallback(@RequestParam String code, @RequestParam(required = false) String state) {
  // 1) 基础配置校验
  if (qqAppId == null || qqAppId.isBlank() || qqAppKey == null || qqAppKey.isBlank()) {
    return ResponseEntity.status(501).body(Map.of("message", "未配置QQ登录"));
  }
  // 2) 校验 state（CSRF 防护）：要求存在且未过期；一次性使用
  if (state == null || state.isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "缺少 state 参数，拒绝回调"));
  }
  Long issuedAt = qqStateStore.get(state);
  if (issuedAt == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "state 无效或已使用"));
  }
  if (System.currentTimeMillis() - issuedAt > qqStateTtlMillis) {
    qqStateStore.remove(state);
    return ResponseEntity.badRequest().body(Map.of("message", "state 已过期，请重新发起登录"));
  }
  qqStateStore.remove(state); // 一次性使用

  RestTemplate rt = new RestTemplate();

  // 3) 使用授权码 code 换取 access_token
  String tokenUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/token")
      .queryParam("grant_type", "authorization_code")
      .queryParam("client_id", qqAppId)
      .queryParam("client_secret", qqAppKey)
      .queryParam("code", code)
      .queryParam("redirect_uri", qqRedirectUri)
      .build(true).toUriString();
  String tokenResp = rt.getForObject(tokenUrl, String.class);
  if (tokenResp == null || !tokenResp.contains("access_token=")) {
    return ResponseEntity.badRequest().body(Map.of("message", "获取access_token失败"));
  }
  String accessToken = parseQueryValue(tokenResp, "access_token");

  // 4) 使用 access_token 获取 openid（附 unionid=1 以便在可用时获取全局唯一 unionid）
  String meUrl = "https://graph.qq.com/oauth2.0/me?access_token=" + accessToken + "&unionid=1";
  String meResp = rt.getForObject(meUrl, String.class);
  if (meResp == null) {
    return ResponseEntity.badRequest().body(Map.of("message", "获取openid失败"));
  }
  // 响应格式类似：callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID","unionid":"可选"} );
  String json = meResp.replace("callback(", "").replace(")", "").replace(";", "").trim();
  String openid = extractJsonValue(json, "openid");
  String unionid = extractJsonValue(json, "unionid");
  if (openid == null || openid.isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "解析openid失败"));
  }

  // 5) 可选：拉取 QQ 用户资料（昵称/头像）以丰富站内展示
  // 文档：https://wiki.connect.qq.com/get_user_info（需提供 access_token、oauth_consumer_key、openid）
  String infoUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/user/get_user_info")
      .queryParam("access_token", accessToken)
      .queryParam("oauth_consumer_key", qqAppId)
      .queryParam("openid", openid)
      .build(true).toUriString();
  java.util.Map info = null;
  try { info = rt.getForObject(infoUrl, java.util.Map.class); } catch (Exception ignored) {}
  String nickname = null;
  String avatar = null;
  if (info != null) {
    Object n = info.get("nickname");
    if (n != null) nickname = String.valueOf(n);
    // 优先高清头像（figureurl_qq_2），回退到普通头像（figureurl_qq_1）
    Object a2 = info.get("figureurl_qq_2");
    Object a1 = info.get("figureurl_qq_1");
    if (a2 != null && !String.valueOf(a2).isBlank()) avatar = String.valueOf(a2);
    else if (a1 != null && !String.valueOf(a1).isBlank()) avatar = String.valueOf(a1);
  }
  // 6) 使用 unionid（若可用）作为更稳定的跨应用标识；否则回退为 openid
  String socialKey = (unionid != null && !unionid.isBlank()) ? ("qq_" + unionid) : ("qq_" + openid);
  // 7) 生成站内 JWT 并重定向到前端回调页（带昵称与头像的回填逻辑）
  return issueTokenAndRedirect(socialKey, nickname != null ? nickname : "QQ用户", avatar);
}
```

### 2.3 颁发站内 JWT 并重定向前端回调页（昵称/头像写入）

```java
private ResponseEntity<?> issueTokenAndRedirect(String username, String nicknameIfNew, String avatarUrlIfNew) {
  // a) 查找或创建站内用户（社交账号以唯一 username 绑定，例如 qq_openid/qq_unionid）
  User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
  if (user == null) {
    user = new User();
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode("social-" + username)); // 占位密码
    user.setNickname(nicknameIfNew);
    user.setEmail(null);
    user.setRole("USER"); // 设置默认角色，避免 JWT 中 role 为空导致授权异常
    user.setCreatedAt(LocalDateTime.now());
    if (avatarUrlIfNew != null && !avatarUrlIfNew.isBlank()) {
      user.setAvatarUrl(avatarUrlIfNew);
    }
    userMapper.insert(user);
  } else {
    boolean needUpdate = false;
    if ((user.getNickname() == null || user.getNickname().isBlank()) && nicknameIfNew != null && !nicknameIfNew.isBlank()) {
      user.setNickname(nicknameIfNew); // 一次性回填社交昵称
      needUpdate = true;
    }
    if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && avatarUrlIfNew != null && !avatarUrlIfNew.isBlank()) {
      user.setAvatarUrl(avatarUrlIfNew); // 一次性回填社交头像
      needUpdate = true;
    }
    if (user.getRole() == null || user.getRole().isBlank()) {
      user.setRole("USER");
      needUpdate = true;
    }
    if (needUpdate) userMapper.updateById(user);
  }
  // b) 生成 JWT（包含 uid/username/role），交由前端存储
  String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
  // c) 302 重定向到前端回调页，并在查询参数中携带 token 与用户展示信息
  String frontend = frontendBaseUrl + "/#/oauth/callback?token=" + token
      + "&username=" + user.getUsername()
      + "&nickname=" + (user.getNickname()==null?"":user.getNickname());
  return ResponseEntity.status(302).location(URI.create(frontend)).build();
}
```

---

## 三、前端实现（Login.vue / OAuthCallback.vue）

- 登录入口：点击“QQ 登录”按钮，跳转到后端 `GET /api/auth/qq/login`。
- 回调处理：`OAuthCallback.vue` 读取查询参数 `token/username/nickname`，保存到前端存储（当前项目使用 localStorage），更新全局认证状态后路由跳转（例如 `/shiyan`）。
- 体验一致性：保持旧路径别名与顶部入口一致，授权成功后回到主页面或用户中心。

---

## 四、配置要求（application.yml）

文件：`backend/src/main/resources/application.yml`

```yaml
frontend:
  # 前端公网地址（Cloudflare Tunnel 域名）：用于 OAuth 回跳到前端回调页
  base-url: "https://app.shiyan.online"

oauth:
  qq:
    app-id: "你的QQ App ID"
    app-key: "你的QQ App Key"
    # QQ 授权回调地址（公网）：必须与 QQ 互联平台设置完全一致（协议/域名/路径）
    # 若通过 Cloudflare Tunnel 将外网 `https://api.shiyan.online` 映射到本机 `http://localhost:8080`
    redirect-uri: "https://api.shiyan.online/api/auth/qq/callback"

cors:
  allowed-origins:
    - "http://localhost:5500"
    - "http://localhost:5173"
    - "https://app.shiyan.online"
    - "https://api.shiyan.online"
  allowed-origin-patterns:
    - "http://localhost:*"
    - "https://*.shiyan.online"
    - "http://*.shiyan.online"  # 若前端为 HTTP 子域，需临时放行；迁移至 HTTPS 后可移除此项
  allowed-methods:
    - GET
    - HEAD
    - POST
    - PUT
    - DELETE
    - OPTIONS
    - PATCH
  allowed-headers:
    - Authorization
    - Content-Type
    - X-Requested-With
  allow-credentials: true
```

- 关键点：
  - `frontend.base-url` 必须指向实际前端域名或端口（例如 `http://localhost:5173` 或你的生产域），否则后端回跳会指向错误页面。
  - `oauth.qq.redirect-uri` 必须与 QQ 开放平台应用设置完全一致，包含协议/域名/端口/路径。
  - CORS：当 `allow-credentials=true` 时不能使用 `*`；使用 `allowed-origin-patterns` 精确匹配通配子域。
  - “HTTP 子域”仅为临时兼容：建议前端迁移到 HTTPS 并移除 `http://*.shiyan.online` 以提升安全性。

---

## 五、联调步骤（本地）

- 后端启动本地 `:8080`，确保数据库与 `application.yml` 配置可用。
- 前端开发服务器（例如 `vite`）启动在 `:5173` 或 `:5500`。
- 浏览器访问前端站点，点击“QQ 登录”，在授权成功后应重定向到前端 `/#/oauth/callback` 并看到用户已登录。
- 验证：
  - Network 面板观测后端回调的 302 与前端页面的 token 读取；
  - 打开后端控制台日志，确认 `state` 校验通过、`openid/unionid` 与用户资料拉取成功；
  - 数据库 `users` 表出现新的 `qq_*` 用户（含昵称/头像/role）。

---

## 六、安全与健壮性建议

- `state` 防 CSRF：已在后端实现 `state` 生成与强校验（含 TTL 与一次性使用），生产建议改为 Redis 或带签名的 JWT，以支持多实例与更强防护。
- Token 存储：优先选择 HttpOnly Cookie（后端设置 `Set-Cookie`）降低 XSS 风险；当前项目采用本地存储，需确保前端不注入不可信脚本。
- 错误处理：对 `access_token` 获取失败、`openid` 解析失败分别返回明确错误；可结合全局日志（认证/请求日志）进行审计与告警。
- 账号绑定：以 `qq_<openid/unionid>` 作为站内用户名；如需与已有账号合并，建议引入“绑定/解绑”流程。
- 过期与刷新：JWT 的有效期通过 `jwt.expire-minutes` 控制；可扩展为刷新令牌机制以提升长期会话体验。

补充说明：
- 用户资料拉取与写入策略：首次创建用户时写入昵称/头像；对于已存在但缺少昵称/头像的用户进行一次性回填，避免覆盖用户自行设置的资料。
- 角色默认值：为社交创建的用户默认设为 `USER`，避免后续授权流程中 `role` 为空导致异常。

---

## 七、常见问题排查

- 授权失败：检查 QQ 应用的 `App ID/App Key` 与 `redirect_uri` 是否与后端配置一致。
- 回调 400：确认 `code/state` 参数齐全；`state` 是否在 TTL 内且未被使用。
- 解析失败：`access_token` 接口响应为空或未包含 `access_token=`；`me` 接口无返回或 JSON 包装未正确去除。
- CORS 拦截：前端域名未在 `cors.allowed-origins` 列表；或端口不匹配导致预检失败。
  - 特例：若浏览器请求 `Origin` 为 `http://app.shiyan.online` 而仅放行了 `https://*.shiyan.online`，将导致响应无 `Access-Control-Allow-*` 头并被拦截。可临时加入 `http://*.shiyan.online`，建议尽快切换前端到 HTTPS。

---

## 八、生产联调与验证（公网域名）

- 域名与映射：
  - 前端：`https://app.shiyan.online`（Cloudflare Tunnel → 本地前端）
  - 后端：`https://api.shiyan.online`（Cloudflare Tunnel → 本机 `http://localhost:8080`）
  - QQ 回调地址：`https://api.shiyan.online/api/auth/qq/callback`（必须与 QQ 应用配置一致）
- 验证步骤：
  - 在浏览器中点击 QQ 登录，授权后回到后端回调 → 302 重定向到前端回调页。
  - 打开 DevTools 的 Network 面板：检查请求头 `Origin` 与响应头 `Access-Control-Allow-Origin/Allow-Credentials`。
  - 关注后端控制台的 CORS 日志字段（预检与响应头）。
- 命令行辅助（示例）：
  - `curl -I -H "Origin: https://app.shiyan.online" "https://api.shiyan.online/api/notes"`
  - 观察响应是否包含 `Access-Control-Allow-Origin: https://app.shiyan.online`。

---

## 九、前端回跳与路由统一

- 回跳地址固定为：`/#/oauth/callback?token=...&username=...&nickname=...`
- `OAuthCallback.vue` 在收到 `token` 后跳转到 `/shiyan`（保留旧地址别名），确保用户体验一致。

---

## 十、安全威胁模型与建议

- 回调重放：通过一次性 `state` 与 TTL 限制降低风险；在生产采用中心化存储或签名方案。
- Token 泄露：避免在 URL 查询参数长期存放敏感信息；可改为中转页 POST 或 HttpOnly Cookie。
- 社交标识稳定性：优先使用 `unionid`（跨应用唯一）作为站内用户名后缀；不可用时回退 `openid`。
- 代理/隧道安全：尽量统一 HTTPS；避免混用 HTTP 子域导致的链路降级与 CORS 复杂性。

---

## 十一、代码位置速览

- 后端控制器：`backend/src/main/java/com/yunbq/backend/controller/SocialAuthController.java`
- 安全配置：`backend/src/main/java/com/yunbq/backend/config/SecurityConfig.java`（`/api/auth/**` 放行、CORS 策略）
- JWT 工具：`backend/src/main/java/com/yunbq/backend/util/JwtUtil.java`（`generateToken(userId,username,role)`）
- 用户模型与服务：`backend/src/main/java/com/yunbq/backend/model/User.java`、`service/UserService.java`
- 前端回调页：`frontend/src/views/OAuthCallback.vue`

---

## 十二、变更追踪（本次完善）

- 加入 `state` 防护与 TTL 存储，并在回调中强校验与一次性移除。
- 支持 `unionid` 与 QQ 用户资料拉取（昵称、头像），并在创建或空值场景下进行一次性回填。
- 颁发 JWT 前保证 `role` 默认设为 `USER`，避免授权异常。
- 更新配置示例，补充 HTTPS/HTTP 子域通配、`X-Requested-With`、`PATCH` 方法等 CORS 细节。

---

如需将 `state` 存储替换为 Redis 或引入签名 `state`（JWT/HMAC），我可以提交相应代码改动并同步更新本文档与配置说明，确保多实例环境的安全与一致性。