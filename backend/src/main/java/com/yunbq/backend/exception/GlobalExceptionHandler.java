package com.yunbq.backend.exception;

import com.yunbq.backend.service.LogService;
import com.yunbq.backend.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 全局异常处理器：
 * - 捕获系统中未被控制器显式处理的异常，将其写入 error_logs，便于运维与审计。
 * - 返回统一的错误响应，避免泄漏内部实现细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final LogService logService;

    public GlobalExceptionHandler(LogService logService) {
        this.logService = logService;
    }

    /**
     * 兜底异常处理。
     * 注意：保持简单、统一的响应结构；详细错误信息已入库，可由管理员在后台查询。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleAll(HttpServletRequest req, Exception e) {
        Long uid = AuthUtil.currentUserId();
        String path = req.getRequestURI();
        // 关联 requestId，若为空说明异常发生在生成 requestId 之前或非 HTTP 场景。
        String requestId = (String) req.getAttribute("requestId");
        // 控制台打印简要信息，便于开发态快速定位；完整堆栈入库。
        log.error("Unhandled exception at path {}: {}", path, e.toString());
        try {
            logService.logError(uid, path, e, requestId);
        } catch (Exception ex) {
            log.warn("persist error log failed: path={} msg={}", path, ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "服务器内部错误，请稍后重试"));
    }
}