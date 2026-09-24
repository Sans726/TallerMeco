import { reactive } from 'vue'
import {demoApi,demoLogin,demoUser} from './demo'
export const isDemo=import.meta.env.VITE_DEMO==='true'
export type Row = Record<string, any>
export const session = reactive<{user:Row|null;loading:boolean}>({user:null,loading:true})
let csrf:{token:string;header:string}|null=null
export async function csrfRefresh(){const r=await fetch('/api/auth/csrf');csrf=await r.json()}
export async function api<T=any>(path:string,method='GET',body?:unknown):Promise<T>{
 if(isDemo)return demoApi(path,method,body)
 if(!csrf && method!=='GET') await csrfRefresh()
 const headers:Record<string,string>={}
 if(method!=='GET' && csrf)headers[csrf.header]=csrf.token
 if(body!==undefined)headers['Content-Type']='application/json'
 const r=await fetch('/api'+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)})
 if(!r.ok){let message='No se pudo completar la operación';try{message=(await r.json()).message||message}catch{}
  if(r.status===401 && path!=='/auth/me'){session.user=null;location.hash='/login'}
  throw new Error(message)
 }
 return r.status===204||r.headers.get('content-length')==='0'?undefined as T:await r.text().then(t=>t?JSON.parse(t):undefined)
}
export async function login(email:string,password:string){if(isDemo){demoLogin(email);session.user=demoUser;return;}await csrfRefresh();const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded',[csrf!.header]:csrf!.token},body:new URLSearchParams({email,password})});if(!r.ok){let m='No se pudo iniciar sesión';try{m=(await r.json()).message}catch{}throw new Error(m)}await csrfRefresh();session.user=await api('/auth/me')}
export async function logout(){await api('/auth/logout','POST');session.user=null;csrf=null;location.hash='/login'}
export const money=(v:unknown)=>new Intl.NumberFormat('es-MX',{style:'currency',currency:'MXN'}).format(Number(v||0))
export const date=(v:unknown)=>v?new Date(String(v).replace(' ','T')+(String(v).includes('Z')||String(v).includes('+')?'':'Z')).toLocaleDateString('es-MX',{day:'numeric',month:'short',year:'numeric'}):'—'
export const statuses:Record<string,string>={RECEIVED:'Recibido',DIAGNOSIS:'En diagnóstico',AWAITING_APPROVAL:'Por autorizar',IN_PROGRESS:'En reparación',READY:'Listo para entrega',DELIVERED:'Entregado',CANCELLED:'Cancelado',PENDING:'Pendiente',DONE:'Terminado'}
export const roles:Record<string,string>={ADMIN:'Administración',MECHANIC:'Mecánico',CLIENT:'Cliente'}
export const alert=reactive({message:'',type:'success'})
let timer:ReturnType<typeof setTimeout>
export function notify(message:string,type='success'){alert.message=message;alert.type=type;clearTimeout(timer);timer=setTimeout(()=>alert.message='',6000)}
