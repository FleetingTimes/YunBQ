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
        // =============================
        // 表重命名：将 notes → shiyan
        // 目标：统一数据库命名与前端品牌“拾言”；同时兼容旧环境。
        // 策略：
        // 1) 若当前库存在 notes 且不存在 shiyan，则执行 RENAME TABLE；
        // 2) 后续所有列变更操作优先作用于已存在的表名（shiyan 优先，否则退回 notes）。
        // 说明：RENAME 会连同外键关系一并迁移到新表名（MySQL 自动维护），无需手动更新约束；
        //      若历史环境外键名不一致，仍能正常保留，避免启动时断链。
        try {
            Integer shiyanExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'shiyan'",
                Integer.class,
                currentSchema
            );
            Integer notesExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'notes'",
                Integer.class,
                currentSchema
            );
            if ((shiyanExists == null || shiyanExists == 0) && (notesExists != null && notesExists > 0)) {
                // 执行重命名：将旧表 notes 改为新表 shiyan
                jdbc.execute("RENAME TABLE notes TO shiyan");
            }
        } catch (Exception ignored) {
            // 不阻塞启动：不同环境权限/版本可能导致 RENAME 失败，允许保留旧表名继续工作
        }

        // 统一后续操作目标表：优先 shiyan，其次 notes（用于极端回退场景）
        String tblNotes = "shiyan";
        try {
            Integer shiyanExists2 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'shiyan'",
                Integer.class,
                currentSchema
            );
            if (shiyanExists2 == null || shiyanExists2 == 0) {
                Integer notesExists2 = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'notes'",
                    Integer.class,
                    currentSchema
                );
                if (notesExists2 != null && notesExists2 > 0) tblNotes = "notes";
            }
        } catch (Exception ignored) { /* 保持默认 shiyan */ }
        // 尝试删除 title 列（若存在）：
        // 兼容迁移前后表名，动态选择目标表。
        try {
            Integer titleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = '" + tblNotes + "' AND column_name = 'title'",
                Integer.class,
                currentSchema
            );
            if (titleCount != null && titleCount > 0) {
                jdbc.execute("ALTER TABLE " + tblNotes + " DROP COLUMN title");
            }
        } catch (Exception ignored) {
            // 不阻塞启动，数据库差异或权限不足时忽略
        }

        // 新增：为目标表添加 color 列（若不存在）
        try {
            Integer colorCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = '" + tblNotes + "' AND column_name = 'color'",
                Integer.class,
                currentSchema
            );
            if (colorCount == null || colorCount == 0) {
                jdbc.execute("ALTER TABLE " + tblNotes + " ADD COLUMN color VARCHAR(16)");
            }
        } catch (Exception ignored) {
            // 不阻塞启动
        }

        // 新增：创建 note_favorites 表（若不存在）
        // 外键：引用到当前实际“便签”表（tblNotes：shiyan 或 notes）
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
                    jdbc.execute("ALTER TABLE note_favorites ADD CONSTRAINT fk_favorites_note FOREIGN KEY (note_id) REFERENCES " + tblNotes + "(id) ON DELETE CASCADE");
                } catch (Exception ignored2) {}
                try {
                    jdbc.execute("ALTER TABLE note_favorites ADD CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE");
                } catch (Exception ignored3) {}
            }
        } catch (Exception ignored) {
            // 不阻塞启动
        }

        // 修复历史环境下 note_likes 的外键指向旧表名 notes 导致插入失败的问题
        // 背景：部分库早期创建了 note_likes，并将 fk_likes_note 指向 notes(id)，
        //      当主表已迁移/统一为 shiyan 时，插入会因外键不匹配而报错。
        // 策略：检查 note_likes 上与 note_id 相关的外键，如果引用表名不是当前 tblNotes，
        //      则删除原外键并重建为引用 tblNotes(id)。
        try {
            // 查询现有外键约束名称与引用表
            var fks = jdbc.query(
                "SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE table_schema = ? AND table_name = 'note_likes' AND COLUMN_NAME = 'note_id' AND REFERENCED_TABLE_NAME IS NOT NULL",
                (rs, rowNum) -> new String[]{ rs.getString(1), rs.getString(2) },
                currentSchema
            );
            for (String[] fk : fks) {
                String fkName = fk[0];
                String refTbl = fk[1];
                if (fkName == null) continue;
                // 若引用表不是当前选定的主表名，则删除并重建
                if (refTbl != null && !refTbl.equalsIgnoreCase(tblNotes)) {
                    try { jdbc.execute("ALTER TABLE note_likes DROP FOREIGN KEY " + fkName); } catch (Exception ignoredDrop) {}
                    try { jdbc.execute("ALTER TABLE note_likes ADD CONSTRAINT " + fkName + " FOREIGN KEY (note_id) REFERENCES " + tblNotes + "(id) ON DELETE CASCADE"); } catch (Exception ignoredAdd) {}
                }
            }
        } catch (Exception ignored) {
            // 不阻塞启动：不同权限或版本下可能无法读取信息架构或修改外键
        }

        // 修复历史环境下 note_favorites 的外键如果仍引用旧表名（如 notes），统一为当前主表 tblNotes
        // 说明：收藏的 note_id 应级联删除（ON DELETE CASCADE），与当前 schema 保持一致
        try {
            var favFks = jdbc.query(
                "SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE table_schema = ? AND table_name = 'note_favorites' AND COLUMN_NAME = 'note_id' AND REFERENCED_TABLE_NAME IS NOT NULL",
                (rs, rowNum) -> new String[]{ rs.getString(1), rs.getString(2) },
                currentSchema
            );
            for (String[] fk : favFks) {
                String fkName = fk[0];
                String refTbl = fk[1];
                if (fkName == null) continue;
                if (refTbl != null && !refTbl.equalsIgnoreCase(tblNotes)) {
                    try { jdbc.execute("ALTER TABLE note_favorites DROP FOREIGN KEY " + fkName); } catch (Exception ignoredDrop) {}
                    try { jdbc.execute("ALTER TABLE note_favorites ADD CONSTRAINT " + fkName + " FOREIGN KEY (note_id) REFERENCES " + tblNotes + "(id) ON DELETE CASCADE"); } catch (Exception ignoredAdd) {}
                }
            }
        } catch (Exception ignored) {
            // 不阻塞启动
        }

        // 修复历史环境下 messages 的外键如仍引用旧表名（如 notes），统一为当前主表 tblNotes
        // 说明：消息的 note_id 是可空字段，删除 note 时应置空（ON DELETE SET NULL），与 schema.sql 保持一致
        try {
            var msgFks = jdbc.query(
                "SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME FROM information_schema.KEY_COLUMN_USAGE " +
                "WHERE table_schema = ? AND table_name = 'messages' AND COLUMN_NAME = 'note_id' AND REFERENCED_TABLE_NAME IS NOT NULL",
                (rs, rowNum) -> new String[]{ rs.getString(1), rs.getString(2) },
                currentSchema
            );
            for (String[] fk : msgFks) {
                String fkName = fk[0];
                String refTbl = fk[1];
                if (fkName == null) continue;
                if (refTbl != null && !refTbl.equalsIgnoreCase(tblNotes)) {
                    try { jdbc.execute("ALTER TABLE messages DROP FOREIGN KEY " + fkName); } catch (Exception ignoredDrop) {}
                    try { jdbc.execute("ALTER TABLE messages ADD CONSTRAINT " + fkName + " FOREIGN KEY (note_id) REFERENCES " + tblNotes + "(id) ON DELETE SET NULL"); } catch (Exception ignoredAdd) {}
                }
            }
        } catch (Exception ignored) {
            // 不阻塞启动
        }
    }
}