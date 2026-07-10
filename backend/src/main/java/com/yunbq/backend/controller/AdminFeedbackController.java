package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.PageResult;
import com.yunbq.backend.mapper.FeedbackSubmissionMapper;
import com.yunbq.backend.model.FeedbackSubmission;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 反馈管理后台控制器（管理员专用）
 * 职责与设计要点：
 * - 仅限 ADMIN 角色访问，采用路径级（/api/admin/**）+ 方法级 @PreAuthorize 双重保护
 * - 提供反馈列表（按类型/状态/关键词筛选）、单条详情、状态更新功能
 * - 排序规则：按创建时间倒序，优先展示最新反馈
 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackSubmissionMapper mapper;

    /**
     * 构造函数注入：依赖注入反馈提交数据访问对象
     * @param mapper 反馈提交 Mapper
     */
    public AdminFeedbackController(FeedbackSubmissionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 反馈列表（分页查询，支持筛选）
     * 筛选维度：
     * - type: 反馈类型（issue/suggest）
     * - status: 状态（open/closed/in_progress 等）
     * - q: 关键词（对标题/描述做模糊匹配）
     * 排序：按 created_at 倒序
     * @param type 反馈类型（可选）
     * @param status 状态（可选）
     * @param q 搜索关键词（可选）
     * @param page 页码，默认 1
     * @param size 每页条数，默认 20
     * @return 分页结果（PageResult 包装反馈列表）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<FeedbackSubmission>> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size
    ){
        QueryWrapper<FeedbackSubmission> qw = new QueryWrapper<>();
        // 按反馈类型精确匹配
        if (type != null && !type.isBlank()) qw.eq("type", type);
        // 按状态精确匹配
        if (status != null && !status.isBlank()) qw.eq("status", status);
        // 按关键词对标题或描述做模糊匹配（OR 关系）
        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("title", q).or().like("description", q));
        }
        // 按创建时间倒序
        qw.orderByDesc("created_at");
        // 执行分页查询
        Page<FeedbackSubmission> p = mapper.selectPage(new Page<>(page, size), qw);
        // 统一包装为 PageResult 返回
        PageResult<FeedbackSubmission> pr = new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(pr);
    }

    /**
     * 获取单条反馈详情
     * @param id 反馈记录 ID
     * @return 反馈详情，不存在返回 404
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable("id") Long id){
        FeedbackSubmission f = mapper.selectById(id);
        if (f == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(f);
    }

    /**
     * 更新反馈状态（如 open → in_progress → closed）
     * 只允许管理员操作，只更新状态字段，保留其他字段不变
     * @param id 反馈记录 ID
     * @param body 请求体，需包含 status 字段
     * @return 成功返回 { ok: true }，状态缺失返回 400，记录不存在返回 404
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body){
        String status = asString(body.get("status"));
        // 状态字段必填且非空
        if (status == null || status.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "缺少状态"));
        FeedbackSubmission f = mapper.selectById(id);
        // 反馈不存在返回 404
        if (f == null) return ResponseEntity.notFound().build();
        // 仅更新状态字段
        f.setStatus(status);
        mapper.updateById(f);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 工具方法：对象安全转字符串并去首尾空格
     * 用途：避免类型转换异常与 NPE，统一字符串处理
     * @param v 任意对象
     * @return 去空格后的字符串，null 返回 null
     */
    private static String asString(Object v){ return v == null ? null : String.valueOf(v).trim(); }
}
