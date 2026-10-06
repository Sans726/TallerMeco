package mx.tallermeco.workshop.management;
import mx.tallermeco.identity.Actor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkshopAccess {
 private final WorkshopRepository repository;private final Actor actor;
 public WorkshopAccess(WorkshopRepository repository,Actor actor){this.repository=repository;this.actor=actor;}
 public void manager(){if(!actor.is("ADMIN")&&!actor.is("RECEPTIONIST"))throw new AccessDeniedException("Solo administración o recepción");}
 public void require(long id,boolean mustBeActive){
  manager();if(id<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Selecciona un taller");
  if(!repository.accessible(id,actor.id(),actor.is("ADMIN"),mustBeActive,org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Taller inexistente, inactivo o fuera de tu alcance");
 }
}
