package com.yunbq.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String role;
    private java.time.LocalDateTime createdAt;
}