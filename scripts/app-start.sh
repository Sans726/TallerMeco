#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if systemctl --user is-active --quiet tallermeco-app-real.service; then
  printf 'TallerMeco ya está activo: http://127.0.0.1:8080/#/login\n'
  exit 0
fi
systemd-run --user --unit=tallermeco-app-real --collect --working-directory="$PWD" \
  /run/current-system/sw/bin/bash "$PWD/scripts/run-real.sh"
printf 'TallerMeco iniciando. URL: http://127.0.0.1:8080/#/login\n'
