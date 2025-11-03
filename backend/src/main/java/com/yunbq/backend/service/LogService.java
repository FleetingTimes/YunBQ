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

    // ===========================
    // 日志导出相关方法（同步查询与CSV构建）
    // 说明：导出接口调用这些方法获取数据并生成CSV文本，与分页接口的过滤逻辑保持一致。
    // ===========================

    /**
     * 获取审计日志列表（可按级别筛选），按时间倒序。
     */
    public java.util.List<AuditLog> listAuditLogs(String level) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AuditLog> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (level != null && !level.isBlank()) { qw.eq("level", level); }
        qw.orderByDesc("created_at");
        return auditLogMapper.selectList(qw);
    }

    /**
     * 将审计日志导出为CSV文本。
     * 表头：id,userId,level,message,createdAt
     * 字段转义规则：使用 csv() 方法处理逗号、引号与换行。
     */
    public String exportAuditLogsToCsv(java.util.List<AuditLog> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,userId,level,message,createdAt\n");
        for (AuditLog it : items) {
            sb.append(csv(String.valueOf(it.getId()))).append(',')
              .append(csv(String.valueOf(it.getUserId()))).append(',')
              .append(csv(it.getLevel())).append(',')
              .append(csv(it.getMessage())).append(',')
              .append(csv(String.valueOf(it.getCreatedAt())))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * 获取认证日志列表（可按成功/失败、用户名、requestId筛选），按时间倒序。
     */
    public java.util.List<AuthLog> listAuthLogs(Boolean success, String username, String requestId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AuthLog> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (success != null) { qw.eq("success", success ? 1 : 0); }
        if (username != null && !username.isBlank()) { qw.like("username", username); }
        if (requestId != null && !requestId.isBlank()) { qw.eq("request_id", requestId); }
        qw.orderByDesc("created_at");
        return authLogMapper.selectList(qw);
    }

    /**
     * 将认证日志导出为CSV文本。
     * 表头：id,userId,username,success,reason,ip,userAgent,requestId,createdAt
     */
    public String exportAuthLogsToCsv(java.util.List<AuthLog> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,userId,username,success,reason,ip,userAgent,requestId,createdAt\n");
        for (AuthLog it : items) {
            sb.append(csv(String.valueOf(it.getId()))).append(',')
              .append(csv(String.valueOf(it.getUserId()))).append(',')
              .append(csv(it.getUsername())).append(',')
              .append(csv(String.valueOf(Boolean.TRUE.equals(it.getSuccess())))).append(',')
              .append(csv(it.getReason())).append(',')
              .append(csv(it.getIp())).append(',')
              .append(csv(it.getUserAgent())).append(',')
              .append(csv(it.getRequestId())).append(',')
              .append(csv(String.valueOf(it.getCreatedAt())))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * 获取请求日志列表（可按URI、状态码、requestId筛选），按时间倒序。
     */
    public java.util.List<RequestLog> listRequestLogs(String uri, Integer status, String requestId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RequestLog> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (uri != null && !uri.isBlank()) { qw.like("uri", uri); }
        if (status != null) { qw.eq("status", status); }
        if (requestId != null && !requestId.isBlank()) { qw.eq("request_id", requestId); }
        qw.orderByDesc("created_at");
        return requestLogMapper.selectList(qw);
    }

    /**
     * 将请求日志导出为CSV文本。
     * 表头：id,method,uri,query,ip,userAgent,status,durationMs,userId,requestId,createdAt
     */
    public String exportRequestLogsToCsv(java.util.List<RequestLog> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,method,uri,query,ip,userAgent,status,durationMs,userId,requestId,createdAt\n");
        for (RequestLog it : items) {
            sb.append(csv(String.valueOf(it.getId()))).append(',')
              .append(csv(it.getMethod())).append(',')
              .append(csv(it.getUri())).append(',')
              .append(csv(it.getQuery())).append(',')
              .append(csv(it.getIp())).append(',')
              .append(csv(it.getUserAgent())).append(',')
              .append(csv(String.valueOf(it.getStatus()))).append(',')
              .append(csv(String.valueOf(it.getDurationMs()))).append(',')
              .append(csv(String.valueOf(it.getUserId()))).append(',')
              .append(csv(it.getRequestId())).append(',')
              .append(csv(String.valueOf(it.getCreatedAt())))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * 获取错误日志列表（可按异常类名、requestId筛选），按时间倒序。
     */
    public java.util.List<ErrorLog> listErrorLogs(String exception, String requestId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ErrorLog> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        if (exception != null && !exception.isBlank()) { qw.like("exception", exception); }
        if (requestId != null && !requestId.isBlank()) { qw.eq("request_id", requestId); }
        qw.orderByDesc("created_at");
        return errorLogMapper.selectList(qw);
    }

    /**
     * 将错误日志导出为CSV文本。
     * 表头：id,userId,path,exception,message,stackTrace,requestId,createdAt
     */
    public String exportErrorLogsToCsv(java.util.List<ErrorLog> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,userId,path,exception,message,stackTrace,requestId,createdAt\n");
        for (ErrorLog it : items) {
            sb.append(csv(String.valueOf(it.getId()))).append(',')
              .append(csv(String.valueOf(it.getUserId()))).append(',')
              .append(csv(it.getPath())).append(',')
              .append(csv(it.getException())).append(',')
              .append(csv(it.getMessage())).append(',')
              .append(csv(it.getStackTrace())).append(',')
              .append(csv(it.getRequestId())).append(',')
              .append(csv(String.valueOf(it.getCreatedAt())))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * CSV 字段安全转义工具方法。
     * 规则：若包含逗号、双引号或换行，则使用双引号包裹，并将内部双引号替换为两个双引号。
     */
    private String csv(String s) {
        if (s == null) return "";
        boolean needQuote = s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"");
        if (needQuote) {
            return '"' + s.replace("\"", "\"\"") + '"';
        } else {
            return s;
        }
    }
}