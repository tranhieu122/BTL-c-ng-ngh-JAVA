package com.hieu.edurepo.service;

import com.hieu.edurepo.service.impl.PdfHighlightServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class FindSnippetPageTest {



    @Test
    void testHighlightOnRealFile() throws Exception {
        File pdf = new File("uploads/64551b86-5980-4f09-bf01-cba2b559310b.pdf");
        if (!pdf.exists()) {
            pdf = new File("uploads/12edc74d-2b06-4971-9261-c39970386d91.pdf");
        }
        if (!pdf.exists()) return;

        PdfHighlightServiceImpl service = new PdfHighlightServiceImpl();
        String phrase = "Embedded servlet container failed to start. Port 8080 was already in use.";
        org.springframework.core.io.FileSystemResource res = new org.springframework.core.io.FileSystemResource(pdf);
        
        long start = System.currentTimeMillis();
        // Test locatePage
        Integer located = service.locatePage(res, phrase, 6);
        System.out.println("locatePage returned: " + located);
        org.junit.jupiter.api.Assertions.assertEquals(74, located);

        // Test second call cached
        long start2 = System.currentTimeMillis();
        var result2 = service.highlightWithResult(res, phrase, 6);
        long dur2 = System.currentTimeMillis() - start2;
        System.out.println("Cached highlight duration: " + dur2 + "ms");
        org.junit.jupiter.api.Assertions.assertNotNull(result2);
        org.junit.jupiter.api.Assertions.assertEquals(74, result2.actualPage());
    }
}
