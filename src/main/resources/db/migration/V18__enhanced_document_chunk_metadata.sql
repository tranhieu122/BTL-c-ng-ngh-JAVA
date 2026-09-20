-- Flyway Migration V18: Bổ sung metadata phân cấp (Section, Subsection, Page Range, Source Type) cho Document Chunks
ALTER TABLE document_chunks
    ADD COLUMN section_title VARCHAR(255) NULL,
    ADD COLUMN subsection_title VARCHAR(255) NULL,
    ADD COLUMN start_page INT NULL,
    ADD COLUMN end_page INT NULL,
    ADD COLUMN source_type VARCHAR(50) NULL;

-- Index hỗ trợ lọc nhanh chunk theo section và page range khi truy vấn mở rộng
CREATE INDEX idx_chunks_doc_section ON document_chunks (document_id, section_title);
CREATE INDEX idx_chunks_doc_page_range ON document_chunks (document_id, start_page, end_page);
