package mx.tallermeco.workshop.management;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class WorkshopManagementService {
 private final WorkshopRepository repository;private final WorkshopAccess access;private final Actor actor;private final Audit audit;
 public WorkshopManagementService(WorkshopRepository r,WorkshopAccess a,Actor actor,Audit audit){repository=r;access=a;this.actor=actor;this.audit=audit;}
 public List<Map<String,Object>> list(boolean activeOnly){access.manager();return repository.list(actor.id(),actor.is("ADMIN"),activeOnly||!actor.is("ADMIN"));}
 public Map<String,Object> get(long id){access.require(id,!actor.is("ADMIN"));return repository.get(id,false);}
 public List<Map<String,Object>> companies(){actor.admin();return repository.companies();}
 @Transactional public Map<String,Object> create(CreateWorkshopRequest request){
  actor.admin();var w=normalize(request.workshop());
  try{
   long company;
   if(request.companyId()==null){if(!repository.companies().isEmpty())InputRules.fail("companyId","Selecciona una empresa existente");company=repository.company(w.legalName());}
   else{company=request.companyId();if(!repository.companyActive(company))InputRules.fail("companyId","Empresa inexistente o inactiva");}
   long id=repository.create(company,w);audit.record(actor.id(),"WORKSHOP_CREATED","workshop",id,Map.of("companyId",company));return get(id);
  }catch(DataIntegrityViolationException ex){throw duplicate();}
 }
 @Transactional public Map<String,Object> update(long id,UpdateWorkshopRequest request){
  actor.admin();access.require(id,false);var current=repository.get(id,true);
  if(request.version()==null||request.version()!=((Number)current.get("version")).longValue())throw new ResponseStatusException(HttpStatus.CONFLICT,"El taller cambió. Vuelve a abrirlo antes de editar");
  var normalized=normalize(request.workshop());
  try{repository.update(id,normalized,request.active());audit.record(actor.id(),"WORKSHOP_UPDATED","workshop",id,Map.of("active",request.active()));return get(id);}catch(DataIntegrityViolationException ex){throw duplicate();}
 }
 public List<Map<String,Object>> receptionists(){actor.admin();return repository.receptionists();}
 public List<Map<String,Object>> users(long id){actor.admin();access.require(id,false);return repository.users(id);}
 @Transactional public void assign(long id,long user,boolean active){actor.admin();access.require(id,false);repository.get(id,true);if(!repository.receptionist(user))InputRules.fail("userId","Selecciona una cuenta activa con rol recepción");repository.assign(id,user,active);audit.record(actor.id(),"WORKSHOP_ACCESS_CHANGED","workshop",id,Map.of("userId",user,"active",active));}
 public static WorkshopData normalize(WorkshopData w){
  if(w==null)InputRules.fail("workshop","Los datos del taller son obligatorios");
  return new WorkshopData(InputRules.business("name",w.name()),InputRules.business("legalName",w.legalName()),InputRules.rfc("rfc",w.rfc(),true,null),InputRules.phone("phone",w.phone(),true),InputRules.email("email",w.email(),true),InputRules.address("street",w.street(),180),InputRules.address("neighborhood",w.neighborhood(),120),InputRules.name("municipality",w.municipality(),120,true),InputRules.name("state",w.state(),120,true),InputRules.postal(w.postalCode()));
 }
 private static ResponseStatusException duplicate(){return new ResponseStatusException(HttpStatus.CONFLICT,"Ya existe un taller con ese RFC o nombre y razón social");}
}
