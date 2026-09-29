#!/usr/bin/env python3
"""Verify the P2-02 migration on an isolated, empty MariaDB schema."""
import getpass
import secrets
import subprocess
import uuid
from pathlib import Path

import MySQLdb

ROOT = Path(__file__).resolve().parents[1]
SOCKET = ROOT / ".local/mariadb.sock"
schema = "tallermeco_p202_" + uuid.uuid4().hex[:12]
connection = None
admin = MySQLdb.connect(unix_socket=str(SOCKET), user=getpass.getuser())
cursor = admin.cursor()

try:
    cursor.execute(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    for name in ("V1__core_schema.sql", "V2__movement_returns.sql", "V3__receptionist_role.sql", "V4__customer_workshop_model.sql"):
        migration = ROOT / "backend/src/main/resources/db/migration" / name
        subprocess.run(
            ["mariadb", "--no-defaults", "--socket=" + str(SOCKET), schema],
            input=migration.read_bytes(), check=True,
        )

    connection = MySQLdb.connect(unix_socket=str(SOCKET), user=getpass.getuser(), db=schema)
    connection.autocommit(True)
    check = connection.cursor()
    check.execute("SELECT COUNT(*) FROM company")
    assert check.fetchone()[0] == 0
    check.execute("SELECT COUNT(*) FROM workshop")
    assert check.fetchone()[0] == 0
    check.execute("SELECT COUNT(*) FROM customer")
    assert check.fetchone()[0] == 0
    check.execute("SELECT COUNT(*) FROM customer_workshop")
    assert check.fetchone()[0] == 0

    check.execute("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME='customer' AND COLUMN_NAME='age'", (schema,))
    assert check.fetchone()[0] == 0
    check.execute("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=%s AND TABLE_NAME='customer_workshop' AND CONSTRAINT_TYPE='PRIMARY KEY'", (schema,))
    assert check.fetchone()[0] == 1
    check.execute("SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE CONSTRAINT_SCHEMA=%s AND TABLE_NAME='customer_workshop' AND REFERENCED_TABLE_NAME IN ('customer','workshop')", (schema,))
    assert check.fetchone()[0] == 2

    check.execute("INSERT INTO company(name) VALUES ('Empresa prueba')")
    company_id = check.lastrowid
    check.execute("INSERT INTO workshop(company_id,name) VALUES (%s,'Taller prueba')", (company_id,))
    workshop_id = check.lastrowid
    check.execute("INSERT INTO customer(full_name,personal_email,personal_phone) VALUES ('Cliente Uno',' Test@Example.COM ','(55) 1234-5678')")
    customer_one = check.lastrowid
    check.execute("INSERT INTO customer(full_name,personal_email) VALUES ('Cliente Dos','otro@example.com')")
    customer_two = check.lastrowid
    check.execute("INSERT INTO customer_workshop(customer_id,workshop_id) VALUES (%s,%s),(%s,%s)", (customer_one, workshop_id, customer_two, workshop_id))
    check.execute("SELECT personal_email_normalized,personal_phone_normalized FROM customer WHERE id=%s", (customer_one,))
    assert check.fetchone() == ("test@example.com", "5512345678")
    try:
        check.execute("INSERT INTO customer(full_name,personal_email) VALUES ('Duplicado','test@example.com')")
        raise AssertionError("email duplicate accepted")
    except MySQLdb.IntegrityError:
        pass
    try:
        check.execute("INSERT INTO customer(full_name,personal_phone) VALUES ('Duplicado','55 1234 5678')")
        raise AssertionError("phone duplicate accepted")
    except MySQLdb.IntegrityError:
        pass
    print("P2-02: migration, tables, FKs, composite PK, normalization, duplicate constraints and empty initial state passed.")
finally:
    if connection is not None:
        connection.close()
    cursor.execute(f"DROP DATABASE IF EXISTS `{schema}`")
    admin.close()
