package com.yunbq.backend.controller;

import com.yunbq.backend.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;

/**
 * 调试辅助接口（仅开发/联调阶段使用）
 * 用途：
 * - 检查当前安全上下文与用户ID解析情况；
 * - 模拟 AccountController 的登录判断分支；
 * - 读取请求头（如 Authorization）以确认前端是否正确携带 Token。
 * 注意：
 * - 该控制器应仅在开发环境开放；生产环境下建议移除或进行严格限制。
 */
@RestController
@RequestMapping("/api/debug")
/**
 * 调试控制器
 * 职责：
 * - 提供开发/测试期间的简单探针接口（如当前配置、时间、回显等）；
 * - 仅用于开发场景，避免在生产暴露敏感信息。
 * 安全：
 * - 建议在生产环境禁用或限制访问；
 * - 返回内容需审慎，以防信息泄露。
 */
public class DebugController {

    @GetMapping("/auth")
    /**
     * 调试：查看当前认证信息与用户ID解析结果。
     *
     * 用途：
     * - 联调期间快速确认后端是否正确读取 SecurityContext；
     * - 验证 {@code AuthUtil.currentUserId()} 能否正确解析 JWT 中的用户信息。
     *
     * 返回：
     * - 200 OK，包含以下字段：
     *   - {@code isAuthenticated}：是否通过 Spring Security 认证；
     *   - {@code authName}：认证名（通常为用户名或主体标识）；
     *   - {@code principal}：认证主体对象（类型可能为 UserDetails 或字符串等）；
     *   - {@code principalType}：主体对象的类型名称；
     *   - {@code authorities}：角色/权限集合；
     *   - {@code currentUserId}：通过 AuthUtil 解析出的用户ID（可能为 null）。
     *
     * 安全：仅用于开发/联调环境，生产环境建议禁用或严格限制访问。
     */
    public ResponseEntity<?> debugAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long currentUserId = AuthUtil.currentUserId();
        
        Map<String, Object> debugInfo = new HashMap<>();
        debugInfo.put("isAuthenticated", auth != null && auth.isAuthenticated());
        debugInfo.put("authName", auth != null ? auth.getName() : null);
        debugInfo.put("principal", auth != null ? auth.getPrincipal() : null);
        debugInfo.put("principalType", auth != null && auth.getPrincipal() != null ? 
                     auth.getPrincipal().getClass().getSimpleName() : null);
        debugInfo.put("authorities", auth != null ? auth.getAuthorities() : null);
        debugInfo.put("currentUserId", currentUserId);
        
        return ResponseEntity.ok(debugInfo);
    }
    
    @GetMapping("/me-debug")
    /**
     * 调试：模拟 {@code AccountController.me()} 的登录判断分支。
     *
     * 行为：
     * - 返回解析到的 {@code uid} 以及该值是否为 null（表示未登录）；
     * - 未登录时，返回提示“would return 401”，用于前端判断联调行为；
     * - 登录时，返回提示“would query user {uid}”，但不执行真实数据库查询。
     *
     * 返回：200 OK，包含上述调试字段与提示文本。
     */
     public ResponseEntity<?> debugMe() {
         // 完全复制 AccountController.me() 的逻辑
         Long uid = AuthUtil.currentUserId();
         Map<String, Object> debugInfo = new HashMap<>();
         debugInfo.put("uid", uid);
         debugInfo.put("uidIsNull", uid == null);
         
         if (uid == null) {
             debugInfo.put("result", "would return 401");
             return ResponseEntity.ok(debugInfo);
         }
         
         // 这里我们不查询数据库，只返回调试信息
         debugInfo.put("result", "would query user " + uid);
         return ResponseEntity.ok(debugInfo);
     }
     
     @GetMapping("/headers")
    /**
     * 调试：查看请求头中的关键字段。
     *
     * 用途：
     * - 确认前端是否正确携带 {@code Authorization} 头；
     * - 用于定位跨域或代理层是否移除了认证头的场景。
     *
     * 安全：
     * - 返回敏感头部信息需谨慎，建议仅在开发环境使用；
     * - 不记录完整 Token 至日志，避免泄露风险。
     *
     * @param req 当前请求对象，由 Spring 注入
     * @return 200 OK，返回 { authorization: <原样值> }
     */
     public ResponseEntity<?> headers(HttpServletRequest req) {
         Map<String, Object> m = new HashMap<>();
         m.put("authorization", req.getHeader("Authorization"));
         return ResponseEntity.ok(m);
     }
}