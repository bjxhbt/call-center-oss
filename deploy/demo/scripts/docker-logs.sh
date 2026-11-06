#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/docker-common.sh"
compose logs --tail=100 api freeswitch web
