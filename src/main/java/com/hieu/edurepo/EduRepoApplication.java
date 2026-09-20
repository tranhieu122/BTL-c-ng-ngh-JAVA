package com.hieu.edurepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Điểm khởi động chính của ứng dụng EduRepo.
 *
 * <p>EduRepo là hệ thống quản lý kho học liệu nội sinh tích hợp AI Chatbot
 * và pipeline RAG (Retrieval-Augmented Generation). Hệ thống cho phép:</p>
 * <ul>
 *   <li>Người dùng upload, quản lý và tìm kiếm tài liệu học thuật.</li>
 *   <li>Kiểm duyệt viên duyệt/từ chối tài liệu qua workflow có kiểm soát.</li>
 *   <li>AI Chatbot hỗ trợ tra cứu và trả lời câu hỏi dựa trên nội dung tài liệu.</li>
 * </ul>
 *
 * <p>Công nghệ: Spring Boot, Spring Security, Spring Data JPA, Thymeleaf, OpenAI API.</p>
 */
@SpringBootApplication
public class EduRepoApplication {

    /**
     * Phương thức main – khởi động Spring Boot application context.
     *
     * @param args Tham số dòng lệnh (truyền thẳng vào Spring Boot runner).
     */
    public static void main(String[] args) {
        SpringApplication.run(EduRepoApplication.class, args);
    }

}
