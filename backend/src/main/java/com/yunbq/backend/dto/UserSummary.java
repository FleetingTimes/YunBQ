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
    /**
     * 用户创建时间（LocalDateTime）。
     * 为避免在 JSON 导出时出现日期序列化问题（例如未注册 JavaTimeModule 的情况），
     * 显式指定序列化格式为 ISO-8601。
     */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private java.time.LocalDateTime createdAt;
    /**
     * 头像地址（相对路径或完整 URL）。
     * 前端可根据部署情况拼接成可访问的完整资源地址。
     */
    private String avatarUrl;
    /**
     * 是否已设置密码（不返回真实密码或哈希）。
     * 说明：用于在管理界面上显示“已设置/未设置”的状态标签。
     */
    private boolean hasPassword;
}