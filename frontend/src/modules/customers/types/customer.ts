export type CustomerData={givenName:string;paternalSurname:string;maternalSurname:string;curp:string;rfc:string;birthDate:string;alias:string;alternativeContactName:string;personalEmail:string;workEmail:string;personalPhone:string;cellPhone:string;workPhone:string;street:string;neighborhood:string;municipality:string;state:string;postalCode:string}
export type Customer=CustomerData & {id:number;fullName:string;active:boolean;version:number;age:number|null;photoReference:string|null}
export type CustomerPage={items:Customer[];page:number;pageSize:number;totalItems:number;totalPages:number}
export type WorkshopOption={id:number;name:string;active:boolean;companyId:number}
export type CustomerWorkshop=WorkshopOption & {associationActive:boolean}
