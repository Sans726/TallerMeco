package mx.tallermeco.status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.util.*;

@Repository
public class StatusRepository {
 public enum Kind {CUSTOMER("customer_status","customer"),VEHICLE("vehicle_status","vehicle");
  final String table,entity;Kind(String table,String entity){this.table=table;this.entity=entity;}
  public static Kind parse(String raw){return switch(raw){case "customers"->CUSTOMER;case "vehicles"->VEHICLE;default->throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Catálogo no encontrado");};}
 }
 public record Status(long id,String code,String description,boolean allowsOperations,boolean system,long version,long usageCount){}
 private final JdbcTemplate jdbc;
 public StatusRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private String select(Kind k){return "SELECT s.*,(SELECT COUNT(*) FROM "+k.entity+" e WHERE e.status_id=s.id) usage_count FROM "+k.table+" s";}
 public List<Status> list(Kind k){return jdbc.query(select(k)+" ORDER BY s.system DESC,s.code",(r,n)->new Status(r.getLong("id"),r.getString("code"),r.getString("description"),r.getBoolean("allows_operations"),r.getBoolean("system"),r.getLong("version"),r.getLong("usage_count")));}
 public Status get(Kind k,long id,boolean lock){return jdbc.query(select(k)+" WHERE s.id=?"+(lock?" FOR UPDATE":""),(r,n)->new Status(r.getLong("id"),r.getString("code"),r.getString("description"),r.getBoolean("allows_operations"),r.getBoolean("system"),r.getLong("version"),r.getLong("usage_count")),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Estatus no encontrado"));}
 public Status code(Kind k,String code){Long id=jdbc.queryForObject("SELECT id FROM "+k.table+" WHERE code=?",Long.class,code);return get(k,Objects.requireNonNull(id),true);}
 public long create(Kind k,String code,String description,boolean allows){var keys=new GeneratedKeyHolder();jdbc.update(c->{var p=c.prepareStatement("INSERT INTO "+k.table+"(code,description,allows_operations) VALUES (?,?,?)",Statement.RETURN_GENERATED_KEYS);p.setString(1,code);p.setString(2,description);p.setBoolean(3,allows);return p;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
 public void update(Kind k,long id,String code,String description,boolean allows){jdbc.update("UPDATE "+k.table+" SET code=?,description=?,allows_operations=?,version=version+1 WHERE id=?",code,description,allows,id);}
 public void delete(Kind k,long id){jdbc.update("DELETE FROM "+k.table+" WHERE id=?",id);}
}
