package mx.tallermeco.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=${AUTH_TEST_URL}",
    "spring.datasource.username=${AUTH_TEST_USER}",
    "spring.datasource.password=${AUTH_TEST_PASSWORD}",
    "spring.flyway.url=${AUTH_TEST_URL}",
    "spring.flyway.user=${AUTH_TEST_OWNER}",
    "spring.flyway.password=${AUTH_TEST_OWNER_PASSWORD}",
    "app.bootstrap.password=",
    "server.servlet.session.cookie.secure=false"
})
class AuthenticationIntegrationTest {
    /** Offline HTTP runner when Maven's Surefire provider is unavailable. Uses the same assertions. */
    public static void main(String[] args) throws Exception {
        System.setProperty("spring.datasource.url", System.getenv("AUTH_TEST_URL"));
        System.setProperty("spring.datasource.username", System.getenv("AUTH_TEST_USER"));
        System.setProperty("spring.datasource.password", System.getenv("AUTH_TEST_PASSWORD"));
        System.setProperty("spring.flyway.url", System.getenv("AUTH_TEST_URL"));
        System.setProperty("spring.flyway.user", System.getenv("AUTH_TEST_OWNER"));
        System.setProperty("spring.flyway.password", System.getenv("AUTH_TEST_OWNER_PASSWORD"));
        System.setProperty("app.bootstrap.password", "");
        System.setProperty("server.port", "0");
        System.setProperty("server.servlet.session.cookie.secure", "false");
        var photoDir = java.nio.file.Files.createTempDirectory("tallermeco-profile-test-");
        System.setProperty("app.profile-photo-dir", photoDir.toString());
        try (var context = org.springframework.boot.SpringApplication.run(mx.tallermeco.TallerApplication.class)) {
            var checks = new AuthenticationIntegrationTest();
            checks.port = ((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext) context).getWebServer().getPort();
            checks.jdbc = context.getBean(JdbcTemplate.class);
            checks.passwords = context.getBean(PasswordEncoder.class);
            checks.json = context.getBean(ObjectMapper.class);
            checks.anonymousRequestsAreUnauthorized();
            for (String role : List.of("ADMIN", "RECEPTIONIST", "MECHANIC", "CLIENT")) checks.databaseRolesAndIdentityAreAuthoritative(role);
            checks.invalidCredentialsDoNotAuthenticate();
            checks.disabledAndRolelessAccountsCannotLogin();
            checks.csrfIsRequiredForLoginAndLogoutAndLogoutInvalidatesSession();
            checks.changingDatabaseRoleInvalidatesCachedAuthorities();
            checks.disablingUserOrChangingPasswordVersionInvalidatesSession();
            checks.receptionistCanRegisterAndEditCustomersButCannotAdminister();
            checks.profileDataAndPhotoArePrivateAndPersistent();
            System.out.println("PASS: authentication, receptionist customer create/update, admin-only operations, profile persistence, photo upload/replace/remove, invalid uploads and CSRF.");
        } finally {
            try (var files = java.nio.file.Files.walk(photoDir)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(file);
            }
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    static final String PASSWORD = "P2-01-test-only-password";

    record Account(long id, String email, String name) {}
    Account account(String role, boolean enabled) {
        String email = "p2-01-" + UUID.randomUUID() + "@example.invalid";
        jdbc.update("INSERT INTO app_user(email,password_hash,enabled) VALUES (?,?,?)", email, passwords.encode(PASSWORD), enabled);
        long id = Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM app_user WHERE email=?", Long.class, email));
        if (role != null) jdbc.update("INSERT INTO user_role(user_id,role_code) VALUES (?,?)", id, role);
        String name = email;
        if (role != null && !role.equals("CLIENT")) {
            name = "Prueba de autenticación " + role;
            jdbc.update("INSERT INTO employee(user_id,full_name,job_title) VALUES (?,?,?)", id, name, role);
        }
        return new Account(id, email, name);
    }

    final class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        String token;
        String header;
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response = get("/api/auth/csrf");
            assertEquals(200, response.statusCode());
            JsonNode body = json.readTree(response.body());
            token = body.get("token").asText(); header = body.get("header").asText();
        }
        HttpResponse<String> post(String path, String body, boolean withCsrf) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (withCsrf) request.header(header, token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> login(Account account, String password) throws Exception {
            return post("/api/auth/login", "email=" + URLEncoder.encode(account.email(), StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                    + "&role=ADMIN&id=999999&name=Untrusted", true);
        }
        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }
    }

    @Test void anonymousRequestsAreUnauthorized() throws Exception {
        var browser = new Browser();
        assertEquals(401, browser.get("/api/auth/me").statusCode());
        assertEquals(401, browser.get("/api/employees").statusCode());
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "RECEPTIONIST", "MECHANIC", "CLIENT"})
    void databaseRolesAndIdentityAreAuthoritative(String role) throws Exception {
        Account account = account(role, true);
        var browser = new Browser(); browser.csrf();
        String anonymousId = browser.sessionId();
        var login = browser.login(account, PASSWORD);
        assertEquals(200, login.statusCode(), login.body());
        assertNotEquals(anonymousId, browser.sessionId(), "Session id must rotate at login");
        String cookie = String.join(";", login.headers().allValues("set-cookie"));
        assertTrue(cookie.toLowerCase(Locale.ROOT).contains("httponly"), cookie);
        assertTrue(cookie.toLowerCase(Locale.ROOT).contains("samesite=lax"), cookie);
        var me = browser.get("/api/auth/me");
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.headers().firstValue("cache-control").orElse("").contains("no-store"));
        var profile = json.readTree(me.body());
        assertEquals(account.id(), profile.get("id").asLong());
        assertEquals(account.email(), profile.get("email").asText());
        assertEquals(account.name(), profile.get("name").asText());
        assertEquals(role, profile.get("role").asText());
        assertEquals(role, profile.get("roles").get(0).asText());
        assertFalse(profile.has("password_hash"));
        assertEquals(role.equals("ADMIN") ? 200 : 403, browser.get("/api/employees").statusCode());
        if (role.equals("RECEPTIONIST")) assertEquals(403, browser.get("/api/orders").statusCode());
    }

    @Test void invalidCredentialsDoNotAuthenticate() throws Exception {
        var account = account("CLIENT", true); var browser = new Browser(); browser.csrf();
        assertEquals(401, browser.login(account, "wrong-password").statusCode());
        assertEquals(401, browser.get("/api/auth/me").statusCode());
    }

    @Test void disabledAndRolelessAccountsCannotLogin() throws Exception {
        for (var account : List.of(account("CLIENT", false), account(null, true))) {
            var browser = new Browser(); browser.csrf();
            assertEquals(401, browser.login(account, PASSWORD).statusCode());
            assertEquals(401, browser.get("/api/auth/me").statusCode());
        }
    }

    @Test void csrfIsRequiredForLoginAndLogoutAndLogoutInvalidatesSession() throws Exception {
        var account = account("CLIENT", true); var browser = new Browser(); browser.csrf();
        assertEquals(403, browser.post("/api/auth/login", "email=" + account.email() + "&password=" + PASSWORD, false).statusCode());
        assertEquals(200, browser.login(account, PASSWORD).statusCode());
        assertEquals(403, browser.post("/api/auth/logout", "", false).statusCode());
        assertEquals(200, browser.get("/api/auth/me").statusCode());
        browser.csrf();
        String oldSession = browser.sessionId();
        var response = browser.post("/api/auth/logout", "", true);
        assertEquals(204, response.statusCode());
        assertTrue(response.headers().allValues("set-cookie").stream().anyMatch(c -> c.contains("JSESSIONID=") && c.contains("Max-Age=0")));
        assertEquals(401, browser.get("/api/auth/me").statusCode());
        var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                .header("Cookie", "JSESSIONID=" + oldSession).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, replay.statusCode(), "Old cookie must not authenticate");
    }

    @Test void changingDatabaseRoleInvalidatesCachedAuthorities() throws Exception {
        var account = account("ADMIN", true); var browser = new Browser(); browser.csrf();
        assertEquals(200, browser.login(account, PASSWORD).statusCode());
        jdbc.update("UPDATE user_role SET role_code='RECEPTIONIST' WHERE user_id=?", account.id());
        assertEquals(401, browser.get("/api/auth/me").statusCode());
        browser.csrf(); assertEquals(200, browser.login(account, PASSWORD).statusCode());
        assertEquals("RECEPTIONIST", json.readTree(browser.get("/api/auth/me").body()).get("role").asText());
        assertEquals(403, browser.get("/api/employees").statusCode());
    }

    @Test void disablingUserOrChangingPasswordVersionInvalidatesSession() throws Exception {
        var account = account("MECHANIC", true); var browser = new Browser(); browser.csrf();
        assertEquals(200, browser.login(account, PASSWORD).statusCode());
        jdbc.update("UPDATE app_user SET version=version+1 WHERE id=?", account.id());
        assertEquals(401, browser.get("/api/auth/me").statusCode());
        browser.csrf(); assertEquals(200, browser.login(account, PASSWORD).statusCode());
        jdbc.update("UPDATE app_user SET enabled=false WHERE id=?", account.id());
        assertEquals(401, browser.get("/api/auth/me").statusCode());
    }

    HttpResponse<String> request(Browser browser, String method, String path, Object body, boolean csrf) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        if(csrf) builder.header(browser.header,browser.token);
        return browser.client.send(builder.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    Browser authenticated(Account account) throws Exception {
        var browser=new Browser();browser.csrf();assertEquals(200,browser.login(account,PASSWORD).statusCode());browser.csrf();return browser;
    }
    @Test void receptionistCanRegisterAndEditCustomersButCannotAdminister() throws Exception {
        var admin=authenticated(account("ADMIN",true));
        var setup=request(admin,"POST","/api/workshops/setup",Map.of("companyName","Empresa prueba","workshopName","Taller prueba"),true);
        assertEquals(200,setup.statusCode(),setup.body());
        long workshop=json.readTree(setup.body()).get("workshopId").asLong();
        String email="recepcion-"+UUID.randomUUID()+"@example.invalid";
        var created=request(admin,"POST","/api/employees",Map.of("email",email,"password",PASSWORD,"name","Recepción prueba","role","RECEPTIONIST"),true);
        assertEquals(200,created.statusCode(),created.body());
        long userId=json.readTree(created.body()).get("id").asLong();
        assertEquals("RECEPTIONIST",jdbc.queryForObject("SELECT role_code FROM user_role WHERE user_id=?",String.class,userId));
        var receptionist=authenticated(new Account(userId,email,"Recepción prueba"));
        assertEquals(200,receptionist.get("/api/customers").statusCode());
        assertEquals(200,receptionist.get("/api/workshops").statusCode());
        var customer=new HashMap<String,Object>();customer.put("fullName","Cliente de prueba");customer.put("personalEmail","customer-"+UUID.randomUUID()+"@example.invalid");customer.put("personalPhone","5512345678");
        var body=Map.of("customer",customer,"workshopId",workshop);
        var creation=request(receptionist,"POST","/api/customers",body,true);
        assertEquals(200,creation.statusCode(),creation.body());long id=json.readTree(creation.body()).get("id").asLong();
        assertEquals(409,request(receptionist,"POST","/api/customers",body,true).statusCode());
        customer.put("alias","Actualizado por recepción");
        assertEquals(200,request(receptionist,"PUT","/api/customers/"+id,body,true).statusCode());
        assertEquals("Actualizado por recepción",json.readTree(receptionist.get("/api/customers/"+id).body()).get("alias").asText());
        assertEquals(403,request(receptionist,"DELETE","/api/customers/"+id,Map.of(),true).statusCode());
        assertEquals(403,request(receptionist,"POST","/api/workshops/setup",Map.of(),true).statusCode());
        assertEquals(403,request(receptionist,"POST","/api/employees",Map.of(),true).statusCode());
        assertEquals(403,receptionist.get("/api/reports").statusCode());
        for(String role:List.of("MECHANIC","CLIENT")) {
            var other=authenticated(account(role,true));
            assertEquals(403,request(other,"POST","/api/customers",body,true).statusCode());
            assertEquals(403,request(other,"PUT","/api/customers/"+id,body,true).statusCode());
        }
    }
    HttpResponse<String> upload(Browser browser, byte[] bytes, boolean csrf) throws Exception {
        String boundary="test-profile-boundary";
        var body=new java.io.ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/account/photo")).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(csrf)builder.header(browser.header,browser.token);
        return browser.client.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void profileDataAndPhotoArePrivateAndPersistent() throws Exception {
        var owner=account("RECEPTIONIST",true);var browser=authenticated(owner);
        var body=Map.of("name","Mi perfil actualizado","phone","5511112233","birthDate","1995-04-15","bio","Recepción del taller","role","ADMIN","id",999999,"email","changed@example.invalid");
        assertEquals(200,request(browser,"PUT","/api/account",body,true).statusCode());
        var fresh=authenticated(owner);var profile=json.readTree(fresh.get("/api/auth/me").body());
        assertEquals("Mi perfil actualizado",profile.get("name").asText());
        assertEquals("5511112233",profile.get("phone").asText());
        assertEquals("1995-04-15",profile.get("birthDate").asText());
        assertEquals("Recepción del taller",profile.get("bio").asText());
        assertEquals("RECEPTIONIST",profile.get("role").asText());assertEquals(owner.email(),profile.get("email").asText());
        assertEquals(400,request(fresh,"PUT","/api/account",Map.of("name","Nombre","birthDate","2999-01-01"),true).statusCode());
        assertEquals(400,request(fresh,"PUT","/api/account",Map.of("name","Nombre","phone","letras"),true).statusCode());
        assertEquals(403,request(fresh,"PUT","/api/account",body,false).statusCode());
        assertEquals(400,upload(fresh,"not a real png".getBytes(StandardCharsets.UTF_8),true).statusCode());
        var image=new java.awt.image.BufferedImage(30,30,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        assertEquals(403,upload(fresh,bytes.toByteArray(),false).statusCode());
        var result=upload(fresh,bytes.toByteArray(),true);assertEquals(200,result.statusCode(),result.body());
        String first=json.readTree(result.body()).get("photoUrl").asText();
        var photo=fresh.get(first);assertEquals(200,photo.statusCode());assertEquals("image/png",photo.headers().firstValue("content-type").orElse(""));
        assertTrue(photo.headers().firstValue("cache-control").orElse("").contains("no-store"));
        assertEquals(401,new Browser().get(first).statusCode());
        var other=authenticated(account("CLIENT",true));assertEquals(404,other.get(first).statusCode());
        result=upload(fresh,bytes.toByteArray(),true);assertEquals(200,result.statusCode());assertNotEquals(first,json.readTree(result.body()).get("photoUrl").asText());
        assertEquals(200,request(fresh,"DELETE","/api/account/photo",Map.of(),true).statusCode());
        assertEquals(404,fresh.get("/api/account/photo").statusCode());
        assertTrue(json.readTree(fresh.get("/api/auth/me").body()).get("photoUrl").isNull());
    }
}
