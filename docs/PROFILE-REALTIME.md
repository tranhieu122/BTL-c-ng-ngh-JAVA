# Hồ sơ, tài khoản và realtime

## Nâng cấp frontend ngày 06/09/2026

- Tài liệu của tôi và hàng chờ lấy lại vùng HTML từ server khi snapshot thay đổi, không tải lại toàn trang. Giữ query bộ lọc/phân trang và ô tìm kiếm/lọc của reviewer; hỗ trợ danh sách thêm, bớt và chuyển từ trống sang có dữ liệu.
- Chi tiết tài liệu của người nộp tự đồng bộ nội dung và các nút sửa, xóa, gửi duyệt. Hoãn thay vùng đang có focus hoặc khi hộp xác nhận đang mở/thao tác đang gửi.
- Trang duyệt cập nhật lựa chọn quyết định theo trạng thái; giữ góp ý và điểm đang nhập. Khóa quyết định khi hồ sơ rời hàng chờ. Lịch sử trên trang duyệt vẫn cần tải lại để xem đầy đủ.
- Request HTML có timeout, thử lại khi lỗi và bỏ response từ vòng đời trang cũ. Dùng quyền truy cập và HTML/CSRF do server hiện tại cung cấp.
- Kiểm tra: 17 test JavaScript đạt (gồm 6 test mới); 11 test Java về SSE và render giao diện đạt. Chưa kiểm tra trực quan bằng trình duyệt trong lần nâng cấp này.

Lệnh JavaScript: `node --experimental-vm-modules --test scripts/frontend-regression.test.cjs scripts/profile-realtime.test.cjs scripts/realtime-regions.test.cjs`.

Mô tả và kết quả ngày 05/09 bên dưới là mốc triển khai ban đầu; hành vi danh sách/nút thao tác đã được nâng cấp như trên.

Đã triển khai trên Spring Boot MVC / Thymeleaf / Spring Security session và BCrypt hiện có. Không thêm dịch vụ bên ngoài hoặc chat.

## Chức năng

- Menu tài khoản có **Hồ sơ cá nhân** cho ADMIN, REVIEWER, SUBMITTER và USER. `/profile` lấy chủ tài khoản từ principal của phiên; chỉ nhận họ tên, điện thoại, đơn vị và giới thiệu. Email đăng nhập chỉ đọc, quản trị viên quản lý thay đổi email. ID, vai trò, trạng thái, mật khẩu và đường dẫn ảnh gửi kèm biểu mẫu bị bỏ qua.
- Avatar nhận PNG/JPEG tối đa 2 MB, chiều tối đa 4096 px và tối đa 16 triệu điểm ảnh. Frontend kiểm tra và xem trước; backend đọc nội dung ảnh, thu nhỏ tối đa 512 px, mã hóa lại PNG, loại metadata. Tệp nằm trong `UPLOAD_DIR/avatars/`, DB chỉ lưu khóa ảnh. Endpoint ảnh chỉ phục vụ ảnh của phiên hiện tại; khóa trên query string không chọn tài khoản/tệp. Thay/xóa ảnh dọn tệp cũ sau commit và chỉ xóa tệp mang đúng ID chủ sở hữu; rollback dọn ảnh mới.
- Đổi mật khẩu yêu cầu mật khẩu hiện tại, mật khẩu mới đúng chính sách BCrypt (ít nhất 8 ký tự, tối đa 72 byte UTF-8), xác nhận khớp và khác mật khẩu cũ. Phiên hiện tại đăng xuất ngay; phiên khác bị từ chối ở request kế tiếp, SSE bị đóng ở lần kiểm tra kế tiếp.
- Xóa tài khoản cần nhập lại mật khẩu, chuỗi **XÓA TÀI KHOẢN** và checkbox xác nhận. Xóa hồ sơ/ảnh/dấu trang/bộ sưu tập riêng; vô hiệu hóa và ẩn danh dòng user để giữ khóa ngoại tài liệu, phiên bản và lịch sử duyệt. Metadata tác giả/nội dung đã nộp vẫn là dữ liệu nghiệp vụ, không được tự động viết lại. Chặn xóa admin hoạt động cuối cùng. Tài khoản bootstrap đã xóa không tự xuất hiện lại khi khởi động.
- Giao diện có layout một cột trên màn hình nhỏ, thông báo thành công/lỗi, trạng thái đang lưu và bảo vệ nội dung đang nhập khi có cập nhật từ phiên khác.

