package mx.tallermeco.workshop.management;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@Service
public class WorkshopBannerService {
 private final WorkshopRepository repository;private final WorkshopAccess access;private final Actor actor;private final Audit audit;private final SafeImageStorage images;
 public WorkshopBannerService(WorkshopRepository repository,WorkshopAccess access,Actor actor,Audit audit,@Value("${app.workshop-banner-dir:.local/uploads/workshop-banners}") String dir){this.repository=repository;this.access=access;this.actor=actor;this.audit=audit;images=new SafeImageStorage(dir);}
 @Transactional public String store(long id,MultipartFile file){actor.admin();access.require(id,false);var workshop=repository.get(id,true);String ref=images.store(file);repository.banner(id,ref);images.replaceAfterCommit((String)workshop.get("bannerReference"));audit.record(actor.id(),"WORKSHOP_BANNER_CHANGED","workshop",id,Map.of());return ref;}
 public ResponseEntity<Resource> read(long id){access.require(id,!actor.is("ADMIN"));return images.read((String)repository.get(id,false).get("bannerReference"));}
}
