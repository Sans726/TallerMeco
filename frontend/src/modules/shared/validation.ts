export type InputKind='name'|'business'|'address'|'postal'|'phone'|'email'|'curp'|'rfc'|'date'|'text'
export type FieldErrors=Record<string,string>
export const limits:Record<InputKind,number>={name:160,business:160,address:180,postal:5,phone:24,email:254,curp:18,rfc:13,date:10,text:160}
export const canonical=(raw:string)=>raw.normalize('NFC').trim().replace(/ +/g,' ').toLowerCase().replace(/’/g,"'")
export function acceptsInput(kind:InputKind,value:string,max=limits[kind]):boolean {
 if(value.length>max||/[\u0000-\u001f\u007f]/.test(value))return false
 const patterns:Record<InputKind,RegExp>={name:/^[\p{L}\p{M} '\u2019-]*$/u,business:/^[\p{L}\p{M}0-9 .,&'\u2019()/\-]*$/u,address:/^[\p{L}\p{M}0-9 .,#º°'\u2019/&()\-]*$/u,postal:/^[0-9]*$/,phone:/^(?:\+?[0-9 ()-]*)$/,email:/^[^\s]*$/,curp:/^[a-zA-Z0-9]*$/,rfc:/^[a-zA-ZñÑ&0-9]*$/,date:/^[0-9-]*$/,text:/^[^\u0000-\u001f\u007f]*$/}
 return patterns[kind].test(value)
}
export function validDate(value:string):boolean {
 if(!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value))return false
 const [y,m,d]=value.split('-').map(Number),date=new Date(Date.UTC(y!,m!-1,d))
 return date.getUTCFullYear()===y&&date.getUTCMonth()+1===m&&date.getUTCDate()===d
}
/** Birth dates are displayed explicitly as day/month/year, independent of browser locale. */
export function birthDateToDisplay(value:string):string {
 const match=/^([0-9]{4})-([0-9]{2})-([0-9]{2})$/.exec(value)
 return match?`${match[3]}/${match[2]}/${match[1]}`:value
}
export function birthDateFromDisplay(value:string):string {
 const match=/^([0-9]{2})\/([0-9]{2})\/([0-9]{4})$/.exec(value)
 return match?`${match[3]}-${match[2]}-${match[1]}`:value
}
export function acceptsBirthDateInput(value:string):boolean {
 return value.length<=10&&(/^[0-9]{0,2}(?:\/[0-9]{0,2}(?:\/[0-9]{0,4})?)?$/.test(value)||validDate(value))
}
export const today=()=>{const n=new Date();return `${n.getFullYear()}-${String(n.getMonth()+1).padStart(2,'0')}-${String(n.getDate()).padStart(2,'0')}`}
function birthCutoff():string {
 const n=new Date(),year=n.getFullYear()-130,month=n.getMonth()+1,day=Math.min(n.getDate(),new Date(year,month,0).getDate())
 return `${year}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}`
}
export function age(value:string):number|null {
 if(!validDate(value)||value>today())return null
 const [y,m,d]=value.split('-').map(Number),n=new Date()
 return n.getFullYear()-y!-(n.getMonth()+1<m!||(n.getMonth()+1===m&&n.getDate()<d!)?1:0)
}
function encodedDate(value:string,birth?:string,century?:number):boolean {
 const y=Number(value.slice(0,2)),m=value.slice(2,4),d=value.slice(4,6)
 let year=century===undefined?2000+y:century+y
 if(century===undefined&&year>new Date().getFullYear())year-=100
 const date=`${year}-${m}-${d}`
 return validDate(date)&&date<=today()&&(century===undefined||!birth||birth===date)&&(!birth||`${birth.slice(2,4)}${birth.slice(5,7)}${birth.slice(8,10)}`===value)
}
export function validate(kind:InputKind,raw:string,required=false,max=limits[kind],birth?:string):string {
 if(raw.length>max||/[\u0000-\u001f\u007f]/.test(raw))return `Máximo ${max} caracteres; no se permiten caracteres de control`
 if(raw&&!raw.trim()&&['curp','rfc','postal'].includes(kind))return 'No se permiten espacios'
 if(!raw||!raw.trim())return required?'Este campo es obligatorio':''
 const value=canonical(raw)
 switch(kind){
  case 'postal':return /^[0-9]{5}$/.test(raw)?'':'El código postal debe contener exactamente 5 dígitos, sin espacios ni signos'
  case 'name':return /^[\p{L}\p{M}]+(?:[ '\u2019-][\p{L}\p{M}]+)*$/u.test(value)?'':'Usa letras, acentos, espacios, apóstrofes o guiones entre palabras'
  case 'phone':return /^(?:\+52[ -]?)?(?:[0-9]{10}|[0-9]{2}[ -][0-9]{4}[ -][0-9]{4}|\([0-9]{2}\)[ ]?[0-9]{4}[ -][0-9]{4})$/.test(raw.trim())?'':'Usa 10 dígitos mexicanos, por ejemplo 5512345678 o +52 55 1234 5678'
  case 'email':return /^[a-z0-9!#$%&'*+/=?^_`{|}~.-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/.test(value)&&value.indexOf('@')<=64&&!value.startsWith('.')&&!value.includes('..')&&!value.includes('.@')?'':'Email inválido; no se permiten espacios internos'
  case 'curp': {
   if(raw!==raw.trim()||!/^[a-z][aeioux][a-z]{2}[0-9]{6}[hm](?:as|bc|bs|cc|cl|cm|cs|ch|df|dg|gt|gr|hg|jc|mc|mn|ms|nt|nl|oc|pl|qt|qr|sp|sl|sr|tc|ts|tl|vz|yn|zs|ne)[b-df-hj-np-tv-z]{3}[a-z0-9][0-9]$/.test(value))return 'CURP inválida: verifica sus 18 caracteres y estructura mexicana'
   const century=/[a-z]/.test(value[16]!)?2000:1900
   if(!encodedDate(value.slice(4,10),undefined,century))return 'La fecha incluida en la CURP es inválida o futura'
   return encodedDate(value.slice(4,10),birth,century)?'':'La fecha de la CURP no coincide con el nacimiento; revisa día, mes y año'
  }
  case 'rfc':return raw===raw.trim()&&/^[a-zñ&]{3,4}[0-9]{6}[a-z0-9]{3}$/.test(value)&&encodedDate(value.slice(-9,-3),birth)?'':'RFC inválido: usa 12 o 13 caracteres y una fecha válida'
  case 'date':return validDate(raw)&&raw<=today()&&raw>=birthCutoff()?'':'Usa una fecha de nacimiento válida, no futura y de hasta 130 años'
  case 'business':return /^[\p{L}\p{M}0-9 .,&'\u2019()/\-]+$/u.test(value)&&value.length>=2&&/[\p{L}0-9]/u.test(value)?'':'Nombre o razón social inválidos'
  case 'address':return /^[\p{L}\p{M}0-9 .,#º°'\u2019/&()\-]+$/u.test(value)&&/[\p{L}0-9]/u.test(value)?'':'La dirección contiene caracteres inválidos'
  default:return ''
 }
}
export const addressRules={street:['address',180],neighborhood:['address',120],municipality:['name',120],state:['name',120],postalCode:['postal',5]} as const
export function validateAddress(form:Record<string,unknown>):FieldErrors {
 const errors:FieldErrors={};for(const [key,[kind,max]] of Object.entries(addressRules)){const message=validate(kind,String(form[key]??''),true,max);if(message)errors[key]=message}return errors
}
export function validateCustomer(form:Record<string,unknown>):FieldErrors {
 const errors=validateAddress(form)
 const rules:Record<string,[InputKind,number,boolean]>={givenName:['name',50,true],paternalSurname:['name',50,true],maternalSurname:['name',50,false],alias:['name',120,false],alternativeContactName:['name',160,false],birthDate:['date',10,false],curp:['curp',18,false],rfc:['rfc',13,false],personalEmail:['email',254,false],workEmail:['email',254,false],personalPhone:['phone',24,false],cellPhone:['phone',24,false],workPhone:['phone',24,false]}
 for(const [key,[kind,max,required]] of Object.entries(rules)){const message=validate(kind,String(form[key]??''),required,max,String(form.birthDate||''));if(message)errors[key]=message}
 if(!['personalEmail','workEmail','personalPhone','cellPhone','workPhone'].some(k=>String(form[k]??'').trim()))errors.personalEmail='Registra al menos un email o teléfono válido'
 return errors
}
export function validateWorkshop(form:Record<string,unknown>):FieldErrors {
 const errors=validateAddress(form)
 const rules:Record<string,InputKind>={name:'business',legalName:'business',rfc:'rfc',phone:'phone',email:'email'}
 for(const [key,kind] of Object.entries(rules)){const message=validate(kind,String(form[key]??''),true);if(message)errors[key]=message}return errors
}
export function focusInvalid(form:HTMLElement|null,errors:FieldErrors){const fields=form?.querySelectorAll<HTMLElement>('[name]');if(fields)Array.from(fields).find(field=>errors[field.getAttribute('name')??''])?.focus()}
