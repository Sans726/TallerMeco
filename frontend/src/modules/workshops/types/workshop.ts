export type WorkshopData={name:string;legalName:string;rfc:string;phone:string;email:string;street:string;neighborhood:string;municipality:string;state:string;postalCode:string}
export type Workshop=WorkshopData & {id:number;companyId:number;version:number;active:boolean;bannerReference:string|null}
export type Company={id:number;name:string}
export type AccessUser={id:number;email:string;name?:string;active?:boolean}
