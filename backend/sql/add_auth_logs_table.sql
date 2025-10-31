-- 增量迁移：创建认证日志表（用于记录登录/令牌校验的成功或失败）
CREATE TABLE IF NOT EXISTS auth_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  username VARCHAR(128) NULL,
  success TINYINT(1) NOT NULL,
  reason VARCHAR(256) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(256) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_created (created_at),
  INDEX idx_success (success),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;