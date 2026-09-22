package com.hieu.edurepo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiểm thử tiện ích chuẩn hóa từ khóa tìm kiếm tiếng Việt (Search Text Normalizer Test).
 * Đảm bảo loại bỏ dấu câu, chuyển chữ thường và xử lý khoảng trắng thừa chính xác.
 */
class SearchTextNormalizerTest {

    @Test
    void foldsVietnameseDiacriticsAndD() {
        assertEquals("co so du lieu", SearchTextNormalizer.fold("Cơ sở dữ liệu"));
        assertEquals("dang van a", SearchTextNormalizer.fold("Đặng Văn A"));
    }
}
