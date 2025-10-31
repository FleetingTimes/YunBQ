package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 请求日志实体。
 * 字段设计与表 request_logs 对齐，用于记录每个 HTTP 请求的关键信息与耗时。
 */
@Data
@TableName("request_logs")
public class RequestLog {
    private Long id;
    private String method;
    private String uri;
    private String query;
    private String ip;
    private String userAgent;
    private Integer status;
    private Integer durationMs;
    private Long userId;
    /** 请求唯一标识，用于跨表串联与运维排查 */
    private String requestId;
    private LocalDateTime createdAt;
}