package mx.tallermeco.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mx.tallermeco.identity.Actor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workshops")
public class WorkshopSetupController {
    private final JdbcTemplate jdbc; private final Actor actor; private final WorkshopSetupService setup;
    public WorkshopSetupController(JdbcTemplate jdbc, Actor actor, WorkshopSetupService setup) { this.jdbc = jdbc; this.actor = actor; this.setup=setup; }
    public record Setup(@NotBlank @Size(max=160) String companyName, @NotBlank @Size(max=160) String workshopName) {}
    @GetMapping public List<Map<String,Object>> list() { return jdbc.queryForList("SELECT id,company_id,name,active FROM workshop WHERE active=true ORDER BY id"); }
    @PostMapping("/setup") public Map<String,Long> setup(@Valid @RequestBody Setup request) {
        actor.admin(); return setup.setup(request.companyName(), request.workshopName(), actor.id());
    }
}
