#!/usr/bin/env python3
"""Apply runtime grants required by the customer/workshop module after V4."""
import subprocess

statements = []
for host in ('localhost', '10.89.0.2'):
    for table in ('company', 'workshop', 'customer_workshop'):
        statements.append(f"GRANT SELECT, INSERT ON tallermeco.{table} TO 'taller_app'@'{host}';")
subprocess.run(['podman', 'exec', '-i', 'tallermeco-db', 'mariadb', '--no-defaults', '--user=root'],
               input=('\n'.join(statements) + '\n').encode(), check=True)
print('Runtime grants applied for the application connection.')
