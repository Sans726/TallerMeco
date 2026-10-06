package mx.tallermeco.customer;
import mx.tallermeco.customer.repository.CustomerRepository;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import mx.tallermeco.workshop.management.WorkshopAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@Service
public class CustomerPhotoService {
 private final CustomerRepository repository;private final WorkshopAccess access;private final Actor actor;private final Audit audit;private final SafeImageStorage images;
 public CustomerPhotoService(CustomerRepository repository,WorkshopAccess access,Actor actor,Audit audit,@Value("${app.customer-photo-dir:.local/uploads/customer-photos}") String directory){this.repository=repository;this.access=access;this.actor=actor;this.audit=audit;images=new SafeImageStorage(directory);}
 @Transactional public String store(long id,long workshop,MultipartFile file){access.require(workshop,true);var customer=repository.lockScoped(id,workshop).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente no encontrado en este taller"));String ref=images.store(file);repository.updatePhotoReference(id,ref);images.replaceAfterCommit(customer.photoReference());audit.record(actor.id(),"CUSTOMER_PHOTO_CHANGED","customer",id,Map.of("workshopId",workshop));return ref;}
 public ResponseEntity<Resource> read(String ref,long workshop){access.require(workshop,true);var customer=repository.byPhoto(ref).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Fotografía no encontrada"));if(repository.findScoped(customer.id(),workshop).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Fotografía no encontrada en este taller");return images.read(ref);}
}
