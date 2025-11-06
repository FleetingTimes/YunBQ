package com.yunbq.backend.security;

/**
 * JWT 工具类
 * 职责与说明：
 * - 生成携带基础声明的 Token（issuer、iat、exp、uid、uname、role）；
 * - 校验签名与发行者（issuer），并返回解析后的 DecodedJWT；
 * - 使用 HMAC256 对称加密算法，密钥与发行者从配置注入（`jwt.secret`、`jwt.issuer`）；
 * - 过期时间由 `jwt.expire-minutes` 控制，单位为分钟（从签发时间起算）。
 * 使用建议：
 * - 仅在服务端创建与校验；前端不要尝试解析或信任其中声明；
 * - role 字段用于基础授权（ADMIN/USER），细粒度权限应结合服务端校验；
 * - 若部署在多服务实例场景，确保各实例的 `secret`、`issuer` 与时间同步一致。
 */

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.expire-minutes}")
    private int expireMinutes;

    /**
     * 生成 JWT Token。
     * @param userId 当前用户ID（uid 声明）
     * @param username 当前用户名（uname 声明）
     * @param role 角色（role 声明，示例：ADMIN/USER）
     * @return 已签名的 JWT 字符串（携带 iat/exp/issuer 与自定义声明）
     */
    public String generateToken(Long userId, String username, String role) {
        Algorithm alg = Algorithm.HMAC256(secret);
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireMinutes * 60L * 1000L);
        return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(now)
                .withExpiresAt(exp)
                .withClaim("uid", userId)
                .withClaim("uname", username)
                .withClaim("role", role)
                .sign(alg);
    }

    /**
     * 校验并解析 JWT Token。
     * @param token Bearer Token（不含前缀）
     * @return 解析后的 DecodedJWT，可提取声明（uid/uname/role 等）
     * @throws com.auth0.jwt.exceptions.JWTVerificationException 当签名或发行者不匹配，或 Token 过期时抛出
     */
    public DecodedJWT verify(String token) {
        Algorithm alg = Algorithm.HMAC256(secret);
        return JWT.require(alg).withIssuer(issuer).build().verify(token);
    }
}