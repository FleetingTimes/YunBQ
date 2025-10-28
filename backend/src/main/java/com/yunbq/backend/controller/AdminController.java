package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.PageResult;
import com.yunbq.backend.dto.UserSummary;
import com.yunbq.backend.mapper.AuditLogMapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.AuditLog;
import com.yunbq.backend.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserMapper userMapper;
    private final AuditLogMapper auditLogMapper;

    public AdminController(UserMapper userMapper, AuditLogMapper auditLogMapper) {
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
    }

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("ok", true, "message", "admin access granted"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<UserSummary>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q
    ) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (q != null && !q.isBlank()) {
            qw.like("username", q).or().like("nickname", q).or().like("email", q);
        }
        qw.orderByDesc("id");
        Page<User> p = userMapper.selectPage(Page.of(page, size), qw);
        List<UserSummary> items = p.getRecords().stream().map(u -> new UserSummary(
                u.getId(), u.getUsername(), u.getNickname(), u.getEmail(), u.getRole(), u.getCreatedAt()
        )).collect(Collectors.toList());
        PageResult<UserSummary> resp = new PageResult<>(items, p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<AuditLog>> listLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String level
    ) {
        QueryWrapper<AuditLog> qw = new QueryWrapper<>();
        if (level != null && !level.isBlank()) {
            qw.eq("level", level);
        }
        qw.orderByDesc("created_at");
        Page<AuditLog> p = auditLogMapper.selectPage(Page.of(page, size), qw);
        PageResult<AuditLog> resp = new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(resp);
    }
}