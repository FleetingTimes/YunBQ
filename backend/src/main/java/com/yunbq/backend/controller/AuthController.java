package com.yunbq.backend.controller;

import com.yunbq.backend.dto.AuthRequest;
import com.yunbq.backend.dto.AuthResponse;
import com.yunbq.backend.dto.RegisterRequest;
import com.yunbq.backend.model.User;
import com.yunbq.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(userService.login(req));
    }

}