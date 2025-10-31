package com.yunbq.backend.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yunbq.backend.config.LogProperties;
import com.yunbq.backend.mapper.*;
import com.yunbq.backend.model.RequestLog;
import com.yunbq.backend.model.AuthLog;
import com.yunbq.backend.model.ErrorLog;
import com.yunbq.backend.model.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 日志保留清理任务。
 * 作用：根据配置的保留天数，定期清理超期的日志数据，控制表规模。
 * 触发：固定延迟执行（fixedDelay），间隔来自配置 logdb.retention-sweep-interval-ms。
 */
@Component
public class LogRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(LogRetentionScheduler.class);

    private final LogProperties props;
    private final AuditLogMapper auditLogMapper;
    private final RequestLogMapper requestLogMapper;
    private final AuthLogMapper authLogMapper;
    private final ErrorLogMapper errorLogMapper;

    public LogRetentionScheduler(LogProperties props,
                                 AuditLogMapper auditLogMapper,
                                 RequestLogMapper requestLogMapper,
                                 AuthLogMapper authLogMapper,
                                 ErrorLogMapper errorLogMapper) {
        this.props = props;
        this.auditLogMapper = auditLogMapper;
        this.requestLogMapper = requestLogMapper;
        this.authLogMapper = authLogMapper;
        this.errorLogMapper = errorLogMapper;
    }

    /**
     * 定期执行日志清理：按各表的保留天数删除 created_at 在截止时间之前的数据。
     * 注意：为避免长事务与锁影响，将每类日志分别清理；若数据量巨大，建议改为分批 limit 删除。
     */
    @Scheduled(fixedDelayString = "${logdb.retention-sweep-interval-ms:3600000}")
    public void sweep() {
        try {
            // 请求日志清理
            LocalDateTime cutoffReq = LocalDateTime.now().minusDays(props.getRetentionRequestDays());
            int reqDel = requestLogMapper.delete(new QueryWrapper<RequestLog>()
                    .lt("created_at", cutoffReq));
            // 认证日志清理
            LocalDateTime cutoffAuth = LocalDateTime.now().minusDays(props.getRetentionAuthDays());
            int authDel = authLogMapper.delete(new QueryWrapper<AuthLog>()
                    .lt("created_at", cutoffAuth));
            // 错误日志清理
            LocalDateTime cutoffErr = LocalDateTime.now().minusDays(props.getRetentionErrorDays());
            int errDel = errorLogMapper.delete(new QueryWrapper<ErrorLog>()
                    .lt("created_at", cutoffErr));
            // 审计日志清理
            LocalDateTime cutoffAudit = LocalDateTime.now().minusDays(props.getRetentionAuditDays());
            int auditDel = auditLogMapper.delete(new QueryWrapper<AuditLog>()
                    .lt("created_at", cutoffAudit));

            log.info("[LogRetention] sweep done: requestDel={} authDel={} errorDel={} auditDel={}",
                    reqDel, authDel, errDel, auditDel);
        } catch (Exception e) {
            log.warn("[LogRetention] sweep failed: {}", e.getMessage());
        }
    }
}