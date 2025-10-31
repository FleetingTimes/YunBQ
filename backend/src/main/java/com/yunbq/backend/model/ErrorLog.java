package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错误日志实体。
 * 记录未处理异常的类型、消息、堆栈以及发生路径。
 */
@Data
@TableName("error_logs")
public class ErrorLog {
    private Long id;
    private Long userId;
    private String path;
    private String exception;
    private String message;
    private String stackTrace;
    /** 关联到抛出异常的请求标识（可能为空） */
    private String requestId;
    private LocalDateTime createdAt;
}