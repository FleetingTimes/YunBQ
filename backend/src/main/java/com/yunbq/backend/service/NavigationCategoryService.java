package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.mapper.NavigationCategoryMapper;
import com.yunbq.backend.model.NavigationCategory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * 导航分类服务（NavigationCategoryService）
 * 职责：
 * - 管理导航分类的增删改查、分页检索与导入导出；
 * - 提供一级/二级分类的视图与排序管理；
 * - 对分类的启用状态与父子关系进行维护与校验。
 *
 * 设计要点：
 * - 分页与排序：默认按 `parent_id`、`sort_order`、`id` 排序，保证层级与顺序稳定；
 * - 父子关系：更新与导入时允许 `parentId=null` 表示根分类，并进行基本防御性校验；
 * - 导出格式：CSV 字段转义规则与站点导出保持一致，便于互操作与比对。
 *
 * 作者：YunBQ
 * 时间：2024-11-01
 */
@Service
public class NavigationCategoryService {
    
    private final NavigationCategoryMapper categoryMapper;
    // 注入 CacheManager：用于在分类写操作后执行精确的缓存清理
    private final CacheManager cacheManager;
    
    public NavigationCategoryService(NavigationCategoryMapper categoryMapper, CacheManager cacheManager) {
        this.categoryMapper = categoryMapper;
        this.cacheManager = cacheManager;
    }
    
    /**
     * 获取所有启用的一级分类
     * 返回仅包含根分类（`parent_id IS NULL`）且 `is_enabled=true` 的列表。
     *
     * 返回：
     * - 一级分类列表。
     */
    /**
     * 启用的一级分类（根分类）查询（带缓存）
     * 缓存名：`categories_root`
     * 说明：用于渲染导航栏的一级分类；读多写少场景下提升响应性能。
     */
    @Cacheable(cacheNames = "categories_root")
    public List<NavigationCategory> getRootCategories() {
        return categoryMapper.selectRootCategories();
    }
    
    /**
     * 根据父级ID获取子分类
     * 参数：
     * - parentId：父级分类 ID（不可为 null）。
     * 返回：
     * - 子分类列表（按排序与 ID 升序）。
     */
    public List<NavigationCategory> getSubCategories(Long parentId) {
        return categoryMapper.selectByParentId(parentId);
    }
    
    /**
     * 获取所有启用的分类（包含一级和二级）
     * 返回：
     * - 启用状态为 true 的全部分类列表。
     */
    /**
     * 启用的全部分类（含一级与二级）查询（带缓存）
     * 缓存名：`categories_enabled`
     * 说明：用于一次性加载完整导航树（/categories/all）。
     */
    @Cacheable(cacheNames = "categories_enabled")
    public List<NavigationCategory> getAllEnabledCategories() {
        return categoryMapper.selectAllEnabled();
    }

    /**
     * 获取所有分类（管理员导出使用）
     * 返回包含一级与二级分类的完整列表，按父级、排序权重与ID升序。
     *
     * 设计说明：
     * - 导出功能通常需要全量数据，因此不对 `is_enabled` 做过滤；
     * - 按 `parent_id`、`sort_order`、`id` 排序可以保证层级与顺序稳定，便于对齐；
     * - 如需在导出时支持筛选条件（仅启用、仅某父级等），可在 Controller 扩展参数并在此处应用条件。
     *
     * 返回：
     * - 全量分类列表。
     */
    public List<NavigationCategory> getAllCategories() {
        return categoryMapper.selectList(
            new QueryWrapper<NavigationCategory>()
                .orderByAsc("parent_id", "sort_order", "id")
        );
    }

