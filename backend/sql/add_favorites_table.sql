-- 手动创建收藏记录表（MySQL / MariaDB）
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

-- 如外键创建失败（权限或引擎限制），可先去掉外键：
-- CREATE TABLE IF NOT EXISTS note_favorites (
--   id BIGINT PRIMARY KEY AUTO_INCREMENT,
--   note_id BIGINT NOT NULL,
--   user_id BIGINT NOT NULL,
--   created_at DATETIME NOT NULL,
--   UNIQUE KEY uniq_note_user_fav (note_id, user_id),
--   INDEX idx_note_fav (note_id),
--   INDEX idx_user_fav (user_id)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;