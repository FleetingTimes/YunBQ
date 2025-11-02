package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.mapper.NavigationSiteMapper;
import com.yunbq.backend.mapper.NavigationCategoryMapper;
import com.yunbq.backend.model.NavigationSite;
import com.yunbq.backend.model.NavigationCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导航站点服务层
 * 提供导航站点的业务逻辑处理
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@Service
public class NavigationSiteService {
    
    private final NavigationSiteMapper siteMapper;
    private final NavigationCategoryMapper categoryMapper;
    
    public NavigationSiteService(NavigationSiteMapper siteMapper, NavigationCategoryMapper categoryMapper) {
        this.siteMapper = siteMapper;
        this.categoryMapper = categoryMapper;
    }
    
    /**
     * 根据分类ID获取站点列表
     * 
     * @param categoryId 分类ID
     * @return 站点列表
     */
    public List<NavigationSite> getSitesByCategory(Long categoryId) {
        // 修复：Mapper 方法名与 Service 不一致
        // 说明：NavigationSiteMapper 中定义的方法为 selectByCategoryId(Long categoryId)，
        // 此处原本调用了不存在的 selectByCategory(...)，会导致编译失败或运行时异常。
        // 调整为调用正确的方法名，保证查询按分类ID返回启用的站点并按排序权重升序。
        return siteMapper.selectByCategoryId(categoryId);
    }
    
    /**
     * 获取推荐站点
     * 
     * @param limit 限制数量
     * @return 推荐站点列表
     */
    public List<NavigationSite> getFeaturedSites(int limit) {
        // 修复：Mapper 方法名与 Service 不一致
        // 说明：NavigationSiteMapper 中定义的方法为 selectFeaturedSites(int limit)，
        // 此处原本调用了不存在的 selectFeatured(...)。
        // 调整为调用正确的方法名，使推荐站点查询按点击次数降序并限制数量。
        return siteMapper.selectFeaturedSites(limit);
    }
    
    /**
     * 获取热门站点
     * 
     * @param limit 限制数量
     * @return 热门站点列表
     */
    public List<NavigationSite> getPopularSites(int limit) {
        // 修复：Mapper 方法名与 Service 不一致
        // 说明：NavigationSiteMapper 中定义的方法为 selectPopularSites(int limit)，
        // 此处原本调用了不存在的 selectPopular(...)。
        // 调整为调用正确的方法名，使热门站点查询按点击次数降序并限制数量。
        return siteMapper.selectPopularSites(limit);
    }
    
