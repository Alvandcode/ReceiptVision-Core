@echo off
chcp 65001 >nul
setlocal
title ReceiptVision - Backup
REM Backup for Docker deployment (volume receipt-data) AND portable ./data folder.
cd /d "%~dp0"

set STAMP=%DATE:~0,4%%DATE:~5,2%%DATE:~8,2%-%TIME:~0,2%%TIME:~3,2%
set STAMP=%STAMP: =0%
if not exist "backups" mkdir "backups"

echo [1/2] Portable ./data folder...
if exist "..\ReceiptVision-Core\windows-package" cd /d "%~dp0"
if exist "data" (
  powershell -NoProfile -Command "Compress-Archive -Path 'data' -DestinationPath ('backups\receipt-data-local-' + '%STAMP%' + '.zip') -Force"
  echo   OK: backups\receipt-data-local-%STAMP%.zip
) else (
  echo   (skip: no local .\data folder)
)

echo [2/2] Docker volume receipt-data...
docker volume inspect receipt-data >nul 2>&1
if errorlevel 1 (
  echo   (skip: docker volume receipt-data not found)
) else (
  docker run --rm -v receipt-data:/data -v "%CD%\backups:/backup" alpine tar czf /backup/receipt-data-docker-%STAMP%.tar.gz -C /data . >nul 2>&1
  if errorlevel 1 (
    echo   [warn] docker backup failed. Is Docker running?
  ) else (
    echo   OK: backups\receipt-data-docker-%STAMP%.tar.gz
  )
)

echo.
echo Done. Keep the backups\ folder somewhere safe (USB / cloud).
echo Restore: Docker -> docker run --rm -v receipt-data:/data -v %%CD%%\backups:/backup alpine sh -c "rm -rf /data/* && tar xzf /backup/FILE -C /data"
echo Restore: Portable -> unzip over the .\data folder while the app is stopped.
pause
