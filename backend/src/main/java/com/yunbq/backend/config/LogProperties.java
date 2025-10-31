package com.yunbq.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 日志模块属性配置（来自 application.yml / application.properties）。
 * 作用：
 * - 提供日志写入的开关与采样，便于在不同环境下灵活调整日志量；
 * - 提供各类日志的保留天数，用于定期清理历史数据；
 * - 提供保留清理的执行频率（毫秒）。
 *
 * 前缀：logdb
 */
@Component
@ConfigurationProperties(prefix = "logdb")
public class LogProperties {
    /** 是否写入请求日志 */
    private boolean requestEnabled = true;
    /** 请求日志采样百分比（0-100，100 表示全量） */
    private int requestSamplingPercent = 100;

    /** 是否写入认证日志 */
    private boolean authEnabled = true;

    /** 是否写入错误日志 */
    private boolean errorEnabled = true;

    /** 是否写入审计日志 */
    private boolean auditEnabled = true;

    /** 请求日志保留天数（定期清理） */
    private int retentionRequestDays = 30;
    /** 认证日志保留天数 */
    private int retentionAuthDays = 30;
    /** 错误日志保留天数 */
    private int retentionErrorDays = 90;
    /** 审计日志保留天数 */
    private int retentionAuditDays = 90;

    /** 保留清理的执行间隔（毫秒），默认一小时 */
    private long retentionSweepIntervalMs = 60 * 60 * 1000L;

    public boolean isRequestEnabled() { return requestEnabled; }
    public void setRequestEnabled(boolean requestEnabled) { this.requestEnabled = requestEnabled; }

    public int getRequestSamplingPercent() { return requestSamplingPercent; }
    public void setRequestSamplingPercent(int requestSamplingPercent) { this.requestSamplingPercent = requestSamplingPercent; }

    public boolean isAuthEnabled() { return authEnabled; }
    public void setAuthEnabled(boolean authEnabled) { this.authEnabled = authEnabled; }

    public boolean isErrorEnabled() { return errorEnabled; }
    public void setErrorEnabled(boolean errorEnabled) { this.errorEnabled = errorEnabled; }

    public boolean isAuditEnabled() { return auditEnabled; }
    public void setAuditEnabled(boolean auditEnabled) { this.auditEnabled = auditEnabled; }

    public int getRetentionRequestDays() { return retentionRequestDays; }
    public void setRetentionRequestDays(int retentionRequestDays) { this.retentionRequestDays = retentionRequestDays; }

    public int getRetentionAuthDays() { return retentionAuthDays; }
    public void setRetentionAuthDays(int retentionAuthDays) { this.retentionAuthDays = retentionAuthDays; }

    public int getRetentionErrorDays() { return retentionErrorDays; }
    public void setRetentionErrorDays(int retentionErrorDays) { this.retentionErrorDays = retentionErrorDays; }

    public int getRetentionAuditDays() { return retentionAuditDays; }
    public void setRetentionAuditDays(int retentionAuditDays) { this.retentionAuditDays = retentionAuditDays; }

    public long getRetentionSweepIntervalMs() { return retentionSweepIntervalMs; }
    public void setRetentionSweepIntervalMs(long retentionSweepIntervalMs) { this.retentionSweepIntervalMs = retentionSweepIntervalMs; }
}