# Cấu trúc front-end EduRepo

Front-end dùng Thymeleaf, CSS thuần và JavaScript ES Modules. Hai file đầu vào duy nhất là:

- `static/css/style.css`: nhập các module CSS theo đúng thứ tự phụ thuộc.
- `static/js/main.js`: nhập và khởi tạo các module JavaScript.

Nhờ vậy, template chỉ cần liên kết một file CSS và một file JavaScript, trong khi mã nguồn vẫn được tách nhỏ để dễ bảo trì.

## CSS

```text
static/css/
├── style.css                  # Điểm vào, chỉ chứa @import
├── foundation.css             # Biến màu, reset, typography, button chung
├── app-shell.css              # Sidebar, form, bảng và dashboard nội bộ
├── responsive.css             # Breakpoint dùng chung và chế độ in
├── components/
│   ├── header.css             # Logo, header, menu desktop/mobile
│   └── footer.css             # Footer và toast notification
└── pages/
    ├── repository.css         # Trang chủ và danh sách học liệu
    ├── document-detail.css    # Trang chi tiết học liệu
    └── login.css              # Trang đăng nhập
```

Quy tắc chỉnh sửa:

- Màu sắc, kích thước, button hoặc style cơ bản: sửa `foundation.css`.
- Thành phần dùng chung: sửa file tương ứng trong `components/`.
- Chỉ một trang sử dụng: sửa file tương ứng trong `pages/`.
- Giao diện quản trị, form và table: sửa `app-shell.css`.
- Breakpoint responsive: sửa `responsive.css`.
- Không viết style mới trực tiếp vào `style.css`; file này chỉ quản lý thứ tự import.

## JavaScript

```text
static/js/
├── main.js                    # Điểm vào và thứ tự khởi tạo
└── modules/
    ├── core.js                # Tiện ích chung, ngày, file, alert, toast
    ├── navigation.js          # Menu mobile và trạng thái route active
    ├── catalog.js             # Lọc, sort, đổi kiểu hiển thị, phím tắt tìm kiếm
    ├── auth.js                # Hiện mật khẩu, Caps Lock, loading đăng nhập
    └── document-actions.js    # Chia sẻ và sao chép liên kết tài liệu
```

Quy tắc chỉnh sửa:

- Mỗi module chỉ quản lý một nhóm chức năng.
- Hàm dùng chung được `export` từ `core.js` và `import` ở module cần dùng.
- Module mới phải export một hàm khởi tạo, sau đó được gọi trong `main.js`.
- Script trong template phải giữ `type="module"` để trình duyệt xử lý các lệnh `import`.

## Liên kết trong Thymeleaf

CSS:

```html
<link rel="stylesheet" th:href="@{/css/style.css}">
```

JavaScript:

```html
<script type="module" th:src="@{/js/main.js}"></script>
```

Spring Security đã cho phép `/css/**` và `/js/**`, vì vậy các file nằm trong thư mục con vẫn được tải bình thường.
