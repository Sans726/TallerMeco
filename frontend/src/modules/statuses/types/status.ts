export type StatusKind='customers'|'vehicles'
export type EntityStatus={id:number;code:string;description:string;allowsOperations:boolean;system:boolean;version:number;usageCount:number}
export type StatusData={code:string;description:string;allowsOperations:boolean;version?:number}
export const canDeleteStatus=(s:EntityStatus)=>!s.system&&s.usageCount===0
export const statusLabel=(s:{code:string})=>s.code==='ACTIVE'?'Activo':s.code==='SUSPENDED'?'Suspendido':s.code.replace(/_/g,' ')
