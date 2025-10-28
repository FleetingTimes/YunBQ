package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_logs")
public class AuditLog {
    private Long id;
    private Long userId;
    private String level;
    private String message;
    private LocalDateTime createdAt;
}