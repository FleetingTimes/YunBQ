package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.mapper.NavigationCategoryMapper;
import com.yunbq.backend.model.NavigationCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * 导航分类服务层
 * 提供导航分类的业务逻辑处理
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@Service
public class NavigationCategoryService {
    
    private final NavigationCategoryMapper categoryMapper;
    
    public NavigationCategoryService(NavigationCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }
    
    /**
     * 获取所有启用的一级分类
     * 
     * @return 一级分类列表
     */
    public List<NavigationCategory> getRootCategories() {
        return categoryMapper.selectRootCategories();
    }
    
    /**
     * 根据父级ID获取子分类
     * 
     * @param parentId 父级分类ID
     * @return 子分类列表
     */
    public List<NavigationCategory> getSubCategories(Long parentId) {
        return categoryMapper.selectByParentId(parentId);
    }
    
    /**
     * 获取所有启用的分类（包含一级和二级）
     * 
     * @return 所有启用分类列表
     */
    public List<NavigationCategory> getAllEnabledCategories() {
        return categoryMapper.selectAllEnabled();
    }

    /**
     * 获取所有分类（管理员导出使用）
     * 返回包含一级与二级分类的完整列表，按父级、排序权重与ID升序。
     * 
     * 设计说明：
     * - 导出功能通常需要全量数据，因此不对 is_enabled 做过滤；
     * - 按 parent_id、sort_order、id 排序可以保证层级与顺序稳定，便于对齐；
     * - 如需在导出时支持筛选条件（仅启用、仅某父级等），可在 Controller 扩展参数并在此处应用条件。
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
     * 
     * @param page 页码
     * @param size 每页大小
     * @param name 分类名称（模糊查询）
     * @param parentId 父级分类ID（null表示查询所有分类，包括一级和二级；具体值表示查询该父级下的子分类）
     * @param isEnabled 是否启用
     * @return 分页结果
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
     * 
     * @param category 导航分类信息
     * @return 创建的导航分类
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
        return category;
    }
    
    /**
     * 更新导航分类
     * 
     * @param id 分类ID
     * @param category 更新的分类信息
     * @return 更新后的导航分类
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
        return categoryMapper.selectById(id);
    }
    
    /**
     * 删除导航分类
     * 
     * @param id 分类ID
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
    }
    
    /**
     * 批量更新分类排序
     * 
     * @param categoryIds 分类ID列表（按新的排序顺序）
     * @param parentId 父级分类ID
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
    }
    
    /**
     * 切换分类启用状态
     * 
     * @param id 分类ID
     * @return 更新后的分类
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
        return categoryMapper.selectById(id);
    }

    /**
     * 根据导入记录匹配已存在的分类（用于去重与更新）
     *
     * 优先级：
     * 1) id 精确匹配；
     * 2) name + parentId 组合匹配（父级允许为 null）；
     * 3) 当 parentId 为空（根分类），按 name 唯一匹配。
     *
     * 返回：命中则返回已有分类，否则返回 null。
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
     *
     * 事务策略：
     * - 使用同一事务上下文，逐条处理并捕获异常，不因单条失败而整体回滚；
     * - 成功的记录会提交，失败的记录收集到 errors 列表中返回。
     *
     * 字段处理：
     * - 创建：填充 createdAt/updatedAt、isEnabled、sortOrder；
     * - 更新：仅更新非空字段，并刷新 updatedAt；允许更新 parentId（含 null）。
     *
     * 返回统计：total/created/updated/errors。
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

        return Map.of(
            "total", total,
            "created", created,
            "updated", updated,
            "errors", errors
        );
    }
}