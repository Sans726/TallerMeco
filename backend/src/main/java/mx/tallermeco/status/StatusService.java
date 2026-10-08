package mx.tallermeco.status;

import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import mx.tallermeco.workshop.management.WorkshopAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static mx.tallermeco.status.StatusRepository.*;

@Service
public class StatusService {
 public record Data(String code,String description,Boolean allowsOperations,Long version){}
 private final StatusRepository repository;private final Actor actor;private final Audit audit;private final WorkshopAccess access;
 public StatusService(StatusRepository repository,Actor actor,Audit audit,WorkshopAccess access){this.repository=repository;this.actor=actor;this.audit=audit;this.access=access;}
 public List<Status> list(Kind k){access.manager();return repository.list(k);}
 private Data normalize(Kind k,Data r){
  if(r==null)InputRules.fail("status","Completa los datos del estatus");
  InputRules.text("code",r.code(),40,true);
  String code=r.code()==null?"":r.code().strip().toUpperCase(Locale.ROOT);
  if(!code.matches("[A-Z][A-Z0-9_]{1,39}"))InputRules.fail("code","Usa de 2 a 40 letras, números o guiones bajos; comienza con una letra");
  if(k==Kind.CUSTOMER&&Set.of("DELETED","UPDATED","CANCELLED","INACTIVE").contains(code))InputRules.fail("code","Ese código no corresponde a un estatus de cliente en este caso de uso");
  if(k==Kind.VEHICLE&&code.equals("IN_SERVICE"))InputRules.fail("code","En servicio se deriva de las órdenes, no del catálogo");
  if(r.allowsOperations()==null)InputRules.fail("allowsOperations","Indica si permite operaciones");
  InputRules.text("description",r.description(),500,true);
  return new Data(code,java.text.Normalizer.normalize(r.description(),java.text.Normalizer.Form.NFC).strip().replaceAll(" +"," "),r.allowsOperations(),r.version());
 }
 @Transactional public Status create(Kind k,Data r){actor.admin();var n=normalize(k,r);if(Set.of("ACTIVE","SUSPENDED").contains(n.code()))throw conflict("Ese código está reservado al sistema");try{long id=repository.create(k,n.code(),n.description(),n.allowsOperations());audit.record(actor.id(),k+"_STATUS_CREATED",k.table,id,Map.of("code",n.code(),"allowsOperations",n.allowsOperations()));return repository.get(k,id,false);}catch(DataIntegrityViolationException e){throw conflict("Ya existe un estatus con ese código");}}
 @Transactional public Status update(Kind k,long id,Data r){actor.admin();var current=repository.get(k,id,true);var n=normalize(k,r);if(n.version()==null||n.version()!=current.version())throw conflict("El estatus cambió; vuelve a abrirlo");if(current.system()&&(!current.code().equals(n.code())||current.allowsOperations()!=n.allowsOperations()))throw conflict("Solo puedes cambiar la descripción de un estatus del sistema");if(!current.system()&&Set.of("ACTIVE","SUSPENDED").contains(n.code()))throw conflict("Ese código está reservado al sistema");try{repository.update(k,id,n.code(),n.description(),n.allowsOperations());audit.record(actor.id(),k+"_STATUS_UPDATED",k.table,id,Map.of("previousCode",current.code(),"code",n.code(),"allowsOperations",n.allowsOperations()));return repository.get(k,id,false);}catch(DataIntegrityViolationException e){throw conflict("Ya existe un estatus con ese código");}}
 @Transactional public void delete(Kind k,long id,long version){actor.admin();var current=repository.get(k,id,true);if(version!=current.version())throw conflict("El estatus cambió; vuelve a abrirlo");if(current.system())throw conflict("No puedes eliminar un estatus del sistema");if(current.usageCount()>0)throw conflict("El estatus está asignado; cambia esas entidades antes de eliminarlo");try{repository.delete(k,id);audit.record(actor.id(),k+"_STATUS_DELETED",k.table,id,Map.of("code",current.code()));}catch(DataIntegrityViolationException e){throw conflict("El estatus está en uso y no puede eliminarse");}}
 private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
