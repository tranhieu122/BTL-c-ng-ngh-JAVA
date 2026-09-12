package com.hieu.edurepo.enums;

public enum LearningResourceType {
    LECTURE_NOTES("Bài giảng"),
    TEXTBOOK("Giáo trình"),
    PRESENTATION("Bài trình chiếu"),
    EXERCISE("Bài tập"),
    RESEARCH_PAPER("Bài nghiên cứu"),
    GUIDE("Tài liệu hướng dẫn"),
    OTHER("Khác");

    private final String label;

    LearningResourceType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
