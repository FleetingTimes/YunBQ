package com.yunbq.backend.service;

import com.yunbq.backend.mapper.*;
import com.yunbq.backend.model.*;
import com.yunbq.backend.config.LogProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 日志统一写入服务。
 * 作用：集中管理各类日志（审计、请求、认证、错误）的入库逻辑，便于维护与复用。
 * 统一入口便于在过滤器、切面、异常处理等位置直接调用。
 */
@Service
public class LogService {

    private final AuditLogMapper auditLogMapper;
    private final RequestLogMapper requestLogMapper;
    private final AuthLogMapper authLogMapper;
    private final ErrorLogMapper errorLogMapper;
    private final LogProperties logProperties;

    /**
     * 通过构造函数注入 Mapper 与日志配置。
     * 说明：LogProperties 为配置中心，承载日志开关、采样与保留天数等。
     */
    public LogService(AuditLogMapper auditLogMapper,
                      RequestLogMapper requestLogMapper,
                      AuthLogMapper authLogMapper,
                      ErrorLogMapper errorLogMapper,
                      LogProperties logProperties) {
        this.auditLogMapper = auditLogMapper;
        this.requestLogMapper = requestLogMapper;
        this.authLogMapper = authLogMapper;
        this.errorLogMapper = errorLogMapper;
        this.logProperties = logProperties;
    }

    /**
     * 写入一条审计日志。
     * - userId：触发该业务动作的用户ID（允许为 null，表示匿名或系统动作）。
     * - level：审计级别，如 "INFO"、"WARN"、"ERROR" 等（用于后台筛选）。
     * - message：简要业务描述，例如 "note 123 liked by user 45"。
     */
    /**
     * 异步写入审计日志。
     * 开关控制：logdb.audit-enabled
     */
    @Async("logTaskExecutor")
    public void logAudit(Long userId, String level, String message) {
        if (!logProperties.isAuditEnabled()) {
            return; // 审计日志关闭时直接跳过写入
        }
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setLevel(level != null ? level : "INFO");
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    /**
     * 写入请求日志。
     * 用于记录请求进入与返回后的关键指标：方法、路径、状态码、耗时、用户等。
     */
    /**
     * 异步写入请求日志。
     * 开关控制：logdb.request-enabled；采样：logdb.request-sampling-percent。
     * - 当采样 < 100 时，使用简单的百分比随机采样，避免数据库压力。
     * - requestId 关联用于跨模块定位同一次请求。
     */
    @Async("logTaskExecutor")
    public void logRequest(String method, String uri, String query,
                           String ip, String userAgent,
                           int status, int durationMs,
                           Long userId,
                           String requestId) {
        if (!logProperties.isRequestEnabled()) {
            return; // 请求日志关闭时直接跳过
        }
        int pct = Math.max(0, Math.min(100, logProperties.getRequestSamplingPercent()));
        if (pct < 100) {
            // 简单百分比采样：生成 0-99 的随机数，小于 pct 则写入
            int r = ThreadLocalRandom.current().nextInt(100);
            if (r >= pct) {
                return; // 未命中采样，跳过持久化
            }
        }
        RequestLog rl = new RequestLog();
        rl.setMethod(method);
        rl.setUri(uri);
        rl.setQuery(query);
        rl.setIp(ip);
        rl.setUserAgent(userAgent);
        rl.setStatus(status);
        rl.setDurationMs(durationMs);
        rl.setUserId(userId);
        rl.setRequestId(requestId);
        rl.setCreatedAt(LocalDateTime.now());
        requestLogMapper.insert(rl);
    }

    /**
     * 写入认证日志：成功。
     */
    /**
     * 异步写入认证成功日志。
     * 开关控制：logdb.auth-enabled；携带 requestId 便于与请求日志串联。
     */
    @Async("logTaskExecutor")
    public void logAuthSuccess(Long userId, String username, String ip, String userAgent, String requestId) {
        if (!logProperties.isAuthEnabled()) {
            return;
        }
        AuthLog al = new AuthLog();
        al.setUserId(userId);
        al.setUsername(username);
        al.setSuccess(Boolean.TRUE);
        al.setReason(null);
        al.setIp(ip);
        al.setUserAgent(userAgent);
        al.setRequestId(requestId);
        al.setCreatedAt(LocalDateTime.now());
        authLogMapper.insert(al);
    }

    /**
     * 写入认证日志：失败。
     */
    /**
     * 异步写入认证失败日志。
     * 开关控制：logdb.auth-enabled；携带 requestId 便于与请求日志串联。
     */
    @Async("logTaskExecutor")
    public void logAuthFailure(String reason, String ip, String userAgent, String requestId) {
        if (!logProperties.isAuthEnabled()) {
            return;
        }
        AuthLog al = new AuthLog();
        al.setUserId(null);
        al.setUsername(null);
        al.setSuccess(Boolean.FALSE);
        al.setReason(reason);
        al.setIp(ip);
        al.setUserAgent(userAgent);
        al.setRequestId(requestId);
        al.setCreatedAt(LocalDateTime.now());
        authLogMapper.insert(al);
    }

    /**
     * 写入错误日志。
     */
    /**
     * 异步写入错误日志。
     * 开关控制：logdb.error-enabled；携带 requestId 便于在异常聚合时回溯具体请求。
     */
    @Async("logTaskExecutor")
    public void logError(Long userId, String path, Throwable e, String requestId) {
        if (!logProperties.isErrorEnabled()) {
            return;
        }
        ErrorLog el = new ErrorLog();
        el.setUserId(userId);
        el.setPath(path);
        el.setException(e.getClass().getName());
        el.setMessage(e.getMessage());
        el.setStackTrace(stackTraceToString(e));
        el.setRequestId(requestId);
        el.setCreatedAt(LocalDateTime.now());
        errorLogMapper.insert(el);
    }

    private String stackTraceToString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}