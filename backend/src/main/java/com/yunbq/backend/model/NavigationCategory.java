package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导航分类实体类
 * 支持二级分类结构，用于侧边栏导航的分类管理
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@Data
@TableName("navigation_categories")
public class NavigationCategory {
    
    /**
     * 分类ID，主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 父级分类ID，NULL表示一级分类
     */
    private Long parentId;
    
    /**
     * 分类名称
     */
    private String name;
    
    /**
     * 分类图标（CSS类名或图标路径）
     */
    private String icon;
    
    /**
     * 分类描述
     */
    private String description;
    
    /**
     * 排序权重，数值越小越靠前
     */
    private Integer sortOrder;
    
    /**
     * 是否启用
     */
    private Boolean isEnabled;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}