-- V19: Tao bang chat_message_feedback luu danh gia Thumbs Up / Thumbs Down cua nguoi dung
-- cho cau tra loi cua Tro ly AI (EduBot).

CREATE TABLE chat_message_feedback (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    message_id  VARCHAR(128)    NOT NULL COMMENT 'ID dinh danh cau tra loi trong phien chat',
    user_id     BIGINT          NULL     COMMENT 'FK users.id (NULL neu nguoi dung chua dang nhap)',
    client_ip   VARCHAR(64)     NULL     COMMENT 'Dia chi IP cua client gui danh gia',
    rating      VARCHAR(16)     NOT NULL COMMENT 'THUMBS_UP | THUMBS_DOWN',
    reason      VARCHAR(64)     NULL     COMMENT 'Ly do ngan (tuy chon)',
    comment     VARCHAR(1000)   NULL     COMMENT 'Nhan xet chi tiet (tuy chon)',
    created_at  DATETIME        NOT NULL,
    updated_at  DATETIME        NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_feedback_message_id (message_id),
    INDEX idx_feedback_user_id    (user_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='Danh gia phan hoi (Thumbs Up/Down) cua nguoi dung cho cau tra loi EduBot';
