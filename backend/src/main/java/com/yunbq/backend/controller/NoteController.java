package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.model.Note;
import com.yunbq.backend.service.NoteService;
import com.yunbq.backend.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public ResponseEntity<Page<Note>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String q) {
        Long uid = AuthUtil.currentUserId();
        return ResponseEntity.ok(noteService.list(uid, page, size, q));
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
}