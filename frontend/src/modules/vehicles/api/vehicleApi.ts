import {api} from '../../../api'
import type {Vehicle,VehicleData,VehiclePage} from '../types/vehicle'
export const vehicleApi={
 list:(workshopId:number,page=1,direction='ASC',query='',statusId?:number)=>api<VehiclePage>(`/vehicles?workshopId=${workshopId}&page=${page}&pageSize=10&sort=make&direction=${direction}&query=${encodeURIComponent(query)}${statusId?`&statusId=${statusId}`:''}`),
 get:(id:number,workshopId:number)=>api<Vehicle>(`/vehicles/${id}?workshopId=${workshopId}`),
 create:(customerId:number,workshopId:number,vehicle:VehicleData)=>api<Vehicle>('/vehicles','POST',{customerId,workshopId,vehicle}),
 update:(id:number,workshopId:number,version:number,vehicle:VehicleData)=>api<Vehicle>(`/vehicles/${id}`,'PUT',{workshopId,version,vehicle}),
 status:(id:number,workshopId:number,version:number,statusId:number)=>api<Vehicle>(`/vehicles/${id}/status`,'PATCH',{workshopId,version,statusId}),
 personal:()=>api<Record<string,any>[]>('/self/vehicles'),
 personalWorkshops:()=>api<{id:number;name:string}[]>('/self/vehicles/workshops'),
 createPersonal:(workshopId:number,vehicle:VehicleData)=>api<{id:number}>('/self/vehicles','POST',{workshopId,vehicle})
}
