package com.yunbq.backend.controller;

import com.yunbq.backend.service.CaptchaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {
    private final CaptchaService captchaService;
    public CaptchaController(CaptchaService captchaService){ this.captchaService = captchaService; }

    @GetMapping
    /**
     * 获取图形验证码。
     *
     * 行为：
     * - 调用 {@code CaptchaService.generate()} 生成一次性验证码与对应图片的 DataURL；
     * - 返回体包含验证码标识 {@code id} 与图片内容 {@code image}（base64 DataURL）。
     *
     * 返回：
     * - 200 OK，形如：{ "id": "uuid", "image": "data:image/png;base64,..." }
     *
     * 约束与安全：
     * - 验证码通常带有有效期与一次性校验语义，具体由服务层控制；
     * - 建议前端在登录/注册/找回密码等场景中显示并在提交时带上 {@code id} 与用户输入的 {@code code}。
     */
    public ResponseEntity<Map<String,Object>> get() {
        var c = captchaService.generate();
        return ResponseEntity.ok(Map.of("id", c.id(), "image", c.dataUrl()));
    }

    @PostMapping("/verify")
    /**
     * 校验图形验证码。
     *
     * @param body 请求体，需包含：
     *             - {@code id}：验证码标识（从获取验证码接口返回）；
     *             - {@code code}：用户输入的验证码文本；
     * @return 200 OK，形如：{ "valid": true/false }
     *
     * 行为与异常：
     * - 当 {@code id/code} 缺失或过期时，返回 {@code valid=false}；
     * - 服务层可记录失败次数以防暴力破解；控制层统一返回 200，便于前端统一处理提示。
     */
    public ResponseEntity<Map<String,Object>> verify(@RequestBody Map<String,String> body) {
        boolean ok = captchaService.verify(body.get("id"), body.get("code"));
        return ResponseEntity.ok(Map.of("valid", ok));
    }
}