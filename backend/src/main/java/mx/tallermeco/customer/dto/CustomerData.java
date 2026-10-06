package mx.tallermeco.customer.dto;
import java.time.LocalDate;

/** Editable customer fields, shared by distinct create/update envelopes. Age and image ownership are server-managed. */
public record CustomerData(
 String givenName,String paternalSurname,String maternalSurname,String curp,String rfc,LocalDate birthDate,
 String alias,String alternativeContactName,String personalPhone,String cellPhone,String workPhone,
 String personalEmail,String workEmail,String street,String neighborhood,String municipality,String state,String postalCode) {
 public String fullName(){return givenName+" "+paternalSurname+(maternalSurname==null?"":" "+maternalSurname);}
}
