-- 为 users 表添加个性签名字段
-- 数据库：yunbq，MySQL 8
-- 说明：为已有库添加列，长度 255，允许为空

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS signature VARCHAR(255) COMMENT '用户个性签名';