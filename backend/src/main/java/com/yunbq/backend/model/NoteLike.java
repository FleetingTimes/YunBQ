package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note_likes")
public class NoteLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noteId;
    private Long userId;
    private LocalDateTime createdAt;
}