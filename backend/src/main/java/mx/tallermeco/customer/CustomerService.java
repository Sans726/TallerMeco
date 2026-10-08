package mx.tallermeco.customer;

import mx.tallermeco.customer.dto.*;
import mx.tallermeco.customer.repository.CustomerRepository;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import mx.tallermeco.workshop.management.WorkshopAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class CustomerService {
 private final CustomerRepository repository;private final Actor actor;private final Audit audit;private final WorkshopAccess access;private final mx.tallermeco.status.StatusRepository statuses;
 public CustomerService(CustomerRepository repository,Actor actor,Audit audit,WorkshopAccess access,mx.tallermeco.status.StatusRepository statuses){this.repository=repository;this.actor=actor;this.audit=audit;this.access=access;this.statuses=statuses;}
 @Transactional public CustomerResponse create(CustomerData request,long workshopId){
  access.require(workshopId,true);var normalized=normalize(request);
  try{long id=repository.create(normalized);repository.associateWithWorkshop(id,workshopId);audit.record(actor.id(),"CUSTOMER_CREATED","customer",id,Map.of("workshopId",workshopId));return find(id,workshopId);}
  catch(DataIntegrityViolationException ex){throw duplicate();}
 }
 public CustomerResponse find(long id,long workshopId){access.require(workshopId,true);return repository.findScoped(id,workshopId).orElseThrow(CustomerService::missing);}
 @Transactional public CustomerResponse update(long id,UpdateCustomerRequest request){
  access.require(request.workshopId(),true);var current=repository.lockScoped(id,request.workshopId()).orElseThrow(CustomerService::missing);
  if(request.version()==null||request.version()!=current.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"La ficha cambió. Vuelve a abrirla antes de editar");
  var normalized=normalize(request.customer());
  try{repository.update(id,normalized);audit.record(actor.id(),"CUSTOMER_UPDATED","customer",id,Map.of("workshopId",request.workshopId()));return find(id,request.workshopId());}catch(DataIntegrityViolationException ex){throw duplicate();}
 }
 @Transactional public CustomerResponse status(long id,long workshopId,Long statusId,Long version){
  access.require(workshopId,true);var current=repository.lockScoped(id,workshopId).orElseThrow(CustomerService::missing);
  if(statusId==null||statusId<=0)InputRules.fail("statusId","Selecciona un estatus");
  if(version==null||version!=current.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"La ficha cambió; vuelve a abrirla");
  var next=statuses.get(mx.tallermeco.status.StatusRepository.Kind.CUSTOMER,statusId,true);
  if(current.statusId()==next.id())return current;
  repository.status(id,next.id());audit.record(actor.id(),"CUSTOMER_STATUS_CHANGED","customer",id,Map.of("workshopId",workshopId,"previousStatusId",current.statusId(),"previousStatus",current.statusCode(),"statusId",next.id(),"status",next.code()));return find(id,workshopId);
 }

 public List<Map<String,Object>> workshops(long id,long workshopId){find(id,workshopId);return repository.scopedWorkshops(id,actor.id(),actor.is("ADMIN"));}
 @Transactional public void associate(long id,long source,long target,String mode){
  access.require(source,true);access.require(target,true);repository.lockScoped(id,source).orElseThrow(CustomerService::missing);
  if((mode==null||!Set.of("ASSOCIATE","REASSIGN").contains(mode))||source==target)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona otro taller y una operación válida");
  repository.associate(id,target);
  if(mode.equals("REASSIGN"))repository.associationActive(id,source,false);
  audit.record(actor.id(),mode.equals("REASSIGN")?"CUSTOMER_REASSIGNED":"CUSTOMER_ASSOCIATED","customer",id,Map.of("sourceWorkshopId",source,"targetWorkshopId",target));
 }
 @Transactional public void associationActive(long id,long context,long target,boolean active){
  access.require(context,true);access.require(target,true);repository.lockScoped(id,context).orElseThrow(CustomerService::missing);
  if(active)repository.associate(id,target);
  else{
   if(!repository.isAssociatedWithWorkshop(id,target))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Asociación no encontrada");
   if(repository.activeAssociations(id)<=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Conserva al menos una asociación activa; reasigna antes de desactivar");
   repository.associationActive(id,target,false);
  }
  audit.record(actor.id(),active?"CUSTOMER_ASSOCIATION_ACTIVATED":"CUSTOMER_ASSOCIATION_DEACTIVATED","customer",id,Map.of("workshopId",target));
 }
 @Transactional(readOnly=true) public CustomerPage list(CustomerListQuery q){
  access.require(q.workshopId(),true);
  if(q.statusId()!=null&&q.statusId()<=0||q.page()<1||q.pageSize()<1||q.pageSize()>10||!Set.of("name","id").contains(q.sort())||!Set.of("ASC","DESC").contains(q.direction()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página, tamaño u orden inválidos (máximo 10 clientes)");
  String query=InputRules.text("query",q.query(),160,false);
  return repository.page(new CustomerListQuery(q.workshopId(),q.page(),q.pageSize(),q.sort(),q.direction(),query==null?"":query,q.statusId()));
 }
 @Transactional(readOnly=true) public CustomerPage unassigned(int page,int pageSize,String direction,String query){
  actor.admin();if(page<1||pageSize<1||pageSize>10||!Set.of("ASC","DESC").contains(direction))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página, tamaño u orden inválidos");
  String normalized=InputRules.text("query",query,160,false);
  return repository.unassigned(new CustomerListQuery(0,page,pageSize,"name",direction,normalized==null?"":normalized));
 }
 @Transactional public void initialWorkshop(long id,long workshop){actor.admin();access.require(workshop,true);repository.lockUnassigned(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente pendiente de asignar no encontrado"));repository.associate(id,workshop);audit.record(actor.id(),"CUSTOMER_ASSOCIATED","customer",id,Map.of("targetWorkshopId",workshop,"initial",true));}
 public static CustomerData normalize(CustomerData r){
  if(r==null)InputRules.fail("customer","Los datos del cliente son obligatorios");
  String given=InputRules.name("givenName",r.givenName(),50,true),paternal=InputRules.name("paternalSurname",r.paternalSurname(),50,true),maternal=InputRules.name("maternalSurname",r.maternalSurname(),50,false);
  var birth=InputRules.birth(r.birthDate());
  String email=InputRules.email("personalEmail",r.personalEmail(),false),workEmail=InputRules.email("workEmail",r.workEmail(),false),personal=InputRules.phone("personalPhone",r.personalPhone(),false),cell=InputRules.phone("cellPhone",r.cellPhone(),false),work=InputRules.phone("workPhone",r.workPhone(),false);
  if(email==null&&workEmail==null&&personal==null&&cell==null&&work==null)InputRules.fail("personalEmail","Registra al menos un email o teléfono válido");
  return new CustomerData(given,paternal,maternal,InputRules.curp(r.curp(),birth),InputRules.rfc("rfc",r.rfc(),false,birth),birth,
   InputRules.name("alias",r.alias(),120,false),InputRules.name("alternativeContactName",r.alternativeContactName(),160,false),personal,cell,work,email,workEmail,
   InputRules.address("street",r.street(),180),InputRules.address("neighborhood",r.neighborhood(),120),InputRules.name("municipality",r.municipality(),120,true),InputRules.name("state",r.state(),120,true),InputRules.postal(r.postalCode()));
 }
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Cliente no encontrado en este taller");}
 private static ResponseStatusException duplicate(){return new ResponseStatusException(HttpStatus.CONFLICT,"Ya existe un cliente con esa CURP, RFC, email, teléfono o nombre y fecha de nacimiento");}
}
