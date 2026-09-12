package com.hieu.edurepo.enums;

public enum EducationLevel {
    BEGINNER("Cơ bản"),
    INTERMEDIATE("Trung cấp"),
    ADVANCED("Nâng cao"),
    ALL_LEVELS("Mọi trình độ");

    private final String label;

    EducationLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