    /**
     * 将分类列表导出为 CSV 字符串
     * 
     * CSV 格式约定：
     * - 第一行是表头，字段顺序与 NavigationCategory 属性一致（便于导入与比对）；
     * - 文本字段统一进行转义：包含逗号、双引号、换行时使用双引号包裹，并将内部双引号替换为两个双引号；
     * - 空值输出为空字符串；
     * - 时间字段使用 toString()（ISO-8601），与站点导出保持一致，便于解析。
     */
    public String exportCategoriesToCsv(List<NavigationCategory> categories) {
        StringBuilder sb = new StringBuilder();
        // 表头：与实体字段的 camelCase 保持一致
        sb.append("id,parentId,name,icon,description,sortOrder,isEnabled,createdAt,updatedAt\n");
        for (NavigationCategory c : categories) {
            sb.append(csv(c.getId())).append(',')
              .append(csv(c.getParentId())).append(',')
              .append(csv(c.getName())).append(',')
              .append(csv(c.getIcon())).append(',')
              .append(csv(c.getDescription())).append(',')
              .append(csv(c.getSortOrder())).append(',')
              .append(csv(c.getIsEnabled())).append(',')
              .append(csv(c.getCreatedAt())).append(',')
              .append(csv(c.getUpdatedAt())).append('\n');
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
     * 与 NavigationSiteService 中的实现保持一致，确保导出格式统一。
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
     * 分页查询导航分类
     * 行为：
     * - 名称模糊匹配 `name`；
     * - 当 `parentId` 为 null 时查询所有层级；为具体值时查询该父级的子分类；
     * - 按 `parent_id`、`sort_order`、`id` 升序返回分页结果。
     *
     * 参数：
     * - page/size：分页参数；
     * - name：分类名称（模糊查询）；
     * - parentId：父级分类ID（null 查询全部，非 null 查询该父级下子分类）；
     * - isEnabled：启用状态筛选。
     *
     * 返回：
     * - {@link Page} 包装的 {@link NavigationCategory} 列表与分页信息。
     */
    public Page<NavigationCategory> listCategories(int page, int size, String name, Long parentId, Boolean isEnabled) {
        QueryWrapper<NavigationCategory> queryWrapper = new QueryWrapper<>();
        
        // 名称模糊查询
        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("name", name.trim());
        }
        
        // 父级分类过滤
        // 修改逻辑：只有明确传递了parentId参数时才进行过滤
        // 如果parentId为null（未传递），则查询所有分类（包括一级和二级）
        // 如果parentId为具体值，则查询该父级下的子分类
        if (parentId != null) {
            queryWrapper.eq("parent_id", parentId);
        }
        // 注意：这里不再添加 isNull("parent_id") 条件，允许返回所有分类
        
        // 启用状态过滤
        if (isEnabled != null) {
            queryWrapper.eq("is_enabled", isEnabled);
        }
        
        // 按父级分类、排序权重和ID排序，确保一级分类在前，二级分类在后
        queryWrapper.orderByAsc("parent_id", "sort_order", "id");
        
        return categoryMapper.selectPage(Page.of(page, size), queryWrapper);
    }
    
    /**
     * 根据ID获取导航分类
     * 
     * @param id 分类ID
     * @return 导航分类
     */
    public NavigationCategory getById(Long id) {
        return categoryMapper.selectById(id);
    }
    
    /**
     * 创建导航分类
     * 行为：
     * - 填充创建与更新时间戳；
     * - 若未设置 `sortOrder`，同父级下取最大值+1；
     * - 若未设置 `isEnabled`，默认启用。
     *
     * 参数：
     * - category：分类信息（允许 `parentId=null` 作为根分类）。
     *
     * 返回：
     * - 创建后的分类实体。
     */
    @Transactional
    public NavigationCategory createCategory(NavigationCategory category) {
        // 设置创建时间和更新时间
        LocalDateTime now = LocalDateTime.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        
        // 如果未设置排序权重，设置为最大值+1
        if (category.getSortOrder() == null) {
            QueryWrapper<NavigationCategory> queryWrapper = new QueryWrapper<>();
            if (category.getParentId() == null) {
                queryWrapper.isNull("parent_id");
            } else {
                queryWrapper.eq("parent_id", category.getParentId());
            }
            queryWrapper.orderByDesc("sort_order").last("LIMIT 1");
            NavigationCategory lastCategory = categoryMapper.selectOne(queryWrapper);
            int nextSortOrder = (lastCategory != null && lastCategory.getSortOrder() != null) 
                ? lastCategory.getSortOrder() + 1 : 1;
            category.setSortOrder(nextSortOrder);
        }
        
        // 如果未设置启用状态，默认启用
        if (category.getIsEnabled() == null) {
            category.setIsEnabled(true);
        }
        
        categoryMapper.insert(category);
        // 写操作后：清理分类相关缓存，确保导航栏数据刷新
        clearCategoryCaches();
        return category;
    }
    
    /**
     * 更新导航分类
     * 行为：
     * - 校验分类存在性；
     * - 允许更新 `parentId`（含 null，表示设为根分类），并进行基本合法性校验（父级不可为自身、父级需存在）；
     * - 对传入的非空字段进行更新，刷新 `updatedAt`。
     *
     * 参数：
     * - id：分类ID；
     * - category：更新的分类信息。
     *
     * 返回：
     * - 更新后的分类实体。
     *
     * 异常：
     * - RuntimeException：分类不存在或父级非法时抛出。
     */
    @Transactional
    public NavigationCategory updateCategory(Long id, NavigationCategory category) {
        NavigationCategory existingCategory = categoryMapper.selectById(id);
        if (existingCategory == null) {
            throw new RuntimeException("导航分类不存在");
        }
        
        // ==================== 关键修复说明 ====================
        // 问题：编辑已有导航分类时，父ID（parentId）无法保存到数据库。
        // 根因：历史实现仅更新 name、icon、description、sortOrder、isEnabled 等字段，
        //      未对 parentId 进行持久化写入，导致父子关系更新丢失。
        // 方案：补充对 parentId 的校验与更新逻辑，允许将分类移动到其他父级，
        //      或者置为根分类（parentId=null）。
        // 安全性：
        //  - 禁止将分类设为它自身的父级（避免自循环）。
        //  - 当传入非空父ID时，校验父分类必须存在。
        //  - 暂不做更复杂的循环依赖检测（例如将父级设为其子孙），如需可在后续迭代补充。
        // ==================== 关键修复说明 ====================

        // 更新字段
        UpdateWrapper<NavigationCategory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        
        // === 父级分类更新（允许为 null 表示设为根分类） ===
        // 前端 AdminNavigation 页面在编辑时会显式提交 parentId（数字或 null），
        // 因此这里直接以请求体中的 parentId 为准进行写入。
        // 注意：如果调用方不提交该字段，Jackson 会将其反序列化为 null，
        //       在这种情况下也会被视为置为根分类。当前业务场景为管理员页面，
        //       表单总是提交该字段（包含 null），因此符合预期。
        if (category.getParentId() != null) {
            // 防御性校验：父级不可为自身
            if (id.equals(category.getParentId())) {
                throw new RuntimeException("父级分类不可为自身");
            }
            // 校验父级分类是否存在
            NavigationCategory parentCategory = categoryMapper.selectById(category.getParentId());
            if (parentCategory == null) {
                throw new RuntimeException("指定的父级分类不存在");
            }
            updateWrapper.set("parent_id", category.getParentId());
        } else {
            // 明确置为根分类（parent_id = NULL）
            updateWrapper.set("parent_id", null);
        }

        if (category.getName() != null) {
            updateWrapper.set("name", category.getName());
        }
        if (category.getIcon() != null) {
            updateWrapper.set("icon", category.getIcon());
        }
        if (category.getDescription() != null) {
            updateWrapper.set("description", category.getDescription());
        }
        if (category.getSortOrder() != null) {
            updateWrapper.set("sort_order", category.getSortOrder());
        }
        if (category.getIsEnabled() != null) {
            updateWrapper.set("is_enabled", category.getIsEnabled());
        }
        
        // 设置更新时间
        updateWrapper.set("updated_at", LocalDateTime.now());
        
        categoryMapper.update(null, updateWrapper);
        // 写操作后：清理分类相关缓存
        clearCategoryCaches();
        return categoryMapper.selectById(id);
    }
    
    /**
     * 删除导航分类
     * 行为：
     * - 校验分类存在性；
     * - 当存在子分类时拒绝删除；
     * - 可扩展校验是否有关联站点后再删除（目前 TODO）。
     *
     * 参数：
     * - id：分类 ID。
     *
     * 异常：
     * - RuntimeException：分类不存在或仍有子分类时抛出。
     */
    @Transactional
    public void deleteCategory(Long id) {
        NavigationCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("导航分类不存在");
        }
        
        // 检查是否有子分类
        QueryWrapper<NavigationCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", id);
        Long subCategoryCount = categoryMapper.selectCount(queryWrapper);
        if (subCategoryCount > 0) {
            throw new RuntimeException("该分类下还有子分类，无法删除");
        }
        
        // TODO: 检查是否有关联的站点，如果有则不允许删除
        // 这里需要注入NavigationSiteMapper来检查
        
        categoryMapper.deleteById(id);
        // 写操作后：清理分类相关缓存
        clearCategoryCaches();
    }
    
