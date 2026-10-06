package mx.tallermeco.customer;
import mx.tallermeco.customer.dto.CustomerData;
import mx.tallermeco.customer.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=${AUTH_TEST_URL}","spring.datasource.username=${AUTH_TEST_USER}","spring.datasource.password=${AUTH_TEST_PASSWORD}",
 "spring.flyway.url=${AUTH_TEST_URL}","spring.flyway.user=${AUTH_TEST_OWNER}","spring.flyway.password=${AUTH_TEST_OWNER_PASSWORD}","app.bootstrap.password="})
class CustomerRepositoryIntegrationTest {
 @Autowired CustomerRepository repository;
 @Autowired JdbcTemplate jdbc;
 @Test void persistsCanonicalCustomerAndUniqueWorkshopAssociation(){
  jdbc.update("INSERT INTO company(name) VALUES ('repository company')");
  long company=jdbc.queryForObject("SELECT MAX(id) FROM company",Long.class);
  jdbc.update("INSERT INTO workshop(company_id,name) VALUES (?,'repository workshop')",company);
  long workshop=jdbc.queryForObject("SELECT MAX(id) FROM workshop",Long.class);
  var raw=new CustomerData("Ana","Pérez",null,null,null,LocalDate.of(1990,2,3),"Ana",null,"55 6543 2109",null,null," REPOSITORY@Example.COM ",null,"Calle 1","Centro","Ciudad de México","Ciudad de México","01000");
  var normalized=CustomerService.normalize(raw);long id=repository.create(normalized);
  assertEquals("ana pérez",repository.findById(id).orElseThrow().fullName());
  assertEquals(id,repository.findByPersonalEmail("repository@example.com").orElseThrow().id());
  assertEquals(id,repository.findByPersonalPhone("5565432109").orElseThrow().id());
  assertEquals(id,repository.findByNameAndBirthDate("ana pérez",raw.birthDate()).orElseThrow().id());
  assertFalse(repository.isAssociatedWithWorkshop(id,workshop));repository.associateWithWorkshop(id,workshop);
  assertTrue(repository.isAssociatedWithWorkshop(id,workshop));assertEquals(1,repository.findWorkshops(id).size());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->repository.associateWithWorkshop(id,workshop));
 }
}
