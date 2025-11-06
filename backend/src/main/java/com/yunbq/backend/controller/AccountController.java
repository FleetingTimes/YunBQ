package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.User;
import com.yunbq.backend.util.AuthUtil;
import com.yunbq.backend.service.PasswordResetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 账户与个人资料接口
 * 职责：
 * - 公开查询：按用户名返回公开资料（匿名可访问）；
 * - 个人中心：获取当前用户信息（需登录）；
 * - 资料更新：头像上传、绑定邮箱、更新昵称/签名等；
 * 安全策略：
 * - 通过 `AuthUtil.currentUserId()` 判断登录态；
 * - 公开接口仅返回非敏感字段；写入操作需登录且关联当前用户ID；
 * 静态资源：
 * - 头像上传保存于 `uploads/avatars`，由 `YunbqBackendApplication#webMvcConfigurer` 映射到 `/uploads/**`；
 */
@RestController
@RequestMapping("/api/account")
/**
 * 账户控制器
 * 职责：
 * - 提供用户登录、登出、注册、资料更新等账户相关接口；
 * - 返回基础用户信息用于前端展示（头像、昵称、个性签名等）。
 * 安全：
 * - 登录态接口需校验当前用户（通过 Jwt 放入 SecurityContext）；
 * - 敏感字段严格过滤，避免越权更新其他用户资料。
 */
public class AccountController {
    private final UserMapper userMapper;
    private final PasswordResetService resetService;
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    public AccountController(UserMapper userMapper, PasswordResetService resetService){ this.userMapper = userMapper; this.resetService = resetService; }

