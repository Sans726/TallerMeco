package mx.tallermeco.customer.dto;
public record CustomerListQuery(long workshopId,int page,int pageSize,String sort,String direction,String query) {}
