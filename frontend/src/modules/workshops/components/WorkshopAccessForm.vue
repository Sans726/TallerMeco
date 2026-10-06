<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {workshopFacade} from '../facade/workshopFacade'
import type {AccessUser} from '../types/workshop'
import {notify} from '../../../api'
const props=defineProps<{id:number}>(),users=ref<AccessUser[]>([]),assigned=ref<AccessUser[]>([]),selected=ref<number|''>(''),busy=ref(false),error=ref('')
async function load(){try{[users.value,assigned.value]=await Promise.all([workshopFacade.accessUsers(),workshopFacade.users(props.id)])}catch(e){error.value=(e as Error).message}}
async function assign(id:number,active:boolean){if(busy.value)return;busy.value=true;error.value='';try{await workshopFacade.assign(props.id,id,active);await load();selected.value='';notify(active?'Acceso a taller asignado':'Acceso a taller desactivado')}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
onMounted(load)
</script>
<template><section class="info-section"><h3>Acceso de recepción</h3><p class="field-hint">Recepción solo puede administrar clientes de los talleres asignados. Administración puede trabajar en todos los talleres.</p><ul class="association-list"><li v-for="u in assigned" :key="u.id"><span>{{u.email}} · {{u.active?'Activo':'Inactivo'}}</span><button class="text-link" :disabled="busy" @click="assign(u.id,!u.active)">{{u.active?'Desactivar':'Activar'}}</button></li></ul><form class="button-row" @submit.prevent="selected&&assign(Number(selected),true)"><label>Cuenta de recepción<select v-model="selected" required :disabled="busy"><option value="" disabled>Selecciona una cuenta</option><option v-for="u in users" :key="u.id" :value="u.id">{{u.name||u.email}}</option></select></label><button class="btn secondary" :disabled="busy||!selected">Asignar acceso</button></form><p v-if="!users.length" class="field-hint">Crea una cuenta de recepción desde Equipo.</p><p v-if="error" class="error" role="alert">{{error}}</p></section></template>
