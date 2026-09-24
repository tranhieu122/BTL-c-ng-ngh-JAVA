package com.hieu.edurepo.service;

import com.hieu.edurepo.service.impl.PdfHighlightServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class PdfHighlightTest {

    @Test
    void testHighlightPdfServiceOnSample() {
        PdfHighlightServiceImpl service = new PdfHighlightServiceImpl();
        File uploadsDir = new File("uploads");
        File[] pdfs = uploadsDir.listFiles((dir, name) -> name.endsWith(".pdf"));
        if (pdfs == null || pdfs.length == 0) return;

        File samplePdf = pdfs[0];
        FileSystemResource resource = new FileSystemResource(samplePdf);

        // Test with non-matching phrase returns null cleanly without error
        byte[] resultNull = service.highlight(resource, "NonExistentPhrase12345", 1);
        assertNull(resultNull);

        // Test candidate phrases builder
        var candidates = service.buildCandidatePhrases("Description: Embedded servlet container failed to start. Port 8080 was already in use.");
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().anyMatch(c -> c.contains("Embedded servlet container")));
    }
}
