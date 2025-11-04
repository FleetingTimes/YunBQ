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
import com.yunbq.backend.util.AuthUtil; // 工具类：用于获取当前认证用户ID（从 SecurityContext）
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.yunbq.backend.service.LogService logService;

    public AdminController(UserMapper userMapper,
                           AuditLogMapper auditLogMapper,
                           RequestLogMapper requestLogMapper,
                           AuthLogMapper authLogMapper,
                           ErrorLogMapper errorLogMapper,
                           com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                           com.yunbq.backend.service.LogService logService) {
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
        this.requestLogMapper = requestLogMapper;
        this.authLogMapper = authLogMapper;
        this.errorLogMapper = errorLogMapper;
        this.objectMapper = objectMapper;
        this.logService = logService;
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
        // 映射用户列表到摘要，补充头像地址与密码状态（不暴露真实密码或哈希）
        List<UserSummary> items = p.getRecords().stream().map(u -> {
            boolean hasPassword = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();
            return new UserSummary(
                u.getId(),
                u.getUsername(),
                u.getNickname(),
                u.getEmail(),
                u.getRole(),
                u.getCreatedAt(),
                u.getAvatarUrl(),
                hasPassword
            );
        }).collect(Collectors.toList());
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

    /**
     * 导出审计日志（支持 csv 或 json）。
     * 说明：JSON 导出直接序列化到字节；CSV 导出通过服务层构建文本并添加 UTF-8 BOM。
     */
    @GetMapping("/logs/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String level
    ) throws Exception {
        java.util.List<AuditLog> items = logService.listAuditLogs(level);
        String filename = "audit-logs." + ("json".equalsIgnoreCase(format) ? "json" : "csv");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);
        if ("json".equalsIgnoreCase(format)) {
            byte[] json = objectMapper.writeValueAsBytes(items);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM); // 使用通用类型便于前端 blob 下载
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(json);
        } else {
            String csv = logService.exportAuditLogsToCsv(items);
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] bytes = (new java.io.ByteArrayOutputStream(){
                { try { this.write(bom); this.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (java.io.IOException ignored) {} }
            }).toByteArray();
            headers.setContentType(MediaType.valueOf("text/csv; charset=UTF-8"));
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
        }
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

    /**
     * 导出请求日志（支持 csv 或 json），可带筛选。
     */
    @GetMapping("/request-logs/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> exportRequestLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String uri,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String requestId
    ) throws Exception {
        java.util.List<RequestLog> items = logService.listRequestLogs(uri, status, requestId);
        String filename = "request-logs." + ("json".equalsIgnoreCase(format) ? "json" : "csv");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);
        if ("json".equalsIgnoreCase(format)) {
            byte[] json = objectMapper.writeValueAsBytes(items);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(json);
        } else {
            String csv = logService.exportRequestLogsToCsv(items);
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] bytes = (new java.io.ByteArrayOutputStream(){
                { try { this.write(bom); this.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (java.io.IOException ignored) {} }
            }).toByteArray();
            headers.setContentType(MediaType.valueOf("text/csv; charset=UTF-8"));
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
        }
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

    /**
     * 导出认证日志（支持 csv 或 json），可带筛选。
     */
    @GetMapping("/auth-logs/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> exportAuthLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String requestId
    ) throws Exception {
        java.util.List<AuthLog> items = logService.listAuthLogs(success, username, requestId);
        String filename = "auth-logs." + ("json".equalsIgnoreCase(format) ? "json" : "csv");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);
        if ("json".equalsIgnoreCase(format)) {
            byte[] json = objectMapper.writeValueAsBytes(items);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(json);
        } else {
            String csv = logService.exportAuthLogsToCsv(items);
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] bytes = (new java.io.ByteArrayOutputStream(){
                { try { this.write(bom); this.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (java.io.IOException ignored) {} }
            }).toByteArray();
            headers.setContentType(MediaType.valueOf("text/csv; charset=UTF-8"));
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
        }
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

    /**
     * 导出错误日志（支持 csv 或 json），可带筛选。
     */
    @GetMapping("/error-logs/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> exportErrorLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String exception,
            @RequestParam(required = false) String requestId
    ) throws Exception {
        java.util.List<ErrorLog> items = logService.listErrorLogs(exception, requestId);
        String filename = "error-logs." + ("json".equalsIgnoreCase(format) ? "json" : "csv");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);
        if ("json".equalsIgnoreCase(format)) {
            byte[] json = objectMapper.writeValueAsBytes(items);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(json);
        } else {
            String csv = logService.exportErrorLogsToCsv(items);
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] bytes = (new java.io.ByteArrayOutputStream(){
                { try { this.write(bom); this.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (java.io.IOException ignored) {} }
            }).toByteArray();
            headers.setContentType(MediaType.valueOf("text/csv; charset=UTF-8"));
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
        }
    }

    /**
     * 导出用户信息（支持 csv 或 json 格式）。
     * 说明：导出所有用户或根据搜索条件过滤的用户列表，包含用户基本信息、角色、头像地址和密码状态。
     * 注意：不导出真实密码或哈希，仅导出是否已设置密码的状态。
     */
    @GetMapping("/users/export")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> exportUsers(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String q
    ) throws Exception {
        // 构建查询条件，与列表接口保持一致
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (q != null && !q.isBlank()) {
            qw.like("username", q).or().like("nickname", q).or().like("email", q);
        }
        qw.orderByDesc("id");
        
        // 查询所有符合条件的用户（不分页）
        List<User> users = userMapper.selectList(qw);
        
        // 映射为 UserSummary 对象，包含头像地址和密码状态
        List<UserSummary> userSummaries = users.stream().map(u -> {
            boolean hasPassword = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();
            return new UserSummary(
                u.getId(),
                u.getUsername(),
                u.getNickname(),
                u.getEmail(),
                u.getRole(),
                u.getCreatedAt(),
                u.getAvatarUrl(),
                hasPassword
            );
        }).collect(Collectors.toList());
        
        // 设置文件名和响应头
        String filename = "users." + ("json".equalsIgnoreCase(format) ? "json" : "csv");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);
        
        if ("json".equalsIgnoreCase(format)) {
            // JSON 格式导出
            // 说明：为了更好兼容部分浏览器/代理的处理，这里将 Content-Type 设置为 application/json。
            //       同时 UserSummary.createdAt 已配置 @JsonFormat，避免 LocalDateTime 序列化问题。
            byte[] json = objectMapper.writeValueAsBytes(userSummaries);
            headers.setContentType(MediaType.APPLICATION_JSON);
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(json);
        } else {
            // CSV 格式导出
            String csv = exportUsersToCsv(userSummaries);
            // 添加 UTF-8 BOM 以确保中文正确显示
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] bytes = (new java.io.ByteArrayOutputStream(){
                { try { this.write(bom); this.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (java.io.IOException ignored) {} }
            }).toByteArray();
            headers.setContentType(MediaType.valueOf("text/csv; charset=UTF-8"));
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
        }
    }

    /**
     * 将用户列表转换为 CSV 格式字符串。
     * 包含表头和数据行，密码状态显示为"已设置"或"未设置"。
     */
    private String exportUsersToCsv(List<UserSummary> users) {
        StringBuilder csv = new StringBuilder();
        
        // CSV 表头
        csv.append("用户ID,用户名,昵称,邮箱,角色,注册时间,头像地址,密码状态\n");
        
        // 数据行
        for (UserSummary user : users) {
            csv.append(escapeCsvField(String.valueOf(user.getId()))).append(",");
            csv.append(escapeCsvField(user.getUsername())).append(",");
            csv.append(escapeCsvField(user.getNickname())).append(",");
            csv.append(escapeCsvField(user.getEmail())).append(",");
            csv.append(escapeCsvField(user.getRole())).append(",");
            csv.append(escapeCsvField(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")).append(",");
            csv.append(escapeCsvField(user.getAvatarUrl() != null ? user.getAvatarUrl() : "")).append(",");
            csv.append(escapeCsvField(user.isHasPassword() ? "已设置" : "未设置"));
            csv.append("\n");
        }
        
        return csv.toString();
    }

    /**
     * 高级导出用户信息（支持 csv 或 json 格式）。
     * 说明：
     * - 与普通导出不同，高级导出会包含完整的用户字段，包括敏感的密码哈希（passwordHash）。
     * - 该功能仅面向管理员，通常用于数据迁移或灾备；请谨慎使用并妥善存储导出文件。
     * - 后端会记录审计日志，包含触发管理员ID、格式与导出数量，方便后续审计追踪。
     * 安全性：
     * - 接口受 @PreAuthorize("hasRole('ADMIN')") 限制；
     * - 建议仅在受信任的网络环境中使用，并确保导出文件存储加密。
     */
    @GetMapping("/users/export/advanced")
    @PreAuthorize("hasRole('ADMIN')")
    public org.springframework.http.ResponseEntity<byte[]> exportUsersAdvanced(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String q
    ) throws Exception {
        // 1) 构建查询条件：与列表/普通导出保持一致，支持根据用户名/昵称/邮箱模糊查询
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (q != null && !q.isBlank()) {
            qw.like("username", q).or().like("nickname", q).or().like("email", q);
        }
        qw.orderByDesc("id");

        // 2) 查询所有符合条件的用户（不分页）
        List<User> users = userMapper.selectList(qw);

        // 3) 审计记录：标记此为敏感操作，记录管理员ID、导出格式与数量
        try {
            Long adminId = AuthUtil.currentUserId();
            // level 选择 WARN，以凸显敏感数据导出；message 保持简洁明了
            logService.logAudit(adminId, "WARN",
                    String.format("Advanced export users: format=%s, count=%d", format, users.size()));
        } catch (Exception ignore) {
            // 审计写入失败不影响导出主流程，但建议在系统日志中观察该异常
        }

        // 4) 设置文件名与响应头
        String filename = "users-advanced." + ("json".equalsIgnoreCase(format) ? "json" : "csv");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=" + filename);

        // 5) 根据格式输出：JSON 直接序列化完整实体；CSV 构造文本并添加 UTF-8 BOM
        if ("json".equalsIgnoreCase(format)) {
            // JSON 导出：
            // - 直接序列化 User 实体列表，包含所有字段（id, username, passwordHash, nickname, email, signature, avatarUrl, role, createdAt）。
            // - Content-Type 使用 application/json，便于部分工具直接解析。
            byte[] json = objectMapper.writeValueAsBytes(users);
            headers.setContentType(MediaType.APPLICATION_JSON);
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(json);
        } else {
            // CSV 导出：构造包含完整字段的 CSV 文本
            String csv = exportUsersAdvancedToCsv(users);
            byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}; // 添加 UTF-8 BOM，确保 Excel 等正确识别编码
            byte[] bytes = (new java.io.ByteArrayOutputStream(){
                { try { this.write(bom); this.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8)); } catch (java.io.IOException ignored) {} }
            }).toByteArray();
            headers.setContentType(MediaType.valueOf("text/csv; charset=UTF-8"));
            return org.springframework.http.ResponseEntity.ok().headers(headers).body(bytes);
        }
    }

    /**
     * 高级导出：将完整的 User 实体列表转换为 CSV。
     * 字段顺序与表头如下：
     * id,username,nickname,email,signature,avatarUrl,role,createdAt,passwordHash
     * 格式化说明：
     * - 对所有文本字段进行转义（逗号、引号、换行），确保 CSV 合规；
     * - 时间使用 LocalDateTime#toString()（ISO-8601），便于通用解析；
     * - 空值输出为空字符串。
     */
    private String exportUsersAdvancedToCsv(List<User> users) {
        StringBuilder sb = new StringBuilder();
        // 表头
        sb.append("id,username,nickname,email,signature,avatarUrl,role,createdAt,passwordHash\n");
        // 数据行
        for (User u : users) {
            sb.append(escapeCsvField(u.getId() != null ? String.valueOf(u.getId()) : ""))
              .append(',')
              .append(escapeCsvField(u.getUsername()))
              .append(',')
              .append(escapeCsvField(u.getNickname()))
              .append(',')
              .append(escapeCsvField(u.getEmail()))
              .append(',')
              .append(escapeCsvField(u.getSignature()))
              .append(',')
              .append(escapeCsvField(u.getAvatarUrl()))
              .append(',')
              .append(escapeCsvField(u.getRole()))
              .append(',')
              .append(escapeCsvField(u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""))
              .append(',')
              .append(escapeCsvField(u.getPasswordHash()))
              .append('\n');
        }
        return sb.toString();
    }

    /**
     * 转义 CSV 字段，处理包含逗号、引号或换行符的内容。
     */
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // 如果字段包含逗号、引号或换行符，需要用引号包围并转义内部引号
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}