-- 为 users 表的 email 字段添加唯一索引，避免重复绑定
-- 请在 MySQL 中执行：
-- 使用你的数据库名替换 `yunbq`（如使用其他库名）

ALTER TABLE `users`
  ADD UNIQUE KEY `uniq_users_email` (`email`);

-- 如需回滚：
-- ALTER TABLE `users` DROP INDEX `uniq_users_email`;