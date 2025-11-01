-- Create navigation categories table (supports two-level categories)
-- Used for sidebar navigation category management
CREATE TABLE IF NOT EXISTS navigation_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Category ID',
    parent_id BIGINT DEFAULT NULL COMMENT 'Parent category ID, NULL for root categories',
    name VARCHAR(100) NOT NULL COMMENT 'Category name',
    icon VARCHAR(100) DEFAULT NULL COMMENT 'Category icon (CSS class or icon path)',
    description TEXT DEFAULT NULL COMMENT 'Category description',
    sort_order INT DEFAULT 0 COMMENT 'Sort order, smaller values appear first',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT 'Whether enabled',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    
    -- Indexes
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort_order (sort_order),
    INDEX idx_enabled (is_enabled),
    
    -- Foreign key constraint (self-referencing)
    FOREIGN KEY (parent_id) REFERENCES navigation_categories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Navigation categories table';

-- Insert sample data
-- Root categories
INSERT INTO navigation_categories (name, icon, description, sort_order, is_enabled) VALUES
('Development Tools', 'fas fa-code', 'Programming development tools and resources', 1, TRUE),
('Design Resources', 'fas fa-palette', 'Design tools and materials', 2, TRUE),
('Learning & Education', 'fas fa-graduation-cap', 'Online learning and education platforms', 3, TRUE),
('Life Services', 'fas fa-life-ring', 'Daily life related services', 4, TRUE),
('Entertainment', 'fas fa-gamepad', 'Entertainment and leisure websites', 5, TRUE);

-- Sub categories
INSERT INTO navigation_categories (parent_id, name, icon, description, sort_order, is_enabled) VALUES
-- Development Tools subcategories
(1, 'Code Hosting', 'fab fa-git-alt', 'Git repositories and version control', 1, TRUE),
(1, 'Online Editors', 'fas fa-edit', 'Online code editors and runtime environments', 2, TRUE),
(1, 'Documentation', 'fas fa-book', 'Programming language and framework docs', 3, TRUE),
-- Design Resources subcategories
(2, 'Icons', 'fas fa-icons', 'Free icon resource websites', 1, TRUE),
(2, 'Color Schemes', 'fas fa-fill-drip', 'Color matching and palette tools', 2, TRUE),
(2, 'Fonts', 'fas fa-font', 'Free font download websites', 3, TRUE),
-- Learning & Education subcategories
(3, 'Programming Learning', 'fas fa-laptop-code', 'Programming skill learning platforms', 1, TRUE),
(3, 'Online Courses', 'fas fa-chalkboard-teacher', 'Various online course platforms', 2, TRUE),
(3, 'Tech Blogs', 'fas fa-blog', 'Technology sharing and blog websites', 3, TRUE);