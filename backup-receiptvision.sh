#!/usr/bin/env sh
# ReceiptVision backup: Docker volume + H2 files.
# Usage: sh backup-receiptvision.sh
set -eu
STAMP=$(date +%Y%m%d-%H%M)
mkdir -p backups
if docker volume inspect receipt-data >/dev/null 2>&1; then
  docker run --rm -v receipt-data:/data -v "$PWD/backups:/backup" \
    alpine tar czf "/backup/receipt-data-docker-$STAMP.tar.gz" -C /data .
  echo "OK: backups/receipt-data-docker-$STAMP.tar.gz"
else
  echo "(skip: docker volume receipt-data not found)"
fi
echo "Restore: docker run --rm -v receipt-data:/data -v \$PWD/backups:/backup alpine sh -c 'rm -rf /data/* && tar xzf /backup/FILE -C /data'"
