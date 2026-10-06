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
  var flyway=Flyway.configure().dataSource(url,user,password).load();assertEquals(1,flyway.migrate().migrationsExecuted);assertTrue(flyway.validateWithResult().validationSuccessful);
  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){
   try(var rs=s.executeQuery("SELECT full_name,given_name FROM customer WHERE id=1")){assertTrue(rs.next());assertEquals("maría muñoz",rs.getString(1));assertNull(rs.getString(2));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM customer")){rs.next();assertEquals(2,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM customer_workshop WHERE active=true")){rs.next();assertEquals(1,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT COUNT(*) FROM user_workshop")){rs.next();assertEquals(0,rs.getInt(1));}
   try(var rs=s.executeQuery("SELECT customer_id FROM customer_contact WHERE value='5512345678'")){assertTrue(rs.next());assertEquals(1,rs.getLong(1));assertFalse(rs.next());}
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO customer(full_name,cell_phone) VALUES ('Duplicado','5512345678')"));
   // A legacy insert also goes through the DB uniqueness registry.
   assertThrows(java.sql.SQLException.class,()->s.executeUpdate("INSERT INTO customer(full_name,phone) VALUES ('Duplicado','55 9876 5432')"));
  }
 }
}
