import {api} from '../../../api'
import type {StatusKind,StatusData,EntityStatus} from '../types/status'
export const statusApi={
 list:(kind:StatusKind)=>api<EntityStatus[]>(`/statuses/${kind}`),
 create:(kind:StatusKind,data:StatusData)=>api<EntityStatus>(`/statuses/${kind}`,'POST',data),
 update:(kind:StatusKind,id:number,data:StatusData)=>api<EntityStatus>(`/statuses/${kind}/${id}`,'PUT',data),
 delete:(kind:StatusKind,id:number,version:number)=>api<void>(`/statuses/${kind}/${id}?version=${version}`,'DELETE')
}
