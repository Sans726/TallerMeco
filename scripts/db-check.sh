#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
podman exec tallermeco-db mariadb-admin --no-defaults ping
