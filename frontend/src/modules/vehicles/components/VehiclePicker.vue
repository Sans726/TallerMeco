<script setup lang="ts">
import {ref,onMounted,watch} from 'vue'
import {vehicleFacade} from '../facade/vehicleFacade'
import type {VehiclePage} from '../types/vehicle'
import {customerFacade} from '../../customers/facade/customerFacade'
import type {WorkshopOption} from '../../customers/types/customer'
import {displayText} from '../../shared/presentation'
defineProps<{disabled?:boolean}>()
const vehicle=defineModel<number|''>({default:''}),workshop=defineModel<number|''>('workshop',{default:''}),workshops=ref<WorkshopOption[]>([]),page=ref(1),query=ref(''),result=ref<VehiclePage>({items:[],page:1,pageSize:10,totalItems:0,totalPages:0}),loading=ref(false),error=ref('');let generation=0
async function load(){if(!workshop.value)return;const n=++generation;loading.value=true;error.value='';try{const data=await vehicleFacade.list(Number(workshop.value),page.value,'ASC',query.value);if(n===generation)result.value=data}catch(e){error.value=(e as Error).message}finally{if(n===generation)loading.value=false}}
watch(workshop,()=>{vehicle.value='';page.value=1;void load()});watch([page,query],()=>{vehicle.value='';void load()});watch(query,()=>page.value=1)
onMounted(async()=>{try{workshops.value=await customerFacade.workshopsList();workshop.value=workshops.value[0]?.id??''}catch(e){error.value=(e as Error).message}})
</script>
<template><fieldset :disabled="disabled"><label>Taller<select v-model="workshop" required><option value="" disabled>Selecciona un taller</option><option v-for="w in workshops" :key="w.id" :value="w.id">{{displayText(w.name)}}</option></select></label><label>Buscar vehículo<input v-model="query" maxlength="160" placeholder="VIN, placa, marca o modelo"></label><label>Vehículo<select v-model="vehicle" required :disabled="loading"><option value="" disabled>Selecciona un vehículo operativo</option><option v-for="v in result.items" :key="v.id" :value="v.id" :disabled="!v.allowsOperations">{{displayText(v.make)}} {{displayText(v.model)}} · {{v.plate}} · {{displayText(v.customerName)}}{{v.allowsOperations?'':' · Sin operaciones'}}</option></select></label><div class="button-row"><button type="button" :disabled="loading||page===1" @click="page--">Anterior</button><span>Página {{page}} de {{result.totalPages||1}}</span><button type="button" :disabled="loading||page>=result.totalPages" @click="page++">Siguiente</button></div><p v-if="error" class="error" role="alert">{{error}}</p></fieldset></template>
