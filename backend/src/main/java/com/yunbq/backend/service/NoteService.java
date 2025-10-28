package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.dto.NoteItem;
import com.yunbq.backend.mapper.NoteMapper;
import com.yunbq.backend.mapper.NoteLikeMapper;
import com.yunbq.backend.model.Note;
import com.yunbq.backend.model.NoteLike;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoteService {
    private final NoteMapper noteMapper;
    private final NoteLikeMapper likeMapper;

    public NoteService(NoteMapper noteMapper, NoteLikeMapper likeMapper) {
        this.noteMapper = noteMapper;
        this.likeMapper = likeMapper;
    }

    @Transactional
    public Note create(Long userId, NoteRequest req) {
        // 兼容：若未显式提供 tags，则从 content 中解析 #标签（逗号分隔），并清理内容
        Parsed ct = parseFromContent(req.getContent(), req.getTags());
        Note n = new Note();
        n.setUserId(userId);
        // 移除 title 引用
        n.setContent(req.getContent());
        n.setTags(req.getTags());
        n.setColor(req.getColor());
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
        // 兼容：若未显式提供 tags，则从 content 中解析 #标签（逗号分隔）；若仍为空则保留原标签
        Parsed ct = parseFromContent(req.getContent(), req.getTags());
        String finalTags = (ct.tags == null || ct.tags.isBlank()) ? (n.getTags() == null ? "" : n.getTags()) : ct.tags;
        // 移除 title 引用
<<<<<<< HEAD
        n.setContent(ct.content);
        n.setTags(finalTags);
=======
        n.setContent(req.getContent());
        n.setTags(req.getTags());
>>>>>>> 33e1ff3ce6d549a37c62a6a9792aa5b54a1393ef
        n.setColor(req.getColor());
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

    public Page<NoteItem> list(Long userId, int page, int size, String q, Boolean archived, Boolean isPublic) {
        QueryWrapper<Note> qw = new QueryWrapper<>();
        qw.eq("user_id", userId);
        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("content", q).or().like("tags", q));
        }
        if (archived != null) {
            qw.eq("archived", archived);
        }
        if (isPublic != null) {
            qw.eq("is_public", isPublic);
        }
        qw.orderByDesc("updated_at");
        Page<Note> np = noteMapper.selectPage(Page.of(page, size), qw);
        List<Note> records = np.getRecords();
        List<Long> ids = records.stream().map(Note::getId).collect(Collectors.toList());
    
        Map<Long, Long> countMap = new HashMap<>();
        final Set<Long> likedSet;
        if (!ids.isEmpty()) {
            List<Map<String,Object>> counts = likeMapper.countByNoteIds(ids);
            for (Map<String,Object> m : counts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                countMap.put(nid, cnt);
            }
            List<Long> likedIds = likeMapper.findLikedNoteIdsByUser(userId, ids);
            likedSet = likedIds.stream().collect(Collectors.toSet());
        } else {
            likedSet = Set.of();
        }
    
        List<NoteItem> items = records.stream().map(n -> {
            NoteItem it = new NoteItem();
            it.setId(n.getId());
            it.setUserId(n.getUserId());
            // 移除 title 映射
            it.setContent(n.getContent());
            it.setTags(n.getTags());
            it.setColor(n.getColor());
            it.setArchived(n.getArchived());
            it.setIsPublic(n.getIsPublic());
            it.setCreatedAt(n.getCreatedAt());
            it.setUpdatedAt(n.getUpdatedAt());
            it.setLikeCount(countMap.getOrDefault(n.getId(), 0L));
            it.setLikedByMe(likedSet.contains(n.getId()));
            return it;
        }).collect(Collectors.toList());
    
        Page<NoteItem> ip = Page.of(np.getCurrent(), np.getSize());
        ip.setTotal(np.getTotal());
        ip.setRecords(items);
        return ip;
    }

    // 点赞相关
    @Transactional
    public Map<String, Object> like(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        // 私有便签仅作者本人可点赞；公开便签任何登录用户可点赞
        if (!Boolean.TRUE.equals(n.getIsPublic()) && !n.getUserId().equals(userId)) {
            throw new RuntimeException("私有便签仅作者可操作");
        }
        QueryWrapper<NoteLike> qw = new QueryWrapper<>();
        qw.eq("note_id", noteId).eq("user_id", userId);
        Long exists = likeMapper.selectCount(qw);
        if (exists == null || exists == 0) {
            NoteLike l = new NoteLike();
            l.setNoteId(noteId);
            l.setUserId(userId);
            l.setCreatedAt(LocalDateTime.now());
            likeMapper.insert(l);
        }
        long count = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId));
        return Map.of("count", count, "likedByMe", true);
    }

    @Transactional
    public Map<String, Object> unlike(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        if (!Boolean.TRUE.equals(n.getIsPublic()) && !n.getUserId().equals(userId)) {
            throw new RuntimeException("私有便签仅作者可操作");
        }
        likeMapper.delete(new QueryWrapper<NoteLike>().eq("note_id", noteId).eq("user_id", userId));
        long count = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId));
        return Map.of("count", count, "likedByMe", false);
    }

    public Map<String, Object> likeInfo(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        long count = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId));
        boolean likedByMe = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId).eq("user_id", userId)) > 0;
        return Map.of("count", count, "likedByMe", likedByMe);
    }

    // ========= 解析工具 =========
    private static class Parsed {
        final String content;
        final String tags;
        Parsed(String c, String t){ this.content = c; this.tags = t; }
    }
    /**
     * 若 tags 为空，尝试从 content 中提取以 # 开头、逗号分隔的标签；并将这些标签从内容中移除。
     */
    private static Parsed parseFromContent(String rawContent, String rawTags){
        String content = (rawContent == null) ? "" : rawContent;
        String tags = (rawTags == null) ? "" : rawTags;
        if (tags != null && !tags.isBlank()) {
            return new Parsed(content, normalizeTags(tags));
        }
        // 提取 #标签
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("#([\\p{L}\\w-]+)", java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);
        java.util.regex.Matcher m = p.matcher(content);
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        int found = 0;
        while (m.find()) {
            String tag = m.group(1);
            if (tag != null && !tag.isBlank()) { set.add(tag.trim()); found++; }
        }
        String joined = String.join(",", set);
        if (found == 0) {
            // 未从内容解析出标签时，不清理内容，直接返回原内容与规范化的 tags（可能为空）
            return new Parsed(content, normalizeTags(tags));
        }
        // 清理内容：移除 #标签 及其后可能的逗号与空白
        String cleaned = content.replaceAll("\\s*#([\\p{L}\\w-]+)\\s*(,\\s*)?", " ")
                                .replaceAll("\\s{2,}", " ")
                                .trim();
        return new Parsed(cleaned, joined);
    }
    private static String normalizeTags(String s){
        if (s == null) return "";
        String[] parts = s.split(",");
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (String part : parts){
            if (part == null) continue;
            String t = part.trim().replaceFirst("^#", "");
            if (!t.isBlank()) set.add(t);
        }
        return String.join(",", set);
    }
}