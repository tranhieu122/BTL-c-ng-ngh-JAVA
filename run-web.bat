@echo off
setlocal
title EduRepo - Nhap mat khau MySQL va khoi dong web

cd /d "%~dp0"

rem Maven Wrapper stops immediately when JAVA_HOME points to an incomplete JDK.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" if exist "%JAVA_HOME%\bin\javac.exe" goto java_ready

if defined JAVA_HOME echo JAVA_HOME hien tai khong hop le: %JAVA_HOME%
echo Dang tim JDK hop le tren may...
set "JAVA_HOME="

for /d %%D in ("%ProgramFiles%\Java\jdk-*" "%ProgramFiles%\Eclipse Adoptium\jdk-*" "%LocalAppData%\Programs\Eclipse Adoptium\jdk-*") do call :use_jdk "%%~fD"

if not defined JAVA_HOME (
    echo.
    echo Khong tim thay JDK hop le. Hay cai JDK 25 tro len va mo lai file nay.
    echo JDK phai co ca bin\java.exe va bin\javac.exe.
    pause
    exit /b 1
)

:java_ready
echo Dang dung JDK: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version
echo.

echo ========================================
echo EduRepo - Khoi dong web
echo ========================================
echo.

set "DB_PASSWORD="
for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "$secret=Read-Host 'Nhap mat khau MySQL cua user root (Enter neu de trong)' -AsSecureString; $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }"`) do set "DB_PASSWORD=%%P"

if not defined APP_ADMIN_EMAIL set "APP_ADMIN_EMAIL=admin@edurepo.local"
if not defined APP_ADMIN_PASSWORD set "APP_ADMIN_PASSWORD=Admin@123456"
if not defined APP_USER_EMAIL set "APP_USER_EMAIL=user@edurepo.local"
if not defined APP_USER_PASSWORD set "APP_USER_PASSWORD=User@123456"

echo.
echo Dang khoi dong Spring Boot. Dung cua so nay de xem log.
echo Trinh duyet se tu mo sau khi web san sang.
echo Tai khoan demo se duoc tao hoac dat lai mat khau:
echo Admin: %APP_ADMIN_EMAIL% / %APP_ADMIN_PASSWORD%
echo User:  %APP_USER_EMAIL% / %APP_USER_PASSWORD%
echo.

rem Kiem tra cong bang TcpClient de khong in Test-NetConnection chen vao log.
start "" /b powershell.exe -NoProfile -WindowStyle Hidden -Command "$limit=(Get-Date).AddSeconds(120); while ((Get-Date) -lt $limit) { $client=New-Object Net.Sockets.TcpClient; try { $client.Connect('127.0.0.1',8081); $client.Close(); Start-Process 'http://localhost:8081'; exit 0 } catch { $client.Dispose(); Start-Sleep -Seconds 1 } }" >nul 2>&1

call ".\mvnw.cmd" "-Dmaven.repo.local=%USERPROFILE%\.m2\repository" --no-transfer-progress spring-boot:run
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

:use_jdk
if defined JAVA_HOME exit /b 0
if exist "%~1\bin\java.exe" if exist "%~1\bin\javac.exe" set "JAVA_HOME=%~1"
exit /b 0
