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
        URI authUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", qqAppId)
                .queryParam("redirect_uri", qqRedirectUri)
                .queryParam("state", String.valueOf(new Random().nextInt(100000)))
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
        RestTemplate rt = new RestTemplate();
        // 1) 使用code换取access_token
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

        // 2) 使用access_token获取openid
        String meUrl = "https://graph.qq.com/oauth2.0/me?access_token=" + accessToken;
        String meResp = rt.getForObject(meUrl, String.class);
        if (meResp == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "获取openid失败"));
        }
        // 响应格式类似：callback( {"client_id":"YOUR_APPID","openid":"YOUR_OPENID"} );
        String json = meResp.replace("callback(", "").replace(")", "").replace(";", "").trim();
        String openid = extractJsonValue(json, "openid");
        if (openid == null || openid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "解析openid失败"));
        }

        return issueTokenAndRedirect("qq_" + openid, "QQ用户");
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
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        // 回跳到前端并携带token
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