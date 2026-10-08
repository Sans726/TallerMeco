package mx.tallermeco.status;
import org.springframework.web.bind.annotation.*;
import static mx.tallermeco.status.StatusRepository.Kind;
@RestController
@RequestMapping("/api/statuses/{kind}")
public class StatusController {
 private final StatusService service;public StatusController(StatusService service){this.service=service;}
 @GetMapping public Object list(@PathVariable String kind){return service.list(Kind.parse(kind));}
 @PostMapping public Object create(@PathVariable String kind,@RequestBody StatusService.Data data){return service.create(Kind.parse(kind),data);}
 @PutMapping("/{id}") public Object update(@PathVariable String kind,@PathVariable long id,@RequestBody StatusService.Data data){return service.update(Kind.parse(kind),id,data);}
 @DeleteMapping("/{id}") public void delete(@PathVariable String kind,@PathVariable long id,@RequestParam long version){service.delete(Kind.parse(kind),id,version);}
}
