package com.yunbq.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoteItem {
    private Long id;
    private Long userId;
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