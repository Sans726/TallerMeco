<script setup lang="ts">
import {ref,computed,onUnmounted} from 'vue'
import {customerFacade} from '../facade/customerFacade'
import {notify} from '../../../api'
import Icon from '../../../components/Icon.vue'

const props=defineProps<{initial?:any;workshops?:any[];customerId?:number}>()
const emit=defineEmits<{created:[any];cancel:[]}>()
const busy=ref(false), error=ref(''), photoError=ref(''), file=ref<File|null>(null), preview=ref('')
const persistedId=ref(props.customerId)
const workshopId=ref(props.initial?.workshops?.[0]?.id||'')
const form=ref<any>({...{fullName:'',alias:'',alternativeContactName:'',birthDate:'',personalPhone:'',workPhone:'',personalEmail:'',workEmail:'',street:'',neighborhood:'',municipality:'',state:'',postalCode:''},...(props.initial||{})})
const today=new Date().toLocaleDateString('en-CA')
const age=computed(()=>{
  if(!form.value.birthDate)return '—'
  const [y,m,d]=form.value.birthDate.split('-').map(Number),n=new Date()
  const years=n.getFullYear()-y-(n.getMonth()+1<m||(n.getMonth()+1===m&&n.getDate()<d)?1:0)
  return years>=0?`${years} años`:'—'
})
const photo=computed(()=>preview.value||(form.value.photoReference?'/api/customers/photos/'+form.value.photoReference:''))
const initials=computed(()=>String(form.value.fullName||'').trim().split(/\s+/).map((s:string)=>s[0]).slice(0,2).join('').toUpperCase())
let reader:FileReader|null=null
onUnmounted(()=>reader?.abort())
function choose(e:Event){
  const input=e.target as HTMLInputElement,f=input.files?.[0];if(!f)return
  photoError.value=''
  if(f.size===0||f.size>15*1024*1024||!['image/jpeg','image/png'].includes(f.type)){
    photoError.value='Selecciona una imagen JPG o PNG no vacía de hasta 15 MB.';input.value='';return
  }
  reader?.abort();reader=new FileReader()
  reader.onload=()=>{file.value=f;preview.value=String(reader?.result||'')}
  reader.onerror=()=>{photoError.value='No se pudo leer la fotografía. Inténtalo con otro archivo.'}
  reader.readAsDataURL(f)
}
async function submit(){
  error.value='';busy.value=true
  try{
    const result=persistedId.value?await customerFacade.updateCustomer(persistedId.value,form.value,Number(workshopId.value)):await customerFacade.createCustomer(form.value,Number(workshopId.value))
    persistedId.value=result.id
    if(file.value){
      try{await customerFacade.uploadPhoto(result.id,file.value)}
      catch(e:any){error.value='Los datos se guardaron, pero la fotografía no: '+e.message+'. Puedes volver a guardar para reintentarlo.';return}
    }
    notify(props.customerId?'Cliente actualizado':'Cliente registrado');emit('created',result)
  }catch(e:any){error.value=e.message}finally{busy.value=false}
}
</script>
<template>
  <form class="customer-form" @submit.prevent="submit" :aria-busy="busy">
    <fieldset :disabled="busy" class="customer-form-layout">
      <div class="customer-form-fields">
        <section class="form-section" aria-labelledby="personal-heading">
          <header class="form-section-heading"><span class="section-icon"><Icon name="users"/></span><div><h2 id="personal-heading">Información personal</h2><p>Los datos que identifican a tu cliente.</p></div><span class="step-number">01</span></header>
          <div class="form-grid">
            <label class="span-two">Nombre completo <span class="required-mark">*</span><input v-model="form.fullName" required minlength="2" maxlength="160" autocomplete="name" placeholder="Nombre y apellidos"></label>
            <label>Alias<input v-model="form.alias" maxlength="120" placeholder="Cómo prefiere que le llamen"></label>
            <label>Contacto alternativo<input v-model="form.alternativeContactName" maxlength="160" placeholder="Nombre de una persona de contacto"></label>
            <label>Fecha de nacimiento<input v-model="form.birthDate" type="date" :max="today" autocomplete="bday"></label>
            <label>Edad<input :value="age" readonly aria-describedby="age-hint"><small id="age-hint" class="field-hint">Calculada a partir de la fecha de nacimiento.</small></label>
          </div>
        </section>
        <section class="form-section" aria-labelledby="contact-heading">
          <header class="form-section-heading"><span class="section-icon"><Icon name="mail"/></span><div><h2 id="contact-heading">Contacto</h2><p>Para mantener al cliente al tanto de sus servicios.</p></div><span class="step-number">02</span></header>
          <div class="form-grid">
            <label>Teléfono personal<input v-model="form.personalPhone" type="tel" autocomplete="tel" pattern="[+()\- 0-9]{7,30}" maxlength="30" placeholder="55 1234 5678"></label>
            <label>Teléfono de trabajo<input v-model="form.workPhone" type="tel" pattern="[+()\- 0-9]{7,30}" maxlength="30" placeholder="Opcional"></label>
            <label>Email personal <span class="required-mark">*</span><input v-model="form.personalEmail" type="email" required maxlength="254" autocomplete="email" placeholder="nombre@correo.com"></label>
            <label>Email de trabajo<input v-model="form.workEmail" type="email" maxlength="254" placeholder="Opcional"></label>
          </div>
        </section>
        <section class="form-section" aria-labelledby="address-heading">
          <header class="form-section-heading"><span class="section-icon"><Icon name="pin"/></span><div><h2 id="address-heading">Dirección</h2><p>Ubicación y datos de correspondencia.</p></div><span class="step-number">03</span></header>
          <div class="form-grid">
            <label class="span-two">Calle<input v-model="form.street" maxlength="180" autocomplete="street-address" placeholder="Calle y número"></label>
            <label>Colonia<input v-model="form.neighborhood" maxlength="120" placeholder="Colonia o barrio"></label>
            <label>Municipio<input v-model="form.municipality" maxlength="120" autocomplete="address-level2" placeholder="Municipio o alcaldía"></label>
            <label>Estado<input v-model="form.state" maxlength="120" autocomplete="address-level1" placeholder="Estado"></label>
            <label>Código postal<input v-model="form.postalCode" maxlength="12" pattern="[A-Za-z0-9 -]{3,12}" autocomplete="postal-code" placeholder="Código postal"></label>
          </div>
        </section>
      </div>
      <aside class="customer-form-aside">
        <section class="photo-card">
          <h2>Fotografía del cliente</h2><p class="field-hint">Una forma sencilla de reconocerle.</p>
          <div class="photo-frame"><img v-if="photo" :src="photo" alt="Fotografía del cliente"><span v-else class="photo-placeholder"><span v-if="initials">{{initials}}</span><Icon v-else name="users" :size="40"/></span></div>
          <label class="upload-control"><Icon name="upload" :size="17"/>{{photo?'Cambiar fotografía':'Seleccionar fotografía'}}<input type="file" accept="image/jpeg,image/png" aria-label="Seleccionar fotografía del cliente" @change="choose"></label>
          <p class="field-hint photo-file-name">{{file?.name||'JPG o PNG · Hasta 15 MB'}}</p>
          <p v-if="photoError" class="error" role="alert">{{photoError}}</p>
        </section>
        <section class="workshop-card"><span class="section-icon"><Icon name="wrench"/></span><h2>Taller asociado</h2><p class="field-hint">Selecciona el taller que atiende al cliente.</p><label class="sr-only" for="customer-workshop">Taller asociado</label><select id="customer-workshop" v-model="workshopId" required><option disabled value="">Selecciona un taller</option><option v-for="w in workshops" :key="w.id" :value="w.id">{{w.name}}</option></select><p v-if="!workshops?.length" class="inline-notice"><Icon name="shield" :size="16"/>Falta configurar un taller antes de registrar clientes.</p></section>
      </aside>
    </fieldset>
    <p v-if="error" class="error" role="alert">{{error}}</p>
    <footer class="form-actions"><span class="field-hint"><span class="required-mark">*</span> Campos obligatorios</span><div class="button-row"><button type="button" class="btn secondary" :disabled="busy" @click="emit('cancel')">Cancelar</button><button class="btn primary" :disabled="busy||!workshops?.length"><span v-if="busy" class="loader small-loader" aria-hidden="true"></span><Icon v-else name="check" :size="18"/>{{busy?'Guardando…':customerId?'Guardar cambios':'Registrar cliente'}}</button></div></footer>
  </form>
</template>
