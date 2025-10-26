package com.yunbq.backend.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtil {
    public static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        // 兼容其他可能的类型
        if (principal instanceof Number) return ((Number) principal).longValue();
        if (principal instanceof String) {
            try { return Long.parseLong((String) principal); } catch (Exception ignored) {}
        }
        return null;
    }
}