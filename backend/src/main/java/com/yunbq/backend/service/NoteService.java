package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.dto.NoteItem;
import com.yunbq.backend.mapper.NoteMapper;
import com.yunbq.backend.mapper.NoteLikeMapper;
import com.yunbq.backend.mapper.NoteFavoriteMapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.Note;
import com.yunbq.backend.model.NoteLike;
import com.yunbq.backend.model.NoteFavorite;
import com.yunbq.backend.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NoteService {
    private final NoteMapper noteMapper;
    private final NoteLikeMapper likeMapper;
    private final NoteFavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final NoteCacheService noteCache;

    public NoteService(NoteMapper noteMapper, NoteLikeMapper likeMapper, NoteFavoriteMapper favoriteMapper, UserMapper userMapper, NoteCacheService noteCache) {
        this.noteMapper = noteMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
        this.noteCache = noteCache;
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
        // 便签新增，失效热门与最近缓存
        try { noteCache.evictHotAll(); noteCache.evictRecentAll(); } catch (Exception ignored) {}
        return n;
    }

    @Transactional
    public Note update(Long userId, Long id, NoteRequest req) {
        Note n = noteMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new RuntimeException("笔记不存在或无权限");
        }
        Parsed ct = parseFromContent(req.getContent(), req.getTags());
        String oldTags = (n.getTags() == null) ? "" : n.getTags();
        String finalTags = (ct.tags == null || ct.tags.isBlank()) ? oldTags : ct.tags;
        n.setContent(ct.content);
        n.setTags(finalTags);
        n.setColor(req.getColor());
        n.setArchived(Boolean.TRUE.equals(req.getArchived()));
        n.setIsPublic(Boolean.TRUE.equals(req.getIsPublic()));
        n.setUpdatedAt(LocalDateTime.now());
        noteMapper.updateById(n);
        // 便签更新，失效热门与最近缓存
        try { noteCache.evictHotAll(); noteCache.evictRecentAll(); } catch (Exception ignored) {}
        return n;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Note n = noteMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new RuntimeException("笔记不存在或无权限");
        }
        noteMapper.deleteById(id);
        // 便签删除，失效热门与最近缓存
        try { noteCache.evictHotAll(); noteCache.evictRecentAll(); } catch (Exception ignored) {}
    }

    public Page<NoteItem> list(Long userId, int page, int size, String q, Boolean archived, Boolean isPublic, Boolean mineOnly) {
        QueryWrapper<Note> qw = new QueryWrapper<>();

        // 范围控制：
        // - 未登录：仅公开便签
        // - 已登录：默认 公开便签 + 我的全部便签；若 mineOnly=true，则仅我的便签
        boolean onlyMine = Boolean.TRUE.equals(mineOnly);
        if (userId == null) {
            qw.eq("is_public", true);
        } else if (onlyMine) {
            qw.eq("user_id", userId);
            if (isPublic != null) qw.eq("is_public", isPublic);
        } else {
            // 默认范围：公开 OR 我的
            qw.and(w -> w.eq("user_id", userId).or().eq("is_public", true));
            // 如果明确传了 isPublic=false，则仅我的私有
            if (Boolean.FALSE.equals(isPublic)) {
                qw.eq("user_id", userId).eq("is_public", false);
            } else if (Boolean.TRUE.equals(isPublic)) {
                // 仅公开（含他人公开 + 我公开）
                qw.and(w -> w.eq("is_public", true).or().eq("user_id", userId));
            }
        }

        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("content", q).or().like("tags", q));
        }
        if (archived != null) {
            qw.eq("archived", archived);
        }
        qw.orderByDesc("updated_at");
        Page<Note> np = noteMapper.selectPage(Page.of(page, size), qw);
        List<Note> records = np.getRecords();
        List<Long> ids = records.stream().map(Note::getId).collect(Collectors.toList());
        // 作者昵称映射
        Map<Long, String> authorNameMap = new HashMap<>();
        List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(authorIds);
            for (User u : users) {
                String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                authorNameMap.put(u.getId(), name);
            }
        }
    
        Map<Long, Long> likeCountMap = new HashMap<>();
        Map<Long, Long> favoriteCountMap = new HashMap<>();
        final Set<Long> likedSet;
        final Set<Long> favoritedSet;
        if (!ids.isEmpty()) {
            List<Map<String,Object>> likeCounts = likeMapper.countByNoteIds(ids);
            for (Map<String,Object> m : likeCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                likeCountMap.put(nid, cnt);
            }
            List<Long> likedIds = likeMapper.findLikedNoteIdsByUser(userId, ids);
            likedSet = likedIds.stream().collect(Collectors.toSet());

            List<Map<String,Object>> favCounts = favoriteMapper.countByNoteIds(ids);
            for (Map<String,Object> m : favCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                favoriteCountMap.put(nid, cnt);
            }
            // 仅在“我的便签”列表中，根据当前用户的收藏记录计算收藏态
            List<Long> favoritedIds = favoriteMapper.findFavoritedNoteIdsByUser(userId, ids);
            favoritedSet = favoritedIds.stream().collect(java.util.stream.Collectors.toSet());
        } else {
            likedSet = Set.of();
            favoritedSet = Set.of();
        }
    
        List<NoteItem> items = records.stream().map(n -> {
            NoteItem it = new NoteItem();
            it.setId(n.getId());
            it.setUserId(n.getUserId());
            it.setAuthorName(authorNameMap.get(n.getUserId()));
            // 移除 title 映射
            it.setContent(n.getContent());
            it.setTags(n.getTags());
            it.setColor(n.getColor());
            it.setArchived(n.getArchived());
            it.setIsPublic(n.getIsPublic());
            it.setCreatedAt(n.getCreatedAt());
            it.setUpdatedAt(n.getUpdatedAt());
            it.setLikeCount(likeCountMap.getOrDefault(n.getId(), 0L));
            it.setLikedByMe(likedSet.contains(n.getId()));
            it.setFavoriteCount(favoriteCountMap.getOrDefault(n.getId(), 0L));
            it.setFavoritedByMe(favoritedSet.contains(n.getId()));
            return it;
        }).collect(Collectors.toList());
    
        Page<NoteItem> ip = Page.of(np.getCurrent(), np.getSize());
        ip.setTotal(np.getTotal());
        ip.setRecords(items);
        return ip;
    }

    // 最近公开便签：按更新时间倒序，仅公开且未归档，返回前 size 条
    public Page<NoteItem> recentPublic(Long userId, int size) {
        if (size <= 0) size = 10;
        // 先尝试读取缓存（不含用户态）
        List<NoteItem> cached = (noteCache == null) ? null : noteCache.getRecent(size);
        List<Long> ids;
        List<NoteItem> baseItems;
        if (cached != null && !cached.isEmpty()) {
            baseItems = cached;
            ids = cached.stream().map(NoteItem::getId).collect(Collectors.toList());
        } else {
            QueryWrapper<Note> qw = new QueryWrapper<>();
            qw.eq("is_public", 1).eq("archived", 0).orderByDesc("updated_at");
            Page<Note> np = noteMapper.selectPage(Page.of(1, size), qw);
            List<Note> records = np.getRecords();
            ids = records.stream().map(Note::getId).collect(Collectors.toList());

            Map<Long, String> authorNameMap = new HashMap<>();
            List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
            if (!authorIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(authorIds);
                for (User u : users) {
                    String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                    authorNameMap.put(u.getId(), name);
                }
            }

            Map<Long, Long> likeCountMap = new HashMap<>();
            Map<Long, Long> favoriteCountMap = new HashMap<>();
            if (!ids.isEmpty()) {
                List<Map<String,Object>> likeCounts = likeMapper.countByNoteIds(ids);
                for (Map<String,Object> m : likeCounts) {
                    Long nid = ((Number)m.get("noteId")).longValue();
                    Long cnt = ((Number)m.get("cnt")).longValue();
                    likeCountMap.put(nid, cnt);
                }
                List<Map<String,Object>> favCounts = favoriteMapper.countByNoteIds(ids);
                for (Map<String,Object> m : favCounts) {
                    Long nid = ((Number)m.get("noteId")).longValue();
                    Long cnt = ((Number)m.get("cnt")).longValue();
                    favoriteCountMap.put(nid, cnt);
                }
            }

            baseItems = records.stream().map(n -> {
                NoteItem it = new NoteItem();
                it.setId(n.getId());
                it.setUserId(n.getUserId());
                it.setAuthorName(authorNameMap.get(n.getUserId()));
                it.setContent(n.getContent());
                it.setTags(n.getTags());
                it.setColor(n.getColor());
                it.setArchived(n.getArchived());
                it.setIsPublic(n.getIsPublic());
                it.setCreatedAt(n.getCreatedAt());
                it.setUpdatedAt(n.getUpdatedAt());
                it.setLikeCount(likeCountMap.getOrDefault(n.getId(), 0L));
                it.setFavoriteCount(favoriteCountMap.getOrDefault(n.getId(), 0L));
                return it;
            }).collect(Collectors.toList());
            // 写入缓存（不包含用户 liked/favorited 标记）
            try { if (noteCache != null) noteCache.setRecent(size, baseItems); } catch (Exception ignored) {}
        }

        // 用户态补充
        final Set<Long> likedSet;
        final Set<Long> favoritedSet;
        if (userId != null && userId > 0 && !ids.isEmpty()) {
            List<Long> likedIds = likeMapper.findLikedNoteIdsByUser(userId, ids);
            likedSet = likedIds.stream().collect(Collectors.toSet());
            List<Long> favoritedIds = favoriteMapper.findFavoritedNoteIdsByUser(userId, ids);
            favoritedSet = favoritedIds.stream().collect(Collectors.toSet());
        } else {
            likedSet = Set.of();
            favoritedSet = Set.of();
        }
        List<NoteItem> items = baseItems.stream().map(it -> {
            it.setLikedByMe(likedSet.contains(it.getId()));
            it.setFavoritedByMe(favoritedSet.contains(it.getId()));
            return it;
        }).collect(Collectors.toList());

        Page<NoteItem> ip = Page.of(1, size);
        ip.setTotal(items.size());
        ip.setRecords(items);
        return ip;
    }

    // 热门公开便签：按综合热度排序（收藏、点赞、时效加权），仅公开且未归档
    public Page<NoteItem> hotPublic(Long userId, int size, int days) {
        if (size <= 0) size = 10;
        if (days <= 0) days = 30;
        // 先读取缓存（不含用户态）
        List<NoteItem> cached = (noteCache == null) ? null : noteCache.getHot(size);
        List<Long> ids;
        List<NoteItem> baseItems;
        if (cached != null && !cached.isEmpty()) {
            baseItems = cached;
            ids = cached.stream().map(NoteItem::getId).collect(Collectors.toList());
        } else {
            LocalDateTime since = LocalDateTime.now().minus(days, ChronoUnit.DAYS);
            // 先取最近 N 天的公开便签（最多 300 条）
            QueryWrapper<Note> qw = new QueryWrapper<>();
            qw.eq("is_public", 1).eq("archived", 0).ge("updated_at", since).orderByDesc("updated_at");
            Page<Note> np = noteMapper.selectPage(Page.of(1, 300), qw);
            List<Note> records = np.getRecords();
            if (records.isEmpty()) {
                Page<NoteItem> empty = Page.of(1, size);
                empty.setTotal(0);
                empty.setRecords(java.util.Collections.emptyList());
                return empty;
            }
            ids = records.stream().map(Note::getId).collect(Collectors.toList());

            Map<Long, String> authorNameMap = new HashMap<>();
            List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
            if (!authorIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(authorIds);
                for (User u : users) {
                    String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                    authorNameMap.put(u.getId(), name);
                }
            }

            Map<Long, Long> likeCountMap = new HashMap<>();
            Map<Long, Long> favoriteCountMap = new HashMap<>();
            List<Map<String,Object>> likeCounts = likeMapper.countByNoteIds(ids);
            for (Map<String,Object> m : likeCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                likeCountMap.put(nid, cnt);
            }
            List<Map<String,Object>> favCounts = favoriteMapper.countByNoteIds(ids);
            for (Map<String,Object> m : favCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                favoriteCountMap.put(nid, cnt);
            }

            // 计算综合热度分：收藏权重 1.0，点赞权重 0.5，时效加权 0.2~1.0
            Map<Long, Double> scoreMap = new HashMap<>();
            for (Note n : records) {
                long like = likeCountMap.getOrDefault(n.getId(), 0L);
                long fav = favoriteCountMap.getOrDefault(n.getId(), 0L);
                long hours = ChronoUnit.HOURS.between(n.getUpdatedAt(), LocalDateTime.now());
                double decay = Math.max(0.2, 1.0 - (hours / (24.0 * days))); // 越新加权越高
                double score = fav * 1.0 + like * 0.5 + decay;
                scoreMap.put(n.getId(), score);
            }
            List<Note> sorted = records.stream()
                    .sorted((a, b) -> Double.compare(scoreMap.getOrDefault(b.getId(), 0.0), scoreMap.getOrDefault(a.getId(), 0.0)))
                    .limit(size)
                    .collect(Collectors.toList());
            baseItems = sorted.stream().map(n -> {
                NoteItem it = new NoteItem();
                it.setId(n.getId());
                it.setUserId(n.getUserId());
                it.setAuthorName(authorNameMap.get(n.getUserId()));
                it.setContent(n.getContent());
                it.setTags(n.getTags());
                it.setColor(n.getColor());
                it.setArchived(n.getArchived());
                it.setIsPublic(n.getIsPublic());
                it.setCreatedAt(n.getCreatedAt());
                it.setUpdatedAt(n.getUpdatedAt());
                it.setLikeCount(likeCountMap.getOrDefault(n.getId(), 0L));
                it.setFavoriteCount(favoriteCountMap.getOrDefault(n.getId(), 0L));
                return it;
            }).collect(Collectors.toList());
            try { if (noteCache != null) noteCache.setHot(size, baseItems); } catch (Exception ignored) {}
        }

        // 用户态补充
        final Set<Long> likedSet;
        final Set<Long> favoritedSet;
        if (userId != null && userId > 0 && !ids.isEmpty()) {
            List<Long> likedIds = likeMapper.findLikedNoteIdsByUser(userId, ids);
            likedSet = likedIds.stream().collect(Collectors.toSet());
            List<Long> favoritedIds = favoriteMapper.findFavoritedNoteIdsByUser(userId, ids);
            favoritedSet = favoritedIds.stream().collect(Collectors.toSet());
        } else {
            likedSet = Set.of();
            favoritedSet = Set.of();
        }
        List<NoteItem> items = baseItems.stream().map(it -> {
            it.setLikedByMe(likedSet.contains(it.getId()));
            it.setFavoritedByMe(favoritedSet.contains(it.getId()));
            return it;
        }).collect(Collectors.toList());

        Page<NoteItem> ip = Page.of(1, size);
        ip.setTotal(items.size());
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
        // 点赞变化影响热门，失效热门缓存
        try { if (noteCache != null) noteCache.evictHotAll(); } catch (Exception ignored) {}
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
        try { if (noteCache != null) noteCache.evictHotAll(); } catch (Exception ignored) {}
        return Map.of("count", count, "likedByMe", false);
    }

    public Map<String, Object> likeInfo(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        long count = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId));
        boolean likedByMe = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId).eq("user_id", userId)) > 0;
        return Map.of("count", count, "likedByMe", likedByMe);
    }

    // 收藏相关
    @Transactional
    public Map<String, Object> favorite(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        // 私有便签仅作者本人可收藏；公开便签任何登录用户可收藏
        if (!Boolean.TRUE.equals(n.getIsPublic()) && !n.getUserId().equals(userId)) {
            throw new RuntimeException("私有便签仅作者可操作");
        }
        QueryWrapper<NoteFavorite> qw = new QueryWrapper<>();
        qw.eq("note_id", noteId).eq("user_id", userId);
        Long exists = favoriteMapper.selectCount(qw);
        if (exists == null || exists == 0) {
            NoteFavorite f = new NoteFavorite();
            f.setNoteId(noteId);
            f.setUserId(userId);
            f.setCreatedAt(LocalDateTime.now());
            favoriteMapper.insert(f);
        }
        long count = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId));
        // 收藏变化影响热门，失效热门缓存
        try { if (noteCache != null) noteCache.evictHotAll(); } catch (Exception ignored) {}
        return Map.of("count", count, "favoritedByMe", true);
    }

    @Transactional
    public Map<String, Object> unfavorite(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        if (!Boolean.TRUE.equals(n.getIsPublic()) && !n.getUserId().equals(userId)) {
            throw new RuntimeException("私有便签仅作者可操作");
        }
        favoriteMapper.delete(new QueryWrapper<NoteFavorite>().eq("note_id", noteId).eq("user_id", userId));
        long count = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId));
        try { if (noteCache != null) noteCache.evictHotAll(); } catch (Exception ignored) {}
        return Map.of("count", count, "favoritedByMe", false);
    }

    public Map<String, Object> favoriteInfo(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        long count = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId));
        boolean favoritedByMe = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId).eq("user_id", userId)) > 0;
        return Map.of("count", count, "favoritedByMe", favoritedByMe);
    }

    public Page<NoteItem> listFavorited(Long userId, int page, int size, String q) {
        // 先取用户已收藏的 note_id 集合，再按条件筛选便签并分页
        List<Long> favIds = favoriteMapper.selectList(new QueryWrapper<NoteFavorite>().eq("user_id", userId))
                .stream().map(NoteFavorite::getNoteId).collect(Collectors.toList());
        if (favIds == null || favIds.isEmpty()) {
            Page<NoteItem> empty = Page.of(page, size);
            empty.setTotal(0);
            empty.setRecords(java.util.Collections.emptyList());
            return empty;
        }
        QueryWrapper<Note> qw = new QueryWrapper<>();
        qw.in("id", favIds);
        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("content", q).or().like("tags", q));
        }
        qw.orderByDesc("updated_at");
        Page<Note> np = noteMapper.selectPage(Page.of(page, size), qw);
        List<Note> records = np.getRecords();
        List<Long> ids = records.stream().map(Note::getId).collect(Collectors.toList());
        // 作者昵称映射
        Map<Long, String> authorNameMap = new HashMap<>();
        List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(authorIds);
            for (User u : users) {
                String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                authorNameMap.put(u.getId(), name);
            }
        }

        Map<Long, Long> likeCountMap = new HashMap<>();
        Map<Long, Long> favoriteCountMap = new HashMap<>();
        final Set<Long> likedSet;
        final Set<Long> favoritedSet;
        if (!ids.isEmpty()) {
            List<Map<String,Object>> likeCounts = likeMapper.countByNoteIds(ids);
            for (Map<String,Object> m : likeCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                likeCountMap.put(nid, cnt);
            }
            List<Long> likedIds = likeMapper.findLikedNoteIdsByUser(userId, ids);
            likedSet = likedIds.stream().collect(Collectors.toSet());

            List<Map<String,Object>> favCounts = favoriteMapper.countByNoteIds(ids);
            for (Map<String,Object> m : favCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                favoriteCountMap.put(nid, cnt);
            }
            List<Long> favoritedIds = favoriteMapper.findFavoritedNoteIdsByUser(userId, ids);
            favoritedSet = favoritedIds.stream().collect(Collectors.toSet());
        } else {
            likedSet = Set.of();
            favoritedSet = Set.of();
        }

        List<NoteItem> items = records.stream().map(n -> {
            NoteItem it = new NoteItem();
            it.setId(n.getId());
            it.setUserId(n.getUserId());
            it.setAuthorName(authorNameMap.get(n.getUserId()));
            it.setContent(n.getContent());
            it.setTags(n.getTags());
            it.setColor(n.getColor());
            it.setArchived(n.getArchived());
            it.setIsPublic(n.getIsPublic());
            it.setCreatedAt(n.getCreatedAt());
            it.setUpdatedAt(n.getUpdatedAt());
            it.setLikeCount(likeCountMap.getOrDefault(n.getId(), 0L));
            it.setLikedByMe(likedSet.contains(n.getId()));
            it.setFavoriteCount(favoriteCountMap.getOrDefault(n.getId(), 0L));
            it.setFavoritedByMe(favoritedSet.contains(n.getId()));
            return it;
        }).collect(Collectors.toList());

        Page<NoteItem> ip = Page.of(np.getCurrent(), np.getSize());
        ip.setTotal(np.getTotal());
        ip.setRecords(items);
        return ip;
    }

    // 已移除：标签统计接口实现

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