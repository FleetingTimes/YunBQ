package com.yunbq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    // 可选：前端先调用 /api/captcha/verify 验证，后端不强制校验该字段
    private String captchaId;
    private String captchaCode;
}