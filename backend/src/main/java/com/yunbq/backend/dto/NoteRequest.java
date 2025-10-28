package com.yunbq.backend.dto;

import lombok.Data;

@Data
public class NoteRequest {
    private String content;
    private String tags; // comma-separated
    private String color; // hex color like #RRGGBB
    private Boolean archived;
    private Boolean isPublic; // 新增：公开/私有
}