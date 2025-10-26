package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.mapper.NoteMapper;
import com.yunbq.backend.model.Note;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NoteService {
    private final NoteMapper noteMapper;

    public NoteService(NoteMapper noteMapper) {
        this.noteMapper = noteMapper;
    }

    @Transactional
    public Note create(Long userId, NoteRequest req) {
        Note n = new Note();
        n.setUserId(userId);
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setTags(req.getTags());
        n.setArchived(Boolean.TRUE.equals(req.getArchived()));
        n.setIsPublic(Boolean.TRUE.equals(req.getIsPublic()));
        n.setCreatedAt(LocalDateTime.now());
        n.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(n);
        return n;
    }

    @Transactional
    public Note update(Long userId, Long id, NoteRequest req) {
        Note n = noteMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new RuntimeException("笔记不存在或无权限");
        }
        n.setTitle(req.getTitle());
        n.setContent(req.getContent());
        n.setTags(req.getTags());
        n.setArchived(Boolean.TRUE.equals(req.getArchived()));
        n.setIsPublic(Boolean.TRUE.equals(req.getIsPublic()));
        n.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(n);
        return n;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Note n = noteMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new RuntimeException("笔记不存在或无权限");
        }
        noteMapper.deleteById(id);
    }

    public Page<Note> list(Long userId, int page, int size, String q) {
        QueryWrapper<Note> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("title", q).or().like("content", q).or().like("tags", q));
        }
        qw.orderByDesc("updated_at");
        return noteMapper.selectPage(Page.of(page, size), qw);
    }
}