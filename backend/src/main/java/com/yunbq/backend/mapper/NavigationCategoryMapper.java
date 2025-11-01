package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NavigationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 导航分类Mapper接口
 * 提供导航分类的数据库操作方法
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@Mapper
public interface NavigationCategoryMapper extends BaseMapper<NavigationCategory> {
    
    /**
     * 查询所有启用的一级分类，按排序权重升序
     * 
     * @return 一级分类列表
     */
    @Select("SELECT * FROM navigation_categories WHERE parent_id IS NULL AND is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<NavigationCategory> selectRootCategories();
    
    /**
     * 根据父级分类ID查询子分类，按排序权重升序
     * 
     * @param parentId 父级分类ID
     * @return 子分类列表
     */
    @Select("SELECT * FROM navigation_categories WHERE parent_id = #{parentId} AND is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<NavigationCategory> selectByParentId(Long parentId);
    
    /**
     * 查询所有启用的分类（包含一级和二级），按层级和排序权重排序
     * 
     * @return 所有启用分类列表
     */
    @Select("SELECT * FROM navigation_categories WHERE is_enabled = 1 ORDER BY COALESCE(parent_id, 0), sort_order ASC, id ASC")
    List<NavigationCategory> selectAllEnabled();
}