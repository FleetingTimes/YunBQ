# 后端缓存实现详解（Spring Cache + Caffeine）

本文面向不了解 Spring Cache 与 Caffeine 的读者，系统性说明本项目中“导航栏（分类）与分类对应站点”的缓存设计、实现与使用方法，帮助你快速上手并进行二次调优。

## 为什么需要缓存
- 广场页属于“读多写少”的典型场景：导航栏分类列表与按分类查询站点在页面浏览时频繁查询。
- 缓存能显著降低数据库压力、缩短响应时间、提高吞吐量。
- 同时通过精确的缓存失效策略保证数据在写操作后尽快一致。

## 技术选型概览
- Spring Cache：提供统一的缓存抽象与注解（`@Cacheable`、`@CacheEvict` 等），不依赖具体实现。
- Caffeine：高性能本地内存缓存实现，支持容量限制、过期策略等，适合单机与低复杂度场景。
- 本项目采用“Spring Cache + Caffeine”的组合，便于后续平滑切换到 Redis 等分布式缓存。

## 依赖声明（pom.xml）
新增的依赖如下：

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>3.1.8</version>
</dependency>
```

说明：
- `spring-boot-starter-cache` 激活缓存注解框架。
- `caffeine` 提供具体的本地缓存实现。

## 启用缓存（应用入口）
在应用主类 `YunbqBackendApplication.java` 增加注解：

```java
@EnableCaching    // 启用 Spring Cache 注解（@Cacheable/@CacheEvict 等）
```

作用：使 `@Cacheable` 等注解在运行时生效，交由 CacheManager 管理。

## 缓存配置（CacheConfig）
新增 `backend/src/main/java/com/yunbq/backend/config/CacheConfig.java`：

```java
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(5000)                // 最大容量，防止内存过大
                .expireAfterWrite(60, TimeUnit.SECONDS) // 写入后 60s 过期
        );
        manager.setCacheNames(List.of(
            "sites_by_category", // 站点：按分类ID查询
            "categories_root",   // 分类：一级（导航栏）
            "categories_enabled" // 分类：启用的完整树（一级+二级）
        ));
        return manager;
    }
}
```

设计要点：
- 容量与过期策略采用“稳妥默认值”，避免内存膨胀与长期不一致。
- 可按业务需求调整：热门/推荐列表可使用更短 TTL（如 15s）以提升实时性。

## 服务层读路径加缓存
目标：在高频读操作上命中缓存，降低数据库压力。

代码位置：
- 站点读取：`backend/src/main/java/com/yunbq/backend/service/NavigationSiteService.java`
- 分类读取：`backend/src/main/java/com/yunbq/backend/service/NavigationCategoryService.java`

读方法已注解：

```java
// 1) 分类站点列表：key = categoryId
@Cacheable(cacheNames = "sites_by_category", key = "#categoryId")
public List<NavigationSite> getSitesByCategory(Long categoryId) {
    return siteMapper.selectByCategoryId(categoryId);
}

// 2) 分类列表：一级（导航栏）
@Cacheable(cacheNames = "categories_root")
public List<NavigationCategory> getRootCategories() {
    return categoryMapper.selectRootCategories();
}

// 3) 分类列表：启用的完整树（一级+二级）
@Cacheable(cacheNames = "categories_enabled")
public List<NavigationCategory> getAllEnabledCategories() {
    return categoryMapper.selectAllEnabled();
}
```

缓存命名与键规则：
- `sites_by_category::categoryId`
- `categories_root`
- `categories_enabled`

说明：分类列表按分类维度缓存；热门/推荐按返回数量上限维度缓存。

## 写路径的精确缓存失效
目标：在数据发生变化后，尽快让下一次读看到最新结果，从而避免过期窗口内的“旧数据”。

实现方式：注入 `CacheManager`，在写操作后执行精确的清理逻辑。

工具方法：

```java
private void evictCategoryCache(Long categoryId) {
    if (categoryId == null) return;
    Cache cache = cacheManager.getCache("sites_by_category");
    if (cache != null) cache.evict(categoryId);
}

