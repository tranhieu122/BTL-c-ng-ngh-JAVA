@echo off
setlocal
title EduRepo - Nhap mat khau MySQL va khoi dong web

cd /d "%~dp0"

echo ========================================
echo EduRepo - Khoi dong web
echo ========================================
echo.

set "DB_PASSWORD="
for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "$secret=Read-Host 'Nhap mat khau MySQL cua user root (Enter neu de trong)' -AsSecureString; $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }"`) do set "DB_PASSWORD=%%P"

if not defined APP_ADMIN_EMAIL set "APP_ADMIN_EMAIL=admin@edurepo.local"
if not defined APP_ADMIN_PASSWORD (
    echo.
    for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "$secret=Read-Host 'Nhap mat khau admin khoi tao (de trong neu admin da ton tai)' -AsSecureString; $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }"`) do set "APP_ADMIN_PASSWORD=%%P"
)

echo.
echo Dang khoi dong Spring Boot. Dung cua so nay de xem log.
echo Trinh duyet se tu mo sau khi web san sang.
echo Tai khoan admin khoi tao neu database chua co user:
echo Email: %APP_ADMIN_EMAIL%
if defined APP_ADMIN_PASSWORD echo Password: da duoc nhap an
echo.

rem Kiem tra cong bang TcpClient de khong in Test-NetConnection chen vao log.
start "" /b powershell.exe -NoProfile -WindowStyle Hidden -Command "$limit=(Get-Date).AddSeconds(120); while ((Get-Date) -lt $limit) { $client=New-Object Net.Sockets.TcpClient; try { $client.Connect('127.0.0.1',8080); $client.Close(); Start-Process 'http://localhost:8080'; exit 0 } catch { $client.Dispose(); Start-Sleep -Seconds 1 } }" >nul 2>&1

call ".\mvnw.cmd" "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" --no-transfer-progress clean spring-boot:run
set "APP_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%APP_EXIT_CODE%"=="0" (
    echo Spring Boot da dung voi ma loi %APP_EXIT_CODE%.
    echo Kiem tra lai:
    echo 1. Da tao database document_management trong MySQL.
    echo 2. Mat khau root MySQL da nhap dung.
    echo 3. MySQL dang chay va lang nghe tai localhost:3306.
    echo 4. Neu muon tu tao admin, hay dat APP_ADMIN_EMAIL va APP_ADMIN_PASSWORD.
) else (
    echo Ung dung da dung.
)
pause
exit /b %APP_EXIT_CODE%
