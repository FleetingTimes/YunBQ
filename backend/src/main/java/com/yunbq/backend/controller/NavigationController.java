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
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 导航控制器
 * 提供导航分类和站点的API接口
 * 
 * @author YunBQ
 * @since 2024-11-01
 */
@RestController
@RequestMapping("/api/navigation")
/**
 * 导航与站点信息控制器
 * 职责：
 * - 提供导航分类与站点集合的查询、导出、导入等接口；
 * - 支持按分类、标签、推荐、热门等维度的筛选视图。
 *
 * 分页/筛选/排序与边界：
 * - 列表接口按 `sort_order ASC, id ASC` 或点击量降序返回稳定顺序；
 * - 模糊匹配基于 `LIKE`，大小写敏感与通配行为由数据库决定；
 * - 分页参数 `page/size` 需由服务层设定上限（如 size ≤ 50），防止过大请求；
 * - 导入/导出接口遵循 CSV 约定，字段包含逗号/引号/换行时需转义。
 *
 * 安全：
 * - 公共查询开放；涉及写入与批量导入仅管理员可用（结合安全配置与 AdminController）。
 */
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

    /**
     * 批量导入导航站点（管理员接口）
     *
     * 使用说明：
     * - 前端以 multipart/form-data 上传文件，字段名为 `file`；文件内容为 JSON 数组，元素结构参考 NavigationSite。
     * - 去重策略（按优先级）：
     *   1) 若传入 `id`，则以主键匹配更新；
     *   2) 否则若传入非空 `url`，按 URL 唯一匹配更新；
     *   3) 否则若同时传入 `name` 与 `categoryId`，按 (name + categoryId) 组合匹配更新；
     *   4) 若未命中任何已有记录，则创建新站点。
     * - 字段填充：
     *   - 创建时：默认填充 `createdAt`=当前时间、`updatedAt`=当前时间、`isEnabled`=true、`isFeatured`=false、`clickCount`=0、`userId`=当前用户；
     *   - 更新时：仅更新请求体中非空字段，`updatedAt` 自动刷新为当前时间。
     * - 事务与容错：
     *   - 由服务层方法进行事务管理（逐条处理并捕获异常），保证成功的记录可提交，失败的记录收集到 `errors` 中返回；
     *   - 不因单条失败而整体回滚，便于批量导入的实际操作体验。
     */
    @PostMapping("/admin/sites/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importSites(@RequestParam("file") MultipartFile file) {
        Long userId = AuthUtil.currentUserId();
        if (userId == null) {
            // 鉴权保护：必须登录且具备 ADMIN 角色
            return ResponseEntity.status(401).body(Map.of("message", "未登录或权限不足"));
        }

        log.info("[NavigationController] POST /api/navigation/admin/sites/import called, filename={}", file.getOriginalFilename());
        try {
            // 使用 Spring 注入的 ObjectMapper 解析 JSON 数组，避免 LocalDateTime 的序列化/反序列化问题
            List<NavigationSite> sites = objectMapper.readValue(
                    file.getInputStream(), new TypeReference<List<NavigationSite>>() {}
            );

            // 委托服务层执行批量导入与去重逻辑，返回统计信息（total/created/updated/errors）
            Map<String, Object> result = siteService.importSites(sites, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 错误处理：记录详细异常信息并返回 500，包含简要错误信息便于前端提示
            log.error("Failed to import sites: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "message", "导入失败",
                    "error", e.getMessage()
            ));
        }
    }

    // ==================== 分类相关接口 ====================
    
    /**
     * 获取所有启用的一级分类
     *
     * 用途与语义：
     * - 返回所有 `isEnabled=true` 且 `parentId=null` 的根分类，用于首页或导航菜单渲染。
     * - 不包含二级分类；如需完整树结构请使用 `/categories/all`。
     *
     * 边界与排序：
     * - 当无任何启用分类时返回空列表（HTTP 200）。
     * - 按服务层默认排序（通常依据 `sortOrder`、`id`）。
     *
     * 异常与安全：
     * - 无需登录；只返回公开可见数据。
     * - 服务层异常将记录日志并以 400/500 转换；本方法正常返回 200 空列表以提升容错。
     *
     * @return 所有启用的根分类列表；可能为空。
     */
    @GetMapping("/categories")
    public ResponseEntity<List<NavigationCategory>> getRootCategories() {
        log.info("[NavigationController] GET /api/navigation/categories called");
        List<NavigationCategory> categories = categoryService.getRootCategories();
        return ResponseEntity.ok(categories);
    }
    
    /**
     * 获取指定分类的子分类
     *
     * 设计与行为：
     * - 返回 `parentId` 对应的直接子分类集合（仅一层，不递归）。
     * - 仅返回启用的子分类（`isEnabled=true`）。
     *
     * 参数与校验：
     * - 当 `parentId` 不存在或无启用子分类时，返回空列表（HTTP 200）。
     * - 不对 `parentId` 作负数校验，由服务层统一处理。
     *
     * 安全：无需登录；仅公开数据。
     *
     * @param parentId 父分类 ID（必填）。
     * @return 指定父分类的启用子分类列表；可能为空。
     */
    @GetMapping("/categories/{parentId}/children")
    public ResponseEntity<List<NavigationCategory>> getSubCategories(@PathVariable Long parentId) {
        log.info("[NavigationController] GET /api/navigation/categories/{}/children called", parentId);
        List<NavigationCategory> subCategories = categoryService.getSubCategories(parentId);
        return ResponseEntity.ok(subCategories);
    }
    
    /**
     * 获取所有启用的分类（包含一级和二级）
     *
     * 用途：
     * - 用于一次性加载完整导航树（根+子），便于前端缓存与渲染。
     *
     * 行为与边界：
     * - 仅返回 `isEnabled=true` 的分类。
     * - 若不存在启用分类，返回空列表（HTTP 200）。
     *
     * 安全：公开接口，无需登录。
     *
     * @return 启用分类（一级与二级）的完整集合；可能为空。
     */
    @GetMapping("/categories/all")
    public ResponseEntity<List<NavigationCategory>> getAllEnabledCategories() {
        log.info("[NavigationController] GET /api/navigation/categories/all called");
        List<NavigationCategory> categories = categoryService.getAllEnabledCategories();
        return ResponseEntity.ok(categories);
    }
    
    /**
     * 分页查询导航分类（管理员接口）
     *
     * 参数与筛选：
     * - `page` 页码（>=1，默认 1），`size` 每页大小（1–100，默认 10）。
     * - 可选筛选：`name`（模糊匹配）、`parentId`（父分类）、`isEnabled`（启用状态）。
     *
     * 返回与排序：
     * - 返回 `PageResult<NavigationCategory>`，包含 `records`、`total`、`current`、`size`。
     * - 排序由服务层确定，通常按 `sortOrder`、`id`。
     *
     * 安全与授权：
     * - 仅管理员可访问（`@PreAuthorize("hasRole('ADMIN')")`）。
     *
     * 异常策略：
     * - 非法分页参数由服务层归一化或抛出异常；统一返回 400。
     *
     * @param page 页码，默认 1。
     * @param size 每页大小，默认 10。
     * @param name 分类名称模糊匹配（可选）。
     * @param parentId 父分类 ID（可选）。
     * @param isEnabled 是否启用（可选）。
     * @return 分页结果（可能为空列表，但分页元信息完整）。
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
     * 根据 ID 获取导航分类
     *
     * 行为：
     * - 若存在返回 200 与实体；不存在返回 404。
     *
     * 安全：公开接口；仅返回基础分类信息。
     *
     * @param id 分类主键 ID。
     * @return 分类实体或 404。
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
     *
     * 请求体与校验：
     * - 必填字段：`name`；可选字段：`parentId`、`icon`、`description`、`sortOrder`、`isEnabled`。
     * - 重名策略：同一 `parentId` 下 `name` 应唯一；由服务层判重并抛出业务异常。
     *
     * 事务与返回：
     * - 服务层在单条事务内插入并填充默认字段（如 `createdAt`）。
     * - 成功返回创建后的实体；失败返回 400。
     *
     * 安全：仅管理员可调用。
     *
     * @param category 待创建分类的字段集合。
     * @return 创建成功的分类实体；失败返回 400。
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
     *
     * 行为与校验：
     * - 根据 `id` 局部更新非空字段；`updatedAt` 自动刷新。
     * - 重名与父子关系变更由服务层校验并抛出业务异常。
     *
     * 安全与事务：
     * - 管理员权限；单条更新在事务内执行。
     *
     * @param id 分类主键。
     * @param category 变更字段集合（仅非空字段生效）。
     * @return 更新后的实体；失败返回 400。
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
     *
     * 约束与行为：
     * - 若存在子分类或被站点引用，服务层需阻止删除并抛出业务异常（保护数据一致性）。
     * - 成功返回统一消息体 `{message: "分类删除成功"}`。
     *
     * 安全：仅管理员。
     *
     * @param id 分类主键。
     * @return 删除结果消息或错误原因（400）。
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
     *
     * 行为：
     * - 反转 `isEnabled` 状态；返回最新实体。
     * - 当分类不存在时返回 400（服务层抛出异常）。
     *
     * 安全：仅管理员。
     *
     * @param id 分类主键。
     * @return 最新分类实体或 400。
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
     *
     * 请求体结构：
     * - `categoryIds`: 需要按新顺序排列的分类 ID 列表（非空，唯一，长度>0）。
     * - `parentId`: 可选父分类 ID（为 null 表示根分类排序）。
     *
     * 行为：
     * - 按列表顺序更新 `sortOrder`；缺失或重复 ID 将被拒绝。
     * - 服务层应保证同一父级下的稳定排序与原子更新（事务）。
     *
     * 返回：统一成功消息；失败含错误信息（400）。
     *
     * 安全：仅管理员。
     *
     * @param request 包含 `categoryIds` 与可选 `parentId` 的映射。
     * @return 操作结果消息体。
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
     *
     * 格式与编码：
     * - `format=csv|json`，默认 `csv`；CSV 添加 UTF-8 BOM 以避免 Excel 乱码。
     * - 返回 `Content-Disposition: attachment` 指示下载，文件名随格式变化。
     *
     * 数据范围与排序：
     * - 导出包含所有分类（含禁用），排序由服务层确定。
     *
     * 异常策略：
     * - 序列化/IO 异常记录日志并返回 400（字节信息）。
     *
     * 安全：仅管理员。
     *
     * @param format 导出格式，支持 `csv`（默认）与 `json`。
     * @return 文件字节流响应（CSV/JSON）。
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

    /**
     * 导入导航分类（管理员接口）
     * 路径：POST /api/navigation/admin/categories/import
     *
     * 设计说明：
     * - 前端以 multipart/form-data 上传文件字段名为 `file`；
     * - 当前仅支持 JSON 格式的分类数组导入（format=json），CSV 可在后续扩展；
     * - 去重规则由服务层实现：优先 `id`，其次 `name+parentId`，最后当 `parentId` 为空时按 `name`（根分类唯一）。
     * - 导入行为：命中则更新非空字段并刷新 `updatedAt`；未命中则创建并填充默认字段。
     * - 返回统计信息：`total/created/updated/errors`（逐条错误包含 `index/name/message`）。
     *
     * 安全与事务：仅管理员；服务层以逐条处理+收集错误的方式保证最大化提交。
     */
    @PostMapping("/admin/categories/import")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> importCategories(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", defaultValue = "json") String format
    ) {
        log.info("[NavigationController] POST /api/navigation/admin/categories/import called, format={}", format);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "上传文件为空"
            ));
        }

        final String lower = format == null ? "json" : format.toLowerCase();
        try {
            if (!"json".equals(lower)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "暂不支持的导入格式：" + format
                ));
            }

            // 读取并解析 JSON：期待内容为 NavigationCategory 数组
            String json = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<NavigationCategory> categories = objectMapper.readValue(json, new TypeReference<List<NavigationCategory>>() {});

            // 委托服务层进行批量导入与去重
            Map<String, Object> result = categoryService.importCategories(categories);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to import categories: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "message", e.getMessage()
            ));
        }
    }
    
    // ==================== 站点相关接口 ====================
    
    /**
     * 根据分类 ID 获取站点列表
     *
     * 行为：返回指定分类下启用站点集合；不包含其他筛选。
     * 边界：分类不存在或无站点时返回空列表（HTTP 200）。
     * 安全：公开接口。
     *
     * @param categoryId 分类主键。
     * @return 站点列表；可能为空。
     */
    @GetMapping("/sites/category/{categoryId}")
    public ResponseEntity<List<NavigationSite>> getSitesByCategory(@PathVariable Long categoryId) {
        log.info("[NavigationController] GET /api/navigation/sites/category/{} called", categoryId);
        List<NavigationSite> sites = siteService.getSitesByCategory(categoryId);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 获取推荐站点
     *
     * 行为：返回 `isFeatured=true` 的启用站点，按服务层默认排序且限制数量。
     * 边界：`limit<=0` 归一化为默认值；无推荐站点返回空列表（HTTP 200）。
     * 安全：公开接口。
     *
     * @param limit 返回的最大条数（默认 10）。
     * @return 推荐站点列表；可能为空。
     */
    @GetMapping("/sites/featured")
    public ResponseEntity<List<NavigationSite>> getFeaturedSites(@RequestParam(defaultValue = "10") int limit) {
        log.info("[NavigationController] GET /api/navigation/sites/featured called, limit={}", limit);
        List<NavigationSite> sites = siteService.getFeaturedSites(limit);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 获取热门站点
     *
     * 行为：依据点击数等指标返回热门站点集合，数量受 `limit` 限制。
     * 边界：`limit<=0` 归一化为默认值；无热门数据返回空列表（HTTP 200）。
     * 安全：公开接口。
     *
     * @param limit 返回的最大条数（默认 10）。
     * @return 热门站点列表；可能为空。
     */
    @GetMapping("/sites/popular")
    public ResponseEntity<List<NavigationSite>> getPopularSites(@RequestParam(defaultValue = "10") int limit) {
        log.info("[NavigationController] GET /api/navigation/sites/popular called, limit={}", limit);
        List<NavigationSite> sites = siteService.getPopularSites(limit);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 搜索站点
     *
     * 参数：
     * - `keyword` 搜索关键字（必填，去除前后空格后进行模糊匹配）。
     * - `page` 页码（>=1，默认 1），`size` 每页大小（1–100，默认 10）。
     *
     * 行为与返回：
     * - 返回分页结构 `PageResult<NavigationSite>`；当关键字为空时返回空结果或服务层归一化处理。
     *
     * 安全：公开接口。
     *
     * @param keyword 关键字。
     * @param page 页码。
     * @param size 每页大小。
     * @return 符合条件的分页结果。
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
     *
     * 行为：
     * - 支持以逗号分隔的多标签输入（`tags=java,spring`），服务层按包含任一或全部标签实现（视实现）。
     * - 限制返回数量为 `limit`，默认 10。
     *
     * 安全：公开接口。
     *
     * @param tags 逗号分隔的标签字符串。
     * @param limit 最大返回条目数。
     * @return 匹配标签的站点集合；可能为空。
     */
    @GetMapping("/sites/tags/{tags}")
    public ResponseEntity<List<NavigationSite>> searchByTags(@PathVariable String tags, @RequestParam(defaultValue = "10") int limit) {
        log.info("[NavigationController] GET /api/navigation/sites/tags/{} called, limit={}", tags, limit);
        List<NavigationSite> sites = siteService.searchByTags(tags, limit);
        return ResponseEntity.ok(sites);
    }
    
    /**
     * 分页查询导航站点（管理员接口）
     *
     * 参数与筛选：
     * - `page` 页码（>=1，默认 1），`size` 每页大小（1–100，默认 10）。
     * - 可选筛选：`name`（模糊）、`categoryId`、`isEnabled`、`isFeatured`、`userId`（创建者）。
     *
     * 返回与排序：
     * - 返回 `PageResult<NavigationSite>`，包含分页元信息。
     *
     * 安全与授权：仅管理员。
     *
     * 异常策略：非法参数统一转为 400；服务层异常记录日志。
     *
     * @param page 页码。
     * @param size 每页大小。
     * @param name 名称模糊匹配（可选）。
     * @param categoryId 分类 ID（可选）。
     * @param isEnabled 是否启用（可选）。
     * @param isFeatured 是否推荐（可选）。
     * @param userId 创建者用户 ID（可选）。
     * @return 分页结果。
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
     * 根据 ID 获取导航站点
     *
     * 行为：存在返回 200 与实体；不存在返回 404。
     * 安全：公开接口。
     *
     * @param id 站点主键。
     * @return 站点实体或 404。
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
     *
     * 请求体与校验：
     * - 必填：`name`、`url`、`categoryId`；可选：`description`、`tags`、`icon`、`sortOrder`、`isEnabled`、`isFeatured`。
     * - 去重：按 `id`/`url`/`(name+categoryId)` 优先级判重；服务层抛出业务异常。
     *
     * 事务与返回：单条插入事务，成功返回实体；失败 400。
     * 安全：仅管理员。
     *
     * @param site 站点字段集合。
     * @return 创建后的站点实体或错误。
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
     *
     * 行为与校验：
     * - 同管理员创建逻辑，但 `userId` 取当前登录用户，默认 `isEnabled=true`、`isFeatured=false`。
     * - 未登录返回 401；字段校验失败返回 400。
     *
     * 安全：需要登录；速率限制与反垃圾由服务层或网关层负责（如有）。
     *
     * @param site 站点字段集合。
     * @return 创建后的站点实体或错误。
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
     *
     * 行为：
     * - 根据 `id` 局部更新非空字段；校验重复与引用完整性由服务层处理。
     *
     * 安全与事务：仅管理员；单条事务更新。
     *
     * @param id 站点主键。
     * @param site 变化字段集合（仅非空字段生效）。
     * @return 更新后的实体或错误（400）。
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
     *
     * 约束：
     * - 若站点被其他资源引用（如收藏/日志），服务层应阻止删除并返回业务异常。
     *
     * 安全：仅管理员。
     *
     * @param id 站点主键。
     * @return 删除结果消息或错误（400）。
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
     *
     * 行为：
     * - 将 `clickCount` +1；返回更新后的实体。
     * - 若站点不存在或更新失败，返回 400。
     *
     * 安全：公开接口；若存在频控策略应在服务层或网关层实现。
     *
     * @param id 站点主键。
     * @return 更新后的站点实体或错误。
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
     *
     * 行为：反转 `isEnabled`，返回最新实体；不存在返回 400。
     * 安全：仅管理员。
     *
     * @param id 站点主键。
     * @return 最新站点实体或错误（400）。
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
     *
     * 行为：反转 `isFeatured`，返回最新实体；不存在返回 400。
     * 安全：仅管理员。
     *
     * @param id 站点主键。
     * @return 最新站点实体或错误（400）。
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
     * 格式与编码：
     * - `format=csv|json`，默认 `csv`；CSV 添加 UTF-8 BOM；JSON 使用标准数组格式。
     * - 返回 `Content-Disposition: attachment` 指示下载。
     *
     * 数据范围与排序：导出包含所有站点（含禁用），排序由服务层确定。
     *
     * 异常策略：序列化/IO 异常返回 400（字节信息），并记录日志。
     *
     * 安全：仅管理员。
     *
     * @param format 导出格式，`csv`（默认）或 `json`。
     * @return 文件字节流响应（CSV/JSON）。
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
     *
     * 请求体结构：
     * - `siteIds`: 需要按新顺序排列的站点 ID 列表（非空，唯一）。
     * - `categoryId`: 可选分类 ID；为 null 表示跨分类或全局排序视实现。
     *
     * 行为与事务：服务层在事务内按列表顺序更新 `sortOrder`；参数非法返回 400。
     * 安全：仅管理员。
     *
     * @param request 包含 `siteIds` 与可选 `categoryId` 的映射。
     * @return 操作结果消息体。
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
     *
     * 行为：返回当前登录用户创建的站点集合。
     * 边界：未登录返回 401；无数据返回空列表（200）。
     * 安全：需要登录；权限基于 `AuthUtil.currentUserId()`。
     *
     * @return 当前用户的站点列表；未登录返回 401。
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