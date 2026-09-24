package com.hieu.edurepo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO đại diện cho thông tin chi tiết của một nguồn trích dẫn / chunk cụ thể trong cơ sở dữ liệu.
 * Phục vụ popup xem trước trích dẫn (Citation Preview) và điều hướng đúng trang PDF khi người dùng click [1], [2], [4]...
 *
 * @param citationIndex Số thứ tự của citation trong câu trả lời (1, 2, 3...)
 * @param documentId ID của tài liệu trong bảng documents
 * @param chunkId ID định danh của chunk cụ thể trong bảng document_chunks
 * @param chunkIndex Vị trí thứ tự của chunk trong tài liệu gốc (bắt đầu từ 0)
 * @param pageNumber Trang trong tệp PDF chứa đoạn trích dẫn này
 * @param title Tên tài liệu
 * @param sectionTitle Tiêu đề chương / mục chứa đoạn trích dẫn (nếu có)
 * @param content Nội dung văn bản thực tế của chunk
 * @param snippet Đoạn trích dẫn tóm tắt (khoảng 300-500 ký tự)
 * @param detailUrl Đường dẫn trực tiếp tới trang tài liệu kèm tham số nhảy đúng trang
 */
public record CitationDetailDto(
        @JsonProperty("citationIndex") Integer citationIndex,
        @JsonProperty("documentId") Long documentId,
        @JsonProperty("chunkId") Long chunkId,
        @JsonProperty("chunkIndex") Integer chunkIndex,
        @JsonProperty("pageNumber") Integer pageNumber,
        @JsonProperty("title") String title,
        @JsonProperty("sectionTitle") String sectionTitle,
        @JsonProperty("content") String content,
        @JsonProperty("snippet") String snippet,
        @JsonProperty("detailUrl") String detailUrl
) {}
