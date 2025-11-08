package com.yunbq.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置（Caffeine）
 * 作用：
 * - 定义全局的 CacheManager，使用 Caffeine 作为缓存实现；
 * - 设置统一的容量与过期策略，保障在读多写少场景下的性能与一致性；
 * - 支持的缓存名称通过注解 `@Cacheable(cacheNames=...)` 使用时自动创建。
 *
 * 策略说明：
 * - 最大容量 `5000`：适配分类列表与按分类查询站点的读多写少场景；
 * - 过期时间 `60s`：在分类增删改、启用状态切换、排序调整后能较快自动过期；
 *   同时，服务层已在写操作后执行精确的缓存清理，保证数据一致性。
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(60, TimeUnit.SECONDS)
        );
        // 可选：预先声明常用缓存名称（非必需），更清晰地看到已使用的缓存
        manager.setCacheNames(java.util.List.of(
            // 站点：按分类查询
            "sites_by_category",
            // 分类：导航栏与完整启用分类树
            "categories_root",
            "categories_enabled"
        ));
        return manager;
    }
}