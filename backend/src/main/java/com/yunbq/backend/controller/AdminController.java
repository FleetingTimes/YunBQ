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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final PasswordEncoder passwordEncoder; // 密码编码器：用于对明文密码进行哈希处理

    public AdminController(UserMapper userMapper,
                           AuditLogMapper auditLogMapper,
                           RequestLogMapper requestLogMapper,
                           AuthLogMapper authLogMapper,
                           ErrorLogMapper errorLogMapper,
                           com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                           com.yunbq.backend.service.LogService logService,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.auditLogMapper = auditLogMapper;
        this.requestLogMapper = requestLogMapper;
        this.authLogMapper = authLogMapper;
        this.errorLogMapper = errorLogMapper;
        this.objectMapper = objectMapper;
        this.logService = logService;
        this.passwordEncoder = passwordEncoder;
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

    /**
     * 创建用户（管理员）
     *
     * 入参说明：
     * - 支持字段：username(必填)、nickname、email、signature、avatarUrl、role（ADMIN/USER）、password（可选明文）
     * - 若提供 password（明文），将进行 BCrypt 哈希后写入 passwordHash；未提供则不设置密码（不可登录）。
     * - email 若已被其他用户占用，将返回 409。
     *
     * 返回：创建后的用户摘要（不含密码/哈希），与列表接口保持一致。
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        String username = asString(body.get("username"));
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "username 为必填项"));
        }

        // 邮箱唯一性检查（若提供）
        String email = asString(body.get("email"));
        if (email != null && !email.isBlank()) {
            User existEmail = userMapper.selectOne(new QueryWrapper<User>().eq("email", email).last("limit 1"));
            if (existEmail != null) {
                return ResponseEntity.status(409).body(Map.of("message", "该邮箱已被使用"));
            }
        }

        // 用户名唯一性检查
        User existUsername = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("limit 1"));
        if (existUsername != null) {
            return ResponseEntity.status(409).body(Map.of("message", "该用户名已存在"));
        }

        User u = new User();
        u.setUsername(username);
        u.setNickname(asString(body.get("nickname")));
        u.setEmail(email);
        u.setSignature(asString(body.get("signature")));
        u.setAvatarUrl(asString(body.get("avatarUrl")));
        String role = asString(body.get("role"));
        u.setRole(role != null && !role.isBlank() ? role : "USER");
        u.setCreatedAt(java.time.LocalDateTime.now());

        // 处理明文密码：若提供则进行哈希
        String password = asString(body.get("password"));
        if (password != null && !password.isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(password));
        }

        userMapper.insert(u);

        boolean hasPassword = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();
        UserSummary resp = new UserSummary(
                u.getId(), u.getUsername(), u.getNickname(), u.getEmail(), u.getRole(), u.getCreatedAt(), u.getAvatarUrl(), hasPassword
        );
        return ResponseEntity.ok(resp);
    }

    /**
     * 更新用户（管理员）
     *
     * 入参说明：
     * - 支持更新：nickname、email、signature、avatarUrl、role、password（明文，覆盖原密码）
     * - 如需修改 username，可传入 username（需通过唯一性检查）。
     * - email 若与其他用户冲突，返回 409。
     *
     * 返回：更新后的用户摘要。
     */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        }

        // username 更新（可选且需唯一性检查）
        String newUsername = asString(body.get("username"));
        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(u.getUsername())) {
            User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", newUsername).last("limit 1"));
            if (exist != null && !exist.getId().equals(id)) {
                return ResponseEntity.status(409).body(Map.of("message", "该用户名已存在"));
            }
            u.setUsername(newUsername);
        }

        // email 更新（唯一性检查）
        String newEmail = asString(body.get("email"));
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(u.getEmail())) {
            User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", newEmail).last("limit 1"));
            if (exist != null && !exist.getId().equals(id)) {
                return ResponseEntity.status(409).body(Map.of("message", "该邮箱已被使用"));
            }
            u.setEmail(newEmail);
        }

        // 其他字段更新：仅覆盖提供的非空值
        if (body.containsKey("nickname")) u.setNickname(asString(body.get("nickname")));
        if (body.containsKey("signature")) u.setSignature(asString(body.get("signature")));
        if (body.containsKey("avatarUrl")) u.setAvatarUrl(asString(body.get("avatarUrl")));
        if (body.containsKey("role")) {
            String r = asString(body.get("role"));
            if (r != null && !r.isBlank()) u.setRole(r);
        }

        // 重置密码：若提供明文 password，则进行哈希覆盖原密码
        String password = asString(body.get("password"));
        if (password != null && !password.isBlank()) {
            u.setPasswordHash(passwordEncoder.encode(password));
        }

        userMapper.updateById(u);

        boolean hasPassword = u.getPasswordHash() != null && !u.getPasswordHash().isBlank();
        UserSummary resp = new UserSummary(
                u.getId(), u.getUsername(), u.getNickname(), u.getEmail(), u.getRole(), u.getCreatedAt(), u.getAvatarUrl(), hasPassword
        );
        return ResponseEntity.ok(resp);
    }

    /**
     * 删除用户（管理员）
     *
     * 安全提示：可根据需要限制删除自身或 ADMIN 账号，这里仅做简单删除。
     */
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User u = userMapper.selectById(id);
        if (u == null) {
            return ResponseEntity.status(404).body(Map.of("message", "用户不存在"));
        }
        userMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ==================== 辅助方法 ====================
    /**
     * 将对象安全转换为字符串，避免出现类型不匹配导致的 ClassCastException。
     * null 或非字符串类型将返回其 toString()（若为 null 则返回 null）。
     */
    private String asString(Object v) {
        if (v == null) return null;
        return (v instanceof String) ? (String) v : v.toString();
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

    /**
     * 从 JSON 节点中读取字符串字段，若不存在或为 null 则返回 null。
     * 使用说明：避免直接调用 node.get(field).asText() 导致 NPE 或将 null 转为空字符串。
     */
    private String textOrNull(com.fasterxml.jackson.databind.JsonNode node, String field) {
        com.fasterxml.jackson.databind.JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    // ========================= 用户导入 =========================

    /**
     * 批量导入用户数据（管理员接口）。
     *
     * 功能说明：
     * - 通过 `multipart/form-data` 上传一个 JSON 文件，字段名固定为 `file`；
     * - JSON 内容为用户数组（或 { users: [...] } 对象），每项包含基本字段：
     *   username, email, nickname, signature, avatarUrl, role, password（明文）或 passwordHash（BCrypt）；
     * - 去重策略：优先按 username 查找，其次按 email 查找；存在则按提供字段更新，不存在则创建；
     * - 密码处理：
     *   1) 若提供 `passwordHash` 且看起来是 BCrypt（形如 `$2a|2b|2y$<cost>$<53 chars>`），直接入库；
     *   2) 若提供 `password`：
     *      - 若其看起来像 BCrypt 哈希，则直接入库（兼容错误标注场景）；
     *      - 否则视为明文，使用后端 `PasswordEncoder` 统一哈希后入库；
     *   3) 若未提供密码字段：
     *      - 新建用户不设置密码（前端列表将显示“未设置”）；
     *      - 更新用户保留原密码不变；
     * - 返回导入统计：{ total, created, updated, skipped, errors }。
     *
     * 安全与审计：
     * - 接口受 @PreAuthorize("hasRole('ADMIN')") 限制，仅管理员可调用；
     * - 每次导入会写入审计日志（管理员ID、导入数量、创建/更新统计），便于追踪。
     */
    // 注意：为批量导入开启事务，保证同一请求内的插入/更新
    // 在没有显式事务的情况下，MyBatis-Spring 会创建“非事务”SqlSession，
    // 不同数据源/配置下可能出现变更未提交的情况（尤其是多条 DML 混合执行时）。
    // 加上 @Transactional 后，Spring 会接管连接与提交/回滚，确保导入结果写入数据库。
    @PostMapping(path = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> importUsers(
            @RequestPart("file") MultipartFile file
    ) throws Exception {
        // 参数校验：必须提供非空文件
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "未选择文件或文件为空"
            ));
        }

        // 读取并解析 JSON 内容，支持纯数组或包裹对象（{ users: [...] }）
        byte[] bytes = file.getBytes();
        com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(bytes);
        com.fasterxml.jackson.databind.JsonNode arr;
        if (root.isArray()) {
            arr = root;
        } else if (root.isObject() && root.has("users") && root.get("users").isArray()) {
            arr = root.get("users");
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "JSON 格式不正确：需为数组或包含 users 数组的对象"
            ));
        }

        // 统计信息
        int total = arr.size();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();

        // BCrypt 检测正则：$2a/$2b/$2y + 两位成本因子 + 53 字符哈希
        java.util.regex.Pattern BCRYPT = java.util.regex.Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

        // 遍历每个用户项，执行去重、创建或更新逻辑
        for (com.fasterxml.jackson.databind.JsonNode node : arr) {
            try {
                // 读取基础字段
                String username = textOrNull(node, "username");
                String email = textOrNull(node, "email");
                String nickname = textOrNull(node, "nickname");
                String signature = textOrNull(node, "signature");
                String avatarUrl = textOrNull(node, "avatarUrl");
                String role = textOrNull(node, "role");
                String createdAtStr = textOrNull(node, "createdAt"); // 可选：导入时的创建时间字符串（ISO-8601）
                java.time.LocalDateTime createdAtParsed = null; // 解析后的创建时间

                // 基本校验：创建时必须提供 username；若仅提供 email 则尝试更新，但不创建
                if ((username == null || username.isBlank()) && (email == null || email.isBlank())) {
                    skipped++;
                    errors.add("跳过：缺少 username 与 email，无法识别用户");
                    continue;
                }

                // 查找现有用户：优先按 username，其次按 email
                User existing = null;
                if (username != null && !username.isBlank()) {
                    existing = userMapper.selectOne(new QueryWrapper<User>().eq("username", username).last("limit 1"));
                }
                if (existing == null && email != null && !email.isBlank()) {
                    existing = userMapper.selectOne(new QueryWrapper<User>().eq("email", email).last("limit 1"));
                }

                // 构造即将写入的实体
                User u = existing != null ? existing : new User();

                // 赋值非空字段：更新仅覆盖提供的字段；创建必须设置 username
                if (existing == null) {
                    if (username == null || username.isBlank()) {
                        // 不创建无用户名的记录（避免产生“不可登录”的孤儿账号）
                        skipped++;
                        errors.add("跳过：创建新用户时必须提供 username");
                        continue;
                    }
                    u.setUsername(username);
                }
                if (nickname != null) u.setNickname(nickname);
                if (email != null) u.setEmail(email);
                if (signature != null) u.setSignature(signature);
                if (avatarUrl != null) u.setAvatarUrl(avatarUrl);
                if (role != null) u.setRole(role);
                // 处理 createdAt：
                // - 若 JSON 提供了合法的 ISO-8601 格式（例如 2025-11-04T14:18:32），则按该值设置；
                // - 若未提供或解析失败：
                //   - 对“新建用户”设置为当前时间（满足数据库非空约束与审计要求）；
                //   - 对“更新用户”保持原值不变（不覆盖历史创建时间）。
                if (createdAtStr != null && !createdAtStr.isBlank()) {
                    try {
                        createdAtParsed = java.time.LocalDateTime.parse(createdAtStr);
                    } catch (Exception pe) {
                        // 解析失败时不直接报错，以当前时间兜底（仅在创建场景使用）
                        createdAtParsed = null;
                    }
                }

                // 密码处理：优先 passwordHash，其次 password
                String passwordHash = textOrNull(node, "passwordHash");
                String password = textOrNull(node, "password");

                // 若提供 BCrypt 格式的 passwordHash，直接入库
                if (passwordHash != null && !passwordHash.isBlank()) {
                    if (BCRYPT.matcher(passwordHash).matches()) {
                        u.setPasswordHash(passwordHash);
                    } else {
                        // 非 BCrypt 的 passwordHash 不可信，忽略该字段（避免错误哈希污染）
                    }
                } else if (password != null && !password.isBlank()) {
                    // 若提供 password：
                    if (BCRYPT.matcher(password).matches()) {
                        // 看起来是 BCrypt 哈希：直接入库（兼容前端误标注明文的场景）
                        u.setPasswordHash(password);
                    } else {
                        // 明文密码：使用后端编码器统一哈希后入库
                        u.setPasswordHash(passwordEncoder.encode(password));
                    }
                } else {
                    // 未提供密码：
                    // - 创建：保持为空（允许后续通过“重置密码”或“社交登录”完成设置）；
                    // - 更新：保留原有密码不变（即不覆盖 existing.passwordHash）。
                }

                // 执行插入或更新
                if (existing == null) {
                    // 新建用户默认角色与创建时间兜底：
                    // - 角色：如果未提供，则使用默认值 "USER"（与表结构一致）；
                    // - 创建时间：如果未提供或解析失败，则使用当前时间，避免因非空约束导致插入失败。
                    if (u.getRole() == null || u.getRole().isBlank()) {
                        u.setRole("USER");
                    }
                    if (createdAtParsed != null) {
                        u.setCreatedAt(createdAtParsed);
                    } else {
                        u.setCreatedAt(java.time.LocalDateTime.now());
                    }
                    userMapper.insert(u);
                    created++;
                } else {
                    userMapper.updateById(u);
                    updated++;
                }
            } catch (Exception ex) {
                skipped++; // 将异常视为跳过项
                errors.add("异常：" + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            }
        }

        // 写入审计日志：记录管理员ID与导入统计
        try {
            Long adminId = com.yunbq.backend.util.AuthUtil.currentUserId();
            logService.logAudit(adminId, "INFO",
                    String.format("Import users: total=%d, created=%d, updated=%d, skipped=%d", total, created, updated, skipped));
        } catch (Exception ignore) {
            // 审计失败不影响主流程
        }

        // 返回统计结果
        Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("total", total);
        resp.put("created", created);
        resp.put("updated", updated);
        resp.put("skipped", skipped);
        resp.put("errors", errors);
        return ResponseEntity.ok(resp);
    }
}