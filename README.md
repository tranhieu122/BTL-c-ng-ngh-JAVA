# EduRepo

EduRepo là ứng dụng Spring Boot MVC quản lý, duyệt và công bố học liệu trực tuyến.

## Công nghệ

- Java 25+
- Spring Boot, Spring Data JPA, Spring Security
- Thymeleaf, MySQL
- Maven Wrapper

## Chạy ứng dụng

Database `document_management` sẽ được tự tạo nếu tài khoản MySQL có quyền tạo database.
Bạn vẫn có thể tạo thủ công nếu muốn:

```sql
CREATE DATABASE document_management CHARACTER SET utf8mb4;
```

Cách nhanh trên Windows:

```text
Double-click run-web.bat
```

Hoặc mở terminal trong thư mục project và chạy:

```powershell
.\run-web.bat
```

File này sẽ hỏi mật khẩu MySQL, khởi động Spring Boot và mở `http://localhost:8080`.
Nếu tài khoản `root` không có mật khẩu, có thể nhấn `Enter`.

Thiết lập biến môi trường trong PowerShell:

```powershell
$env:DB_PASSWORD="mat_khau_mysql"
$env:APP_ADMIN_EMAIL="admin@edurepo.local"
$env:APP_ADMIN_PASSWORD="mat_khau_admin_manh"
.\mvnw.cmd spring-boot:run
```

Truy cập `http://localhost:8080`. Lần chạy đầu, Hibernate tạo/cập nhật bảng và `DataInitializer` tạo bốn vai trò `ADMIN`, `REVIEWER`, `SUBMITTER`, `USER`.

Nếu log báo `Access denied for user 'root'@'localhost'`, hãy nhập lại đúng mật khẩu MySQL của tài khoản `root` hoặc đặt `DB_PASSWORD` trước khi chạy.

## Luồng chính

1. Submitter tạo bản nháp và tải PDF/DOC/DOCX.
2. Submitter gửi tài liệu để duyệt.
3. Reviewer phê duyệt, từ chối hoặc yêu cầu chỉnh sửa.
4. Reviewer/Admin công bố tài liệu đã phê duyệt.
5. Khách truy cập tìm kiếm và tải tài liệu đã công bố.
