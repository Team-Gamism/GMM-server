#!/usr/bin/env bash
# Deploys one image tag on the school server and rolls back if the API does not become healthy.
# Usage: deploy.sh <image-tag>. Run by .github/workflows/cd.yml on the self-hosted runner.
set -euo pipefail

TAG="${1:?image tag required}"
DIR="${GMM_DEPLOY_DIR:-/srv/gmm}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"

if [[ ! -f "$DIR/.env" ]]; then
  echo "::error::$DIR/.env is missing. Copy deploy/.env.example there and fill it in."
  exit 1
fi

mkdir -p "$DIR/nginx"
install -m 644 "$SRC/compose.prod.yaml" "$DIR/compose.prod.yaml"
install -m 644 "$SRC/nginx/builds.conf" "$DIR/nginx/builds.conf.template"

compose() {
  local tag="$1"
  shift
  IMAGE_TAG="$tag" docker compose --project-directory "$DIR" --env-file "$DIR/.env" -f "$DIR/compose.prod.yaml" "$@"
}

wait_healthy() {
  local tag="$1" container status
  for _ in $(seq 1 36); do
    container="$(compose "$tag" ps -q api)"
    status="$(docker inspect -f '{{.State.Health.Status}}' "$container" 2>/dev/null || echo starting)"
    [[ "$status" == "healthy" ]] && return 0
    [[ "$status" == "unhealthy" ]] && return 1
    sleep 5
  done
  return 1
}

echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin >/dev/null
trap 'docker logout ghcr.io >/dev/null 2>&1 || true' EXIT

PREVIOUS="$(cat "$DIR/.deployed-tag" 2>/dev/null || true)"
echo "Deploy $TAG (previous: ${PREVIOUS:-none})"

compose "$TAG" pull api builds mysql
compose "$TAG" up -d --remove-orphans

if wait_healthy "$TAG"; then
  echo "$TAG" > "$DIR/.deployed-tag"
  echo "Deployed $TAG"
  exit 0
fi

echo "::error::API did not become healthy with $TAG."
compose "$TAG" logs --tail 200 api || true
if [[ -n "$PREVIOUS" ]]; then
  echo "Roll back to $PREVIOUS"
  compose "$PREVIOUS" up -d api
  if wait_healthy "$PREVIOUS"; then
    echo "::warning::Rolled back to $PREVIOUS."
  else
    echo "::error::Rollback to $PREVIOUS is not healthy either."
  fi
fi
exit 1
