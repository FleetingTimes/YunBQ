-- YunBQ 初始化建表 DDL（仅结构，无初始数据）
-- 数据库：MySQL 8+（推荐），字符集：utf8mb4，排序规则：utf8mb4_unicode_ci
-- 说明：外键策略根据业务选择 CASCADE/SET NULL；索引覆盖常用查询字段
-- 声明：项目默认在首次部署时由 resources/schema.sql 自动建表；
--      本文件仅用于手动初始化数据库（命令行执行），与 resources/schema.sql 的表结构保持一致。

-- 数据库创建与切换
CREATE DATABASE IF NOT EXISTS `yunbq` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `yunbq`;

-- 表：users（用户基础信息与认证）
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  email VARCHAR(128),
  signature VARCHAR(255),
  avatar_url VARCHAR(255),
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  created_at DATETIME NOT NULL,
  -- 唯一索引：用户名与邮箱
  UNIQUE KEY uniq_users_username (username),
  UNIQUE KEY uniq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：shiyan（拾言/便签主表）
CREATE TABLE IF NOT EXISTS shiyan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  content TEXT,
  tags VARCHAR(255),
  color VARCHAR(16),
  archived TINYINT(1) DEFAULT 0,
  is_public TINYINT(1) DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_shiyan_user (user_id),
  -- 外键：删除用户时级联删除其拾言
  CONSTRAINT fk_shiyan_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：note_likes（拾言点赞记录，一用户对一拾言仅一条）
CREATE TABLE IF NOT EXISTS note_likes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_note_user (note_id, user_id),
  INDEX idx_likes_note (note_id),
  INDEX idx_likes_user (user_id),
  -- 外键：删除拾言或用户时级联删除对应点赞
  CONSTRAINT fk_likes_note FOREIGN KEY (note_id) REFERENCES shiyan(id) ON DELETE CASCADE,
  CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：note_favorites（拾言收藏记录，一用户对一拾言仅一条）
CREATE TABLE IF NOT EXISTS note_favorites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_note_user_fav (note_id, user_id),
  INDEX idx_fav_note (note_id),
  INDEX idx_fav_user (user_id),
  -- 外键：删除拾言或用户时级联删除对应收藏
  CONSTRAINT fk_favorites_note FOREIGN KEY (note_id) REFERENCES shiyan(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：audit_logs（审计日志，记录后台操作事件）
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  level VARCHAR(16) NOT NULL,
  message VARCHAR(512) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_audit_created (created_at),
  INDEX idx_audit_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：request_logs（请求日志，记录HTTP请求关键指标）
CREATE TABLE IF NOT EXISTS request_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  method VARCHAR(16) NOT NULL,
  uri VARCHAR(256) NOT NULL,
  query VARCHAR(512) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(256) NULL,
  status INT NOT NULL,
  duration_ms INT NOT NULL,
  user_id BIGINT NULL,
  request_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_req_created (created_at),
  INDEX idx_req_uri (uri),
  INDEX idx_req_status (status),
  INDEX idx_req_user (user_id),
  INDEX idx_request_logs_reqid (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：auth_logs（认证日志，记录登录或令牌校验）
CREATE TABLE IF NOT EXISTS auth_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  username VARCHAR(128) NULL,
  success TINYINT(1) NOT NULL,
  reason VARCHAR(256) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(256) NULL,
  request_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_auth_created (created_at),
  INDEX idx_auth_success (success),
  INDEX idx_auth_user (user_id),
  INDEX idx_auth_logs_reqid (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：error_logs（错误日志，记录未处理异常）
CREATE TABLE IF NOT EXISTS error_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NULL,
  path VARCHAR(256) NULL,
  exception VARCHAR(128) NOT NULL,
  message VARCHAR(512) NULL,
  stack_trace TEXT NULL,
  request_id VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_err_created (created_at),
  INDEX idx_err_exception (exception),
  INDEX idx_err_user (user_id),
  INDEX idx_error_logs_reqid (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：navigation_categories（导航分类，支持两级）
CREATE TABLE IF NOT EXISTS navigation_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT DEFAULT NULL,
  name VARCHAR(100) NOT NULL,
  icon VARCHAR(100) DEFAULT NULL,
  description TEXT DEFAULT NULL,
  sort_order INT DEFAULT 0,
  is_enabled BOOLEAN DEFAULT TRUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_navcat_parent_id (parent_id),
  INDEX idx_navcat_sort_order (sort_order),
  INDEX idx_navcat_enabled (is_enabled),
  -- 外键：自引用父分类，删除父分类时级联删除子分类
  FOREIGN KEY (parent_id) REFERENCES navigation_categories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：navigation_sites（导航站点信息）
CREATE TABLE IF NOT EXISTS navigation_sites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id BIGINT NOT NULL,
  name VARCHAR(200) NOT NULL,
  url VARCHAR(500) NOT NULL,
  description TEXT DEFAULT NULL,
  icon VARCHAR(200) DEFAULT NULL,
  favicon_url VARCHAR(500) DEFAULT NULL,
  tags VARCHAR(500) DEFAULT NULL,
  sort_order INT DEFAULT 0,
  is_enabled BOOLEAN DEFAULT TRUE,
  is_featured BOOLEAN DEFAULT FALSE,
  click_count BIGINT DEFAULT 0,
  user_id BIGINT DEFAULT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_navsite_category_id (category_id),
  INDEX idx_navsite_sort_order (sort_order),
  INDEX idx_navsite_enabled (is_enabled),
  INDEX idx_navsite_featured (is_featured),
  INDEX idx_navsite_click_count (click_count),
  INDEX idx_navsite_user_id (user_id),
  INDEX idx_navsite_tags (tags(100)),
  -- 外键：删除分类时级联删除站点；删除用户时置空添加者
  FOREIGN KEY (category_id) REFERENCES navigation_categories(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 表：messages（消息中心，收到的赞/收藏/系统通知等）
CREATE TABLE IF NOT EXISTS messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  type VARCHAR(16) NOT NULL,
  actor_user_id BIGINT NULL,
  receiver_user_id BIGINT NOT NULL,
  note_id BIGINT NULL,
  message VARCHAR(512) NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_msg_receiver (receiver_user_id),
  INDEX idx_msg_type (type),
  INDEX idx_msg_note (note_id),
  -- 外键：删除触发者置空；删除接收者级联；删除拾言置空
  FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY (receiver_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (note_id) REFERENCES shiyan(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
