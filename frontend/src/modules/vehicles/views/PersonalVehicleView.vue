<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {session} from '../../../api'
import Modal from '../../../components/Modal.vue'
import VehicleForm from '../components/VehicleForm.vue'
import {vehicleFacade} from '../facade/vehicleFacade'
import {displayText} from '../../shared/presentation'
const rows=ref<Record<string,any>[]>([]),workshops=ref<{id:number;name:string}[]>([]),workshop=ref<number|''>(''),show=ref(false),busy=ref(false),error=ref('')
async function load(){try{rows.value=await vehicleFacade.personal();if(session.user?.role==='CLIENT'){workshops.value=await vehicleFacade.personalWorkshops();workshop.value=workshops.value[0]?.id??''}}catch(e){error.value=(e as Error).message}}
async function saved(){show.value=false;await load()};onMounted(load)
</script>
<template><section class="page"><div class="page-heading"><h1>{{session.user?.role==='CLIENT'?'Mis vehículos':'Vehículos asignados'}}</h1><button v-if="session.user?.role==='CLIENT'" class="btn primary" :disabled="!workshop" @click="show=true">Registrar mi vehículo</button></div><p v-if="error" class="error" role="alert">{{error}}</p><p v-if="session.user?.role==='CLIENT'&&!workshops.length" class="field-hint">Recepción debe asociar tu ficha a un taller para registrar vehículos.</p><section class="panel table-wrap"><table><thead><tr><th>Vehículo</th><th>Placa</th><th>VIN</th><th>Estatus</th></tr></thead><tbody><tr v-for="v in rows" :key="v.id"><td>{{displayText(v.make)}} {{displayText(v.model)}}</td><td>{{v.license_plate}}</td><td>{{v.vin}}</td><td>{{v.status_code}}</td></tr></tbody></table></section><Modal v-if="show" title="Registrar mi vehículo" :busy="busy" embedded-form @close="show=false"><label>Taller<select v-model="workshop" :disabled="busy"><option v-for="w in workshops" :key="w.id" :value="w.id">{{displayText(w.name)}}</option></select></label><VehicleForm :key="Number(workshop)" personal :workshop-id="Number(workshop)" @saved="saved" @busy="busy=$event" @cancel="show=false"/></Modal></section></template>
