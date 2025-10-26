package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notes")
public class Note {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String tags; // comma-separated tags
    private Boolean archived;
    private Boolean isPublic; // 新增：公开/私有
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}