package mx.tallermeco.shared;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class Audit {
 private final Store db; private final ObjectMapper json;
 public Audit(Store db,ObjectMapper json){this.db=db;this.json=json;}
 public void record(Long actor,String action,String entity,Long id,Object changes){
  try {db.update("INSERT INTO audit_event(actor_id,action,entity_type,entity_id,changes) VALUES (?,?,?,?,?)",actor,action,entity,id,json.writeValueAsString(changes));}
  catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Invalid audit data",e);}
 }
}
