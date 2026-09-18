-- Một số database cũ được Hibernate tạo cột này dưới dạng ENUM MySQL.
-- START_REVIEW được bổ sung sau đó nên các database đó từ chối ghi lịch sử bắt đầu duyệt.
-- Dùng VARCHAR giống schema Flyway chuẩn để Java enum có thể phát triển mà không gây lỗi ghi dữ liệu.
ALTER TABLE approval_history
    MODIFY COLUMN action VARCHAR(30) NOT NULL;
