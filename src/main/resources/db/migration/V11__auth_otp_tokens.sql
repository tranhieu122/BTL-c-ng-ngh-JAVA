CREATE TABLE auth_otp_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_hash VARCHAR(88) NOT NULL,
    attempts INT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    resend_available_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_auth_otp_email_purpose_created (email, purpose, created_at),
    INDEX idx_auth_otp_consumed_expires (consumed_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
