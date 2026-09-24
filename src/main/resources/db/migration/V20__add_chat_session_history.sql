-- V20: Tao bang chat_session va chat_message quan ly lich su hoi thoai da phien cho EduBot

CREATE TABLE chat_session (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_id             BIGINT          NOT NULL,
    title               VARCHAR(255)    NOT NULL,
    scope_type          ENUM('GLOBAL', 'DOCUMENT') NOT NULL DEFAULT 'GLOBAL',
    scoped_document_id  BIGINT          NULL,
    status              ENUM('ACTIVE', 'ARCHIVED', 'DELETED') NOT NULL DEFAULT 'ACTIVE',
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_session_doc  FOREIGN KEY (scoped_document_id) REFERENCES documents (id) ON DELETE SET NULL,
    INDEX idx_chat_session_user_status_updated (user_id, status, updated_at DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Phien hoi thoai nguoi dung voi tro ly AI EduBot';

CREATE TABLE chat_message (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    session_id          BIGINT          NOT NULL,
    sender_type         ENUM('USER', 'ASSISTANT', 'SYSTEM') NOT NULL,
    content             MEDIUMTEXT      NOT NULL,
    citations_json      JSON            NULL,
    feedback_rating     TINYINT         NULL COMMENT '1: Thumbs Up, -1: Thumbs Down, NULL: Chua danh gia',
    client_message_id   VARCHAR(128)    NULL COMMENT 'UUID/Idempotency key tu client',
    created_at          DATETIME        NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session (id) ON DELETE CASCADE,
    INDEX idx_chat_message_session_created (session_id, created_at ASC),
    INDEX idx_chat_message_client_id (client_message_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Chi tiet cac tin nhan trong tung phien hoi thoai EduBot';
