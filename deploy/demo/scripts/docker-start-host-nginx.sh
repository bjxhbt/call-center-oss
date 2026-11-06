#!/usr/bin/env bash
set -euo pipefail
export OCH_HOST_NGINX=true
source "$(dirname -- "${BASH_SOURCE[0]}")/docker-common.sh"
if [[ $# -gt 1 || ($# -eq 1 && "$1" != '--init') ]]; then echo 'Usage: bash scripts/docker-start-host-nginx.sh [--init]' >&2; exit 1; fi
compose build api freeswitch
if [[ "${1:-}" == '--init' ]]; then
  read -r -p 'Type INITIALIZE to create the new EMPTY demo schema: ' answer
  [[ "$answer" == INITIALIZE ]] || { echo 'Initialization cancelled' >&2; exit 1; }
  read -r -s -p 'Choose admin password (at least 12 characters): ' OCH_BOOTSTRAP_PASSWORD; printf '\n'
  read -r -s -p 'Re-enter the administrator credential: ' confirmation; printf '\n'
  [[ ${#OCH_BOOTSTRAP_PASSWORD} -ge 12 && "$OCH_BOOTSTRAP_PASSWORD" == "$confirmation" ]] || { echo 'Passwords do not match or are too short' >&2; exit 1; }
  export OCH_BOOTSTRAP_PASSWORD
  trap 'unset OCH_BOOTSTRAP_PASSWORD confirmation' EXIT
  compose --profile init run --rm check-redis
  compose --profile init run --rm init
  unset OCH_BOOTSTRAP_PASSWORD confirmation
fi
compose up -d --wait --wait-timeout 240 rustfs
compose --profile init run --rm --no-deps storage-setup
compose --profile init run --rm --no-deps fs-setup
compose up -d --force-recreate --wait --wait-timeout 300 api freeswitch
compose --profile init run --rm --no-deps verify-fs
compose --profile init run --rm --no-deps verify-storage
echo 'API and FreeSWITCH ready. Start the host Nginx site with scripts/install-host-nginx.sh.'
