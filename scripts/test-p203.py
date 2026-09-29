#!/usr/bin/env python3
"""Run CustomerRepository checks against an isolated MariaDB schema."""
import getpass, os, re, secrets, subprocess, sys, uuid
from pathlib import Path
import MySQLdb

root = Path(__file__).resolve().parents[1]
socket = root / '.local/mariadb.sock'
schema = 'tallermeco_p203_' + uuid.uuid4().hex[:12]
owner, runtime = 'p203_owner_' + uuid.uuid4().hex[:8], 'p203_app_' + uuid.uuid4().hex[:8]
owner_password, app_password = secrets.token_hex(24), secrets.token_hex(24)
admin = MySQLdb.connect(unix_socket=str(socket), user=getpass.getuser())
cur = admin.cursor()
env = os.environ.copy()
env.update(CUSTOMER_TEST_URL=f'jdbc:mariadb://127.0.0.1:3306/{schema}', CUSTOMER_TEST_USER=runtime,
           CUSTOMER_TEST_PASSWORD=app_password, CUSTOMER_TEST_OWNER=owner,
           CUSTOMER_TEST_OWNER_PASSWORD=owner_password)
status = 1
try:
    cur.execute(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    cur.execute('CREATE USER %s@%s IDENTIFIED BY %s', (owner, 'localhost', owner_password))
    cur.execute('CREATE USER %s@%s IDENTIFIED BY %s', (runtime, 'localhost', app_password))
    cur.execute(f"GRANT ALL PRIVILEGES ON `{schema}`.* TO '{owner}'@'localhost'")
    cur.execute(f"GRANT SELECT,INSERT,UPDATE,DELETE ON `{schema}`.* TO '{runtime}'@'localhost'")
    admin.commit()
    maven = root / '.local/apache-maven-3.9.11/bin/mvn'
    build = subprocess.run([str(maven), '-o', '-B', '-X', '-f', str(root / 'backend/pom.xml'),
                            '-Dmaven.compiler.release=', '-Dmaven.compiler.source=21', '-Dmaven.compiler.target=21', 'test-compile'],
                           cwd=root, env=env, text=True, capture_output=True)
    if build.returncode:
        print(build.stdout[-4000:]); print(build.stderr[-1000:]); raise SystemExit(build.returncode)
    match = re.search(r'\(f\) testPath = \[(.*?)\]', build.stdout)
    if not match: raise RuntimeError('Maven did not return test classpath')
    classpath = os.pathsep.join(match.group(1).split(', '))
    java = os.environ.get('JAVA_HOME', '') + '/bin/java' if os.environ.get('JAVA_HOME') else 'java'
    result = subprocess.run([java, '-ea', '-cp', classpath, 'mx.tallermeco.customer.CustomerRepositoryIntegrationTest'], cwd=root, env=env)
    status = result.returncode
finally:
    cur.execute(f'DROP DATABASE IF EXISTS `{schema}`')
    cur.execute('DROP USER IF EXISTS %s@%s', (runtime, 'localhost'))
    cur.execute('DROP USER IF EXISTS %s@%s', (owner, 'localhost'))
    admin.commit(); admin.close()
sys.exit(status)
