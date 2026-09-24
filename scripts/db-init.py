#!/usr/bin/env python3
"""One-time bootstrap. Future schema changes will use Flyway in Spring Boot."""
import getpass
import hashlib
from pathlib import Path
import subprocess
import MySQLdb

root = Path(__file__).resolve().parents[1]
config = dict(line.split('=', 1) for line in (root / '.env').read_text().splitlines() if '=' in line)
db = MySQLdb.connect(unix_socket=str(root / '.local/mariadb.sock'), user=getpass.getuser())
c = db.cursor()
c.execute("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='tallermeco'")
if c.fetchone()[0]:
    raise SystemExit('La base tallermeco ya existe. No se modifica: usar migraciones para cambios posteriores.')
c.execute("CREATE DATABASE tallermeco CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
subprocess.run(['mariadb', '--no-defaults', '--socket=' + str(root / '.local/mariadb.sock'), 'tallermeco'],
               input=(root / 'backend/src/main/resources/db/migration/V1__core_schema.sql').read_bytes(), check=True)
for user, key in [('taller_owner','DB_OWNER_PASSWORD'),('taller_app','DB_APP_PASSWORD')]:
    c.execute('CREATE USER %s@%s IDENTIFIED BY %s', (user,'localhost',config[key]))
c.execute("GRANT ALL PRIVILEGES ON tallermeco.* TO 'taller_owner'@'localhost'")
for table in ['app_user','password_reset_token','customer','employee','vehicle','service_order','order_assignment','work_entry']:
    c.execute(f"GRANT SELECT, INSERT, UPDATE ON tallermeco.{table} TO 'taller_app'@'localhost'")
c.execute("GRANT SELECT ON tallermeco.role TO 'taller_app'@'localhost'")
c.execute("GRANT SELECT, INSERT, DELETE ON tallermeco.user_role TO 'taller_app'@'localhost'")
for table in ['inventory_movement','payment','order_status_history','audit_event']:
    c.execute(f"GRANT SELECT, INSERT ON tallermeco.{table} TO 'taller_app'@'localhost'")
c.execute("GRANT SELECT ON tallermeco.part TO 'taller_app'@'localhost'")
columns = 'sku,name,unit,minimum_stock,reference_cost,reference_price,active'
c.execute(f"GRANT INSERT ({columns}), UPDATE ({columns}) ON tallermeco.part TO 'taller_app'@'localhost'")
(root / '.local/schema-v1.sha256').write_text(hashlib.sha256((root / 'backend/src/main/resources/db/migration/V1__core_schema.sql').read_bytes()).hexdigest()+'\n')
db.close()
print('Base tallermeco creada; usuarios administrativos y de aplicación separados.')
