package com.yunbq.backend.security;

import com.yunbq.backend.util.AuthUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求级别日志过滤器（仅用于联调阶段定位问题）。
 * 作用：
 * - 在请求进入控制器之前记录关键信息（HTTP 方法、URI、查询串、用户ID等）。
 * - 在控制链路执行完成后记录响应状态码，便于判断是否在安全链路阶段被拦截（如 401/403），
 *   或者控制器执行业务后正常返回（如 200/204）。
 * 注意：
 * - 该过滤器每个请求只记录一次（继承 OncePerRequestFilter）。
 * - 仅用于开发/联调期间的观测，生产环境建议改为按需开启或使用更细粒度的审计日志。
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String method = request.getMethod();
        final String uri = request.getRequestURI();
        final String query = request.getQueryString();
        final Long uid = AuthUtil.currentUserId();

        // 进入链路前记录请求基线信息。
        log.info("[RequestLoggingFilter] Incoming request: method={}, uri={}, query={}, uid={}",
                method, uri, query, uid);

        // 放行，交由后续安全链路与控制器处理。
        filterChain.doFilter(request, response);

        // 记录响应状态码，帮助快速确认是否到达控制器（通常 2xx/4xx 来自控制器），
        // 或在安全链路阶段被拦截（如 401 未认证、403 权限不足）。
        int status = response.getStatus();
        log.info("[RequestLoggingFilter] Response: method={}, uri={}, status={}, uid={}",
                method, uri, status, uid);
    }
}