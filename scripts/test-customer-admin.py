#!/usr/bin/env python3
"""Run backend tests against disposable MariaDB schemas; never reset the application's DB."""
import os,secrets,subprocess,sys,time,tempfile,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
run=secrets.token_hex(6)
schema=f'tallermeco_test_{run}'
migration=f'tallermeco_migration_test_{run}'
owner=f'tm_owner_{run}'
app=f'tm_app_{run}'
owner_password=secrets.token_hex(24)
app_password=secrets.token_hex(24)
def sql(text):
    subprocess.run(['podman','exec','-i','tallermeco-db','mariadb','--no-defaults','--user=root'],input=text.encode(),check=True,stdout=subprocess.DEVNULL)
env=os.environ.copy()
env.update(AUTH_TEST_URL=f'jdbc:mariadb://127.0.0.1:3306/{schema}',AUTH_TEST_USER=app,AUTH_TEST_PASSWORD=app_password,
           AUTH_TEST_OWNER=owner,AUTH_TEST_OWNER_PASSWORD=owner_password,MIGRATION_TEST_URL=f'jdbc:mariadb://127.0.0.1:3306/{migration}')
status=1
subprocess.run([str(root/'scripts/db-start.sh')],cwd=root,check=True)
try:
    sql(f"CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE `{migration}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER '{owner}'@'%' IDENTIFIED BY '{owner_password}'; CREATE USER '{app}'@'%' IDENTIFIED BY '{app_password}'; GRANT ALL PRIVILEGES ON `{schema}`.* TO '{owner}'@'%'; GRANT ALL PRIVILEGES ON `{migration}`.* TO '{owner}'@'%'; GRANT SELECT,INSERT,UPDATE,DELETE ON `{schema}`.* TO '{app}'@'%';")
    if '--preview' in sys.argv:
        with tempfile.TemporaryDirectory(prefix='tallermeco-ui-') as directory:
            env.update(SPRING_DATASOURCE_URL=env['AUTH_TEST_URL'],SPRING_DATASOURCE_USERNAME=app,SPRING_DATASOURCE_PASSWORD=app_password,
                       SPRING_FLYWAY_URL=env['AUTH_TEST_URL'],SPRING_FLYWAY_USER=owner,SPRING_FLYWAY_PASSWORD=owner_password,
                       APP_PORT='18081',ADMIN_INITIAL_PASSWORD='TallerMeco-ui-check-2026',
                       APP_CUSTOMER_PHOTO_DIR=directory+'/customers',APP_WORKSHOP_BANNER_DIR=directory+'/banners')
            log=root/'.local/customer-ui.log'
            with log.open('w') as output:
                process=subprocess.Popen(['java','-jar',str(root/'backend/target/tallermeco-0.1.0.jar')],cwd=root,env=env,stdout=output,stderr=subprocess.STDOUT)
                try:
                    for attempt in range(120):
                        if process.poll() is not None: raise RuntimeError(f'Preview failed; see {log}')
                        try:
                            urllib.request.urlopen('http://127.0.0.1:18081/api/auth/csrf',timeout=1).close()
                            break
                        except (OSError,urllib.error.URLError): time.sleep(.5)
                    else: raise RuntimeError('Preview did not become ready')
                    # Exercise the UI using the same table-specific write privileges as the application.
                    grants=[f"REVOKE ALL PRIVILEGES ON `{schema}`.* FROM '{app}'@'%';",f"GRANT SELECT ON `{schema}`.* TO '{app}'@'%';"]
                    for table,rights in {'customer_status':'INSERT,UPDATE,DELETE','vehicle_status':'INSERT,UPDATE,DELETE','app_user':'INSERT,UPDATE','user_role':'INSERT,DELETE','employee':'INSERT,UPDATE','customer':'INSERT,UPDATE','company':'INSERT','workshop':'INSERT,UPDATE','customer_workshop':'INSERT,UPDATE','user_workshop':'INSERT,UPDATE','audit_event':'INSERT','password_reset_token':'INSERT,UPDATE','vehicle':'INSERT,UPDATE','service_order':'INSERT,UPDATE','order_assignment':'INSERT,UPDATE','work_entry':'INSERT,UPDATE','part':'INSERT','inventory_movement':'INSERT','payment':'INSERT','order_status_history':'INSERT'}.items():
                        grants.append(f"GRANT {rights} ON `{schema}`.{table} TO '{app}'@'%';")
                    sql(' '.join(grants))
                    print('UI preview ready: http://127.0.0.1:18081/#/login',flush=True)
                    print('Disposable account: cameraadmin@tallermeco.local / TallerMeco-ui-check-2026',flush=True)
                    print('Ctrl-C removes the preview, database, users and uploaded images.',flush=True)
                    while process.poll() is None: time.sleep(.5)
                except KeyboardInterrupt: pass
                finally:
                    process.terminate()
                    try: process.wait(timeout=10)
                    except subprocess.TimeoutExpired: process.kill();process.wait()
        status=0
    else:
        status=subprocess.run([str(root/'.local/apache-maven-3.9.11/bin/mvn'),'-B','-f',str(root/'backend/pom.xml'),'test'],cwd=root,env=env).returncode
finally:
    sql(f"DROP DATABASE IF EXISTS `{schema}`; DROP DATABASE IF EXISTS `{migration}`; DROP USER IF EXISTS '{app}'@'%'; DROP USER IF EXISTS '{owner}'@'%';")
sys.exit(status)
