package com.yunbq.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoteItem {
    private Long id;
    private Long userId;
    private String authorName;
    // 新增字段：作者头像相对路径（例如 "/uploads/avatars/xxx.jpg"），
    // 由前端使用 avatarFullUrl 拼接为完整可访问地址。
    // 之所以放在 DTO 而不是嵌套 User，是为了保持返回体轻量并兼容现有前端结构。
    private String avatarUrl;
    private String content;
    private String tags;
    private String color;
    private Boolean archived;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // extra fields
    private Long likeCount;
    private Boolean likedByMe;
    private Long favoriteCount;
    private Boolean favoritedByMe;
}