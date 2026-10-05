package mx.tallermeco.customer;

import mx.tallermeco.customer.dto.CreateCustomerRequest;
import mx.tallermeco.customer.dto.CustomerResponse;
import mx.tallermeco.customer.dto.WorkshopResponse;
import mx.tallermeco.customer.repository.CustomerRepository;
import mx.tallermeco.identity.Actor;
import mx.tallermeco.shared.Audit;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository repository;
    private final Actor actor;
    private final Audit audit;

    public CustomerService(CustomerRepository repository, Actor actor, Audit audit) {
        this.repository = repository; this.actor = actor; this.audit = audit;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request, long workshopId) {
        if (!(actor.is("ADMIN") || actor.is("RECEPTIONIST"))) throw new AccessDeniedException("Solo administración o recepción");
        validate(request);
        String email = CustomerRepository.normalizeEmail(request.personalEmail());
        String phone = CustomerRepository.normalizePhone(request.personalPhone());
        String name = request.fullName().trim().replaceAll("\\s+", " ");
        if (email != null && repository.existsByPersonalEmail(email)) throw duplicate("El email personal ya está registrado");
        if (phone != null && repository.existsByPersonalPhone(phone)) throw duplicate("El teléfono personal ya está registrado");
        if (repository.existsByNameAndBirthDate(name, request.birthDate())) throw duplicate("El cliente ya está registrado");
        var normalized = new CreateCustomerRequest(name, trim(request.alias()), trim(request.alternativeContactName()), request.birthDate(),
                phone, CustomerRepository.normalizePhone(request.workPhone()), email, CustomerRepository.normalizeEmail(request.workEmail()),
                request.photoReference(), trim(request.street()), trim(request.neighborhood()), trim(request.municipality()), trim(request.state()), trim(request.postalCode()));
        try {
            long id = repository.create(normalized);
            repository.associateWithWorkshop(id, workshopId);
            audit.record(actor.id(), "CUSTOMER_CREATED", "customer", id, java.util.Map.of("workshopId", workshopId));
            return repository.findById(id).orElseThrow();
        } catch (DataIntegrityViolationException ex) { throw duplicate("El cliente o la asociación ya existe"); }
    }

    public CustomerResponse find(long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado")); }
    @Transactional public CustomerResponse update(long id, CreateCustomerRequest request) {
        if (!(actor.is("ADMIN") || actor.is("RECEPTIONIST"))) throw new AccessDeniedException("Solo administración o recepción");
        validate(request); String email=CustomerRepository.normalizeEmail(request.personalEmail()), phone=CustomerRepository.normalizePhone(request.personalPhone());
        var current=find(id); var byEmail=email==null?java.util.Optional.<CustomerResponse>empty():repository.findByPersonalEmail(email);
        if (byEmail.isPresent() && byEmail.get().id()!=id) throw duplicate("El email personal ya está registrado");
        var byPhone=phone==null?java.util.Optional.<CustomerResponse>empty():repository.findByPersonalPhone(phone);
        if (byPhone.isPresent() && byPhone.get().id()!=id) throw duplicate("El teléfono personal ya está registrado");
        repository.update(id,new CreateCustomerRequest(request.fullName().trim().replaceAll("\\s+"," "),trim(request.alias()),trim(request.alternativeContactName()),request.birthDate(),phone,CustomerRepository.normalizePhone(request.workPhone()),email,CustomerRepository.normalizeEmail(request.workEmail()),request.photoReference(),trim(request.street()),trim(request.neighborhood()),trim(request.municipality()),trim(request.state()),trim(request.postalCode())));
        audit.record(actor.id(),"CUSTOMER_UPDATED","customer",id,java.util.Map.of()); return find(id);
    }
    public List<WorkshopResponse> workshops(long id) { return repository.findWorkshops(id); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static ResponseStatusException duplicate(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void validate(CreateCustomerRequest r) {
        if (r == null || r.fullName() == null || r.fullName().trim().length() < 2 || r.fullName().matches(".*\\d.*")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre completo inválido");
        if (r.birthDate() != null && r.birthDate().isAfter(LocalDate.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de nacimiento no puede ser futura");
        if (r.personalEmail() != null && !r.personalEmail().isBlank() && !r.personalEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email personal inválido");
        if (r.workEmail() != null && !r.workEmail().isBlank() && !r.workEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email laboral inválido");
        if (r.personalPhone() != null && !r.personalPhone().isBlank() && !r.personalPhone().matches("^[+()\\- 0-9]{7,30}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teléfono personal inválido");
        if (r.workPhone() != null && !r.workPhone().isBlank() && !r.workPhone().matches("^[+()\\- 0-9]{7,30}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teléfono laboral inválido");
        if (r.postalCode() != null && !r.postalCode().isBlank() && !r.postalCode().matches("^[A-Za-z0-9 -]{3,12}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código postal inválido");
    }
}
