import {api,uploadCustomerPhoto} from '../../../api'
export const customerFacade={
 async createCustomer(customer:any,workshopId:number){return api('/customers','POST',{customer,workshopId})},
 async list(){return api('/customers')},
 async get(id:number){return api(`/customers/${id}`)},
 async updateCustomer(id:number,customer:any,workshopId:number){return api(`/customers/${id}`,'PUT',{customer,workshopId})},
 async workshopsList(){return api('/workshops')},
 async workshops(id:number){return api(`/customers/${id}/workshops`)},
 async uploadPhoto(id:number,file:File){return uploadCustomerPhoto(id,file)}
}
