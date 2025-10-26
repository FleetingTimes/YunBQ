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

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @GetMapping("/auth")
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
     public ResponseEntity<?> headers(HttpServletRequest req) {
         Map<String, Object> m = new HashMap<>();
         m.put("authorization", req.getHeader("Authorization"));
         return ResponseEntity.ok(m);
     }
}