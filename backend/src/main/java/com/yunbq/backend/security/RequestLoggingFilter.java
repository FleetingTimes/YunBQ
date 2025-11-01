package com.yunbq.backend.security;

import com.yunbq.backend.util.AuthUtil;
import org.slf4j.MDC;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    // 说明：通过构造注入日志服务，将请求的关键指标持久化到数据库。
    private final com.yunbq.backend.service.LogService logService;

    @Autowired
    public RequestLoggingFilter(com.yunbq.backend.service.LogService logService) {
        this.logService = logService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 生成本次请求的唯一标识，用于贯穿日志与返回响应头，方便跨系统排查。
        final String requestId = java.util.UUID.randomUUID().toString();
        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        // 将 requestId 放入 MDC，便于控制台日志自动携带该标识。
        MDC.put("requestId", requestId);
        final String method = request.getMethod();
        final String uri = request.getRequestURI();
        final String query = request.getQueryString();
        // 注意：此处不要过早读取用户ID，避免在 JWT 过滤器设置上下文之前读取到 null。
        // 在 filterChain.doFilter 之后再读取一次，确保认证完成后能拿到正确的 userId。
        Long uidBefore = AuthUtil.currentUserId();

        // 进入链路前记录请求基线信息。
        log.info("[RequestLoggingFilter] Incoming request: method={}, uri={}, query={}, uid={}",
                method, uri, query, uidBefore);

        // 采集入参以便持久化：客户端 IP 与 UA。
        final String ip = clientIp(request);
        final String ua = request.getHeader("User-Agent");

        long start = System.currentTimeMillis();
        // 放行，交由后续安全链路与控制器处理。
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 无论链路是否异常，都需要清理 MDC，避免串请求污染。
            MDC.remove("requestId");
        }
        long cost = (int)(System.currentTimeMillis() - start);

        // 记录响应状态码，帮助快速确认是否到达控制器（通常 2xx/4xx 来自控制器），
        // 或在安全链路阶段被拦截（如 401 未认证、403 权限不足）。
        int status = response.getStatus();
        // 在认证过滤器运行后再次获取用户ID，记录到请求日志中。
        Long uidAfter = AuthUtil.currentUserId();
        log.info("[RequestLoggingFilter] Response: method={}, uri={}, status={}, uid={}",
                method, uri, status, uidAfter);

        // 将请求指标持久化到数据库，便于后续在管理后台检索与分析。
        try {
            logService.logRequest(method, uri, query, ip, ua, status, (int) cost, uidAfter, requestId);
        } catch (Exception e) {
            // 说明：日志写入失败不影响正常请求流程，仅记录到控制台避免中断业务。
            log.warn("persist request log failed: uri={} msg={}", uri, e.getMessage());
        }
    }

    /**
     * 提取客户端 IP：优先使用反向代理注入的 X-Forwarded-For，然后回退到远程地址。
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // 多级代理场景取第一个 IP
            int idx = xff.indexOf(',');
            return idx > 0 ? xff.substring(0, idx).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }
}