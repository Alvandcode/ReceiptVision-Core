@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title ReceiptVision - شروع خودکار

echo ================================================
echo   ReceiptVision - نسخه کاربر عادی
echo   فقط چند ثانیه صبر کنید...
echo ================================================
echo.

REM ---- 1) Docker نصب است؟ ----
docker --version >nul 2>&1
if errorlevel 1 (
  echo [خطا] داکر پیدا نشد.
  echo.
  echo لطفا اول Docker Desktop را نصب کنید:
  echo   https://www.docker.com/products/docker-desktop/
  echo.
  echo بعد از نصب، دوباره روی همین فایل دابل‌کلیک کنید.
  start https://www.docker.com/products/docker-desktop/
  pause
  exit /b 1
)

REM ---- 2) Docker در حال اجراست؟ ----
docker info >nul 2>&1
if errorlevel 1 (
  echo [خطا] داکر نصب است ولی روشن نیست.
  echo لطفا برنامه Docker Desktop را باز کنید و ۱ دقیقه صبر کنید،
  echo بعد دوباره روی همین فایل دابل‌کلیک کنید.
  pause
  exit /b 1
)

set IMAGE=ghcr.io/alvandcode/receiptvision-core:latest
set CNAME=receiptvision
set SECRET_FILE=%APPDATA%\ReceiptVision\jwt_secret.txt

REM ---- 3) کلید امنیتی شخصی (فقط بار اول ساخته می‌شود) ----
if not exist "%SECRET_FILE%" (
  echo [..] ساخت کلید امنیتی شخصی برای اولین بار...
  if not exist "%APPDATA%\ReceiptVision" mkdir "%APPDATA%\ReceiptVision"
  powershell -NoProfile -Command "$chars='abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -join ((1..48) | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })" > "%SECRET_FILE%"
)
set /p APP_JWT_SECRET=<"%SECRET_FILE%"

REM ---- 4) دانلود آخرین نسخه (بدون نیاز به سورس‌کد) ----
echo [..] دانلود آخرین نسخه... (بار اول کمی طول می‌کشد)
docker pull %IMAGE%
if errorlevel 1 (
  echo [خطا] دانلود ناموفق بود. اینترنت را بررسی کنید و دوباره تلاش کنید.
  pause
  exit /b 1
)

REM ---- 5) اجرای برنامه ----
echo [..] در حال روشن کردن برنامه...
docker rm -f %CNAME% >nul 2>&1
docker run -d --name %CNAME% --restart unless-stopped -p 8080:8080 -v receipt-data:/data -e APP_JWT_SECRET=%APP_JWT_SECRET% %IMAGE% >nul
if errorlevel 1 (
  echo [خطا] اجرا نشد. اگر پورت 8080 توسط برنامه دیگری اشغال است آن را ببندید.
  pause
  exit /b 1
)

REM ---- 6) انتظار تا آماده شدن (حداکثر حدود ۱ دقیقه) ----
echo [..] صبر کنید تا آماده شود...
set READY=0
for /L %%i in (1,1,30) do (
  powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
  if not errorlevel 1 (
    set READY=1
    goto :opened
  )
  echo      ... %%i
  timeout /t 2 /nobreak >nul
)

:opened
if "%READY%"=="1" (
  echo.
  echo [OK] برنامه آماده است! مرورگر باز می‌شود...
  echo آدرس: http://localhost:8080/
  echo.
  echo با «ثبت‌نام» یک حساب بسازید و وارد شوید.
  echo رسیدهای شما فقط برای خودتان ذخیره می‌شود.
  start http://localhost:8080/
) else (
  echo.
  echo [هشدار] برنامه روشن شد ولی هنوز جواب نمی‌دهد.
  echo ۱ دقیقه دیگر صبر کنید و این آدرس را باز کنید:
  echo   http://localhost:8080/
  echo اگر باز نشد، این دستور را ببینید: docker logs %CNAME%
)

echo.
echo برای خاموش کردن: روی Stop-ReceiptVision.bat دابل‌کلیک کنید.
echo (اطلاعات شما پاک نمی‌شود.)
pause
