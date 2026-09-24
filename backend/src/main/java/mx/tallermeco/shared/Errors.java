package mx.tallermeco.shared;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import java.util.Map;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){ return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"No fue posible completar la operación":e.getReason())); }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){ return ResponseEntity.badRequest().body(Map.of("message", e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).findFirst().orElse("Revisa los datos"))); }
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> integrity(){return ResponseEntity.status(409).body(Map.of("message","Datos duplicados, saldo insuficiente o referencia inválida. Revisa la operación."));}
 @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("message","No tienes permiso para esta operación"));}
 @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class}) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Datos inválidos. Revisa el formulario."));}
}
