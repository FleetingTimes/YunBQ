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

-- 插入示例数据
-- 注意：这些category_id值应与navigation_categories表中的实际ID匹配
-- 根分类
INSERT INTO navigation_categories (name, icon, description, sort_order, is_enabled) VALUES
('开发工具', 'fas fa-code', '编程开发工具和资源', 1, TRUE),
('设计资源', 'fas fa-palette', '设计相关工具和资源', 2, TRUE),
('学习教育', 'fas fa-graduation-cap', '在线学习和教育平台', 3, TRUE),
('生活服务', 'fas fa-life-ring', '日常生活相关服务', 4, TRUE),
('娱乐休闲', 'fas fa-gamepad', '娱乐和休闲网站', 5, TRUE);

-- 子分类
INSERT INTO navigation_categories (parent_id, name, icon, description, sort_order, is_enabled) VALUES
-- 开发工具子分类
(1, '代码托管', 'fab fa-git-alt', 'Git仓库和版本控制', 1, TRUE),
(1, '在线编辑器', 'fas fa-edit', '在线代码编辑器和运行环境', 2, TRUE),
(1, '开发文档', 'fas fa-book', '编程语言和框架文档', 3, TRUE),
-- 设计资源子分类
(2, '图标资源', 'fas fa-icons', '免费图标资源网站', 1, TRUE),
(2, '配色方案', 'fas fa-fill-drip', '颜色搭配和调色板工具', 2, TRUE),
(2, '字体资源', 'fas fa-font', '免费字体下载网站', 3, TRUE),
-- 学习教育子分类
(3, '编程学习', 'fas fa-laptop-code', '编程技能学习平台', 1, TRUE),
(3, '在线课程', 'fas fa-chalkboard-teacher', '各类在线课程平台', 2, TRUE),
(3, '技术博客', 'fas fa-blog', '技术分享和博客网站', 3, TRUE);

-- 插入示例站点数据
-- 注意：这些category_id值应与实际的分类ID匹配
INSERT INTO navigation_sites (category_id, name, url, description, icon, tags, sort_order, is_enabled, is_featured, user_id) VALUES
-- 代码托管站点（假设代码托管分类ID为6）
(6, 'GitHub', 'https://github.com', '全球最大的代码托管平台', 'fab fa-github', 'git,代码,开源', 1, TRUE, TRUE, NULL),
(6, 'GitLab', 'https://gitlab.com', '企业级Git代码管理平台', 'fab fa-gitlab', 'git,代码,cicd', 2, TRUE, TRUE, NULL),
(6, 'Bitbucket', 'https://bitbucket.org', 'Atlassian代码托管服务', 'fab fa-bitbucket', 'git,代码,团队', 3, TRUE, FALSE, NULL),

-- 在线编辑器站点（假设在线编辑器分类ID为7）
(7, 'CodePen', 'https://codepen.io', '前端代码在线编辑器和分享', 'fab fa-codepen', '前端,html,css,js', 1, TRUE, TRUE, NULL),
(7, 'JSFiddle', 'https://jsfiddle.net', 'JavaScript在线测试工具', 'fas fa-code', 'javascript,测试,在线', 2, TRUE, TRUE, NULL),
(7, 'CodeSandbox', 'https://codesandbox.io', '现代Web应用在线开发', 'fas fa-cube', 'react,vue,开发', 3, TRUE, TRUE, NULL),

-- 图标资源站点（假设图标资源分类ID为9）
(9, 'Feather Icons', 'https://feathericons.com', '简洁美观的开源图标', 'fas fa-feather-alt', '图标,简洁,开源', 2, TRUE, TRUE, NULL),
(9, 'Heroicons', 'https://heroicons.com', 'Tailwind CSS官方图标库', 'fas fa-star', '图标,tailwind,svg', 3, TRUE, FALSE, NULL),

-- 配色方案站点（假设配色方案分类ID为10）
(10, 'Coolors', 'https://coolors.co', '快速配色方案生成器', 'fas fa-palette', '颜色,生成器,设计', 1, TRUE, TRUE, NULL),
(10, 'Adobe Color', 'https://color.adobe.com', 'Adobe官方配色工具', 'fas fa-fill-drip', '颜色,adobe,专业', 2, TRUE, TRUE, NULL),

-- 编程学习站点（假设编程学习分类ID为12）
(12, 'MDN Web Docs', 'https://developer.mozilla.org', '权威的Web开发文档', 'fab fa-firefox', 'web,文档,权威', 1, TRUE, TRUE, NULL),
(12, 'W3Schools', 'https://www.w3schools.com', 'Web技术在线教程', 'fas fa-graduation-cap', 'web,教程,在线', 2, TRUE, TRUE, NULL),
(12, 'Stack Overflow', 'https://stackoverflow.com', '程序员问答社区', 'fab fa-stack-overflow', '问答,编程,社区', 3, TRUE, TRUE, NULL);