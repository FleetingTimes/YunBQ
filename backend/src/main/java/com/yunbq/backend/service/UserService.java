package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yunbq.backend.dto.AuthRequest;
import com.yunbq.backend.dto.AuthResponse;
import com.yunbq.backend.dto.RegisterRequest;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.User;
import com.yunbq.backend.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public User register(RegisterRequest req) {
        User existing = userMapper.selectOne(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (existing != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setEmail(req.getEmail());
        user.setRole("USER");
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public AuthResponse login(AuthRequest req) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    public boolean resetPasswordByEmail(String email, String newPassword) {
        if (email == null || email.isBlank()) return false;
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (user == null) return false;
        String hash = passwordEncoder.encode(newPassword);
        userMapper.update(null, new UpdateWrapper<User>().eq("id", user.getId()).set("password_hash", hash));
        return true;
    }
}