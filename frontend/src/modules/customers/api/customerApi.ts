import {api} from '../../../api'
import type {Customer,CustomerData,CustomerPage,CustomerWorkshop,WorkshopOption} from '../types/customer'
const context=(workshopId:number)=>`workshopId=${workshopId}`
export const customerApi={
 unassigned:(page:number,direction:string,query:string)=>api<CustomerPage>(`/customers/unassigned?page=${page}&pageSize=10&direction=${direction}&query=${encodeURIComponent(query)}`),
 initialWorkshop:(id:number,workshopId:number)=>api<void>(`/customers/${id}/initial-workshop`,'POST',{workshopId}),
 list:(workshopId:number,page:number,direction:string,query='')=>api<CustomerPage>(`/customers?${context(workshopId)}&page=${page}&pageSize=10&sort=name&direction=${direction}&query=${encodeURIComponent(query)}`),
 get:(id:number,workshopId:number)=>api<Customer>(`/customers/${id}?${context(workshopId)}`),
 create:(customer:CustomerData,workshopId:number)=>api<Customer>('/customers','POST',{customer,workshopId}),
 update:(id:number,customer:CustomerData,workshopId:number,version:number)=>api<Customer>(`/customers/${id}`,'PUT',{customer,workshopId,version}),
 status:(id:number,workshopId:number,active:boolean)=>api<Customer>(`/customers/${id}/status`,'PATCH',{workshopId,active}),
 workshopsList:()=>api<WorkshopOption[]>('/workshops'),
 workshops:(id:number,workshopId:number)=>api<CustomerWorkshop[]>(`/customers/${id}/workshops?${context(workshopId)}`),
 associate:(id:number,workshopId:number,targetWorkshopId:number,mode:'ASSOCIATE'|'REASSIGN')=>api<void>(`/customers/${id}/workshops`,'POST',{workshopId,targetWorkshopId,mode}),
 associationStatus:(id:number,workshopId:number,target:number,active:boolean)=>api<void>(`/customers/${id}/workshops/${target}`,'PATCH',{workshopId,active}),
 uploadPhoto:(id:number,workshopId:number,file:File)=>{const body=new FormData();body.append('file',file);return api<{photoReference:string}>(`/customers/${id}/photo?${context(workshopId)}`,'POST',body)},
 photoUrl:(reference:string,workshopId:number)=>`/api/customers/photos/${encodeURIComponent(reference)}?${context(workshopId)}`
}
