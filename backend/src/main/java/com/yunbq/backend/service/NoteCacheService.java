package com.yunbq.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunbq.backend.dto.NoteItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class NoteCacheService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${cache.notes.hot-ttl-seconds:60}")
    private long hotTtlSeconds;
    @Value("${cache.notes.recent-ttl-seconds:20}")
    private long recentTtlSeconds;

    public NoteCacheService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 笔记热点/最近列表的缓存服务。
     * <p>
     * 该服务负责：
     * - 使用 Redis 的字符串键存取热点与最近便签列表，键空间分别为 `notes:hot:size:{size}` 与 `notes:recent:size:{size}`。
     * - 统一控制 TTL，通过配置项 `cache.notes.hot-ttl-seconds` 与 `cache.notes.recent-ttl-seconds` 设置过期时间。
     * - 采用 Jackson 将 {@link NoteItem} 列表序列化为 JSON 存储；读取时反序列化为 List。
     * - 提供批量失效方法以在写操作（新增/删除/点赞/收藏等）后主动清理相关缓存，避免脏读。
     * <p>
     * 线程安全性：本类无共享可变状态（除配置值），且仅调用线程安全的 `StringRedisTemplate` 与局部变量，因此是线程安全的。
     * 异常处理：所有与缓存交互的方法在遇到异常时返回空值或吞掉异常，保证上层业务可降级为数据库直读，不影响主流程。
     */

    private String hotKey(int size) { return "notes:hot:size:" + size; }
    private String recentKey(int size) { return "notes:recent:size:" + size; }

    /**
     * 获取热点便签列表的缓存命中结果。
     *
     * @param size 期望返回的列表大小（用于构造键名，需与写入时的一致）。
     * @return 命中时返回 {@code List<NoteItem>}；未命中或解析异常时返回 {@code null}，上层可据此回退到数据库查询。
     * @implNote 采用 JSON 文本序列化以降低 Redis 内存占用与对象膨胀；若数据结构演进，需兼容旧 JSON。
     */
    public List<NoteItem> getHot(int size) {
        try {
            String val = redis.opsForValue().get(hotKey(size));
            if (val == null || val.isBlank()) return null;
            return mapper.readValue(val, new TypeReference<List<NoteItem>>(){});
        } catch (Exception e) { return null; }
    }

    /**
     * 设置热点便签列表的缓存值。
     *
     * @param size 该列表的大小标识（写入键名为 `notes:hot:size:{size}`）。
     * @param items 需要缓存的便签列表；建议遵循去重、按点赞/交互热度排序的策略以匹配热点语义。
     * @implNote 写入采用 `ObjectMapper` 序列化为 JSON 字符串，并设置 TTL 为 `hotTtlSeconds`；序列化异常会被忽略以保证主流程。
     */
    public void setHot(int size, List<NoteItem> items) {
        try {
            String json = mapper.writeValueAsString(items);
            redis.opsForValue().set(hotKey(size), json, Duration.ofSeconds(hotTtlSeconds));
        } catch (Exception ignored) {}
    }

    /**
     * 获取最近便签列表的缓存命中结果。
     *
     * @param size 期望返回的列表大小（用于构造键名，需与写入时的一致）。
     * @return 命中时返回 {@code List<NoteItem>}；未命中或解析异常时返回 {@code null}。
     */
    public List<NoteItem> getRecent(int size) {
        try {
            String val = redis.opsForValue().get(recentKey(size));
            if (val == null || val.isBlank()) return null;
            return mapper.readValue(val, new TypeReference<List<NoteItem>>(){});
        } catch (Exception e) { return null; }
    }

    /**
     * 设置最近便签列表的缓存值。
     *
     * @param size 该列表的大小标识（写入键名为 `notes:recent:size:{size}`）。
     * @param items 需要缓存的便签列表；建议遵循按创建时间倒序的排序策略以匹配“最近”语义。
     */
    public void setRecent(int size, List<NoteItem> items) {
        try {
            String json = mapper.writeValueAsString(items);
            redis.opsForValue().set(recentKey(size), json, Duration.ofSeconds(recentTtlSeconds));
        } catch (Exception ignored) {}
    }

    /**
     * 失效所有热点列表缓存。
     * <p>
     * 键模式：`notes:hot:size:*`。适用于热点榜重算、涉及大量点赞/取消点赞操作后统一清理。
     * 在 Redis 实例开启了 `keys` 命令限制或数据量较大时，建议改为维护显式键集合以避免阻塞。
     */
    public void evictHotAll() {
        try {
            Set<String> keys = redis.keys("notes:hot:size:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {}
    }

    /**
     * 失效所有最近列表缓存。
     * <p>
     * 键模式：`notes:recent:size:*`。适用于大批量内容写入或清理后统一失效以保证新鲜度。
     */
    public void evictRecentAll() {
        try {
            Set<String> keys = redis.keys("notes:recent:size:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {}
    }
}