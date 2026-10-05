#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if [[ ! -f .env ]]; then
  echo "Falta .env en $PROJECT_DIR" >&2
  exit 1
fi

if [[ ! -x .local/apache-maven-3.9.11/bin/mvn ]]; then
  echo "Falta Maven local en .local/apache-maven-3.9.11/bin/mvn" >&2
  exit 1
fi

if [[ ! -x frontend/node_modules/.bin/vue-tsc ]]; then
  echo "Faltan las dependencias locales de frontend." >&2
  echo "No se instalará nada automáticamente; ejecuta npm install dentro de frontend si es necesario." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "No se encontró Java en PATH." >&2
  exit 1
fi

if [[ ! -x "${JAVA_HOME:-}/bin/java" ]]; then
  if [[ -x /usr/lib64/jvm/java-25-openjdk-25/bin/java ]]; then
    export JAVA_HOME=/usr/lib64/jvm/java-25-openjdk-25
  elif command -v java >/dev/null 2>&1; then
    java_bin="$(readlink -f "$(command -v java)")"
    export JAVA_HOME="${java_bin%/bin/java}"
  else
    echo "No se encontró una instalación válida de Java." >&2
    exit 1
  fi
fi

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME no apunta a una instalación válida: $JAVA_HOME" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
. ./.env
set +a

echo "[1/3] Iniciando MariaDB local..."
./scripts/db-start.sh

echo "[2/3] Compilando frontend real..."
npm --prefix frontend run build

echo "[3/3] Iniciando Spring Boot real en http://127.0.0.1:8080"
echo "Aplicación real: http://127.0.0.1:8080/#/login"
echo "Demo Vite en 5173: no se inicia."

exec .local/apache-maven-3.9.11/bin/mvn \
  -B \
  -f backend/pom.xml \
  -Dmaven.compiler.release= \
  -Dmaven.compiler.source=21 \
  -Dmaven.compiler.target=21 \
  spring-boot:run
