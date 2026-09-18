-- Flyway Migration V16: Bảng lưu trữ Document Chunks và Vector Embeddings cho RAG pipeline
CREATE TABLE IF NOT EXISTS document_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    embedding_json MEDIUMTEXT NULL,
    token_count INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id)
        REFERENCES documents (id) ON DELETE CASCADE,
    INDEX idx_chunks_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
