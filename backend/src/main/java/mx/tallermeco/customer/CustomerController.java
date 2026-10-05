package mx.tallermeco.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import mx.tallermeco.customer.dto.CreateCustomerRequest;
import mx.tallermeco.customer.dto.CustomerResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;
    private final JdbcTemplate jdbc;
    private final CustomerPhotoService photos;
    public CustomerController(CustomerService service, JdbcTemplate jdbc, CustomerPhotoService photos) { this.service = service; this.jdbc = jdbc; this.photos = photos; }
    public record CreateRequest(@NotNull CreateCustomerRequest customer, @Positive long workshopId) {}
    public record WorkshopSetup(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=160) String companyName,
                                @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=160) String workshopName) {}

    @GetMapping("/{id}") public CustomerResponse get(@PathVariable long id) { return service.find(id); }
    @GetMapping("/{id}/workshops") public Object workshops(@PathVariable long id) { return service.workshops(id); }
    @PostMapping public CustomerResponse create(@Valid @RequestBody CreateRequest request) { return service.create(request.customer(), request.workshopId()); }
    @PutMapping("/{id}") public CustomerResponse update(@PathVariable long id, @Valid @RequestBody CreateRequest request) { return service.update(id, request.customer()); }
    @PostMapping(value="/{id}/photo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> photo(@PathVariable long id, @RequestPart("file") MultipartFile file) { return Map.of("photoReference", photos.store(id, file)); }
    @GetMapping(value="/photos/{reference}")
    public ResponseEntity<Resource> photo(@PathVariable String reference) { Resource resource = photos.read(reference); return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(resource); }
    @GetMapping public List<Map<String,Object>> list() {
        return jdbc.queryForList("SELECT c.id,c.full_name,c.alias,c.personal_email,c.personal_phone,c.birth_date,c.active,c.municipality,c.photo_reference FROM customer c ORDER BY c.full_name LIMIT 500");
    }
}
