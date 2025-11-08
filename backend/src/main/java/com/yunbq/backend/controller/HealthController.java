package com.yunbq.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器（/healthz）
 * 设计目标：
 * - 提供一个始终可匿名访问的健康检查端点，返回 HTTP 200，包含关键依赖的探测结果；
 * - 与 Actuator (/actuator/health) 并行存在：Actuator 负责更丰富的健康细节，本端点用于负载均衡/探针的简化判定；
 * - 对可选依赖（如 Redis）在未启用时返回 "UNKNOWN"，避免本地开发环境因未启动而导致 503。
 *
 * 返回结构示例：
 * {
 *   "status": "UP",
 *   "details": {
 *     "db": "UP",
 *     "redis": "UNKNOWN"
 *   }
 * }
 */
@RestController
public class HealthController {

    // 数据源（可选）：存在时尝试获取连接以判断数据库可用性
    @Autowired(required = false)
    private DataSource dataSource;

    // Redis（可选）：存在时进行 PING 判断；本地开发若未配置则为 null
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    /**
     * 简化健康检查端点：/healthz
     * 行为与约定：
     * - 始终返回 200；status 字段为 "UP"/"DEGRADED"/"DOWN"（仅在关键依赖不可用时为 DOWN）；
     * - details 中包含 db、redis 等组件的探测结果；
     * - 探测过程采用尽量轻量的操作（数据库仅尝试获取/关闭连接，Redis 使用 PING）。
     */
    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        Map<String, Object> details = new HashMap<>();

        // 数据库探测：若存在数据源，尝试获取连接；成功则为 UP，失败为 DOWN
        String dbStatus = "UNKNOWN";
        if (dataSource != null) {
            try (var conn = dataSource.getConnection()) {
                dbStatus = (conn != null && !conn.isClosed()) ? "UP" : "DOWN";
            } catch (Exception e) {
                dbStatus = "DOWN";
            }
        }
        details.put("db", dbStatus);

        // Redis 探测：若存在 RedisTemplate，执行 PING；否则标记为 UNKNOWN
        String redisStatus = "UNKNOWN";
        if (redisTemplate != null) {
            try {
                String pong = redisTemplate.getConnectionFactory().getConnection().ping();
                redisStatus = (pong != null && !pong.isBlank()) ? "UP" : "DOWN";
            } catch (Exception e) {
                redisStatus = "DOWN";
            }
        }
        details.put("redis", redisStatus);

        // 总体状态：若关键依赖（数据库）DOWN，则标记为 DOWN；若可选依赖（Redis）DOWN，则标记为 DEGRADED；
        String overall = "UP";
        if ("DOWN".equals(dbStatus)) {
            overall = "DOWN";
        } else if ("DOWN".equals(redisStatus)) {
            overall = "DEGRADED";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", overall);
        body.put("details", details);
        return ResponseEntity.ok(body);
    }
}