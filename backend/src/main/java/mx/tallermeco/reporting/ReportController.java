package mx.tallermeco.reporting;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.Store;
import mx.tallermeco.workshop.WorkshopService;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ReportController {
 final Store db; final Actor actor; final WorkshopService workshop;
 public ReportController(Store db,Actor actor,WorkshopService workshop){this.db=db;this.actor=actor;this.workshop=workshop;}
 @GetMapping("/dashboard") public Map<String,Object> dashboard(){
  var orders=workshop.orders();var data=new LinkedHashMap<String,Object>();
  data.put("active",orders.stream().filter(o->!Set.of("DELIVERED","CANCELLED").contains(o.get("status"))).count());
  data.put("ready",orders.stream().filter(o->o.get("status").equals("READY")).count());
  data.put("completed",orders.stream().filter(o->o.get("status").equals("DELIVERED")).count());
  data.put("orders",orders.stream().limit(6).toList());
  if(actor.is("ADMIN")){data.put("lowStock",db.list("SELECT id,sku,name,stock,minimum_stock FROM part WHERE active=true AND stock<=minimum_stock ORDER BY stock LIMIT 8"));data.put("todayPayments",db.one("SELECT COALESCE(SUM(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END),0) total FROM payment WHERE DATE(CONVERT_TZ(created_at,'+00:00','-06:00'))=DATE(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','-06:00'))").get("total"));}
  return data;
 }
 @GetMapping("/reports") public Map<String,Object> reports(@RequestParam LocalDate from,@RequestParam LocalDate to){actor.admin();Store.require(!to.isBefore(from)&&!to.isAfter(from.plusYears(2)),"Selecciona un periodo de hasta dos años");
  var zone=ZoneId.of("America/Mexico_City");var start=java.sql.Timestamp.from(from.atStartOfDay(zone).toInstant());var end=java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
  var orders=db.list("SELECT o.id,o.closed_at,c.full_name customer_name,v.license_plate FROM service_order o JOIN customer c ON c.id=o.customer_id JOIN vehicle v ON v.id=o.vehicle_id WHERE o.status='DELIVERED' AND o.closed_at>=? AND o.closed_at<? ORDER BY o.closed_at DESC",start,end);
  java.math.BigDecimal revenue=java.math.BigDecimal.ZERO,cost=java.math.BigDecimal.ZERO;
  for(var o:orders){var totals=workshop.totals(Store.number(o.get("id")));o.putAll(totals);revenue=revenue.add(WorkshopService.money(totals.get("total")));cost=cost.add(WorkshopService.money(totals.get("direct_cost")));}
  var cash=db.one("SELECT COALESCE(SUM(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END),0) total FROM payment WHERE created_at>=? AND created_at<?",start,end).get("total");
  var workload=db.list("SELECT e.id,e.full_name,COUNT(DISTINCT CASE WHEN o.status IN ('RECEIVED','DIAGNOSIS','AWAITING_APPROVAL','IN_PROGRESS','READY') THEN a.order_id END) active_orders FROM employee e LEFT JOIN order_assignment a ON a.employee_id=e.id LEFT JOIN service_order o ON o.id=a.order_id GROUP BY e.id,e.full_name");
  var works=db.list("SELECT e.full_name,COUNT(*) completed FROM work_entry w JOIN employee e ON e.id=w.employee_id WHERE w.status='DONE' AND w.finished_at>=? AND w.finished_at<? GROUP BY e.id,e.full_name",start,end);
  return Map.of("orders",orders,"revenue",revenue,"cost",cost,"margin",revenue.subtract(cost),"payments",cash,"workload",workload,"works",works);
 }
 @GetMapping("/audit") public List<Map<String,Object>> audit(){actor.admin();return db.list("SELECT a.id,a.action,a.entity_type,a.entity_id,a.changes,a.created_at,u.email actor FROM audit_event a LEFT JOIN app_user u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT 300");}
}
