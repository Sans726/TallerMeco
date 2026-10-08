package mx.tallermeco.identity;
import com.fasterxml.jackson.databind.*;
import mx.tallermeco.TallerApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=TallerApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=${AUTH_TEST_URL}","spring.datasource.username=${AUTH_TEST_USER}","spring.datasource.password=${AUTH_TEST_PASSWORD}",
 "spring.flyway.url=${AUTH_TEST_URL}","spring.flyway.user=${AUTH_TEST_OWNER}","spring.flyway.password=${AUTH_TEST_OWNER_PASSWORD}","app.bootstrap.password=","server.servlet.session.cookie.secure=false","app.test.case=uc-cv-03"})
class StatusVehicleIntegrationTest {
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;@Autowired ObjectMapper json;
 AuthenticationIntegrationTest helper;AuthenticationIntegrationTest.Browser admin,reception;long a,b,customer;String tag;
 @BeforeEach void setup()throws Exception{tag=UUID.randomUUID().toString();helper=new AuthenticationIntegrationTest();helper.port=port;helper.jdbc=jdbc;helper.passwords=passwords;helper.json=json;admin=helper.authenticated(helper.account("ADMIN",true));var fixture=new CustomerAdministrationIntegrationTest();fixture.helper=helper;fixture.admin=admin;fixture.jdbc=jdbc;fixture.json=json;a=fixture.createWorkshop("Taller "+tag,"AAA900203"+String.format("%03d",nextRfcNumber()));b=fixture.createWorkshop("Otro "+tag,"BBB900203"+String.format("%03d",nextRfcNumber()));var account=helper.account("RECEPTIONIST",true);reception=helper.authenticated(account);send(admin,"PUT","/api/workshops/"+a+"/users/"+account.id(),Map.of("active",true),200);customer=send(admin,"POST","/api/customers",Map.of("customer",fixture.client(tag),"workshopId",a),200).get("id").asLong();}
 JsonNode send(AuthenticationIntegrationTest.Browser browser,String method,String path,Object body,int expected)throws Exception{var r=helper.request(browser,method,path,body,true);assertEquals(expected,r.statusCode(),r.body());return r.body().isEmpty()?json.createObjectNode():json.readTree(r.body());}
 JsonNode get(AuthenticationIntegrationTest.Browser browser,String path,int expected)throws Exception{var r=browser.get(path);assertEquals(expected,r.statusCode(),r.body());return r.body().isEmpty()?json.createObjectNode():json.readTree(r.body());}
 long status(String kind,String code){return jdbc.queryForObject("SELECT id FROM "+(kind.equals("customers")?"customer_status":"vehicle_status")+" WHERE code=?",Long.class,code);}
 int nextRfcNumber(){int n;do{n=new Random().nextInt(1000);}while(jdbc.queryForObject("SELECT COUNT(*) FROM workshop WHERE rfc IN (?,?)",Long.class,"AAA900203"+String.format("%03d",n),"BBB900203"+String.format("%03d",n))>0);return n;}
 Map<String,Object> data(){var r=new HashMap<String,Object>();String vin;do{vin="1hgcm82633a"+String.format("%06d",new Random().nextInt(1000000));}while(jdbc.queryForObject("SELECT COUNT(*) FROM vehicle WHERE vin=?",Long.class,vin)>0);r.put("vin",vin);r.put("plate","abc -123");r.put("make","  HONDA  ");r.put("model","Accord 2.0");r.put("trim","EX-L");r.put("color","Azul metálico");r.put("year","2003");return r;}
 String cpath(){return "/api/customers/"+customer+"?workshopId="+a;}
 Map<String,Object> transition(long workshop,long id,long version){return Map.of("workshopId",workshop,"statusId",id,"version",version);}
 @Test void catalogsAreIndependentProtectedAuditedAndRestricted()throws Exception{
  for(String kind:List.of("customers","vehicles")){
   String path="/api/statuses/"+kind;var base=get(admin,path,200);assertTrue(base.size()>=2);assertTrue(get(reception,path,200).size()>=2);long active=status(kind,"ACTIVE");
   send(admin,"DELETE",path+"/"+active+"?version=0",null,409);
   send(admin,"PUT",path+"/"+active,Map.of("code","BROKEN","description","No debe cambiar","allowsOperations",false,"version",0),409);
   send(reception,"POST",path,Map.of("code","DENIED","description","Sin permiso","allowsOperations",false),403);
   send(reception,"PUT",path+"/"+active,Map.of(),403);send(reception,"DELETE",path+"/"+active+"?version=0",null,403);
   String code="CUSTOM_"+tag.replace("-","").substring(0,12).toUpperCase();var created=send(admin,"POST",path,Map.of("code",code.toLowerCase(),"description","Cliente especial","allowsOperations",true),200);long id=created.get("id").asLong();assertEquals(code,created.get("code").asText());assertFalse(created.get("system").asBoolean());
   send(admin,"POST",path,Map.of("code",code,"description","Duplicado","allowsOperations",false),409);
   send(admin,"POST",path,Map.of("code","MISSING","description","Falta bandera"),400);
   send(admin,"PUT",path+"/"+id,Map.of("code",code,"description","Descripción nueva","allowsOperations",false,"version",0),200);
   send(admin,"PUT",path+"/"+id,Map.of("code",code,"description","Versión vieja","allowsOperations",false,"version",0),409);
   send(admin,"DELETE",path+"/"+id+"?version=1",null,200);
   for(String role:List.of("CLIENT","MECHANIC")){var denied=helper.authenticated(helper.account(role,true));get(denied,path,403);send(denied,"POST",path,Map.of(),403);send(denied,"PUT",path+"/"+active,Map.of(),403);send(denied,"DELETE",path+"/"+active+"?version=0",null,403);}
   var system=get(admin,path,200);JsonNode fundamental=null;for(var row:system)if(row.get("id").asLong()==active)fundamental=row;
   send(admin,"PUT",path+"/"+active,Map.of("code","ACTIVE","description","Operativo del sistema","allowsOperations",true,"version",fundamental.get("version").asLong()),200);
   String prefix=kind.equals("customers")?"CUSTOMER":"VEHICLE";for(String event:List.of("CREATED","UPDATED","DELETED"))assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action=?",Long.class,prefix+"_STATUS_"+event)>0);
  }
  for(String code:List.of("DELETED","UPDATED","CANCELLED","INACTIVE"))send(admin,"POST","/api/statuses/customers",Map.of("code",code,"description","Código fuera de alcance","allowsOperations",false),400);
  send(admin,"POST","/api/statuses/vehicles",Map.of("code","IN_SERVICE","description","Derivado","allowsOperations",true),400);
  assertEquals(403,helper.request(admin,"POST","/api/statuses/customers",Map.of("code","CSRF","description","Sin token","allowsOperations",true),false).statusCode());
 }
 @Test void customerTransitionsRespectScopeVersionAndNoopAndCustomOperations()throws Exception{
  long active=status("customers","ACTIVE"),suspended=status("customers","SUSPENDED");String path="/api/customers/"+customer+"/status";
  send(reception,"PATCH",path,transition(b,suspended,0),404);send(admin,"PATCH",path,transition(b,suspended,0),404);
  var first=send(reception,"PATCH",path,transition(a,suspended,0),200);assertFalse(first.get("allowsOperations").asBoolean());assertEquals("SUSPENDED",first.get("statusCode").asText());long version=first.get("version").asLong();
  long events=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_type='customer' AND entity_id=? AND action='CUSTOMER_STATUS_CHANGED'",Long.class,customer);
  var noop=send(reception,"PATCH",path,transition(a,suspended,version),200);assertEquals(version,noop.get("version").asLong());assertEquals(events,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE entity_type='customer' AND entity_id=? AND action='CUSTOMER_STATUS_CHANGED'",Long.class,customer));
  send(reception,"PATCH",path,transition(a,active,0),409);var restored=send(reception,"PATCH",path,transition(a,active,version),200);assertTrue(restored.get("allowsOperations").asBoolean());
  var custom=send(admin,"POST","/api/statuses/customers",Map.of("code","VIP_"+tag.replace("-","").substring(0,10),"description","Operativo personalizado","allowsOperations",true),200);long customId=custom.get("id").asLong();var assigned=send(reception,"PATCH",path,transition(a,customId,restored.get("version").asLong()),200);assertTrue(assigned.get("allowsOperations").asBoolean());
  assertEquals(1,get(reception,"/api/customers?workshopId="+a+"&statusId="+customId,200).get("totalItems").asInt());send(admin,"DELETE","/api/statuses/customers/"+customId+"?version=0",null,409);
  send(reception,"POST","/api/vehicles",Map.of("customerId",customer,"workshopId",a,"vehicle",data()),200);
  send(admin,"PUT","/api/statuses/customers/"+customId,Map.of("code",custom.get("code").asText(),"description","Personalizado sin operaciones","allowsOperations",false,"version",0),200);
  send(reception,"POST","/api/vehicles",Map.of("customerId",customer,"workshopId",a,"vehicle",data()),409);
  var change=jdbc.queryForMap("SELECT changes FROM audit_event WHERE action='CUSTOMER_STATUS_CHANGED' AND entity_id=? ORDER BY id DESC LIMIT 1",customer);assertTrue(change.get("changes").toString().contains("previousStatus"));
 }
 @Test void vehiclesAreValidatedScopedPaginatedOperationalAndAudited()throws Exception{
  String root="/api/vehicles";var valid=data();var created=send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",valid),200);long id=created.get("id").asLong();assertEquals(valid.get("vin").toString().toUpperCase(),created.get("vin").asText());assertEquals("ABC-123",created.get("plate").asText());assertEquals("honda",created.get("make").asText());assertTrue(created.get("odometer").isNull());
  send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",valid),409);get(admin,root+"/"+id+"?workshopId="+b,404);get(reception,root+"/"+id+"?workshopId="+b,404);send(admin,"POST",root,Map.of("customerId",customer,"workshopId",b,"vehicle",data()),404);
  for(String vin:List.of("1HGCM82633A00435","1HGCM82633A0043522","IHGCM82633A004352","OHGCM82633A004352","QHGCM82633A004352","1HGCM826 3A004352","1HGCM826-3A004352","1HGCM826,3A004352")){var bad=data();bad.put("vin",vin);assertTrue(send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",bad),400).get("fields").has("vin"));}
  for(var pair:List.of(Map.entry("year","12e3"),Map.entry("year","2003.0"),Map.entry("year","1899"),Map.entry("year","9999"),Map.entry("odometer","-1"),Map.entry("odometer","12.5"),Map.entry("odometer","NaN"),Map.entry("odometer","2147483648"),Map.entry("make","<script>"),Map.entry("color",""))){var bad=data();bad.put(pair.getKey(),pair.getValue());send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",bad),400);}
  for(Object bad:List.of(-1,12.5,Double.POSITIVE_INFINITY,2000.0)){var d=data();d.put("odometer",bad);assertTrue(send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",d),400).get("fields").has("odometer"));}
  valid.put("odometer",0);var updated=send(reception,"PUT",root+"/"+id,Map.of("workshopId",a,"version",0,"vehicle",valid),200);assertEquals(0,updated.get("odometer").asInt());send(reception,"PUT",root+"/"+id,Map.of("workshopId",a,"version",0,"vehicle",valid),409);send(admin,"PUT",root+"/"+id,Map.of("workshopId",b,"version",1,"vehicle",valid),404);
  long suspended=status("vehicles","SUSPENDED"),active=status("vehicles","ACTIVE");var stopped=send(reception,"PATCH",root+"/"+id+"/status",transition(a,suspended,1),200);assertFalse(stopped.get("allowsOperations").asBoolean());var again=send(reception,"PATCH",root+"/"+id+"/status",transition(a,suspended,2),200);assertEquals(2,again.get("version").asLong());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='VEHICLE_STATUS_CHANGED' AND entity_type='vehicle' AND entity_id=?",Long.class,id));send(reception,"PATCH",root+"/"+id+"/status",transition(a,active,2),200);send(admin,"PATCH",root+"/"+id+"/status",transition(b,suspended,3),404);
  var custom=send(admin,"POST","/api/statuses/vehicles",Map.of("code","HOLD_"+tag.replace("-","").substring(0,10),"description","Estatus personalizado de vehículo","allowsOperations",false),200);long customId=custom.get("id").asLong();send(reception,"PATCH",root+"/"+id+"/status",transition(a,customId,3),200);send(admin,"DELETE","/api/statuses/vehicles/"+customId+"?version=0",null,409);send(reception,"PATCH",root+"/"+id+"/status",transition(a,active,4),200);
  send(reception,"PATCH","/api/customers/"+customer+"/status",transition(a,status("customers","SUSPENDED"),0),200);send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",data()),409);send(reception,"PATCH","/api/customers/"+customer+"/status",transition(a,status("customers","ACTIVE"),1),200);
  long order=send(admin,"POST","/api/orders",Map.of("vehicleId",id,"workshopId",a,"complaint","Revisión","odometer",0),200).get("id").asLong();assertTrue(get(reception,root+"/"+id+"?workshopId="+a,200).get("inService").asBoolean());jdbc.update("UPDATE service_order SET status='DELIVERED' WHERE id=?",order);assertFalse(get(reception,root+"/"+id+"?workshopId="+a,200).get("inService").asBoolean());
  for(int i=0;i<21;i++)send(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",data()),200);
  var first=get(reception,root+"?workshopId="+a,200);assertEquals(10,first.get("items").size());assertEquals(22,first.get("totalItems").asLong());assertEquals(3,first.get("totalPages").asInt());assertEquals(2,get(reception,root+"?workshopId="+a+"&page=3",200).get("items").size());assertEquals(0,get(admin,root+"?workshopId="+b,200).get("totalItems").asInt());assertEquals(1,get(reception,root+"?workshopId="+a+"&query="+valid.get("vin"),200).get("totalItems").asInt());assertEquals(0,get(reception,root+"?workshopId="+a+"&statusId="+suspended,200).get("totalItems").asInt());assertNotEquals(first.get("items").get(0).get("id"),get(reception,root+"?workshopId="+a+"&direction=DESC",200).get("items").get(0).get("id"));
  for(String q:List.of("pageSize=11","page=0","direction=sideways","sort=vin%3BDROP","statusId=-1"))get(reception,root+"?workshopId="+a+"&"+q,400);
  for(String role:List.of("CLIENT","MECHANIC")){var denied=helper.authenticated(helper.account(role,true));get(denied,root+"?workshopId="+a,403);get(denied,root+"/"+id+"?workshopId="+a,403);send(denied,"POST",root,Map.of(),403);send(denied,"PUT",root+"/"+id,Map.of(),403);send(denied,"PATCH",root+"/"+id+"/status",Map.of(),403);}
  assertEquals(403,helper.request(reception,"POST",root,Map.of("customerId",customer,"workshopId",a,"vehicle",data()),false).statusCode());
  assertEquals(403,helper.request(reception,"PUT",root+"/"+id,Map.of("workshopId",a,"version",5,"vehicle",valid),false).statusCode());assertEquals(403,helper.request(reception,"PATCH",root+"/"+id+"/status",transition(a,suspended,5),false).statusCode());
  for(String event:List.of("VEHICLE_CREATED","VEHICLE_UPDATED","VEHICLE_STATUS_CHANGED"))assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action=? AND entity_type='vehicle' AND entity_id=?",Long.class,event,id)>0);
 }
 @Test void personalVehicleFlowsDoNotExposeAdministrativeCapabilities()throws Exception{
  var account=helper.account("CLIENT",true);var self=helper.authenticated(account);jdbc.update("INSERT INTO customer(user_id,full_name) VALUES (?,'cliente personal')",account.id());long own=jdbc.queryForObject("SELECT id FROM customer WHERE user_id=?",Long.class,account.id());jdbc.update("INSERT INTO customer_workshop(customer_id,workshop_id) VALUES (?,?)",own,a);
  get(self,"/api/self/vehicles/workshops",200);var vehicle=send(self,"POST","/api/self/vehicles",Map.of("workshopId",a,"vehicle",data()),200);assertEquals(own,jdbc.queryForObject("SELECT customer_id FROM vehicle WHERE id=?",Long.class,vehicle.get("id").asLong()));assertEquals(1,get(self,"/api/self/vehicles",200).size());send(self,"POST","/api/self/vehicles",Map.of("workshopId",b,"customerId",customer,"vehicle",data()),404);
  send(admin,"PATCH","/api/customers/"+own+"/status",transition(a,status("customers","SUSPENDED"),0),200);send(self,"POST","/api/self/vehicles",Map.of("workshopId",a,"vehicle",data()),409);
  var mechanic=helper.authenticated(helper.account("MECHANIC",true));get(mechanic,"/api/self/vehicles",200);send(mechanic,"POST","/api/self/vehicles",Map.of("workshopId",a,"vehicle",data()),403);get(mechanic,"/api/self/vehicles/workshops",403);get(reception,"/api/self/vehicles",403);
 }

}
