package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feedback_submissions")
public class FeedbackSubmission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;          // issue/suggest
    private String status;        // open/processing/resolved/rejected
    private Long userId;          // 可空（匿名）
    private String contactEmail;  // 可空
    private String contactQq;     // 可空
    // 问题反馈字段
    private String module;        // 广场/我的便签/搜索/喜欢/收藏/消息/用户拾言
    private String pagePath;      // 页面路径，如 /、/my/shiyan、/search?q=...
    private String title;         // 标题
    private String description;   // 详情
    // 根据需求：移除 steps/expected/actual 字段，仅保留核心描述
    // 站点建议字段
    private String category;      // 建议分类
    // 根据需求：移除 expectedBenefit 字段
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
