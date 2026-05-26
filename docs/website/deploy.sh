#!/usr/bin/env bash
# Deploy Marine Weather static site to 94.237.38.55 under /marine-weather/
set -euo pipefail
HOST="${DEPLOY_HOST:-94.237.38.55}"
REMOTE_DIR="/var/www/marine-weather"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

rsync -avz --delete \
  "$SCRIPT_DIR/" \
  "root@${HOST}:${REMOTE_DIR}/" \
  --exclude deploy.sh \
  --exclude '.DS_Store'

echo "Deployed to http://${HOST}/marine-weather/"
