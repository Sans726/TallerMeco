package mx.tallermeco.config;

import mx.tallermeco.shared.Audit;
import mx.tallermeco.shared.Store;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean UserDetailsService users(Store db) {
        return email -> {
            var rows = db.list("SELECT id,email,password_hash,enabled FROM app_user WHERE email=?",
                    email.trim().toLowerCase(Locale.ROOT));
            if (rows.isEmpty()) throw new UsernameNotFoundException("Credenciales inválidas");
            var user = rows.getFirst();
            String[] roles = db.list("SELECT role_code FROM user_role WHERE user_id=?", user.get("id"))
                    .stream().map(r -> r.get("role_code").toString()).toArray(String[]::new);
            if (roles.length == 0) throw new UsernameNotFoundException("Cuenta sin roles");
            boolean enabled = Boolean.TRUE.equals(user.get("enabled")) || "1".equals(user.get("enabled").toString());
            return User.withUsername(user.get("email").toString())
                    .password(user.get("password_hash").toString()).disabled(!enabled).roles(roles).build();
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http, Store db, Audit audit) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/forgot", "/api/auth/reset").permitAll()
                .requestMatchers("/api/auth/me", "/api/account", "/api/account/**")
                    .hasAnyRole("ADMIN", "RECEPTIONIST", "MECHANIC", "CLIENT")
                .requestMatchers("/api/employees", "/api/employees/**", "/api/reports", "/api/audit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/addresses/postal-codes/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET,"/api/statuses/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/statuses/**").hasRole("ADMIN")
                .requestMatchers("/api/vehicles", "/api/vehicles/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/customers", "/api/customers/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers(HttpMethod.GET, "/api/workshops", "/api/workshops/**").hasAnyRole("ADMIN", "RECEPTIONIST")
                .requestMatchers("/api/workshops", "/api/workshops/**").hasRole("ADMIN")
                // Customer and workshop scope checks are enforced again in services.
                .requestMatchers("/api/**").hasAnyRole("ADMIN", "MECHANIC", "CLIENT")
                .anyRequest().permitAll())
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            // Keep Spring Security's session-backed CSRF protection, including login and logout.
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    var user = db.one("SELECT id,version FROM app_user WHERE email=?", authentication.getName());
                    request.getSession().setAttribute("credentialVersion", Store.number(user.get("version")));
                    audit.record(Store.number(user.get("id")), "LOGIN", "user", Store.number(user.get("id")), Map.of());
                    json(response, 200, "{\"ok\":true}");
                })
                .failureHandler((request, response, error) -> {
                    audit.record(null, "LOGIN_FAILED", "authentication", null, Map.of());
                    json(response, 401, "{\"message\":\"Correo o contraseña incorrectos\"}");
                }))
            .logout(l -> l.logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, error) -> json(response, 401, "{\"message\":\"Inicia sesión para continuar\"}"))
                .accessDeniedHandler((request, response, error) -> json(response, 403, "{\"message\":\"Operación no permitida. Actualiza la página e intenta de nuevo.\"}")))
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
            .addFilterBefore(new GuardFilter(db), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(body);
    }

    static class GuardFilter extends OncePerRequestFilter {
        final Store db;
        final ConcurrentHashMap<String, long[]> rates = new ConcurrentHashMap<>();
        GuardFilter(Store db) { this.db = db; }

        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                  FilterChain chain) throws ServletException, IOException {
            if (request.getMethod().equals("POST") && Set.of("/api/auth/login", "/api/auth/forgot", "/api/auth/reset", "/api/auth/register").contains(request.getRequestURI())) {
                long now = System.currentTimeMillis();
                rates.entrySet().removeIf(e -> now - e.getValue()[0] > 900000);
                var slot = rates.computeIfAbsent(request.getRemoteAddr() + request.getRequestURI(), k -> new long[]{now, 0});
                synchronized (slot) {
                    if (++slot[1] > 30) {
                        json(response, 429, "{\"message\":\"Demasiados intentos. Espera 15 minutos.\"}");
                        return;
                    }
                }
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                HttpSession session = request.getSession(false);
                var rows = db.list("SELECT id,version FROM app_user WHERE email=? AND enabled=true", authentication.getName());
                Object version = session == null ? null : session.getAttribute("credentialVersion");
                boolean valid = !rows.isEmpty() && version instanceof Number
                        && Store.number(rows.getFirst().get("version")) == ((Number) version).longValue();
                if (valid) {
                    var databaseRoles = db.list("SELECT role_code FROM user_role WHERE user_id=?", rows.getFirst().get("id"))
                            .stream().map(r -> "ROLE_" + r.get("role_code")).collect(Collectors.toSet());
                    var sessionRoles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
                    valid = !databaseRoles.isEmpty() && databaseRoles.equals(sessionRoles);
                }
                if (!valid) {
                    if (session != null) session.invalidate();
                    SecurityContextHolder.clearContext();
                    json(response, 401, "{\"message\":\"Tu acceso cambió. Inicia sesión nuevamente.\"}");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
