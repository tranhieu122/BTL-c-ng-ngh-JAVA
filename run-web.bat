@echo off
setlocal
title EduRepo - Khoi dong website

cd /d "%~dp0"
if exist "%~dp0.env" (
    for /f "usebackq eol=# tokens=1* delims==" %%A in ("%~dp0.env") do if not "%%A"=="" set "%%A=%%B"
)
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dev"
if not defined UPLOAD_DIR set "UPLOAD_DIR=%~dp0uploads"
if not defined MAIL_USERNAME set "MAIL_USERNAME=theshup990@gmail.com"
set "APP_URL=http://127.0.0.1:8080"
set "HOME_URL=%APP_URL%/?launch=%RANDOM%%RANDOM%"

for /f "tokens=5" %%P in ('netstat -ano -p tcp ^| findstr /R /C:"[.:]8080 .*LISTENING"') do if not defined PORT_8080_PID set "PORT_8080_PID=%%P"
if defined PORT_8080_PID (
    powershell.exe -NoProfile -Command "try { $response=Invoke-WebRequest -Uri '%APP_URL%/' -UseBasicParsing -TimeoutSec 5; if ([string]$response.Content -match '<title>\s*EduRepo') { exit 0 } } catch {}; exit 1"
    if not errorlevel 1 (
        echo EduRepo dang chay san tren cong 8080 voi PID %PORT_8080_PID%.
        echo Hay tat cua so EduRepo cu hoac chay: taskkill /PID %PORT_8080_PID% /F
        echo Sau do chay lai run-web.bat de nap Gmail App Password va mat khau MySQL moi.
        pause
        exit /b 1
    )

    echo.
    echo Khong the khoi dong EduRepo: cong 8080 dang bi PID %PORT_8080_PID% su dung.
    echo Hay chuyen ung dung do sang cong 8081, sau do chay lai file nay.
    pause
    exit /b 1
)

rem EduRepo is compiled and run with JDK 25.
echo Dang tim JDK 25 tren may...
set "JAVA_HOME="

for /d %%D in ("%ProgramFiles%\Java\jdk-25*" "%ProgramFiles%\Eclipse Adoptium\jdk-25*" "%LocalAppData%\Programs\Eclipse Adoptium\jdk-25*") do (
    if exist "%%~fD\bin\java.exe" if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"
)

