#!/usr/bin/env python3
"""Apply table-specific application grants after the customer/workshop Flyway migrations."""
import subprocess
statements=[]
hosts=subprocess.check_output(['podman','exec','tallermeco-db','mariadb','--no-defaults','--user=root','--batch','--skip-column-names','-e',"SELECT Host FROM mysql.user WHERE User='taller_app'"],text=True).splitlines()
for raw_host in hosts:
    host=raw_host.replace("'","''")
    for table in ('company','workshop','customer_workshop','user_workshop'):
        privileges='SELECT,INSERT,UPDATE' if table!='company' else 'SELECT,INSERT'
        statements.append(f"GRANT {privileges} ON tallermeco.{table} TO 'taller_app'@'{host}';")
    for table in ('customer_status','vehicle_status'):
        statements.append(f"GRANT SELECT,INSERT,UPDATE,DELETE ON tallermeco.{table} TO 'taller_app'@'{host}';")
    # Registry is maintained by definer triggers; the runtime cannot alter uniqueness reservations.
    statements.append(f"GRANT SELECT ON tallermeco.customer_contact TO 'taller_app'@'{host}';")
subprocess.run(['podman','exec','-i','tallermeco-db','mariadb','--no-defaults','--user=root'],input=('\n'.join(statements)+'\n').encode(),check=True)
print('Customer/workshop runtime grants applied.')