private void clearCache(String cacheName) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) cache.clear(); // 清空该命名空间下的所有条目
}
```

写操作与对应失效策略：

- `createSite(...)`
  - 新增站点后：`evictCategoryCache(newSite.categoryId)`
  - 说明：让分类列表立即包含新站点。

- `updateSite(id, site)`
  - 更新站点后：同时清理旧分类与新分类缓存
  - 说明：考虑到站点可能被移动到其他分类。

- `deleteSite(id)`
  - 删除站点后：`evictCategoryCache(deletedSite.categoryId)`

- `toggleEnabled(id)`
  - 启用状态切换：`evictCategoryCache(site.categoryId)`
  - 说明：分类列表展示启用站点，状态变更需即时反映。

— 分类相关写操作（create/update/delete/toggleEnabled/updateCategoriesOrder/import）
  - 统一清理：`clearCache("categories_root")` 与 `clearCache("categories_enabled")`
  - 说明：分类层级、启用状态与排序变化会影响导航栏与分类树，采用统一清理确保一致。

— 站点相关写操作（create/update/delete/toggleEnabled/updateSitesOrder/import）
  - 精确失效：`evictCategoryCache(categoryId)`（同时考虑旧分类与新分类）
  - 说明：分类站点列表只需按分类维度清理即可。

## 与前端的协同
- 前端一次性加载启用分类树（`GET /api/navigation/categories/all`）或导航栏一级分类，再按需加载各分类站点。
- 后端缓存对“分类树”和“按分类查询站点”生效；即使前端静默预取，也能明显降低数据库负载。
- 写操作（分类或站点变更）触发的缓存失效确保用户尽快看到最新数据。

## 调优建议
- TTL（过期时间）
  - 默认 `60s`：兼顾稳定与一致性。
  - 分类与站点：根据你的更新频率，建议 `30–120s`；保留写后主动清理。
- 容量（maximumSize）
  - 视站点与分类规模调整，如 `1000–10000`。容量过小会增加缓存命中不稳定，过大占用内存。
- 键维度
  - 暂按 `categoryId` 与 `limit`；如果未来引入更多筛选（如标签、用户维度），需扩展键以区分。

## 可选增强方案
- 使用注解式失效：在写方法上添加 `@CacheEvict`（`key` 或 `allEntries=true`），由 Spring 自动处理；当前使用手工控制更灵活。
- 分布式缓存：切换至 `RedisCacheManager` 以支持多节点共享缓存；适合集群部署。
- 指标与监控：结合 Actuator 与 Micrometer 对缓存命中率、大小与失效次数进行监控与告警。
- 点击数聚合：高并发场景下将点击自增改为异步队列 + 批量聚合写，减少热点写入。

## 手动验证步骤
1. 启动后端服务（确保已构建并运行）。
2. 连续调用分类列表接口：`GET /api/navigation/sites/category/{id}`
   - 第一次命中数据库，随后命中缓存；你可对比响应时间。
3. 执行写操作（如 `POST /api/navigation/sites/{id}/click` 或管理员更新）。
   - 再次调用热门/推荐或分类接口，应看到最新结果（缓存已失效或过期）。
4. 若需要更强可视化，开启 SQL 打印或添加日志打印缓存命中（生产环境慎用）。

## 常见问题与坑
- 写操作未清理缓存导致前端看到旧数据：本项目已在所有相关写路径添加清理，但新增写方法时请勿遗漏。
- 过期时间过长：可能导致一致性问题；建议配合主动清理。
- 键维度不完整：若未来新增筛选参数，但未纳入缓存键，可能出现“串数据”。
- 本地缓存的分布式问题：单机缓存不共享，集群部署时需考虑 Redis 或关闭某些缓存。

## 快速参考（关键代码入口）
- 应用入口：`YunbqBackendApplication.java`（`@EnableCaching`）
- 缓存配置：`config/CacheConfig.java`（`CaffeineCacheManager`）
- 服务实现：
  - `service/NavigationSiteService.java`：按分类查询站点缓存与失效
  - `service/NavigationCategoryService.java`：分类列表缓存与写后统一清理

---

如需我帮助你根据实际访问量与行为调整 TTL/容量、或切换到 Redis 分布式缓存，请告诉我你的部署规模与性能目标，我会给出有针对性的建议与补丁。