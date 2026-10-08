package mx.tallermeco.vehicle;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/self/vehicles")
public class PersonalVehicleController {
 private final VehicleService service;public PersonalVehicleController(VehicleService service){this.service=service;}
 public record Create(long workshopId,VehicleData vehicle){}
 @GetMapping public Object list(){return service.personal();}
 @GetMapping("/workshops") public Object workshops(){return service.personalWorkshops();}
 @PostMapping public Object create(@RequestBody Create r){return Map.of("id",service.createPersonal(r.workshopId(),r.vehicle()));}
}
