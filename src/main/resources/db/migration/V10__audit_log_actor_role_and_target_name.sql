ALTER TABLE audit_logs ADD COLUMN target_name VARCHAR(255);
CREATE INDEX idx_audit_logs_actor_roles_time ON audit_logs (actor_roles, occurred_at);
