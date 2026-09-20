-- Citation metadata for RAG sources. Page number may be null for DOCX or
-- extractors that do not preserve page boundaries.
ALTER TABLE document_chunks
    ADD COLUMN page_number INT NULL;

-- Keep chunk citations deterministic and prevent duplicate chunk positions.
CREATE UNIQUE INDEX uk_document_chunks_document_index
    ON document_chunks (document_id, chunk_index);

-- Supports grouped visible-rating lookup for assistant ranking.
CREATE INDEX idx_document_reviews_visible_rating
    ON document_reviews (document_id, hidden, rating);
