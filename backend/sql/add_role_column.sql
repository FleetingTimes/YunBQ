-- 兼容已存在的数据库，增加 users.role 字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER';