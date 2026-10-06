package mx.tallermeco.customer.dto;
public record UpdateCustomerRequest(CustomerData customer,long workshopId,Long version) {}
