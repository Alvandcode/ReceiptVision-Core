@echo off
chcp 65001 >nul
title ReceiptVision - خاموش کردن

echo در حال خاموش کردن ReceiptVision...
taskkill /F /FI "WINDOWTITLE eq ReceiptVision-Server*" >nul 2>&1
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -match 'receiptvision-core' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>&1
echo [OK] خاموش شد.
echo اطلاعات و رسیدهای شما پاک نشده و با اجرای دوباره برمی‌گردد.
echo.
pause
