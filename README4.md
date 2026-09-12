# README4 - Giai thich logic va cach van hanh code OTP Email

Tai lieu nay giai thich cach chuc nang OTP qua email hoat dong trong du an Spring Boot MVC/Thymeleaf.

## 1. Muc tieu chuc nang

Chuc nang OTP duoc them vao hai luong chinh:

- Dang ky tai khoan.
- Quen mat khau.

Muc dich la khong cho tao tai khoan hoac doi mat khau ngay lap tuc. Nguoi dung phai xac thuc email bang ma OTP truoc.

## 2. Logic dang ky tai khoan

### Buoc 1: Nguoi dung gui form dang ky

Nguoi dung nhap:

- Ho ten.
- Email.
- Mat khau.
- Xac nhan mat khau.

Khi bam submit, request duoc gui den `AuthController`.

### Buoc 2: He thong kiem tra du lieu

Controller kiem tra:

- Email da ton tai hay chua.
- Mat khau va xac nhan mat khau co khop khong.
- Mat khau co dung policy hien co cua du an khong.

Neu loi, he thong quay lai trang dang ky va hien thong bao loi.

### Buoc 3: Chua tao tai khoan ngay

Neu du lieu hop le, he thong khong tao user ngay trong database.

Thong tin dang ky duoc luu tam trong session:

- Ho ten.
- Email.
- Mat khau da duoc BCrypt encode.

Vi mat khau da encode nen khong luu mat khau plain text lau dai.

### Buoc 4: Tao va gui OTP

`OtpService` tao ma OTP 6 chu so.

Truoc khi luu vao database, OTP duoc hash. Database chi luu OTP da hash, khong luu ma OTP goc.

Sau do `EmailService` gui OTP ve email dang ky.

### Buoc 5: Chuyen sang trang nhap OTP

Nguoi dung duoc chuyen sang trang:

```text
/register/verify
```

Tai day nguoi dung nhap ma OTP da nhan trong email.

### Buoc 6: Xac thuc OTP

Khi nguoi dung nhap OTP:

- Neu OTP dung va con han: he thong tao tai khoan moi.
- Neu OTP sai: he thong bao loi va tang so lan nhap sai.
- Neu OTP het han: he thong yeu cau gui lai OTP.
- Neu nhap sai qua so lan cho phep: OTP bi khoa.

Sau khi tao tai khoan thanh cong, nguoi dung duoc dua ve trang dang nhap.

## 3. Logic quen mat khau

### Buoc 1: Nguoi dung nhap email

Nguoi dung truy cap trang quen mat khau va nhap email.

Request duoc xu ly trong `AuthController`.

### Buoc 2: Khong tiet lo email co ton tai hay khong

He thong luon hien thong bao chung dang:

```text
Neu email hop le, chung toi da gui ma OTP.
```

Dieu nay giup tranh viec ke xau doan email nao dang co tai khoan trong he thong.

### Buoc 3: Neu email hop le thi gui OTP

Neu email ton tai va tai khoan dang hoat dong:

- He thong tao OTP.
- Hash OTP roi luu vao bang `auth_otp_tokens`.
- Gui OTP ve email nguoi dung.

Neu email khong ton tai, he thong khong gui OTP nhung van hien thong bao chung.

### Buoc 4: Nguoi dung nhap OTP

Nguoi dung duoc chuyen sang trang:

```text
/forgot-password/verify
```

Tai day nguoi dung nhap ma OTP.

### Buoc 5: OTP dung thi cho dat lai mat khau

Neu OTP dung va con han, he thong chuyen sang:

```text
/reset-password
```

Nguoi dung nhap mat khau moi va xac nhan mat khau.

### Buoc 6: Luu mat khau moi

Mat khau moi duoc kiem tra theo policy hien co cua du an.

Neu hop le, `UserServiceImpl` encode mat khau bang BCrypt roi moi luu vao database.

Sau khi thanh cong, nguoi dung duoc chuyen ve trang dang nhap.

## 4. Cach OTP duoc bao mat

### Khong luu OTP plain text

OTP gui qua email la ma goc, nhung database chi luu ban hash.

Khi nguoi dung nhap OTP, he thong hash ma nguoi dung vua nhap roi so sanh voi ma hash trong database.

### Co thoi gian het han

Moi OTP co thoi gian het han, mac dinh cau hinh bang:

