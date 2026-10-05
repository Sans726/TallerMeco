import { reactive } from 'vue'

export type Row = Record<string, any>
export const session = reactive<{ user: Row | null; loading: boolean }>({ user: null, loading: true })
let csrf: { token: string; header: string } | null = null

function clearSession() {
  session.user = null
  csrf = null
}

async function errorMessage(response: Response, fallback: string) {
  try { return (await response.json()).message || fallback } catch { return fallback }
}

export async function csrfRefresh() {
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) throw new Error(await errorMessage(response, 'No se pudo iniciar una sesión segura'))
  csrf = await response.json()
}

export async function api<T = any>(path: string, method = 'GET', body?: unknown): Promise<T> {
  if (!csrf && method !== 'GET') await csrfRefresh()
  const headers: Record<string, string> = {}
  if (method !== 'GET' && csrf) headers[csrf.header] = csrf.token
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch('/api' + path, {
    method, headers, credentials: 'same-origin', cache: 'no-store',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    if (response.status === 401) {
      clearSession()
      if (path !== '/auth/me') location.hash = '/login'
    }
    throw new Error(await errorMessage(response, 'No se pudo completar la operación'))
  }
  const text = await response.text()
  return text ? JSON.parse(text) : undefined as T
}

export async function uploadCustomerPhoto<T = any>(id:number,file:File):Promise<T>{
  if(!csrf) await csrfRefresh()
  const form=new FormData();form.append('file',file)
  const response=await fetch(`/api/customers/${id}/photo`,{method:'POST',credentials:'same-origin',headers:{[csrf!.header]:csrf!.token},body:form})
  if(!response.ok) throw new Error(await errorMessage(response,'No se pudo guardar la fotografía'))
  return await response.json()
}

export async function login(email: string, password: string) {
  clearSession()
  await csrfRefresh()
  const token = csrf!
  const response = await fetch('/api/auth/login', {
    method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.header]: token.token },
    body: new URLSearchParams({ email: email.trim(), password }),
  })
  if (!response.ok) throw new Error(await errorMessage(response, 'No se pudo iniciar sesión'))
  // Spring rotates both the session id and CSRF token on successful authentication.
  await csrfRefresh()
  session.user = await api('/auth/me')
}

export async function logout() {
  try {
    await csrfRefresh()
    await api('/auth/logout', 'POST')
    clearSession()
    location.hash = '/login'
  } catch (error) {
    notify(error instanceof Error ? error.message : 'No se pudo cerrar la sesión', 'error')
  }
}

export const money=(v:unknown)=>new Intl.NumberFormat('es-MX',{style:'currency',currency:'MXN'}).format(Number(v||0))
export const date=(v:unknown)=>v?new Date(String(v).replace(' ','T')+(String(v).includes('Z')||String(v).includes('+')?'':'Z')).toLocaleDateString('es-MX',{day:'numeric',month:'short',year:'numeric'}):'—'
export const statuses:Record<string,string>={RECEIVED:'Recibido',DIAGNOSIS:'En diagnóstico',AWAITING_APPROVAL:'Por autorizar',IN_PROGRESS:'En reparación',READY:'Listo para entrega',DELIVERED:'Entregado',CANCELLED:'Cancelado',PENDING:'Pendiente',DONE:'Terminado'}
export const roles:Record<string,string>={ADMIN:'Administración',RECEPTIONIST:'Recepción',MECHANIC:'Mecánico',CLIENT:'Cliente'}
export const alert=reactive({message:'',type:'success'})
let timer:ReturnType<typeof setTimeout>
export function notify(message:string,type='success'){alert.message=message;alert.type=type;clearTimeout(timer);timer=setTimeout(()=>alert.message='',6000)}

export async function uploadProfilePhoto(file:File):Promise<Row>{
  if(!csrf) await csrfRefresh()
  const form=new FormData();form.append('file',file)
  const response=await fetch('/api/account/photo',{method:'POST',credentials:'same-origin',headers:{[csrf!.header]:csrf!.token},body:form})
  if(!response.ok){
    if(response.status===401){clearSession();location.hash='/login'}
    throw new Error(await errorMessage(response,'No se pudo guardar la fotografía'))
  }
  return await response.json()
}
