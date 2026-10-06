#!/usr/bin/env python3
"""Copy existing local DB accounts/grants to the current rootless container peer IP.

Preserves passwords and table privileges; never grants wildcard network access.
Run after recreating the MariaDB container if its address changed.
"""
import ipaddress
import re
import subprocess

def sql(statement):
    result = subprocess.run(
        ['podman','exec','-i','tallermeco-db','mariadb','--no-defaults','--user=root','--batch','--skip-column-names'],
        input=statement, text=True, capture_output=True)
    if result.returncode:
        # SQL diagnostics can contain authentication hashes; never echo them.
        raise SystemExit('Database account synchronization failed; review local DB configuration.')
    return result.stdout

addresses = subprocess.check_output(
    ['podman','inspect','--format','{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}','tallermeco-db'],text=True).strip()
host = str(ipaddress.IPv4Address(addresses))
if not ipaddress.IPv4Address(host).is_private:
    raise SystemExit('Expected a private local container address.')
statements=[]
for user in ('taller_owner','taller_app'):
    exists=sql(f"SELECT COUNT(*) FROM mysql.user WHERE User='{user}' AND Host='{host}';").strip()
    if exists == '1':
        continue
    create=sql(f"SHOW CREATE USER '{user}'@'localhost';").strip().split('\t')[-1]
    grants=sql(f"SHOW GRANTS FOR '{user}'@'localhost';").splitlines()
    for statement in [create,*grants]:
        changed=re.sub(r"@(?:'localhost'|`localhost`)","@'"+host+"'",statement)
        if changed == statement:
            raise SystemExit('Unexpected account SQL format; no new grants applied.')
        statements.append(changed.rstrip(';')+';')
if statements:
    sql('\n'.join(statements))
print('Existing DB account privileges synchronized for local container peer:',host)
