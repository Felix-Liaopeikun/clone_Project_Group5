-- ================================================================
-- 用户种子数据
-- 幂等：INSERT IGNORE（key 冲突自动跳过）
--
--   默认账号：
--   ┌─────────┬──────────────┬────────────┐
--   │ username │ password    │   role     │
--   ├─────────┼──────────────┼────────────┤
--   │ admin   │ Admin@123456│  ADMIN     │
--   │ test    │ Test@123456 │  USER      │
--   └─────────┴─────────────┴────────────┘
--
--   BCrypt 哈希（rounds=10）：
--     Admin@123456  →  $2b$10$RCC4FNm.T6VT9M1IlohLNOMzKcMab8glDD70o87XM8XKb4bdhrT3C
--     Test@123456   →  $2b$10$jHOUrmP7uuyboDqSZgrj8el/QourzPXt3M8Jjpnlmb63dXuJJ87yi
-- ================================================================

INSERT IGNORE INTO user (id, username, password, role) VALUES
(1, 'admin', '$2b$10$RCC4FNm.T6VT9M1IlohLNOMzKcMab8glDD70o87XM8XKb4bdhrT3C', 'ADMIN'),
(2, 'test',  '$2b$10$jHOUrmP7uuyboDqSZgrj8el/QourzPXt3M8Jjpnlmb63dXuJJ87yi', 'USER');

ALTER TABLE qa_history
    ADD COLUMN conversation_id VARCHAR(36) NULL,
    ADD INDEX idx_conversation (conversation_id);