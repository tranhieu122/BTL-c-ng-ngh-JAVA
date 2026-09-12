ALTER TABLE documents ADD COLUMN summary TEXT;
ALTER TABLE documents ADD COLUMN keywords VARCHAR(1000);
ALTER TABLE documents ADD COLUMN language_code VARCHAR(10) DEFAULT 'vi';
ALTER TABLE documents ADD COLUMN learning_resource_type VARCHAR(40) DEFAULT 'OTHER';
ALTER TABLE documents ADD COLUMN education_level VARCHAR(30) DEFAULT 'ALL_LEVELS';
ALTER TABLE documents ADD COLUMN license_type VARCHAR(40) DEFAULT 'ALL_RIGHTS_RESERVED';
ALTER TABLE documents ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE documents ADD COLUMN download_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE approval_history ADD COLUMN content_quality_score INT;
ALTER TABLE approval_history ADD COLUMN teaching_effectiveness_score INT;
ALTER TABLE approval_history ADD COLUMN ease_of_use_score INT;

CREATE TABLE document_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    file_type VARCHAR(255),
    file_size BIGINT,
    checksum VARCHAR(64),
    change_note VARCHAR(1000),
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_version_number UNIQUE (document_id, version_number),
    CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_versions_user FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO document_versions (
    document_id, version_number, file_name, file_path, file_type, file_size, created_by, created_at, change_note
)
SELECT id, 1, file_name, file_path, file_type, file_size, created_by, created_at, 'Phiên bản ban đầu'
FROM documents
WHERE file_name IS NOT NULL AND file_path IS NOT NULL;

CREATE TABLE bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_bookmark_user_document UNIQUE (user_id, document_id),
    CONSTRAINT fk_bookmarks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmarks_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document_collections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_collection_owner_name UNIQUE (owner_id, name),
    CONSTRAINT fk_collections_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE collection_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    collection_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    added_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_collection_item UNIQUE (collection_id, document_id),
    CONSTRAINT fk_collection_items_collection FOREIGN KEY (collection_id) REFERENCES document_collections (id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_items_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_documents_academic_filters
    ON documents (status, category_id, learning_resource_type, education_level, license_type, language_code);
CREATE INDEX idx_document_versions_document ON document_versions (document_id, version_number);
CREATE INDEX idx_bookmarks_user ON bookmarks (user_id, created_at);
CREATE INDEX idx_collection_items_collection ON collection_items (collection_id, added_at);
