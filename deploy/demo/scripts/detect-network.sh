#!/usr/bin/env bash
set -euo pipefail
mapfile -t containers < <(docker ps -q)
if [[ ${#containers[@]} -eq 0 ]]; then echo 'No running Docker containers found' >&2; exit 1; fi
format='{{range $name, $net := .NetworkSettings.Networks}}{{printf "%s " $name}}{{range $net.Aliases}}{{printf "%s " .}}{{end}}{{println}}{{end}}'
matches="$(docker inspect --format "$format" "${containers[@]}" | awk '
  {net=$1; for(i=2;i<=NF;i++){if($i=="mysql8") mysql[net]=1; if($i=="redis") redis[net]=1}}
  END {for(net in mysql) if(redis[net]) print net}' | sort -u)"
if [[ -z "$matches" || $(printf '%s\n' "$matches" | wc -l) -ne 1 ]]; then
  echo 'Could not select one shared network for aliases mysql8 and redis. Set OCH_DOCKER_NETWORK in docker/.env explicitly.' >&2
  exit 1
fi
printf '%s\n' "$matches"
