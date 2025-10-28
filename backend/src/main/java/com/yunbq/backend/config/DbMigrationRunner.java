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
        String currentSchema;
        try {
            currentSchema = jdbc.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            currentSchema = null;
        }
        // 尝试删除 notes.title 列（若存在）
        try {
            Integer titleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'notes' AND column_name = 'title'",
                Integer.class,
                currentSchema
            );
            if (titleCount != null && titleCount > 0) {
                jdbc.execute("ALTER TABLE notes DROP COLUMN title");
            }
        } catch (Exception ignored) {
            // 不阻塞启动，数据库差异或权限不足时忽略
        }

        // 新增：为 notes 表添加 color 列（若不存在）
        try {
            Integer colorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'notes' AND column_name = 'color'",
                Integer.class,
                currentSchema
            );
            if (colorCount == null || colorCount == 0) {
                jdbc.execute("ALTER TABLE notes ADD COLUMN color VARCHAR(16)");
            }
        } catch (Exception ignored) {
            // 不阻塞启动
        }

        // 新增：为 users 表添加 role 列（若不存在），并修复历史空值
        try {
            Integer roleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'users' AND column_name = 'role'",
                Integer.class,
                currentSchema
            );
            if (roleCount == null || roleCount == 0) {
                jdbc.execute("ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER'");
            }
            // 兼容历史数据：将空值或空字符串设置为 USER
            jdbc.execute("UPDATE users SET role = 'USER' WHERE role IS NULL OR role = ''");
        } catch (Exception ignored) {
            // 不阻塞启动，数据库差异或权限不足时忽略
        }
    }
}