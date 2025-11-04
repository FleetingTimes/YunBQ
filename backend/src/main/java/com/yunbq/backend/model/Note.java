package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
// 表名迁移说明：
// - 旧表名为 notes；为品牌统一改造，数据库迁移将其重命名为 shiyan；
// - 这里将实体注解更新为 "shiyan"，确保 MyBatis-Plus 直接访问新表；
// - 若线上仍有旧库未迁移，DbMigrationRunner 会在启动时自动执行重命名以保持兼容。
@TableName("shiyan")
public class Note {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String tags; // comma-separated tags
    private String color; // hex color like #RRGGBB
    private Boolean archived;
    private Boolean isPublic; // 新增：公开/私有
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}