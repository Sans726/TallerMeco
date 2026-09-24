package mx.tallermeco.identity;
import mx.tallermeco.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
@Service
public class IdentityService {
 private final Store db; private final Audit audit; private final PasswordEncoder encoder; private final JavaMailSender mail;
 @Value("${app.mail.mode}") String mode; @Value("${app.mail.from}") String from;
 @Value("${app.base-url}") String baseUrl; @Value("${app.outbox}") String outbox;
 public IdentityService(Store db,Audit audit,PasswordEncoder encoder,JavaMailSender mail){this.db=db;this.audit=audit;this.encoder=encoder;this.mail=mail;}
 @Transactional public long register(String email,String password,String name,String phone){
  long id=db.id("INSERT INTO app_user(email,password_hash) VALUES (?,?)",email.trim().toLowerCase(Locale.ROOT),encoder.encode(password));
  db.update("INSERT INTO user_role VALUES (?,'CLIENT')",id);
  db.update("INSERT INTO customer(user_id,full_name,phone) VALUES (?,?,?)",id,name.trim(),phone);
  audit.record(id,"REGISTER","user",id,Map.of()); return id;
 }
 @Transactional public void changePassword(long id,String oldPassword,String password){
  var u=db.one("SELECT password_hash FROM app_user WHERE id=? FOR UPDATE",id);
  Store.require(encoder.matches(oldPassword,u.get("password_hash").toString()),"La contraseña actual no coincide");
  db.update("UPDATE app_user SET password_hash=?,version=version+1 WHERE id=?",encoder.encode(password),id);
  db.update("UPDATE password_reset_token SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL",id);
  audit.record(id,"PASSWORD_CHANGED","user",id,Map.of());
 }
 static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @Transactional public void forgot(String email){
  var rows=db.list("SELECT id,email FROM app_user WHERE email=? AND enabled=true",email.trim().toLowerCase(Locale.ROOT));if(rows.isEmpty())return;
  var u=rows.getFirst(); long id=Store.number(u.get("id"));
  byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
  db.update("UPDATE password_reset_token SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL",id);
  db.update("INSERT INTO password_reset_token(user_id,token_hash,expires_at) VALUES (?,?,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 MINUTE))",id,hash(token));
  String content="Restablece tu contraseña de TallerMeco (válido 30 minutos):\n"+baseUrl+"/#/reset?token="+token+"\nSi no lo solicitaste, ignora este correo.";
  if(mode.equals("local"))try {
   Path dir=Path.of(outbox);Files.createDirectories(dir);Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
   Path file=Files.createTempFile(dir,"recovery-",".txt");Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));Files.writeString(file,"Para: "+u.get("email")+"\n"+content);
  }catch(java.io.IOException e){throw new IllegalStateException("No se pudo guardar el correo local",e);}
  else {var message=new SimpleMailMessage();message.setFrom(from);message.setTo(u.get("email").toString());message.setSubject("Recupera tu acceso · TallerMeco");message.setText(content);mail.send(message);}
  audit.record(id,"PASSWORD_RESET_REQUESTED","user",id,Map.of());
 }
 @Transactional public void reset(String token,String password){
  var rows=db.list("SELECT id,user_id FROM password_reset_token WHERE token_hash=? AND used_at IS NULL AND expires_at>UTC_TIMESTAMP(6) FOR UPDATE",hash(token));
  Store.require(!rows.isEmpty(),"El enlace expiró o ya fue utilizado");long id=Store.number(rows.getFirst().get("user_id"));
  db.update("UPDATE app_user SET password_hash=?,version=version+1 WHERE id=?",encoder.encode(password),id);
  db.update("UPDATE password_reset_token SET used_at=UTC_TIMESTAMP(6) WHERE user_id=? AND used_at IS NULL",id);
  audit.record(id,"PASSWORD_RESET","user",id,Map.of());
 }
 @Transactional public long staff(String email,String password,String name,String role,long actor){
  Store.require(Set.of("ADMIN","MECHANIC").contains(role),"Rol inválido");
  long id=db.id("INSERT INTO app_user(email,password_hash) VALUES (?,?)",email.trim().toLowerCase(Locale.ROOT),encoder.encode(password));
  db.update("INSERT INTO user_role VALUES (?,?)",id,role);db.update("INSERT INTO employee(user_id,full_name,job_title) VALUES (?,?,?)",id,name,role.equals("ADMIN")?"Administración":"Mecánica");
  audit.record(actor==0?id:actor,"STAFF_CREATED","user",id,Map.of("role",role));return id;
 }
 @Transactional public void staffAccess(long userId,boolean enabled,long actor){
  Store.require(userId!=actor,"No puedes desactivar tu propio acceso");
  db.one("SELECT id FROM employee WHERE user_id=? FOR UPDATE",userId);
  db.update("UPDATE app_user SET enabled=?,version=version+1 WHERE id=?",enabled,userId);
  db.update("UPDATE employee SET active=? WHERE user_id=?",enabled,userId);
  audit.record(actor,"ACCESS_CHANGED","user",userId,Map.of("enabled",enabled));
 }
}
