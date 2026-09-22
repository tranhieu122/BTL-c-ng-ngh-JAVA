package com.hieu.edurepo.enums;

/**
 * Cấp độ / Trình độ kiến thức phù hợp của học liệu đào tạo.
 */
public enum EducationLevel {
    /** Phù hợp cho sinh viên mới nhập môn, kiến thức nền tảng cơ sở */
    BEGINNER("Cơ bản"),

    /** Dành cho sinh viên đã có kiến thức cơ sở, đào tạo chuyên ngành */
    INTERMEDIATE("Trung cấp"),

    /** Chuyên sâu, nghiên cứu khoa học, đồ án tốt nghiệp hoặc cao học */
    ADVANCED("Nâng cao"),

    /** Phù hợp cho mọi đối tượng sinh viên và người học tham khảo */
    ALL_LEVELS("Mọi trình độ");

    /** Nhãn tiếng Việt hiển thị trên giao diện bộ lọc và thẻ tài liệu */
    private final String label;

    EducationLevel(String label) {
        this.label = label;
    }

    /**
     * Lấy nhãn hiển thị trình độ tiếng Việt.
     * @return Tên trình độ đào tạo
     */
    public String getLabel() {
        return label;
    }
}
