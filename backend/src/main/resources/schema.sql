-- MySQL schema for YunBQ
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  email VARCHAR(128),
  signature VARCHAR(255),
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  content TEXT,
  tags VARCHAR(255),
  color VARCHAR(16),
  archived TINYINT(1) DEFAULT 0,
  is_public TINYINT(1) DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_user (user_id),
  CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 新增：点赞记录表，唯一用户对便签的点赞
CREATE TABLE IF NOT EXISTS note_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_note_user (note_id, user_id),
  INDEX idx_note (note_id),
  INDEX idx_user (user_id),
  CONSTRAINT fk_likes_note FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
  CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 新增：收藏记录表，唯一用户对便签的收藏
CREATE TABLE IF NOT EXISTS note_favorites (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_note_user_fav (note_id, user_id),
  INDEX idx_note_fav (note_id),
  INDEX idx_user_fav (user_id),
  CONSTRAINT fk_favorites_note FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 管理日志表
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,
  level VARCHAR(16) NOT NULL,
  message VARCHAR(512) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_created (created_at),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 日志模块：请求日志、认证日志、错误日志
-- 说明：以下三张表用于审计与故障排查；为了快速检索，均建立常用索引。
-- 注意：字段长度根据常见上限设定，若业务需要可调整。

-- 请求日志表：记录每个 HTTP 请求的关键信息与耗时
CREATE TABLE IF NOT EXISTS request_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  method VARCHAR(16) NOT NULL,           -- HTTP 方法，如 GET/POST
  uri VARCHAR(256) NOT NULL,             -- 请求路径（不含域名）
  query VARCHAR(512) NULL,               -- 查询参数字符串（可空）
  ip VARCHAR(64) NULL,                   -- 客户端 IP
  user_agent VARCHAR(256) NULL,          -- 客户端 UA
  status INT NOT NULL,                   -- 响应状态码
  duration_ms INT NOT NULL,              -- 处理耗时（毫秒）
  user_id BIGINT NULL,                   -- 触发该请求的用户 ID（匿名为 NULL）
  created_at DATETIME NOT NULL,          -- 记录时间（UTC）
  INDEX idx_created (created_at),
  INDEX idx_uri (uri),
  INDEX idx_status (status),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- 追加列：request_id（用于跨表串联）
-- 使用 IF NOT EXISTS 以便重复执行 schema 时不报错（MySQL 8+ 支持）。
-- =========================
-- 兼容 MySQL 5.7 的条件 ALTER：按列不存在时追加
-- request_logs.request_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'request_logs' AND COLUMN_NAME = 'request_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE request_logs ADD COLUMN request_id VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- auth_logs.request_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auth_logs' AND COLUMN_NAME = 'request_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE auth_logs ADD COLUMN request_id VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- error_logs.request_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_logs' AND COLUMN_NAME = 'request_id');
SET @ddl := IF(@col_exists = 0, 'ALTER TABLE error_logs ADD COLUMN request_id VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兼容 MySQL 5.7 的条件索引追加：按索引不存在时追加
-- request_logs.idx_request_logs_reqid
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'request_logs' AND INDEX_NAME = 'idx_request_logs_reqid');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE request_logs ADD INDEX idx_request_logs_reqid (request_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- auth_logs.idx_auth_logs_reqid
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'auth_logs' AND INDEX_NAME = 'idx_auth_logs_reqid');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE auth_logs ADD INDEX idx_auth_logs_reqid (request_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- error_logs.idx_error_logs_reqid
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_logs' AND INDEX_NAME = 'idx_error_logs_reqid');
SET @ddl := IF(@idx_exists = 0, 'ALTER TABLE error_logs ADD INDEX idx_error_logs_reqid (request_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 认证日志表：记录登录或令牌校验的成功/失败事件
CREATE TABLE IF NOT EXISTS auth_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,                   -- 成功认证的用户 ID（失败时可能为空）
  username VARCHAR(128) NULL,            -- 用户名（失败时用于审计尝试账号）
  success TINYINT(1) NOT NULL,           -- 是否成功：1 成功，0 失败
  reason VARCHAR(256) NULL,              -- 失败原因（成功为空）
  ip VARCHAR(64) NULL,                   -- 客户端 IP
  user_agent VARCHAR(256) NULL,          -- 客户端 UA
  created_at DATETIME NOT NULL,          -- 记录时间（UTC）
  INDEX idx_created (created_at),
  INDEX idx_success (success),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 错误日志表：持久化未处理异常（便于定位故障）
CREATE TABLE IF NOT EXISTS error_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,                   -- 当前登录用户 ID（未登录为 NULL）
  path VARCHAR(256) NULL,                -- 发生异常的请求路径
  exception VARCHAR(128) NOT NULL,       -- 异常类名
  message VARCHAR(512) NULL,             -- 异常消息
  stack_trace TEXT NULL,                 -- 堆栈信息（可选，体积较大）
  created_at DATETIME NOT NULL,          -- 记录时间（UTC）
  INDEX idx_created (created_at),
  INDEX idx_exception (exception),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;