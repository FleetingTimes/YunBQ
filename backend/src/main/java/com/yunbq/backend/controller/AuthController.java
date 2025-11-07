package com.yunbq.backend.controller;

import com.yunbq.backend.dto.AuthRequest;
import com.yunbq.backend.dto.AuthResponse;
import com.yunbq.backend.dto.RegisterRequest;
import com.yunbq.backend.dto.EmailAuthRequest;
import com.yunbq.backend.model.User;
import com.yunbq.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(req);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    /**
     * 用户名登录
     * 行为与响应：
     * - 成功：200 OK，返回 {@link AuthResponse}（含 JWT 与基础用户信息）。
     * - 失败：401 Unauthorized，返回统一错误结构 { message }，不暴露用户是否存在的细节。
     * 
     * 异常处理策略：
     * - 服务层在凭据错误时抛出运行时异常；此处捕获并转换为 401，避免全局异常处理器将其变为 500。
     */
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        try {
            return ResponseEntity.ok(userService.login(req));
        } catch (RuntimeException ex) {
            // 登录失败统一返回 401，文案使用异常消息（如“用户名或密码错误”）
            String msg = ex.getMessage() != null ? ex.getMessage() : "用户名或密码错误";
            return ResponseEntity.status(401).body(Map.of("message", msg));
        }
    }

    @PostMapping("/login/email")
    /**
     * 邮箱登录接口
     * 说明：
     * - 请求体为 EmailAuthRequest（email、password）；
     * - 通过 UserService.loginByEmail 按邮箱查找用户并校验密码；
     * - 登录成功返回 JWT 与用户基础信息。
     */
    public ResponseEntity<?> loginByEmail(@Valid @RequestBody EmailAuthRequest req) {
        try {
            return ResponseEntity.ok(userService.loginByEmail(req));
        } catch (RuntimeException ex) {
            // 邮箱登录失败统一返回 401，避免被枚举具体原因
            String msg = ex.getMessage() != null ? ex.getMessage() : "邮箱或密码错误";
            return ResponseEntity.status(401).body(Map.of("message", msg));
        }
    }

}