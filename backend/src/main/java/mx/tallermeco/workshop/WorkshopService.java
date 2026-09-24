package mx.tallermeco.workshop;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.math.BigDecimal;
@Service
public class WorkshopService {
 public final Store db; private final Actor actor; private final Audit audit;
 public WorkshopService(Store db,Actor actor,Audit audit){this.db=db;this.actor=actor;this.audit=audit;}
 public String scope(){return actor.is("ADMIN")?"1=1":actor.is("CLIENT")?"o.customer_id="+actor.customer():"EXISTS(SELECT 1 FROM order_assignment a WHERE a.order_id=o.id AND a.employee_id="+actor.employee()+")";}
 public Map<String,Object> order(long id,boolean lock){
  var o=db.one("SELECT o.* FROM service_order o WHERE o.id=? AND "+scope()+(lock?" FOR UPDATE":""),id);return o;
 }
 public void writable(Map<String,Object> o){Store.require(!Set.of("DELIVERED","CANCELLED").contains(o.get("status").toString()),"La orden está cerrada. Un administrador debe reabrirla.");}
 public void staff(){if(actor.is("CLIENT"))throw new AccessDeniedException("Solo personal");}
 public List<Map<String,Object>> orders(){return db.list("SELECT o.id,o.status,o.complaint,o.received_at,o.estimated_amount,o.version,c.full_name customer_name,v.make,v.model,v.license_plate FROM service_order o JOIN customer c ON c.id=o.customer_id JOIN vehicle v ON v.id=o.vehicle_id WHERE "+scope()+" ORDER BY o.id DESC LIMIT 500");}
 public Map<String,Object> totals(long id){return db.one("""
  SELECT COALESCE((SELECT SUM(sale_price) FROM work_entry WHERE order_id=? AND status<>'CANCELLED'),0)
  + COALESCE((SELECT SUM(-quantity*unit_price) FROM inventory_movement WHERE order_id=?),0) total,
  COALESCE((SELECT SUM(direct_cost) FROM work_entry WHERE order_id=? AND status<>'CANCELLED'),0)
  + COALESCE((SELECT SUM(-quantity*unit_cost) FROM inventory_movement WHERE order_id=?),0) direct_cost,
  COALESCE((SELECT SUM(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END) FROM payment WHERE order_id=?),0) paid
  """,id,id,id,id,id);}
 public Map<String,Object> detail(long id){
  var o=new LinkedHashMap<>(order(id,false));
  o.put("vehicle",db.one("SELECT id,make,model,model_year,license_plate,vin FROM vehicle WHERE id=?",o.get("vehicle_id")));
  o.put("customer",db.one("SELECT id,full_name FROM customer WHERE id=?",o.get("customer_id")));
  o.put("assignments",db.list("SELECT a.employee_id,e.full_name FROM order_assignment a JOIN employee e ON e.id=a.employee_id WHERE a.order_id=?",id));
  o.put("works",db.list("SELECT w.id,w.employee_id,w.description,w.status,w.started_at,w.finished_at,w.sale_price"+(actor.is("ADMIN")?",w.direct_cost":"")+",e.full_name mechanic FROM work_entry w JOIN employee e ON e.id=w.employee_id WHERE w.order_id=? ORDER BY w.id",id));
  o.put("parts",db.list("SELECT m.id,m.part_id,m.kind,m.quantity,m.unit_price,m.reason,m.created_at"+(actor.is("ADMIN")?",m.unit_cost":"")+",p.name,p.sku FROM inventory_movement m JOIN part p ON p.id=m.part_id WHERE m.order_id=? ORDER BY m.id",id));
  o.put("payments",db.list("SELECT id,kind,amount,method,reference,reason,created_at FROM payment WHERE order_id=? ORDER BY id",id));
  o.put("history",db.list("SELECT h.*,u.email actor FROM order_status_history h JOIN app_user u ON u.id=h.actor_id WHERE order_id=? ORDER BY h.id",id));
  if(actor.is("CLIENT")) { @SuppressWarnings("unchecked") var history=(List<Map<String,Object>>)o.get("history"); history.forEach(h->{h.remove("actor");h.remove("actor_id");}); }
  var t=new LinkedHashMap<>(totals(id)); if(!actor.is("ADMIN"))t.remove("direct_cost");o.put("totals",t);return o;
 }
 @Transactional public long create(long vehicleId,String complaint,Integer odometer){actor.admin();
  var v=db.one("SELECT * FROM vehicle WHERE id=? AND active=true",vehicleId);
  long id=db.id("INSERT INTO service_order(vehicle_id,customer_id,complaint,odometer_km,created_by) VALUES (?,?,?,?,?)",vehicleId,v.get("customer_id"),complaint,odometer,actor.id());
  db.update("INSERT INTO order_status_history(order_id,new_status,actor_id,reason) VALUES (?,'RECEIVED',?,'Recepción de vehículo')",id,actor.id());
  audit.record(actor.id(),"ORDER_CREATED","order",id,Map.of("vehicleId",vehicleId));return id;
 }
 @Transactional public void diagnosis(long id,String diagnosis,BigDecimal estimate,long version){actor.admin();var o=order(id,true);writable(o);
  Store.require(Store.number(o.get("version"))==version,"La orden cambió. Actualiza antes de guardar.");
  Store.require(Set.of("RECEIVED","DIAGNOSIS","AWAITING_APPROVAL").contains(o.get("status")),"Para modificar el presupuesto vuelve a solicitar autorización");
  db.update("UPDATE service_order SET diagnosis=?,estimated_amount=?,version=version+1 WHERE id=?",diagnosis,estimate,id);
  audit.record(actor.id(),"QUOTE_UPDATED","order",id,Map.of("before",o,"diagnosis",diagnosis,"estimate",estimate));
 }
 @Transactional public void assign(long id,long employee){actor.admin();var o=order(id,true);writable(o);
  db.one("SELECT e.id FROM employee e JOIN app_user u ON u.id=e.user_id JOIN user_role r ON r.user_id=u.id WHERE e.id=? AND e.active=true AND u.enabled=true AND r.role_code='MECHANIC'",employee);
  db.update("INSERT INTO order_assignment(order_id,employee_id,assigned_by) VALUES (?,?,?)",id,employee,actor.id());
  audit.record(actor.id(),"ORDER_ASSIGNED","order",id,Map.of("employeeId",employee));
 }
 @Transactional public void status(long id,String next,String reason,long version){staff();var o=order(id,true);String prev=o.get("status").toString();
  Store.require(Store.number(o.get("version"))==version,"La orden cambió. Actualiza antes de continuar.");
  Map<String,Set<String>> transitions=Map.of("RECEIVED",Set.of("DIAGNOSIS","CANCELLED"),"DIAGNOSIS",Set.of("AWAITING_APPROVAL","CANCELLED"),"AWAITING_APPROVAL",Set.of("IN_PROGRESS","DIAGNOSIS","CANCELLED"),"IN_PROGRESS",Set.of("READY","AWAITING_APPROVAL","CANCELLED"),"READY",Set.of("DELIVERED","IN_PROGRESS","CANCELLED"),"DELIVERED",Set.of("IN_PROGRESS"),"CANCELLED",Set.of());
  Store.require(transitions.get(prev).contains(next),"Transición de estado no permitida");
  if(!actor.is("ADMIN"))Store.require((prev.equals("RECEIVED")&&next.equals("DIAGNOSIS"))||(prev.equals("IN_PROGRESS")&&next.equals("READY")),"Este cambio requiere administrador");
  if(next.equals("AWAITING_APPROVAL"))Store.require(o.get("estimated_amount")!=null && o.get("diagnosis")!=null,"Registra diagnóstico y presupuesto");
  if(prev.equals("AWAITING_APPROVAL") && next.equals("IN_PROGRESS")){
   actor.admin();Store.require(o.get("estimated_amount")!=null,"Falta presupuesto");
   db.update("UPDATE service_order SET authorized_at=UTC_TIMESTAMP(6),authorization_method=?,authorized_by=? WHERE id=?",reason,actor.id(),id);
  }
  if(next.equals("READY")){
   Store.require(db.count("SELECT COUNT(*) FROM work_entry WHERE order_id=? AND status IN ('PENDING','IN_PROGRESS')",id)==0,"Hay trabajos pendientes");
   Store.require(db.count("SELECT COUNT(*) FROM work_entry WHERE order_id=? AND status='DONE'",id)>0,"Registra al menos un trabajo terminado");
   Store.require(o.get("estimated_amount")!=null && money(totals(id).get("total")).compareTo(money(o.get("estimated_amount")))<=0,"El total supera el presupuesto autorizado; solicita una nueva autorización");
  }
  if(next.equals("DELIVERED")){var t=totals(id);Store.require(money(t.get("paid")).compareTo(money(t.get("total")))==0,"Liquida el saldo antes de entregar");}
  if(next.equals("CANCELLED")){
   Store.require(db.count("SELECT COUNT(*) FROM (SELECT part_id FROM inventory_movement WHERE order_id=? GROUP BY part_id HAVING SUM(quantity)<>0) remaining",id)==0,"Devuelve las piezas consumidas antes de cancelar");
   Store.require(money(totals(id).get("paid")).signum()==0,"Reembolsa los cobros antes de cancelar");
   db.update("UPDATE work_entry SET status='CANCELLED' WHERE order_id=?",id);
  }
  db.update("UPDATE service_order SET status=?,closed_at="+(Set.of("DELIVERED","CANCELLED").contains(next)?"UTC_TIMESTAMP(6)":"NULL")+",version=version+1 WHERE id=?",next,id);
  db.update("INSERT INTO order_status_history(order_id,previous_status,new_status,actor_id,reason) VALUES (?,?,?,?,?)",id,prev,next,actor.id(),reason);
  audit.record(actor.id(),"ORDER_STATUS_CHANGED","order",id,Map.of("before",prev,"after",next,"reason",reason));
 }
 @Transactional public long work(long id,long employee,String description,BigDecimal cost,BigDecimal price){staff();var o=order(id,true);writable(o);
  Store.require(o.get("status").equals("IN_PROGRESS"),"La orden debe estar autorizada y en reparación");
  if(!actor.is("ADMIN")){employee=actor.employee();cost=BigDecimal.ZERO;price=BigDecimal.ZERO;}
  long w=db.id("INSERT INTO work_entry(order_id,employee_id,description,status,started_at,direct_cost,sale_price) VALUES (?,?,?,'IN_PROGRESS',UTC_TIMESTAMP(6),?,?)",id,employee,description,cost,price);
  audit.record(actor.id(),"WORK_CREATED","order",id,Map.of("workId",w));return w;
 }
 @Transactional public void workStatus(long orderId,long workId,String next){staff();var o=order(orderId,true);writable(o);
  Store.require(o.get("status").equals("IN_PROGRESS"),"La orden debe estar en reparación");
  var w=db.one("SELECT * FROM work_entry WHERE id=? AND order_id=?",workId,orderId);
  if(!actor.is("ADMIN"))Store.require(Store.number(w.get("employee_id"))==actor.employee(),"Solo puedes modificar tus trabajos");
  Store.require(Set.of("IN_PROGRESS","DONE","CANCELLED").contains(next),"Estado inválido");
  Store.require(!w.get("status").equals("CANCELLED"),"Trabajo cancelado");
  db.update("UPDATE work_entry SET status=?,finished_at="+(next.equals("DONE")?"UTC_TIMESTAMP(6)":"NULL")+" WHERE id=?",next,workId);
  audit.record(actor.id(),"WORK_STATUS_CHANGED","work",workId,Map.of("before",w.get("status"),"after",next));
 }
 @Transactional public void price(long orderId,long workId,BigDecimal cost,BigDecimal price){actor.admin();writable(order(orderId,true));
  var w=db.one("SELECT * FROM work_entry WHERE order_id=? AND id=?",orderId,workId);
  db.update("UPDATE work_entry SET direct_cost=?,sale_price=? WHERE id=?",cost,price,workId);
  audit.record(actor.id(),"WORK_PRICE_CHANGED","work",workId,Map.of("before",w,"cost",cost,"price",price));
 }
 @Transactional public void pay(long id,String kind,BigDecimal amount,String method,String reference,String reason,String requestKey){actor.admin();var o=order(id,true);
  Store.require(!o.get("status").equals("CANCELLED"),"La orden está cancelada");
  Store.require(Set.of("PAYMENT","REFUND").contains(kind)&&Set.of("CASH","CARD","TRANSFER").contains(method),"Tipo de cobro inválido");
  var t=totals(id);BigDecimal paid=money(t.get("paid"));
  if(kind.equals("REFUND"))Store.require(amount.compareTo(paid)<=0,"El reembolso supera lo cobrado");
  else {BigDecimal cap=money(t.get("total")).max(o.get("estimated_amount")==null?BigDecimal.ZERO:money(o.get("estimated_amount")));Store.require(paid.add(amount).compareTo(cap)<=0,"El pago supera el total o presupuesto");}
  long payment=db.id("INSERT INTO payment(order_id,kind,amount,method,reference,reason,actor_id,request_key) VALUES (?,?,?,?,?,?,?,?)",id,kind,amount,method,reference,reason,actor.id(),requestKey);
  audit.record(actor.id(),"PAYMENT_RECORDED","payment",payment,Map.of("orderId",id,"kind",kind,"amount",amount));
 }
 public static BigDecimal money(Object x){return new BigDecimal(x.toString());}
}
