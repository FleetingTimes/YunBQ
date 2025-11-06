package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.dto.NoteItem;
import org.springframework.util.StringUtils;
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
/**
 * 拾言服务（NoteService）
 * 职责：
 * - 管理便签的增删改查与分页检索；
 * - 提供公开便签的“最近/热门”聚合视图，并与 {@link NoteCacheService} 协作做轻量缓存；
 * - 处理点赞与收藏交互，并在成功时通过 {@link MessageService} 生成行为消息通知作者；
 * - 支持批量导入简单的 NoteRequest 列表。
 *
 * 设计要点：
 * - 缓存失效：便签的新增/更新/删除、点赞/收藏变化均会影响热门/最近列表，需要按粒度失效对应缓存键空间；
 * - 权限与可见性：公开便签任何登录用户可互动；私有便签仅作者可互动（点赞/收藏）；
 * - 查询范围：登录态下默认返回“我的全部”与“他人公开”的合并视图，支持参数限定仅公开或仅私有；
 * - 头像与作者名：为前端提供作者昵称与头像相对路径，前端负责拼接完整 URL 并兜底默认头像；
 * - JPA/Mapper：使用 MyBatis-Plus 的 QueryWrapper 与 Page 实现分页与聚合统计。
 */
public class NoteService {
    private final NoteMapper noteMapper;
    private final NoteLikeMapper likeMapper;
    private final NoteFavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final NoteCacheService noteCache;
    // 消息服务：在点赞/收藏成功后生成行为消息，通知作者
    private final MessageService messageService;

    /**
     * 构造函数：通过 Spring 注入依赖。
     * 新增参数 messageService 用于在点赞/收藏成功后写入消息。
     */
    public NoteService(NoteMapper noteMapper, NoteLikeMapper likeMapper, NoteFavoriteMapper favoriteMapper, UserMapper userMapper, NoteCacheService noteCache, MessageService messageService) {
        this.noteMapper = noteMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
        this.noteCache = noteCache;
        this.messageService = messageService;
    }

