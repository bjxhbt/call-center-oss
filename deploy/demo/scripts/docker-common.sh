#!/usr/bin/env bash
set -euo pipefail
DEMO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OCH_DOCKER_ENV_FILE="${OCH_DOCKER_ENV_FILE:-$DEMO_ROOT/docker/.env}"
if [[ ! -f "$OCH_DOCKER_ENV_FILE" ]]; then
  echo 'Missing private config. Place the prepared file at docker/.env or set OCH_DOCKER_ENV_FILE.' >&2
  exit 1
fi
command -v docker >/dev/null || { echo 'Docker Engine and Compose v2 are required' >&2; exit 1; }
docker compose version >/dev/null
if [[ -z "${OCH_FS_PUBLIC_IP:-}" ]] && grep -Eq "^OCH_FS_PUBLIC_IP=('auto'|auto)$" "$OCH_DOCKER_ENV_FILE"; then
  export OCH_FS_PUBLIC_IP
  OCH_FS_PUBLIC_IP="$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") {print $(i+1); exit}}')"
  [[ -n "$OCH_FS_PUBLIC_IP" ]] || { echo 'Set OCH_FS_PUBLIC_IP to the reachable server IPv4 address in docker/.env' >&2; exit 1; }
fi
if [[ -z "${OCH_DOCKER_NETWORK:-}" ]] && grep -Eq "^OCH_DOCKER_NETWORK=('auto'|auto)$" "$OCH_DOCKER_ENV_FILE"; then
  export OCH_DOCKER_NETWORK
  OCH_DOCKER_NETWORK="$(bash "$DEMO_ROOT/scripts/detect-network.sh")"
fi
compose() {
  local files=(-f "$DEMO_ROOT/docker/compose.yml")
  if [[ "${OCH_HOST_NGINX:-false}" == true ]]; then
    files+=(-f "$DEMO_ROOT/docker/compose.host-nginx.yml")
  fi
  docker compose --env-file "$OCH_DOCKER_ENV_FILE" "${files[@]}" "$@"
}
compose config --quiet
