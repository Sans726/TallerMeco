#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if systemctl --user is-active --quiet tallermeco-prototype; then
 printf 'Prototipo activo: http://localhost:5173\n'
 exit 0
fi
if [ ! -f frontend/node_modules/vite/bin/vite.js ]; then
 printf 'Faltan dependencias locales. No se descargará nada automáticamente.\n' >&2
 exit 1
fi
systemd-run --user --unit=tallermeco-prototype --collect --working-directory="$PWD/frontend" --setenv=VITE_DEMO=true /usr/bin/node "$PWD/frontend/node_modules/vite/bin/vite.js" --host 127.0.0.1 --port 5173 --strictPort
printf 'Abrir http://localhost:5173\n'
