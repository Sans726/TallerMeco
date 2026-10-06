<script setup lang="ts">
import {ref,watch,nextTick,onUnmounted} from 'vue'
import type {Workshop,WorkshopData,Company} from '../types/workshop'
import {workshopFacade} from '../facade/workshopFacade'
import ValidatedInput from '../../shared/ValidatedInput.vue'
import AddressFields from '../../../components/address/AddressFields.vue'
import {validateWorkshop,focusInvalid,type FieldErrors} from '../../shared/validation'
import {ApiError,notify} from '../../../api'
const props=defineProps<{initial?:Workshop;companies:Company[]}>(),emit=defineEmits<{saved:[Workshop];cancel:[];busy:[boolean]}>()
const empty:WorkshopData={name:'',legalName:'',rfc:'',phone:'',email:'',street:'',neighborhood:'',municipality:'',state:'',postalCode:''}
const form=ref<WorkshopData>(Object.fromEntries(Object.entries(empty).map(([key,value])=>[key,props.initial?.[key as keyof Workshop]??value])) as WorkshopData)
const companyId=ref<number|''>(props.initial?.companyId??''),active=ref(props.initial?.active??true),persistedId=ref(props.initial?.id),version=ref(props.initial?.version??0)
const element=ref<HTMLFormElement|null>(null),errors=ref<FieldErrors>({}),busy=ref(false),error=ref(''),file=ref<File|null>(null),preview=ref(''),imageError=ref('')
watch(()=>({...form.value}),(next,prev)=>{for(const key of Object.keys(next) as (keyof WorkshopData)[])if(next[key]!==prev[key])delete errors.value[key]})
let reader:FileReader|null=null
watch(companyId,()=>{delete errors.value.companyId})
function choose(e:Event){const input=e.target as HTMLInputElement,f=input.files?.[0];if(!f)return;imageError.value='';if(f.size===0||f.size>15*1024*1024||!['image/jpeg','image/png'].includes(f.type)){imageError.value='Usa una imagen JPG o PNG no vacía de hasta 15 MB';input.value='';return}reader?.abort();reader=new FileReader();reader.onload=()=>{file.value=f;preview.value=String(reader?.result||'')};reader.onerror=()=>{imageError.value='No se pudo leer la imagen'};reader.readAsDataURL(f)}
onUnmounted(()=>reader?.abort())
function clearImage(){reader?.abort();file.value=null;preview.value='';imageError.value='';const input=element.value?.querySelector<HTMLInputElement>('input[type="file"]');if(input)input.value=''}
async function submit(){if(busy.value)return;error.value='';errors.value=validateWorkshop(form.value);if(!persistedId.value&&props.companies.length&&!companyId.value)errors.value.companyId='Selecciona una empresa';if(imageError.value)errors.value.image=imageError.value;if(Object.keys(errors.value).length){await nextTick();focusInvalid(element.value,errors.value);return}busy.value=true;emit('busy',true);try{const saved=persistedId.value?await workshopFacade.update(persistedId.value,form.value,version.value,active.value):await workshopFacade.create(form.value,companyId.value?Number(companyId.value):null);persistedId.value=saved.id;version.value=saved.version;if(file.value){try{await workshopFacade.upload(saved.id,file.value);file.value=null}catch(e){error.value='El taller se guardó, pero el banner no: '+(e as Error).message+'. Vuelve a guardar para reintentarlo.';return}}notify(props.initial?'Taller actualizado':'Taller registrado');emit('saved',saved)}catch(e){error.value=(e as Error).message;if(e instanceof ApiError){errors.value=e.fields;await nextTick();focusInvalid(element.value,errors.value)}}finally{busy.value=false;emit('busy',false)}}
</script>
<template>
 <form ref="element" novalidate class="customer-form" :aria-busy="busy" @submit.prevent="submit">
  <fieldset class="customer-form-layout" :disabled="busy">
   <div class="customer-form-fields">
    <section class="form-section"><header class="form-section-heading"><div><h2>Identidad del taller</h2><p>Datos de operación y facturación.</p></div></header><div class="form-grid">
     <ValidatedInput v-model="form.name" name="name" label="Nombre del taller" kind="business" required placeholder="Ej. Taller del Centro" :error="errors.name" />
     <ValidatedInput v-model="form.legalName" name="legalName" label="Razón social" kind="business" required placeholder="Ej. Servicios del Centro S.A. de C.V." :error="errors.legalName" />
     <ValidatedInput v-model="form.rfc" name="rfc" label="RFC" kind="rfc" required :error="errors.rfc" />
     <label v-if="companies.length">Empresa <span class="required-mark">*</span><select name="companyId" v-model="companyId" :disabled="!!persistedId" required><option value="" disabled>Selecciona una empresa</option><option v-for="c in companies" :key="c.id" :value="c.id">{{c.name}}</option></select><small v-if="errors.companyId" class="field-error">{{errors.companyId}}</small></label>
     <p v-else class="field-hint">La primera empresa se registrará con esta razón social.</p>
     <ValidatedInput v-model="form.phone" name="phone" label="Teléfono" kind="phone" required placeholder="55 1234 5678" :error="errors.phone" />
     <ValidatedInput v-model="form.email" name="email" label="Email" kind="email" required :error="errors.email" />
    </div></section>
    <section class="form-section"><header class="form-section-heading"><div><h2>Dirección</h2><p>Completa la ubicación con el catálogo SEPOMEX.</p></div></header><AddressFields v-model="form" :errors="errors" /></section>
   </div>
   <aside class="customer-form-aside"><section class="photo-card"><h2>Banner del taller</h2><p class="field-hint">JPG o PNG reales · Hasta 15 MB</p><div class="workshop-banner"><img v-if="preview||initial?.bannerReference" :src="preview||(initial?workshopFacade.bannerUrl(initial):'')" alt="Banner del taller"><span v-else>Tu taller</span></div><label class="upload-control">Seleccionar banner<input name="image" type="file" accept="image/jpeg,image/png" @change="choose"></label><button v-if="file||imageError" type="button" class="text-link" @click="clearImage">Quitar archivo seleccionado</button><p v-if="imageError" class="field-error" role="alert">{{imageError}}</p><small>{{file?.name}}</small></section><label v-if="initial" class="workshop-card checkbox-label"><input type="checkbox" v-model="active"> Taller activo</label></aside>
  </fieldset>
  <p v-if="error" class="error" role="alert">{{error}}</p>
  <footer class="form-actions"><span class="field-hint">* Campos obligatorios</span><div class="button-row"><button class="btn secondary" type="button" :disabled="busy" @click="emit('cancel')">Cancelar</button><button class="btn primary" :disabled="busy">{{busy?'Guardando…':persistedId?'Guardar cambios':'Registrar taller'}}</button></div></footer>
 </form>
</template>
