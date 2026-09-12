@echo off
chcp 65001 >nul
setlocal
title ReceiptVision - Backup
REM Backup for the portable (no-Docker) package: zips the .\data folder.
REM Run it while the app is STOPPED (use Stop.bat first).
cd /d "%~dp0"

if not exist "data" (
  echo [خطا] پوشه data پیدا نشد. اول برنامه را یک‌بار با Start.bat اجرا کنید.
  pause
  exit /b 1
)

set STAMP=%DATE:~0,4%%DATE:~5,2%%DATE:~8,2%-%TIME:~0,2%%TIME:~3,2%
set STAMP=%STAMP: =0%
if not exist "backups" mkdir "backups"
set OUT=backups\receipt-data-%STAMP%.zip

powershell -NoProfile -Command "Compress-Archive -Path 'data' -DestinationPath '%OUT%' -Force"
if errorlevel 1 (
  echo [خطا] بکاپ ناموفق بود.
  pause
  exit /b 1
)

echo.
echo [OK] بکاپ ساخته شد: %OUT%
echo آن را در فلش/هارد دیگر نگه دارید.
echo برگرداندن: برنامه را خاموش کنید و محتوای زیپ را روی پوشه data بازگردانید.
pause
