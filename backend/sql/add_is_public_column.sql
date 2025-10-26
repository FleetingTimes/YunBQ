-- 手动迁移脚本：为 notes 表添加公开/私有列
ALTER TABLE notes ADD COLUMN IF NOT EXISTS is_public TINYINT(1) DEFAULT 0 AFTER archived;