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
 * 请求级别日志过滤器（OncePerRequestFilter）
 * 作用与设计：
 * - 生成并贯穿唯一的 `requestId`，通过响应头 `X-Request-Id` 暴露给前端；
 * - 在进入控制器前记录请求基线信息（方法、URI、查询串、用户ID等）；
 * - 在安全链路与控制器处理完成后记录响应状态码与耗时，用于快速定位是否被 401/403 拦截；
 * - 将关键指标持久化（ip、ua、status、cost、uid、requestId），便于后台管理页检索分析。
 * 过滤器顺序：在 `SecurityConfig` 中注册为位于 `SecurityContextHolderFilter` 之前，
 * 以便将 `requestId` 提前放入请求属性与 MDC，后续过滤器与控制器均可使用该标识。
 * 使用建议：
 * - 该过滤器主要用于开发/联调阶段的观测，生产环境可按需调整采集字段或采样率；
 * - 日志持久化失败不影响业务流程，仅记录警告避免中断请求。
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    // 说明：通过构造注入日志服务，将请求的关键指标持久化到数据库。
    private final com.yunbq.backend.service.LogService logService;

    /**
     * 构造函数：注入日志服务用于持久化请求指标。
     * @param logService 日志服务（数据库持久化）
     */
    @Autowired
    public RequestLoggingFilter(com.yunbq.backend.service.LogService logService) {
        this.logService = logService;
    }

    @Override
    /**
     * 请求日志与请求ID生成/传播。
     * 参数：
     * - request：HTTP 请求对象；
     * - response：HTTP 响应对象；
     * - filterChain：过滤器链，需在处理完毕后继续调用。
     * 行为与边界：
     * - 若请求属性已包含 `requestId`（上游代理/网关生成），则复用；否则生成新的唯一ID（如 UUID）；
     * - 将 `requestId` 放入 `MDC`，便于后续日志链路追踪；
     * - 在响应或异常场景结束时清理 `MDC`，防止泄漏到其他线程或请求。
     * 异常策略：
     * - 持久化日志失败或其他非致命异常不应影响主链路；记录 warn 后继续；
     * - 无论异常与否，均保证调用 `filterChain.doFilter(...)`，避免阻断请求。
     */
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 方法说明：生成 requestId、记录入站请求、放行链路并记录出站响应与耗时，最后持久化。
        // 参数：
        // - request：当前 HTTP 请求
        // - response：当前 HTTP 响应
        // - filterChain：过滤器链，调用后进入安全链路与控制器
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
     * 提取客户端 IP：优先使用反向代理头 `X-Forwarded-For`，多级代理取第一个；
     * 若不存在则回退到 `request.getRemoteAddr()`。
     * @param request 当前 HTTP 请求
     * @return 推断出的客户端 IP 地址字符串
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