```properties
OTP_TTL_MINUTES=10
```

Neu qua thoi gian nay, OTP khong con dung duoc.

### Gioi han so lan nhap sai

Moi OTP co bo dem so lan nhap sai.

So lan toi da duoc cau hinh bang:

```properties
OTP_MAX_ATTEMPTS=5
```

Neu vuot qua so lan nay, OTP bi khoa va nguoi dung phai gui lai OTP moi.

### Chong spam gui lai OTP

Nguoi dung khong duoc bam gui lai OTP lien tuc.

Thoi gian cho duoc cau hinh bang:

```properties
OTP_RESEND_COOLDOWN_SECONDS=60
```

## 5. Cac file chiu trach nhiem chinh

### `AuthController.java`

Dieu phoi cac route:

- Dang ky.
- Xac thuc OTP dang ky.
- Gui lai OTP dang ky.
- Quen mat khau.
- Xac thuc OTP quen mat khau.
- Gui lai OTP quen mat khau.
- Dat lai mat khau.

### `OtpService.java`

Xu ly logic OTP:

- Tao OTP.
- Hash OTP.
- Luu OTP.
- Xac thuc OTP.
- Kiem tra het han.
- Kiem tra so lan nhap sai.
- Kiem tra cooldown gui lai OTP.

### `EmailService.java`

Xu ly gui email OTP bang `JavaMailSender`.

Neu chua cau hinh SMTP, service se log canh bao va khong lam ung dung crash.

### `AuthOtpToken.java`

Entity anh xa voi bang `auth_otp_tokens`.

### `AuthOtpTokenRepository.java`

Repository dung de lay va luu OTP trong database.

### `UserServiceImpl.java`

Xu ly tao user va doi mat khau.

Mat khau luon duoc encode bang BCrypt truoc khi luu.

### `SecurityConfig.java`

Mo cac route public cho nguoi dung chua dang nhap co the truy cap trang OTP va reset password.

## 6. Cach van hanh tren moi truong dev

### Buoc 1: Cau hinh email gui OTP

Neu dung Gmail, can tao Gmail App Password.

Sau do cau hinh bien moi truong:

```powershell
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-gmail-app-password"
```

Co the tuy chinh them:

```powershell
$env:OTP_TTL_MINUTES="10"
$env:OTP_RESEND_COOLDOWN_SECONDS="60"
$env:OTP_MAX_ATTEMPTS="5"
```

### Buoc 2: Chay ung dung

Chay ung dung Spring Boot bang Maven:

```powershell
.\mvnw.cmd spring-boot:run
```

### Buoc 3: Thu dang ky

Mo trinh duyet va vao trang dang ky.

Nhap email that de nhan OTP.

Neu email gui thanh cong, nguoi dung se thay trang nhap OTP.

### Buoc 4: Thu quen mat khau

Vao trang quen mat khau.

Nhap email cua tai khoan dang hoat dong.

He thong se gui OTP, sau do cho phep dat lai mat khau neu OTP dung.

## 7. Khi chua cau hinh SMTP

Neu chua co `MAIL_USERNAME` hoac `MAIL_PASSWORD`, ung dung van chay.

Tuy nhien, email OTP that se khong duoc gui.

He thong se log canh bao de lap trinh vien biet rang SMTP chua san sang.

## 8. Cach chay test

Chay toan bo test:

```powershell
.\mvnw.cmd test
```

Test chinh cho OTP nam o file:

```text
src/test/java/com/hieu/edurepo/controller/AuthOtpIntegrationTest.java
```

File test nay kiem tra:

- Dang ky chua tao tai khoan khi chua nhap OTP.
- OTP dung moi tao tai khoan.
- OTP sai khong tao tai khoan.
- OTP het han khong tao tai khoan.
- Quen mat khau khong lam lo email co ton tai hay khong.
- Dat lai mat khau thanh cong sau khi OTP dung.

## 9. Tom tat ngan gon

Dang ky:

```text
Form dang ky -> Validate -> Luu tam session -> Tao OTP -> Gui email -> Nhap OTP -> Tao tai khoan
```

Quen mat khau:

```text
Nhap email -> Gui OTP neu tai khoan hop le -> Nhap OTP -> Dat mat khau moi -> Dang nhap lai
```

Bao mat:

```text
OTP duoc hash, co han su dung, co gioi han so lan sai va co cooldown gui lai
```

