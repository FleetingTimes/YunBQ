package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证日志实体。
 * 用于记录登录或令牌校验的成功与失败。
 */
@Data
@TableName("auth_logs")
public class AuthLog {
    private Long id;
    private Long userId;
    private String username;
    private Boolean success;
    private String reason;
    private String ip;
    private String userAgent;
    /** 关联到触发认证校验的请求标识 */
    private String requestId;
    private LocalDateTime createdAt;
}