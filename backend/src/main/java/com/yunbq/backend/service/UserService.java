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

/**
 * 用户服务（UserService）
 * 职责：
 * - 管理用户注册、登录与密码重置的核心流程；
 * - 在注册时做唯一性校验与密码哈希，设置默认角色与时间戳；
 * - 在登录时校验凭据并签发 JWT，统一返回基本身份信息；
 * - 在密码重置时通过邮箱定位用户并更新加密后的密码哈希（示例实现）。
 *
 * 安全与实现要点：
 * - 密码哈希：依赖 {@link PasswordEncoder} 的安全哈希与校验，禁止明文比较；
 * - 令牌签发：使用 {@link JwtUtil} 生成基于用户 ID/用户名/角色的 JWT；
 * - 异常策略：注册/登录失败抛出运行时异常，由控制层统一转换为合适的 HTTP 状态与消息；
 * - 隐私保护：登录失败不暴露用户是否存在的具体信息，降低被枚举风险。
 */
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

    /**
     * 注册用户
     * 行为：
     * - 进行用户名唯一性检查；
     * - 对密码进行哈希（如 BCrypt）；
     * - 设置默认角色为 `USER` 并填充创建时间；
     * - 持久化到数据库并返回创建后的实体。
     *
     * 参数：
     * - req：注册请求体（username/password/nickname/email）。
     *
     * 返回：
     * - 创建后的 {@link User} 实体。
     *
     * 异常：
     * - RuntimeException：当用户名已存在时抛出。
     */
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

    /**
     * 用户登录
     * 行为：
     * - 按用户名查询用户并校验密码哈希；
     * - 通过 {@link JwtUtil} 签发 JWT；
     * - 返回包含 token 与用户基本信息的响应体。
     *
     * 参数：
     * - req：登录请求（用户名、密码；可选验证码不在此处强制校验）。
     *
     * 返回：
     * - {@link AuthResponse}：包含 `token`、`userId`、`username`、`nickname`、`role`。
     *
     * 异常与安全：
     * - RuntimeException：用户不存在或密码错误时抛出，控制层转换为 400/401；
     * - 使用 `PasswordEncoder.matches` 做密码校验，避免明文比较；
     * - 可结合请求/认证日志做限流与锁定保护（此处为基础实现）。
     */
    public AuthResponse login(AuthRequest req) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }

    /**
     * 邮箱登录
     * 行为：
     * - 按邮箱查询用户并校验密码哈希；
     * - 通过 {@link JwtUtil} 签发 JWT；
     * - 返回包含 token 与用户基本信息的响应体。
     *
     * 参数：
     * - req：邮箱登录请求（email、password；验证码不在此处强制校验）。
     *
     * 返回：
     * - {@link AuthResponse}：包含 token、userId、username、nickname、role。
     *
     * 异常与安全：
     * - RuntimeException：用户不存在或密码错误时抛出；
     * - 使用 PasswordEncoder.matches 做密码校验，避免明文比较；
     */
    public AuthResponse loginByEmail(com.yunbq.backend.dto.EmailAuthRequest req) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", req.getEmail()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("邮箱或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getNickname(), user.getRole());
    }


    /**
     * 通过邮箱重置密码（示例实现）
     * 行为：
     * - 按邮箱查找用户并将新密码进行哈希后更新到数据库；
     * - 不返回具体失败原因，避免被枚举用户信息。
     *
     * 参数：
     * - email：邮箱地址；
     * - newPassword：新的明文密码（将哈希后入库）。
     *
     * 返回：
     * - 是否更新成功（true/false）。
     */
    public boolean resetPasswordByEmail(String email, String newPassword) {
        if (email == null || email.isBlank()) return false;
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (user == null) return false;
        String hash = passwordEncoder.encode(newPassword);
        userMapper.update(null, new UpdateWrapper<User>().eq("id", user.getId()).set("password_hash", hash));
        return true;
    }

    /**
     * 判断邮箱是否存在于用户表中。
     * 用途：找回密码发送验证码前进行存在性检查，可在控制器层结合“统一提示”策略避免枚举风险。
     * 参数：email 邮箱地址
     * 返回：存在返回 true；不存在或为空返回 false
     */
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) return false;
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        return user != null;
    }
}