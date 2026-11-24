#!/usr/bin/env bash
# Deploys one image tag on the school server and rolls back if the API does not become healthy.
# Usage: deploy.sh <image-tag>. Run by .github/workflows/cd.yml on the self-hosted runner.
set -euo pipefail

TAG="${1:?image tag required}"
DIR="${GMM_DEPLOY_DIR:-/srv/gmm}"
SRC="$(cd "$(dirname "$0")/.." && pwd)"

# Settings passed in from the GitHub `production` environment by cd.yml.
REQUIRED=(MYSQL_PASSWORD MYSQL_ROOT_PASSWORD DATAGSM_CLIENT_ID DATAGSM_REDIRECT_URIS GMM_BUILD_PUBLIC_BASE_URL GMM_FRONTEND_ORIGIN)
OPTIONAL=(MYSQL_DATABASE MYSQL_USER DATAGSM_ALLOW_TEACHER GMM_ADMIN_EMAILS GMM_CORS_ALLOWED_ORIGINS
  GMM_SESSION_COOKIE_SECURE GMM_API_DOCS_ENABLED COMPOSE_PROFILES GMM_FRONT_DIR FRONT_BIND FRONT_PORT
  BUILDS_BIND BUILDS_PORT API_BIND API_PORT API_MEM_LIMIT MYSQL_MEM_LIMIT MYSQL_BUFFER_POOL JAVA_TOOL_OPTIONS)

missing=()
for name in "${REQUIRED[@]}"; do
  [[ -n "${!name:-}" ]] || missing+=("$name")
done
if (( ${#missing[@]} )); then
  echo "::error::Set these in GitHub → Settings → Environments → production: ${missing[*]}"
  exit 1
fi

mkdir -p "$DIR/nginx"

# Rebuild .env from scratch on every deploy, readable only by the runner account.
# Values are single-quoted so compose takes them literally.
env_file="$(mktemp "$DIR/.env.XXXXXX")"
chmod 600 "$env_file"
for name in "${REQUIRED[@]}" "${OPTIONAL[@]}"; do
  value="${!name:-}"
  [[ -n "$value" ]] || continue
  if [[ "$value" == *"'"* || "$value" == *$'\n'* ]]; then
    rm -f "$env_file"
    echo "::error::$name must not contain a single quote or a line break."
    exit 1
  fi
  printf "%s='%s'\n" "$name" "$value" >> "$env_file"
done
mv "$env_file" "$DIR/.env"
nginx_config_hash() {
  cat "$DIR/nginx/builds.conf.template" "$DIR/nginx/edge.conf" 2>/dev/null | sha256sum
}
nginx_before="$(nginx_config_hash)"
install -m 644 "$SRC/compose.prod.yaml" "$DIR/compose.prod.yaml"
install -m 644 "$SRC/nginx/builds.conf" "$DIR/nginx/builds.conf.template"
install -m 644 "$SRC/nginx/edge.conf" "$DIR/nginx/edge.conf"
nginx_changed=false
[[ "$(nginx_config_hash)" == "$nginx_before" ]] || nginx_changed=true

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

compose "$TAG" pull
compose "$TAG" up -d --remove-orphans

# The nginx configs are single-file bind mounts. `install` replaces the file, but a running container keeps the old
# one and `up -d` does not recreate it for a content change, so restart the running nginx services to load the new one.
# Only running services: naming a service of an inactive profile (e.g. front) would start it.
if [[ "$nginx_changed" == true ]]; then
  running="$(compose "$TAG" ps --services --status running)"
  for service in builds front; do
    if grep -qx "$service" <<< "$running"; then
      echo "Restart $service for the new nginx config"
      compose "$TAG" restart "$service"
    fi
  done
fi

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
