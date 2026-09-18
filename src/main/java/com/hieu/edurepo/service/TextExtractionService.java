package com.hieu.edurepo.service;

import java.io.InputStream;
import java.nio.file.Path;

public interface TextExtractionService {

    String extractText(Path filePath);

    String extractText(InputStream inputStream, String fileName);

    String cleanText(String rawText);
}
