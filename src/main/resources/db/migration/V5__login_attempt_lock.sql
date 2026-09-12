ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN login_locked_until DATETIME(6);

