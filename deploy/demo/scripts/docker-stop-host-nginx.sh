#!/usr/bin/env bash
set -euo pipefail
export OCH_HOST_NGINX=true
source "$(dirname -- "${BASH_SOURCE[0]}")/docker-common.sh"
compose stop freeswitch api rustfs
echo 'Demo API, FreeSWITCH and RustFS stopped; existing host Nginx, MySQL, Redis and data volumes were preserved.'
