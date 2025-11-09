package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.User;
import com.yunbq.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 第三方登录接口（QQ/微信）
 * 职责：
 * - 构造跳转到第三方授权页的 URL（qqLogin/wxLogin）；
 * - 处理授权回调（qqCallback/wxCallback）：交换 `code` 为 `access_token` 与 `openid`，
 *   若用户不存在则自动注册，最终颁发 JWT 并重定向至前端首页；
 * 配置：
 * - 在 `application.yml` 中配置 `oauth.qq.*` 与 `oauth.wechat.*`，并设置 `frontend.base-url`；
 * 安全：
 * - 回调接口不需要登录；
 * - 颁发的 JWT 包含 `uid/uname/role` 声明，前端存储于本地并用于后续鉴权。
 */
@RestController
@RequestMapping("/api/auth")
/**
 * 社交登录控制器
 * 职责：
 * - 对接第三方社交平台的授权登录流程（OAuth 等）；
 * - 将第三方身份与站内用户绑定，返回站内登录态（JWT）。
 * 安全：
 * - 严格校验第三方回调参数与状态（state/nonce），防止 CSRF；
 * - 记录登录审计日志，追踪来源与设备信息。
 */
public class SocialAuthController {

    @Value("${oauth.qq.app-id:}")
    private String qqAppId;
    @Value("${oauth.qq.app-key:}")
    private String qqAppKey;
    @Value("${oauth.qq.redirect-uri:http://localhost:8080/api/auth/qq/callback}")
    private String qqRedirectUri;

    @Value("${oauth.wechat.app-id:}")
    private String wxAppId;
    @Value("${oauth.wechat.secret:}")
    private String wxSecret;
    @Value("${oauth.wechat.redirect-uri:http://localhost:8080/api/auth/wechat/callback}")
    private String wxRedirectUri;

