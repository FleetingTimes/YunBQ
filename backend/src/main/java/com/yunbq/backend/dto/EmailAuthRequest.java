package com.yunbq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮箱登录请求体（EmailAuthRequest）
 * 作用：用于前端“邮箱登录”模式提交的凭据，后端按邮箱查找用户并校验密码。
 * 字段说明：
 * - email：邮箱地址，不能为空，格式需符合邮箱规范；
 * - password：明文密码，不能为空（服务层将使用 PasswordEncoder 做哈希比对）；
 * - captchaId/captchaCode：可选验证码参数；当前控制器不强制校验，前端可在调用前独立验证。
 */
@Data
public class EmailAuthRequest {
    @NotBlank
    @Email
    private String email;
    @NotBlank
    private String password;
    // 可选：前端可在调用前自行进行验证码校验；后端不强制依赖该字段
    private String captchaId;
    private String captchaCode;
}