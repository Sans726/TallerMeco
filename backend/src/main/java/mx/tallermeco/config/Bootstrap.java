package mx.tallermeco.config;
import mx.tallermeco.shared.Store;
import mx.tallermeco.identity.IdentityService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class Bootstrap {
 @Bean ApplicationRunner initialAdmin(Store db,IdentityService identity,@Value("${app.bootstrap.password}")String password){return args->{
  if(db.count("SELECT COUNT(*) FROM app_user")==0){if(password.length()<12)throw new IllegalStateException("ADMIN_INITIAL_PASSWORD de al menos 12 caracteres requerido");identity.staff("admin@tallermeco.local",password,"Administrador","ADMIN",0);}
 };}
}
