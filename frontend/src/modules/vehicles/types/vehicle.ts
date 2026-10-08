export type VehicleData={vin:string;plate:string;make:string;model:string;trim:string;year:string;color:string;odometer:string}
export type Vehicle={id:number;customerId:number;customerName:string;vin:string|null;plate:string|null;make:string;model:string;trim:string|null;year:number|null;color:string|null;odometer:number|null;statusId:number;statusCode:string;statusDescription:string;allowsOperations:boolean;version:number;inService:boolean}
export type VehiclePage={items:Vehicle[];page:number;pageSize:number;totalItems:number;totalPages:number}
