package com.yunbq.backend.controller;

import com.yunbq.backend.service.CaptchaService;
import com.yunbq.backend.service.PasswordResetService;
import com.yunbq.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {
    private final PasswordResetService resetService;
    private final CaptchaService captchaService;
    private final UserService userService;

    public PasswordResetController(PasswordResetService resetService, CaptchaService captchaService, UserService userService) {
        this.resetService = resetService;
        this.captchaService = captchaService;
        this.userService = userService;
    }

    @PostMapping("/forgot")
    public ResponseEntity<Map<String,Object>> forgot(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String captchaId = body.get("captchaId");
        String captchaCode = body.get("captchaCode");
        boolean pass = captchaService.verify(captchaId, captchaCode);
        if (!pass) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码错误"));
        try {
            resetService.createCode(email);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String,Object>> reset(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        boolean pass = resetService.verifyCode(email, code);
        if (!pass) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码无效或已过期"));
        boolean changed = userService.resetPasswordByEmail(email, newPassword);
        if (!changed) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "邮箱不存在"));
        return ResponseEntity.ok(Map.of("ok", true));
    }
}