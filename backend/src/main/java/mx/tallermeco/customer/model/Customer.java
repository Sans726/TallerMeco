package mx.tallermeco.customer.model;

import java.time.LocalDate;

/** Persistence/domain representation of a customer. Age is deliberately not stored. */
public record Customer(
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
) {}
