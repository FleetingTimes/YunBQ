package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NavigationSite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 导航站点 Mapper 接口
 * 职责：
 * - 提供站点的查询、筛选与统计更新（点击次数）等数据库操作；
 * - 约定统一的排序策略与分页/限制行为，服务层需据此组合业务逻辑。
 *
 * 排序与边界：
 * - 非分页查询统一使用 `ORDER BY sort_order ASC, id ASC` 保证稳定顺序；
 * - 限制返回条数时使用 MySQL `LIMIT`，入参需由服务层进行上限控制（如 20/50 等），避免过大导致性能问题；
 * - 模糊搜索基于 `LIKE`，大小写与前后缀匹配受数据库/字符集影响。
 *
 * 作者：YunBQ
 * 时间：2024-11-01
 */
@Mapper
public interface NavigationSiteMapper extends BaseMapper<NavigationSite> {
    
    /**
     * 根据分类ID查询启用的站点。
     * 排序：按 `sort_order ASC, id ASC` 返回稳定结果。
     * 参数：
     * - categoryId：分类 ID。
     * 返回：
     * - 启用状态的站点列表。
     */
    @Select("SELECT * FROM navigation_sites WHERE category_id = #{categoryId} AND is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<NavigationSite> selectByCategoryId(Long categoryId);
    
    /**
     * 查询推荐站点。
     * 排序：按 `click_count DESC, sort_order ASC`，并使用 `LIMIT` 做数量限制。
     * 参数：
     * - limit：返回数量上限（由服务层设定合理范围）。
     * 返回：
     * - 推荐站点列表。
     */
    @Select("SELECT * FROM navigation_sites WHERE is_enabled = 1 AND is_featured = 1 ORDER BY click_count DESC, sort_order ASC LIMIT #{limit}")
    List<NavigationSite> selectFeaturedSites(int limit);
    
    /**
     * 查询热门站点。
     * 排序：按 `click_count DESC, sort_order ASC`，并使用 `LIMIT` 做数量限制。
     * 参数：
     * - limit：返回数量上限（由服务层设定合理范围）。
     * 返回：
     * - 热门站点列表。
     */
    @Select("SELECT * FROM navigation_sites WHERE is_enabled = 1 ORDER BY click_count DESC, sort_order ASC LIMIT #{limit}")
    List<NavigationSite> selectPopularSites(int limit);
    
    /**
     * 根据标签搜索站点。
     * 过滤与匹配：在 `tags/name/description` 上进行 `LIKE` 模糊匹配。
     * 排序：按 `sort_order ASC, id ASC`。
     * 参数：
     * - tag：标签或关键字。
     * 返回：
     * - 匹配的站点列表。
     */
    @Select("SELECT * FROM navigation_sites WHERE is_enabled = 1 AND (tags LIKE CONCAT('%', #{tag}, '%') OR name LIKE CONCAT('%', #{tag}, '%') OR description LIKE CONCAT('%', #{tag}, '%')) ORDER BY sort_order ASC, id ASC")
    List<NavigationSite> searchByTag(String tag);
    
    /**
     * 增加站点点击次数（原子自增）。
     * 并发：依赖数据库的原子性更新，可能存在高并发下的写入热点；
     * 如需更高性能可考虑异步队列与批量聚合。
     * 参数：
     * - siteId：站点 ID。
     * 返回：
     * - 影响行数（通常为 1）。
     */
    @Update("UPDATE navigation_sites SET click_count = click_count + 1 WHERE id = #{siteId}")
    int incrementClickCount(Long siteId);
    
    /**
     * 查询用户添加的站点。
     * 排序：按 `created_at DESC` 返回最新创建的站点在前。
     * 参数：
     * - userId：用户 ID。
     * 返回：
     * - 用户站点列表。
     */
    @Select("SELECT * FROM navigation_sites WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<NavigationSite> selectByUserId(Long userId);
}