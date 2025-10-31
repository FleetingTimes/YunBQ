package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.dto.NoteItem;
import com.yunbq.backend.dto.PageResult;
import com.yunbq.backend.model.Note;
import com.yunbq.backend.service.NoteService;
import com.yunbq.backend.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;
    // 说明：加入日志记录用于观测控制层是否成功接收到请求以及关键参数（方法、路径、用户ID、目标ID等）。
    // 这有助于前后端联调时快速定位是“未到达控制器”还是“控制器执行过程中出现业务错误”。
    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ResponseEntity<PageResult<NoteItem>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) Boolean archived,
                                           @RequestParam(required = false) Boolean isPublic,
                                           @RequestParam(required = false) Boolean mineOnly) {
        Long uid = AuthUtil.currentUserId();
        // 详细注释：记录列表查询的入参及当前用户，以便确认请求是否达到控制器。
        log.info("[NoteController] GET /api/notes list called, uid={}, page={}, size={}, q={}, archived={}, isPublic={}, mineOnly={}",
                uid, page, size, q, archived, isPublic, mineOnly);
        Page<NoteItem> p = noteService.list(uid, page, size, q, archived, isPublic, mineOnly);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    public ResponseEntity<Note> create(@Valid @RequestBody NoteRequest req) {
        Long uid = AuthUtil.currentUserId();
        // 说明：避免直接访问请求体的字段导致编译问题，仅记录已接收到创建请求与当前用户。
        log.info("[NoteController] POST /api/notes create called, uid={}", uid);
        return ResponseEntity.ok(noteService.create(uid, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Note> update(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        Long uid = AuthUtil.currentUserId();
        // 说明：同上，避免访问请求体中的具体字段，保留最有用的参数用于审计。
        log.info("[NoteController] PUT /api/notes/{} update called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.update(uid, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] DELETE /api/notes/{} delete called, uid={}", id, uid);
        noteService.delete(uid, id);
        return ResponseEntity.noContent().build();
    }

    // 点赞接口
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String,Object>> like(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        // 详细注释：当点击“喜欢”图标触发点赞时，该日志能证明控制层方法被调用，以及当前用户与目标便签ID。
        log.info("[NoteController] POST /api/notes/{}/like called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.like(uid, id));
    }

    @PostMapping("/{id}/unlike")
    public ResponseEntity<Map<String,Object>> unlike(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] POST /api/notes/{}/unlike called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.unlike(uid, id));
    }

    @GetMapping("/{id}/likes")
    public ResponseEntity<Map<String,Object>> likeInfo(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/{}/likes called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.likeInfo(uid, id));
    }

    // 收藏接口
    @PostMapping("/{id}/favorite")
    public ResponseEntity<Map<String,Object>> favorite(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] POST /api/notes/{}/favorite called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.favorite(uid, id));
    }

    @PostMapping("/{id}/unfavorite")
    public ResponseEntity<Map<String,Object>> unfavorite(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] POST /api/notes/{}/unfavorite called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.unfavorite(uid, id));
    }

    @GetMapping("/{id}/favorites")
    public ResponseEntity<Map<String,Object>> favoriteInfo(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/{}/favorites called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.favoriteInfo(uid, id));
    }

    @GetMapping("/favorites")
    public ResponseEntity<PageResult<NoteItem>> listFavorited(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String q) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/favorites called, uid={}, page={}, size={}, q={} ", uid, page, size, q);
        Page<NoteItem> p = noteService.listFavorited(uid, page, size, q);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    /**
     * 列出当前用户点过赞的便签（分页）
     * 说明：
     * - 与 /favorites 类似，通过用户点赞记录反查便签并分页返回；
     * - 返回的每条记录包含作者昵称、点赞/收藏数量以及“我是否已点赞/已收藏”等用户态标记；
     * - GET 接口允许匿名访问，但仅在携带 token 时才能计算“我是否已点赞/已收藏”，匿名时两者均为 false。
     */
    @GetMapping("/liked")
    public ResponseEntity<PageResult<NoteItem>> listLiked(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String q) {
        Long uid = AuthUtil.currentUserId();
        // 详细注释：用于确认“喜欢页”列表请求是否抵达控制器，若 uid 为空（匿名）则只返回公开数据；携带 token 则可计算用户态标记。
        log.info("[NoteController] GET /api/notes/liked called, uid={}, page={}, size={}, q={} ", uid, page, size, q);
        Page<NoteItem> p = noteService.listLiked(uid, page, size, q);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    // 最近公开便签（匿名可访问）
    @GetMapping("/recent")
    public ResponseEntity<PageResult<NoteItem>> recent(@RequestParam(defaultValue = "10") int size) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/recent called, uid={}, size={} ", uid, size);
        Page<NoteItem> p = noteService.recentPublic(uid, size);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    // 热门公开便签（按综合热度排序，匿名可访问）
    @GetMapping("/hot")
    public ResponseEntity<PageResult<NoteItem>> hot(@RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(defaultValue = "30") int days) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/hot called, uid={}, size={}, days={} ", uid, size, days);
        Page<NoteItem> p = noteService.hotPublic(uid, size, days);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }
}