    @Transactional
    /**
     * 创建便签
     * 行为：
     * - 将请求体中的内容与标签进行最小解析与标准化（如从内容中解析 #标签），
     * - 写入数据库并设置创建/更新时间；
     * - 在成功后失效热门与最近相关缓存键。
     *
     * 参数：
     * - userId：当前用户 ID（便签作者）；
     * - req：便签请求体（包含 content/tags/color/archived/isPublic）。
     *
     * 返回：
     * - 新创建的 {@link Note} 实体对象。
     *
     * 异常：
     * - 运行时异常：在不可预期错误时可能抛出，调用方应在控制层统一处理。
     */
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
    /**
     * 更新便签
     * 行为：
     * - 校验便签存在性和归属（仅作者可更新）；
     * - 将请求体与旧值合并，解析内容中的标签并标准化；
     * - 更新数据库记录与更新时间；
     * - 在成功后失效热门与最近相关缓存键。
     *
     * 参数：
     * - userId：当前用户 ID（必须为作者）；
     * - id：便签 ID；
     * - req：更新请求体（允许部分字段为空）。
     *
     * 返回：
     * - 更新后的 {@link Note} 实体对象。
     *
     * 异常：
     * - RuntimeException：便签不存在或无权限时抛出。
     */
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
    /**
     * 删除便签
     * 行为：
     * - 校验便签存在性和归属（仅作者可删除）；
     * - 删除数据库记录；
     * - 在成功后失效热门与最近相关缓存键。
     *
     * 参数：
     * - userId：当前用户 ID（必须为作者）；
     * - id：便签 ID。
     *
     * 异常：
     * - RuntimeException：便签不存在或无权限时抛出。
     */
    public void delete(Long userId, Long id) {
        Note n = noteMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new RuntimeException("笔记不存在或无权限");
        }
        noteMapper.deleteById(id);
        // 便签删除，失效热门与最近缓存
        try { noteCache.evictHotAll(); noteCache.evictRecentAll(); } catch (Exception ignored) {}
    }

    /**
     * 分页检索便签列表
     * 说明：
     * - 未登录用户仅可见公开便签；登录用户默认可见“他人公开 + 我的全部”；
     * - 支持使用 mineOnly、isPublic、archived、q 进行范围与筛选；
     * - 结果包含作者昵称与头像相对路径，以及点赞/收藏统计与当前用户态标记。
     *
     * 参数：
     * - userId：当前用户 ID，未登录可为 null；
     * - page/size：分页参数；
     * - q：关键词（匹配 content 与 tags）；
     * - archived：是否归档筛选；
     * - isPublic：可见性筛选（true 仅公开；false 仅我的私有；null 为默认行为）；
     * - mineOnly：是否仅我的便签。
     *
     * 返回：
     * - MyBatis-Plus {@link Page} 包装的 {@link NoteItem} 列表与分页信息。
     */
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
            // 默认范围（登录态）：公开 OR 我的
            // 说明：当未显式要求仅公开或仅私有时，展示“他人公开 + 我的全部”以提升信息密度。
            qw.and(w -> w.eq("user_id", userId).or().eq("is_public", true));
            // 显式筛选：
            // - isPublic=false → 仅我的私有便签；
            // - isPublic=true  → 仅公开便签（不包含“我的私有”）。
            if (Boolean.FALSE.equals(isPublic)) {
                qw.eq("user_id", userId).eq("is_public", false);
            } else if (Boolean.TRUE.equals(isPublic)) {
                // 修正：此前误用了 (is_public=true OR user_id=userId)，导致包含“我的私有”。
                // 现在严格限定仅公开，符合“公开拾言”页面预期与前端传参。
                qw.eq("is_public", true);
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
        // 新增：作者头像映射（user_id -> avatar_url）。
        // 说明：数据库中存的是相对路径（如 "/uploads/avatars/xxx.jpg"），
        // 前端会使用 avatarFullUrl(base 去掉 /api 前缀) 拼接为完整 URL。
        Map<Long, String> avatarUrlMap = new HashMap<>();
        List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(authorIds);
            for (User u : users) {
                String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                authorNameMap.put(u.getId(), name);
                avatarUrlMap.put(u.getId(), u.getAvatarUrl());
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
            // 注入作者头像相对路径（可能为 null）；前端负责拼完整 URL 与兜底默认头像。
            it.setAvatarUrl(avatarUrlMap.get(n.getUserId()));
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

    /**
     * 最近公开便签
     * 行为：
     * - 按更新时间倒序，仅公开且未归档，返回前 size 条；
     * - 先尝试读取缓存（不含用户态 liked/favorited），缓存命中则直接返回；
     * - 若用户已登录，补充 likedByMe、favoritedByMe 标记。
     *
     * 参数：
     * - userId：当前用户 ID，可为 null；
     * - size：返回条数，<=0 时使用默认 10。
     *
     * 返回：
     * - {@link Page} 包装的 {@link NoteItem} 列表（当前页固定为 1）。
     */
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
            Map<Long, String> avatarUrlMap = new HashMap<>();
            List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
            if (!authorIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(authorIds);
                for (User u : users) {
                    String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                    authorNameMap.put(u.getId(), name);
                    avatarUrlMap.put(u.getId(), u.getAvatarUrl());
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
                it.setAvatarUrl(avatarUrlMap.get(n.getUserId()));
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

    /**
     * 热门公开便签
     * 行为：
     * - 仅公开且未归档，先取最近 days 天内的候选集合（最多 300 条），
     * - 计算综合热度分（收藏权重 1.0，点赞权重 0.5，时效加权 0.2~1.0）并排序截断；
     * - 结果写入热门缓存，不含用户态；登录用户补充 likedByMe/favoritedByMe。
     *
     * 参数：
     * - userId：当前用户 ID，可为 null；
     * - size：返回条数，<=0 使用默认 10；
     * - days：候选范围的时间窗口天数，<=0 使用默认 30。
     *
     * 返回：
     * - {@link Page} 包装的 {@link NoteItem} 列表（当前页固定为 1）。
     */
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
            Map<Long, String> avatarUrlMap = new HashMap<>();
            List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
            if (!authorIds.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(authorIds);
                for (User u : users) {
                    String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                    authorNameMap.put(u.getId(), name);
                    avatarUrlMap.put(u.getId(), u.getAvatarUrl());
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
                // 注入作者头像：数据库保存的是相对路径（如 "/uploads/avatars/xxx.jpg"），
                // 前端通过 avatarFullUrl(base 去掉 /api 前缀) 拼接成完整访问地址。
                // 之前遗漏该字段会导致热门列表头像不展示，这里补上。
                it.setAvatarUrl(avatarUrlMap.get(n.getUserId()));
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

    /**
     * 批量导入拾言
     * 说明：
     * - 入参为当前用户ID与 NoteRequest 列表（来源于前端解析的 CSV/JSON 数据）；
     * - 对每项进行最小校验：content 为空则计为失败并跳过；
     * - 其余字段（tags/color/archived/isPublic）按已有 create 逻辑写入；
     * - 返回导入统计信息：imported（成功条数）、failed（失败条数）、errors（可选错误消息列表）。
     *
     * 参数：
     * - userId：当前用户 ID；
     * - items：待导入的便签请求列表。
     *
     * 返回：
     * - 统计信息 Map：imported、failed、errors（可选）。
     */
    @Transactional
    public Map<String, Object> importNotes(Long userId, List<NoteRequest> items) {
        if (items == null || items.isEmpty()) {
            return Map.of("imported", 0, "failed", 0);
        }
        int ok = 0;
        int fail = 0;
        List<String> errors = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            NoteRequest req = items.get(i);
            // 最小校验：content 非空（去除空格）
            String content = (req == null) ? null : req.getContent();
            if (!StringUtils.hasText(content)) {
                fail++;
                errors.add("第" + (i + 1) + "条：内容为空，已跳过");
                continue;
            }
            try {
                // 直接复用 create 逻辑（包含标签解析与缓存失效处理）
                this.create(userId, req);
                ok++;
            } catch (Exception e) {
                fail++;
                // 记录简要错误信息（不暴露堆栈），便于前端提示
                errors.add("第" + (i + 1) + "条：" + (e.getMessage() == null ? "导入失败" : e.getMessage()));
            }
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("imported", ok);
        resp.put("failed", fail);
        if (!errors.isEmpty()) resp.put("errors", errors);
        return resp;
    }
    // 点赞相关
    @Transactional
    /**
     * 点赞便签
     * 行为：
     * - 私有便签仅作者可点赞；公开便签任何登录用户可点赞；
     * - 若未点赞则插入一条点赞记录，并向作者发送“收到的赞”消息（避免自赞发消息）；
     * - 点赞变化影响热门，失效热门缓存。
     *
     * 参数：
     * - userId：当前用户 ID；
     * - noteId：便签 ID。
     *
     * 返回：
     * - Map：{"count": 总点赞数, "likedByMe": true}
     *
     * 异常：
     * - RuntimeException：便签不存在或私有便签非作者操作时抛出。
     */
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
            // 点赞成功后，向作者发送一条“收到的赞”消息（避免自赞发消息）
            try { if (messageService != null) messageService.createLikeMessage(userId, noteId); } catch (Exception ignored) {}
        }
        long count = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId));
        // 点赞变化影响热门，失效热门缓存
        try { if (noteCache != null) noteCache.evictHotAll(); } catch (Exception ignored) {}
        return Map.of("count", count, "likedByMe", true);
    }

    @Transactional
    /**
     * 取消点赞
     * 行为：
     * - 校验可见性与归属规则；
     * - 删除当前用户的点赞记录；
     * - 失效热门缓存。
     *
     * 返回：
     * - Map：{"count": 总点赞数, "likedByMe": false}
     */
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

    /**
     * 点赞信息查询
     * 行为：
     * - 返回便签的总点赞数与当前用户是否已点赞。
     *
     * 参数：
     * - userId：当前用户 ID；
     * - noteId：便签 ID。
     *
     * 返回：
     * - Map：{"count": 总点赞数, "likedByMe": 是否点赞}
     */
    public Map<String, Object> likeInfo(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        long count = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId));
        boolean likedByMe = likeMapper.selectCount(new QueryWrapper<NoteLike>().eq("note_id", noteId).eq("user_id", userId)) > 0;
        return Map.of("count", count, "likedByMe", likedByMe);
    }

    // 收藏相关
    @Transactional
    /**
     * 收藏便签
     * 行为：
     * - 私有便签仅作者可收藏；公开便签任何登录用户可收藏；
     * - 若未收藏则插入一条收藏记录，并向作者发送“收到的收藏”消息（避免自藏发消息）；
     * - 收藏变化影响热门，失效热门缓存。
     *
     * 返回：
     * - Map：{"count": 总收藏数, "favoritedByMe": true}
     */
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
            // 收藏成功后，向作者发送一条“收到的收藏”消息（避免自藏发消息）
            try { if (messageService != null) messageService.createFavoriteMessage(userId, noteId); } catch (Exception ignored) {}
        }
        long count = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId));
        // 收藏变化影响热门，失效热门缓存
        try { if (noteCache != null) noteCache.evictHotAll(); } catch (Exception ignored) {}
        return Map.of("count", count, "favoritedByMe", true);
    }

    @Transactional
    /**
     * 取消收藏
     * 行为：
     * - 校验可见性与归属规则；
     * - 删除当前用户的收藏记录；
     * - 失效热门缓存。
     *
     * 返回：
     * - Map：{"count": 总收藏数, "favoritedByMe": false}
     */
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

    /**
     * 收藏信息查询
     * 行为：
     * - 返回便签的总收藏数与当前用户是否已收藏。
     *
     * 参数：
     * - userId：当前用户 ID；
     * - noteId：便签 ID。
     *
     * 返回：
     * - Map：{"count": 总收藏数, "favoritedByMe": 是否收藏}
     */
    public Map<String, Object> favoriteInfo(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) throw new RuntimeException("笔记不存在");
        long count = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId));
        boolean favoritedByMe = favoriteMapper.selectCount(new QueryWrapper<NoteFavorite>().eq("note_id", noteId).eq("user_id", userId)) > 0;
        return Map.of("count", count, "favoritedByMe", favoritedByMe);
    }

    /**
     * 分页检索“我收藏的便签”
     * 行为：
     * - 先取当前用户收藏的 noteId 集合，再按条件筛选便签并分页；
     * - 返回作者信息、统计数据与当前用户态（likedByMe/favoritedByMe）。
     *
     * 参数：
     * - userId：当前用户 ID；
     * - page/size：分页参数；
     * - q：关键词搜索（content/tags）。
     *
     * 返回：
     * - {@link Page} 包装的 {@link NoteItem} 列表与分页信息。
     */
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
        // 作者头像映射：user_id -> avatar_url 相对路径。
        // 详细说明：头像在前端使用 avatarFullUrl 进行完整 URL 拼接；
        // 这里提供原始相对路径，保持返回体轻量，兼容现有渲染逻辑。
        Map<Long, String> avatarUrlMap = new HashMap<>();
        List<Long> authorIds = records.stream().map(Note::getUserId).distinct().collect(Collectors.toList());
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(authorIds);
            for (User u : users) {
                String name = Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                authorNameMap.put(u.getId(), name);
                // 同步记录头像相对路径，可能为 null（前端需做默认头像兜底）。
                avatarUrlMap.put(u.getId(), u.getAvatarUrl());
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
            it.setAvatarUrl(avatarUrlMap.get(n.getUserId()));
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

    /**
     * 列出当前用户点过赞的便签（分页）
     * 实现思路：
     * 1) 先从点赞表按 user_id 取出该用户点赞过的所有 note_id；
     * 2) 再使用这些 note_id 反查便签并分页；
     * 3) 计算作者昵称、点赞/收藏数量，并根据当前用户补充 likedByMe/favoritedByMe 标记；
     * 注意：
     * - 当 userId 为空（匿名访问）时，无法计算“我是否点赞/收藏”，统一返回 false；
     * - 为了简化实现，liked 集合为空时直接返回空分页结果。
     */
    /**
     * 分页检索“我点赞的便签”
     * 行为：
     * - 先取当前用户点赞的 noteId 集合，再按条件筛选便签并分页；
     * - 返回作者信息、统计数据与当前用户态（likedByMe/favoritedByMe）。
     *
     * 参数：
     * - userId：当前用户 ID；
     * - page/size：分页参数；
     * - q：关键词搜索（content/tags）。
     *
     * 返回：
     * - {@link Page} 包装的 {@link NoteItem} 列表与分页信息。
     */
    public Page<NoteItem> listLiked(Long userId, int page, int size, String q) {
        // 若未登录，无用户上下文，直接返回空列表（也可改为公开便签中过滤，但前端喜欢页语义为“我点赞过的”）
        if (userId == null || userId <= 0) {
            Page<NoteItem> empty = Page.of(page, size);
            empty.setTotal(0);
            empty.setRecords(java.util.Collections.emptyList());
            return empty;
        }

        // 取当前用户点赞过的便签 ID 集合
        java.util.List<Long> likedIdsAll = likeMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.yunbq.backend.model.NoteLike>().eq("user_id", userId))
                .stream().map(com.yunbq.backend.model.NoteLike::getNoteId).collect(java.util.stream.Collectors.toList());
        if (likedIdsAll == null || likedIdsAll.isEmpty()) {
            Page<NoteItem> empty = Page.of(page, size);
            empty.setTotal(0);
            empty.setRecords(java.util.Collections.emptyList());
            return empty;
        }

        // 反查便签并分页
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.yunbq.backend.model.Note> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.in("id", likedIdsAll);
        if (q != null && !q.isBlank()) {
            qw.and(w -> w.like("content", q).or().like("tags", q));
        }
        qw.orderByDesc("updated_at");
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.yunbq.backend.model.Note> np = noteMapper.selectPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(page, size), qw);
        java.util.List<com.yunbq.backend.model.Note> records = np.getRecords();
        java.util.List<Long> ids = records.stream().map(com.yunbq.backend.model.Note::getId).collect(java.util.stream.Collectors.toList());

        // 作者昵称映射
        java.util.Map<Long, String> authorNameMap = new java.util.HashMap<>();
        java.util.Map<Long, String> avatarUrlMap = new java.util.HashMap<>();
        java.util.List<Long> authorIds = records.stream().map(com.yunbq.backend.model.Note::getUserId).distinct().collect(java.util.stream.Collectors.toList());
        if (!authorIds.isEmpty()) {
            java.util.List<com.yunbq.backend.model.User> users = userMapper.selectBatchIds(authorIds);
            for (com.yunbq.backend.model.User u : users) {
                String name = java.util.Optional.ofNullable(u.getNickname()).filter(s -> !s.isBlank()).orElse(u.getUsername());
                authorNameMap.put(u.getId(), name);
                avatarUrlMap.put(u.getId(), u.getAvatarUrl());
            }
        }

        // 点赞/收藏数量与用户态标记
        java.util.Map<Long, Long> likeCountMap = new java.util.HashMap<>();
        java.util.Map<Long, Long> favoriteCountMap = new java.util.HashMap<>();
        final java.util.Set<Long> likedSet;
        final java.util.Set<Long> favoritedSet;
        if (!ids.isEmpty()) {
            java.util.List<java.util.Map<String,Object>> likeCounts = likeMapper.countByNoteIds(ids);
            for (java.util.Map<String,Object> m : likeCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                likeCountMap.put(nid, cnt);
            }
            java.util.List<java.util.Map<String,Object>> favCounts = favoriteMapper.countByNoteIds(ids);
            for (java.util.Map<String,Object> m : favCounts) {
                Long nid = ((Number)m.get("noteId")).longValue();
                Long cnt = ((Number)m.get("cnt")).longValue();
                favoriteCountMap.put(nid, cnt);
            }
            // 当前用户已点赞/已收藏集合
            java.util.List<Long> likedIds = likeMapper.findLikedNoteIdsByUser(userId, ids);
            likedSet = likedIds.stream().collect(java.util.stream.Collectors.toSet());
            java.util.List<Long> favoritedIds = favoriteMapper.findFavoritedNoteIdsByUser(userId, ids);
            favoritedSet = favoritedIds.stream().collect(java.util.stream.Collectors.toSet());
        } else {
            likedSet = java.util.Set.of();
            favoritedSet = java.util.Set.of();
        }

        java.util.List<com.yunbq.backend.dto.NoteItem> items = records.stream().map(n -> {
            com.yunbq.backend.dto.NoteItem it = new com.yunbq.backend.dto.NoteItem();
            it.setId(n.getId());
            it.setUserId(n.getUserId());
            it.setAuthorName(authorNameMap.get(n.getUserId()));
            it.setAvatarUrl(avatarUrlMap.get(n.getUserId()));
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
        }).collect(java.util.stream.Collectors.toList());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.yunbq.backend.dto.NoteItem> ip = com.baomidou.mybatisplus.extension.plugins.pagination.Page.of(np.getCurrent(), np.getSize());
        ip.setTotal(np.getTotal());
        ip.setRecords(items);
        return ip;
    }

    // 已移除：标签统计接口实现

    // ========= 解析工具 =========
    /**
     * 内容解析结果
     * 说明：
     * - 用于承载从原始内容中解析出的主体内容与标准化标签串。
     */
    private static class Parsed {
        final String content;
        final String tags;
        Parsed(String c, String t){ this.content = c; this.tags = t; }
    }
    /**
     * 若 tags 为空，尝试从 content 中提取以 # 开头、逗号分隔的标签；并将这些标签从内容中移除。
     */
    /**
     * 从原始内容解析标签并清理内容
     * 行为：
     * - 若提供 rawTags 使用其为主，否则从 rawContent 中提取以 `#tag` 形式出现的标签；
     * - 规范化标签串（去重、去空、统一分隔与大小写策略）；
     * - 返回清理后的内容与最终标签串。
     *
     * 参数：
     * - rawContent：原始便签内容；
     * - rawTags：显式传入的标签串，可为空。
     *
     * 返回：
     * - {@link Parsed}：包含清理后的内容与标准化标签。
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
    /**
     * 标签串标准化
     * 行为：
     * - 按逗号/空白分隔拆分标签，去除空白与重复；
     * - 可选采用统一大小写策略（当前保持原样），再按逗号拼接；
     * - 返回适合存储与检索的轻量字符串表示。
     */
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