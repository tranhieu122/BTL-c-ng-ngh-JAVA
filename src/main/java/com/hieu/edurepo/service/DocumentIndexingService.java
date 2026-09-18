package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;

public interface DocumentIndexingService {

    void indexDocument(Document document);

    void indexDocument(Long documentId);

    void removeIndex(Long documentId);

    int reindexAllPublishedDocuments();
}
