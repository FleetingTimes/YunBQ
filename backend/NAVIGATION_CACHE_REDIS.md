# 分布式缓存替换与 Redis 实现详解（Spring Cache + Redis）

本文在你已阅读 `NAVIGATION_CACHE.md`（Caffeine 本地缓存实现）的基础上，提供一份“分布式缓存 Redis”的替换教程与详细讲解，帮你理解、落地与调优。

## 为什么改用 Redis
- 分布式场景：多实例部署时，本地缓存不能共享。Redis 作为集中式缓存，实例之间共享数据，避免重复查询与不一致。
- 易扩展与可观测：支持持久化、复制、哨兵与集群；可接入监控与告警。
- 丰富特性：键过期、数据结构、Lua 脚本等，未来可做更多玩法（如限流、延时队列）。

## 总体策略
- 继续使用 Spring Cache 注解（`@Cacheable` / `@CacheEvict`），仅替换底层 CacheManager 为 Redis。
- 服务层的失效逻辑（写操作后主动清理缓存）保持不变；只需切换配置，即可实现分布式一致性。
- 推荐采用“按 Profile 切换”配置：`local` 使用 Caffeine，`redis` 使用 Redis，代码互不干扰、便于环境区分。

## 依赖与现状
- 你的 `pom.xml` 已包含：`spring-boot-starter-data-redis`（Spring Data Redis）。无需新增依赖。
- 已启用 `@EnableCaching`，因此只需提供 Redis 的 CacheManager 即可。

## 新增配置文件与类
我们将添加两个文件：
1) `application-redis.yml`：Redis 连接与基础参数示例（按需改成你的环境）。
2) `CacheConfigRedis.java`：基于 Profile 的 Redis CacheManager 配置（仅在 `redis` Profile 激活时生效）。

### application-redis.yml（示例）
路径：`backend/src/main/resources/application-redis.yml`

```yaml
spring:
  profiles:
    # 当启用 redis Profile 时使用本文件配置
    active: redis

  # Redis 连接配置（Lettuce 默认连接池）
  data:
    redis:
      host: 127.0.0.1          # 你的 Redis 主机地址
      port: 6379               # 你的 Redis 端口
      # password: your_password # 如有密码，取消注释并填写
      # ssl: false              # 如需启用 TLS/SSL
      # timeout: 2000ms         # 连接/操作超时
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

  cache:
    type: redis                # 指定缓存类型为 Redis（可选，使用自定义 CacheManager 时非必需）

# 日志级别（可选，用于观察缓存与 Redis 连接行为）
logging:
  level:
    org.springframework.cache: INFO
    org.springframework.data.redis: INFO
```

说明：
- 这是一个“示例”文件。你可以将 `spring.profiles.active=redis` 放在环境变量或命令行参数中，而不在 `application-redis.yml` 内写死。
- 如果你已经有 `application.yml`，可把 Redis 相关配置合并，并用 `spring.profiles` 控制生效。

### CacheConfigRedis.java（配置类）
路径：`backend/src/main/java/com/yunbq/backend/config/CacheConfigRedis.java`

核心思路：当 `redis` Profile 激活时，注册一个 `RedisCacheManager`，并为不同缓存命名空间（分类与按分类站点）设置 TTL 等策略。

关键点解释：
- 序列化：使用 `StringRedisSerializer`（key）+ `GenericJackson2JsonRedisSerializer`（value）保证可读性与兼容性。
- TTL 与前缀：可针对不同的缓存命名空间（如 `sites_by_category`、`categories_root`、`categories_enabled`）设置不同的过期时间。
- Key 前缀：使用默认前缀（`cacheName::`）便于按命名空间清理。

示例代码（已在本项目中新增）：

```java
@Configuration
@Profile("redis")
public class CacheConfigRedis {

    // 连接工厂：默认基于 Lettuce；也可直接使用 Spring Boot 自动配置的工厂
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Key 与 Value 的序列化策略
        RedisSerializationContext.SerializationPair<String> keyPair = RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer());
        RedisSerializationContext.SerializationPair<Object> valuePair = RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());

        // 默认配置：适用于未在 map 中显式声明的 cache 名称
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(keyPair)
                .serializeValuesWith(valuePair)
                .disableCachingNullValues()
                .entryTtl(Duration.ofSeconds(60)) // 默认 TTL：60s
                .computePrefixWith(cacheName -> cacheName + "::");

        // 针对不同缓存命名空间设置差异化 TTL
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("sites_by_category", defaultConfig.entryTtl(Duration.ofSeconds(60)));  // 按分类查询站点：60s
        configs.put("categories_root",   defaultConfig.entryTtl(Duration.ofSeconds(120))); // 导航栏一级分类：120s
        configs.put("categories_enabled",defaultConfig.entryTtl(Duration.ofSeconds(120))); // 启用的完整分类树：120s

        // 构建 RedisCacheManager
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .transactionAware()
                .build();
    }
}
```

