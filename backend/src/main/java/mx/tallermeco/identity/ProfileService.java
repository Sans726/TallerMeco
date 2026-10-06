package mx.tallermeco.identity;

import jakarta.validation.constraints.*;
import mx.tallermeco.shared.Audit;
import mx.tallermeco.shared.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;

@Service
public class ProfileService {
    private final Store db;
    private final Audit audit;
    public ProfileService(Store db, Audit audit) { this.db = db; this.audit = audit; }

    public record Profile(
        @NotBlank @Size(min=2, max=160) String name,
        @Pattern(regexp="^$|[+()0-9 \\-]{7,30}", message="Teléfono inválido") String phone,
        @PastOrPresent(message="La fecha de nacimiento no puede ser futura") LocalDate birthDate,
        @Size(max=500) String bio) {}

    @Transactional
    public void update(long userId, Profile profile) {
        String name = profile.name().trim();
        if (name.length() < 2) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Nombre inválido");
        db.update("UPDATE app_user SET display_name=?,phone=?,birth_date=?,bio=? WHERE id=?",
            name, profile.phone() == null ? "" : profile.phone().trim(), profile.birthDate(),
            profile.bio() == null ? "" : profile.bio().trim(), userId);
        // Account preferences must not bypass managed customer validation or workshop authorization.
        db.update("UPDATE employee SET full_name=? WHERE user_id=?", name, userId);
        audit.record(userId, "PROFILE_UPDATED", "user", userId, Map.of());
    }
}
