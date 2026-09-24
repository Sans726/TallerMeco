package mx.tallermeco.workshop;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/api")
public class WorkshopController {
 final WorkshopService service; final Store db; final Actor actor; final Audit audit;
 public WorkshopController(WorkshopService service,Store db,Actor actor,Audit audit){this.service=service;this.db=db;this.actor=actor;this.audit=audit;}
 public record Customer(@NotBlank @Size(max=160) String name,@Size(max=30) String phone){}
 public record Vehicle(Long customerId,@NotBlank @Size(max=60) String make,@NotBlank @Size(max=80) String model,@Min(1900) @Max(2200) Integer year,@Size(max=80) String trim,@Size(max=20) String plate,@Pattern(regexp="^$|[A-HJ-NPR-Z0-9]{17}") String vin){}
 public record NewOrder(@Positive long vehicleId,@NotBlank @Size(max=3000) String complaint,@Min(0) Integer odometer){}
 public record Quote(@NotBlank @Size(max=5000) String diagnosis,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal estimate,long version){}
 public record Assignment(@Positive long employeeId){}
 public record Status(@NotBlank String status,@NotBlank @Size(max=80) String reason,long version){}
 public record Work(@Positive long employeeId,@NotBlank @Size(max=3000) String description,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal cost,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price){}
 public record WorkStatus(@NotBlank String status){}
 public record Price(@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal cost,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price){}
 public record Payment(@NotBlank String kind,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank String method,@Size(max=120) String reference,@NotBlank @Size(max=500) String reason,@NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String requestKey){}
 @GetMapping("/customers") public List<Map<String,Object>> customers(){actor.admin();return db.list("SELECT c.*,u.email FROM customer c LEFT JOIN app_user u ON u.id=c.user_id ORDER BY c.full_name LIMIT 500");}
 @PostMapping("/customers") @Transactional public Map<String,Long> customer(@Valid @RequestBody Customer r){actor.admin();long id=db.id("INSERT INTO customer(full_name,phone) VALUES (?,?)",r.name,r.phone);audit.record(actor.id(),"CUSTOMER_CREATED","customer",id,Map.of());return Map.of("id",id);}
 @PutMapping("/customers/{id}") @Transactional public void customer(@PathVariable long id,@Valid @RequestBody Customer r){actor.admin();var before=db.one("SELECT * FROM customer WHERE id=?",id);db.update("UPDATE customer SET full_name=?,phone=?,version=version+1 WHERE id=?",r.name,r.phone,id);audit.record(actor.id(),"CUSTOMER_UPDATED","customer",id,Map.of("before",before,"name",r.name));}
 @GetMapping("/vehicles") public List<Map<String,Object>> vehicles(){
  String where=actor.is("ADMIN")?"1=1":actor.is("CLIENT")?"v.customer_id="+actor.customer():"EXISTS(SELECT 1 FROM service_order o JOIN order_assignment a ON a.order_id=o.id WHERE o.vehicle_id=v.id AND a.employee_id="+actor.employee()+")";
  return db.list("SELECT v.*,c.full_name customer_name FROM vehicle v JOIN customer c ON c.id=v.customer_id WHERE "+where+" ORDER BY v.id DESC LIMIT 500");
 }
 @PostMapping("/vehicles") @Transactional public Map<String,Long> vehicle(@Valid @RequestBody Vehicle r){
  Store.require(actor.is("ADMIN")||actor.is("CLIENT"),"Acceso restringido");Long customer=actor.is("CLIENT")?actor.customer():r.customerId;Store.require(customer!=null,"Selecciona un cliente");
  long id=db.id("INSERT INTO vehicle(customer_id,make,model,model_year,trim_level,license_plate,vin) VALUES (?,?,?,?,?,?,?)",customer,r.make,r.model,r.year,r.trim,r.plate,r.vin==null||r.vin.isBlank()?null:r.vin);
  audit.record(actor.id(),"VEHICLE_CREATED","vehicle",id,Map.of());return Map.of("id",id);
 }
 @GetMapping("/orders") public List<Map<String,Object>> orders(){return service.orders();}
 @PostMapping("/orders") public Map<String,Long> create(@Valid @RequestBody NewOrder r){return Map.of("id",service.create(r.vehicleId,r.complaint,r.odometer));}
 @GetMapping("/orders/{id}") public Map<String,Object> detail(@PathVariable long id){return service.detail(id);}
 @PutMapping("/orders/{id}/quote") public void quote(@PathVariable long id,@Valid @RequestBody Quote r){service.diagnosis(id,r.diagnosis,r.estimate,r.version);}
 @PostMapping("/orders/{id}/assignments") public void assign(@PathVariable long id,@Valid @RequestBody Assignment r){service.assign(id,r.employeeId);}
 @PostMapping("/orders/{id}/status") public void status(@PathVariable long id,@Valid @RequestBody Status r){service.status(id,r.status,r.reason,r.version);}
 @PostMapping("/orders/{id}/works") public Map<String,Long> work(@PathVariable long id,@Valid @RequestBody Work r){return Map.of("id",service.work(id,r.employeeId,r.description,r.cost,r.price));}
 @PutMapping("/orders/{id}/works/{workId}/status") public void workStatus(@PathVariable long id,@PathVariable long workId,@Valid @RequestBody WorkStatus r){service.workStatus(id,workId,r.status);}
 @PutMapping("/orders/{id}/works/{workId}/price") public void price(@PathVariable long id,@PathVariable long workId,@Valid @RequestBody Price r){service.price(id,workId,r.cost,r.price);}
 @PostMapping("/orders/{id}/payments") public void pay(@PathVariable long id,@Valid @RequestBody Payment r){service.pay(id,r.kind,r.amount,r.method,r.reference,r.reason,r.requestKey);}
}
