package com.hieu.edurepo.service;

import org.springframework.core.io.Resource;

/**
 * Dịch vụ bôi vàng (highlight) trực tiếp các đoạn trích dẫn vào nội dung file PDF.
 * Giúp người dùng khi xem tài liệu sẽ thấy đoạn trích dẫn được bôi vàng nổi bật ngay trong trang PDF.
 */
public interface PdfHighlightService {

    record HighlightResult(byte[] pdfBytes, int actualPage, boolean highlighted) {}

    /**
     * Bôi vàng đoạn văn bản trong tệp PDF và trả về kết quả gồm mảng byte và số trang thực tế tìm thấy.
     *
     * @param pdfResource Nguồn tệp PDF gốc
     * @param phrase      Đoạn trích dẫn hoặc từ khóa cần bôi vàng
     * @param targetPage  Trang mục tiêu dự kiến (1-based, có thể sai lệch hoặc null)
     * @return HighlightResult chứa bytes và actualPage, hoặc null nếu không tìm thấy
     */
    HighlightResult highlightWithResult(Resource pdfResource, String phrase, Integer targetPage);

    /**
     * Tìm số trang thực tế chứa đoạn văn bản trong tệp PDF.
     *
     * @param pdfResource Nguồn tệp PDF gốc
     * @param phrase      Đoạn trích dẫn cần tìm
     * @param hintPage    Trang gợi ý
     * @return Số trang 1-based thực tế, hoặc hintPage nếu không tìm thấy
     */
    Integer locatePage(Resource pdfResource, String phrase, Integer hintPage);

    /**
     * Bôi vàng đoạn văn bản trong tệp PDF và trả về mảng byte của PDF đã được bôi vàng.
     */
    default byte[] highlight(Resource pdfResource, String phrase, Integer targetPage) {
        HighlightResult res = highlightWithResult(pdfResource, phrase, targetPage);
        return res != null ? res.pdfBytes() : null;
    }
}
