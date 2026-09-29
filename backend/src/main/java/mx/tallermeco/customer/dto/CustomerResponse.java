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
        boolean active,
        long version
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.id(), customer.userId(), customer.fullName(), customer.alias(),
                customer.alternativeContactName(), customer.birthDate(), customer.personalPhone(),
                customer.workPhone(), customer.personalEmail(), customer.workEmail(), customer.photoReference(),
                customer.street(), customer.neighborhood(), customer.municipality(), customer.state(),
                customer.postalCode(), customer.active(), customer.version());
    }
}
