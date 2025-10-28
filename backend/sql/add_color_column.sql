-- 手动迁移脚本：为 notes 表添加颜色列
-- 适用：MySQL/MariaDB 10.3+（支持 ADD COLUMN IF NOT EXISTS）

ALTER TABLE notes
  ADD COLUMN IF NOT EXISTS color VARCHAR(16) NULL COMMENT '便签颜色(如#ffd966)' AFTER tags;