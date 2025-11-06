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
import java.util.ArrayList;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导航站点服务（NavigationSiteService）
 * 职责：
 * - 管理导航站点的增删改查、分页检索、导入导出与排序；
 * - 提供按分类、标签、推荐、热门、用户维度的视图与查询；
 * - 保障站点与分类之间的关联合法性（创建/更新时校验 categoryId）。
 *
 * 设计要点：
 * - 排序策略：默认按 `sort_order`、`id` 升序，用于稳定展示与导出；
 * - 导出格式：CSV 字段转义与分类导出保持一致，便于互操作；
 * - 兼容说明：部分 Mapper 方法不支持多标签与 limit，这里在服务层做拆分与截断处理。
 *
 * 作者：YunBQ
 * 时间：2024-11-01
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
     * 获取所有站点（管理员导出使用）
     * 返回按排序权重升序、ID 升序的完整站点列表。
     *
     * 设计说明：
     * - 导出功能需要全量数据，不做启用状态筛选；
     * - 通过 MyBatis-Plus 的 QueryWrapper 指定排序；
     * - 如后续需要按时间、用户等维度筛选，可在 Controller 接口参数中扩展并在此处应用条件。
     *
     * 返回：
     * - 全量站点列表。
     */
    public java.util.List<NavigationSite> getAllSites() {
        return siteMapper.selectList(
            new QueryWrapper<NavigationSite>()
                .orderByAsc("sort_order", "id")
        );
    }

    /**
     * 将站点列表导出为 CSV 字符串
     * CSV 格式约定：
     * - 第一行是表头，字段顺序与 NavigationSite 属性一致（便于导入与对齐）；
     * - 文本字段统一进行转义：包含逗号、双引号、换行时使用双引号包裹，并将内部双引号替换为两个双引号；
     * - 空值输出为空字符串；
     * - 时间字段使用 toString()（ISO-8601），便于后续解析。
     *
     * 参数：
     * - sites：待导出的站点列表。
     *
     * 返回：
     * - CSV 字符串。
     */
    public String exportSitesToCsv(java.util.List<NavigationSite> sites) {
        StringBuilder sb = new StringBuilder();
        // 表头
        sb.append("id,categoryId,name,url,description,icon,faviconUrl,tags,sortOrder,isEnabled,isFeatured,clickCount,userId,createdAt,updatedAt\n");
        for (NavigationSite s : sites) {
            sb.append(csv(s.getId())).append(',')
              .append(csv(s.getCategoryId())).append(',')
              .append(csv(s.getName())).append(',')
              .append(csv(s.getUrl())).append(',')
              .append(csv(s.getDescription())).append(',')
              .append(csv(s.getIcon())).append(',')
              .append(csv(s.getFaviconUrl())).append(',')
              .append(csv(s.getTags())).append(',')
              .append(csv(s.getSortOrder())).append(',')
              .append(csv(s.getIsEnabled())).append(',')
              .append(csv(s.getIsFeatured())).append(',')
              .append(csv(s.getClickCount())).append(',')
              .append(csv(s.getUserId())).append(',')
              .append(csv(s.getCreatedAt())).append(',')
              .append(csv(s.getUpdatedAt())).append('\n');
        }
        return sb.toString();
    }

    /**
     * CSV 字段转义工具
     * 规则：
     * - null 输出为空；
     * - 将值转为字符串；
     * - 若包含逗号、双引号、换行（\n/\r），则使用双引号包裹，并将内部双引号替换为两个双引号。
     *
     * 参数：
     * - val：待转义的字段值。
     *
     * 返回：
     * - 已转义的安全 CSV 字段字符串。
     */
    private String csv(Object val) {
        if (val == null) return "";
        String str = String.valueOf(val);
        String escaped = str.replace("\"", "\"\"");
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
    
    /**
     * 根据分类ID获取站点列表
     * 参数：
     * - categoryId：分类 ID。
     * 返回：
     * - 站点列表（按排序权重升序）。
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
     * 参数：
     * - limit：返回数量上限。
     * 返回：
     * - 推荐站点列表（按点击次数降序）。
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
     * 参数：
     * - limit：返回数量上限。
     * 返回：
     * - 热门站点列表（按点击次数降序）。
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
     * 行为：
     * - 拆分多标签，仅使用第一个非空标签进行查询；
     * - 若 `limit>0`，对结果列表进行内存截断。
     *
     * 参数：
     * - tags：标签字符串（支持逗号/空白分隔多个关键词）；
     * - limit：返回数量上限。
     *
     * 返回：
     * - 站点列表（可能已截断）。
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
     * 参数：
     * - userId：用户 ID。
     * 返回：
     * - 用户站点列表（按创建时间倒序）。
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
     * 行为：
     * - 支持名称模糊匹配与分类/启用/推荐/用户维度过滤；
     * - 按 `sort_order`、`id` 升序返回分页结果。
     *
     * 参数：
     * - page/size：分页参数；
     * - name：站点名称（模糊查询）；
     * - categoryId：分类 ID；
     * - isEnabled：启用状态；
     * - isFeatured：是否推荐；
     * - userId：用户 ID（查询用户添加的站点）。
     *
     * 返回：
     * - {@link Page} 包装的 {@link NavigationSite} 列表与分页信息。
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
     * 参数：
     * - id：站点 ID。
     * 返回：
     * - 站点实体（可能为 null）。
     */
    public NavigationSite getById(Long id) {
        return siteMapper.selectById(id);
    }
    
    /**
     * 创建导航站点
     * 行为：
     * - 校验 `categoryId` 非空并存在；
     * - 填充创建与更新时间戳、默认排序权重、启用/推荐状态与点击次数；
     * - 持久化并返回创建后的实体。
     *
     * 参数：
     * - site：站点信息；
     * - userId：创建用户 ID（可为 null，表示系统）。
     *
     * 返回：
     * - 创建后的站点实体。
     *
     * 异常：
     * - RuntimeException：分类ID为空或指定分类不存在时抛出。
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
     * 行为：
     * - 校验站点存在性；
     * - 若传入 `categoryId`，校验对应分类存在；
     * - 更新非空字段并刷新 `updatedAt`。
     *
     * 参数：
     * - id：站点 ID；
     * - site：更新的站点信息。
     *
     * 返回：
     * - 更新后的站点实体。
     *
     * 异常：
     * - RuntimeException：站点不存在或指定分类不存在时抛出。
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
     * 根据导入记录匹配已存在的站点（用于去重与更新）
     *
     * 优先级：
     * 1) id 精确匹配；
     * 2) url 精确匹配（非空）；
     * 3) name + categoryId 组合匹配（均非空）。
     *
     * 返回：命中则返回已有站点，否则返回 null。
     */
    public NavigationSite findExistingByKeys(NavigationSite site) {
        if (site == null) return null;
        // 1) 主键匹配
        if (site.getId() != null) {
            NavigationSite byId = siteMapper.selectById(site.getId());
            if (byId != null) return byId;
        }
        // 2) URL 唯一匹配
        if (site.getUrl() != null && !site.getUrl().trim().isEmpty()) {
            NavigationSite byUrl = siteMapper.selectOne(new QueryWrapper<NavigationSite>().eq("url", site.getUrl()));
            if (byUrl != null) return byUrl;
        }
        // 3) 名称 + 分类组合匹配
        if (site.getName() != null && site.getCategoryId() != null) {
            NavigationSite byNameCat = siteMapper.selectOne(new QueryWrapper<NavigationSite>()
                .eq("name", site.getName())
                .eq("category_id", site.getCategoryId())
            );
            if (byNameCat != null) return byNameCat;
        }
        return null;
    }

    /**
     * 批量导入导航站点（逐条处理，汇总统计）
     *
     * 事务策略：
     * - 使用同一事务上下文，逐条处理并捕获异常，不因单条失败而整体回滚；
     * - 成功的记录会提交，失败的记录收集到 errors 列表中返回。
     *
     * 字段处理：
     * - 创建：填充 createdAt/updatedAt、userId、isEnabled、isFeatured、clickCount、sortOrder；
     * - 更新：仅更新非空字段，并刷新 updatedAt。
     *
     * 返回统计：total/created/updated/errors。
     */
    @Transactional
    public Map<String, Object> importSites(List<NavigationSite> sites, Long userId) {
        int total = sites == null ? 0 : sites.size();
        int created = 0;
        int updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        if (sites == null || sites.isEmpty()) {
            return Map.of("total", 0, "created", 0, "updated", 0, "errors", errors);
        }

        for (int i = 0; i < sites.size(); i++) {
            NavigationSite s = sites.get(i);
            try {
                NavigationSite existing = findExistingByKeys(s);
                if (existing != null) {
                    // 更新：仅更新传入的非空字段，刷新更新时间
                    UpdateWrapper<NavigationSite> uw = new UpdateWrapper<>();
                    uw.eq("id", existing.getId());
                    if (s.getCategoryId() != null) uw.set("category_id", s.getCategoryId());
                    if (s.getName() != null) uw.set("name", s.getName());
                    if (s.getUrl() != null) uw.set("url", s.getUrl());
                    if (s.getDescription() != null) uw.set("description", s.getDescription());
                    if (s.getIcon() != null) uw.set("icon", s.getIcon());
                    if (s.getFaviconUrl() != null) uw.set("favicon_url", s.getFaviconUrl());
                    if (s.getTags() != null) uw.set("tags", s.getTags());
                    if (s.getSortOrder() != null) uw.set("sort_order", s.getSortOrder());
                    if (s.getIsEnabled() != null) uw.set("is_enabled", s.getIsEnabled());
                    if (s.getIsFeatured() != null) uw.set("is_featured", s.getIsFeatured());
                    if (s.getClickCount() != null) uw.set("click_count", s.getClickCount());
                    uw.set("updated_at", LocalDateTime.now());
                    siteMapper.update(null, uw);
                    updated++;
                } else {
                    // 创建：分类ID必须存在
                    if (s.getCategoryId() == null) {
                        throw new RuntimeException("分类ID不能为空");
                    }
                    // 默认字段填充
                    LocalDateTime now = LocalDateTime.now();
                    if (s.getCreatedAt() == null) s.setCreatedAt(now);
                    s.setUpdatedAt(now);
                    s.setUserId(userId);
                    if (s.getIsEnabled() == null) s.setIsEnabled(true);
                    if (s.getIsFeatured() == null) s.setIsFeatured(false);
                    if (s.getClickCount() == null) s.setClickCount(0L);
                    // 默认排序权重：同分类下最大值 + 1
                    if (s.getSortOrder() == null) {
                        QueryWrapper<NavigationSite> qw = new QueryWrapper<>();
                        if (s.getCategoryId() != null) {
                            qw.eq("category_id", s.getCategoryId());
                        }
                        qw.orderByDesc("sort_order").last("LIMIT 1");
                        NavigationSite last = siteMapper.selectOne(qw);
                        int next = (last != null && last.getSortOrder() != null) ? last.getSortOrder() + 1 : 1;
                        s.setSortOrder(next);
                    }
                    siteMapper.insert(s);
                    created++;
                }
            } catch (Exception ex) {
                errors.add(Map.of(
                    "index", i,
                    "name", s != null ? s.getName() : null,
                    "url", s != null ? s.getUrl() : null,
                    "error", ex.getMessage()
                ));
            }
        }

        return Map.of(
            "total", total,
            "created", created,
            "updated", updated,
            "errors", errors
        );
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