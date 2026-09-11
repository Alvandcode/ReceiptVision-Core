@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title ReceiptVision
REM اجرا از پوشه خود فایل، مهم برای مسیرهای نسبی
cd /d "%~dp0"

echo ================================================
echo   ReceiptVision - نسخه بدون داکر
echo   فقط چند ثانیه صبر کنید...
echo ================================================
echo.

set JAVA=.\jre\bin\java.exe
set JAR=.\app\receiptvision-core.jar
set TESSDIR=%CD%\tesseract
set TESSDATA=%TESSDIR%\tessdata
set SECRET_FILE=%APPDATA%\ReceiptVision\jwt_secret.txt

REM ---- بررسی فایل‌های لازم ----
if not exist "%JAVA%" (
  echo [خطا] پوشه jre پیدا نشد. فایل زیپ را کامل باز کنید.
  pause
  exit /b 1
)
if not exist "%JAR%" (
  echo [خطا] فایل برنامه پیدا نشد. فایل زیپ را کامل باز کنید.
  pause
  exit /b 1
)
if not exist "%TESSDIR%\tesseract.exe" (
  echo [خطا] پوشه tesseract پیدا نشد. فایل زیپ را کامل باز کنید.
  pause
  exit /b 1
)

REM ---- کلید امنیتی شخصی (فقط بار اول ساخته می‌شود) ----
if not exist "%SECRET_FILE%" (
  echo [..] ساخت کلید امنیتی برای اولین بار...
  if not exist "%APPDATA%\ReceiptVision" mkdir "%APPDATA%\ReceiptVision"
  powershell -NoProfile -Command "$chars='abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -join ((1..48) | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })" > "%SECRET_FILE%"
)
set /p APP_JWT_SECRET=<"%SECRET_FILE%"

REM ---- تنظیمات: فقط روی همین کامپیوتر، بدون نیاز به اینترنت ----
set PORT=8080
set SERVER_ADDRESS=127.0.0.1
set SPRING_DATASOURCE_URL=jdbc:h2:file:./data/receiptsdb;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE
set H2_CONSOLE_ENABLED=false
set OCR_LANGUAGES=fas+eng
set OCR_COMMAND=%TESSDIR%\tesseract.exe
set TESSDATA_PREFIX=%TESSDATA%
set JAVA_OPTS=-XX:MaxRAMPercentage=75.0

REM ---- اگر از قبل روشن است، همان را باز کن ----
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if not errorlevel 1 (
  echo [OK] برنامه از قبل روشن است. بازش می‌کنم...
  goto :openbrowser
)

REM ---- روشن کردن در پس‌زمینه ----
echo [..] در حال روشن کردن برنامه... (بار اول تا ۱ دقیقه طول می‌کشد)
if not exist ".\data" mkdir ".\data"
start "ReceiptVision-Server" /min "%JAVA%" %JAVA_OPTS% -jar "%JAR%"
set READY=0
for /L %%i in (1,1,45) do (
  powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
  if not errorlevel 1 (
    set READY=1
    goto :opened
  )
  timeout /t 2 /nobreak >nul
)

:opened
if "%READY%"=="0" (
  echo [خطا] برنامه جواب نداد. پنجره ReceiptVision-Server را ببینید.
  pause
  exit /b 1
)

echo.
echo [OK] برنامه آماده است!
echo.
echo با «ثبت‌نام» یک حساب بسازید و وارد شوید.
echo رسیدهای شما فقط برای خودتان ذخیره می‌شود.

:openbrowser
REM باز کردن مثل یک برنامه واقعی (پنجره جدا، بدون نوار آدرس)
where msedge >nul 2>&1
if not errorlevel 1 (
  start "" msedge --app=http://localhost:8080/
  goto :done
)
where chrome >nul 2>&1
if not errorlevel 1 (
  start "" chrome --app=http://localhost:8080/
  goto :done
)
start http://localhost:8080/

:done
echo.
echo برای خاموش کردن: روی Stop.bat دابل‌کلیک کنید.
echo (اطلاعات شما پاک نمی‌شود.)
pause
