-- MySQL schema for YunBQ
-- 声明：本文件可由 Spring SQL 初始化在首次部署时自动执行（当 spring.sql.init.mode=always）。
--       若使用 backend/sql/init_schema.sql 手动初始化，可将 spring.sql.init.mode 设为 never。
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(64),
  email VARCHAR(128),
  signature VARCHAR(255),
  avatar_url VARCHAR(255),
  role VARCHAR(16) NOT NULL DEFAULT 'USER',
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 主表：拾言（原 notes 重命名为 shiyan）
CREATE TABLE IF NOT EXISTS shiyan (
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
  CONSTRAINT fk_shiyan_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 新增：点赞记录表，唯一用户对拾言的点赞
CREATE TABLE IF NOT EXISTS note_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_note_user (note_id, user_id),
  INDEX idx_note (note_id),
  INDEX idx_user (user_id),
  CONSTRAINT fk_likes_note FOREIGN KEY (note_id) REFERENCES shiyan(id) ON DELETE CASCADE,
  CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 新增：收藏记录表，唯一用户对拾言的收藏
CREATE TABLE IF NOT EXISTS note_favorites (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  note_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uniq_note_user_fav (note_id, user_id),
  INDEX idx_note_fav (note_id),
  INDEX idx_user_fav (user_id),
  CONSTRAINT fk_favorites_note FOREIGN KEY (note_id) REFERENCES shiyan(id) ON DELETE CASCADE,
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
  request_id VARCHAR(64) NULL,           -- 请求唯一标识（用于跨表串联）
  created_at DATETIME NOT NULL,          -- 记录时间（UTC）
  INDEX idx_created (created_at),
  INDEX idx_uri (uri),
  INDEX idx_status (status),
  INDEX idx_user (user_id),
  INDEX idx_request_logs_reqid (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已在 CREATE TABLE 中包含 request_id 与其索引，无需条件 ALTER

-- 认证日志表：记录登录或令牌校验的成功/失败事件
CREATE TABLE IF NOT EXISTS auth_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,                   -- 成功认证的用户 ID（失败时可能为空）
  username VARCHAR(128) NULL,            -- 用户名（失败时用于审计尝试账号）
  success TINYINT(1) NOT NULL,           -- 是否成功：1 成功，0 失败
  reason VARCHAR(256) NULL,              -- 失败原因（成功为空）
  ip VARCHAR(64) NULL,                   -- 客户端 IP
  user_agent VARCHAR(256) NULL,          -- 客户端 UA
  request_id VARCHAR(64) NULL,           -- 请求唯一标识
  created_at DATETIME NOT NULL,          -- 记录时间（UTC）
  INDEX idx_created (created_at),
  INDEX idx_success (success),
  INDEX idx_user (user_id),
  INDEX idx_auth_logs_reqid (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 错误日志表：持久化未处理异常（便于定位故障）
CREATE TABLE IF NOT EXISTS error_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NULL,                   -- 当前登录用户 ID（未登录为 NULL）
  path VARCHAR(256) NULL,                -- 发生异常的请求路径
  exception VARCHAR(128) NOT NULL,       -- 异常类名
  message VARCHAR(512) NULL,             -- 异常消息
  stack_trace TEXT NULL,                 -- 堆栈信息（可选，体积较大）
  request_id VARCHAR(64) NULL,           -- 请求唯一标识
  created_at DATETIME NOT NULL,          -- 记录时间（UTC）
  INDEX idx_created (created_at),
  INDEX idx_exception (exception),
  INDEX idx_user (user_id),
  INDEX idx_error_logs_reqid (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 导航模块：导航分类表和导航站点表
-- 说明：用于管理侧边栏导航分类和具体站点信息
-- ------------------------------------------------------------

-- 导航分类表（支持两级分类）
-- 用于侧边栏导航分类管理
CREATE TABLE IF NOT EXISTS navigation_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父分类ID，NULL表示根分类',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    icon VARCHAR(100) DEFAULT NULL COMMENT '分类图标（CSS类或图标路径）',
    description TEXT DEFAULT NULL COMMENT '分类描述',
    sort_order INT DEFAULT 0 COMMENT '排序顺序，数值越小越靠前',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort_order (sort_order),
    INDEX idx_enabled (is_enabled),
    
    -- 外键约束（自引用）
    FOREIGN KEY (parent_id) REFERENCES navigation_categories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导航分类表';

-- 导航站点表
-- 用于存储导航分类下的具体站点信息
CREATE TABLE IF NOT EXISTS navigation_sites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '站点ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    name VARCHAR(200) NOT NULL COMMENT '站点名称',
    url VARCHAR(500) NOT NULL COMMENT '站点URL',
    description TEXT DEFAULT NULL COMMENT '站点描述',
    icon VARCHAR(200) DEFAULT NULL COMMENT '站点图标（CSS类或图标路径）',
    favicon_url VARCHAR(500) DEFAULT NULL COMMENT '站点favicon URL',
    tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    sort_order INT DEFAULT 0 COMMENT '排序顺序，数值越小越靠前',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    is_featured BOOLEAN DEFAULT FALSE COMMENT '是否为推荐站点',
    click_count BIGINT DEFAULT 0 COMMENT '点击次数统计',
    user_id BIGINT DEFAULT NULL COMMENT '添加此站点的用户ID（用于自定义站点）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引
    INDEX idx_category_id (category_id),
    INDEX idx_sort_order (sort_order),
    INDEX idx_enabled (is_enabled),
    INDEX idx_featured (is_featured),
    INDEX idx_click_count (click_count),
    INDEX idx_user_id (user_id),
    INDEX idx_tags (tags(100)),
    
    -- 外键约束
    FOREIGN KEY (category_id) REFERENCES navigation_categories(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导航站点表';


-- 消息中心：用于“收到的赞/收藏/系统通知”等消息的展示
-- 设计说明：
-- - type：消息类型（like/favorite/reply/at/system 等，采用短字符串便于扩展）；
-- - actor_user_id：触发该消息的用户（点赞者、收藏者等），系统消息可为空；
-- - receiver_user_id：接收该消息的用户（被点赞的拾言作者等）；
-- - note_id：关联拾言的 ID（系统消息可为空）；
-- - message：附加的行为文案（可空，前端也会根据 type 自动渲染）；
-- - is_read：是否已读（0 未读 / 1 已读）；
-- - created_at：创建时间；
CREATE TABLE IF NOT EXISTS messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  type VARCHAR(16) NOT NULL,
  actor_user_id BIGINT NULL,
  receiver_user_id BIGINT NOT NULL,
  note_id BIGINT NULL,
  message VARCHAR(512) NULL,
  is_read TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_receiver (receiver_user_id),
  INDEX idx_type (type),
  INDEX idx_note (note_id),
  CONSTRAINT fk_messages_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_messages_receiver FOREIGN KEY (receiver_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_note FOREIGN KEY (note_id) REFERENCES shiyan(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

