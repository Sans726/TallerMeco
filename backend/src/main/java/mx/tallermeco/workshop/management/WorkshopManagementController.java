package mx.tallermeco.workshop.management;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/workshops")
public class WorkshopManagementController {
 private final WorkshopManagementService service;private final WorkshopBannerService banners;
 public WorkshopManagementController(WorkshopManagementService s,WorkshopBannerService b){service=s;banners=b;}
 public record UserAccess(@jakarta.validation.constraints.NotNull Boolean active){}
 @GetMapping public Object list(@RequestParam(defaultValue="true") boolean activeOnly){return service.list(activeOnly);}
 @GetMapping("/{id}") public Object get(@PathVariable long id){return service.get(id);}
 @GetMapping("/companies") public Object companies(){return service.companies();}
 @PostMapping public Object create(@RequestBody CreateWorkshopRequest r){return service.create(r);}
 @PutMapping("/{id}") public Object update(@PathVariable long id,@jakarta.validation.Valid @RequestBody UpdateWorkshopRequest r){return service.update(id,r);}
 @GetMapping("/access-users") public Object receptionists(){return service.receptionists();}
 @GetMapping("/{id}/users") public Object users(@PathVariable long id){return service.users(id);}
 @PutMapping("/{id}/users/{user}") public void assign(@PathVariable long id,@PathVariable long user,@jakarta.validation.Valid @RequestBody UserAccess r){service.assign(id,user,r.active());}
 @PostMapping(value="/{id}/banner",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Map<String,String> banner(@PathVariable long id,@RequestPart("file") MultipartFile file){return Map.of("bannerReference",banners.store(id,file));}
 @GetMapping("/{id}/banner") public ResponseEntity<Resource> banner(@PathVariable long id){return banners.read(id);}
}
