# EduRepo

EduRepo là ứng dụng Spring Boot MVC quản lý, duyệt và công bố học liệu trực tuyến.

Hồ sơ cá nhân, avatar, đổi mật khẩu, xóa tài khoản và SSE đã được bổ sung. Xem [chức năng, migration V4, kiểm thử và cấu hình](docs/PROFILE-REALTIME.md).

## Công nghệ

- JDK 25
- Spring Boot, Spring Data JPA, Spring Security
- Thymeleaf, MySQL
- Maven Wrapper

## Cấu trúc mã nguồn

```text
EduRepo/
|-- src/
|   |-- main/
|   |   |-- java/com/hieu/edurepo/
|   |   |   |-- EduRepoApplication.java   # Điểm khởi động ứng dụng
|   |   |   |-- config/                   # Security, dữ liệu mẫu và cấu hình môi trường
|   |   |   |-- controller/               # Tiếp nhận request và trả về view/JSON
|   |   |   |   `-- admin/                 # Các màn hình quản trị
|   |   |   |-- dto/                       # Dữ liệu form, request và response
|   |   |   |-- entity/                    # Thực thể JPA ánh xạ bảng dữ liệu
|   |   |   |-- enums/                     # Trạng thái, vai trò và loại dữ liệu
|   |   |   |-- exception/                 # Ngoại lệ và xử lý lỗi toàn cục
|   |   |   |-- observability/             # Metrics và mã theo dõi request
|   |   |   |-- repository/                # Truy vấn dữ liệu bằng Spring Data JPA
|   |   |   |-- security/                  # Đăng nhập, phiên và giới hạn đăng nhập sai
|   |   |   |-- service/                   # Interface xử lý nghiệp vụ
|   |   |   |   `-- impl/                  # Cài đặt nghiệp vụ
|   |   |   `-- util/                      # Kiểm tra file và chính sách mật khẩu
|   |   `-- resources/
|   |       |-- application.properties     # Cấu hình chung
|   |       |-- application-*.properties   # Cấu hình local, dev và prod
|   |       |-- db/migration/              # Migration Flyway từ V1 đến V13
|   |       |-- static/
|   |       |   |-- css/                    # Style chung, component và từng trang
|   |       |   |-- js/                     # JavaScript giao diện
|   |       |   |   `-- modules/            # Module realtime, form, thông báo...
|   |       |   `-- images/                 # Hình ảnh tĩnh
|   |       `-- templates/                 # View Thymeleaf theo từng chức năng
|   |           `-- fragments/             # Header, sidebar, footer và UI dùng chung
`-- README.md


Dự án tuân theo kiến trúc MVC nhiều tầng:

```text
Trình duyệt
    -> Controller
    -> Service
    -> Repository
    -> MySQL

Controller -> DTO/Entity -> Thymeleaf template -> HTML trả về trình duyệt
```

- **Controller** chỉ điều phối request, kiểm tra quyền truy cập và chọn view hoặc response.
- **Service** chứa quy tắc nghiệp vụ như nộp tài liệu, duyệt, xuất bản, OTP và thông báo.
- **Repository** chịu trách nhiệm đọc/ghi dữ liệu, không chứa xử lý giao diện.
- **Entity** biểu diễn dữ liệu lưu trong MySQL; **DTO** dùng để nhận form và trả dữ liệu an toàn.
- **Templates**, **CSS** và **JavaScript** tạo giao diện; các fragment và module dùng chung giúp tránh lặp mã.
- **Flyway migration** quản lý thay đổi cấu trúc database theo thứ tự phiên bản.

Dự án dùng cấu trúc mặc định của Maven và Spring Boot, không cấu hình thư mục nguồn riêng trong `pom.xml`.

## Chạy ứng dụng

Ứng dụng luôn dùng database MySQL bền vững `document_management`; ứng dụng không tự tạo database mới.
Chỉ tạo database một lần nếu máy chưa có database này:

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
$env:APP_USER_EMAIL="user@edurepo.local"
$env:APP_USER_PASSWORD="mat_khau_user_manh"
$env:MAIL_USERNAME="yourgmail@gmail.com"
$env:MAIL_PASSWORD="gmail_app_password"
.\mvnw.cmd spring-boot:run
```

Truy cập `http://localhost:8080`. Hibernate chỉ kiểm tra schema (`ddl-auto=validate`); Flyway quản lý thay đổi schema và không xóa bảng. `DataInitializer` chỉ bổ sung vai trò hoặc tài khoản chưa tồn tại.

Khi chạy bằng `run-web.bat`, tài khoản demo mặc định là `admin@edurepo.local / Admin@123456`
và `user@edurepo.local / User@123456`. Nếu tài khoản đã tồn tại, mật khẩu và dữ liệu của tài khoản được giữ nguyên.

`run-web.bat` cố định thư mục tải lên tại `<thư mục dự án>\uploads`. Khi chạy theo cách khác,
hãy đặt `UPLOAD_DIR` thành đường dẫn tuyệt đối. Xem [docs/OPERATIONS.md](docs/OPERATIONS.md) để cấu hình `local`, `dev` và `prod` cùng một kho dữ liệu.

Nếu log báo `Access denied for user 'root'@'localhost'`, hãy nhập lại đúng mật khẩu MySQL của tài khoản `root` hoặc đặt `DB_PASSWORD` trước khi chạy.

### Gửi OTP qua Gmail

Đăng ký và lấy lại mật khẩu dùng OTP gửi qua email. Với Gmail, hãy bật xác thực 2 bước và tạo **App Password**, rồi cấu hình `MAIL_USERNAME` và `MAIL_PASSWORD`. Không hardcode tài khoản Gmail hoặc mật khẩu vào mã nguồn.

Nếu chưa cấu hình SMTP ở môi trường phát triển, ứng dụng ghi cảnh báo và vẫn chạy, nhưng email OTP sẽ không được gửi thật.

## Luồng chính

1. Submitter tạo bản nháp và tải PDF/DOC/DOCX.
2. Submitter gửi tài liệu để duyệt.
3. Reviewer phê duyệt, từ chối hoặc yêu cầu chỉnh sửa.
4. Reviewer/Admin công bố tài liệu đã phê duyệt.
5. Khách truy cập tìm kiếm và tải tài liệu đã công bố.

## Các sửa lỗi và chức năng bổ sung

- Nút **Lưu bản nháp** lưu hồ sơ và tệp đã điền hợp lệ vào tài khoản, chưa gửi duyệt. Mở bản nháp trong **Tài liệu của tôi** để sửa hoặc gửi duyệt sau.
- Tự lưu trong trình duyệt giữ các trường thông tin theo tài khoản và tab; không lưu tệp đính kèm. Dữ liệu này được xóa sau khi server xác nhận tạo thành công hoặc khi đăng xuất.
- Đăng ký và quên mật khẩu xác thực bằng OTP qua email; mã OTP được hash trong database, có hạn dùng, giới hạn nhập sai và chống gửi lại quá nhanh.
- Danh mục có sửa/kích hoạt lại; bộ sưu tập có sửa tên/mô tả; trang người dùng có thao tác xóa và bảo vệ dữ liệu đang được tham chiếu.

Xem [kết quả sửa và kiểm thử](docs/FIXES-2026-09-05.md) và [hướng dẫn cập nhật/sao lưu](docs/OPERATIONS.md).
