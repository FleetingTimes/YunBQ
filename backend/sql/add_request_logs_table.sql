-- 增量迁移：创建请求日志表（用于记录每个 HTTP 请求的关键信息）
CREATE TABLE IF NOT EXISTS request_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  method VARCHAR(16) NOT NULL,
  uri VARCHAR(256) NOT NULL,
  query VARCHAR(512) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(256) NULL,
  status INT NOT NULL,
  duration_ms INT NOT NULL,
  user_id BIGINT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_created (created_at),
  INDEX idx_uri (uri),
  INDEX idx_status (status),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;