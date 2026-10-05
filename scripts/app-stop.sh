#!/bin/sh
set -eu
systemctl --user stop tallermeco-app-real.service
"$(dirname "$0")/db-stop.sh"
printf 'TallerMeco y MariaDB detenidos.\n'