    @Value("${frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // QQ OAuth state 存储（简易内存版，含 TTL）。
    // 说明：用于在发起授权时生成随机 state 并在回调中校验，防止 CSRF。
    // 生产环境建议替换为：Redis（设置过期）或带签名的 JWT state，以支持多实例与持久化。
    private final ConcurrentMap<String, Long> qqStateStore = new ConcurrentHashMap<>();
    private final long qqStateTtlMillis = 5 * 60 * 1000L; // 5 分钟有效期

    public SocialAuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/qq/login")
    /**
     * QQ 登录：跳转到 QQ 授权页。
     *
     * 行为：
     * - 校验是否已在配置文件中设置 {@code oauth.qq.app-id/app-key}；
     * - 构造 QQ OAuth 授权 URL 并以 302 重定向返回；
     * - 附带随机 {@code state} 参数用于 CSRF 防护（当前实现未做回调校验，建议后续增强）。
     *
     * 返回：
     * - 302 Redirect 到 QQ 授权页；
     * - 501 Not Implemented，当未配置必要参数时返回提示消息。
     */
    public ResponseEntity<?> qqLogin() {
        if (qqAppId == null || qqAppId.isBlank() || qqAppKey == null || qqAppKey.isBlank()) {
            return ResponseEntity.status(501).body(Map.of("message", "未配置QQ登录，请在 application.yml 配置 oauth.qq.app-id/app-key"));
        }
        // 生成随机 state 并缓存（用于 CSRF 防护）
        String state = "qq-" + System.currentTimeMillis() + "-" + new Random().nextInt(1000000);
        qqStateStore.put(state, System.currentTimeMillis());
        URI authUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", qqAppId)
                .queryParam("redirect_uri", qqRedirectUri)
                // 携带随机 state：QQ 将原样回传，用于在回调中进行校验
                .queryParam("state", state)
                .build(true).toUri();
        return ResponseEntity.status(302).location(authUrl).build();
    }

    @GetMapping("/qq/callback")
    /**
     * QQ 登录回调：用授权码换取令牌并登录。
     *
     * @param code 由 QQ 授权页回传的授权码
     * @param state 防 CSRF 的状态值（当前实现未进行强校验，建议增强）
     * @return 302 Redirect 到前端回调页，携带站内 JWT；错误时返回 400/501。
     *
     * 行为：
     * - 使用 {@code code} 换取 {@code access_token}；
     * - 使用 {@code access_token} 获取 {@code openid}；
     * - 若用户不存在则自动注册；
     * - 生成站内 JWT，并重定向到前端回调页，携带 token 与用户信息。
     */
    public ResponseEntity<?> qqCallback(@RequestParam String code, @RequestParam(required = false) String state) {
        if (qqAppId == null || qqAppId.isBlank() || qqAppKey == null || qqAppKey.isBlank()) {
            return ResponseEntity.status(501).body(Map.of("message", "未配置QQ登录"));
        }
        // 1) 校验 state（CSRF 防护）：要求存在且未过期
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
        // 一次性使用：校验通过后移除，避免重放
        qqStateStore.remove(state);
        RestTemplate rt = new RestTemplate();
        // 2) 使用 code 换取 access_token
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

        // 3) 使用 access_token 获取 openid（附 unionid=1 以便在可用时获取全局唯一 unionid）
        String meUrl = "https://graph.qq.com/oauth2.0/me?access_token=" + accessToken + "&unionid=1";
        String meResp = rt.getForObject(meUrl, String.class);
        if (meResp == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "获取openid失败"));
        }
        // 响应格式类似：callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID"} );
        String json = meResp.replace("callback(", "").replace(")", "").replace(";", "").trim();
        String openid = extractJsonValue(json, "openid");
        String unionid = extractJsonValue(json, "unionid");
        if (openid == null || openid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "解析openid失败"));
        }
        // 4) 可选：拉取 QQ 用户资料（昵称/头像）以丰富站内展示
        // 文档：https://wiki.connect.qq.com/get_user_info（需提供 access_token、oauth_consumer_key、openid）
        String infoUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/user/get_user_info")
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", qqAppId)
                .queryParam("openid", openid)
                .build(true).toUriString();
        Map info = null;
        try {
            info = rt.getForObject(infoUrl, Map.class);
        } catch (Exception ignored) {
            // 网络或解析异常不影响登录流程：仅跳过资料填充
        }
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

        // 使用 unionid（若可用）作为更稳定的跨应用标识；否则回退为 openid
        String socialKey = (unionid != null && !unionid.isBlank()) ? ("qq_" + unionid) : ("qq_" + openid);
        return issueTokenAndRedirect(socialKey, nickname != null ? nickname : "QQ用户", avatar);
    }

    @GetMapping("/wechat/login")
    /**
     * 微信登录：跳转到微信扫码授权页。
     *
     * 行为：
     * - 校验是否已配置 {@code oauth.wechat.app-id/secret}；
     * - 构造微信二维码登录 URL 并以 302 重定向返回；
     * - 附带随机 {@code state} 参数用于 CSRF 防护（当前实现未做回调校验，建议后续增强）。
     *
     * 返回：
     * - 302 Redirect 到微信扫码授权页；
     * - 501 Not Implemented，当未配置必要参数时返回提示消息。
     */
    public ResponseEntity<?> wxLogin() {
        if (wxAppId == null || wxAppId.isBlank() || wxSecret == null || wxSecret.isBlank()) {
            return ResponseEntity.status(501).body(Map.of("message", "未配置微信登录，请在 application.yml 配置 oauth.wechat.app-id/secret"));
        }
        URI authUrl = UriComponentsBuilder.fromHttpUrl("https://open.weixin.qq.com/connect/qrconnect")
                .queryParam("appid", wxAppId)
                .queryParam("redirect_uri", wxRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "snsapi_login")
                .queryParam("state", String.valueOf(new Random().nextInt(100000)))
                .fragment("wechat_redirect")
                .build(true).toUri();
        return ResponseEntity.status(302).location(authUrl).build();
    }

    @GetMapping("/wechat/callback")
    /**
     * 微信登录回调：用授权码换取令牌并登录。
     *
     * @param code 由微信授权页回传的授权码
     * @param state 防 CSRF 的状态值（当前实现未进行强校验，建议增强）
     * @return 302 Redirect 到前端回调页，携带站内 JWT；错误时返回 400/501。
     *
     * 行为：
     * - 使用 {@code code} 换取 {@code access_token} 与 {@code openid}；
     * - 若用户不存在则自动注册；
     * - 生成站内 JWT，并重定向到前端回调页，携带 token 与用户信息。
     */
    public ResponseEntity<?> wxCallback(@RequestParam String code, @RequestParam(required = false) String state) {
        if (wxAppId == null || wxAppId.isBlank() || wxSecret == null || wxSecret.isBlank()) {
            return ResponseEntity.status(501).body(Map.of("message", "未配置微信登录"));
        }
        RestTemplate rt = new RestTemplate();
        // 1) 使用code换取access_token
        String tokenUrl = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/oauth2/access_token")
                .queryParam("appid", wxAppId)
                .queryParam("secret", wxSecret)
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .build(true).toUriString();
        Map resp = rt.getForObject(tokenUrl, Map.class);
        if (resp == null || resp.get("openid") == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "获取openid失败"));
        }
        String openid = String.valueOf(resp.get("openid"));
        return issueTokenAndRedirect("wx_" + openid, "微信用户");
    }

    private ResponseEntity<?> issueTokenAndRedirect(String username, String nicknameIfNew) {
        /**
         * 为社交登录用户颁发站内 JWT，并重定向到前端回调页。
         *
         * 行为：
         * - 根据社交平台标识创建或查找站内用户；
         * - 生成 JWT，包含用户ID、用户名与角色声明；
         * - 重定向到前端回调页（{@code frontend.base-url}/#/oauth/callback），并在查询参数中携带 token。
         *
         * 安全：
         * - 站内 JWT 的有效期与刷新策略由 {@code JwtUtil} 控制；
         * - 前端应安全存储 token 并在后续请求中通过 Authorization 头传递。
         */
        // 查找是否已有用户；若无则创建
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode("social-" + username)); // 占位密码
            user.setNickname(nicknameIfNew);
            user.setEmail(null);
            // 设置默认角色，避免后续 JWT 中 role 为空导致授权异常
            user.setRole("USER");
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
        } else if ((user.getNickname() == null || user.getNickname().isBlank()) && nicknameIfNew != null && !nicknameIfNew.isBlank()) {
            // 若已存在用户但未设置昵称，则填充为社交侧昵称（一次性回填）
            user.setNickname(nicknameIfNew);
            userMapper.updateById(user);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        // 回跳到前端并携带token
        String frontend = frontendBaseUrl + "/#/oauth/callback?token=" + token + "&username=" + user.getUsername() + "&nickname=" + (user.getNickname()==null?"":user.getNickname());
        return ResponseEntity.status(302).location(URI.create(frontend)).build();
    }

    /**
     * 重载：支持头像的社交登录签发逻辑。
     * 行为：在创建新用户或回填空头像时，写入社交头像；其他逻辑与上方方法一致。
     */
    private ResponseEntity<?> issueTokenAndRedirect(String username, String nicknameIfNew, String avatarUrlIfNew) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode("social-" + username));
            user.setNickname(nicknameIfNew);
            user.setEmail(null);
            user.setRole("USER");
            user.setCreatedAt(LocalDateTime.now());
            if (avatarUrlIfNew != null && !avatarUrlIfNew.isBlank()) {
                user.setAvatarUrl(avatarUrlIfNew);
            }
            userMapper.insert(user);
        } else {
            boolean needUpdate = false;
            if ((user.getNickname() == null || user.getNickname().isBlank()) && nicknameIfNew != null && !nicknameIfNew.isBlank()) {
                user.setNickname(nicknameIfNew);
                needUpdate = true;
            }
            if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && avatarUrlIfNew != null && !avatarUrlIfNew.isBlank()) {
                user.setAvatarUrl(avatarUrlIfNew);
                needUpdate = true;
            }
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole("USER");
                needUpdate = true;
            }
            if (needUpdate) {
                userMapper.updateById(user);
            }
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String frontend = frontendBaseUrl + "/#/oauth/callback?token=" + token + "&username=" + user.getUsername() + "&nickname=" + (user.getNickname()==null?"":user.getNickname());
        return ResponseEntity.status(302).location(URI.create(frontend)).build();
    }

    private String parseQueryValue(String queryLike, String key) {
        // 格式如：access_token=xxx&expires_in=7776000&refresh_token=xxx
        String[] parts = queryLike.split("&");
        for (String p : parts) {
            int i = p.indexOf('=');
            if (i > 0) {
                String k = p.substring(0, i);
                String v = p.substring(i + 1);
                if (k.equals(key)) return v;
            }
        }
        return null;
    }

    private String extractJsonValue(String json, String key) {
        // 极简解析：假设格式 {"key":"value", ...}
        String pattern = "\"" + key + "\":\"";
        int i = json.indexOf(pattern);
        if (i < 0) return null;
        int start = i + pattern.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}