    /**
     * 批量更新分类排序
     * 行为：
     * - 按传入顺序重写同父级下的 `sort_order`，从 1 开始；
     * - 根据 `parentId` 为 null/非 null 区分根分类与子分类集合；
     * - 刷新 `updatedAt`。
     *
     * 参数：
     * - categoryIds：分类 ID 列表（表示新的排序顺序）；
     * - parentId：父级分类 ID（null 表示根分类集合）。
     */
    @Transactional
    public void updateCategoriesOrder(List<Long> categoryIds, Long parentId) {
        for (int i = 0; i < categoryIds.size(); i++) {
            Long categoryId = categoryIds.get(i);
            UpdateWrapper<NavigationCategory> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", categoryId);
            if (parentId == null) {
                updateWrapper.isNull("parent_id");
            } else {
                updateWrapper.eq("parent_id", parentId);
            }
            updateWrapper.set("sort_order", i + 1);
            updateWrapper.set("updated_at", LocalDateTime.now());
            categoryMapper.update(null, updateWrapper);
        }
        // 排序调整后：清理分类相关缓存
        clearCategoryCaches();
    }
    
    /**
     * 切换分类启用状态
     * 行为：
     * - 取反 `is_enabled` 并刷新 `updatedAt`。
     *
     * 参数：
     * - id：分类 ID。
     *
     * 返回：
     * - 更新后的分类实体。
     */
    @Transactional
    public NavigationCategory toggleEnabled(Long id) {
        NavigationCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("导航分类不存在");
        }
        
