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

        // 新增：创建 note_favorites 表（若不存在）
        try {
            Integer tableExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'note_favorites'",
                Integer.class,
                currentSchema
            );
            if (tableExists == null || tableExists == 0) {
                jdbc.execute("CREATE TABLE note_favorites (\n" +
                        "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n" +
                        "  note_id BIGINT NOT NULL,\n" +
                        "  user_id BIGINT NOT NULL,\n" +
                        "  created_at DATETIME NOT NULL,\n" +
                        "  UNIQUE KEY uniq_note_user_fav (note_id, user_id),\n" +
                        "  INDEX idx_note_fav (note_id),\n" +
                        "  INDEX idx_user_fav (user_id)\n" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                // 外键约束可能因权限或时序问题失败，留给 schema.sql 或手工执行
                try {
                    jdbc.execute("ALTER TABLE note_favorites ADD CONSTRAINT fk_favorites_note FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE");
                } catch (Exception ignored2) {}
                try {
                    jdbc.execute("ALTER TABLE note_favorites ADD CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE");
                } catch (Exception ignored3) {}
            }
        } catch (Exception ignored) {
            // 不阻塞启动
        }
    }
}