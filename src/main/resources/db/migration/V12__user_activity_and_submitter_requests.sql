CREATE TABLE document_view_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    viewed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_view_history_user_document UNIQUE (user_id, document_id),
    CONSTRAINT fk_view_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_view_history_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_view_history_user_time ON document_view_history (user_id, viewed_at);

CREATE TABLE document_download_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    downloaded_at DATETIME(6) NOT NULL,
    download_count BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT uk_download_history_user_document UNIQUE (user_id, document_id),
    CONSTRAINT fk_download_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_download_history_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_download_history_user_time ON document_download_history (user_id, downloaded_at);

CREATE TABLE submitter_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_id BIGINT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(1000),
    reviewed_by BIGINT,
    created_at DATETIME(6) NOT NULL,
    reviewed_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_submitter_request_user FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_submitter_request_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_submitter_request_user_status ON submitter_requests (requester_id, status, created_at);
CREATE INDEX idx_submitter_request_status_time ON submitter_requests (status, created_at);
