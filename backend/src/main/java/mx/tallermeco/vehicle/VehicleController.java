package mx.tallermeco.vehicle;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
 private final VehicleService service;public VehicleController(VehicleService service){this.service=service;}
 public record Create(long customerId,long workshopId,VehicleData vehicle){}
 public record Update(long workshopId,Long version,VehicleData vehicle){}
 public record Status(long workshopId,Long version,Long statusId){}
 @GetMapping public Object list(@RequestParam long workshopId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(defaultValue="make") String sort,@RequestParam(defaultValue="ASC") String direction,@RequestParam(defaultValue="") String query,@RequestParam(required=false) Long statusId){return service.list(workshopId,page,pageSize,sort,direction,query,statusId);}
 @GetMapping("/{id}") public Object get(@PathVariable long id,@RequestParam long workshopId){return service.get(id,workshopId);}
 @PostMapping public Object create(@RequestBody Create r){return service.create(r.customerId(),r.workshopId(),r.vehicle());}
 @PutMapping("/{id}") public Object update(@PathVariable long id,@RequestBody Update r){return service.update(id,r.workshopId(),r.version(),r.vehicle());}
 @PatchMapping("/{id}/status") public Object status(@PathVariable long id,@RequestBody Status r){return service.status(id,r.workshopId(),r.version(),r.statusId());}
}
