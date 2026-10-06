package mx.tallermeco.workshop.management;
public record UpdateWorkshopRequest(WorkshopData workshop,Long version,@jakarta.validation.constraints.NotNull Boolean active) {}
