package com.yunbq.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbMigrationRunner implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public DbMigrationRunner(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) {
        // 尝试删除 notes.title 列（若存在）
        try {
            Boolean exists = jdbc.query("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'notes' AND column_name = 'title'", rs -> {
                if (rs.next()) return rs.getInt(1) > 0; return false;
            });
            if (Boolean.TRUE.equals(exists)) {
                jdbc.execute("ALTER TABLE notes DROP COLUMN title");
            }
        } catch (Exception ignored) {
            // 不阻塞启动，数据库差异或权限不足时忽略
        }
    }
}