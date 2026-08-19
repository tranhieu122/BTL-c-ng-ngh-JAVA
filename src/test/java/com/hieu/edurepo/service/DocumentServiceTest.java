package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    @Test
    void submitMovesDraftToSubmitted() {
        DocumentRepository repository = mock(DocumentRepository.class);
        DocumentService service = new DocumentServiceImpl(repository);
        User owner = new User();
        owner.setId(1L);
        Document document = new Document();
        document.setId(10L);
        document.setCreatedBy(owner);
        document.setStatus(DocumentStatus.DRAFT);
        when(repository.findById(10L)).thenReturn(Optional.of(document));
        when(repository.save(document)).thenReturn(document);

        Document result = service.submit(10L, owner);

        assertEquals(DocumentStatus.SUBMITTED, result.getStatus());
    }
}
