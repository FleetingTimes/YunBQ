-- Create navigation sites table
-- Used to store specific site information under navigation categories
CREATE TABLE IF NOT EXISTS navigation_sites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Site ID',
    category_id BIGINT NOT NULL COMMENT 'Category ID',
    name VARCHAR(200) NOT NULL COMMENT 'Site name',
    url VARCHAR(500) NOT NULL COMMENT 'Site URL',
    description TEXT DEFAULT NULL COMMENT 'Site description',
    icon VARCHAR(200) DEFAULT NULL COMMENT 'Site icon (CSS class or icon path)',
    favicon_url VARCHAR(500) DEFAULT NULL COMMENT 'Site favicon URL',
    tags VARCHAR(500) DEFAULT NULL COMMENT 'Tags, comma separated',
    sort_order INT DEFAULT 0 COMMENT 'Sort order, smaller values appear first',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT 'Whether enabled',
    is_featured BOOLEAN DEFAULT FALSE COMMENT 'Whether featured site',
    click_count BIGINT DEFAULT 0 COMMENT 'Click count statistics',
    user_id BIGINT DEFAULT NULL COMMENT 'User ID who added this site (for custom sites)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    
    -- Indexes
    INDEX idx_category_id (category_id),
    INDEX idx_sort_order (sort_order),
    INDEX idx_enabled (is_enabled),
    INDEX idx_featured (is_featured),
    INDEX idx_click_count (click_count),
    INDEX idx_user_id (user_id),
    INDEX idx_tags (tags(100)),
    
    -- Foreign key constraints
    FOREIGN KEY (category_id) REFERENCES navigation_categories(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Navigation sites table';

-- Insert sample data
-- Note: These category_id values should match the actual IDs from navigation_categories table
INSERT INTO navigation_sites (category_id, name, url, description, icon, tags, sort_order, is_enabled, is_featured, user_id) VALUES
-- Sample sites for demonstration (using placeholder category_id values)
(1, 'GitHub', 'https://github.com', 'World largest code hosting platform', 'fab fa-github', 'git,code,opensource', 1, TRUE, TRUE, NULL),
(1, 'GitLab', 'https://gitlab.com', 'Enterprise Git code management platform', 'fab fa-gitlab', 'git,code,cicd', 2, TRUE, TRUE, NULL),
(1, 'Bitbucket', 'https://bitbucket.org', 'Atlassian code hosting service', 'fab fa-bitbucket', 'git,code,team', 3, TRUE, FALSE, NULL),

(2, 'CodePen', 'https://codepen.io', 'Frontend code online editor and sharing', 'fab fa-codepen', 'frontend,html,css,js', 1, TRUE, TRUE, NULL),
(2, 'JSFiddle', 'https://jsfiddle.net', 'JavaScript online testing tool', 'fas fa-code', 'javascript,testing,online', 2, TRUE, TRUE, NULL),
(2, 'CodeSandbox', 'https://codesandbox.io', 'Modern web app online development', 'fas fa-cube', 'react,vue,development', 3, TRUE, TRUE, NULL),

(3, 'Font Awesome', 'https://fontawesome.com', 'Most popular icon font library', 'fab fa-font-awesome', 'icons,fonts,free', 1, TRUE, TRUE, NULL),
(3, 'Feather Icons', 'https://feathericons.com', 'Simple and beautiful open source icons', 'fas fa-feather-alt', 'icons,simple,opensource', 2, TRUE, TRUE, NULL),
(3, 'Heroicons', 'https://heroicons.com', 'Tailwind CSS official icon library', 'fas fa-star', 'icons,tailwind,svg', 3, TRUE, FALSE, NULL),

(4, 'Coolors', 'https://coolors.co', 'Fast color scheme generator', 'fas fa-palette', 'colors,generator,design', 1, TRUE, TRUE, NULL),
(4, 'Adobe Color', 'https://color.adobe.com', 'Adobe official color tool', 'fas fa-fill-drip', 'colors,adobe,professional', 2, TRUE, TRUE, NULL),

(5, 'MDN Web Docs', 'https://developer.mozilla.org', 'Authoritative web development documentation', 'fab fa-firefox', 'web,documentation,authoritative', 1, TRUE, TRUE, NULL),
(5, 'W3Schools', 'https://www.w3schools.com', 'Web technology online tutorials', 'fas fa-graduation-cap', 'web,tutorials,online', 2, TRUE, TRUE, NULL),
(5, 'Stack Overflow', 'https://stackoverflow.com', 'Programmer Q&A community', 'fab fa-stack-overflow', 'qa,programming,community', 3, TRUE, TRUE, NULL);