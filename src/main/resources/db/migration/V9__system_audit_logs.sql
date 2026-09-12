CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    actor_id BIGINT,
    actor_name VARCHAR(255),
    actor_identifier VARCHAR(255),
    actor_roles VARCHAR(255),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id BIGINT,
    description VARCHAR(1000) NOT NULL,
    result VARCHAR(20) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at, id);
CREATE INDEX idx_audit_logs_action_time ON audit_logs (action, occurred_at);
CREATE INDEX idx_audit_logs_actor_time ON audit_logs (actor_id, occurred_at);
CREATE INDEX idx_audit_logs_target_time ON audit_logs (target_type, target_id, occurred_at);
CREATE INDEX idx_audit_logs_result_time ON audit_logs (result, occurred_at);
