# EduRepo: vận hành, migration và sao lưu

## Cấu hình môi trường

Mặc định dự án chạy profile `dev`. Cả `local` và `dev` đều trỏ tới MySQL `document_management`; đổi giữa hai profile không đổi kho dữ liệu. Khi triển khai, đặt `SPRING_PROFILES_ACTIVE=prod` cùng các biến `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và `UPLOAD_DIR`, trong đó `DB_URL` vẫn phải trỏ tới database bền vững đã chọn.

`run-web.bat` đặt `UPLOAD_DIR` thành đường dẫn tuyệt đối tới thư mục `uploads` cạnh file chạy. Khi chạy bằng Maven/IDE, nên đặt `UPLOAD_DIR` tuyệt đối; nếu không, ứng dụng dùng `${user.home}/.edurepo/uploads`, không phụ thuộc thư mục hiện hành.

Profile production không tạo tài khoản demo. `DataInitializer` chỉ tạo tài khoản khi cả email/username chưa tồn tại; nó không đổi mật khẩu, vai trò, trạng thái hay hồ sơ của tài khoản cũ.

### Biến production

Không lưu giá trị thật của secret trong repository, file `.properties`, script hoặc workflow CI. Các biến sau được đọc từ môi trường triển khai:

| Biến | Mục đích | Mặc định | Phạm vi | Bắt buộc ở production |
|---|---|---:|---|---|
| `SPRING_PROFILES_ACTIVE` | chọn cấu hình triển khai | `dev` | runtime | đặt thành `prod` |
| `DB_URL` | JDBC URL MySQL | không có | prod | có |
| `DB_USERNAME` | tài khoản MySQL | không có | prod | có |
| `DB_PASSWORD` | mật khẩu MySQL | không có | prod | có |
| `DB_CONNECTION_TIMEOUT_MS` | thời gian tối đa chờ lấy connection | `5000` | prod | không |
| `DB_VALIDATION_TIMEOUT_MS` | timeout kiểm tra connection | `3000` | prod | không |
| `DB_MAX_POOL_SIZE` | số connection tối đa mỗi instance | `10` | prod | không |
| `DB_MIN_IDLE` | số connection idle tối thiểu | `2` | prod | không |
| `DB_IDLE_TIMEOUT_MS` | thời gian connection idle trước khi thu hồi | `600000` | prod | không |
| `DB_MAX_LIFETIME_MS` | vòng đời tối đa của connection | `1500000` | prod | không |
| `UPLOAD_DIR` | thư mục bền vững chứa học liệu | dev: `${user.home}/.edurepo/uploads`; prod: không có | mọi profile | có |
| `UPLOAD_MAX_FILE_SIZE_BYTES` | giới hạn byte được service kiểm tra | `209715200` | mọi profile | không |
| `OTP_HASH_PEPPER` | pepper để hash OTP, tối thiểu 32 ký tự ngẫu nhiên | dev/test có giá trị chỉ dùng local; prod: không có | mọi profile | có |
| `OTP_TTL_MINUTES` | tuổi thọ OTP | `10` | mọi profile | không |
| `OTP_RESEND_COOLDOWN_SECONDS` | khoảng chờ gửi lại OTP | `60` | mọi profile | không |
| `OTP_MAX_ATTEMPTS` | số lần nhập sai OTP tối đa | `5` | mọi profile | không |
| `MAIL_HOST` | SMTP host | dev: `smtp.gmail.com`; prod: không có | mọi profile | có |
| `MAIL_PORT` | SMTP port | `587` | mọi profile | không |
| `MAIL_SMTP_AUTH` | bật SMTP authentication | `true` | mọi profile | không |
| `MAIL_USERNAME` | SMTP username/địa chỉ gửi | không có | mọi profile | có khi `MAIL_SMTP_AUTH=true` |
| `MAIL_PASSWORD` | SMTP password/app password | không có | mọi profile | có khi `MAIL_SMTP_AUTH=true` |
| `MAIL_SMTP_STARTTLS` | bật STARTTLS | `true` | mọi profile | không |
| `MAIL_SMTP_STARTTLS_REQUIRED` | bắt buộc STARTTLS ở production | `true` | prod | không |
| `MAIL_SMTP_CONNECTION_TIMEOUT_MS` | timeout mở kết nối SMTP | `5000` | mọi profile | không |
| `MAIL_SMTP_READ_TIMEOUT_MS` | timeout đọc SMTP | `5000` | mọi profile | không |
| `MAIL_SMTP_WRITE_TIMEOUT_MS` | timeout ghi SMTP | `5000` | mọi profile | không |
| `APP_ADMIN_EMAIL` | tạo admin lần đầu | không có ở prod | dev/prod | không; phải đi cùng password |
| `APP_ADMIN_PASSWORD` | password admin bootstrap | không có ở prod | dev/prod | không; nếu đặt phải đạt policy |
| `APP_TIME_ZONE` | múi giờ hiển thị phía server | `Asia/Ho_Chi_Minh` | mọi profile | không |
| `APP_TIME_DISPLAY_FORMAT` | định dạng thời gian hiển thị | `dd/MM/yyyy HH:mm` | mọi profile | không |
| `REALTIME_INTERVAL_MS` | chu kỳ kiểm tra SSE khi đang hoạt động | `2000` | mọi profile | không |
| `REALTIME_SAFETY_REFRESH_MS` | chu kỳ snapshot dự phòng | `30000` | mọi profile | không |
| `LOGIN_MAX_FAILURES` | số lần sai trước khi khóa tạm | `5` | mọi profile | không |
| `LOGIN_LOCK_MINUTES` | thời gian khóa đăng nhập tạm | `15` | mọi profile | không |
| `SESSION_TIMEOUT` | thời gian session không hoạt động | `30m` | mọi profile | không |
| `SHUTDOWN_TIMEOUT` | thời gian chờ graceful shutdown | `30s` | prod | không |

Ứng dụng production dừng startup với thông báo chỉ nêu **tên biến bị thiếu**, không in giá trị, nếu thiếu DB, upload directory, OTP pepper hoặc cấu hình SMTP bắt buộc. Không đặt `APP_ADMIN_PASSWORD` nếu không cần bootstrap; tuyệt đối không dùng password mặc định của profile dev.

Các tham số vận hành có thể override gồm `DB_CONNECTION_TIMEOUT_MS=5000`, `DB_VALIDATION_TIMEOUT_MS=3000`, `DB_MAX_POOL_SIZE=10`, `DB_MIN_IDLE=2`, `DB_IDLE_TIMEOUT_MS=600000`, `DB_MAX_LIFETIME_MS=1500000`, `MAIL_SMTP_CONNECTION_TIMEOUT_MS=5000`, `MAIL_SMTP_READ_TIMEOUT_MS=5000` và `MAIL_SMTP_WRITE_TIMEOUT_MS=5000`. Pool 10 connection chỉ là mặc định bảo thủ cho monolith nhỏ/trung bình, không phải giá trị tối ưu. Cần tuning lại theo CPU, số instance, giới hạn connection MySQL, traffic, thời gian giữ connection và kết quả load test. `DB_MAX_LIFETIME_MS` phải thấp hơn timeout của MySQL/proxy.

## Flyway

Tất cả thay đổi cấu trúc MySQL nằm trong `src/main/resources/db/migration`. Không sửa migration đã từng chạy; thay vào đó hãy thêm một file có version tiếp theo, ví dụ `V2__add_document_index.sql`.

Với database cũ đã được Hibernate tạo trước đây, profile `dev` sẽ baseline ở version 1, nghĩa là Flyway coi schema hiện tại tương ứng với `V1__initial_schema.sql` và không chạy lại file đó. Trước lần chạy đầu tiên, bắt buộc sao lưu database và `uploads`. Production không tự baseline để tránh vô tình chấp nhận một schema không rõ nguồn gốc.

## Sao lưu

Chạy PowerShell từ thư mục dự án:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-edurepo.ps1 -UploadDir "$PWD\uploads"
```

