# README5 - Giai thich cac file trong bai lam OTP Email

Tai lieu nay tom tat cac file chinh da duoc them hoac sua de lam chuc nang xac thuc OTP qua email cho dang ky tai khoan va quen mat khau.

## 1. File cau hinh va thu vien

### `pom.xml`

Them thu vien `spring-boot-starter-mail` de Spring Boot co the gui email thong qua `JavaMailSender`.

### `src/main/resources/application.properties`

Them cau hinh SMTP bang bien moi truong:

- `MAIL_USERNAME`: email dung de gui OTP.
- `MAIL_PASSWORD`: Gmail App Password hoac mat khau SMTP.
- `MAIL_HOST`, `MAIL_PORT`: may chu SMTP.
- `OTP_TTL_MINUTES`: thoi gian OTP con hieu luc.
- `OTP_RESEND_COOLDOWN_SECONDS`: thoi gian cho truoc khi duoc gui lai OTP.
- `OTP_MAX_ATTEMPTS`: so lan nhap sai toi da.
- `OTP_HASH_PEPPER`: chuoi bo sung khi hash OTP.

Neu chua cau hinh SMTP, he thong chi ghi canh bao va khong lam ung dung bi dung.

## 2. File database

### `src/main/resources/db/migration/V11__auth_otp_tokens.sql`

Tao bang `auth_otp_tokens` de luu thong tin OTP.

Bang nay luu:

- Email nhan OTP.
- Muc dich OTP: dang ky hoac quen mat khau.
- Ma OTP da duoc hash, khong luu OTP goc.
- Thoi gian het han.
- So lan nhap sai.
- Thoi gian duoc phep gui lai OTP.
- Thoi diem OTP da duoc su dung.

## 3. Entity, enum va repository

### `src/main/java/com/hieu/edurepo/entity/AuthOtpToken.java`

Entity dai dien cho bang `auth_otp_tokens`.

File nay dung de quan ly OTP trong database, bao gom ma OTP da hash, email, loai OTP, han su dung, so lan thu va trang thai da dung hay chua.

### `src/main/java/com/hieu/edurepo/enums/OtpPurpose.java`

Enum xac dinh muc dich cua OTP:

- `REGISTER`: OTP dung cho dang ky tai khoan.
- `PASSWORD_RESET`: OTP dung cho quen mat khau.

### `src/main/java/com/hieu/edurepo/repository/AuthOtpTokenRepository.java`

Repository dung de truy van OTP trong database.

Chuc nang chinh:

- Lay OTP moi nhat theo email va muc dich.
- Tim cac OTP chua duoc su dung de vo hieu hoa khi tao OTP moi.

## 4. DTO dung cho form

### `src/main/java/com/hieu/edurepo/dto/OtpForm.java`

DTO nhan du lieu tu form nhap OTP.

### `src/main/java/com/hieu/edurepo/dto/ResetPasswordForm.java`

DTO nhan du lieu tu form dat lai mat khau moi.

File nay dung cho man hinh reset password sau khi nguoi dung da xac thuc OTP thanh cong.

## 5. Service xu ly nghiep vu

### `src/main/java/com/hieu/edurepo/service/EmailService.java`

Service phu trach gui email OTP.

Chuc nang:

- Tao noi dung email theo tung muc dich.
- Gui OTP bang `JavaMailSender`.
- Neu SMTP chua cau hinh hoac gui that bai, ghi log canh bao va khong lam ung dung crash.

### `src/main/java/com/hieu/edurepo/service/OtpService.java`

Service phu trach toan bo logic OTP.

Chuc nang:

- Tao ma OTP 6 chu so.
- Hash OTP truoc khi luu vao database.
- Kiem tra OTP dung hay sai.
- Kiem tra OTP het han.
- Gioi han so lan nhap sai.
- Chong spam gui lai OTP bang resend cooldown.
- Danh dau OTP da duoc su dung sau khi xac thuc thanh cong.

### `src/main/java/com/hieu/edurepo/service/UserService.java`

