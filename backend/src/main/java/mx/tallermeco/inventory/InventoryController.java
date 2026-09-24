package mx.tallermeco.inventory;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
@RestController @RequestMapping("/api")
public class InventoryController {
 final InventoryService service; final Store db; final Actor actor; final Audit audit;
 public InventoryController(InventoryService service,Store db,Actor actor,Audit audit){this.service=service;this.db=db;this.actor=actor;this.audit=audit;}
 public record Part(@NotBlank @Size(max=60) String sku,@NotBlank @Size(max=160) String name,@NotBlank @Size(max=20) String unit,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=3) BigDecimal minimum,@NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal price){}
 public record Movement(@Positive long partId,Long orderId,@NotBlank String kind,@NotNull @Digits(integer=10,fraction=3) BigDecimal quantity,@DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal cost,Long reversalOf,@NotBlank @Size(max=500) String reason,@NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String requestKey){}
 @GetMapping("/parts") public List<Map<String,Object>> parts(){Store.require(!actor.is("CLIENT"),"Acceso restringido");return db.list("SELECT id,sku,name,unit,minimum_stock,stock,reference_price,active"+(actor.is("ADMIN")?",reference_cost":"")+" FROM part WHERE active=true ORDER BY name LIMIT 500");}
 @PostMapping("/parts") @Transactional public Map<String,Long> part(@Valid @RequestBody Part p){actor.admin();long id=db.id("INSERT INTO part(sku,name,unit,minimum_stock,reference_price) VALUES (?,?,?,?,?)",p.sku,p.name,p.unit,p.minimum,p.price);audit.record(actor.id(),"PART_CREATED","part",id,Map.of());return Map.of("id",id);}
 @PutMapping("/parts/{id}") @Transactional public void part(@PathVariable long id,@Valid @RequestBody Part p){actor.admin();var old=db.one("SELECT * FROM part WHERE id=? FOR UPDATE",id);db.update("UPDATE part SET sku=?,name=?,unit=?,minimum_stock=?,reference_price=? WHERE id=?",p.sku,p.name,p.unit,p.minimum,p.price,id);audit.record(actor.id(),"PART_UPDATED","part",id,Map.of("before",old,"price",p.price));}
 @GetMapping("/inventory/movements") public List<Map<String,Object>> movements(){actor.admin();return db.list("SELECT m.*,p.name,p.sku,u.email actor FROM inventory_movement m JOIN part p ON p.id=m.part_id JOIN app_user u ON u.id=m.actor_id ORDER BY m.id DESC LIMIT 300");}
 @PostMapping("/inventory/movements") public Map<String,Long> move(@Valid @RequestBody Movement m){return Map.of("id",service.move(m.partId,m.orderId,m.kind,m.quantity,m.cost,m.reversalOf,m.reason,m.requestKey));}
}
