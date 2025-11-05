package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体：用于展示“收到的赞/收藏/系统通知”等。
 * 字段含义与 schema.sql 中 messages 表一致。
 */
@Data
@TableName("messages")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;              // 消息类型：like/favorite/reply/at/system
    private Long actorUserId;         // 触发消息的用户（系统消息可为空）
    private Long receiverUserId;      // 接收消息的用户
    private Long noteId;              // 关联拾言 ID（系统消息可为空）
    private String message;           // 附加文案（可空）
    private Boolean isRead;           // 是否已读
    private LocalDateTime createdAt;  // 创建时间
}