Bo sung cac ham can thiet cho flow OTP:

- Kiem tra email da ton tai hay chua.
- Kiem tra tai khoan dang hoat dong.
- Tao tai khoan bang mat khau da encode.
- Dat lai mat khau moi.

### `src/main/java/com/hieu/edurepo/service/impl/UserServiceImpl.java`

Code xu ly that su cho cac ham trong `UserService`.

Trong file nay, mat khau moi luon duoc encode bang BCrypt truoc khi luu vao database.

## 6. Controller va security

### `src/main/java/com/hieu/edurepo/controller/AuthController.java`

Controller xu ly cac route dang ky, xac thuc OTP va quen mat khau.

Luon dang ky:

1. Nguoi dung nhap thong tin dang ky.
2. He thong chua tao tai khoan ngay.
3. He thong tao OTP va gui ve email.
4. Nguoi dung nhap OTP.
5. OTP dung thi moi tao tai khoan.

Luon quen mat khau:

1. Nguoi dung nhap email.
2. Neu email ton tai va tai khoan dang hoat dong, he thong gui OTP.
3. Thong bao hien thi khong lam lo email co ton tai hay khong.
4. OTP dung thi cho phep dat mat khau moi.
5. Mat khau moi duoc encode bang BCrypt.

### `src/main/java/com/hieu/edurepo/config/SecurityConfig.java`

Mo public route cho cac trang OTP va reset password de nguoi dung chua dang nhap van truy cap duoc.

Nhung route duoc mo them:

- `/register/verify`
- `/register/verify/resend`
- `/forgot-password/verify`
- `/forgot-password/verify/resend`
- `/reset-password`

## 7. Template giao dien Thymeleaf

### `src/main/resources/templates/auth/verify-register.html`

Trang nhap OTP sau khi nguoi dung submit form dang ky.

Trang nay co:

- O nhap ma OTP.
- Nut xac nhan OTP.
- Nut gui lai OTP.
- Thong bao loi neu OTP sai, het han hoac nhap sai qua nhieu lan.

### `src/main/resources/templates/auth/verify-reset.html`

Trang nhap OTP cho chuc nang quen mat khau.

Sau khi OTP dung, nguoi dung duoc chuyen sang trang dat mat khau moi.

### `src/main/resources/templates/auth/reset-password.html`

Trang dat mat khau moi sau khi da xac thuc OTP quen mat khau thanh cong.

### `src/main/resources/templates/auth/forgot-password.html`

Cap nhat noi dung trang quen mat khau de dung flow OTP qua email thay vi yeu cau lien he admin.

### `src/main/resources/templates/auth/login.html`

Them thong bao dang nhap sau khi nguoi dung dat lai mat khau thanh cong.

## 8. Test

### `src/test/java/com/hieu/edurepo/controller/AuthOtpIntegrationTest.java`

Test cac truong hop chinh cua OTP:

- Dang ky xong chua tao tai khoan ngay.
- OTP dung thi tao tai khoan.
- OTP sai thi khong tao tai khoan.
- OTP het han thi khong tao tai khoan.
- Gui lai OTP phai ton trong cooldown.
- Quen mat khau khong lam lo email co ton tai hay khong.
- OTP dung thi dat lai mat khau thanh cong.

### `src/test/java/com/hieu/edurepo/controller/AuditFixRegressionTest.java`

Cap nhat test cu de phu hop voi flow quen mat khau moi bang OTP.

## 9. File tai lieu

### `README.md`

Cap nhat huong dan cau hinh Gmail App Password va cac bien moi truong can thiet de gui OTP that.

## 10. Tong ket luong chay

### Dang ky tai khoan

Nguoi dung dang ky -> he thong gui OTP -> nguoi dung nhap OTP -> OTP dung thi tao tai khoan.

### Quen mat khau

Nguoi dung nhap email -> he thong gui OTP neu email hop le -> nguoi dung nhap OTP -> dat mat khau moi -> quay ve trang dang nhap.

