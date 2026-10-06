import {customerApi} from '../api/customerApi'
export const customerFacade={...customerApi,createCustomer:customerApi.create,updateCustomer:customerApi.update}
