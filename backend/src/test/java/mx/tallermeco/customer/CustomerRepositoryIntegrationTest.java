package mx.tallermeco.customer;

import mx.tallermeco.TallerApplication;
import mx.tallermeco.customer.dto.CreateCustomerRequest;
import mx.tallermeco.customer.repository.CustomerRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Objects;

public final class CustomerRepositoryIntegrationTest {
    public static void main(String[] args) {
        System.setProperty("spring.datasource.url", required("CUSTOMER_TEST_URL"));
        System.setProperty("spring.datasource.username", required("CUSTOMER_TEST_USER"));
        System.setProperty("spring.datasource.password", required("CUSTOMER_TEST_PASSWORD"));
        System.setProperty("spring.flyway.url", required("CUSTOMER_TEST_URL"));
        System.setProperty("spring.flyway.user", required("CUSTOMER_TEST_OWNER"));
        System.setProperty("spring.flyway.password", required("CUSTOMER_TEST_OWNER_PASSWORD"));
        System.setProperty("app.bootstrap.password", "");
        System.setProperty("server.port", "0");
        try (ConfigurableApplicationContext context = SpringApplication.run(TallerApplication.class)) {
            CustomerRepository repository = context.getBean(CustomerRepository.class);
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("INSERT INTO company(name) VALUES ('P2-03 Company')");
            Long companyId = jdbc.queryForObject("SELECT id FROM company WHERE name='P2-03 Company'", Long.class);
            jdbc.update("INSERT INTO workshop(company_id,name) VALUES (?, 'P2-03 Workshop')", companyId);
            long workshopId = Objects.requireNonNull(jdbc.queryForObject(
                    "SELECT id FROM workshop WHERE company_id=?", Long.class, companyId));
            var request = new CreateCustomerRequest("Ana Pérez", "Ana", null, LocalDate.of(1990, 2, 3),
                    "55 1234 5678", null, " ANA@Example.COM ", null, null,
                    "Calle 1", "Centro", "CDMX", "CDMX", "01000");
            long customerId = repository.create(request);
            assert repository.findById(customerId).isPresent();
            assert repository.findByPersonalEmail("ana@example.com").orElseThrow().id() == customerId;
            assert repository.findByPersonalPhone("5512345678").orElseThrow().id() == customerId;
            assert repository.findByNameAndBirthDate("Ana Pérez", request.birthDate()).orElseThrow().id() == customerId;
            assert repository.existsByPersonalEmail("ana@example.com");
            assert repository.existsByPersonalPhone("55-1234-5678");
            assert repository.existsByNameAndBirthDate("Ana Pérez", request.birthDate());
            assert !repository.isAssociatedWithWorkshop(customerId, workshopId);
            repository.associateWithWorkshop(customerId, workshopId);
            assert repository.isAssociatedWithWorkshop(customerId, workshopId);
            assert repository.findWorkshops(customerId).size() == 1;
            try {
                repository.associateWithWorkshop(customerId, workshopId);
                throw new AssertionError("duplicate CustomerWorkshop association accepted");
            } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                // Composite primary key remains enforced by V4.
            }
            System.out.println("P2-03: CustomerRepository persistence checks PASSED against temporary MariaDB.");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
