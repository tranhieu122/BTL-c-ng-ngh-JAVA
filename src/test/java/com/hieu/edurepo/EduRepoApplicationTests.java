package com.hieu.edurepo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.unit.DataSize;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class EduRepoApplicationTests {

    @Autowired
    private MultipartProperties multipartProperties;

    @Test
    void contextLoads() {
    }

    @Test
    void uploadSettingsAreLoaded() {
        assertEquals(DataSize.ofMegabytes(200), multipartProperties.getMaxFileSize());
        assertEquals(DataSize.ofMegabytes(201), multipartProperties.getMaxRequestSize());
    }

}