    /**
     * 按用户名查询公开的用户信息（匿名可访问）。
     *
     * 用途：UserNotes.vue 在查看“他人拾言”时显示对方的昵称、头像、签名。
     * 前端请求示例：GET /api/account/user?username=alice
     * 返回字段：id、username、nickname、signature、avatarUrl（均为只读公开信息）。
     * 安全：不返回敏感字段（邮箱、角色等）；允许匿名访问，便于未登录浏览对方资料。
     * 分页/筛选/排序：不适用（单项拉取）。
     */
    @GetMapping("/user")
    public ResponseEntity<?> getUserPublic(@RequestParam("username") String username){
        String u = username == null ? null : username.trim();
        if (u == null || u.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message","username 不能为空"));
        }
        // 按用户名精确查询；若未来需要支持昵称回退，可在此加入 OR 条件。
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", u));
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        }
        // 仅返回公开字段，避免泄露邮箱等敏感信息。
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "signature", user.getSignature(),
                "avatarUrl", user.getAvatarUrl()
        ));
    }

    /**
     * 获取当前登录用户的个人资料。
     * @return 用户基础资料（id、username、nickname、email、signature、avatarUrl、role）
     * 分页/筛选/排序：不适用（单项拉取）。
     * 边界与安全：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 仅返回展示所需字段，避免输出敏感信息；
     * 异常策略：
     * - 用户不存在返回 404；其他异常统一转为 500 或友好提示。
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("/api/account/me auth={}, principal={}", auth, auth != null ? auth.getPrincipal() : null);
        Long uid = AuthUtil.currentUserId();
        log.info("/api/account/me uid={}", uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        java.util.Map<String,Object> resp = new java.util.HashMap<>();
        resp.put("id", u.getId());
        resp.put("username", u.getUsername());
        resp.put("nickname", u.getNickname());
        resp.put("email", u.getEmail());
        resp.put("signature", u.getSignature());
        resp.put("avatarUrl", u.getAvatarUrl());
        resp.put("role", u.getRole());
        return ResponseEntity.ok(resp);
    }

    /**
     * 上传头像并更新用户 `avatarUrl`。
     * @param file 图片文件（multipart/form-data）
     * @return 更新后的用户部分信息与新头像地址
     * 分页/筛选/排序：不适用（写入动作）。
     * 验证与限制：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 文件非空校验；大小限制 5MB；Content-Type 必须以 image/ 开头；
     * - 文件保存于 `uploads/avatars`，静态映射至 `/uploads/**`。
     * 异常策略：
     * - 保存失败返回 500；其他运行时异常统一友好提示。
     */
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message","文件不能为空"));
        if (file.getSize() > 5 * 1024 * 1024) return ResponseEntity.badRequest().body(Map.of("message","文件过大，限制 5MB"));
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) return ResponseEntity.badRequest().body(Map.of("message","仅支持图片文件"));
        try {
            String baseDir = System.getProperty("user.dir") + "/uploads/avatars";
            Path dir = Paths.get(baseDir);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            String ext = "";
            String original = file.getOriginalFilename();
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
            }
            String filename = uid + "_" + UUID.randomUUID() + (ext == null ? "" : ext);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target);
            String url = "/uploads/avatars/" + filename; // 静态资源映射
            // 更新数据库
            userMapper.update(null, new UpdateWrapper<User>().eq("id", uid).set("avatar_url", url));
            User u = userMapper.selectById(uid);
            return ResponseEntity.ok(Map.of(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "nickname", u.getNickname(),
                    "email", u.getEmail(),
                    "avatarUrl", url
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message","服务器保存头像失败"));
        }
    }

    /**
     * 绑定邮箱（基础校验与唯一性检查）。
     * @param body { email }
     * @return { ok: true } 或错误提示
     * 分页/筛选/排序：不适用（写入动作）。
     * 验证与限制：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 邮箱格式校验；与其他用户邮箱唯一性冲突返回 409；
     * 异常策略：
     * - 用户不存在返回 404；其他异常统一友好提示。
     */
    @PostMapping("/bind-email")
    public ResponseEntity<?> bindEmail(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱不能为空"));
        }
        Pattern p = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
        if (!p.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱格式不正确"));
        }
        // 检查是否被其他用户占用
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null && !exist.getId().equals(uid)) {
            return ResponseEntity.status(409).body(Map.of("message","该邮箱已使用！"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setEmail(email);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 发送绑定邮箱验证码（通过 PasswordResetService 的一次性码逻辑）。
     * @param body { email }
     * @return { ok: true } 或错误提示（含 429 限流）
     * 分页/筛选/排序：不适用（写入动作）。
     * 验证与限制：
     * - 需登录；邮箱格式校验与唯一性检查；
     * - 服务层控制发送频率与过期策略，失败返回 429；
     * 异常策略：
     * - 统一捕获运行时异常，返回友好提示。
     */
    @PostMapping("/bind-email/send-code")
    public ResponseEntity<?> sendBindEmailCode(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱不能为空"));
        }
        Pattern p = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
        if (!p.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱格式不正确"));
        }
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null && !exist.getId().equals(uid)) {
            return ResponseEntity.status(409).body(Map.of("message","该邮箱已使用！"));
        }
        try {
            resetService.createCode(email);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(429).body(Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    /**
     * 确认绑定邮箱（校验一次性验证码）。
     * @param body { email, code }
     * @return { ok: true } 或错误提示
     * 分页/筛选/排序：不适用（写入动作）。
     * 验证与限制：
     * - 需登录；邮箱与验证码非空；
     * - 验证码有效性与唯一性再检查（避免竞争条件）；
     * 异常策略：
     * - 越权/冲突返回 409；用户不存在返回 404；验证码失败返回 400；
     */
    @PostMapping("/bind-email/confirm")
    public ResponseEntity<?> confirmBindEmail(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message","邮箱与验证码不能为空"));
        }
        boolean pass = resetService.verifyCode(email, code);
        if (!pass) return ResponseEntity.badRequest().body(Map.of("message","验证码无效或已过期"));
        // 再次检查唯一性
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (exist != null && !exist.getId().equals(uid)) {
            return ResponseEntity.status(409).body(Map.of("message","该邮箱已使用！"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setEmail(email);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * 更新昵称（长度与空值处理）。
     * @param body { nickname }
     * @return 返回更新后的用户部分信息
     * 分页/筛选/排序：不适用（写入动作）。
     * 限制与校验：
     * - 需登录；昵称最长 24 位；空字符串视为取消昵称（置为 null）；
     * 异常策略：
     * - 用户不存在返回 404；其他异常统一友好提示。
     */
    @PostMapping("/update-nickname")
    public ResponseEntity<?> updateNickname(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String nickname = body.getOrDefault("nickname", "").trim();
        if (nickname.length() > 24) {
            return ResponseEntity.badRequest().body(Map.of("message","昵称最长 24 位"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setNickname(nickname.isEmpty() ? null : nickname);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "nickname", u.getNickname(),
                "email", u.getEmail(),
                "avatarUrl", u.getAvatarUrl()
        ));
    }

    /**
     * 更新个性签名（长度与空值处理）。
     * @param body { signature }
     * @return 返回更新后的用户部分信息
     * 分页/筛选/排序：不适用（写入动作）。
     * 限制与校验：
     * - 需登录；签名最长 255 字符；空字符串视为取消签名（置为 null）；
     * 异常策略：
     * - 用户不存在返回 404；其他异常统一友好提示。
     */
    @PostMapping("/update-signature")
    public ResponseEntity<?> updateSignature(@RequestBody Map<String,String> body){
        Long uid = AuthUtil.currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message","未登录"));
        String signature = body.getOrDefault("signature", "").trim();
        if (signature.length() > 255) {
            return ResponseEntity.badRequest().body(Map.of("message","个性签名最长 255 字符"));
        }
        User u = userMapper.selectById(uid);
        if (u == null) return ResponseEntity.status(404).body(Map.of("message","用户不存在"));
        u.setSignature(signature.isEmpty() ? null : signature);
        userMapper.updateById(u);
        return ResponseEntity.ok(Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "nickname", u.getNickname(),
                "email", u.getEmail(),
                "signature", u.getSignature(),
                "avatarUrl", u.getAvatarUrl()
        ));
    }

    /**
     * 调试：查看认证对象与当前用户ID解析情况（与 /me 不同，返回原始 principal 信息）。
     *
     * 用途：
     * - 联调 JWT 写入 SecurityContext 是否生效；
     * - 观察 {@code principal} 的类型（如 UserDetails/字符串）与字段；
     * - 确认 {@code AuthUtil.currentUserId()} 能否正确解析用户ID。
     *
     * 返回：
     * - 200 OK，包含：authNull、isAuthenticated、principal、principalType、currentUserId 等字段。
     *
     * 分页/筛选/排序：不适用（调试查询）。
     * 注意：仅建议在开发/测试环境启用，生产环境应限制访问或下线该端点。
     */
    @GetMapping("/me2")
    public ResponseEntity<?> me2(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        java.util.Map<String,Object> m = new java.util.HashMap<>();
        m.put("authNull", auth == null);
        m.put("isAuthenticated", auth != null && auth.isAuthenticated());
        m.put("principal", principal);
        m.put("principalType", principal != null ? principal.getClass().getSimpleName() : null);
        m.put("currentUserId", AuthUtil.currentUserId());
        return ResponseEntity.ok(m);
    }
}