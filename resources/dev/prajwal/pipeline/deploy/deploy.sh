#!/usr/bin/env bash
# Runs on the target host. Replaces one app container with a new image, waits for its health
# endpoint, and puts the previous image back if the new one never becomes healthy.
#
# Usage: deploy.sh <app-name> <image> <container-port> <host-port> <health-path>
# The host keeps app settings in /etc/apps/<app-name>.env (passed to the container as --env-file).
set -euo pipefail

APP=$1
IMAGE=$2
PORT=$3
HOST_PORT=$4
HEALTH_PATH=$5
ENV_FILE=/etc/apps/${APP}.env
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-90}

log() { echo "[deploy ${APP}] $*"; }

previous_image=$(docker inspect --format '{{.Config.Image}}' "$APP" 2>/dev/null || true)
log "current image: ${previous_image:-none}, new image: ${IMAGE}"

# SKIP_PULL=1 is for testing this script with a locally built image.
[[ "${SKIP_PULL:-0}" == 1 ]] || docker pull --quiet "$IMAGE"

start() { # start <image>
  local env_args=()
  [[ -f "$ENV_FILE" ]] && env_args=(--env-file "$ENV_FILE")
  docker rm -f "$APP" >/dev/null 2>&1 || true
  docker run -d --name "$APP" --restart unless-stopped \
    "${env_args[@]}" -p "${HOST_PORT}:${PORT}" "$1" >/dev/null
}

healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while (( SECONDS < deadline )); do
    if curl -fs -o /dev/null "http://127.0.0.1:${HOST_PORT}${HEALTH_PATH}"; then
      return 0
    fi
    sleep 3
  done
  return 1
}

start "$IMAGE"
if healthy; then
  log "healthy on ${IMAGE}"
  docker image prune -f >/dev/null
  exit 0
fi

log "new image did not become healthy within ${HEALTH_TIMEOUT}s"
docker logs --tail 50 "$APP" || true
if [[ -n "$previous_image" ]]; then
  log "rolling back to ${previous_image}"
  start "$previous_image"
  if healthy; then
    log "rollback healthy"
  else
    log "rollback ALSO unhealthy, needs a person"
  fi
fi
exit 1
