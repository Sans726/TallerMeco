package mx.tallermeco.identity;

import com.fasterxml.jackson.databind.*;
import mx.tallermeco.TallerApplication;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=TallerApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=${AUTH_TEST_URL}","spring.datasource.username=${AUTH_TEST_USER}","spring.datasource.password=${AUTH_TEST_PASSWORD}",
 "spring.flyway.url=${AUTH_TEST_URL}","spring.flyway.user=${AUTH_TEST_OWNER}","spring.flyway.password=${AUTH_TEST_OWNER_PASSWORD}","app.bootstrap.password=","server.servlet.session.cookie.secure=false"})
class CustomerAdministrationIntegrationTest {
 static final Path photos,banners;
 static {try{photos=Files.createTempDirectory("tallermeco-customers-test-");banners=Files.createTempDirectory("tallermeco-banners-test-");}catch(IOException ex){throw new ExceptionInInitializerError(ex);}}
 @DynamicPropertySource static void directories(DynamicPropertyRegistry r){r.add("app.customer-photo-dir",photos::toString);r.add("app.workshop-banner-dir",banners::toString);}
 @AfterAll static void cleanImages()throws IOException{for(Path dir:List.of(photos,banners))try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired PasswordEncoder passwords;
 @Autowired ObjectMapper json;
 AuthenticationIntegrationTest helper;
 AuthenticationIntegrationTest.Browser admin,reception;
 long company,a,b,other,customer;
 @BeforeEach void setup()throws Exception{
  helper=new AuthenticationIntegrationTest();helper.port=port;helper.jdbc=jdbc;helper.passwords=passwords;helper.json=json;
  admin=helper.authenticated(helper.account("ADMIN",true));
 }
 JsonNode expect(AuthenticationIntegrationTest.Browser browser,String method,String path,Object body,int status)throws Exception{
  var response=helper.request(browser,method,path,body,true);assertEquals(status,response.statusCode(),response.body());return response.body().isEmpty()?json.createObjectNode():json.readTree(response.body());
 }
 JsonNode get(AuthenticationIntegrationTest.Browser browser,String path,int status)throws Exception{var response=browser.get(path);assertEquals(status,response.statusCode(),response.body());return json.readTree(response.body());}
 Map<String,Object> client(String suffix){var c=new HashMap<String,Object>();c.put("givenName","María José");c.put("paternalSurname","Muñoz");c.put("maternalSurname","O'Neill");c.put("personalEmail","customer-"+suffix+"@example.invalid");c.put("street","Calle 1");c.put("neighborhood","Centro");c.put("municipality","México");c.put("state","México");c.put("postalCode","01000");return c;}
 Map<String,Object> workshop(String name,String rfc){return Map.of("name",name,"legalName",name+" S.A. de C.V.","rfc",rfc,"phone","+52 55 1234 5678","email","workshop@example.invalid","street","Calle 1","neighborhood","Centro","municipality","México","state","México","postalCode","01000");}
 long createWorkshop(String name,String rfc)throws Exception{
  Map<String,Object> payload=new HashMap<>();payload.put("workshop",workshop(name,rfc));
  var companies=get(admin,"/api/workshops/companies",200);if(!companies.isEmpty())payload.put("companyId",companies.get(0).get("id").asLong());
  return expect(admin,"POST","/api/workshops",payload,200).get("id").asLong();
 }
 String context(long id,long workshop){return "/api/customers/"+id+"?workshopId="+workshop;}
 @Test void customerAdministrationIsScopedValidatedTransactionalAndPaginated()throws Exception{
  a=createWorkshop("Taller A","AAA900203A01");b=createWorkshop("Taller B","BBB900203B01");other=createWorkshop("Taller aislado","CCC900203C01");
  var receptionist=helper.account("RECEPTIONIST",true);reception=helper.authenticated(receptionist);
  assertEquals(0,get(reception,"/api/workshops",200).size());
  expect(admin,"PUT","/api/workshops/"+a+"/users/"+receptionist.id(),Map.of("active",true),200);
  expect(admin,"PUT","/api/workshops/"+b+"/users/"+receptionist.id(),Map.of("active",true),200);
  assertEquals(2,get(reception,"/api/workshops",200).size());
  var clientAccount=helper.account("CLIENT",true);
  jdbc.update("INSERT INTO customer(user_id,full_name) VALUES (?,'cliente anterior')",clientAccount.id());
  long legacy=jdbc.queryForObject("SELECT id FROM customer WHERE user_id=?",Long.class,clientAccount.id());
  get(reception,"/api/customers/unassigned",403);
  assertTrue(get(admin,"/api/customers/unassigned",200).get("items").size()>0);
  expect(reception,"POST","/api/customers/"+legacy+"/initial-workshop",Map.of("workshopId",a),403);
  expect(admin,"POST","/api/customers/"+legacy+"/initial-workshop",Map.of("workshopId",other),200);
  expect(admin,"POST","/api/customers/"+legacy+"/initial-workshop",Map.of("workshopId",a),404);
  var self=helper.authenticated(clientAccount);
  expect(self,"PUT","/api/account",Map.of("name","Mi cuenta nueva","phone","5511113333"),200);
  assertEquals("cliente anterior",jdbc.queryForObject("SELECT full_name FROM customer WHERE id=?",String.class,legacy));
  assertEquals(0,jdbc.queryForObject("SELECT version FROM customer WHERE id=?",Long.class,legacy));
  get(self,"/api/customers/unassigned",403);
  var data=client("first");data.put("birthDate","1990-02-03");data.put("curp","GODE900203HDFMNN01");data.put("rfc","GODE900203ABC");data.put("personalPhone","+52 55 1234 5678");
  var created=expect(reception,"POST","/api/customers",Map.of("customer",data,"workshopId",a),200);customer=created.get("id").asLong();
  assertEquals("maría josé muñoz o'neill",created.get("fullName").asText());assertEquals("5512345678",created.get("personalPhone").asText());assertEquals("gode900203hdfmnn01",created.get("curp").asText());assertTrue(created.get("age").asInt()>=36);
  for(String key:List.of("curp","rfc","personalEmail","personalPhone")){var duplicate=client(UUID.randomUUID().toString());duplicate.put("givenName","Otra");duplicate.put(key,data.get(key));expect(admin,"POST","/api/customers",Map.of("customer",duplicate,"workshopId",b),409);}
  var phoneDuplicate=client("cell-duplicate");phoneDuplicate.put("cellPhone","55-1234-5678");expect(admin,"POST","/api/customers",Map.of("customer",phoneDuplicate,"workshopId",b),409);
  var workEmailDuplicate=client("work-email-duplicate");workEmailDuplicate.put("workEmail",data.get("personalEmail"));expect(admin,"POST","/api/customers",Map.of("customer",workEmailDuplicate,"workshopId",b),409);
  var nameDuplicate=client("name-duplicate");nameDuplicate.put("birthDate","1990-02-03");nameDuplicate.put("givenName","  MARÍA   JOSÉ ");expect(admin,"POST","/api/customers",Map.of("customer",nameDuplicate,"workshopId",a),409);
  // A request cannot bypass source scope even for ADMIN by changing workshopId.
  get(admin,context(customer,b),404);get(reception,context(customer,b),404);get(reception,context(customer,other),404);
  get(reception,"/api/customers",400);
  get(reception,"/api/customers/"+customer+"/workshops?workshopId="+b,404);
  expect(reception,"PUT","/api/customers/"+customer,Map.of("customer",data,"workshopId",b,"version",0),404);
  expect(reception,"PATCH","/api/customers/"+customer+"/status",Map.of("workshopId",b,"active",false),404);
  expect(reception,"POST","/api/customers/"+customer+"/workshops",Map.of("workshopId",b,"targetWorkshopId",a,"mode","ASSOCIATE"),404);
  expect(reception,"POST","/api/customers/"+customer+"/workshops",Map.of("workshopId",a,"targetWorkshopId",other,"mode","ASSOCIATE"),404);
  long before=jdbc.queryForObject("SELECT COUNT(*) FROM customer",Long.class);
  expect(reception,"POST","/api/customers",Map.of("customer",client("wrong-target"),"workshopId",other),404);
  expect(admin,"POST","/api/customers",Map.of("customer",client("missing-target"),"workshopId",999999),404);
  expect(admin,"POST","/api/customers",Map.of("customer",client("zero-target"),"workshopId",0),400);
  assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM customer",Long.class));
  for(String raw:List.of("0100","010000","01,000","0100A","12e3"," 01000","-0100")){var bad=client("bad-cp");bad.put("postalCode",raw);var response=expect(reception,"POST","/api/customers",Map.of("customer",bad,"workshopId",a),400);assertTrue(response.get("fields").has("postalCode"));}
  for(var pair:List.of(Map.entry("givenName","Ana123"),Map.entry("personalPhone","551ABC5678"),Map.entry("curp","bad"),Map.entry("rfc","ABC900230A01"),Map.entry("personalEmail","a @example.com"),Map.entry("birthDate","2026-02-30"),Map.entry("birthDate","2099-01-01"))){var bad=client("bad-field");bad.put(pair.getKey(),pair.getValue());expect(reception,"POST","/api/customers",Map.of("customer",bad,"workshopId",a),400);}
  var noContact=client("none");noContact.remove("personalEmail");expect(reception,"POST","/api/customers",Map.of("customer",noContact,"workshopId",a),400);
  data.put("alias","  Ana   María ");var updated=expect(reception,"PUT","/api/customers/"+customer,Map.of("customer",data,"workshopId",a,"version",created.get("version").asLong()),200);assertEquals("ana maría",updated.get("alias").asText());
  expect(reception,"PUT","/api/customers/"+customer,Map.of("customer",data,"workshopId",a,"version",0),409);
  var withoutCsrf=helper.request(reception,"PATCH","/api/customers/"+customer+"/status",Map.of("workshopId",a,"active",false),false);assertEquals(403,withoutCsrf.statusCode());
  assertFalse(expect(reception,"PATCH","/api/customers/"+customer+"/status",Map.of("workshopId",a,"active",false),200).get("active").asBoolean());assertTrue(get(reception,context(customer,a),200).has("id"));
  assertTrue(expect(reception,"PATCH","/api/customers/"+customer+"/status",Map.of("workshopId",a,"active",true),200).get("active").asBoolean());
  expect(reception,"PATCH","/api/customers/"+customer+"/workshops/"+a,Map.of("workshopId",a,"active",false),409);
  expect(reception,"POST","/api/customers/"+customer+"/workshops",Map.of("workshopId",a,"targetWorkshopId",b,"mode","ASSOCIATE"),200);get(reception,context(customer,b),200);
  expect(reception,"PATCH","/api/customers/"+customer+"/workshops/"+b,Map.of("workshopId",a,"active",false),200);get(reception,context(customer,b),404);
  expect(reception,"PATCH","/api/customers/"+customer+"/workshops/"+b,Map.of("workshopId",a,"active",true),200);
  expect(reception,"POST","/api/customers/"+customer+"/workshops",Map.of("workshopId",a,"targetWorkshopId",b,"mode","REASSIGN"),200);get(reception,context(customer,a),404);get(reception,context(customer,b),200);
  assertEquals(2,get(reception,"/api/customers/"+customer+"/workshops?workshopId="+b,200).size());
  // Resource and photo endpoints share exactly the same scope checks.
  var png=image();var photo=upload(reception,"/api/customers/"+customer+"/photo?workshopId="+b,png,"../../customer.exe","application/octet-stream");assertEquals(200,photo.statusCode(),photo.body());String ref=json.readTree(photo.body()).get("photoReference").asText();
  assertEquals(200,reception.get("/api/customers/photos/"+ref+"?workshopId="+b).statusCode());assertEquals(404,reception.get("/api/customers/photos/"+ref+"?workshopId="+a).statusCode());
  assertEquals(404,upload(reception,"/api/customers/"+customer+"/photo?workshopId="+a,png,"a.png","image/png").statusCode());
  assertEquals(400,upload(reception,"/api/customers/"+customer+"/photo?workshopId="+b,"bad image".getBytes(),"a.png","image/png").statusCode());
  var secondPhoto=upload(reception,"/api/customers/"+customer+"/photo?workshopId="+b,png,"a.png","image/png");assertEquals(200,secondPhoto.statusCode());assertFalse(Files.exists(photos.resolve(ref)));assertEquals(404,reception.get("/api/customers/photos/"+ref+"?workshopId="+b).statusCode());
  for(String role:List.of("MECHANIC","CLIENT")){var denied=helper.authenticated(helper.account(role,true));get(denied,"/api/customers?workshopId="+b,403);get(denied,context(customer,b),403);expect(denied,"POST","/api/customers",Map.of("customer",client("denied"),"workshopId",b),403);expect(denied,"PUT","/api/customers/"+customer,Map.of("customer",data,"workshopId",b,"version",0),403);expect(denied,"PATCH","/api/customers/"+customer+"/status",Map.of("workshopId",b,"active",false),403);expect(denied,"POST","/api/customers/"+customer+"/workshops",Map.of("workshopId",b,"targetWorkshopId",a,"mode","REASSIGN"),403);assertEquals(403,upload(denied,"/api/customers/"+customer+"/photo?workshopId="+b,png,"a.png","image/png").statusCode());}
  expect(reception,"POST","/api/workshops",Map.of(),403);expect(reception,"PUT","/api/workshops/"+b,Map.of(),403);get(reception,"/api/workshops/access-users",403);
  // Fresh workshop A now has exactly 25 clients, all server-side pages are bounded and scoped.
  for(int i=0;i<25;i++){var c=client("page-"+i);c.put("givenName",String.valueOf((char)('A'+i))+"na");expect(admin,"POST","/api/customers",Map.of("customer",c,"workshopId",a),200);}
  var first=get(reception,"/api/customers?workshopId="+a+"&page=1",200);var middle=get(reception,"/api/customers?workshopId="+a+"&page=2",200);var last=get(reception,"/api/customers?workshopId="+a+"&page=3",200);var empty=get(reception,"/api/customers?workshopId="+a+"&page=4",200);
  assertEquals(10,first.get("items").size());assertEquals(10,middle.get("items").size());assertEquals(5,last.get("items").size());assertEquals(0,empty.get("items").size());assertEquals(25,first.get("totalItems").asLong());assertEquals(3,first.get("totalPages").asLong());
  var desc=get(reception,"/api/customers?workshopId="+a+"&direction=DESC",200);assertNotEquals(first.get("items").get(0).get("id"),desc.get("items").get(0).get("id"));assertEquals(last.get("items").get(4).get("id"),desc.get("items").get(0).get("id"));
  assertEquals(1,get(reception,"/api/customers?workshopId="+b,200).get("totalItems").asLong());assertEquals(1,get(admin,"/api/customers?workshopId="+other,200).get("items").size());
  for(String query:List.of("pageSize=11","page=0","sort=name%20DESC%3B","direction=sideways","pageSize=0"))get(reception,"/api/customers?workshopId="+a+"&"+query,400);
  assertEquals(0,get(reception,"/api/customers?workshopId="+a+"&query=does-not-exist",200).get("items").size());
  var simultaneous=client("race");var browserTwo=helper.authenticated(helper.account("ADMIN",true));
  var f1=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return helper.request(admin,"POST","/api/customers",Map.of("customer",simultaneous,"workshopId",a),true).statusCode();}catch(Exception ex){throw new RuntimeException(ex);}});
  var f2=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return helper.request(browserTwo,"POST","/api/customers",Map.of("customer",simultaneous,"workshopId",a),true).statusCode();}catch(Exception ex){throw new RuntimeException(ex);}});
  assertEquals(Set.of(200,409),Set.of(f1.join(),f2.join()));
  expect(admin,"PUT","/api/workshops/"+b+"/users/"+receptionist.id(),Map.of("active",false),200);get(reception,context(customer,b),404);
  for(String action:List.of("CUSTOMER_CREATED","CUSTOMER_UPDATED","CUSTOMER_SUSPENDED","CUSTOMER_REACTIVATED","CUSTOMER_ASSOCIATED","CUSTOMER_REASSIGNED","WORKSHOP_CREATED"))assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action=?",Long.class,action)>0,action);
 }
 @Test void workshopsValidateDuplicatesBannersAndInactiveTargets()throws Exception{
  long id=createWorkshop("Taller imágenes","DDD900203D01");var current=get(admin,"/api/workshops/"+id,200);long companyId=current.get("companyId").asLong();
  expect(admin,"POST","/api/workshops",Map.of("companyId",companyId,"workshop",workshop("Otro taller","DDD900203D01")),409);
  expect(admin,"POST","/api/workshops",Map.of("companyId",companyId,"workshop",workshop("Taller imágenes","EEE900203E01")),409);
  for(var pair:List.of(Map.entry("rfc","ABC900230A12"),Map.entry("email","a @b.com"),Map.entry("phone","55ABC5678"),Map.entry("postalCode","01,000"))){var bad=new HashMap<>(workshop("Taller inválido","FFF900203F01"));bad.put(pair.getKey(),pair.getValue());expect(admin,"POST","/api/workshops",Map.of("companyId",companyId,"workshop",bad),400);}
  var update=expect(admin,"PUT","/api/workshops/"+id,Map.of("workshop",workshop("Taller actualizado","DDD900203D01"),"active",true,"version",current.get("version").asLong()),200);assertEquals("taller actualizado",update.get("name").asText());
  expect(admin,"PUT","/api/workshops/"+id,Map.of("workshop",workshop("Taller actualizado","DDD900203D01"),"active",true,"version",0),409);
  assertEquals(400,upload(admin,"/api/workshops/"+id+"/banner","not image".getBytes(),"banner.jpg","image/jpeg").statusCode());
  assertEquals(413,upload(admin,"/api/workshops/"+id+"/banner",new byte[15*1024*1024+1],"banner.png","image/png").statusCode());
  var image=upload(admin,"/api/workshops/"+id+"/banner",image(),"../../evil.exe","text/plain");assertEquals(200,image.statusCode(),image.body());String ref=json.readTree(image.body()).get("bannerReference").asText();assertTrue(Files.exists(banners.resolve(ref)));assertEquals(200,admin.get("/api/workshops/"+id+"/banner").statusCode());
  assertEquals(200,upload(admin,"/api/workshops/"+id+"/banner",image(),"new.png","image/png").statusCode());assertFalse(Files.exists(banners.resolve(ref)));
  current=get(admin,"/api/workshops/"+id,200);expect(admin,"PUT","/api/workshops/"+id,Map.of("workshop",workshop("Taller actualizado","DDD900203D01"),"active",false,"version",current.get("version").asLong()),200);
  expect(admin,"POST","/api/customers",Map.of("customer",client("inactive"),"workshopId",id),404);
  assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='WORKSHOP_UPDATED'",Long.class)>0);
 }
 static byte[] image()throws IOException{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
 HttpResponse<String> upload(AuthenticationIntegrationTest.Browser browser,String path,byte[] bytes,String filename,String type)throws Exception{
  String boundary="test-customer-boundary";var out=new ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+filename+"\"\r\nContent-Type: "+type+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));return browser.client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header(browser.header,browser.token).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
 }
}
