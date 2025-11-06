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
/**
 * 消息中心接口控制器
 * 职责：
 * - 提供与用户相关的系统通知、互动提醒（点赞/收藏/评论）的拉取与已读状态更新；
 * - 支持分页拉取，兼容移动端与桌面端的统一列表展示。
 *
 * 分页/筛选/排序与边界：
 * - 列表按 `created_at DESC` 返回最近消息在前；
 * - 可按 `type` 进行可选过滤；`size` 建议设定上限（如 ≤ 50）；
 * - 服务层负责计算 `offset=(page-1)*size)` 并进行边界保护；
 *
 * 安全：
 * - 所有消息接口需要登录（个人数据）；
 * - 依据 SecurityContext 中的用户信息（Jwt）返回只属于当前用户的消息。
 */
public class MessageController {
    private static final Logger log = LoggerFactory.getLogger(MessageController.class);
    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    /**
     * 拉取消息列表（分页，可按类型过滤）。
     * @param page 页码，默认 1，最小值 1；过大页码将返回空列表
     * @param size 每页数量，默认 20，建议限制最大值以避免过载
     * @param type 可选消息类型过滤，如 like/favorite/comment/system；null 表示全部
     * @return { data: 列表, total: 总数, page, size, hasNext } 等分页结构
     * 边界与安全：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 对过大 size 进行服务层保护（限流/上限），避免一次返回过多数据；
     * 异常策略：
     * - 服务层异常转为 500 或 400，message 仅提供友好提示，不暴露内部实现。
     */
    public ResponseEntity<Map<String,Object>> list(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String type) {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] GET /api/messages list called, uid={}, page={}, size={}, type={} ", uid, page, size, type);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        return ResponseEntity.ok(messageService.list(uid, page, size, type));
    }

    @PostMapping("/{id}/read")
    /**
     * 标记单条消息为已读。
     * @param id 消息ID，必须属于当前用户
     * @return { ok: true/false }
     * 边界与安全：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 写操作仅允许更新当前用户的消息，服务层应进行归属校验；
     * 异常策略：
     * - 不存在或越权更新的消息返回 ok=false 或 404/403；
     * - 统一捕获运行时异常，返回友好提示。
     */
    public ResponseEntity<Map<String,Object>> markRead(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] POST /api/messages/{}/read called, uid={} ", id, uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        boolean ok = messageService.markRead(uid, id);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    @DeleteMapping("/{id}")
    /**
     * 删除单条消息。
     * @param id 消息ID，必须属于当前用户
     * @return { ok: true/false }
     * 边界与安全：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 删除操作仅允许当前用户的消息；
     * 异常策略：
     * - 不存在或越权删除返回 ok=false 或 404/403；
     * - 统一捕获运行时异常，返回友好提示。
     */
    public ResponseEntity<Map<String,Object>> delete(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] DELETE /api/messages/{} called, uid={} ", id, uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        boolean ok = messageService.delete(uid, id);
        return ResponseEntity.ok(Map.of("ok", ok));
    }

    @GetMapping("/counts")
    /**
     * 获取未读计数与是否有新消息标记。
     * @return { unread: 未读总数, hasNew: 是否有新消息 }，可扩展按类型的计数
     * 边界与安全：
     * - 需登录：当 `uid` 为空时返回 401；
     * - 计数逻辑应尽量轻量（索引优化或缓存），避免大表扫描。
     * 异常策略：
     * - 服务层异常转为 500，message 提供友好提示，不泄露内部信息。
     */
    public ResponseEntity<Map<String,Object>> unreadCounts() {
        Long uid = AuthUtil.currentUserId();
        log.info("[MessageController] GET /api/messages/counts called, uid={} ", uid);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        return ResponseEntity.ok(messageService.unreadCounts(uid));
    }
}