import {api} from '../../../api'
import type {Workshop,WorkshopData,Company,AccessUser} from '../types/workshop'
export const workshopApi={
 list:()=>api<Workshop[]>('/workshops?activeOnly=false'),get:(id:number)=>api<Workshop>(`/workshops/${id}`),companies:()=>api<Company[]>('/workshops/companies'),
 create:(workshop:WorkshopData,companyId:number|null)=>api<Workshop>('/workshops','POST',{workshop,companyId}),
 update:(id:number,workshop:WorkshopData,version:number,active:boolean)=>api<Workshop>(`/workshops/${id}`,'PUT',{workshop,version,active}),
 upload:(id:number,file:File)=>{const body=new FormData();body.append('file',file);return api<{bannerReference:string}>(`/workshops/${id}/banner`,'POST',body)},
 users:(id:number)=>api<AccessUser[]>(`/workshops/${id}/users`),accessUsers:()=>api<AccessUser[]>('/workshops/access-users'),
 assign:(id:number,userId:number,active:boolean)=>api<void>(`/workshops/${id}/users/${userId}`,'PUT',{active}),
 bannerUrl:(w:Workshop)=>`/api/workshops/${w.id}/banner?v=${encodeURIComponent(w.bannerReference??'')}`
}