if not defined JAVA_HOME (
    echo.
    echo Khong tim thay JDK 25. Hay cai JDK 25 va mo lai file nay.
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

set "ENABLE_DEMO_DEFAULTS="
if /i "%SPRING_PROFILES_ACTIVE%"=="dev" set "ENABLE_DEMO_DEFAULTS=true"
if /i "%SPRING_PROFILES_ACTIVE%"=="local" set "ENABLE_DEMO_DEFAULTS=true"
if defined ENABLE_DEMO_DEFAULTS (
    if not defined APP_ADMIN_EMAIL set "APP_ADMIN_EMAIL=admin@edurepo.local"
    if not defined APP_ADMIN_PASSWORD set "APP_ADMIN_PASSWORD=Admin@123456"
    if not defined APP_USER_EMAIL set "APP_USER_EMAIL=user@edurepo.local"
    if not defined APP_USER_PASSWORD set "APP_USER_PASSWORD=User@123456"
)

echo Buoc 1/3 - Cau hinh Gmail de gui OTP
echo Mail gui OTP: %MAIL_USERNAME%
if defined MAIL_USERNAME if not defined MAIL_PASSWORD (
    for /f "usebackq delims=" %%P in (`powershell.exe -NoProfile -Command "$secret=Read-Host 'Nhap Gmail App Password 16 ky tu cho %MAIL_USERNAME%' -AsSecureString; $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret); try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }"`) do set "MAIL_PASSWORD=%%P"
)
if defined MAIL_USERNAME if not defined MAIL_PASSWORD (
    echo.
    echo Chua co Gmail App Password nen email OTP that se khong gui duoc.
    echo Hay tao App Password trong Google Account roi chay lai file nay.
    pause
    exit /b 1
)
echo Da nhan Gmail App Password.
echo.

echo.
echo Dang khoi dong Spring Boot. Dung cua so nay de xem log.
echo Trinh duyet se tu mo sau khi web san sang.
echo Tai khoan demo chi duoc tao neu chua ton tai; mat khau cu khong bi ghi de:
echo Admin email: %APP_ADMIN_EMAIL%
echo User email:  %APP_USER_EMAIL%
echo Profile: %SPRING_PROFILES_ACTIVE%
echo.

rem Wait until the listener has remained stable before making an HTTP request.
rem Probing during Tomcat initialization can cause a false port conflict on Windows.
start "" /b powershell.exe -NoProfile -WindowStyle Hidden -Command "$limit=(Get-Date).AddSeconds(120); while ((Get-Date) -lt $limit) { $listener=[Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() ^| Where-Object Port -eq 8080 ^| Select-Object -First 1; if ($listener) { Start-Sleep -Seconds 8; try { $response=Invoke-WebRequest -Uri '%APP_URL%/actuator/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Start-Process '%HOME_URL%'; exit 0 } } catch {} }; Start-Sleep -Seconds 1 }" >nul 2>&1

set "USES_MYSQL_PROFILE="
if /i "%SPRING_PROFILES_ACTIVE%"=="dev" set "USES_MYSQL_PROFILE=true"
if /i "%SPRING_PROFILES_ACTIVE%"=="local" set "USES_MYSQL_PROFILE=true"

if defined USES_MYSQL_PROFILE (
    echo Buoc 2/3 - Dang nhap MySQL de doc database cu
    echo Database mac dinh: localhost:3306/document_management
    echo Day la mat khau MySQL, khong phai mat khau Gmail.
    if defined DB_PASSWORD (
        rem Reuse a password already present in the process environment without prompting again.
        echo Da co DB_PASSWORD trong moi truong. Dang khoi dong web...
        echo Buoc 3/3 - Khoi dong EduRepo
        .\mvnw.cmd "-Dmaven.repo.local=%~dp0.m2\repository" --no-transfer-progress compile spring-boot:run
    ) else (
        rem Keep the password inside PowerShell's process environment. This avoids cmd.exe
        rem corrupting passwords that contain characters such as &, !, ^, or quotation marks.
        powershell.exe -NoProfile -Command "$secret=Read-Host 'Nhap mat khau MySQL cua user root (Enter neu de trong neu MySQL khong co mat khau)' -AsSecureString; $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret); try { $env:DB_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer); Write-Host 'Buoc 3/3 - Khoi dong EduRepo'; & '.\mvnw.cmd' '-Dmaven.repo.local=%~dp0.m2\repository' '--no-transfer-progress' 'compile' 'spring-boot:run'; exit $LASTEXITCODE } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }"
    )
) else (
    echo Buoc 2/3 - Profile nay khong dung MySQL mac dinh cua dev/local.
    echo Buoc 3/3 - Khoi dong EduRepo
    .\mvnw.cmd "-Dmaven.repo.local=%~dp0.m2\repository" --no-transfer-progress compile spring-boot:run
)
set "APP_EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%APP_EXIT_CODE%"=="0" (
    echo Spring Boot da dung voi ma loi %APP_EXIT_CODE%.
    echo Kiem tra lai:
    echo 1. Khong xoa .data, uploads hoac database cu.
    echo 2. Hay dam bao MySQL dang chay tai localhost:3306.
    echo 3. Voi profile dev, hay nhap dung mat khau root MySQL hoac dat DB_PASSWORD.
    echo 4. Neu muon tu tao admin, hay dat APP_ADMIN_EMAIL va APP_ADMIN_PASSWORD.
) else (
    echo Ung dung da dung.
)
pause
exit /b %APP_EXIT_CODE%
