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

@RestController
@RequestMapping("/api/auth")
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