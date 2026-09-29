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
            System.out.println("P2-01: 10 HTTP authentication checks PASSED against real MariaDB.");
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private static final String PASSWORD = "P2-01-test-only-password";

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
}
