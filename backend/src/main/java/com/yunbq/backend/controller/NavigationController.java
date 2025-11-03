package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.PageResult;
import com.yunbq.backend.model.NavigationCategory;
import com.yunbq.backend.model.NavigationSite;
import com.yunbq.backend.service.NavigationCategoryService;
import com.yunbq.backend.service.NavigationSiteService;
import com.yunbq.backend.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 导航控制器
 * 提供导航分类和站点的API接口
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@RestController
@RequestMapping("/api/navigation")
public class NavigationController {
    
    private static final Logger log = LoggerFactory.getLogger(NavigationController.class);
    
    private final NavigationCategoryService categoryService;
    private final NavigationSiteService siteService;
    // 使用 Spring 管理的 ObjectMapper（已注册 JavaTimeModule 等），避免 LocalDateTime 序列化失败
    private final ObjectMapper objectMapper;
    
    public NavigationController(
            NavigationCategoryService categoryService,
            NavigationSiteService siteService,
            ObjectMapper objectMapper
    ) {
        this.categoryService = categoryService;
        this.siteService = siteService;
        this.objectMapper = objectMapper;
    }
    
    // ==================== 分类相关接口 ====================
    
    /**
     * 获取所有启用的一级分类
     */
    @GetMapping("/categories")
    public ResponseEntity<List<NavigationCategory>> getRootCategories() {
        log.info("[NavigationController] GET /api/navigation/categories called");
        List<NavigationCategory> categories = categoryService.getRootCategories();
        return ResponseEntity.ok(categories);
    }
    
    /**
     * 获取指定分类的子分类
     */
    @GetMapping("/categories/{parentId}/children")
    public ResponseEntity<List<NavigationCategory>> getSubCategories(@PathVariable Long parentId) {
        log.info("[NavigationController] GET /api/navigation/categories/{}/children called", parentId);
        List<NavigationCategory> subCategories = categoryService.getSubCategories(parentId);
        return ResponseEntity.ok(subCategories);
    }
    
    /**
     * 获取所有启用的分类（包含一级和二级）
     */
    @GetMapping("/categories/all")
    public ResponseEntity<List<NavigationCategory>> getAllEnabledCategories() {
        log.info("[NavigationController] GET /api/navigation/categories/all called");
        List<NavigationCategory> categories = categoryService.getAllEnabledCategories();
        return ResponseEntity.ok(categories);
    }
    
