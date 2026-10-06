package mx.tallermeco.workshop.management;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.util.*;

@Repository
public class WorkshopRepository {
 private final JdbcTemplate jdbc;
 public WorkshopRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 private static final String COLUMNS="w.id,w.company_id companyId,w.name,w.legal_name legalName,w.rfc,w.phone,w.email,w.street,w.neighborhood,w.municipality,w.state,w.postal_code postalCode,w.banner_reference bannerReference,w.active,w.version";
 public boolean accessible(long id,long user,boolean admin,boolean active,boolean lock){
  String from=admin?" FROM workshop w JOIN company c ON c.id=w.company_id":" FROM workshop w JOIN company c ON c.id=w.company_id JOIN user_workshop uw ON uw.workshop_id=w.id";
  String scope=admin?"":" AND uw.user_id=? AND uw.active=true";
  String sql="SELECT w.id"+from+" WHERE w.id=? AND (? OR (w.active=true AND c.active=true))"+scope+(lock?" FOR UPDATE":"");
  return !jdbc.queryForList(sql,admin?new Object[]{id,!active}:new Object[]{id,!active,user}).isEmpty();
 }
 public List<Map<String,Object>> list(long user,boolean admin,boolean activeOnly){return jdbc.queryForList("SELECT "+COLUMNS+" FROM workshop w JOIN company c ON c.id=w.company_id WHERE (? OR (w.active=true AND c.active=true)) AND (? OR EXISTS(SELECT 1 FROM user_workshop uw WHERE uw.workshop_id=w.id AND uw.user_id=? AND uw.active=true)) ORDER BY w.name,w.id",!activeOnly,admin,user);}
 public Map<String,Object> get(long id,boolean lock){var rows=jdbc.queryForList("SELECT "+COLUMNS+" FROM workshop w WHERE w.id=?"+(lock?" FOR UPDATE":""),id);return rows.isEmpty()?Map.of():rows.getFirst();}
 public List<Map<String,Object>> companies(){return jdbc.queryForList("SELECT id,name FROM company WHERE active=true ORDER BY name,id");}
 public boolean companyActive(long id){return !jdbc.queryForList("SELECT id FROM company WHERE id=? AND active=true",id).isEmpty();}
 public long company(String name){return insert("INSERT INTO company(name) VALUES (?)",name);}
 public long create(long company,WorkshopData w){return insert("INSERT INTO workshop(company_id,name,legal_name,rfc,phone,email,street,neighborhood,municipality,state,postal_code) VALUES (?,?,?,?,?,?,?,?,?,?,?)",company,w.name(),w.legalName(),w.rfc(),w.phone(),w.email(),w.street(),w.neighborhood(),w.municipality(),w.state(),w.postalCode());}
 public void update(long id,WorkshopData w,boolean active){jdbc.update("UPDATE workshop SET name=?,legal_name=?,rfc=?,phone=?,email=?,street=?,neighborhood=?,municipality=?,state=?,postal_code=?,active=?,version=version+1 WHERE id=?",w.name(),w.legalName(),w.rfc(),w.phone(),w.email(),w.street(),w.neighborhood(),w.municipality(),w.state(),w.postalCode(),active,id);}
 public void banner(long id,String reference){jdbc.update("UPDATE workshop SET banner_reference=?,version=version+1 WHERE id=?",reference,id);}
 public List<Map<String,Object>> receptionists(){return jdbc.queryForList("SELECT u.id,u.email,COALESCE(u.display_name,e.full_name,u.email) name FROM app_user u LEFT JOIN employee e ON e.user_id=u.id WHERE u.enabled=true AND EXISTS(SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_code='RECEPTIONIST') ORDER BY u.email");}
 public List<Map<String,Object>> users(long workshop){return jdbc.queryForList("SELECT u.id,u.email,uw.active FROM user_workshop uw JOIN app_user u ON u.id=uw.user_id WHERE uw.workshop_id=? ORDER BY u.email",workshop);}
 public boolean receptionist(long id){return !jdbc.queryForList("SELECT u.id FROM app_user u WHERE u.id=? AND u.enabled=true AND EXISTS(SELECT 1 FROM user_role ur WHERE ur.user_id=u.id AND ur.role_code='RECEPTIONIST')",id).isEmpty();}
 public void assign(long workshop,long user,boolean active){jdbc.update("INSERT INTO user_workshop(user_id,workshop_id,active) VALUES (?,?,?) ON DUPLICATE KEY UPDATE active=VALUES(active)",user,workshop,active);}
 private long insert(String sql,Object...values){var keys=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);return s;},keys);return Objects.requireNonNull(keys.getKey()).longValue();}
}
