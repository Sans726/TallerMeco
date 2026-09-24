import sys
from pathlib import Path
import MySQLdb
root = Path(__file__).resolve().parents[1]
config = dict(line.split('=', 1) for line in (root / '.env').read_text().splitlines() if '=' in line)
db = MySQLdb.connect(host='127.0.0.1', port=3306, user='taller_app', passwd=config['DB_APP_PASSWORD'], db='tallermeco')
c = db.cursor()
checks = 0

def rejected(sql, args, codes):
    global checks
    try:
        c.execute(sql, args)
    except MySQLdb.Error as error:
        assert error.args[0] in codes, error
        checks += 1
    else:
        raise AssertionError('Unexpectedly accepted: ' + sql)

try:
    c.execute("INSERT INTO app_user(email,password_hash) VALUES ('test@example.invalid','TEST_ONLY')")
    actor = c.lastrowid
    c.execute("INSERT INTO customer(full_name) VALUES ('Test')")
    customer = c.lastrowid
    c.execute("INSERT INTO vehicle(customer_id,make,model) VALUES (%s,'Test','Test')", (customer,))
    vehicle = c.lastrowid
    c.execute("INSERT INTO service_order(vehicle_id,customer_id,complaint,created_by) VALUES (%s,%s,'Test',%s)", (vehicle,customer,actor))
    order = c.lastrowid
    c.execute("INSERT INTO part(sku,name) VALUES ('TEST-PART','Test')")
    part = c.lastrowid
    c.execute("INSERT INTO inventory_movement(part_id,kind,quantity,unit_cost,reason,actor_id) VALUES (%s,'RECEIPT',5,100,'Test',%s)", (part,actor))
    consume = "INSERT INTO inventory_movement(part_id,order_id,kind,quantity,unit_cost,reason,actor_id) VALUES (%s,%s,'CONSUMPTION',%s,100,'Test',%s)"
    c.execute(consume, (part,order,-2,actor))
    c.execute('SELECT stock FROM part WHERE id=%s', (part,))
    assert c.fetchone()[0] == 3
    checks += 1
    rejected(consume, (part,order,-10,actor), {4025})
    c.execute('SELECT stock FROM part WHERE id=%s', (part,))
    assert c.fetchone()[0] == 3
    checks += 1
    rejected('UPDATE part SET stock=999 WHERE id=%s', (part,), {1142,1143})
    rejected('DELETE FROM inventory_movement WHERE part_id=%s', (part,), {1142})
    rejected("INSERT INTO app_user(email,password_hash) VALUES ('test@example.invalid','TEST_ONLY')", (), {1062})
    rejected("INSERT INTO payment(order_id,kind,amount,method,reason,actor_id) VALUES (%s,'PAYMENT',-1,'CASH','Test',%s)", (order,actor), {4025})
    rejected("INSERT INTO vehicle(customer_id,make,model) VALUES (9223372036854775807,'Test','Test')", (), {1452})
    c.execute('SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE()')
    assert c.fetchone()[0] >= 20
    checks += 1
    print(f'{checks} comprobaciones correctas: conexión, stock, restricciones y permisos.')
finally:
    db.rollback()
    db.close()
    print('Datos de prueba revertidos.')