    /**
     * 分页查询导航分类（管理员接口）
     */
    @GetMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<NavigationCategory>> listCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean isEnabled) {
        log.info("[NavigationController] GET /api/navigation/admin/categories called, page={}, size={}, name={}, parentId={}, isEnabled={}", 
                page, size, name, parentId, isEnabled);
        Page<NavigationCategory> result = categoryService.listCategories(page, size, name, parentId, isEnabled);
        PageResult<NavigationCategory> response = new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 根据ID获取导航分类
     */
    @GetMapping("/categories/{id}")
    public ResponseEntity<NavigationCategory> getCategoryById(@PathVariable Long id) {
        log.info("[NavigationController] GET /api/navigation/categories/{} called", id);
        NavigationCategory category = categoryService.getById(id);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(category);
    }
    
    /**
     * 创建导航分类（管理员接口）
     */
    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationCategory> createCategory(@Valid @RequestBody NavigationCategory category) {
        log.info("[NavigationController] POST /api/navigation/admin/categories called");
        log.info("[NavigationController] 接收到的分类数据: name={}, parentId={}, icon={}, description={}, sortOrder={}, isEnabled={}", 
                category.getName(), category.getParentId(), category.getIcon(), 
                category.getDescription(), category.getSortOrder(), category.getIsEnabled());
        try {
            NavigationCategory created = categoryService.createCategory(category);
            log.info("[NavigationController] 创建成功，返回数据: id={}, name={}, parentId={}", 
                    created.getId(), created.getName(), created.getParentId());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Failed to create category: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 更新导航分类（管理员接口）
     */
    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationCategory> updateCategory(@PathVariable Long id, @Valid @RequestBody NavigationCategory category) {
        log.info("[NavigationController] PUT /api/navigation/admin/categories/{} called", id);
        try {
            NavigationCategory updated = categoryService.updateCategory(id, category);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update category {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 删除导航分类（管理员接口）
     */
    @DeleteMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long id) {
        log.info("[NavigationController] DELETE /api/navigation/admin/categories/{} called", id);
        try {
            categoryService.deleteCategory(id);
            return ResponseEntity.ok(Map.of("message", "分类删除成功"));
        } catch (Exception e) {
            log.error("Failed to delete category {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    /**
     * 切换分类启用状态（管理员接口）
     */
    @PatchMapping("/admin/categories/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationCategory> toggleCategoryEnabled(@PathVariable Long id) {
        log.info("[NavigationController] PATCH /api/navigation/admin/categories/{}/toggle called", id);
        try {
            NavigationCategory updated = categoryService.toggleEnabled(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to toggle category {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 批量更新分类排序（管理员接口）
     */
    @PutMapping("/admin/categories/order")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateCategoriesOrder(@RequestBody Map<String, Object> request) {
        log.info("[NavigationController] PUT /api/navigation/admin/categories/order called");
        try {
            @SuppressWarnings("unchecked")
            List<Long> categoryIds = (List<Long>) request.get("categoryIds");
            Long parentId = request.get("parentId") != null ? Long.valueOf(request.get("parentId").toString()) : null;
            categoryService.updateCategoriesOrder(categoryIds, parentId);
            return ResponseEntity.ok(Map.of("message", "排序更新成功"));
        } catch (Exception e) {
            log.error("Failed to update categories order: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 导出全部分类（管理员接口）
     * 支持 CSV 和 JSON 格式导出
     * 
     * @param format 导出格式，支持 csv（默认）和 json
     * @return 导出的文件数据
     */
    @GetMapping("/admin/categories/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportAllCategories(@RequestParam(defaultValue = "csv") String format) {
        log.info("[NavigationController] GET /api/navigation/admin/categories/export called, format={}", format);
        try {
            // 获取所有分类数据（包括一级和二级分类）
            List<NavigationCategory> categories = categoryService.getAllCategories();
            final String lower = format == null ? "csv" : format.toLowerCase();
            
            if ("json".equals(lower)) {
                // JSON 导出：使用 Jackson 序列化为字节数组
                // 说明：
                // - 以二进制（octet-stream）形式返回，避免部分浏览器或前端库对 application/json 的特殊处理；
                // - 通过 Content-Disposition: attachment 指示下载，文件名为 categories.json。
                // 使用 Spring 注入的 objectMapper，确保已注册 JavaTimeModule 支持 LocalDateTime
                byte[] json = objectMapper.writeValueAsBytes(categories);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=categories.json")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(json);
            } else {
                // CSV 导出：由服务层生成带表头的 CSV 文本
                // 乱码修复：
                // - 为了在 Windows Excel 中正确识别 UTF-8 中文，需在文本前添加 BOM（\uFEFF）；
                // - 同时设置 Content-Type 为 text/csv; charset=UTF-8。
                String csv = categoryService.exportCategoriesToCsv(categories);
                String withBom = "\uFEFF" + csv; // 前置 UTF-8 BOM
                byte[] bytes = withBom.getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=categories.csv")
                        .contentType(MediaType.valueOf("text/csv; charset=UTF-8"))
                        .body(bytes);
            }
        } catch (Exception e) {
            log.error("Failed to export categories: {}", e.getMessage());
            return ResponseEntity.badRequest().body("导出失败".getBytes(StandardCharsets.UTF_8));
        }
    }
    
    // ==================== 站点相关接口 ====================
    
    /**
     * 根据分类ID获取站点列表
     */
    @GetMapping("/sites/category/{categoryId}")
    public ResponseEntity<List<NavigationSite>> getSitesByCategory(@PathVariable Long categoryId) {
        log.info("[NavigationController] GET /api/navigation/sites/category/{} called", categoryId);
        List<NavigationSite> sites = siteService.getSitesByCategory(categoryId);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 获取推荐站点
     */
    @GetMapping("/sites/featured")
    public ResponseEntity<List<NavigationSite>> getFeaturedSites(@RequestParam(defaultValue = "10") int limit) {
        log.info("[NavigationController] GET /api/navigation/sites/featured called, limit={}", limit);
        List<NavigationSite> sites = siteService.getFeaturedSites(limit);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 获取热门站点
     */
    @GetMapping("/sites/popular")
    public ResponseEntity<List<NavigationSite>> getPopularSites(@RequestParam(defaultValue = "10") int limit) {
        log.info("[NavigationController] GET /api/navigation/sites/popular called, limit={}", limit);
        List<NavigationSite> sites = siteService.getPopularSites(limit);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 搜索站点
     */
    @GetMapping("/sites/search")
    public ResponseEntity<PageResult<NavigationSite>> searchSites(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("[NavigationController] GET /api/navigation/sites/search called, keyword={}, page={}, size={}", keyword, page, size);
        Page<NavigationSite> result = siteService.searchSites(keyword, page, size);
        PageResult<NavigationSite> response = new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 根据标签搜索站点
     */
    @GetMapping("/sites/tags/{tags}")
    public ResponseEntity<List<NavigationSite>> searchByTags(@PathVariable String tags, @RequestParam(defaultValue = "10") int limit) {
        log.info("[NavigationController] GET /api/navigation/sites/tags/{} called, limit={}", tags, limit);
        List<NavigationSite> sites = siteService.searchByTags(tags, limit);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 分页查询导航站点（管理员接口）
     */
    @GetMapping("/admin/sites")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<NavigationSite>> listSites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean isEnabled,
            @RequestParam(required = false) Boolean isFeatured,
            @RequestParam(required = false) Long userId) {
        log.info("[NavigationController] GET /api/navigation/admin/sites called, page={}, size={}, name={}, categoryId={}, isEnabled={}, isFeatured={}, userId={}", 
                page, size, name, categoryId, isEnabled, isFeatured, userId);
        Page<NavigationSite> result = siteService.listSites(page, size, name, categoryId, isEnabled, isFeatured, userId);
        PageResult<NavigationSite> response = new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
        return ResponseEntity.ok(response);
    }
    
    /**
     * 根据ID获取导航站点
     */
    @GetMapping("/sites/{id}")
    public ResponseEntity<NavigationSite> getSiteById(@PathVariable Long id) {
        log.info("[NavigationController] GET /api/navigation/sites/{} called", id);
        NavigationSite site = siteService.getById(id);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(site);
    }
    
    /**
     * 创建导航站点（管理员接口）
     */
    @PostMapping("/admin/sites")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationSite> createSite(@Valid @RequestBody NavigationSite site) {
        Long userId = AuthUtil.currentUserId();
        log.info("[NavigationController] POST /api/navigation/admin/sites called, name={}, userId={}", site.getName(), userId);
        try {
            NavigationSite created = siteService.createSite(site, userId);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Failed to create site: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 用户创建导航站点（需要登录）
     */
    @PostMapping("/sites")
    public ResponseEntity<NavigationSite> createUserSite(@Valid @RequestBody NavigationSite site) {
        Long userId = AuthUtil.currentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        log.info("[NavigationController] POST /api/navigation/sites called, name={}, userId={}", site.getName(), userId);
        try {
            NavigationSite created = siteService.createSite(site, userId);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Failed to create user site: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 更新导航站点（管理员接口）
     */
    @PutMapping("/admin/sites/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationSite> updateSite(@PathVariable Long id, @Valid @RequestBody NavigationSite site) {
        log.info("[NavigationController] PUT /api/navigation/admin/sites/{} called", id);
        try {
            NavigationSite updated = siteService.updateSite(id, site);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update site {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 删除导航站点（管理员接口）
     */
    @DeleteMapping("/admin/sites/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteSite(@PathVariable Long id) {
        log.info("[NavigationController] DELETE /api/navigation/admin/sites/{} called", id);
        try {
            siteService.deleteSite(id);
            return ResponseEntity.ok(Map.of("message", "站点删除成功"));
        } catch (Exception e) {
            log.error("Failed to delete site {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    /**
     * 增加站点点击次数
     */
    @PostMapping("/sites/{id}/click")
    public ResponseEntity<NavigationSite> incrementClickCount(@PathVariable Long id) {
        log.info("[NavigationController] POST /api/navigation/sites/{}/click called", id);
        try {
            NavigationSite updated = siteService.incrementClickCount(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to increment click count for site {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 切换站点启用状态（管理员接口）
     */
    @PatchMapping("/admin/sites/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationSite> toggleSiteEnabled(@PathVariable Long id) {
        log.info("[NavigationController] PATCH /api/navigation/admin/sites/{}/toggle called", id);
        try {
            NavigationSite updated = siteService.toggleEnabled(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to toggle site {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 切换站点推荐状态（管理员接口）
     */
    @PatchMapping("/admin/sites/{id}/featured")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NavigationSite> toggleSiteFeatured(@PathVariable Long id) {
        log.info("[NavigationController] PATCH /api/navigation/admin/sites/{}/featured called", id);
        try {
            NavigationSite updated = siteService.toggleFeatured(id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to toggle site featured {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 一键导出所有站点信息（管理员接口）
     *
     * 功能说明：
     * - 支持导出为 CSV 或 JSON（通过 format 参数指定，默认 csv）；
     * - 设置 Content-Disposition 为附件下载，文件名根据格式自动切换；
     * - CSV 使用 UTF-8 编码，包含表头；JSON 使用标准数组格式。
     */
    @GetMapping("/admin/sites/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportAllSites(@RequestParam(defaultValue = "csv") String format) {
        log.info("[NavigationController] GET /api/navigation/admin/sites/export called, format={}", format);
        try {
            List<NavigationSite> sites = siteService.getAllSites();
            final String lower = format == null ? "csv" : format.toLowerCase();
            if ("json".equals(lower)) {
                // JSON 导出：使用 Jackson 序列化为字节数组
                // 说明：
                // - 以二进制（octet-stream）形式返回，避免部分浏览器或前端库对 application/json 的特殊处理；
                // - 通过 Content-Disposition: attachment 指示下载，文件名为 sites.json。
                // 使用 Spring 注入的 objectMapper，确保已注册 JavaTimeModule 支持 LocalDateTime
                byte[] json = objectMapper.writeValueAsBytes(sites);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=sites.json")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(json);
            } else {
                // CSV 导出：由服务层生成带表头的 CSV 文本
                // 乱码修复：
                // - 为了在 Windows Excel 中正确识别 UTF-8 中文，需在文本前添加 BOM（\uFEFF）；
                // - 同时设置 Content-Type 为 text/csv; charset=UTF-8。
                String csv = siteService.exportSitesToCsv(sites);
                String withBom = "\uFEFF" + csv; // 前置 UTF-8 BOM
                byte[] bytes = withBom.getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=sites.csv")
                        .contentType(MediaType.valueOf("text/csv; charset=UTF-8"))
                        .body(bytes);
            }
        } catch (Exception e) {
            log.error("Failed to export sites: {}", e.getMessage());
            return ResponseEntity.badRequest().body("导出失败".getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * 批量更新站点排序（管理员接口）
     */
    @PutMapping("/admin/sites/order")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateSitesOrder(@RequestBody Map<String, Object> request) {
        log.info("[NavigationController] PUT /api/navigation/admin/sites/order called");
        try {
            @SuppressWarnings("unchecked")
            List<Long> siteIds = (List<Long>) request.get("siteIds");
            Long categoryId = request.get("categoryId") != null ? Long.valueOf(request.get("categoryId").toString()) : null;
            siteService.updateSitesOrder(siteIds, categoryId);
            return ResponseEntity.ok(Map.of("message", "排序更新成功"));
        } catch (Exception e) {
            log.error("Failed to update sites order: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    /**
     * 获取用户添加的站点（需要登录）
     */
    @GetMapping("/sites/my")
    public ResponseEntity<List<NavigationSite>> getUserSites() {
        Long userId = AuthUtil.currentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        log.info("[NavigationController] GET /api/navigation/sites/my called, userId={}", userId);
        List<NavigationSite> sites = siteService.getUserSites(userId);
        return ResponseEntity.ok(sites);
    }
}