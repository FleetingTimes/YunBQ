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

    private String hotKey(int size) { return "notes:hot:size:" + size; }
    private String recentKey(int size) { return "notes:recent:size:" + size; }

    public List<NoteItem> getHot(int size) {
        try {
            String val = redis.opsForValue().get(hotKey(size));
            if (val == null || val.isBlank()) return null;
            return mapper.readValue(val, new TypeReference<List<NoteItem>>(){});
        } catch (Exception e) { return null; }
    }

    public void setHot(int size, List<NoteItem> items) {
        try {
            String json = mapper.writeValueAsString(items);
            redis.opsForValue().set(hotKey(size), json, Duration.ofSeconds(hotTtlSeconds));
        } catch (Exception ignored) {}
    }

    public List<NoteItem> getRecent(int size) {
        try {
            String val = redis.opsForValue().get(recentKey(size));
            if (val == null || val.isBlank()) return null;
            return mapper.readValue(val, new TypeReference<List<NoteItem>>(){});
        } catch (Exception e) { return null; }
    }

    public void setRecent(int size, List<NoteItem> items) {
        try {
            String json = mapper.writeValueAsString(items);
            redis.opsForValue().set(recentKey(size), json, Duration.ofSeconds(recentTtlSeconds));
        } catch (Exception ignored) {}
    }

    public void evictHotAll() {
        try {
            Set<String> keys = redis.keys("notes:hot:size:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {}
    }

    public void evictRecentAll() {
        try {
            Set<String> keys = redis.keys("notes:recent:size:*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {}
    }
}