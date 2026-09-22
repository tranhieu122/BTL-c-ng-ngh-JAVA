package com.hieu.edurepo.enums;

/**
 * Tên các vai trò phân quyền (RBAC) chính trong hệ thống EduRepo.
 */
public enum RoleName {
    /** Quản trị viên toàn quyền hệ thống: người dùng, danh mục, kiểm toán, cấu hình */
    ADMIN,

    /** Giảng viên kiểm duyệt: chấm điểm Rubric, yêu cầu sửa bài, duyệt xuất bản học liệu */
    REVIEWER,

    /** Tác giả đăng tải: sinh viên/giảng viên được cấp quyền tạo và nộp học liệu */
    SUBMITTER,

    /** Người dùng cơ bản: tìm kiếm, đọc trực tuyến, tải học liệu, hỏi đáp AI Chatbot */
    USER
}
