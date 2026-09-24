package mx.tallermeco.shared;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;
import java.util.*;
@Component
public class Store {
 public final JdbcTemplate jdbc;
 public Store(JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public List<Map<String,Object>> list(String sql,Object... args) { return jdbc.queryForList(sql,args); }
 public Map<String,Object> one(String sql,Object... args) {
  var rows=list(sql,args); if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro no encontrado"); return rows.getFirst();
 }
 public long id(String sql,Object... args) {
  var holder=new GeneratedKeyHolder();
  jdbc.update(c->{var p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS); for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]); return p;},holder);
  return Objects.requireNonNull(holder.getKey()).longValue();
 }
 public int update(String sql,Object... args) { return jdbc.update(sql,args); }
 public long count(String sql,Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args)); }
 public static long number(Object v) { return ((Number)v).longValue(); }
 public static void require(boolean condition,String message) { if(!condition) throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
