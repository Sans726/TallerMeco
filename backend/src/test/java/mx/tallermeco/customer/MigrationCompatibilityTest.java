package mx.tallermeco.customer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
class MigrationCompatibilityTest {
 @Test void upgradesV5WithExistingDataWithoutInventingIdentityOrScopes()throws Exception{
  String url=System.getenv("MIGRATION_TEST_URL"),user=System.getenv("AUTH_TEST_OWNER"),password=System.getenv("AUTH_TEST_OWNER_PASSWORD");
  Flyway.configure().dataSource(url,user,password).target("5").load().migrate();
  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.executeUpdate("INSERT INTO company(id,name) VALUES (1,'Empresa anterior')");s.executeUpdate("INSERT INTO workshop(id,company_id,name) VALUES (1,1,'Taller anterior')");
   s.executeUpdate("INSERT INTO customer(id,full_name,birth_date,personal_email,personal_phone,work_phone) VALUES (1,'  María   Muñoz  ','1990-02-03','Maria@Example.com','+52 55 1234 5678','5512345678')");
   s.executeUpdate("INSERT INTO customer_workshop(customer_id,workshop_id) VALUES (1,1)");
   s.executeUpdate("INSERT INTO app_user(id,email,password_hash) VALUES (1,'legacy@example.com','not-a-login-hash')");s.executeUpdate("INSERT INTO customer(id,user_id,full_name,phone) VALUES (2,1,'Cuenta anterior','5598765432')");
  }
  Flyway.configure().dataSource(url,user,password).target("6").load().migrate();
  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   s.executeUpdate("UPDATE customer SET curp='gode900203hdfmnn01',rfc='gode900203abc',active=false WHERE id=1");
   s.executeUpdate("UPDATE workshop SET rfc='abc900203a12' WHERE id=1");
   s.executeUpdate("INSERT INTO vehicle(id,customer_id,make,model,model_year,vin,license_plate,active) VALUES (1,1,'Honda','Accord',2003,'1hgcm82633a004352','abc-123',false),(2,2,'Toyota','Anterior',1999,NULL,NULL,true)");
   s.executeUpdate("INSERT INTO service_order(id,vehicle_id,customer_id,complaint,created_by) VALUES (1,1,1,'Historial anterior',1)");
   s.executeUpdate("INSERT INTO order_status_history(order_id,new_status,actor_id,reason) VALUES (1,'RECEIVED',1,'Ingreso anterior')");
   s.executeUpdate("INSERT INTO audit_event(actor_id,action,entity_type,entity_id) VALUES (1,'LEGACY_EVENT','vehicle',1)");
  }
  var flyway=Flyway.configure().dataSource(url,user,password).load();assertEquals(1,flyway.migrate().migrationsExecuted);assertTrue(flyway.validateWithResult().validationSuccessful);
  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   try(var rs=s.executeQuery("SELECT full_name,given_name FROM customer WHERE id=1")){assertTrue(rs.next());assertEquals("maría muñoz",rs.getString(1));assertNull(rs.getString(2));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM customer")){rs.next();assertEquals(2,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM customer_workshop WHERE active=true")){rs.next();assertEquals(1,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM user_workshop")){rs.next();assertEquals(0,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT customer_id FROM customer_contact WHERE value='5512345678'")){assertTrue(rs.next());assertEquals(1,rs.getLong(1));assertFalse(rs.next());}
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO customer(full_name,cell_phone) VALUES ('Duplicado','5512345678')"));
   try(var rs=s.executeQuery("SELECT c.curp,c.rfc,s.code,s.allows_operations FROM customer c JOIN customer_status s ON s.id=c.status_id WHERE c.id=1")){assertTrue(rs.next());assertEquals("GODE900203HDFMNN01",rs.getString(1));assertEquals("GODE900203ABC",rs.getString(2));assertEquals("SUSPENDED",rs.getString(3));assertFalse(rs.getBoolean(4));}
   try(var rs=s.executeQuery("SELECT rfc FROM workshop WHERE id=1")){rs.next();assertEquals("ABC900203A12",rs.getString(1));}
   try(var rs=s.executeQuery("SELECT v.id,v.vin,v.color,s.code FROM vehicle v JOIN vehicle_status s ON s.id=v.status_id ORDER BY v.id")){assertTrue(rs.next());assertEquals(1,rs.getLong(1));assertEquals("1HGCM82633A004352",rs.getString(2));assertNull(rs.getString(3));assertEquals("SUSPENDED",rs.getString(4));assertTrue(rs.next());assertNull(rs.getString(2));assertEquals("ACTIVE",rs.getString(4));assertFalse(rs.next());}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM order_status_history WHERE order_id=1")){rs.next();assertEquals(1,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM audit_event WHERE action='LEGACY_EVENT'")){rs.next();assertEquals(1,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('customer','vehicle') AND column_name='active'")){rs.next();assertEquals(0,rs.getInt(1));}
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("UPDATE customer SET curp='gode900203hdfmnn01' WHERE id=1"));
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("UPDATE workshop SET rfc='abc900203a12' WHERE id=1"));
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO vehicle(customer_id,make,model,vin) VALUES (2,'Honda','Duplicado','1HGCM82633A004352')"));
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO customer(full_name,curp) VALUES ('Duplicado','GODE900203HDFMNN01')"));
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("DELETE FROM customer_status WHERE code='ACTIVE'"));
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("UPDATE vehicle_status SET allows_operations=false WHERE code='ACTIVE'"));
   // A legacy insert also goes through the DB uniqueness registry.
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO customer(full_name,phone) VALUES ('Duplicado','55 9876 5432')"));
  }
 }
}
