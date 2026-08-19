package com.hieu.edurepo.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String store(MultipartFile file);
    Resource load(String storedFileName);
    void delete(String storedFileName);
}
