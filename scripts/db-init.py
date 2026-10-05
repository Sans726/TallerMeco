#!/usr/bin/env python3
"""One-time bootstrap for a fresh MariaDB container; existing databases are never replaced."""
import hashlib
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
config = dict(line.split('=', 1) for line in (root / '.env').read_text().splitlines() if '=' in line)

def mariadb(sql: bytes, *args: str, capture: bool = False):
    command = ['podman', 'exec', '-i', 'tallermeco-db', 'mariadb', '--no-defaults', '--user=root', *args]
    if capture:
        return subprocess.run(command, input=sql.decode(), check=True, capture_output=True, text=True)
    return subprocess.run(command, input=sql, check=True)

def sql_string(value: str) -> str:
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"

exists = mariadb(b"SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='tallermeco';\n",
                 '--batch', '--skip-column-names', capture=True).stdout.strip()
if exists != '0':
    raise SystemExit('La base tallermeco ya existe. No se modifica: usar migraciones para cambios posteriores.')

mariadb(b"CREATE DATABASE tallermeco CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n")
subprocess.run(['podman', 'exec', '-i', 'tallermeco-db', 'mariadb', '--no-defaults', '--user=root', 'tallermeco'],
               input=(root / 'backend/src/main/resources/db/migration/V1__core_schema.sql').read_bytes(), check=True)

hosts = ('localhost', '10.89.0.2')
statements = []
for user, key in [('taller_owner', 'DB_OWNER_PASSWORD'), ('taller_app', 'DB_APP_PASSWORD')]:
    password = sql_string(config[key])
    for host in hosts:
        statements.append(f"CREATE USER {sql_string(user)}@{sql_string(host)} IDENTIFIED BY {password};")
        if user == 'taller_owner':
            statements.append(f"GRANT ALL PRIVILEGES ON tallermeco.* TO {sql_string(user)}@{sql_string(host)};")
        else:
            for table in ('app_user', 'password_reset_token', 'employee', 'customer', 'vehicle', 'service_order', 'order_assignment', 'work_entry'):
                statements.append(f"GRANT SELECT, INSERT, UPDATE ON tallermeco.{table} TO {sql_string(user)}@{sql_string(host)};")
            for table in ('inventory_movement', 'payment', 'order_status_history', 'audit_event', 'company', 'workshop', 'customer_workshop'):
                statements.append(f"GRANT SELECT, INSERT ON tallermeco.{table} TO {sql_string(user)}@{sql_string(host)};")
            statements.append(f"GRANT SELECT ON tallermeco.role TO {sql_string(user)}@{sql_string(host)};")
            statements.append(f"GRANT SELECT, INSERT, DELETE ON tallermeco.user_role TO {sql_string(user)}@{sql_string(host)};")
            statements.append(f"GRANT SELECT ON tallermeco.part TO {sql_string(user)}@{sql_string(host)};")
            columns = 'sku,name,unit,minimum_stock,reference_cost,reference_price,active'
            statements.append(f"GRANT INSERT ({columns}), UPDATE ({columns}) ON tallermeco.part TO {sql_string(user)}@{sql_string(host)};")
mariadb(('\n'.join(statements) + '\n').encode())
(root / '.local/schema-v1.sha256').write_text(hashlib.sha256((root / 'backend/src/main/resources/db/migration/V1__core_schema.sql').read_bytes()).hexdigest() + '\n')
print('Base tallermeco creada con permisos de aplicación y migración separados.')
