@echo off
chcp 65001 >nul
title ReceiptVision - خاموش کردن

echo در حال خاموش کردن ReceiptVision...
docker stop receiptvision >nul 2>&1
if errorlevel 1 (
  echo برنامه‌ای در حال اجرا پیدا نشد (قبلا خاموش بوده).
) else (
  echo [OK] خاموش شد.
  echo اطلاعات و رسیدهای شما پاک نشده و با اجرای دوباره برمی‌گردد.
)
echo.
pause
