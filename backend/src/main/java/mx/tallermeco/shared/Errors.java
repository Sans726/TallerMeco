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
 @ExceptionHandler(FieldValidationException.class) ResponseEntity<?> fields(FieldValidationException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage(),"fields",e.fields()));}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<?> uploadSize(){return ResponseEntity.status(413).body(Map.of("message","La imagen excede el máximo de 15 MB","fields",Map.of("image","La imagen excede el máximo de 15 MB")));}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){ return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"No fue posible completar la operación":e.getReason())); }
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){ return ResponseEntity.badRequest().body(Map.of("message", e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).findFirst().orElse("Revisa los datos"))); }
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> integrity(){return ResponseEntity.status(409).body(Map.of("message","Datos duplicados, saldo insuficiente o referencia inválida. Revisa la operación."));}
 @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class) ResponseEntity<?> concurrent(){return ResponseEntity.status(409).body(Map.of("message","Otra operación modificó estos datos. Recarga la ficha e intenta nuevamente."));}
 @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(){return ResponseEntity.status(403).body(Map.of("message","No tienes permiso para esta operación"));}
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<?> malformed(org.springframework.http.converter.HttpMessageNotReadableException e){
  Throwable cause=e.getCause();
  if(cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException format && !format.getPath().isEmpty()){
   String field=format.getPath().getLast().getFieldName();
   return ResponseEntity.badRequest().body(Map.of("message","Formato inválido en "+field,"fields",Map.of(field,field.equals("birthDate")?"La fecha de nacimiento es inválida":"Formato inválido")));
  }
  return ResponseEntity.badRequest().body(Map.of("message","Formato de solicitud inválido"));
 }
 @ExceptionHandler({IllegalArgumentException.class}) ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Datos inválidos. Revisa el formulario."));}
}
