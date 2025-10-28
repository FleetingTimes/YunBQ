package com.yunbq.backend.security;

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

    public DecodedJWT verify(String token) {
        Algorithm alg = Algorithm.HMAC256(secret);
        return JWT.require(alg).withIssuer(issuer).build().verify(token);
    }
}