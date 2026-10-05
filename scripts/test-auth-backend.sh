#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./scripts/db-start.sh
umask 077
mkdir -p .local
test_schema="tallermeco_auth_test_$(date -u +%Y%m%d%H%M%S)"
printf 'AUTH_TEST_DB_NAME=%s\nAUTH_TEST_APP_PASSWORD=%s\nAUTH_TEST_OWNER_PASSWORD=%s\n' \
  "$test_schema" "$(openssl rand -hex 32)" "$(openssl rand -hex 32)" > .local/auth-test.env
. ./.local/auth-test.env
export AUTH_TEST_URL="jdbc:mariadb://127.0.0.1:3306/$AUTH_TEST_DB_NAME"
export AUTH_TEST_USER=tallermeco_test_app
export AUTH_TEST_PASSWORD=$AUTH_TEST_APP_PASSWORD
export AUTH_TEST_OWNER=tallermeco_test_owner
export AUTH_TEST_OWNER_PASSWORD
printf "CREATE DATABASE IF NOT EXISTS %s CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS 'tallermeco_test_app'@'10.89.0.2' IDENTIFIED BY '%s'; ALTER USER 'tallermeco_test_app'@'10.89.0.2' IDENTIFIED BY '%s'; GRANT ALL PRIVILEGES ON %s.* TO 'tallermeco_test_app'@'10.89.0.2'; CREATE USER IF NOT EXISTS 'tallermeco_test_owner'@'10.89.0.2' IDENTIFIED BY '%s'; ALTER USER 'tallermeco_test_owner'@'10.89.0.2' IDENTIFIED BY '%s'; GRANT ALL PRIVILEGES ON %s.* TO 'tallermeco_test_owner'@'10.89.0.2'; FLUSH PRIVILEGES;\n" \
  "$AUTH_TEST_DB_NAME" "$AUTH_TEST_APP_PASSWORD" "$AUTH_TEST_APP_PASSWORD" \
  "$AUTH_TEST_DB_NAME" "$AUTH_TEST_OWNER_PASSWORD" "$AUTH_TEST_OWNER_PASSWORD" "$AUTH_TEST_DB_NAME" |
  podman exec -i tallermeco-db mariadb --no-defaults --user=root
./.local/apache-maven-3.9.11/bin/mvn -o -B -f backend/pom.xml test
