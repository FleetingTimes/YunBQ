-- 增量迁移：创建错误日志表（用于持久化服务端未处理异常）
CREATE TABLE IF NOT EXISTS error_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  path VARCHAR(256) NULL,
  exception VARCHAR(128) NOT NULL,
  message VARCHAR(512) NULL,
  stack_trace TEXT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_created (created_at),
  INDEX idx_exception (exception),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;