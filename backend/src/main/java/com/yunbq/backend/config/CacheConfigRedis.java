package com.yunbq.backend.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 分布式缓存配置（Redis 版本）
 * 作用：
 * - 当激活 Profile `redis` 时，使用 Redis 作为 Spring Cache 的底层实现；
 * - 配置统一的序列化策略与命名空间 TTL；
 * - 与本地缓存（Caffeine）并存，通过 Profile 区分环境（开发用 Caffeine，生产/测试用 Redis）。
 *
 * 关键设计：
 * - 序列化：key 使用 String，value 使用 JSON（GenericJackson2Json）便于跨语言与可观测；
 * - TTL：为不同的缓存命名空间设置差异化过期时间（分类 60s，热门/推荐 30s）；
 * - 前缀：采用 `cacheName::` 作为键前缀，便于按命名空间清理（cache.clear）。
 */
@Configuration
@Profile("redis")
public class CacheConfigRedis {

    /**
     * Redis 连接工厂
     * 使用 Lettuce 默认配置；实际连接参数来自 `spring.data.redis.*`（application-redis.yml 或环境变量）。
     * 如需哨兵/集群，可改为对等的工厂构造。
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    /**
     * Redis CacheManager 配置
     * - 配置默认 RedisCacheConfiguration（序列化、TTL、前缀）；
     * - 为常用命名空间设置差异化 TTL；
     * - 启用事务感知（transactionAware）。
     */
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Key 使用字符串序列化，便于在 Redis CLI 中查看
        RedisSerializationContext.SerializationPair<String> keyPair =
                RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());

        // Value 使用 JSON 序列化，提升可读性与兼容性（避免 JDK 序列化的二进制不可读问题）
        RedisSerializationContext.SerializationPair<Object> valuePair =
                RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());

        // 默认配置：用于未在 map 中显式声明的 cache 名称
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(keyPair)
                .serializeValuesWith(valuePair)
                .disableCachingNullValues()
                .entryTtl(Duration.ofSeconds(60)) // 默认 TTL：60 秒
                .computePrefixWith(cacheName -> cacheName + "::");

        // 针对常用命名空间配置差异化 TTL
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("sites_by_category", defaultConfig.entryTtl(Duration.ofSeconds(60)));  // 分类列表：60s
        configs.put("sites_featured",    defaultConfig.entryTtl(Duration.ofSeconds(30)));  // 推荐站点：30s，更实时
        configs.put("sites_popular",     defaultConfig.entryTtl(Duration.ofSeconds(30)));  // 热门站点：30s，更实时

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .transactionAware()
                .build();
    }
}