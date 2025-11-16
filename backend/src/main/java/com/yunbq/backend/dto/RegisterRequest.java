package com.yunbq.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    @Pattern(regexp = "^[A-Za-z0-9](?:[A-Za-z0-9_-]{1,18}[A-Za-z0-9])?$", message = "用户名格式不合法")
    private String username;
    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$", message = "密码强度不足")
    private String password;
    private String nickname;
    @Email
    private String email;
}