-- 为 users 表添加头像 URL 字段
-- 执行时间：2024-10-26
-- 描述：支持用户头像上传功能

ALTER TABLE users ADD COLUMN avatar_url VARCHAR(255) COMMENT '用户头像URL';

-- 可选：为现有用户设置默认头像（如果需要）
-- UPDATE users SET avatar_url = '/uploads/avatars/default-avatar.png' WHERE avatar_url IS NULL;