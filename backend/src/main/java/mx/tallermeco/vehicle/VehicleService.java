package mx.tallermeco.vehicle;
import mx.tallermeco.customer.repository.CustomerRepository;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.*;
import mx.tallermeco.status.StatusRepository;
import mx.tallermeco.workshop.management.WorkshopAccess;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static mx.tallermeco.vehicle.VehicleRepository.*;
@Service
public class VehicleService {
 private final VehicleRepository repository;private final CustomerRepository customers;private final StatusRepository statuses;private final WorkshopAccess access;private final Actor actor;private final Audit audit;
 public VehicleService(VehicleRepository repository,CustomerRepository customers,StatusRepository statuses,WorkshopAccess access,Actor actor,Audit audit){this.repository=repository;this.customers=customers;this.statuses=statuses;this.access=access;this.actor=actor;this.audit=audit;}
 public Object personal(){if(actor.is("CLIENT"))return repository.personal(actor.customer());if(actor.is("MECHANIC"))return repository.assigned(actor.employee());throw new org.springframework.security.access.AccessDeniedException("Usa el directorio administrativo por taller");}
 public Object personalWorkshops(){if(!actor.is("CLIENT"))throw new org.springframework.security.access.AccessDeniedException("Cuenta de cliente requerida");return repository.personalWorkshops(actor.customer());}
 @Transactional public long createPersonal(long workshop,VehicleData raw){if(!actor.is("CLIENT"))throw new org.springframework.security.access.AccessDeniedException("Cuenta de cliente requerida");long customer=actor.customer();if(!repository.personalWorkshop(customer,workshop))throw missing();var owner=customers.lockScoped(customer,workshop).orElseThrow(VehicleService::missing);if(!owner.allowsOperations())throw conflict("Tu estatus no permite registrar vehículos");var data=VehicleRules.normalize(raw);try{long id=repository.create(customer,data);audit.record(actor.id(),"VEHICLE_CREATED","vehicle",id,Map.of("customerId",customer,"workshopId",workshop,"selfService",true));return id;}catch(DataIntegrityViolationException e){throw conflict("Ya existe un vehículo con ese VIN");}}
 public Vehicle get(long id,long workshop){access.require(workshop,true);return repository.get(id,workshop,false).orElseThrow(VehicleService::missing);}
 @Transactional(readOnly=true) public Page list(long workshop,int page,int size,String sort,String direction,String query,Long status){access.require(workshop,true);if(page<1||size<1||size>10||!Set.of("make","vin","plate","id").contains(sort)||!Set.of("ASC","DESC").contains(direction)||status!=null&&status<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Página, tamaño, estatus u orden inválidos (máximo 10 vehículos)");String normalized=InputRules.text("query",query,160,false);return repository.page(workshop,page,size,sort,direction,normalized==null?"":normalized,status);}
 @Transactional public Vehicle create(long customer,long workshop,VehicleData raw){access.require(workshop,true);var owner=customers.lockScoped(customer,workshop).orElseThrow(VehicleService::missing);if(!owner.allowsOperations())throw conflict("El cliente está suspendido o su estatus no permite registrar vehículos");var data=VehicleRules.normalize(raw);try{long id=repository.create(customer,data);audit.record(actor.id(),"VEHICLE_CREATED","vehicle",id,Map.of("customerId",customer,"workshopId",workshop));return get(id,workshop);}catch(DataIntegrityViolationException e){throw conflict("Ya existe un vehículo con ese VIN");}}
 @Transactional public Vehicle update(long id,long workshop,Long version,VehicleData raw){access.require(workshop,true);var current=repository.get(id,workshop,true).orElseThrow(VehicleService::missing);checkVersion(version,current.version());var data=VehicleRules.normalize(raw);try{repository.update(id,data);audit.record(actor.id(),"VEHICLE_UPDATED","vehicle",id,Map.of("customerId",current.customerId(),"workshopId",workshop));return get(id,workshop);}catch(DataIntegrityViolationException e){throw conflict("Ya existe un vehículo con ese VIN");}}
 @Transactional public Vehicle status(long id,long workshop,Long version,Long status){access.require(workshop,true);var current=repository.get(id,workshop,true).orElseThrow(VehicleService::missing);checkVersion(version,current.version());if(status==null||status<=0)InputRules.fail("statusId","Selecciona un estatus");var next=statuses.get(StatusRepository.Kind.VEHICLE,status,true);if(next.id()==current.statusId())return current;repository.status(id,next.id());audit.record(actor.id(),"VEHICLE_STATUS_CHANGED","vehicle",id,Map.of("previousStatusId",current.statusId(),"previousStatus",current.statusCode(),"statusId",next.id(),"status",next.code(),"workshopId",workshop));return get(id,workshop);}
 private static void checkVersion(Long requested,long current){if(requested==null||requested!=current)throw conflict("La ficha cambió; vuelve a abrirla antes de guardar");}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Vehículo o cliente no encontrado en este taller");}
 private static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
}
