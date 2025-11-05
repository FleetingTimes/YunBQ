package com.yunbq.backend.controller;

import com.yunbq.backend.service.MessageService;
import com.yunbq.backend.util.AuthUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 消息中心接口：
 * - GET /api/messages               分页列表，支持按 type 过滤
 * - POST /api/messages/{id}/read    标记已读
 * - DELETE /api/messages/{id}       删除消息
 * - GET /api/messages/counts        未读计数（含 hasNew）
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ResponseEntity<Map<String,Object>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String type) {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] GET /api/messages list called, uid={}, page={}, size={}, type={} ", uid, page, size, type);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        return ResponseEntity.ok(messageService.list(uid, page, size, type));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String,Object>> markRead(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] POST /api/messages/{}/read called, uid={} ", id, uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        boolean ok = messageService.markRead(uid, id);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String,Object>> delete(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] DELETE /api/messages/{} called, uid={} ", id, uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        boolean ok = messageService.delete(uid, id);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    @GetMapping("/counts")
    public ResponseEntity<Map<String,Object>> unreadCounts() {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] GET /api/messages/counts called, uid={} ", uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        return ResponseEntity.ok(messageService.unreadCounts(uid));
    }
}