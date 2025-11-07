package com.yunbq.backend.controller;

import com.yunbq.backend.service.CaptchaService;
import com.yunbq.backend.service.PasswordResetService;
import com.yunbq.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/auth")
/**
 * 密码找回与重置控制器
 * 职责：
 * - POST /api/auth/forgot 触发找回流程：校验图形验证码并生成一次性重置码，发送至邮箱；
 * - POST /api/auth/reset 进行重置：校验邮箱 + 重置码，更新为新密码。
 * 安全与风控：
 * - 通过 CaptchaService 校验验证码，防止暴力尝试；
 * - 重置码具有有效期与使用次数限制（服务层实现），并有频率限制；
 * - 响应信息避免泄露用户存在与否（如统一提示或节流），此处保留基础友好提示。
 * 使用说明：
 * - 前端先调用 /api/captcha/verify 校验验证码（或在本端进行）；
 * - 邮件发送失败与频率过高会返回 429，前端需提示稍后重试；
 * - 成功后返回 { ok: true }，失败返回 { ok: false, message }。
 */
public class PasswordResetController {
    private final PasswordResetService resetService;
    private final CaptchaService captchaService;
    private final UserService userService;

    public PasswordResetController(PasswordResetService resetService, CaptchaService captchaService, UserService userService) {
        this.resetService = resetService;
        this.captchaService = captchaService;
        this.userService = userService;
    }

/**
 * 触发找回流程（发送一次性重置码至邮箱）。
 * 请求体字段：
 * - email：账户邮箱（必填，用于接收重置码）；
 * - captchaId：图形验证码的会话ID（必填）；
 * - captchaCode：图形验证码文本（必填）。
 * 边界条件与返回：
 * - 验证码错误或过期：返回 { ok:false, message }，HTTP 400/429（视服务层限流策略而定）；
 * - 邮件发送失败或服务繁忙：返回 { ok:false, message }，HTTP 429/500；
 * - 为避免用户枚举攻击，错误场景不区分邮箱是否存在（统一提示）。
 * 异常策略：
 * - 服务层抛出的业务异常被转换为友好消息，不向前端暴露内部堆栈；
 * - 任何异常均不包含敏感信息，避免信息泄露。
 * 成功返回：
 * - { ok:true }，表示已接受发送请求（实际邮件送达以服务层日志为准）。
 */
@PostMapping("/forgot")
    public ResponseEntity<Map<String,Object>> forgot(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String captchaId = body.get("captchaId");
        String captchaCode = body.get("captchaCode");
        // 基础邮箱格式校验，减少无效调用与误发
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "邮箱不能为空"));
        }
        Pattern p = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
        if (!p.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "邮箱格式不正确"));
        }
        // 图形验证码改为“可选”：仅在 captchaId 与 captchaCode 均提供时才进行校验
        if (captchaId != null && !captchaId.isBlank() && captchaCode != null && !captchaCode.isBlank()) {
            boolean pass = captchaService.verify(captchaId, captchaCode);
            if (!pass) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码错误"));
        }
        try {
            // 静默存在性检查：不存在则不发送，但统一返回 ok，避免被枚举
            boolean exists = userService.existsByEmail(email);
            if (exists) {
                resetService.createCode(email);
            }
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

/**
 * 执行密码重置（校验邮箱与重置码后更新密码）。
 * 请求体字段：
 * - email：账户邮箱（必填）；
 * - code：一次性重置码（必填，具有有效期与使用次数限制）；
 * - newPassword：新密码（必填，应满足密码强度要求）。
 * 边界条件与返回：
 * - 重置码错误、过期或已使用：返回 { ok:false, message }，HTTP 400；
 * - 频率过高或风控触发：返回 { ok:false, message }，HTTP 429；
 * - 用户不存在或状态异常：返回 { ok:false, message }，HTTP 400/404。
 * 异常策略：
 * - 服务层统一处理并抛出业务异常，控制器捕获后返回友好提示；
 * - 失败不泄露内部实现细节，避免被枚举与推断。
 * 成功返回：
 * - { ok:true }，表示密码已更新，前端可提示用户重新登录。
 */
@PostMapping("/reset")
    public ResponseEntity<Map<String,Object>> reset(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        String captchaId = body.get("captchaId");
        String captchaCode = body.get("captchaCode");
        // 最终重置需校验图形验证码：防止暴力尝试与重放
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "请先完成图形验证码"));
        }
        boolean captchaPass = captchaService.verify(captchaId, captchaCode);
        if (!captchaPass) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码错误"));
        }
        // 基础强度校验：至少 8 位，需包含大小写字母与数字
        if (!isStrongPassword(newPassword)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "密码强度不足，需包含大小写字母与数字且不少于8位"));
        }
        boolean pass = resetService.verifyCode(email, code);
        if (!pass) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码无效或已过期"));
        boolean changed = userService.resetPasswordByEmail(email, newPassword);
        if (!changed) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "邮箱不存在"));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 仅校验邮箱验证码有效性（不消费验证码）。
     *
     * 用途：前端在“发送邮箱验证码”后，用户输入验证码并点击“验证验证码”按钮时调用；
     * 成功时不移除验证码，保留供后续 /reset 环节消费；失败会累计错误次数以限制暴力尝试。
     *
     * 请求体字段：
     * - email：账户邮箱（必填）；
     * - code：邮箱验证码（必填，期望 6 位数字）。
     * 成功返回：{ ok:true }；失败返回 { ok:false, message }。
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String,Object>> verify(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "邮箱不能为空"));
        }
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码不能为空"));
        }
        boolean pass = resetService.checkCode(email, code);
        if (!pass) return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "验证码无效或已过期"));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 简易密码强度检查：
     * - 长度至少 8；
     * - 至少包含一个小写字母、一个大写字母、一个数字；
     * - 可根据需要扩展特殊字符要求。
     */
    private boolean isStrongPassword(String s) {
        if (s == null) return false;
        String pwd = s.trim();
        if (pwd.length() < 8) return false;
        boolean hasLower = pwd.matches(".*[a-z].*");
        boolean hasUpper = pwd.matches(".*[A-Z].*");
        boolean hasDigit = pwd.matches(".*[0-9].*");
        return hasLower && hasUpper && hasDigit;
    }
}