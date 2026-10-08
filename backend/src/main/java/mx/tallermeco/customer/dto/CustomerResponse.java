package mx.tallermeco.customer.dto;

import mx.tallermeco.customer.model.Customer;

import java.time.LocalDate;

public record CustomerResponse(
        long id,
        Long userId,
        String fullName,
        String alias,
        String alternativeContactName,
        LocalDate birthDate,
        String personalPhone,
        String workPhone,
        String personalEmail,
        String workEmail,
        String photoReference,
        String street,
        String neighborhood,
        String municipality,
        String state,
        String postalCode,
        long statusId,String statusCode,String statusDescription,boolean allowsOperations,
        long version,
        String givenName,String paternalSurname,String maternalSurname,String curp,String rfc,String cellPhone
) {
    public Integer age() { return birthDate==null?null:java.time.Period.between(birthDate,LocalDate.now()).getYears(); }
    @com.fasterxml.jackson.annotation.JsonProperty("age") public Integer getAge() {return age();}
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.id(), customer.userId(), customer.fullName(), customer.alias(),
                customer.alternativeContactName(), customer.birthDate(), customer.personalPhone(),
                customer.workPhone(), customer.personalEmail(), customer.workEmail(), customer.photoReference(),
                customer.street(), customer.neighborhood(), customer.municipality(), customer.state(),
                customer.postalCode(), customer.statusId(),customer.statusCode(),customer.statusDescription(),customer.allowsOperations(), customer.version(),customer.givenName(),customer.paternalSurname(),customer.maternalSurname(),customer.curp(),customer.rfc(),customer.cellPhone());
    }
}
