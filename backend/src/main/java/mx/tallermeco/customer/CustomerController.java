package mx.tallermeco.customer;
import mx.tallermeco.customer.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import java.util.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
 private final CustomerService service;private final CustomerPhotoService photos;
 public CustomerController(CustomerService service,CustomerPhotoService photos){this.service=service;this.photos=photos;}
 public record StatusRequest(long workshopId,@jakarta.validation.constraints.NotNull Long statusId,@jakarta.validation.constraints.NotNull Long version){}
 public record AssociationRequest(long workshopId,long targetWorkshopId,String mode){}
 public record InitialWorkshopRequest(long workshopId){}
 public record AssociationStatus(long workshopId,@jakarta.validation.constraints.NotNull Boolean active){}
 @GetMapping public CustomerPage list(@RequestParam long workshopId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="ASC") String direction,@RequestParam(defaultValue="") String query,@RequestParam(required=false) Long statusId){return service.list(new CustomerListQuery(workshopId,page,pageSize,sort,direction,query,statusId));}
 @GetMapping("/unassigned") public CustomerPage unassigned(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(defaultValue="ASC") String direction,@RequestParam(defaultValue="") String query){return service.unassigned(page,pageSize,direction,query);}
 @PostMapping("/{id}/initial-workshop") public void initialWorkshop(@PathVariable long id,@RequestBody InitialWorkshopRequest request){service.initialWorkshop(id,request.workshopId());}
 @GetMapping("/{id}") public CustomerResponse get(@PathVariable long id,@RequestParam long workshopId){return service.find(id,workshopId);}
 @PostMapping public CustomerResponse create(@RequestBody CreateCustomerRequest request){return service.create(request.customer(),request.workshopId());}
 @PutMapping("/{id}") public CustomerResponse update(@PathVariable long id,@RequestBody UpdateCustomerRequest request){return service.update(id,request);}
 @PatchMapping("/{id}/status") public CustomerResponse status(@PathVariable long id,@jakarta.validation.Valid @RequestBody StatusRequest request){return service.status(id,request.workshopId(),request.statusId(),request.version());}
 @GetMapping("/{id}/workshops") public Object workshops(@PathVariable long id,@RequestParam long workshopId){return service.workshops(id,workshopId);}
 @PostMapping("/{id}/workshops") public void associate(@PathVariable long id,@RequestBody AssociationRequest r){service.associate(id,r.workshopId(),r.targetWorkshopId(),r.mode());}
 @PatchMapping("/{id}/workshops/{target}") public void associationStatus(@PathVariable long id,@PathVariable long target,@jakarta.validation.Valid @RequestBody AssociationStatus r){service.associationActive(id,r.workshopId(),target,r.active());}
 @PostMapping(value="/{id}/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Map<String,String> photo(@PathVariable long id,@RequestParam long workshopId,@RequestPart("file") MultipartFile file){return Map.of("photoReference",photos.store(id,workshopId,file));}
 @GetMapping("/photos/{reference}") public ResponseEntity<Resource> photo(@PathVariable String reference,@RequestParam long workshopId){return photos.read(reference,workshopId);}
}
