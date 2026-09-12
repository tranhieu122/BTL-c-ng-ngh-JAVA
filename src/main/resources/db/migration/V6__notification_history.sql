CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    type VARCHAR(40) NOT NULL,
    document_id BIGINT,
    event_key VARCHAR(180) NOT NULL,
    is_read BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_event_key UNIQUE (event_key),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_notifications_recipient_created (recipient_id, created_at, id),
    INDEX idx_notifications_recipient_read_created (recipient_id, is_read, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