        UpdateWrapper<NavigationCategory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        updateWrapper.set("is_enabled", !category.getIsEnabled());
        updateWrapper.set("updated_at", LocalDateTime.now());
        
        categoryMapper.update(null, updateWrapper);
        // 状态切换后：清理分类相关缓存
        clearCategoryCaches();
        return categoryMapper.selectById(id);
    }

    /**
     * 根据导入记录匹配已存在的分类（用于去重与更新）
     * 优先级：
     * 1) id 精确匹配；
     * 2) name + parentId 组合匹配（父级允许为 null）；
     * 3) 当 parentId 为空（根分类），按 name 唯一匹配。
     *
     * 参数：
     * - c：用于匹配的分类信息。
     *
     * 返回：
     * - 命中则返回已有分类，否则返回 null。
     */
    public NavigationCategory findExistingByKeys(NavigationCategory c) {
        if (c == null) return null;
        // 1) 主键匹配
        if (c.getId() != null) {
            NavigationCategory byId = categoryMapper.selectById(c.getId());
            if (byId != null) return byId;
        }
        // 2) 名称 + 父级组合匹配（父级允许为 null）
        if (c.getName() != null && !c.getName().trim().isEmpty()) {
            QueryWrapper<NavigationCategory> qw = new QueryWrapper<>();
            qw.eq("name", c.getName().trim());
            if (c.getParentId() == null) {
                qw.isNull("parent_id");
            } else {
                qw.eq("parent_id", c.getParentId());
            }
            NavigationCategory byNameParent = categoryMapper.selectOne(qw);
            if (byNameParent != null) return byNameParent;
            // 3) 根分类按名称唯一（当 parentId 为空）
            if (c.getParentId() == null) {
                NavigationCategory byRootName = categoryMapper.selectOne(
                        new QueryWrapper<NavigationCategory>().eq("name", c.getName().trim()).isNull("parent_id")
                );
                if (byRootName != null) return byRootName;
            }
        }
        return null;
    }

    /**
     * 批量导入导航分类（逐条处理，汇总统计）
     * 事务策略：
     * - 使用同一事务上下文，逐条处理并捕获异常，不因单条失败而整体回滚；
     * - 成功的记录会提交，失败的记录收集到 errors 列表中返回。
     *
     * 字段处理：
     * - 创建：填充 `createdAt`/`updatedAt`、`isEnabled`、`sortOrder`；
     * - 更新：仅更新非空字段，并刷新 `updatedAt`；允许更新 `parentId`（含 null）。
     *
     * 参数：
     * - categories：待导入分类列表。
     *
     * 返回：
     * - 统计信息 Map：`total`、`created`、`updated`、`errors`（每项包含索引、名称与错误消息）。
     */
    @Transactional
    public Map<String, Object> importCategories(List<NavigationCategory> categories) {
        int total = categories == null ? 0 : categories.size();
        int created = 0;
        int updated = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        if (categories == null || categories.isEmpty()) {
            return Map.of("total", 0, "created", 0, "updated", 0, "errors", errors);
        }

        for (int i = 0; i < categories.size(); i++) {
            NavigationCategory c = categories.get(i);
            try {
                NavigationCategory existing = findExistingByKeys(c);
                if (existing != null) {
                    // 更新：仅更新传入的非空字段，刷新更新时间；允许 parentId 为 null 表示设为根分类
                    UpdateWrapper<NavigationCategory> uw = new UpdateWrapper<>();
                    uw.eq("id", existing.getId());
                    // 显式写入 parent_id，null 表示根分类
                    uw.set("parent_id", c.getParentId());
                    if (c.getName() != null) uw.set("name", c.getName());
                    if (c.getIcon() != null) uw.set("icon", c.getIcon());
                    if (c.getDescription() != null) uw.set("description", c.getDescription());
                    if (c.getSortOrder() != null) uw.set("sort_order", c.getSortOrder());
                    if (c.getIsEnabled() != null) uw.set("is_enabled", c.getIsEnabled());
                    uw.set("updated_at", LocalDateTime.now());
                    categoryMapper.update(null, uw);
                    updated++;
                } else {
                    // 创建：名称必填
                    if (c.getName() == null || c.getName().trim().isEmpty()) {
                        throw new RuntimeException("分类名称不能为空");
                    }
                    // 默认字段填充
                    LocalDateTime now = LocalDateTime.now();
                    if (c.getCreatedAt() == null) c.setCreatedAt(now);
                    c.setUpdatedAt(now);
                    if (c.getIsEnabled() == null) c.setIsEnabled(true);
                    // 默认排序权重：同父级下最大值 + 1（根分类时 parent_id IS NULL）
                    if (c.getSortOrder() == null) {
                        QueryWrapper<NavigationCategory> qw = new QueryWrapper<>();
                        if (c.getParentId() == null) {
                            qw.isNull("parent_id");
                        } else {
                            qw.eq("parent_id", c.getParentId());
                        }
                        qw.orderByDesc("sort_order").last("LIMIT 1");
                        NavigationCategory last = categoryMapper.selectOne(qw);
                        int next = (last != null && last.getSortOrder() != null) ? last.getSortOrder() + 1 : 1;
                        c.setSortOrder(next);
                    }
                    categoryMapper.insert(c);
                    created++;
                }
            } catch (Exception ex) {
                errors.add(Map.of(
                    "index", i,
                    "name", c != null ? c.getName() : null,
                    "message", ex.getMessage()
                ));
            }
        }

        // 导入执行完成：清理分类相关缓存，确保前端获取到最新的导航结构
        clearCategoryCaches();

        return Map.of(
            "total", total,
            "created", created,
            "updated", updated,
            "errors", errors
        );
    }

    // ==================== 缓存辅助方法 ====================
    /**
     * 清理与导航分类相关的缓存（统一入口）
     * 包含：
     * - categories_root：一级分类缓存（导航栏使用）
     * - categories_enabled：启用的全部分类缓存（含一级与二级）
     */
    private void clearCategoryCaches() {
        clearCache("categories_root");
        clearCache("categories_enabled");
    }

    /**
     * 清理指定名称的缓存
     * 若缓存未初始化（尚未命中），安全跳过。
     * @param cacheName 缓存名称
     */
    private void clearCache(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}