## Các luồng realtime đã chọn

1. Hồ sơ, tên hiển thị và avatar giữa các phiên của cùng tài khoản.
2. Danh sách trạng thái tài liệu do chính tài khoản nộp; cập nhật nhãn trạng thái trên trang danh sách/chi tiết và bảng **Cập nhật** trên thanh điều hướng.
3. Số hồ sơ chờ duyệt/chờ xuất bản cho REVIEWER/ADMIN, gồm các chỉ số trên trang hàng chờ. Khi dữ liệu thay đổi, thông báo có liên kết tải lại danh sách và thao tác; không thay thế biểu mẫu người dùng đang nhập.

`GET /events/stream` là SSE đã xác thực bằng session cùng origin, CSRF vẫn áp dụng cho POST. Mỗi kết nối kiểm tra tài khoản, mật khẩu, email, vai trò và thời hạn session trước khi gửi snapshot từ DB. Người dùng thường không nhận hàng chờ hoặc dữ liệu của người khác. Không phát toàn bộ entity hay hash mật khẩu. API `/events/snapshot` hỗ trợ đồng bộ và phân biệt hết phiên với lỗi mạng.

Server kiểm tra thay đổi mặc định mỗi 2 giây, heartbeat 15 giây, đóng định kỳ khoảng 55 giây. Client nối lại với thời gian chờ tăng dần (tối đa 30 giây), nhận snapshot đầy đủ ngay sau nối lại; không phụ thuộc lịch sử sự kiện trong RAM. Chặn listener/kết nối trùng, bỏ sự kiện đến muộn, dọn khi `pagehide`/offline, kết nối lại khi `pageshow`/online. Đây là đồng bộ trạng thái hiện tại, không phải hộp thư lưu mọi chuyển trạng thái trung gian. Mỗi tài khoản tối đa 5 kết nối SSE; toàn instance tối đa 500. Chức năng HTTP thông thường hoạt động độc lập khi SSE gián đoạn.

## Migration và cách chạy

`src/main/resources/db/migration/V4__personal_profiles.sql` thêm các cột nullable `phone_number`, `affiliation`, `bio`, `avatar_key`, `deleted_at`, `bootstrap_key`. Không drop bảng hoặc reset dữ liệu. Flyway tự áp dụng khi khởi động theo cấu hình sẵn có; không chạy SQL này thủ công lần thứ hai. Sao lưu database và toàn bộ thư mục uploads trước khi nâng cấp như hướng dẫn `OPERATIONS.md`.

Từ thư mục `EduRepo`:

```powershell
.\mvnw.cmd -o "-Dmaven.repo.local=.m2/repository" -DskipTests package
.\run-web.bat
```

Hoặc dùng JDK 25 và cấu hình MySQL hiện có để chạy `java -jar target/EduRepo-1.0-SNAPSHOT.jar`. Giữ `UPLOAD_DIR` cố định và có quyền đọc/ghi, bao gồm thư mục con `avatars`. Các biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, profile `dev`/`prod` giữ cách dùng hiện tại. Không cần secret, broker hay dependency mới. Tùy chọn `REALTIME_INTERVAL_MS` mặc định `2000` điều chỉnh chu kỳ kiểm tra; không cần đặt để chạy.

Nếu có reverse proxy, tắt buffering/cache cho `/events/stream`, cho phép kết nối ít nhất 65 giây và chuyển tiếp cookie session. Server gửi `X-Accel-Buffering: no`. Khi chạy nhiều instance, cần session affinity hoặc kho session chung như yêu cầu của đăng nhập session hiện tại; uploads cũng phải dùng nơi lưu chung. Chưa kiểm thử triển khai nhiều instance hoặc tải lớn.

## Kiểm tra thực tế ngày 05/09/2026

