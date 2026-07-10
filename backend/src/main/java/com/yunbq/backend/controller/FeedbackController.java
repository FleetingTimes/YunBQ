package com.yunbq.backend.controller;

import com.yunbq.backend.mapper.FeedbackSubmissionMapper;
import com.yunbq.backend.model.FeedbackSubmission;
import com.yunbq.backend.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 反馈提交控制器（公开接口）
 * 职责与设计要点：
 * - 提供两类反馈提交：问题反馈（issue）和建议反馈（suggest）
 * - 允许匿名提交，但登录用户会自动关联 userId
 * - 所有新建反馈初始状态为 "open"
 * - 与安全配置协同：已在 SecurityConfig 中对 POST /api/feedback/** 匿名放行
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackSubmissionMapper mapper;

    /**
     * 构造函数注入：依赖注入反馈提交数据访问对象
     * @param mapper 反馈提交 Mapper
     */
    public FeedbackController(FeedbackSubmissionMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 提交问题反馈（issue）
     * 字段约定：
     * - type 固定为 "issue"
     * - status 固定初始为 "open"
     * - module: 问题所属模块
     * - pagePath: 问题页面路径（可选）
     * - contactEmail/contactQq: 联系方式（可选）
     * - title/description: 问题标题与描述（必填）
     * @param body 请求体（Map 灵活接收字段）
     * @return 成功返回 { ok: true, id: 反馈ID }，缺少必填字段返回 400
     */
    @PostMapping("/issue")
    public ResponseEntity<?> submitIssue(@RequestBody Map<String, Object> body) {
        FeedbackSubmission f = new FeedbackSubmission();
        // 固定类型与初始状态
        f.setType("issue");
        f.setStatus("open");
        // 关联当前登录用户（匿名时为 null）
        Long uid = AuthUtil.currentUserId();
        f.setUserId(uid);
        // 联系方式
        f.setContactEmail(asString(body.get("contactEmail")));
        f.setContactQq(asString(body.get("contactQq")));
        // 问题信息
        f.setModule(asString(body.get("module")));
        f.setPagePath(asString(body.get("pagePath")));
        f.setTitle(asString(body.get("title")));
        f.setDescription(asString(body.get("description")));
        // 根据需求：移除 steps/expected/actual/github 字段的处理
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        // 必填字段校验：问题描述不能为空
        if (isBlank(f.getDescription())) {
            return ResponseEntity.badRequest().body(Map.of("message", "请填写问题描述"));
        }
        mapper.insert(f);
        return ResponseEntity.ok(Map.of("ok", true, "id", f.getId()));
    }

    /**
     * 提交建议反馈（suggest）
     * 字段约定：
     * - type 固定为 "suggest"
     * - status 固定初始为 "open"
     * - category: 建议分类（可选）
     * - contactEmail/contactQq: 联系方式（可选）
     * - title/description: 建议标题与内容（必填）
     * @param body 请求体（Map 灵活接收字段）
     * @return 成功返回 { ok: true, id: 反馈ID }，缺少必填字段返回 400
     */
    @PostMapping("/suggest")
    public ResponseEntity<?> submitSuggest(@RequestBody Map<String, Object> body) {
        FeedbackSubmission f = new FeedbackSubmission();
        // 固定类型与初始状态
        f.setType("suggest");
        f.setStatus("open");
        // 关联当前登录用户（匿名时为 null）
        Long uid = AuthUtil.currentUserId();
        f.setUserId(uid);
        // 联系方式与分类
        f.setContactEmail(asString(body.get("contactEmail")));
        f.setContactQq(asString(body.get("contactQq")));
        f.setCategory(asString(body.get("category")));
        // 建议信息
        f.setTitle(asString(body.get("title")));
        f.setDescription(asString(body.get("description")));
        // 根据需求：移除 expectedBenefit/github 字段的处理
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        // 必填字段校验：建议内容不能为空
        if (isBlank(f.getDescription())) {
            return ResponseEntity.badRequest().body(Map.of("message", "请填写建议内容"));
        }
        mapper.insert(f);
        return ResponseEntity.ok(Map.of("ok", true, "id", f.getId()));
    }

    /**
     * 工具方法：对象安全转字符串并去首尾空格
     * 用途：避免类型转换异常与 NPE，统一字符串处理
     * @param v 任意对象
     * @return 去空格后的字符串，null 返回 null
     */
    private static String asString(Object v){ return v == null ? null : String.valueOf(v).trim(); }

    /**
     * 工具方法：判断字符串是否为空或仅空白
     * 用途：统一非空校验逻辑
     * @param s 待判断字符串
     * @return true：空或仅空白；false：非空
     */
    private static boolean isBlank(String s){ return s == null || s.trim().isEmpty(); }
}
