package mx.tallermeco.customer.dto;
import java.util.List;
public record CustomerPage(List<CustomerResponse> items,int page,int pageSize,long totalItems,long totalPages) {}
