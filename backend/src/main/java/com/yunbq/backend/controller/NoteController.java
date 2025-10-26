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
                                           @RequestParam(required = false) Boolean isPublic) {
        Long uid = AuthUtil.currentUserId();
        Page<NoteItem> p = noteService.list(uid, page, size, q, archived, isPublic);
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
}