#!/usr/bin/env bash
set -euo pipefail
DEMO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
HOST_ROOT=/home/nginx/html/xhbt-demo
if [[ -e /etc/nginx/conf.d/xhbt-demo.conf ]] && ! cmp -s "$DEMO_ROOT/docker/nginx-host.conf" /etc/nginx/conf.d/xhbt-demo.conf; then
  echo 'Existing xhbt-demo.conf differs; preserve and review it before updating.' >&2
  exit 1
fi
sudo install -d -m 755 "$HOST_ROOT/web"
sudo rsync -a --chmod=D755,F644 "$DEMO_ROOT/web/" "$HOST_ROOT/web/"
sudo install -m 644 "$DEMO_ROOT/docker/nginx-host.conf" /etc/nginx/conf.d/xhbt-demo.conf
sudo nginx -t
sudo systemctl reload nginx
echo 'Host Nginx is serving the demo at http://81.70.8.219:18088/login'