- Toàn bộ **85 test Java đạt**. Bao gồm đăng nhập/đăng xuất, đủ bốn vai trò, form validation, chặn CSRF, URL tài khoản khác, mass assignment, mật khẩu sai/không khớp/quá dài và thu hồi hai phiên, avatar thật/giả/quá dung lượng, xóa ảnh, xóa tài khoản thử có tài liệu/phiên bản/lịch sử/dấu trang/bộ sưu tập, và chặn khôi phục tài khoản đã xóa bằng form admin cũ.
- SSE chạy qua HTTP trên embedded Tomcat với **ba cookie session độc lập**: người nộp A, người nộp B, reviewer. Đã kiểm tra cập nhật hồ sơ qua POST thật, quyết định duyệt qua POST thật, dữ liệu gửi đúng người, B không nhận tài liệu riêng của A, reconnect nhận thay đổi lúc offline, đổi mật khẩu/đăng xuất đóng stream.
- Kiểm tra cuối chạy lại **11 test Java liên quan đạt**, bổ sung JPEG hợp lệ, ảnh quá giới hạn chiều và thu hồi vai trò reviewer khi SSE đang mở. Đóng gói `target/EduRepo-1.0-SNAPSHOT.jar` thành công.
- **11 test JavaScript đạt**: kiểm tra file, lifecycle SSE, đồng bộ qua HTTP/reconnect, hết phiên, dừng trong khi fetch đang chờ và các hồi quy frontend cũ. Đây là test module thật với DOM/browser primitives giả lập, không thay cho kiểm thử trình duyệt.
- **MySQL 8.0.46 riêng: 1 test tích hợp đạt**. Chạy Flyway tới V3, chèn tài khoản/tài liệu thử, nâng lên V4, chạy Hibernate `validate`, sửa hồ sơ/avatar, khởi động lại, xóa tài khoản thử và khởi động lại lần nữa. Dữ liệu nghiệp vụ giữ nguyên, ảnh/hồ sơ bền vững trước xóa, tài khoản đã xóa không trở lại. Instance thử được tắt sau test; không chạy migration hay xóa tài khoản trên DB thật.
- Kiểm tra log backend của luồng mới, sửa lỗi response khi ngắt SSE và lỗi xử lý route không tồn tại. Render Thymeleaf nằm trong test tích hợp. **Chưa kiểm tra trực quan desktop/mobile, console trình duyệt, proxy thực hoặc triển khai thật** vì công cụ không có trình duyệt kết nối. Không khẳng định dự án không còn lỗi.

Lệnh tái kiểm tra:

```powershell
.\mvnw.cmd -o "-Dmaven.repo.local=.m2/repository" test
node --experimental-vm-modules --test scripts/frontend-regression.test.cjs scripts/profile-realtime.test.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-profile-mysql.ps1
```

Script MySQL yêu cầu MySQL Server cục bộ, mặc định `C:\Program Files\MySQL\MySQL Server 8.0`. Có thể truyền `-MysqlHome` và `-Port`. Nó tạo datadir ngẫu nhiên dưới `target`, kiểm tra PID/datadir của listener, dùng cổng riêng 13367, không đọc cấu hình MySQL máy và tự tắt instance thử. Log giữ tại `target/mysql-profile-*/test.log`. Test MySQL mặc định được bỏ qua trong `mvn test` nếu không chạy script này. Bỏ `-o` trên máy chưa có dependency cache.

## Tệp chính

- Backend: `ProfileController`, `AccountModelAdvice`, `ProfileService`, `AvatarStorageService`, `ProfileForm`, `ProfileView`, `User` và các repository liên quan.
- Realtime: `RealtimeController`, `RealtimeService`, `RealtimeSnapshotService`, `LiveDocument`, `SecurityConfig`, `AccountSessionFilter`.
- Giao diện: `templates/profile/index.html`, `fragments/header.html`, `auth/login.html`, các nhãn trạng thái tài liệu/hàng chờ; `profile.js`, `realtime.js`, `profile.css`, các entry point CSS/JS.
- Tính nhất quán: `UserServiceImpl` giữ các trường hồ sơ mới khi admin lưu form cũ; `DataInitializer` giữ dấu bootstrap sau khi xóa tài khoản; `GlobalExceptionHandler` trả 404 cho route không tồn tại.
- Kiểm thử: `ProfileRealtimeIntegrationTest`, `PersistenceRestartIntegrationTest`, `MysqlProfileMigrationTest`, `scripts/profile-realtime.test.cjs`, `scripts/test-profile-mysql.ps1`.
