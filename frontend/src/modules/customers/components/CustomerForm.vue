<script setup lang="ts">
import {ref,computed,onUnmounted,watch,nextTick} from 'vue'
import {customerFacade} from '../facade/customerFacade'
import type {Customer,CustomerData,WorkshopOption} from '../types/customer'
import {notify,ApiError} from '../../../api'
import Icon from '../../../components/Icon.vue'
import AddressFields from '../../../components/address/AddressFields.vue'
import ValidatedInput from '../../shared/ValidatedInput.vue'
import {validateCustomer,focusInvalid,age as calculateAge,type FieldErrors} from '../../shared/validation'
const props=defineProps<{initial?:Customer;workshops?:WorkshopOption[];customerId?:number;workshopId?:number}>()
const emit=defineEmits<{created:[Customer,number];cancel:[];busy:[boolean]}>()
const busy=ref(false),error=ref(''),photoError=ref(''),file=ref<File|null>(null),preview=ref(''),element=ref<HTMLFormElement|null>(null),errors=ref<FieldErrors>({})
const persistedId=ref(props.customerId),version=ref(props.initial?.version??0),workshopId=ref<number|''>(props.workshopId??'')
const empty:CustomerData={givenName:'',paternalSurname:'',maternalSurname:'',curp:'',rfc:'',alias:'',alternativeContactName:'',birthDate:'',personalPhone:'',cellPhone:'',workPhone:'',personalEmail:'',workEmail:'',street:'',neighborhood:'',municipality:'',state:'',postalCode:''}
const form=ref<CustomerData>(Object.fromEntries(Object.entries(empty).map(([key,value])=>[key,props.initial?.[key as keyof Customer]??value])) as CustomerData)
watch(()=>({...form.value}),(next,previous)=>{for(const key of Object.keys(next) as (keyof CustomerData)[])if(next[key]!==previous[key])delete errors.value[key];if(next.birthDate!==previous.birthDate){delete errors.value.curp;delete errors.value.rfc}if(errors.value.personalEmail==='Registra al menos un email o teléfono válido'&&!validateCustomer(next).personalEmail)delete errors.value.personalEmail})
watch(workshopId,()=>{delete errors.value.workshopId})
const age=computed(()=>{const years=calculateAge(form.value.birthDate);return years===null?'—':`${years} años`})
const photo=computed(()=>preview.value||(props.initial?.photoReference&&workshopId.value?customerFacade.photoUrl(props.initial.photoReference,Number(workshopId.value)):''))
const initials=computed(()=>[form.value.givenName,form.value.paternalSurname].map(s=>s[0]||'').join('').toUpperCase())
let reader:FileReader|null=null
onUnmounted(()=>reader?.abort())
function choose(e:Event){const input=e.target as HTMLInputElement,f=input.files?.[0];if(!f)return;photoError.value='';if(f.size===0||f.size>15*1024*1024||!['image/jpeg','image/png'].includes(f.type)){photoError.value='Selecciona una imagen JPG o PNG no vacía de hasta 15 MB.';input.value='';return}reader?.abort();reader=new FileReader();reader.onload=()=>{file.value=f;preview.value=String(reader?.result||'')};reader.onerror=()=>{photoError.value='No se pudo leer la fotografía.'};reader.readAsDataURL(f)}
function clearImage(){reader?.abort();file.value=null;preview.value='';photoError.value='';const input=element.value?.querySelector<HTMLInputElement>('input[type="file"]');if(input)input.value=''}
async function submit(){
 if(busy.value)return;error.value='';errors.value=validateCustomer(form.value)
 if(!workshopId.value)errors.value.workshopId='Selecciona un taller activo'
 if(photoError.value)errors.value.image=photoError.value
 if(Object.keys(errors.value).length){await nextTick();focusInvalid(element.value,errors.value);return}
 busy.value=true;emit('busy',true)
 try{
  const result=persistedId.value?await customerFacade.updateCustomer(persistedId.value,form.value,Number(workshopId.value),version.value):await customerFacade.createCustomer(form.value,Number(workshopId.value))
  persistedId.value=result.id;version.value=result.version
  if(file.value){try{await customerFacade.uploadPhoto(result.id,Number(workshopId.value),file.value);file.value=null}catch(e){error.value='Los datos se guardaron, pero la fotografía no: '+(e as Error).message+'. Puedes volver a guardar para reintentarla.';return}}
  notify(props.customerId?'Cliente actualizado':'Cliente registrado');emit('created',result,Number(workshopId.value))
 }catch(e){error.value=(e as Error).message;if(e instanceof ApiError){errors.value=e.fields;await nextTick();focusInvalid(element.value,errors.value)}}finally{busy.value=false;emit('busy',false)}
}
</script>
<template>
  <form ref="element" novalidate class="customer-form" @submit.prevent="submit" :aria-busy="busy">
    <fieldset :disabled="busy" class="customer-form-layout">
      <div class="customer-form-fields">
        <section class="form-section" aria-labelledby="personal-heading">
          <header class="form-section-heading"><span class="section-icon"><Icon name="users"/></span><div><h2 id="personal-heading">Información personal</h2><p>Los datos que identifican a tu cliente.</p></div><span class="step-number">01</span></header>
          <div class="form-grid">
            <p v-if="initial && !initial.givenName" class="inline-notice span-two">Ficha anterior: {{initial.fullName}}. Completa nombre y apellidos por separado para guardar.</p>
            <ValidatedInput v-model="form.givenName" name="givenName" label="Nombre" kind="name" required :max="50" autocomplete="given-name" placeholder="Ej. María José" :error="errors.givenName" />
            <ValidatedInput v-model="form.paternalSurname" name="paternalSurname" label="Apellido paterno" kind="name" required :max="50" autocomplete="family-name" :error="errors.paternalSurname" />
            <ValidatedInput v-model="form.maternalSurname" name="maternalSurname" label="Apellido materno" kind="name" :max="50" :error="errors.maternalSurname" />
            <ValidatedInput v-model="form.birthDate" name="birthDate" label="Fecha de nacimiento" kind="date" autocomplete="bday" :error="errors.birthDate" />
            <ValidatedInput v-model="form.curp" name="curp" label="CURP" kind="curp" :birth="form.birthDate" placeholder="18 caracteres" :error="errors.curp" />
            <ValidatedInput v-model="form.rfc" name="rfc" label="RFC" kind="rfc" :birth="form.birthDate" placeholder="12 o 13 caracteres" :error="errors.rfc" />
            <ValidatedInput v-model="form.alias" name="alias" label="Alias" kind="name" :max="120" :error="errors.alias" />
            <ValidatedInput v-model="form.alternativeContactName" name="alternativeContactName" label="Contacto alternativo" kind="name" :max="160" :error="errors.alternativeContactName" />
            <label>Edad<input :value="age" readonly aria-describedby="age-hint"><small id="age-hint" class="field-hint">Calculada a partir de la fecha de nacimiento.</small></label>
          </div>
        </section>
        <section class="form-section" aria-labelledby="contact-heading">
          <header class="form-section-heading"><span class="section-icon"><Icon name="mail"/></span><div><h2 id="contact-heading">Contacto</h2><p>Para mantener al cliente al tanto de sus servicios.</p></div><span class="step-number">02</span></header>
          <div class="form-grid">
            <p class="field-hint span-two">Registra al menos un email o teléfono. Los teléfonos mexicanos se guardan con 10 dígitos.</p>
            <ValidatedInput v-model="form.personalPhone" name="personalPhone" label="Teléfono personal" kind="phone" autocomplete="tel" placeholder="55 1234 5678" :error="errors.personalPhone" />
            <ValidatedInput v-model="form.cellPhone" name="cellPhone" label="Teléfono celular" kind="phone" placeholder="5512345678" :error="errors.cellPhone" />
            <ValidatedInput v-model="form.workPhone" name="workPhone" label="Teléfono de trabajo" kind="phone" placeholder="Opcional" :error="errors.workPhone" />
            <ValidatedInput v-model="form.personalEmail" name="personalEmail" label="Email personal" kind="email" autocomplete="email" placeholder="nombre@correo.com" :error="errors.personalEmail" />
            <ValidatedInput v-model="form.workEmail" name="workEmail" label="Email de trabajo" kind="email" :error="errors.workEmail" />
          </div>
        </section>
        <section class="form-section" aria-labelledby="address-heading">
          <header class="form-section-heading"><span class="section-icon"><Icon name="pin"/></span><div><h2 id="address-heading">Dirección</h2><p>Ubicación y datos de correspondencia.</p></div><span class="step-number">03</span></header>
          <AddressFields v-model="form" :errors="errors" />
        </section>
      </div>
      <aside class="customer-form-aside">
        <section class="photo-card">
          <h2>Fotografía del cliente</h2><p class="field-hint">Una forma sencilla de reconocerle.</p>
          <div class="photo-frame"><img v-if="photo" :src="photo" alt="Fotografía del cliente"><span v-else class="photo-placeholder"><span v-if="initials">{{initials}}</span><Icon v-else name="users" :size="40"/></span></div>
          <label class="upload-control"><Icon name="upload" :size="17"/>{{photo?'Cambiar fotografía':'Seleccionar fotografía'}}<input name="image" type="file" accept="image/jpeg,image/png" aria-label="Seleccionar fotografía del cliente" @change="choose"></label>
          <p class="field-hint photo-file-name">{{file?.name||'JPG o PNG · Hasta 15 MB'}}</p>
          <button v-if="file||photoError" type="button" class="text-link" @click="clearImage">Quitar archivo seleccionado</button><p v-if="photoError" class="error" role="alert">{{photoError}}</p>
        </section>
        <section class="workshop-card"><span class="section-icon"><Icon name="wrench"/></span><h2>Taller asociado</h2><p class="field-hint">Selecciona el taller que atiende al cliente.</p><label class="sr-only" for="customer-workshop">Taller asociado</label><select id="customer-workshop" name="workshopId" v-model="workshopId" required :disabled="!!persistedId" :aria-invalid="!!errors.workshopId"><option disabled value="">Selecciona un taller</option><option v-for="w in workshops" :key="w.id" :value="w.id">{{w.name}}</option></select><small v-if="errors.workshopId" class="field-error" role="alert">{{errors.workshopId}}</small><p v-if="customerId" class="field-hint">Cambia las asociaciones desde la ficha del cliente.</p><p v-if="!workshops?.length" class="inline-notice"><Icon name="shield" :size="16"/>Falta configurar un taller antes de registrar clientes.</p></section>
      </aside>
    </fieldset>
    <p v-if="error" class="error" role="alert">{{error}}</p>
    <footer class="form-actions"><span class="field-hint"><span class="required-mark">*</span> Campos obligatorios</span><div class="button-row"><button type="button" class="btn secondary" :disabled="busy" @click="emit('cancel')">Cancelar</button><button class="btn primary" :disabled="busy||!workshops?.length"><span v-if="busy" class="loader small-loader" aria-hidden="true"></span><Icon v-else name="check" :size="18"/>{{busy?'Guardando…':persistedId?'Guardar cambios':'Registrar cliente'}}</button></div></footer>
  </form>
</template>