    /**
     * 根据标签搜索站点
     * 
     * @param tags 标签（支持逗号分隔的多个关键词）
     * @param limit 限制数量（当 Mapper 不支持 limit 时在内存中截断）
     * @return 站点列表
     */
    public List<NavigationSite> searchByTags(String tags, int limit) {
        // 兼容说明：Mapper 当前提供的是 searchByTag(String tag) 单标签查询，且不支持 limit 参数。
        // 为与 Controller 的「多标签 + limit」接口契合，这里采用以下策略：
        // 1) 拆分传入的 tags（逗号/空白分隔），仅使用第一个非空标签执行数据库查询；
        // 2) 若传入了 limit>0，则对结果列表进行内存级截断，避免返回过多数据；
        // 3) 后续如需支持多标签 AND/OR 组合与 SQL 分页，可在 Mapper 侧新增相应方法。
        if (tags == null || tags.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        String firstTag = java.util.Arrays.stream(tags.split("[,\n\r\t ]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .findFirst()
            .orElse("");
        if (firstTag.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<NavigationSite> all = siteMapper.searchByTag(firstTag);
        if (limit > 0 && all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }
    
    /**
     * 获取用户添加的站点
     * 
     * @param userId 用户ID
     * @return 用户站点列表
     */
    public List<NavigationSite> getUserSites(Long userId) {
        // 修复：Mapper 方法名与 Service 不一致
        // 说明：NavigationSiteMapper 中定义的方法为 selectByUserId(Long userId)，
        // 此处原本调用了不存在的 selectByUser(...)。
        // 调整为调用正确的方法名，返回按创建时间倒序的用户站点列表。
        return siteMapper.selectByUserId(userId);
    }
    
    /**
     * 分页查询导航站点
     * 
     * @param page 页码
     * @param size 每页大小
     * @param name 站点名称（模糊查询）
     * @param categoryId 分类ID
     * @param isEnabled 是否启用
     * @param isFeatured 是否推荐
     * @param userId 用户ID（查询用户添加的站点）
     * @return 分页结果
     */
    public Page<NavigationSite> listSites(int page, int size, String name, Long categoryId, 
                                         Boolean isEnabled, Boolean isFeatured, Long userId) {
        QueryWrapper<NavigationSite> queryWrapper = new QueryWrapper<>();
        
        // 名称模糊查询
        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("name", name.trim());
        }
        
        // 分类过滤
        if (categoryId != null) {
            queryWrapper.eq("category_id", categoryId);
        }
        
        // 启用状态过滤
        if (isEnabled != null) {
            queryWrapper.eq("is_enabled", isEnabled);
        }
        
        // 推荐状态过滤
        if (isFeatured != null) {
            queryWrapper.eq("is_featured", isFeatured);
        }
        
        // 用户过滤
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        
        // 按排序权重和ID排序
        queryWrapper.orderByAsc("sort_order", "id");
        
        return siteMapper.selectPage(Page.of(page, size), queryWrapper);
    }
    
    /**
     * 根据ID获取导航站点
     * 
     * @param id 站点ID
     * @return 导航站点
     */
    public NavigationSite getById(Long id) {
        return siteMapper.selectById(id);
    }
    
    /**
     * 创建导航站点
     * 
     * @param site 导航站点信息
     * @param userId 创建用户ID（可为null，表示系统管理员创建）
     * @return 创建的导航站点
     */
    @Transactional
    public NavigationSite createSite(NavigationSite site, Long userId) {
        // === 重要校验：分类ID不能为空 ===
        // 问题背景：数据库表 navigation_sites 的字段 category_id 为 NOT NULL 且无默认值。
        // 当请求未提供 categoryId 时，MyBatis-Plus 在 insert 语句中会省略该列（默认 NOT_NULL 插入策略），
        // 导致数据库报错：Field 'category_id' doesn't have a default value。
        // 解决方案：在服务层创建前强制校验 categoryId 是否为 null，避免生成不合法的插入语句，
        // 并返回更明确的业务错误提示，便于前端和调用方定位问题。
        if (site.getCategoryId() == null) {
            throw new RuntimeException("分类ID不能为空，请选择一个分类后再创建站点");
        }

        // 验证分类是否存在
        if (site.getCategoryId() != null) {
            NavigationCategory category = categoryMapper.selectById(site.getCategoryId());
            if (category == null) {
                throw new RuntimeException("指定的分类不存在");
            }
        }
        
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        site.setCreatedAt(now);
        site.setUpdatedAt(now);
        
        // 设置创建用户
        site.setUserId(userId);
        
        // 如果未设置排序权重，设置为最大值+1
        if (site.getSortOrder() == null) {
            QueryWrapper<NavigationSite> queryWrapper = new QueryWrapper<>();
            if (site.getCategoryId() != null) {
                queryWrapper.eq("category_id", site.getCategoryId());
            }
            queryWrapper.orderByDesc("sort_order").last("LIMIT 1");
            NavigationSite lastSite = siteMapper.selectOne(queryWrapper);
            int nextSortOrder = (lastSite != null && lastSite.getSortOrder() != null) 
                ? lastSite.getSortOrder() + 1 : 1;
            site.setSortOrder(nextSortOrder);
        }
        
        // 如果未设置启用状态，默认启用
        if (site.getIsEnabled() == null) {
            site.setIsEnabled(true);
        }
        
        // 如果未设置推荐状态，默认不推荐
        if (site.getIsFeatured() == null) {
            site.setIsFeatured(false);
        }
        
        // 如果未设置点击次数，默认为0
        if (site.getClickCount() == null) {
            site.setClickCount(0L);
        }
        
        siteMapper.insert(site);
        return site;
    }
    
    /**
     * 更新导航站点
     * 
     * @param id 站点ID
     * @param site 更新的站点信息
     * @return 更新后的导航站点
     */
    @Transactional
    public NavigationSite updateSite(Long id, NavigationSite site) {
        NavigationSite existingSite = siteMapper.selectById(id);
        if (existingSite == null) {
            throw new RuntimeException("导航站点不存在");
        }
        
        // 验证分类是否存在
        if (site.getCategoryId() != null) {
            NavigationCategory category = categoryMapper.selectById(site.getCategoryId());
            if (category == null) {
                throw new RuntimeException("指定的分类不存在");
            }
        }
        
        // 更新字段
        UpdateWrapper<NavigationSite> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        
        if (site.getCategoryId() != null) {
            updateWrapper.set("category_id", site.getCategoryId());
        }
        if (site.getName() != null) {
            updateWrapper.set("name", site.getName());
        }
        if (site.getUrl() != null) {
            updateWrapper.set("url", site.getUrl());
        }
        if (site.getDescription() != null) {
            updateWrapper.set("description", site.getDescription());
        }
        if (site.getIcon() != null) {
            updateWrapper.set("icon", site.getIcon());
        }
        if (site.getFaviconUrl() != null) {
            updateWrapper.set("favicon_url", site.getFaviconUrl());
        }
        if (site.getTags() != null) {
            updateWrapper.set("tags", site.getTags());
        }
        if (site.getSortOrder() != null) {
            updateWrapper.set("sort_order", site.getSortOrder());
        }
        if (site.getIsEnabled() != null) {
            updateWrapper.set("is_enabled", site.getIsEnabled());
        }
        if (site.getIsFeatured() != null) {
            updateWrapper.set("is_featured", site.getIsFeatured());
        }
        
        // 设置更新时间
        updateWrapper.set("updated_at", LocalDateTime.now());
        
        siteMapper.update(null, updateWrapper);
        return siteMapper.selectById(id);
    }
    
    /**
     * 删除导航站点
     * 
     * @param id 站点ID
     */
    @Transactional
    public void deleteSite(Long id) {
        NavigationSite site = siteMapper.selectById(id);
        if (site == null) {
            throw new RuntimeException("导航站点不存在");
        }
        
        siteMapper.deleteById(id);
    }
    
    /**
     * 增加站点点击次数
     * 
     * @param id 站点ID
     * @return 更新后的站点
     */
    @Transactional
    public NavigationSite incrementClickCount(Long id) {
        NavigationSite site = siteMapper.selectById(id);
        if (site == null) {
            throw new RuntimeException("导航站点不存在");
        }
        
        siteMapper.incrementClickCount(id);
        return siteMapper.selectById(id);
    }
    
    /**
     * 批量更新站点排序
     * 
     * @param siteIds 站点ID列表（按新的排序顺序）
     * @param categoryId 分类ID
     */
    @Transactional
    public void updateSitesOrder(List<Long> siteIds, Long categoryId) {
        for (int i = 0; i < siteIds.size(); i++) {
            Long siteId = siteIds.get(i);
            UpdateWrapper<NavigationSite> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", siteId);
            if (categoryId != null) {
                updateWrapper.eq("category_id", categoryId);
            }
            updateWrapper.set("sort_order", i + 1);
            updateWrapper.set("updated_at", LocalDateTime.now());
            siteMapper.update(null, updateWrapper);
        }
    }
    
    /**
     * 切换站点启用状态
     * 
     * @param id 站点ID
     * @return 更新后的站点
     */
    @Transactional
    public NavigationSite toggleEnabled(Long id) {
        NavigationSite site = siteMapper.selectById(id);
        if (site == null) {
            throw new RuntimeException("导航站点不存在");
        }
        
        UpdateWrapper<NavigationSite> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        updateWrapper.set("is_enabled", !site.getIsEnabled());
        updateWrapper.set("updated_at", LocalDateTime.now());
        
        siteMapper.update(null, updateWrapper);
        return siteMapper.selectById(id);
    }
    
    /**
     * 切换站点推荐状态
     * 
     * @param id 站点ID
     * @return 更新后的站点
     */
    @Transactional
    public NavigationSite toggleFeatured(Long id) {
        NavigationSite site = siteMapper.selectById(id);
        if (site == null) {
            throw new RuntimeException("导航站点不存在");
        }
        
        UpdateWrapper<NavigationSite> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        updateWrapper.set("is_featured", !site.getIsFeatured());
        updateWrapper.set("updated_at", LocalDateTime.now());
        
        siteMapper.update(null, updateWrapper);
        return siteMapper.selectById(id);
    }
    
    /**
     * 搜索站点（支持名称、描述、标签搜索）
     * 
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果
     */
    public Page<NavigationSite> searchSites(String keyword, int page, int size) {
        QueryWrapper<NavigationSite> queryWrapper = new QueryWrapper<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                .like("name", trimmedKeyword)
                .or().like("description", trimmedKeyword)
                .or().like("tags", trimmedKeyword)
            );
        }
        
        // 只查询启用的站点
        queryWrapper.eq("is_enabled", true);
        
        // 按点击次数降序，然后按排序权重升序
        queryWrapper.orderByDesc("click_count").orderByAsc("sort_order", "id");
        
        return siteMapper.selectPage(Page.of(page, size), queryWrapper);
    }
}