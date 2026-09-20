package com.hieu.edurepo.service;

import java.awt.image.BufferedImage;

/**
 * Giao diện trừu tượng hóa cho dịch vụ nhận dạng quang học (Optical Character Recognition - OCR).
 * Được kích hoạt khi PDF là dạng scan/hình ảnh hoặc các trang không có text layer.
 */
public interface OcrService {

    /**
     * Nhận dạng ký tự từ mảng byte ảnh (PNG, JPEG, TIFF...).
     *
     * @param imageBytes Dữ liệu byte của ảnh
     * @return Văn bản nhận dạng được, hoặc chuỗi rỗng nếu không có chữ / lỗi
     */
    String extractText(byte[] imageBytes);

    /**
     * Nhận dạng ký tự trực tiếp từ đối tượng BufferedImage của Java.
     *
     * @param image Ảnh render từ trang PDF
     * @return Văn bản nhận dạng được, hoặc chuỗi rỗng nếu không có chữ / lỗi
     */
    String extractText(BufferedImage image);

    /**
     * Kiểm tra xem engine OCR (ví dụ Tesseract) có đang khả dụng trong môi trường runtime hay không.
     *
     * @return true nếu OCR engine đã được cài đặt và cấu hình sẵn sàng; false nếu chưa có binary/tessdata
     */
    boolean isAvailable();

    /**
     * Mô tả chi tiết trạng thái hoặc hướng dẫn cấu hình nếu OCR chưa khả dụng.
     */
    String getStatusDescription();
}
