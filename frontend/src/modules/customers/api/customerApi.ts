import {api} from '../../../api'
export const customerApi={create:(customer:any,workshopId:number)=>api('/customers','POST',{customer,workshopId}),uploadPhoto:(id:number,file:File)=>api(`/customers/${id}/photo`,'POST',undefined)}
