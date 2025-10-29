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

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

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
        return ResponseEntity.ok(noteService.create(uid, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Note> update(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.update(uid, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        noteService.delete(uid, id);
        return ResponseEntity.noContent().build();
    }

    // 点赞接口
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String,Object>> like(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.like(uid, id));
    }

    @PostMapping("/{id}/unlike")
    public ResponseEntity<Map<String,Object>> unlike(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.unlike(uid, id));
    }

    @GetMapping("/{id}/likes")
    public ResponseEntity<Map<String,Object>> likeInfo(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.likeInfo(uid, id));
    }

    // 收藏接口
    @PostMapping("/{id}/favorite")
    public ResponseEntity<Map<String,Object>> favorite(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.favorite(uid, id));
    }

    @PostMapping("/{id}/unfavorite")
    public ResponseEntity<Map<String,Object>> unfavorite(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.unfavorite(uid, id));
    }

    @GetMapping("/{id}/favorites")
    public ResponseEntity<Map<String,Object>> favoriteInfo(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.favoriteInfo(uid, id));
    }

    @GetMapping("/favorites")
    public ResponseEntity<PageResult<NoteItem>> listFavorited(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String q) {
        Long uid = AuthUtil.currentUserId();
        Page<NoteItem> p = noteService.listFavorited(uid, page, size, q);
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
        Page<NoteItem> p = noteService.hotPublic(uid, size, days);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }
}