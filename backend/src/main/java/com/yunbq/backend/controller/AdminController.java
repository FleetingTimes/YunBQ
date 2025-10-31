package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.PageResult;
import com.yunbq.backend.dto.UserSummary;
import com.yunbq.backend.mapper.AuditLogMapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.mapper.RequestLogMapper;
import com.yunbq.backend.mapper.AuthLogMapper;
import com.yunbq.backend.mapper.ErrorLogMapper;
import com.yunbq.backend.model.AuditLog;
import com.yunbq.backend.model.User;
import com.yunbq.backend.model.RequestLog;
import com.yunbq.backend.model.AuthLog;
import com.yunbq.backend.model.ErrorLog;
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
    private final RequestLogMapper requestLogMapper;
    private final AuthLogMapper authLogMapper;
    private final ErrorLogMapper errorLogMapper;

    public AdminController(UserMapper userMapper,
                           AuditLogMapper auditLogMapper,
                           RequestLogMapper requestLogMapper,
                           AuthLogMapper authLogMapper,
                           ErrorLogMapper errorLogMapper) {
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
        this.requestLogMapper = requestLogMapper;
        this.authLogMapper = authLogMapper;
        this.errorLogMapper = errorLogMapper;
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

    @GetMapping("/request-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<RequestLog>> listRequestLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String uri,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String requestId
    ) {
        // 说明：请求日志分页查询
        // 支持按 URI、状态码与 requestId 精确/模糊过滤；按创建时间倒序。
        QueryWrapper<RequestLog> qw = new QueryWrapper<>();
        if (uri != null && !uri.isBlank()) { qw.like("uri", uri); }
        if (status != null) { qw.eq("status", status); }
        if (requestId != null && !requestId.isBlank()) { qw.eq("request_id", requestId); }
        qw.orderByDesc("created_at");
        Page<RequestLog> p = requestLogMapper.selectPage(Page.of(page, size), qw);
        PageResult<RequestLog> resp = new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/auth-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<AuthLog>> listAuthLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String requestId
    ) {
        // 说明：认证日志分页查询
        // 支持按成功/失败、用户名与 requestId 过滤；按创建时间倒序。
        QueryWrapper<AuthLog> qw = new QueryWrapper<>();
        if (success != null) { qw.eq("success", success ? 1 : 0); }
        if (username != null && !username.isBlank()) { qw.like("username", username); }
        if (requestId != null && !requestId.isBlank()) { qw.eq("request_id", requestId); }
        qw.orderByDesc("created_at");
        Page<AuthLog> p = authLogMapper.selectPage(Page.of(page, size), qw);
        PageResult<AuthLog> resp = new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/error-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResult<ErrorLog>> listErrorLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String exception,
            @RequestParam(required = false) String requestId
    ) {
        // 说明：错误日志分页查询
        // 支持按异常类名与 requestId 过滤；按创建时间倒序。
        QueryWrapper<ErrorLog> qw = new QueryWrapper<>();
        if (exception != null && !exception.isBlank()) { qw.like("exception", exception); }
        if (requestId != null && !requestId.isBlank()) { qw.eq("request_id", requestId); }
        qw.orderByDesc("created_at");
        Page<ErrorLog> p = errorLogMapper.selectPage(Page.of(page, size), qw);
        PageResult<ErrorLog> resp = new PageResult<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize());
        return ResponseEntity.ok(resp);
    }
}