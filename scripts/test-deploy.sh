#!/usr/bin/env bash
# Exercises deploy.sh on the local Docker daemon:
#   1. deploy a healthy image          -> must succeed
#   2. deploy an image that never gets healthy -> must fail AND leave the healthy one running
set -euo pipefail
cd "$(dirname "$0")/.."

APP=deploy-script-test
PORT=18080
DEPLOY=resources/dev/prajwal/pipeline/deploy/deploy.sh
BASE=${BASE_IMAGE:-nginx:1.27-alpine}
work=$(mktemp -d)
trap 'docker rm -f "$APP" >/dev/null 2>&1 || true; rm -rf "$work"' EXIT

echo "--- building test images"
mkdir -p "$work/good" "$work/bad"
echo ok > "$work/good/health"
printf 'FROM %s\nCOPY health /usr/share/nginx/html/health\n' "$BASE" > "$work/good/Dockerfile"
printf 'FROM %s\n' "$BASE" > "$work/bad/Dockerfile"     # no /health -> 404 forever
docker build -q -t "$APP:good" "$work/good" >/dev/null
docker build -q -t "$APP:bad" "$work/bad" >/dev/null

export SKIP_PULL=1 HEALTH_TIMEOUT=15

echo "--- 1. healthy deploy"
bash "$DEPLOY" "$APP" "$APP:good" 80 "$PORT" /health

echo "--- 2. broken deploy must roll back"
if bash "$DEPLOY" "$APP" "$APP:bad" 80 "$PORT" /health; then
  echo "FAIL: broken image was reported as deployed"; exit 1
fi
running=$(docker inspect --format '{{.Config.Image}}' "$APP")
[[ "$running" == "$APP:good" ]] || { echo "FAIL: expected $APP:good after rollback, got $running"; exit 1; }
curl -fs "http://127.0.0.1:${PORT}/health" >/dev/null || { echo "FAIL: rolled-back app not answering"; exit 1; }

echo "PASS: deploy and rollback both behave"
