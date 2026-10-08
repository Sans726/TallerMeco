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
 public record NewOrder(@Positive long vehicleId,@Positive long workshopId,@NotBlank @Size(max=3000) String complaint,@Min(0) Integer odometer){}
 public record Quote(@NotBlank @Size(max=5000) String diagnosis,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal estimate,long version){}
 public record Assignment(@Positive long employeeId){}
 public record Status(@NotBlank String status,@NotBlank @Size(max=80) String reason,long version){}
 public record Work(@Positive long employeeId,@NotBlank @Size(max=3000) String description,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal cost,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price){}
 public record WorkStatus(@NotBlank String status){}
 public record Price(@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal cost,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price){}
 public record Payment(@NotBlank String kind,@NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@NotBlank String method,@Size(max=120) String reference,@NotBlank @Size(max=500) String reason,@NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String requestKey){}
 @GetMapping("/orders") public List<Map<String,Object>> orders(){return service.orders();}
 @PostMapping("/orders") public Map<String,Long> create(@Valid @RequestBody NewOrder r){return Map.of("id",service.create(r.vehicleId,r.workshopId,r.complaint,r.odometer));}
 @GetMapping("/orders/{id}") public Map<String,Object> detail(@PathVariable long id){return service.detail(id);}
 @PutMapping("/orders/{id}/quote") public void quote(@PathVariable long id,@Valid @RequestBody Quote r){service.diagnosis(id,r.diagnosis,r.estimate,r.version);}
 @PostMapping("/orders/{id}/assignments") public void assign(@PathVariable long id,@Valid @RequestBody Assignment r){service.assign(id,r.employeeId);}
 @PostMapping("/orders/{id}/status") public void status(@PathVariable long id,@Valid @RequestBody Status r){service.status(id,r.status,r.reason,r.version);}
 @PostMapping("/orders/{id}/works") public Map<String,Long> work(@PathVariable long id,@Valid @RequestBody Work r){return Map.of("id",service.work(id,r.employeeId,r.description,r.cost,r.price));}
 @PutMapping("/orders/{id}/works/{workId}/status") public void workStatus(@PathVariable long id,@PathVariable long workId,@Valid @RequestBody WorkStatus r){service.workStatus(id,workId,r.status);}
 @PutMapping("/orders/{id}/works/{workId}/price") public void price(@PathVariable long id,@PathVariable long workId,@Valid @RequestBody Price r){service.price(id,workId,r.cost,r.price);}
 @PostMapping("/orders/{id}/payments") public void pay(@PathVariable long id,@Valid @RequestBody Payment r){service.pay(id,r.kind,r.amount,r.method,r.reference,r.reason,r.requestKey);}
}
