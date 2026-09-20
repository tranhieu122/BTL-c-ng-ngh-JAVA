package com.hieu.edurepo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchTextNormalizerTest {

    @Test
    void foldsVietnameseDiacriticsAndD() {
        assertEquals("co so du lieu", SearchTextNormalizer.fold("Cơ sở dữ liệu"));
        assertEquals("dang van a", SearchTextNormalizer.fold("Đặng Văn A"));
    }
}
