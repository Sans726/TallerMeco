#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
backup_file="backups/tallermeco-$(date -u +%Y%m%dT%H%M%SZ).sql"
mariadb-dump --no-defaults --socket="$PWD/.local/mariadb.sock" --single-transaction --routines --triggers tallermeco > "$backup_file.tmp"
mv "$backup_file.tmp" "$backup_file"
printf 'Backup local creado: %s\n' "$backup_file"
