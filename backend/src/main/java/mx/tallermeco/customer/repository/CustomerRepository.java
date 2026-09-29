package mx.tallermeco.customer.repository;

import mx.tallermeco.customer.dto.CreateCustomerRequest;
import mx.tallermeco.customer.dto.CustomerResponse;
import mx.tallermeco.customer.dto.WorkshopResponse;
import mx.tallermeco.customer.model.Customer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** All SQL for Customer and CustomerWorkshop persistence lives here. */
@Repository
public class CustomerRepository {
    private static final String CUSTOMER_COLUMNS = """
            id,user_id,full_name,alias,alternative_contact_name,birth_date,
            personal_phone,work_phone,personal_email,work_email,photo_reference,
            street,neighborhood,municipality,state,postal_code,active,version
            """;

    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CustomerResponse> findById(long id) {
        return one("SELECT " + CUSTOMER_COLUMNS + " FROM customer WHERE id=?", id);
    }

    public Optional<CustomerResponse> findByPersonalEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) return Optional.empty();
        return one("SELECT " + CUSTOMER_COLUMNS + " FROM customer WHERE personal_email_normalized=?", normalized);
    }

    public Optional<CustomerResponse> findByPersonalPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized == null) return Optional.empty();
        return one("SELECT " + CUSTOMER_COLUMNS + " FROM customer WHERE personal_phone_normalized=?", normalized);
    }

    public Optional<CustomerResponse> findByNameAndBirthDate(String fullName, LocalDate birthDate) {
        if (fullName == null || birthDate == null) return Optional.empty();
        return one("SELECT " + CUSTOMER_COLUMNS + " FROM customer WHERE full_name=? AND birth_date=?", fullName, birthDate);
    }

    public boolean existsByPersonalEmail(String email) {
        String normalized = normalizeEmail(email);
        return normalized != null && exists("SELECT 1 FROM customer WHERE personal_email_normalized=? LIMIT 1", normalized);
    }

    public boolean existsByPersonalPhone(String phone) {
        String normalized = normalizePhone(phone);
        return normalized != null && exists("SELECT 1 FROM customer WHERE personal_phone_normalized=? LIMIT 1", normalized);
    }

    public boolean existsByNameAndBirthDate(String fullName, LocalDate birthDate) {
        return fullName != null && birthDate != null
                && exists("SELECT 1 FROM customer WHERE full_name=? AND birth_date=? LIMIT 1", fullName, birthDate);
    }

    public long create(CreateCustomerRequest request) {
        String sql = """
                INSERT INTO customer(
                    full_name,alias,alternative_contact_name,birth_date,personal_phone,work_phone,
                    personal_email,work_email,photo_reference,street,neighborhood,municipality,state,postal_code
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, request.fullName());
            statement.setString(2, request.alias());
            statement.setString(3, request.alternativeContactName());
            setDate(statement, 4, request.birthDate());
            statement.setString(5, request.personalPhone());
            statement.setString(6, request.workPhone());
            statement.setString(7, request.personalEmail());
            statement.setString(8, request.workEmail());
            statement.setString(9, request.photoReference());
            statement.setString(10, request.street());
            statement.setString(11, request.neighborhood());
            statement.setString(12, request.municipality());
            statement.setString(13, request.state());
            statement.setString(14, request.postalCode());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("Customer insert did not return an id");
        return key.longValue();
    }

    public void associateWithWorkshop(long customerId, long workshopId) {
        jdbc.update("INSERT INTO customer_workshop(customer_id,workshop_id) VALUES (?,?)", customerId, workshopId);
    }

    public boolean isAssociatedWithWorkshop(long customerId, long workshopId) {
        return exists("SELECT 1 FROM customer_workshop WHERE customer_id=? AND workshop_id=? LIMIT 1",
                customerId, workshopId);
    }

    public List<WorkshopResponse> findWorkshops(long customerId) {
        return jdbc.query("""
                SELECT w.id,w.company_id,w.name,w.active
                FROM workshop w JOIN customer_workshop cw ON cw.workshop_id=w.id
                WHERE cw.customer_id=? ORDER BY w.id
                """, (rs, rowNum) -> new WorkshopResponse(rs.getLong("id"), rs.getLong("company_id"),
                rs.getString("name"), rs.getBoolean("active")), customerId);
    }

    private Optional<CustomerResponse> one(String sql, Object... args) {
        List<Customer> customers = jdbc.query(sql, this::mapCustomer, args);
        return customers.stream().findFirst().map(CustomerResponse::from);
    }

    private boolean exists(String sql, Object... args) {
        return !jdbc.queryForList(sql, args).isEmpty();
    }

    private Customer mapCustomer(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Date birthDate = rs.getDate("birth_date");
        return new Customer(rs.getLong("id"), (Long) rs.getObject("user_id"), rs.getString("full_name"),
                rs.getString("alias"), rs.getString("alternative_contact_name"),
                birthDate == null ? null : birthDate.toLocalDate(), rs.getString("personal_phone"),
                rs.getString("work_phone"), rs.getString("personal_email"), rs.getString("work_email"),
                rs.getString("photo_reference"), rs.getString("street"), rs.getString("neighborhood"),
                rs.getString("municipality"), rs.getString("state"), rs.getString("postal_code"),
                rs.getBoolean("active"), rs.getLong("version"));
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate date) throws java.sql.SQLException {
        if (date == null) statement.setDate(index, null); else statement.setDate(index, Date.valueOf(date));
    }

    static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    static String normalizePhone(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }
}
