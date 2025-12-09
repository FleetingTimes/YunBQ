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

@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {

    private final FeedbackSubmissionMapper mapper;

    public AdminFeedbackController(FeedbackSubmissionMapper mapper) {
        this.mapper = mapper;
    }

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
        if (type != null && !type.isBlank()) qw.eq("type", type);
        if (status != null && !status.isBlank()) qw.eq("status", status);
        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("title", q).or().like("description", q));
        }
        qw.orderByDesc("created_at");
        Page<FeedbackSubmission> p = mapper.selectPage(new Page<>(page, size), qw);
        PageResult<FeedbackSubmission> pr = new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(pr);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> get(@PathVariable("id") Long id){
        FeedbackSubmission f = mapper.selectById(id);
        if (f == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(f);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body){
        String status = asString(body.get("status"));
        if (status == null || status.isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "缺少状态"));
        FeedbackSubmission f = mapper.selectById(id);
        if (f == null) return ResponseEntity.notFound().build();
        f.setStatus(status);
        mapper.updateById(f);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String asString(Object v){ return v == null ? null : String.valueOf(v).trim(); }
}
