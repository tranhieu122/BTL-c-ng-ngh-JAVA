package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.CategoryService;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerTest {

    @Test
    void myDocumentsUsesAuthenticatedUserId() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentController controller = new DocumentController(
                documentService, mock(UserService.class), mock(CategoryService.class),
                mock(DepartmentRepository.class), mock(FileStorageService.class));
        User authenticatedUser = new User();
        authenticatedUser.setId(7L);
        authenticatedUser.setEmail("submitter@edurepo.local");
        authenticatedUser.setPassword("test-only");
        CustomUserPrincipal principal = CustomUserPrincipal.from(authenticatedUser);
        Model model = mock(Model.class);
        when(documentService.findByOwner(7L)).thenReturn(List.of());

        String view = controller.myDocuments(principal, model);

        assertEquals("documents/my-documents", view);
        verify(documentService).findByOwner(7L);
    }
}
