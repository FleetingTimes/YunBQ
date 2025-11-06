package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NavigationCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 导航分类 Mapper 接口
 * 职责：
 * - 提供分类层级查询（根分类/子分类/全部启用）的数据库操作；
 * - 明确排序策略，保证导航菜单展示稳定。
 *
 * 排序与边界：
 * - 根分类与子分类统一按 `sort_order ASC, id ASC` 排序；
 * - 全部启用分类按层级（`COALESCE(parent_id, 0)`）再按排序权重与主键稳定排序；
 * - 不分页接口由服务层进行必要的截断与缓存策略控制。
 *
 * 作者：YunBQ
 * 时间：2024-11-01
 */
@Mapper
public interface NavigationCategoryMapper extends BaseMapper<NavigationCategory> {
    
    /**
     * 查询所有启用的一级分类。
     * 排序：`sort_order ASC, id ASC`。
     * 返回：
     * - 启用状态的一级分类列表。
     */
    @Select("SELECT * FROM navigation_categories WHERE parent_id IS NULL AND is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<NavigationCategory> selectRootCategories();
    
    /**
     * 根据父级分类 ID 查询子分类。
     * 排序：`sort_order ASC, id ASC`。
     * 参数：
     * - parentId：父级分类 ID。
     * 返回：
     * - 该父级下的启用子分类列表。
     */
    @Select("SELECT * FROM navigation_categories WHERE parent_id = #{parentId} AND is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<NavigationCategory> selectByParentId(Long parentId);
    
    /**
     * 查询所有启用的分类（包含一级和二级）。
     * 排序：按层级（`COALESCE(parent_id, 0)`）、`sort_order ASC`、`id ASC`。
     * 返回：
     * - 所有启用分类列表。
     */
    @Select("SELECT * FROM navigation_categories WHERE is_enabled = 1 ORDER BY COALESCE(parent_id, 0), sort_order ASC, id ASC")
    List<NavigationCategory> selectAllEnabled();
}