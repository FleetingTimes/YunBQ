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
        // 补充采集并打印 CORS 相关请求头，便于定位 403 来源：
        // - Origin：跨域的实际来源（协议+域名+端口），CORS 主要基于该值判断放行；
        // - Access-Control-Request-Method：预检请求声明的实际方法（如 PATCH/DELETE 等）；
        // - Access-Control-Request-Headers：预检请求声明的实际将要携带的非简单头（如 Authorization, X-Requested-With）。
        final String origin = request.getHeader("Origin");
        final String acrMethod = request.getHeader("Access-Control-Request-Method");
        final String acrHeaders = request.getHeader("Access-Control-Request-Headers");
        // 注意：此处不要过早读取用户ID，避免在 JWT 过滤器设置上下文之前读取到 null。
        // 在 filterChain.doFilter 之后再读取一次，确保认证完成后能拿到正确的 userId。
        Long uidBefore = AuthUtil.currentUserId();

        // 进入链路前记录请求基线信息。
        log.info("[RequestLoggingFilter] Incoming request: method={}, uri={}, query={}, uid={}",
                method, uri, query, uidBefore);
        // 额外打印 CORS 相关头，结合响应头可快速判断是否因跨域被拒绝。
        log.debug("[RequestLoggingFilter] CORS headers: Origin={}, ACR-Method={}, ACR-Headers={}",
                origin, acrMethod, acrHeaders);

        // 采集入参以便持久化：客户端 IP 与 UA。
        // 说明：在经过 CDN/反向代理（如 Cloudflare、Nginx）时，`request.getRemoteAddr()` 通常是“最近一跳”的代理 IP，
        //      无法代表真实的终端用户 IP。为此，这里做了多层头部解析，优先级如下：
        //      1) Cloudflare 的 `CF-Connecting-IP`（若存在，直接认为是客户端真实 IP）；
        //      2) 标准代理头 `X-Forwarded-For`（多级代理时取第一个 IP）；
        //      3) 常见反代头 `X-Real-IP`（如 Nginx 设置 `proxy_set_header X-Real-IP $remote_addr;`）；
        //      4) 回退到 `request.getRemoteAddr()`（无代理或未设置头时）。
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
        // 若启用 CORS，响应应包含 `Access-Control-Allow-Origin` 与（在允许凭据时）`Access-Control-Allow-Credentials`。
        final String allowOrigin = response.getHeader("Access-Control-Allow-Origin");
        final String allowCreds = response.getHeader("Access-Control-Allow-Credentials");
        log.debug("[RequestLoggingFilter] CORS response: Allow-Origin={}, Allow-Credentials={}", allowOrigin, allowCreds);

        // 额外打印与“真实客户端 IP 推断”相关的请求头，便于运维定位“为何总是记录到代理 IP”。
        // 注意：仅在 DEBUG 级别输出，避免生产日志过于冗长。
        try {
            String cfConnectingIp = request.getHeader("CF-Connecting-IP");
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            String xRealIp = request.getHeader("X-Real-IP");
            String remoteAddr = request.getRemoteAddr();
            log.debug("[RequestLoggingFilter] IP headers: CF-Connecting-IP={}, X-Forwarded-For={}, X-Real-IP={}, remoteAddr={}, resolvedIp={}",
                    cfConnectingIp, xForwardedFor, xRealIp, remoteAddr, ip);
        } catch (Exception ignored) {
            // 忽略打印头部信息失败的情况（极少发生），不影响正常业务与日志持久化
        }

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
        // 1) Cloudflare 隧道/代理会注入该头，表示客户端真实 IP
        String cf = request.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        // 2) 标准多级代理头部，形如 "client, proxy1, proxy2"，取第一个即为原始客户端 IP
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int idx = xff.indexOf(',');
            return idx > 0 ? xff.substring(0, idx).trim() : xff.trim();
        }
        // 3) 常见反代头（例如 Nginx）
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri.trim();
        }
        // 4) 无代理或未设置头时，回退到最近一跳的地址
        return request.getRemoteAddr();
    }
}