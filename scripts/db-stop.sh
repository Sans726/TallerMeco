#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
podman compose -f infrastructure/podman/compose.yaml stop mariadb
