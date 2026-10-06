#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
compose_file=infrastructure/podman/compose.yaml
podman compose -f "$compose_file" up -d mariadb
for attempt in $(seq 1 45); do
  if podman exec tallermeco-db mariadb-admin --no-defaults ping >/dev/null 2>&1 &&
     /run/current-system/sw/bin/bash -c 'exec 3<>/dev/tcp/127.0.0.1/3306' >/dev/null 2>&1; then
    printf 'MariaDB 12.3.2 listo en 127.0.0.1:3306\n'
    exit 0
  fi
  sleep 1
done
podman logs --tail=80 tallermeco-db >&2
printf 'MariaDB no quedó listo. Revisar los logs del contenedor.\n' >&2
exit 1
