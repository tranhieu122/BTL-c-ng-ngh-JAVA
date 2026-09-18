ALTER TABLE approval_history ADD COLUMN old_status VARCHAR(30);
ALTER TABLE approval_history ADD COLUMN new_status VARCHAR(30);

UPDATE approval_history
SET old_status = CASE action
        WHEN 'APPROVED' THEN 'SUBMITTED'
        WHEN 'REJECTED' THEN 'SUBMITTED'
        WHEN 'REVISION_REQUESTED' THEN 'SUBMITTED'
        WHEN 'PUBLISHED' THEN 'APPROVED'
        ELSE NULL
    END,
    new_status = CASE action
        WHEN 'SUBMITTED' THEN 'SUBMITTED'
        WHEN 'APPROVED' THEN 'APPROVED'
        WHEN 'REJECTED' THEN 'REJECTED'
        WHEN 'REVISION_REQUESTED' THEN 'REVISION_REQUIRED'
        WHEN 'PUBLISHED' THEN 'PUBLISHED'
        ELSE NULL
    END;

ALTER TABLE document_versions ADD COLUMN current_version BIT NOT NULL DEFAULT 0;
UPDATE document_versions current_version_row
JOIN (
    SELECT document_id, MAX(version_number) AS latest_version
    FROM document_versions
    GROUP BY document_id
) latest
ON latest.document_id = current_version_row.document_id
AND latest.latest_version = current_version_row.version_number
SET current_version_row.current_version = 1;

CREATE INDEX idx_approval_history_document_created
    ON approval_history (document_id, created_at);
CREATE INDEX idx_document_versions_current
    ON document_versions (document_id, current_version);
CREATE INDEX idx_documents_owner_status
    ON documents (created_by, status, created_at);
CREATE INDEX idx_documents_review_queue
    ON documents (status, submitted_at);
