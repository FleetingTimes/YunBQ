package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NavigationSite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 导航站点Mapper接口
 * 提供导航站点的数据库操作方法
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@Mapper
public interface NavigationSiteMapper extends BaseMapper<NavigationSite> {
    
    /**
     * 根据分类ID查询启用的站点，按排序权重升序
     * 
     * @param categoryId 分类ID
     * @return 站点列表
     */
    @Select("SELECT * FROM navigation_sites WHERE category_id = #{categoryId} AND is_enabled = 1 ORDER BY sort_order ASC, id ASC")
    List<NavigationSite> selectByCategoryId(Long categoryId);
    
    /**
     * 查询推荐站点，按点击次数降序
     * 
     * @param limit 限制数量
     * @return 推荐站点列表
     */
    @Select("SELECT * FROM navigation_sites WHERE is_enabled = 1 AND is_featured = 1 ORDER BY click_count DESC, sort_order ASC LIMIT #{limit}")
    List<NavigationSite> selectFeaturedSites(int limit);
    
    /**
     * 查询热门站点，按点击次数降序
     * 
     * @param limit 限制数量
     * @return 热门站点列表
     */
    @Select("SELECT * FROM navigation_sites WHERE is_enabled = 1 ORDER BY click_count DESC, sort_order ASC LIMIT #{limit}")
    List<NavigationSite> selectPopularSites(int limit);
    
    /**
     * 根据标签搜索站点
     * 
     * @param tag 标签关键词
     * @return 匹配的站点列表
     */
    @Select("SELECT * FROM navigation_sites WHERE is_enabled = 1 AND (tags LIKE CONCAT('%', #{tag}, '%') OR name LIKE CONCAT('%', #{tag}, '%') OR description LIKE CONCAT('%', #{tag}, '%')) ORDER BY sort_order ASC, id ASC")
    List<NavigationSite> searchByTag(String tag);
    
    /**
     * 增加站点点击次数
     * 
     * @param siteId 站点ID
     * @return 影响行数
     */
    @Update("UPDATE navigation_sites SET click_count = click_count + 1 WHERE id = #{siteId}")
    int incrementClickCount(Long siteId);
    
    /**
     * 查询用户添加的站点
     * 
     * @param userId 用户ID
     * @return 用户站点列表
     */
    @Select("SELECT * FROM navigation_sites WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<NavigationSite> selectByUserId(Long userId);
}