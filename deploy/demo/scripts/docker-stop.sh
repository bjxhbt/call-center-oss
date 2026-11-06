#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/docker-common.sh"
compose stop web freeswitch api rustfs
echo 'Demo API, FreeSWITCH, RustFS and Nginx stopped; existing MySQL, Redis, network and data volumes were preserved.'
