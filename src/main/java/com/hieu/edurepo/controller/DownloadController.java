package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentVersion;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.FileStorageException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.FileStorageService;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.hieu.edurepo.service.PdfHighlightService;

import java.nio.charset.StandardCharsets;
@Controller
public class DownloadController {

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;
    private final com.hieu.edurepo.service.UserActivityService userActivityService;
    private final PdfHighlightService pdfHighlightService;

    public DownloadController(DocumentService documentService, FileStorageService fileStorageService) {
        this(documentService, fileStorageService, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DownloadController(DocumentService documentService, FileStorageService fileStorageService,
                              com.hieu.edurepo.service.AuditLogService auditLogs,
                              com.hieu.edurepo.service.UserActivityService userActivityService,
                              @org.springframework.beans.factory.annotation.Autowired(required = false) PdfHighlightService pdfHighlightService) {
        this.documentService = documentService;
        this.fileStorageService = fileStorageService;
        this.auditLogs = auditLogs;
        this.userActivityService = userActivityService;
        this.pdfHighlightService = pdfHighlightService;
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @AuthenticationPrincipal CustomUserPrincipal principal) {
        Document document = documentService.findById(id);
        // Endpoint công khai chỉ cho tải tài liệu đã xuất bản.
        // Tài liệu nháp/chờ duyệt trả 404 để không làm lộ sự tồn tại ra ngoài.
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        // Tạo response trước rồi mới tăng downloadCount để file thiếu/không đọc được không bị tính là tải thành công.
        ResponseEntity<Resource> response = createDownloadResponse(document);
        documentService.recordDownload(id);
        Long userId = principal == null ? null : principal.getId();
        userActivityService.recordDownload(userId, id);
        auditDownload(document, "Tải tài liệu công khai");
        return response;
    }

    ResponseEntity<Resource> download(Long id) {
        return download(id, null);
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<?> view(@PathVariable Long id,
                                  @RequestParam(value = "highlight", required = false) String highlight,
                                  @RequestParam(value = "search", required = false) String search,
                                  @RequestParam(value = "page", required = false) Integer page) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        String fileName = requireDocumentValue(document.getFileName(), "Thiếu tên tệp");
        Resource resource = fileStorageService.load(requireDocumentValue(document.getFilePath(), "Thiếu đường dẫn tệp"));
        MediaType contentType = MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ContentDisposition disposition = ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build();

        String phrase = (highlight != null && !highlight.isBlank()) ? highlight : search;
        if (pdfHighlightService != null && phrase != null && !phrase.isBlank() && fileName.toLowerCase().endsWith(".pdf")) {
            PdfHighlightService.HighlightResult result = pdfHighlightService.highlightWithResult(resource, phrase, page);
            if (result != null && result.pdfBytes() != null && result.pdfBytes().length > 0) {
                // Nếu trang yêu cầu khác trang thực tế chứa trích dẫn -> tự động chuyển hướng đến trang đúng trong viewer
                if (page == null || !page.equals(result.actualPage())) {
                    String cleanPhrase = java.net.URLEncoder.encode(phrase, StandardCharsets.UTF_8);
                    return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                            .header(HttpHeaders.LOCATION, "/view/" + id + "?highlight=" + cleanPhrase + "&page=" + result.actualPage() + "#page=" + result.actualPage())
                            .build();
                }

                ByteArrayResource byteResource = new ByteArrayResource(result.pdfBytes());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .cacheControl(CacheControl.noCache().mustRevalidate())
                        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                        .header("X-Pdf-Highlighted-Page", String.valueOf(result.actualPage()))
                        .body(byteResource);
            }
        }

        // Override Spring Security's default "no-store" Cache-Control so Edge/Chrome PDF
        // viewer can render the file inside an <iframe>. "private" prevents proxy caching;
        // "max-age=3600" gives the browser enough time to load and navigate the document.
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @GetMapping(value = "/view/{id}/locate", produces = MediaType.APPLICATION_JSON_VALUE)
    @org.springframework.web.bind.annotation.ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> locateSnippet(
            @PathVariable Long id,
            @RequestParam("phrase") String phrase,
            @RequestParam(value = "hintPage", required = false) Integer hintPage) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        String fileName = requireDocumentValue(document.getFileName(), "Thiếu tên tệp");
        Resource resource = fileStorageService.load(requireDocumentValue(document.getFilePath(), "Thiếu đường dẫn tệp"));

        Integer actualPage = (pdfHighlightService != null && fileName.toLowerCase().endsWith(".pdf"))
                ? pdfHighlightService.locatePage(resource, phrase, hintPage)
                : hintPage;

        return ResponseEntity.ok(java.util.Map.of("page", actualPage != null ? actualPage : 1));
    }

    @GetMapping("/reviews/{id}/download")
    public ResponseEntity<Resource> downloadForReview(@PathVariable Long id) {
        Document document = documentService.findById(id);
        // Reviewer chỉ tải bản đang chờ duyệt hoặc đã duyệt nhưng chưa công bố.
        if (document.getStatus() != DocumentStatus.SUBMITTED
                && document.getStatus() != DocumentStatus.RESUBMITTED
                && document.getStatus() != DocumentStatus.UNDER_REVIEW
                && document.getStatus() != DocumentStatus.APPROVED) {
            throw new ResourceNotFoundException("Tài liệu không nằm trong hàng chờ kiểm duyệt");
        }
        ResponseEntity<Resource> response = createDownloadResponse(document);
        auditDownload(document, "Tải tài liệu để kiểm duyệt");
        return response;
    }

    @GetMapping("/reviews/{id}/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadVersionForReview(@PathVariable Long id, @PathVariable Long versionId) {
        Document document = documentService.findById(id);
        if (document.getStatus() != DocumentStatus.SUBMITTED
                && document.getStatus() != DocumentStatus.RESUBMITTED
                && document.getStatus() != DocumentStatus.UNDER_REVIEW
                && document.getStatus() != DocumentStatus.APPROVED) {
            throw new ResourceNotFoundException("Tài liệu không nằm trong hàng chờ kiểm duyệt");
        }
        ResponseEntity<Resource> response = createVersionDownloadResponse(documentService.findVersion(id, versionId));
        auditDownload(document, "Tải phiên bản tài liệu để kiểm duyệt");
        return response;
    }

    @GetMapping("/documents/{id}/versions/{versionId}/download")
    public ResponseEntity<Resource> downloadOwnVersion(@PathVariable Long id, @PathVariable Long versionId,
                                                        @AuthenticationPrincipal CustomUserPrincipal principal) {
        Document document = documentService.findById(id);
        // Chủ tài liệu được tải phiên bản của mình; admin được tải để hỗ trợ quản trị/kiểm tra.
        boolean admin = principal != null && principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!admin && (principal == null || document.getCreatedBy() == null
                || !document.getCreatedBy().getId().equals(principal.getId()))) {
            throw new AccessDeniedException("Bạn không có quyền tải phiên bản này");
        }
        ResponseEntity<Resource> response = createVersionDownloadResponse(documentService.findVersion(id, versionId));
        auditDownload(document, "Tải phiên bản tài liệu cá nhân");
        return response;
    }

    private ResponseEntity<Resource> createDownloadResponse(Document document) {
        // Tên file gửi về dùng tên gốc, còn filePath là tên UUID nội bộ trên server.
        Resource resource = fileStorageService.load(requireDocumentValue(document.getFilePath(), "Thiếu đường dẫn tệp"));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(requireDocumentValue(document.getFileName(), "Thiếu tên tệp"), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private ResponseEntity<Resource> createVersionDownloadResponse(DocumentVersion version) {
        Resource resource = fileStorageService.load(requireDocumentValue(version.getFilePath(), "Thiếu đường dẫn tệp"));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(requireDocumentValue(version.getFileName(), "Thiếu tên tệp"), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(resource);
    }

    private String requireDocumentValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new FileStorageException(message);
        }
        return value;
    }

    private void auditDownload(Document document, String prefix) {
        if (auditLogs != null) {
            auditLogs.record(com.hieu.edurepo.enums.AuditAction.DOCUMENT_DOWNLOADED,
                    com.hieu.edurepo.enums.AuditTargetType.DOCUMENT, document.getId(), document.getTitle(),
                    prefix + ": " + document.getTitle(), com.hieu.edurepo.enums.AuditResult.SUCCESS);
        }
    }
}
