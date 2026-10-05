package mx.tallermeco.customer;

import mx.tallermeco.shared.Store;
import mx.tallermeco.shared.Audit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class WorkshopSetupService {
    private final Store db;
    private final Audit audit;
    public WorkshopSetupService(Store db, Audit audit) { this.db=db; this.audit=audit; }
    @Transactional
    public Map<String,Long> setup(String companyName, String workshopName, long actorId) {
        Store.require(db.count("SELECT COUNT(*) FROM workshop")==0, "La configuración del taller ya existe");
        long company=db.id("INSERT INTO company(name) VALUES (?)",companyName.trim());
        long workshop=db.id("INSERT INTO workshop(company_id,name) VALUES (?,?)",company,workshopName.trim());
        audit.record(actorId,"WORKSHOP_CREATED","workshop",workshop,Map.of("companyId",company));
        return Map.of("companyId",company,"workshopId",workshop);
    }
}
