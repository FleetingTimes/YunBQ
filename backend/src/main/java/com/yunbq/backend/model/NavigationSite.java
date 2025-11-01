package com.yunbq.backend.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导航站点实体类
 * 用于存储导航分类下的具体站点信息
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@Data
@TableName("navigation_sites")
public class NavigationSite {
    
    /**
     * 站点ID，主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属分类ID，关联navigation_categories表
     */
    private Long categoryId;
    
    /**
     * 站点名称
     */
    private String name;
    
    /**
     * 站点URL地址
     */
    private String url;
    
    /**
     * 站点描述
     */
    private String description;
    
    /**
     * 站点图标URL或CSS类名
     */
    private String icon;
    
    /**
     * 站点favicon地址
     */
    private String faviconUrl;
    
    /**
     * 标签，逗号分隔
     */
    private String tags;
    
    /**
     * 排序权重，数值越小越靠前
     */
    private Integer sortOrder;
    
    /**
     * 是否启用
     */
    private Boolean isEnabled;
    
    /**
     * 是否为推荐站点
     */
    private Boolean isFeatured;
    
    /**
     * 点击次数统计
     */
    private Long clickCount;
    
    /**
     * 添加者用户ID，NULL表示系统添加
     */
    private Long userId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}