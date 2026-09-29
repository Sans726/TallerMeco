#!/usr/bin/env python3
"""Run only P2-01 HTTP tests in an ephemeral MariaDB database. No downloads."""
import getpass
import os
import re
from pathlib import Path
import secrets
import subprocess
import sys
import uuid
import MySQLdb

root = Path(__file__).resolve().parents[1]
suffix = uuid.uuid4().hex[:12]
schema = 'tallermeco_auth_test_' + suffix
owner, runtime = 'auth_owner_' + suffix, 'auth_app_' + suffix
owner_password, app_password = secrets.token_hex(24), secrets.token_hex(24)
admin = MySQLdb.connect(unix_socket=str(root / '.local/mariadb.sock'), user=getpass.getuser())
cur = admin.cursor()
env = os.environ.copy()
env.update(AUTH_TEST_URL='jdbc:mariadb://127.0.0.1:3306/' + schema,
           AUTH_TEST_USER=runtime, AUTH_TEST_PASSWORD=app_password,
           AUTH_TEST_OWNER=owner, AUTH_TEST_OWNER_PASSWORD=owner_password)
maven = root / '.local/apache-maven-3.9.11/bin/mvn'
status = 1
try:
    cur.execute(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    cur.execute('CREATE USER %s@%s IDENTIFIED BY %s', (owner, 'localhost', owner_password))
    cur.execute('CREATE USER %s@%s IDENTIFIED BY %s', (runtime, 'localhost', app_password))
    cur.execute(f"GRANT ALL PRIVILEGES ON `{schema}`.* TO '{owner}'@'localhost'")
    cur.execute(f"GRANT SELECT,INSERT,UPDATE,DELETE ON `{schema}`.* TO '{runtime}'@'localhost'")
    print('P2-01: isolated authentication fixtures; existing tallermeco data is not modified.', flush=True)
    arguments = sys.argv[1:]
    if '--http-smoke' in arguments:
        arguments.remove('--http-smoke')
        # Resolve the existing test classpath without invoking or downloading Surefire.
        build = subprocess.run([str(maven), '-o', '-B', '-X', '-f', str(root / 'backend/pom.xml'),
                                *arguments, 'test-compile'], cwd=root, text=True, capture_output=True)
        if build.returncode:
            print('Offline test compilation failed:\n' + '\n'.join(build.stdout.splitlines()[-25:]))
            status = build.returncode
        else:
            match = re.search(r'\(f\) testPath = \[(.*?)\]', build.stdout)
            if not match:
                raise RuntimeError('Maven did not return its resolved test classpath')
            classpath = os.pathsep.join(match.group(1).split(', '))
            java = str(Path(env['JAVA_HOME']) / 'bin/java') if env.get('JAVA_HOME') else 'java'
            result = subprocess.run([java, '-cp', classpath,
                                     'mx.tallermeco.identity.AuthenticationIntegrationTest'], env=env, cwd=root)
            status = result.returncode
    else:
        result = subprocess.run([str(maven), '-o', '-B', '-f', str(root / 'backend/pom.xml'),
                                 '-Dtest=AuthenticationIntegrationTest', *arguments, 'test'], env=env, cwd=root)
        status = result.returncode
finally:
    cur.execute(f'DROP DATABASE IF EXISTS `{schema}`')
    cur.execute('DROP USER IF EXISTS %s@%s', (runtime, 'localhost'))
    cur.execute('DROP USER IF EXISTS %s@%s', (owner, 'localhost'))
    admin.close()
    print('P2-01: temporary database and authentication fixtures removed.', flush=True)
sys.exit(status)