与现有 Caffeine 并存：
- 若你保留 `CacheConfig`（Caffeine），建议为其加 `@Profile("local")`，让本地开发走 Caffeine；生产/测试走 Redis。
- 也可用 `@Primary` 指定优先的 CacheManager，但 Profile 更直观可控。

## 切换流程（一步步）
1. 准备 Redis 服务：本地或容器。
   - Docker 示例（PowerShell）：
     ```bash
     docker run -d --name yunbq-redis -p 6379:6379 redis:7-alpine
     ```
   - Windows 本地安装：可使用官方 MSI 或 WSL。
2. 配置连接：更新 `application-redis.yml` 中的 `spring.data.redis.*`，或在环境变量中设置：
   - `SPRING_DATA_REDIS_HOST=127.0.0.1`
   - `SPRING_DATA_REDIS_PORT=6379`
   - `SPRING_DATA_REDIS_PASSWORD=你的密码（如有）`
3. 激活 Profile：
   - 命令行：`--spring.profiles.active=redis`
   - 环境变量：`SPRING_PROFILES_ACTIVE=redis`
4. 重启后端服务：确认启动日志无 Redis 连接错误。
5. 验证缓存命中与失效：
   - 多次调用分类/热门/推荐接口，观察首次与后续响应时间变化；
   - 执行写操作（点击自增、推荐切换、更新站点），再读，看数据是否即时刷新；
   - 如启用 Redis 命令行客户端，观察 key 与 TTL（`keys *`、`ttl <key>`）。

## 与服务层的协同（已有逻辑复用）
— 你的服务层已在写操作后进行缓存清理：
  - 站点列表：按分类维度精确删除单个 `categoryId` 键；
  - 分类列表：统一清空 `categories_root` 与 `categories_enabled` 命名空间。
— 切换到 Redis 后，这些清理仍通过 `CacheManager` 生效；RedisCacheManager 会删除对应前缀下的键，确保分布式一致。

## 进阶调优建议
- TTL 策略：
  - 分类（`sites_by_category`）通常可较长，如 `60–300s`；
  - 热门/推荐可更短，如 `15–60s`，并保留写后主动清理；
- 前缀与命名：保持清晰的前缀便于清理与审计，如 `cacheName::key`。
- 序列化策略：
  - 默认 JSON（`GenericJackson2JsonRedisSerializer`）可读性好，适合跨语言；
  - 对性能更敏感时可考虑 `JDK 序列化`（但可读性差）或自定义轻量对象。
- 大批量清理：
  - `cache.clear()` 会按前缀批量删除；在超大规模下可能产生阻塞，谨慎使用；
  - 可结合消息通知或增量失效策略（按变更维度只清理必要键）。
- 高并发点击：
  - 点击自增在 Redis 缓存场景下仍建议保留数据库原子自增；如需更高性能可做异步聚合。

## 常见问题
- 连接失败：检查防火墙与密码；确认 Redis 版本兼容（建议 6+）。
- 缓存不生效：确认 `@EnableCaching` 与 `CacheManager` 是否按 Profile 正确注册；查看日志。
- 键冲突或串数据：确保 `@Cacheable` 的 `key` 表达式涵盖所有筛选维度（如 `categoryId`）。
- 集群模式：需使用带哨兵/集群的连接工厂，必要时切换到 Redisson 等更高级客户端。

## 快速参考
- 配置类：`config/CacheConfigRedis.java`
- 示例配置：`src/main/resources/application-redis.yml`
- 读写逻辑与失效：
  - 站点：`service/NavigationSiteService.java`
  - 分类：`service/NavigationCategoryService.java`

---

如需我帮你将 Caffeine 与 Redis 以 Profile 分离（`local`/`redis`）或改为 `@Primary` 切换，或配置不同环境的 TTL 与序列化策略，请告诉我你的部署环境与目标，我可以直接补充代码与参数。