-- 文档摘要与知识库系统 - 数据库初始化脚本
-- 与 docs/design/CONTRACT.md 保持一致
-- 适用于 MySQL 8.0+，字符集 utf8mb4
-- 幂等：使用 CREATE DATABASE IF NOT EXISTS / CREATE TABLE IF NOT EXISTS，可重复执行。

CREATE DATABASE IF NOT EXISTS doc_summary DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE doc_summary;

-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE COMMENT '用户名',
    password    VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS document (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_path     VARCHAR(512) NOT NULL,
    file_type     VARCHAR(20)  NOT NULL COMMENT 'pdf/txt/md/docx',
    file_size     BIGINT       NOT NULL DEFAULT 0,
    category      VARCHAR(64)  NOT NULL DEFAULT '未分类' COMMENT 'AI 自动分类',
    tags          VARCHAR(255) NOT NULL DEFAULT '' COMMENT '英文逗号分隔',
    summary       TEXT         NULL COMMENT 'AI 摘要',
    status        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/done/failed',
    error_msg     VARCHAR(500) NULL,
    owner_id      BIGINT       NULL COMMENT '上传者 user.id，NULL 表示公开文档',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_status (status),
    INDEX idx_created (created_at),
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS document_chunk (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    document_id  BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL COMMENT '段落序号',
    content      MEDIUMTEXT   NOT NULL,
    char_count   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_doc (document_id),
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qa_history (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    owner_id     BIGINT       NULL COMMENT '提问者 user.id，NULL 表示匿名问答',
    question     TEXT         NOT NULL,
    answer       MEDIUMTEXT   NOT NULL,
    citations    JSON         NULL COMMENT '引用来源 JSON 数组',
    rating       INT          NULL COMMENT '评分 1-5 星，NULL 表示未评分',
    useful       TINYINT(1)   NULL COMMENT '是否有用（0/1）',
    feedback     VARCHAR(500) NULL COMMENT '用户反馈',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_created (created_at),
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 增量升级：为已存在的表添加评测字段（幂等）
ALTER TABLE qa_history
    ADD COLUMN IF NOT EXISTS rating   INT          NULL COMMENT '评分 1-5 星',
    ADD COLUMN IF NOT EXISTS useful   TINYINT(1)   NULL COMMENT '是否有用',
    ADD COLUMN IF NOT EXISTS feedback VARCHAR(500) NULL COMMENT '用户反馈';

-- 增量升级：添加多轮对话支持（幂等）
ALTER TABLE qa_history
    ADD COLUMN IF NOT EXISTS conversation_id VARCHAR(36) NULL COMMENT '会话 ID，用于多轮对话关联',
    ADD INDEX IF NOT EXISTS idx_conversation (conversation_id);
