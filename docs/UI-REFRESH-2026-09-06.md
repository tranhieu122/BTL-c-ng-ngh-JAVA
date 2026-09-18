# Giao diện thống nhất ngày 06/09/2026

## Thay đổi

- Font hệ thống Segoe UI/Arial hỗ trợ tiếng Việt, không tải font ngoài. Bỏ quy tắc ép Times New Roman toàn trang và thay các khai báo Georgia riêng lẻ bằng token font chung.
- `components/ui-system.css` quản lý màu, nút, trạng thái, focus bàn phím, form, bảng và mật độ nội dung. Giữ màu cảnh báo/nguy hiểm riêng cho thao tác tương ứng.
- `pages/home-refresh.css` chỉ áp dụng trên `.home-page`. Bỏ import `home-upgrade.css` vốn có các selector tìm kiếm/minh họa dùng chung ngoài trang chủ. File cũ còn trên đĩa nhưng không tham gia giao diện.
- Trang chủ thay hình minh họa bằng danh sách tài liệu thật từ `featuredDocuments`; có trạng thái kho trống, liên kết chi tiết và danh mục. Tìm kiếm vẫn là GET `/repository`.
- Dashboard giữ số liệu và biểu đồ, giảm độ lớn banner và bóng đổ. Hàng chờ bỏ hình trang trí để danh sách xuất hiện sớm hơn.
- Bảng quản trị, form nộp tài liệu, hồ sơ và chi tiết tài liệu dùng panel nhẹ, font dễ đọc và kích thước nút thống nhất.
- Giữ CSS đầu trang kho học liệu riêng trong `repository-intro.css`. Không thay controller, dữ liệu, quyền truy cập, action form hay luồng realtime trong lần nâng cấp này.

## Kiểm tra

- 17 test JavaScript hiện có đạt.
- 11 test Java (`PublicPagesRenderingTest`, `ProfileRealtimeIntegrationTest`) đạt.
- Kiểm tra các import CSS đều tồn tại, cân bằng dấu ngoặc các stylesheet mới, `git diff --check` đạt.
- Chưa kiểm tra ảnh chụp desktop/mobile, độ tương phản bằng công cụ hay tương tác trực quan vì không có trình duyệt kết nối. Kiểm thử render HTML không thay thế kiểm tra hình ảnh.

## Xem thay đổi

Chạy ứng dụng từ mã nguồn hiện tại và tải lại trang không dùng cache. Nếu chạy JAR đã đóng gói từ trước, cần đóng gói lại và khởi động bằng JAR mới để cập nhật template và CSS.

Điểm kiểm tra thủ công: trang chủ khi kho rỗng/có tài liệu; tìm kiếm và xóa từ khóa; bảng bộ môn; dashboard; hàng chờ và góp ý đang nhập; menu ở 375px, 768px, 1024px và màn hình lớn.
