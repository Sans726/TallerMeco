package mx.tallermeco.identity;
import mx.tallermeco.shared.Store;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
@Component
public class Actor {
 private final Store db;
 public Actor(Store db){this.db=db;}
 public Map<String,Object> user(){return db.one("SELECT id,email,version FROM app_user WHERE email=? AND enabled=true",SecurityContextHolder.getContext().getAuthentication().getName());}
 public long id(){return Store.number(user().get("id"));}
 public boolean is(String role){return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}
 public void admin(){if(!is("ADMIN"))throw new AccessDeniedException("Administrador requerido");}
 public long customer(){return Store.number(db.one("SELECT id FROM customer WHERE user_id=?",id()).get("id"));}
 public long employee(){return Store.number(db.one("SELECT id FROM employee WHERE user_id=? AND active=true",id()).get("id"));}
 public Map<String,Object> profile(){
  var u=new LinkedHashMap<>(user()); u.remove("version");
  var roles=List.of("ADMIN","RECEPTIONIST","MECHANIC","CLIENT").stream().filter(this::is).toList();
  if(roles.isEmpty())throw new AccessDeniedException("La cuenta no tiene un rol admitido");
  u.put("role",roles.getFirst());
  u.put("roles",roles);
  var names=db.list("SELECT full_name,phone FROM customer WHERE user_id=? UNION ALL SELECT full_name,NULL FROM employee WHERE user_id=?",id(),id());
  u.put("name",names.isEmpty()?u.get("email"):names.getFirst().get("full_name"));
  u.put("phone",names.isEmpty()?"":names.getFirst().get("phone")); return u;
 }
}
