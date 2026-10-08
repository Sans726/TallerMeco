package mx.tallermeco.customer.dto;
public record CustomerListQuery(long workshopId,int page,int pageSize,String sort,String direction,String query,Long statusId) {public CustomerListQuery(long workshopId,int page,int pageSize,String sort,String direction,String query){this(workshopId,page,pageSize,sort,direction,query,null);}}
