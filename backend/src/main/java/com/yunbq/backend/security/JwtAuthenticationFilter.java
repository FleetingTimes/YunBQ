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

/**
 * JWT 认证过滤器（每次请求仅执行一次）
 * 职责与流程概述：
 * - 从请求头 `Authorization: Bearer <token>` 解析出 JWT；
 * - 调用 `JwtUtil.verify(token)` 校验签名与过期时间，并解析自定义声明（uid/uname/role）；
 * - 根据 `role` 映射为 `ROLE_ADMIN` 或 `ROLE_USER`，构建 `UsernamePasswordAuthenticationToken`，
 *   并放入 `SecurityContextHolder`，使后续控制器可通过 `AuthUtil` 获取当前用户ID；
 * - 将认证成功/失败信息写入审计日志（通过 `LogService`），并携带来自前置过滤器的 `requestId`；
 * - 若没有携带 Bearer Token，则仅记录调试日志并继续放行；
 * 过滤器顺序：在 `SecurityConfig` 中注册为位于 `UsernamePasswordAuthenticationFilter` 之前，
 * 以便在进入表单认证过滤器前先完成 JWT 认证。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final com.yunbq.backend.service.LogService logService;

    /**
     * 构造函数：注入 JWT 工具与日志服务。
     * @param jwtUtil JWT 校验与解析工具
     * @param logService 审计日志服务，用于记录认证成功/失败与请求标识
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, com.yunbq.backend.service.LogService logService) {
        this.jwtUtil = jwtUtil;
        this.logService = logService;
    }

    /**
     * 每请求一次的 JWT 认证处理。
     * 参数：
     * - request：HTTP 请求，若包含 `Authorization: Bearer <token>` 则尝试认证；
     * - response：HTTP 响应；
     * - filterChain：过滤器链，认证完成后必须继续调用。
     * 行为与边界：
     * - 无 `Authorization` 头或非 Bearer 格式：仅记录调试日志，直接继续过滤链；
     * - Token 校验失败（过期、签名不匹配、issuer 不符）：清理 `SecurityContext`，记录失败日志，然后继续过滤链；
     * - Token 校验成功：提取 uid/uname/role，映射为 `ROLE_ADMIN` 或 `ROLE_USER`，放入 `SecurityContextHolder`。
     * 异常策略：
     * - 认证失败不抛出异常中断链路，避免影响公开接口；
     * - 所有持久化日志的异常均被捕获并记录为 warn，不影响请求继续。
     * 安全注意：
     * - 仅在校验成功时设置安全上下文；失败时显式清理，防止脏数据；
     * - 结合 `SecurityConfig` 的 `permitAll`/角色策略实现最终授权判断。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 说明：JWT 认证不拦截链路，仅在成功时设置安全上下文，失败时清理上下文并继续。
        // 这样公开接口（permitAll）不会因缺失 token 而报错，受保护接口则在授权阶段返回 401/403。
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