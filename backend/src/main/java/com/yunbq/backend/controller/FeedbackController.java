package com.yunbq.backend.controller;

import com.yunbq.backend.mapper.FeedbackSubmissionMapper;
import com.yunbq.backend.model.FeedbackSubmission;
import com.yunbq.backend.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackSubmissionMapper mapper;

    public FeedbackController(FeedbackSubmissionMapper mapper) {
        this.mapper = mapper;
    }

    @PostMapping("/issue")
    public ResponseEntity<?> submitIssue(@RequestBody Map<String, Object> body) {
        FeedbackSubmission f = new FeedbackSubmission();
        f.setType("issue");
        f.setStatus("open");
        Long uid = AuthUtil.currentUserId();
        f.setUserId(uid);
        f.setContactEmail(asString(body.get("contactEmail")));
        f.setContactQq(asString(body.get("contactQq")));
        f.setModule(asString(body.get("module")));
        f.setPagePath(asString(body.get("pagePath")));
        f.setTitle(asString(body.get("title")));
        f.setDescription(asString(body.get("description")));
        // 根据需求：移除 steps/expected/actual/github 字段的处理
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        if (isBlank(f.getDescription())) {
            return ResponseEntity.badRequest().body(Map.of("message", "请填写问题描述"));
        }
        mapper.insert(f);
        return ResponseEntity.ok(Map.of("ok", true, "id", f.getId()));
    }

    @PostMapping("/suggest")
    public ResponseEntity<?> submitSuggest(@RequestBody Map<String, Object> body) {
        FeedbackSubmission f = new FeedbackSubmission();
        f.setType("suggest");
        f.setStatus("open");
        Long uid = AuthUtil.currentUserId();
        f.setUserId(uid);
        f.setContactEmail(asString(body.get("contactEmail")));
        f.setContactQq(asString(body.get("contactQq")));
        f.setCategory(asString(body.get("category")));
        f.setTitle(asString(body.get("title")));
        f.setDescription(asString(body.get("description")));
        // 根据需求：移除 expectedBenefit/github 字段的处理
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        if (isBlank(f.getDescription())) {
            return ResponseEntity.badRequest().body(Map.of("message", "请填写建议内容"));
        }
        mapper.insert(f);
        return ResponseEntity.ok(Map.of("ok", true, "id", f.getId()));
    }

    private static String asString(Object v){ return v == null ? null : String.valueOf(v).trim(); }
    private static boolean isBlank(String s){ return s == null || s.trim().isEmpty(); }
}
