package mx.tallermeco.inventory;
import mx.tallermeco.shared.*;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.workshop.WorkshopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
@Service
public class InventoryService {
 final Store db; final Actor actor; final Audit audit; final WorkshopService workshop;
 public InventoryService(Store db,Actor actor,Audit audit,WorkshopService workshop){this.db=db;this.actor=actor;this.audit=audit;this.workshop=workshop;}
 @Transactional public long move(long partId,Long orderId,String kind,BigDecimal quantity,BigDecimal unitCost,Long reversal,String reason,String requestKey){
  workshop.staff();Store.require(Set.of("RECEIPT","CONSUMPTION","RETURN","ADJUSTMENT").contains(kind),"Movimiento inválido");
  Store.require(quantity.signum()!=0,"Cantidad inválida");
  if(Set.of("RECEIPT","ADJUSTMENT").contains(kind)){actor.admin();Store.require(orderId==null,"Este movimiento no lleva orden");}
  else {Store.require(orderId!=null,"Selecciona una orden");var o=workshop.order(orderId,true);workshop.writable(o);if(kind.equals("CONSUMPTION"))Store.require(o.get("status").equals("IN_PROGRESS"),"La orden debe estar autorizada y en reparación");}
  var p=db.one("SELECT * FROM part WHERE id=? AND active=true FOR UPDATE",partId);
  BigDecimal cost=WorkshopService.money(p.get("reference_cost")),price=WorkshopService.money(p.get("reference_price")),signed=quantity;
  switch(kind){
   case "RECEIPT" -> {Store.require(quantity.signum()>0&&unitCost!=null&&unitCost.signum()>=0,"Entrada inválida");cost=unitCost;
    BigDecimal oldStock=WorkshopService.money(p.get("stock"));BigDecimal average=oldStock.multiply(WorkshopService.money(p.get("reference_cost"))).add(quantity.multiply(cost)).divide(oldStock.add(quantity),2,RoundingMode.HALF_UP);
    db.update("UPDATE part SET reference_cost=? WHERE id=?",average,partId);
   }
   case "CONSUMPTION" -> {Store.require(quantity.signum()>0,"Introduce una cantidad positiva");signed=quantity.negate();}
   case "RETURN" -> {
    Store.require(reversal!=null&&quantity.signum()>0,"Selecciona el consumo original");
    var m=db.one("SELECT * FROM inventory_movement WHERE id=? AND order_id=? AND part_id=? AND kind='CONSUMPTION'",reversal,orderId,partId);
    BigDecimal returned=WorkshopService.money(db.one("SELECT COALESCE(SUM(quantity),0) total FROM inventory_movement WHERE reversal_of=?",reversal).get("total"));
    Store.require(returned.add(quantity).compareTo(WorkshopService.money(m.get("quantity")).negate())<=0,"La devolución supera el consumo original");
    cost=WorkshopService.money(m.get("unit_cost"));price=WorkshopService.money(m.get("unit_price"));
    BigDecimal old=WorkshopService.money(p.get("stock"));
    db.update("UPDATE part SET reference_cost=? WHERE id=?",old.multiply(WorkshopService.money(p.get("reference_cost"))).add(quantity.multiply(cost)).divide(old.add(quantity),2,RoundingMode.HALF_UP),partId);
   }
   default -> {}
  }
  long id=db.id("INSERT INTO inventory_movement(part_id,order_id,kind,quantity,unit_cost,unit_price,reason,actor_id,reversal_of,request_key) VALUES (?,?,?,?,?,?,?,?,?,?)",partId,orderId,kind,signed,cost,price,reason,actor.id(),kind.equals("RETURN")?reversal:null,requestKey);
  audit.record(actor.id(),"INVENTORY_MOVEMENT","movement",id,Map.of("kind",kind,"quantity",signed,"partId",partId,"reason",reason));return id;
 }
}
