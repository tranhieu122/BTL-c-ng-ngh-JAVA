CREATE TABLE document_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(500),
    helpful BIT NOT NULL DEFAULT 0,
    easy_to_understand BIT NOT NULL DEFAULT 0,
    on_topic BIT NOT NULL DEFAULT 0,
    good_file_quality BIT NOT NULL DEFAULT 0,
    hidden BIT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_review_user UNIQUE (document_id, user_id),
    CONSTRAINT ck_document_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_document_reviews_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_reviews_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document_reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(40) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    handled_by BIGINT,
    handled_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_report_reason UNIQUE (document_id, reporter_id, reason),
    CONSTRAINT fk_document_reports_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_document_reports_handler FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_document_reviews_visible ON document_reviews (document_id, hidden, created_at);
CREATE INDEX idx_document_reports_status_created ON document_reports (status, created_at);
CREATE INDEX idx_document_reports_document ON document_reports (document_id, status, reason);
