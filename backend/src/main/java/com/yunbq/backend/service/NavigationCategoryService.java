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
     * 分页查询导航分类
     * 
     * @param page 页码
     * @param size 每页大小
     * @param name 分类名称（模糊查询）
     * @param parentId 父级分类ID（null表示查询一级分类）
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
        if (parentId == null) {
            queryWrapper.isNull("parent_id");
        } else {
            queryWrapper.eq("parent_id", parentId);
        }
        
        // 启用状态过滤
        if (isEnabled != null) {
            queryWrapper.eq("is_enabled", isEnabled);
        }
        
        // 按排序权重和ID排序
        queryWrapper.orderByAsc("sort_order", "id");
        
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
        
        // 更新字段
        UpdateWrapper<NavigationCategory> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        
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
}