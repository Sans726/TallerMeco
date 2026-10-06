<script setup lang="ts">
import {ref} from 'vue'
import {displayText} from '../../shared/presentation'
import type {CustomerWorkshop,WorkshopOption} from '../types/customer'
import {customerFacade} from '../facade/customerFacade'
const props=defineProps<{id:number;workshopId:number;associated:CustomerWorkshop[];workshops:WorkshopOption[]}>()
const emit=defineEmits<{changed:[];busy:[boolean]}>()
const target=ref<number|''>(''),mode=ref<'ASSOCIATE'|'REASSIGN'>('ASSOCIATE'),busy=ref(false),error=ref('')
async function operate(action:()=>Promise<void>){if(busy.value)return;busy.value=true;emit('busy',true);error.value='';try{await action();target.value='';emit('changed')}catch(e){error.value=(e as Error).message}finally{busy.value=false;emit('busy',false)}}
function save(){if(!target.value){error.value='Selecciona otro taller';return}void operate(()=>customerFacade.associate(props.id,props.workshopId,Number(target.value),mode.value))}
</script>
<template>
 <section class="info-section"><h3>Talleres asociados</h3>
  <ul class="association-list"><li v-for="w in associated" :key="w.id"><span>{{displayText(w.name)}} · {{w.associationActive?'Asociación activa':'Asociación inactiva'}}{{!w.active?' · Taller inactivo':''}}</span><button v-if="w.active" class="text-link" :disabled="busy" @click="operate(()=>customerFacade.associationStatus(id,workshopId,w.id,!w.associationActive))">{{w.associationActive?'Desactivar vínculo':'Activar vínculo'}}</button></li></ul>
  <form @submit.prevent="save"><fieldset :disabled="busy" class="form-grid"><label>Otro taller<select v-model="target" required><option value="" disabled>Selecciona otro taller</option><option v-for="w in workshops.filter(w=>w.id!==workshopId)" :key="w.id" :value="w.id">{{displayText(w.name)}}</option></select></label><label>Operación<select v-model="mode"><option value="ASSOCIATE">Asociar además del actual</option><option value="REASSIGN">Reasignar desde el actual</option></select></label><p class="field-hint span-two">Asociar conserva el vínculo actual. Reasignar activa el destino y desactiva el vínculo con este taller; mantiene el historial y otros vínculos.</p><button class="btn secondary" :disabled="busy||!target">{{busy?'Guardando…':'Guardar asociación'}}</button></fieldset></form>
  <p v-if="error" class="error" role="alert">{{error}}</p>
 </section>
</template>
