package com.hieu.edurepo.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Điểm mở rộng (SPI) cho dịch vụ quét mã độc, virus và kiểm duyệt nội dung tệp tin tải lên.
 * <p>
 * Nguyên tắc thiết kế bảo mật: Bắt buộc tuân thủ cơ chế fail-closed (nếu xảy ra lỗi quét thì từ chối tệp để đảm bảo an toàn).
 * </p>
 */
@FunctionalInterface
public interface FileThreatScanner {

    /**
     * Kiểm tra xem tệp tin tạm vừa tải lên có an toàn trước các mối đe dọa an ninh mạng hay không.
     *
     * @param stagedFile Đường dẫn tệp tin tạm thời trên ổ đĩa
     * @param originalFileName Tên gốc của tệp tin do người dùng tải lên
     * @return true nếu tệp an toàn; false nếu phát hiện nguy cơ độc hại
     * @throws IOException Ngoại lệ đọc tệp tin
     */
    boolean isSafe(Path stagedFile, String originalFileName) throws IOException;
}
