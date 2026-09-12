ALTER TABLE notifications
    ADD COLUMN sender_id BIGINT NULL AFTER recipient_id,
    ADD COLUMN level VARCHAR(30) NOT NULL DEFAULT 'NORMAL' AFTER message,
    ADD COLUMN send_at DATETIME(6) NULL AFTER created_at,
    ADD COLUMN read_at DATETIME(6) NULL AFTER send_at;

UPDATE notifications SET send_at = created_at WHERE send_at IS NULL;
UPDATE notifications SET read_at = created_at WHERE is_read = 1 AND read_at IS NULL;

ALTER TABLE notifications
    MODIFY send_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT fk_notifications_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE SET NULL,
    ADD INDEX idx_notifications_sender_created (sender_id, created_at, id);
