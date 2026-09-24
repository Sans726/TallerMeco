#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
project_dir="$PWD"
umask 077
mkdir -p .local/mariadb
if [ ! -d .local/mariadb/mysql ]; then
 mariadb-install-db --no-defaults --datadir="$project_dir/.local/mariadb" --auth-root-authentication-method=socket --skip-test-db > .local/install.log 2>&1
fi
if mariadb-admin --no-defaults --socket="$project_dir/.local/mariadb.sock" ping >/dev/null 2>&1; then
 printf 'MariaDB del proyecto ya está activo.\n'
 exit 0
fi
systemd-run --user --unit=tallermeco-db --collect --property=UMask=0077 /usr/sbin/mariadbd --no-defaults --datadir="$project_dir/.local/mariadb" \
 --socket="$project_dir/.local/mariadb.sock" --pid-file="$project_dir/.local/mariadb.pid" \
 --bind-address=127.0.0.1 --port=3306 --character-set-server=utf8mb4 \
 --collation-server=utf8mb4_unicode_ci --default-time-zone=+00:00 \
 --log-error="$project_dir/.local/mariadb.log" \
 --sql-mode=STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION \

for attempt in $(seq 1 30); do
 if mariadb-admin --no-defaults --socket="$project_dir/.local/mariadb.sock" ping >/dev/null 2>&1; then
  printf 'MariaDB listo en 127.0.0.1:3306\n'
  exit 0
 fi
 sleep 1
done
printf 'No arrancó. Revisar .local/mariadb.log\n' >&2
exit 1
