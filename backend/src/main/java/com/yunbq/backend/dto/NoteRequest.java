package com.yunbq.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NoteRequest {
    @NotBlank
    private String title;
    private String content;
    private String tags; // comma-separated
    private Boolean archived;
    private Boolean isPublic; // 新增：公开/私有
}