Script yêu cầu `-UploadDir` hoặc biến `UPLOAD_DIR` trỏ đúng thư mục ứng dụng đang sử dụng. Nó tạo backup gồm SQL MySQL, các tệp học liệu và `files.json` chứa đường dẫn, dung lượng, SHA-256 đã kiểm chứng. Script không báo hoàn tất nếu chép hoặc kiểm tra tệp thất bại. Cần lưu cả SQL và học liệu; database không chứa file PDF/DOCX thực tế.

Ví dụ cho ứng dụng chạy bằng `run-web.bat`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\backup-edurepo.ps1 -UploadDir "$PWD\uploads"
```

Nếu chạy Maven/IDE mà chưa đặt UPLOAD_DIR, thư mục mặc định là `$env:USERPROFILE\.edurepo\uploads`; cần truyền đúng đường dẫn đó. Dừng ứng dụng trước khi backup để SQL và tệp thuộc cùng một thời điểm. Bản sao lưu cần được thử khôi phục trên database riêng trước khi dùng làm phương án khôi phục chính.

Để khôi phục: dừng ứng dụng, import file `.sql` bằng MySQL, chép thư mục `uploads` trở lại đúng `UPLOAD_DIR`, rồi khởi động ứng dụng và mở thử một tài liệu.

## Health check

Các endpoint công khai chỉ trả trạng thái, không trả component/detail/path/exception:

- `GET /actuator/health`: trạng thái tổng hợp, gồm database và dung lượng đĩa của đúng `UPLOAD_DIR`.
- `GET /actuator/health/liveness`: chỉ phản ánh trạng thái process; không phụ thuộc DB, storage hay SMTP để tránh vòng lặp restart khi dependency lỗi tạm thời.
- `GET /actuator/health/readiness`: phản ánh application readiness, database và storage disk-space.

`/actuator/info`, `/actuator/prometheus`, health component path và mọi endpoint Actuator khác yêu cầu role `ADMIN`. Admin có thể xem health component/detail ở health tổng hợp; liveness/readiness vẫn tối giản. Không expose `/env`, `/configprops`, heap dump hoặc endpoint nhạy cảm khác.

Không có SMTP health probe: probe kết nối thường xuyên vừa làm chậm health vừa có nguy cơ bị provider rate-limit, còn gửi thư thử sẽ có side effect. Thay vào đó theo dõi counter kết quả gửi OTP, timeout và timer gửi mail. Health check không thay thế alerting.

## Metrics và logging

`/actuator/prometheus` là điểm scrape được bảo vệ, chưa phải một Prometheus server. Các metric JVM, HTTP server, HikariCP và datasource do Spring Boot/Micrometer cung cấp được giữ nguyên. Metric ứng dụng có prefix:

- `edurepo.otp.*` và `edurepo.smtp.timeouts` cho phát hành OTP, kết quả/thời gian gửi và revoke do delivery lỗi;
- `edurepo.upload.*` cho kết quả upload, byte, validation/scanner duration, scanner result và storage error;
- `edurepo.realtime.*` cho connection active/open/reject/close, snapshot query/skip/duration, event và safety refresh.

Tag chỉ chứa enum hữu hạn như `purpose`, `result`, `failure_type`, `reason`; không chứa email, user ID, OTP, session ID, IP, filename, path hoặc exception message. HTTP metric mặc định dùng route template/status thay vì ID động làm tag.

Mỗi response có `X-Request-ID` do ứng dụng tự sinh và log cùng request qua MDC. Ứng dụng không chấp nhận request ID do client gửi; MDC luôn được xóa khi servlet dispatch kết thúc, kể cả request async/SSE. Production log không bật SQL/bind parameter. Lỗi bất ngờ có stack trace trong server log để điều tra nhưng response chỉ có thông báo an toàn. Khi triển khai thật vẫn cần chuyển stdout/stderr tới hệ thống log tập trung với retention và kiểm soát truy cập phù hợp.

## SMTP

Gửi OTP hiện vẫn đồng bộ trong HTTP request và không retry vô hạn. Với mặc định connection/read/write timeout đều 5 giây, request vẫn phụ thuộc một phần vào mail provider và có thể chậm theo các phase SMTP (tổng thời gian xấu nhất có thể lớn hơn một timeout đơn lẻ). Nếu tải tăng, hướng nâng cấp hợp lý là transactional outbox và worker nền; chưa thêm queue/message broker trong phạm vi hiện tại.

## Reverse proxy, HTTPS và SSE

Profile `prod` dùng `server.forward-headers-strategy=framework` và giả định ứng dụng chỉ được truy cập qua reverse proxy/load balancer tin cậy. Proxy phải **xóa** header `Forwarded`/`X-Forwarded-*` do client gửi rồi tự đặt giá trị đúng; không expose trực tiếp cổng ứng dụng ra Internet. TLS nên terminate tại proxy, không hard-code domain và không cần cấu hình keystore trong Spring Boot cho topology này.

Cookie production có `Secure`, `HttpOnly`, `SameSite=Lax`; dev/test giữ `Secure=false` để chạy HTTP local. Session chỉ truyền bằng cookie, CSRF vẫn bật, logout invalidate session và xóa `JSESSIONID`.

Endpoint SSE trả `X-Accel-Buffering: no` và `Cache-Control: no-store`. Với Nginx cần tắt proxy buffering cho `/events/`, dùng HTTP/1.1 và đặt read timeout lớn hơn vòng đời stream 60 giây (ví dụ 75 giây). Graceful shutdown ngừng nhận request mới, chờ tối đa `SHUTDOWN_TIMEOUT`, đóng scheduler và emitter; file upload dùng staging, cleanup khi lỗi và dọn staging còn sót ở lần startup kế tiếp.

## Giới hạn kiến trúc hiện tại

- Local filesystem chỉ phù hợp một application instance hoặc volume dùng chung có semantics phù hợp; không cung cấp replication/object durability.
- Realtime revision/event nằm trong process nên nhiều instance không đồng bộ tức thời. Có thể cân nhắc Redis Pub/Sub hoặc message broker khi thực sự chạy multi-instance, nhưng chưa triển khai.
- Endpoint metrics mới chỉ xuất dữ liệu; chưa có Prometheus server, dashboard hoặc alert rule.
- Log chưa có collector tập trung; health check cũng không thay thế monitoring/alerting.
- Scanner mặc định là extension point no-op. Production cần cung cấp implementation antivirus fail-closed nếu chính sách an toàn yêu cầu quét malware.

## Bản sửa 05/09/2026

- Flyway tự áp dụng `V3__password_reset_requests.sql` khi khởi động: thêm cột nullable `users.password_reset_requested_at`, không xóa dữ liệu cũ. Chưa chạy migration này trên MySQL thật trong phiên sửa mã; hãy backup trước khi cập nhật ứng dụng.
- Khoa/bộ môn mẫu chỉ được tạo khi cả hai bảng trống; khởi động lại tôn trọng tên và quan hệ admin đã chỉnh.
- Khóa tài khoản, đổi vai trò, email hoặc mật khẩu khiến phiên cũ phải đăng nhập lại ở yêu cầu kế tiếp. Cơ chế này kiểm tra tài khoản trong DB, nên cũng áp dụng khi có nhiều phiên/máy chủ.
- Quên mật khẩu dùng quy trình hỗ trợ bởi admin: gửi yêu cầu → xuất hiện ở đầu trang **Người dùng** → admin xác minh danh tính → đặt mật khẩu mới bằng biểu mẫu sửa tài khoản. Yêu cầu tự đóng sau khi cập nhật mật khẩu. Chưa tích hợp email tự động; không coi nút gửi yêu cầu là đã gửi email.
- `run-web.bat` không tự cấp thông tin tài khoản demo khi profile là `prod`, và không in mật khẩu ra console.

## Chạy kiểm thử

Hồ sơ/ảnh/tài khoản và SSE dùng migration V4. V3 → V4 đã được kiểm tra bổ sung trên MySQL 8.0.46 riêng, không thay đổi DB hiện có. Xem [hướng dẫn cập nhật, cấu hình và giới hạn kiểm thử](PROFILE-REALTIME.md).

```powershell
.\mvnw.cmd -o "-Dmaven.repo.local=.m2/repository" test
node --experimental-vm-modules --test .\scripts\frontend-regression.test.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-backup-files.ps1
```

`-o` sử dụng dependency đã có trong cache; bỏ tùy chọn này nếu máy mới chưa tải dependency. Java tests chạy H2 riêng; kiểm thử sao lưu dùng thư mục ngẫu nhiên dưới `target`, không đụng tới kho uploads thật.
