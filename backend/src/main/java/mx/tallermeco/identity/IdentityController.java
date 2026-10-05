package mx.tallermeco.identity;
import mx.tallermeco.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@RestController @RequestMapping("/api")
public class IdentityController {
 final IdentityService service; final Actor actor; final Store db; final Audit audit; final ProfileService profiles; final ProfilePhotoService photos;
 public IdentityController(IdentityService service,Actor actor,Store db,Audit audit,ProfileService profiles,ProfilePhotoService photos){this.service=service;this.actor=actor;this.db=db;this.audit=audit;this.profiles=profiles;this.photos=photos;}
 public record Register(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=12,max=128) String password,@NotBlank @Size(max=160) String name,@Size(max=30) String phone){}
 public record EmailInput(@NotBlank @Email @Size(max=254) String email){}
 public record Reset(@NotBlank @Size(max=128) String token,@NotBlank @Size(min=12,max=128) String password){}
 public record Password(@NotBlank String currentPassword,@NotBlank @Size(min=12,max=128) String password){}
 public record Staff(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=12,max=128) String password,@NotBlank @Size(max=160) String name,@NotBlank String role){}
 public record Access(boolean enabled){}
 @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("token",token.getToken(),"header",token.getHeaderName());}
 @GetMapping("/auth/me") public Map<String,Object> me(){return actor.profile();}
 @PostMapping("/auth/register") public Map<String,Long> register(@Valid @RequestBody Register r){return Map.of("id",service.register(r.email,r.password,r.name,r.phone));}
 @PostMapping("/auth/forgot") public Map<String,String> forgot(@Valid @RequestBody EmailInput r){service.forgot(r.email);return Map.of("message","Si el correo está registrado, recibirás un enlace de recuperación.");}
 @PostMapping("/auth/reset") public void reset(@Valid @RequestBody Reset r){service.reset(r.token,r.password);}
 @PostMapping("/account/password") public void password(@Valid @RequestBody Password r){service.changePassword(actor.id(),r.currentPassword,r.password);}
 @PutMapping("/account") public void profile(@Valid @RequestBody ProfileService.Profile r){profiles.update(actor.id(),r);}
 @PostMapping(value="/account/photo", consumes="multipart/form-data") public Map<String,Object> photo(@RequestPart("file") org.springframework.web.multipart.MultipartFile file){photos.store(actor.id(),file);return actor.profile();}
 @GetMapping("/account/photo") public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> photo(){return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.IMAGE_PNG).cacheControl(org.springframework.http.CacheControl.noStore()).body(photos.read(actor.id()));}
 @DeleteMapping("/account/photo") public void removePhoto(){photos.remove(actor.id());}
 @GetMapping("/employees") public List<Map<String,Object>> staff(){actor.admin();return db.list("SELECT e.*,u.email,u.enabled,r.role_code FROM employee e JOIN app_user u ON u.id=e.user_id JOIN user_role r ON r.user_id=u.id ORDER BY e.full_name");}
 @PostMapping("/employees") public Map<String,Long> staff(@Valid @RequestBody Staff r){actor.admin();return Map.of("id",service.staff(r.email,r.password,r.name,r.role,actor.id()));}
 @PutMapping("/employees/{userId}/access") public void access(@PathVariable long userId,@RequestBody Access r){actor.admin();service.staffAccess(userId,r.enabled,actor.id());}
}
