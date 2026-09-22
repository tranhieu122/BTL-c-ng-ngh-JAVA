package com.hieu.edurepo.enums;

/**
 * Phân loại định dạng / mục đích của tài nguyên học tập trong EduRepo.
 */
public enum LearningResourceType {
    /** Đề cương, bài giảng tóm tắt của giảng viên bộ môn */
    LECTURE_NOTES("Bài giảng"),

    /** Sách giáo trình chính quy do nhà trường hoặc nhà xuất bản phát hành */
    TEXTBOOK("Giáo trình"),

    /** Slide bài trình chiếu thuyết trình bài học (.pptx, .pdf) */
    PRESENTATION("Bài trình chiếu"),

    /** Bài tập thực hành, đề thi mẫu, bài kiểm tra các năm */
    EXERCISE("Bài tập"),

    /** Báo cáo nghiên cứu khoa học sinh viên, kỷ yếu hội thảo */
    RESEARCH_PAPER("Bài nghiên cứu"),

    /** Tài liệu hướng dẫn đồ án, cài đặt môi trường, lab thực hành */
    GUIDE("Tài liệu hướng dẫn"),

    /** Các loại tài liệu học tập bổ trợ khác */
    OTHER("Khác");

    /** Nhãn hiển thị tiếng Việt trên giao diện phân loại và tìm kiếm */
    private final String label;

    LearningResourceType(String label) {
        this.label = label;
    }

    /**
     * Lấy tên phân loại tài nguyên học tập.
     * @return Chuỗi tiếng Việt phân loại học liệu
     */
    public String getLabel() {
        return label;
    }
}
