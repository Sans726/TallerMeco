package mx.tallermeco.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Input accepted by the future CustomerService. It is not a web/controller contract yet. */
public record CreateCustomerRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 120) String alias,
        @Size(max = 160) String alternativeContactName,
        LocalDate birthDate,
        @Size(max = 30) String personalPhone,
        @Size(max = 30) String workPhone,
        @Email @Size(max = 254) String personalEmail,
        @Email @Size(max = 254) String workEmail,
        @Size(max = 512) String photoReference,
        @Size(max = 180) String street,
        @Size(max = 120) String neighborhood,
        @Size(max = 120) String municipality,
        @Size(max = 120) String state,
        @Size(max = 12) String postalCode
) {}
