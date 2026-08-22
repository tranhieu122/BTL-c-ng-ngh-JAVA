package com.hieu.edurepo.controller;

import com.hieu.edurepo.dto.DocumentForm;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Faculty;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.repository.FacultyRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.CategoryService;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.service.ReviewService;
import com.hieu.edurepo.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.data.domain.PageImpl;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentControllerTest {

    @Test
    void myDocumentsUsesAuthenticatedUserId() {
        DocumentService documentService = mock(DocumentService.class);
        DocumentController controller = new DocumentController(
                documentService, mock(UserService.class), mock(CategoryService.class),
                mock(DepartmentRepository.class), mock(FacultyRepository.class), mock(FileStorageService.class), mock(ReviewService.class));
        User authenticatedUser = new User();
        authenticatedUser.setId(7L);
        authenticatedUser.setEmail("submitter@edurepo.local");
        authenticatedUser.setPassword("test-only");
        CustomUserPrincipal principal = CustomUserPrincipal.from(authenticatedUser);
        Model model = mock(Model.class);
        when(documentService.searchByOwner(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of()));

        String view = controller.myDocuments(principal, model, "", null, null, null, null, 0);

        assertEquals("documents/my-documents", view);
        verify(documentService).searchByOwner(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invalidDepartmentIsRejectedBeforeTheFileIsStored() {
        ControllerFixture fixture = new ControllerFixture();
        DocumentForm form = fixture.validForm();
        form.setDepartmentId(999L);
        when(fixture.departmentRepository.findById(999L)).thenReturn(Optional.empty());

        String view = fixture.controller.create(form, fixture.bindingResult(form), fixture.principal,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("documents/form", view);
        verify(fixture.fileStorageService, never()).store(form.getFile());
    }

    @Test
    void inactiveCategoryIsRejectedBeforeTheFileIsStored() {
        ControllerFixture fixture = new ControllerFixture();
        DocumentForm form = fixture.validForm();
        Category inactiveCategory = new Category();
        inactiveCategory.setId(1L);
        inactiveCategory.setName("Đã ngừng sử dụng");
        inactiveCategory.setActive(false);
        when(fixture.categoryService.findById(1L)).thenReturn(inactiveCategory);

        String view = fixture.controller.create(form, fixture.bindingResult(form), fixture.principal,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("documents/form", view);
        verify(fixture.fileStorageService, never()).store(form.getFile());
    }

    @Test
    void storageValidationErrorReturnsToTheUploadForm() {
        ControllerFixture fixture = new ControllerFixture();
        DocumentForm form = fixture.validForm();
        when(fixture.fileStorageService.store(form.getFile()))
                .thenThrow(new FileStorageException("Chỉ chấp nhận tệp PDF, DOC hoặc DOCX"));

        String view = fixture.controller.create(form, fixture.bindingResult(form), fixture.principal,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("documents/form", view);
    }

    @Test
    void failedDatabaseSaveRemovesTheStoredFile() {
        ControllerFixture fixture = new ControllerFixture();
        DocumentForm form = fixture.validForm();
        when(fixture.fileStorageService.store(form.getFile())).thenReturn("stored.pdf");
        when(fixture.documentService.submitNew(org.mockito.ArgumentMatchers.any(Document.class),
                org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new IllegalStateException("database failure"));

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.create(form, fixture.bindingResult(form), fixture.principal,
                        new ExtendedModelMap(), new RedirectAttributesModelMap()));

        verify(fixture.fileStorageService).delete("stored.pdf");
    }

    @Test
    void validUploadPersistsExpectedMetadataForAuthenticatedOwner() {
        ControllerFixture fixture = new ControllerFixture();
        DocumentForm form = fixture.validForm();
        form.setAuthorName("Nhóm tác giả");
        form.setDescription("Mô tả tài liệu");

        String view = fixture.controller.create(form, fixture.bindingResult(form), fixture.principal,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/documents", view);
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(fixture.documentService).submitNew(
                captor.capture(), org.mockito.ArgumentMatchers.same(fixture.authenticatedUser));
        Document document = captor.getValue();
        assertEquals("Tài liệu hợp lệ", document.getTitle());
        assertEquals("Nhóm tác giả", document.getAuthorName());
        assertEquals("document.pdf", document.getFileName());
        assertEquals("stored.pdf", document.getFilePath());
        assertEquals("application/pdf", document.getFileType());
        assertEquals(form.getFile().getSize(), document.getFileSize());
        assertEquals(1L, document.getCategory().getId());
    }

    @Test
    void revisionRequestedDocumentCanBeUpdatedWithoutReplacingItsFile() {
        ControllerFixture fixture = new ControllerFixture();
        Document current = fixture.editableDocument(DocumentStatus.REVISION_REQUIRED);
        when(fixture.documentService.findById(10L)).thenReturn(current);
        DocumentForm form = fixture.validForm();
        form.setTitle("Tiêu đề sau chỉnh sửa");
        form.setFile(null);

        String view = fixture.controller.update(10L, form, fixture.bindingResult(form), fixture.principal,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/documents/10", view);
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(fixture.documentService).updateDraft(
                org.mockito.ArgumentMatchers.eq(10L), captor.capture(),
                org.mockito.ArgumentMatchers.same(fixture.authenticatedUser));
        assertEquals("Tiêu đề sau chỉnh sửa", captor.getValue().getTitle());
        assertEquals(null, captor.getValue().getFilePath());
        verify(fixture.fileStorageService, never()).delete("old-file.pdf");
    }

    @Test
    void replacingAFileDeletesTheOldFileAfterTheDocumentIsUpdated() {
        ControllerFixture fixture = new ControllerFixture();
        Document current = fixture.editableDocument(DocumentStatus.DRAFT);
        when(fixture.documentService.findById(10L)).thenReturn(current);
        DocumentForm form = fixture.validForm();

        String view = fixture.controller.update(10L, form, fixture.bindingResult(form), fixture.principal,
                new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("redirect:/documents/10", view);
        verify(fixture.documentService).updateDraft(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any(Document.class),
                org.mockito.ArgumentMatchers.same(fixture.authenticatedUser));
        verify(fixture.fileStorageService).delete("old-file.pdf");
    }

    @Test
    void failedReplacementUpdateDeletesOnlyTheNewlyStoredFile() {
        ControllerFixture fixture = new ControllerFixture();
        Document current = fixture.editableDocument(DocumentStatus.DRAFT);
        when(fixture.documentService.findById(10L)).thenReturn(current);
        when(fixture.documentService.updateDraft(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any(Document.class),
                org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new IllegalStateException("database failure"));
        DocumentForm form = fixture.validForm();

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.update(10L, form, fixture.bindingResult(form), fixture.principal,
                        new ExtendedModelMap(), new RedirectAttributesModelMap()));

        verify(fixture.fileStorageService).delete("stored.pdf");
        verify(fixture.fileStorageService, never()).delete("old-file.pdf");
    }

    private static final class ControllerFixture {
        private final DocumentService documentService = mock(DocumentService.class);
        private final UserService userService = mock(UserService.class);
        private final CategoryService categoryService = mock(CategoryService.class);
        private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        private final FacultyRepository facultyRepository = mock(FacultyRepository.class);
        private final FileStorageService fileStorageService = mock(FileStorageService.class);
        private final ReviewService reviewService = mock(ReviewService.class);
        private final DocumentController controller = new DocumentController(
                documentService, userService, categoryService, departmentRepository, facultyRepository,
                fileStorageService, reviewService);
        private final User authenticatedUser = authenticatedUser();
        private final CustomUserPrincipal principal = CustomUserPrincipal.from(authenticatedUser);

        private ControllerFixture() {
            Category category = new Category();
            category.setId(1L);
            category.setName("Công nghệ");
            category.setActive(true);
            when(categoryService.findById(1L)).thenReturn(category);
            when(categoryService.findActive()).thenReturn(List.of(category));
            Faculty faculty = new Faculty();
            faculty.setId(2L);
            faculty.setName("Khoa Công nghệ");
            faculty.setActive(true);
            when(facultyRepository.findById(2L)).thenReturn(Optional.of(faculty));
            when(facultyRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(faculty));
            com.hieu.edurepo.entity.Department department = new com.hieu.edurepo.entity.Department();
            department.setId(3L);
            department.setName("Bộ môn Phần mềm");
            department.setFaculty(faculty);
            department.setActive(true);
            when(departmentRepository.findById(3L)).thenReturn(Optional.of(department));
            when(departmentRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());
            when(userService.findById(7L)).thenReturn(authenticatedUser);
            when(fileStorageService.store(org.mockito.ArgumentMatchers.any())).thenReturn("stored.pdf");
        }

        private DocumentForm validForm() {
            DocumentForm form = new DocumentForm();
            form.setTitle("Tài liệu hợp lệ");
            form.setCategoryId(1L);
            form.setFacultyId(2L);
            form.setDepartmentId(3L);
            form.setFile(new MockMultipartFile(
                    "file", "document.pdf", "application/pdf", "content".getBytes()));
            return form;
        }

        private BindingResult bindingResult(DocumentForm form) {
            return new BeanPropertyBindingResult(form, "documentForm");
        }

        private Document editableDocument(DocumentStatus status) {
            Document document = new Document();
            document.setId(10L);
            document.setCreatedBy(authenticatedUser);
            document.setStatus(status);
            document.setTitle("Tiêu đề cũ");
            document.setFileName("old-file.pdf");
            document.setFilePath("old-file.pdf");
            document.setFileType("application/pdf");
            document.setFileSize(10L);
            return document;
        }

        private static User authenticatedUser() {
            User user = new User();
            user.setId(7L);
            user.setEmail("submitter@edurepo.local");
            user.setPassword("test-only");
            return user;
        }
    }
}
