package com.yunbq.backend.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 认证工具：从 SecurityContextHolder 中提取当前用户ID
 * 说明：
 * - JwtAuthenticationFilter 将用户ID（Long）作为 principal 放入安全上下文；
 * - 为兼容其他可能的 principal 类型，这里做了 Number/String 的宽松解析；
 * - 未认证或无法解析时返回 null，调用方应据此判断是否需要 401 响应或匿名处理。
 */
public class AuthUtil {
    /**
     * 获取当前认证用户的ID。
     * 返回：
     * - Long：已认证用户的唯一ID；
     * - null：未认证或无法解析 principal 时返回。
     * 解析策略：
     * - 支持 Long/Number/String 三种 principal 类型，String 将尝试转换为 Long；
     * - 宽松解析是为兼容不同认证来源（如表单登录与JWT）。
     * 边界与注意：
     * - 当 `SecurityContextHolder.getContext().getAuthentication()` 为 null 时直接返回 null；
     * - 安全上下文按线程隔离（ThreadLocal），无需额外同步；
     * - 调用方应据返回值判断是否需要返回 401 或进行匿名处理。
     */
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