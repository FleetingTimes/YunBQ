package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.User;
import com.yunbq.backend.util.AuthUtil;
import com.yunbq.backend.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UserMapper userMapper;
    private final PasswordResetService resetService;
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    public AccountController(UserMapper userMapper, PasswordResetService resetService){ this.userMapper = userMapper; this.resetService = resetService; }

    @GetMapping("/me")
    public ResponseEntity<?> me(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("/api/account/me auth={}, principal={}", auth, auth != null ? auth.getPrincipal() : null);
        Long uid = AuthUtil.currentUserId();
        log.info("/api/account/me uid={}", uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        java.util.Map<String,Object> resp = new java.util.HashMap<>();
        resp.put("id", u.getId());
        resp.put("username", u.getUsername());
        resp.put("nickname", u.getNickname());
        resp.put("email", u.getEmail());
        resp.put("avatarUrl", u.getAvatarUrl());
        return ResponseEntity.ok(resp);
    }

    // 上传头像并更新用户 avatarUrl
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","文件不能为空"));
        if (file.getSize() > 5 * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("message","文件过大，限制 5MB"));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) return ResponseEntity.badRequest().body(Map.of("message","仅支持图片文件"));
        try {
            String baseDir = System.getProperty("user.dir") + "/uploads/avatars";
            Path dir = Paths.get(baseDir);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            String filename = uid + "_" + UUID.randomUUID() + (ext == null ? "" : ext);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target);
            String url = "/uploads/avatars/" + filename; // 静态资源映射
            // 更新数据库
            userMapper.update(null, new UpdateWrapper<User>().eq("id", uid).set("avatar_url", url));
            User u = userMapper.selectById(uid);
            return ResponseEntity.ok(Map.of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "nickname", u.getNickname(),
                    "email", u.getEmail(),
                    "avatarUrl", url
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message","服务器保存头像失败"));
        }
    }

    @PostMapping("/bind-email")
    public ResponseEntity<?> bindEmail(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱不能为空"));
        }
        Pattern p = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
        if (!p.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱格式不正确"));
        }
        // 检查是否被其他用户占用
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null && !exist.getId().equals(uid)) {
            return ResponseEntity.status(409).body(Map.of("message","该邮箱已使用！"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setEmail(email);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/bind-email/send-code")
    public ResponseEntity<?> sendBindEmailCode(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱不能为空"));
        }
        Pattern p = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
        if (!p.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱格式不正确"));
        }
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null && !exist.getId().equals(uid)) {
            return ResponseEntity.status(409).body(Map.of("message","该邮箱已使用！"));
        }
        try {
            resetService.createCode(email);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/bind-email/confirm")
    public ResponseEntity<?> confirmBindEmail(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱与验证码不能为空"));
        }
        boolean pass = resetService.verifyCode(email, code);
        if (!pass) return ResponseEntity.badRequest().body(Map.of("message","验证码无效或已过期"));
        // 再次检查唯一性
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null && !exist.getId().equals(uid)) {
            return ResponseEntity.status(409).body(Map.of("message","该邮箱已使用！"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setEmail(email);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/update-nickname")
    public ResponseEntity<?> updateNickname(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String nickname = body.getOrDefault("nickname", "").trim();
        if (nickname.length() > 24) {
            return ResponseEntity.badRequest().body(Map.of("message","昵称最长 24 位"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setNickname(nickname.isEmpty() ? null : nickname);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "nickname", u.getNickname(),
                "email", u.getEmail(),
                "avatarUrl", u.getAvatarUrl()
        ));
    }

    @GetMapping("/me2")
    public ResponseEntity<?> me2(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        java.util.Map<String,Object> m = new java.util.HashMap<>();
        m.put("authNull", auth == null);
        m.put("isAuthenticated", auth != null && auth.isAuthenticated());
        m.put("principal", principal);
        m.put("principalType", principal != null ? principal.getClass().getSimpleName() : null);
        m.put("currentUserId", AuthUtil.currentUserId());
        return ResponseEntity.ok(m);
    }
}