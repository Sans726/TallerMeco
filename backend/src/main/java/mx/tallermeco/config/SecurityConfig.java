package mx.tallermeco.config;
import mx.tallermeco.shared.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();}
 @Bean UserDetailsService users(Store db){return email->{
  var rows=db.list("SELECT * FROM app_user WHERE email=?",email.trim().toLowerCase(java.util.Locale.ROOT));
  if(rows.isEmpty())throw new UsernameNotFoundException("Credenciales inválidas");
  var u=rows.getFirst(); var roles=db.list("SELECT role_code FROM user_role WHERE user_id=?",u.get("id")).stream().map(r->r.get("role_code").toString()).toArray(String[]::new);
  return User.withUsername(u.get("email").toString()).password(u.get("password_hash").toString()).disabled(!Boolean.TRUE.equals(u.get("enabled")) && !"1".equals(u.get("enabled").toString())).roles(roles).build();
 };}
 @Bean SecurityFilterChain security(HttpSecurity http,Store db,Audit audit) throws Exception {
  http.authorizeHttpRequests(a->a.requestMatchers("/api/auth/csrf","/api/auth/register","/api/auth/forgot","/api/auth/reset","/api/auth/login").permitAll().requestMatchers("/api/**").authenticated().anyRequest().permitAll())
   .formLogin(f->f.loginProcessingUrl("/api/auth/login").usernameParameter("email")
    .successHandler((q,r,a)->{var u=db.one("SELECT id,version FROM app_user WHERE email=?",a.getName());q.getSession().setAttribute("credentialVersion",Store.number(u.get("version")));audit.record(Store.number(u.get("id")),"LOGIN","user",Store.number(u.get("id")),java.util.Map.of());r.setContentType("application/json");r.getWriter().write("{\"ok\":true}");})
    .failureHandler((q,r,e)->{audit.record(null,"LOGIN_FAILED","authentication",null,java.util.Map.of());r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Correo o contraseña incorrectos\"}");}))
   .logout(l->l.logoutUrl("/api/auth/logout").logoutSuccessHandler((q,r,a)->r.setStatus(204)).deleteCookies("JSESSIONID"))
   .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->{r.setStatus(401);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Inicia sesión para continuar\"}");}).accessDeniedHandler((q,r,x)->{r.setStatus(403);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Operación no permitida. Actualiza la página e intenta de nuevo.\"}");}))
   .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
   .addFilterBefore(new GuardFilter(db),UsernamePasswordAuthenticationFilter.class);
  return http.build();
 }
 static class GuardFilter extends OncePerRequestFilter {
  final Store db; final ConcurrentHashMap<String,long[]> rates=new ConcurrentHashMap<>();
  GuardFilter(Store db){this.db=db;}
  @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws ServletException,IOException {
   if(q.getMethod().equals("POST") && java.util.Set.of("/api/auth/login","/api/auth/forgot","/api/auth/reset","/api/auth/register").contains(q.getRequestURI())) {
    long now=System.currentTimeMillis(); rates.entrySet().removeIf(e->now-e.getValue()[0]>900000);
    var slot=rates.computeIfAbsent(q.getRemoteAddr()+q.getRequestURI(),k->new long[]{now,0});
    synchronized(slot){if(++slot[1]>30){r.setStatus(429);r.setContentType("application/json");r.getWriter().write("{\"message\":\"Demasiados intentos. Espera 15 minutos.\"}");return;}}
   }
   var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
   if(authentication!=null && authentication.getPrincipal() instanceof UserDetails && q.getSession(false)!=null){
    var rows=db.list("SELECT version FROM app_user WHERE email=? AND enabled=true",authentication.getName());
    Object version=q.getSession().getAttribute("credentialVersion");
    if(rows.isEmpty() || version==null || Store.number(rows.getFirst().get("version"))!=((Number)version).longValue()){
     q.getSession().invalidate();org.springframework.security.core.context.SecurityContextHolder.clearContext();r.setStatus(401);return;
    }
   }
   chain.doFilter(q,r);
  }
 }
}
