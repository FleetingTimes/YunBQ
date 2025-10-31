package com.yunbq.backend.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final com.yunbq.backend.service.LogService logService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, com.yunbq.backend.service.LogService logService) {
        this.jwtUtil = jwtUtil;
        this.logService = logService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean bearer = header != null && header.startsWith("Bearer ");
        log.debug("JWT filter enter: uri={} headerPresent={} bearerPrefix={}", request.getRequestURI(), header != null, bearer);
        // 从前置过滤器获取 requestId，若不存在则回退为 null（数据库列允许为空）。
        String requestId = (String) request.getAttribute("requestId");
        if (bearer) {
            String token = header.substring(7);
            try {
                DecodedJWT jwt = jwtUtil.verify(token);
                Long uid = jwt.getClaim("uid").asLong();
                String uname = jwt.getClaim("uname").asString();
                String role = jwt.getClaim("role").asString();
                java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
                if ("ADMIN".equalsIgnoreCase(role)) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                } else {
                    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(uid, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT ok: uid={} uname={} role={} uri={}", uid, uname, role, request.getRequestURI());
                // 记录认证成功
                try {
                    String ip = request.getHeader("X-Forwarded-For");
                    if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
                    String ua = request.getHeader("User-Agent");
                    logService.logAuthSuccess(uid, uname, ip, ua, requestId);
                } catch (Exception ex) {
                    log.warn("persist auth success log failed: uid={} msg={}", uid, ex.getMessage());
                }
            } catch (Exception e) {
                log.warn("JWT verification failed: uri={} msg={}", request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
                // 记录认证失败
                try {
                    String ip = request.getHeader("X-Forwarded-For");
                    if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
                    String ua = request.getHeader("User-Agent");
                    logService.logAuthFailure(e.getMessage(), ip, ua, requestId);
                } catch (Exception ex) {
                    log.warn("persist auth failure log failed: msg={}", ex.getMessage());
                }
            }
        } else if (header != null) {
            log.debug("Authorization header present but not Bearer: uri={} header={}", request.getRequestURI(), header);
        } else {
            log.debug("No Authorization header: uri={}", request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }
}