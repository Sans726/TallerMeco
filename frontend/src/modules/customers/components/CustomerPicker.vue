<script setup lang="ts">
import {ref,watch,onMounted,onUnmounted} from 'vue'
import {customerFacade} from '../facade/customerFacade'
import type {CustomerPage,WorkshopOption} from '../types/customer'
defineProps<{disabled?:boolean}>()
const value=defineModel<number|''>({default:''})
const workshops=ref<WorkshopOption[]>([]),workshop=ref<number|''>(''),page=ref(1),query=ref(''),result=ref<CustomerPage>({items:[],page:1,pageSize:10,totalItems:0,totalPages:0}),error=ref(''),loading=ref(false)
let generation=0,timer:ReturnType<typeof setTimeout>|undefined
async function load(){if(!workshop.value)return;const request=++generation;loading.value=true;error.value='';try{const data=await customerFacade.list(Number(workshop.value),page.value,'ASC',query.value);if(request===generation)result.value=data}catch(e){if(request===generation)error.value=(e as Error).message}finally{if(request===generation)loading.value=false}}
watch(workshop,()=>{value.value='';page.value=1;void load()});watch(page,()=>{value.value='';void load()});watch(query,()=>{clearTimeout(timer);timer=setTimeout(()=>{value.value='';if(page.value!==1)page.value=1;else void load()},300)})
onMounted(async()=>{try{workshops.value=await customerFacade.workshopsList();if(workshops.value.length)workshop.value=workshops.value[0]!.id}catch(e){error.value=(e as Error).message}})
onUnmounted(()=>{generation++;clearTimeout(timer)})
</script>
<template><fieldset class="customer-picker" :disabled="disabled"><label>Taller<select v-model="workshop" required><option value="" disabled>Selecciona un taller</option><option v-for="w in workshops" :key="w.id" :value="w.id">{{w.name}}</option></select></label><label>Buscar cliente<input v-model="query" maxlength="160" placeholder="Nombre, teléfono o email"></label><label>Cliente<select name="customerId" v-model="value" required :disabled="loading"><option value="" disabled>Selecciona un cliente</option><option v-for="c in result.items" :key="c.id" :value="c.id" :disabled="!c.active">{{c.fullName}}{{c.active?'':' (suspendido)'}}</option></select></label><div class="button-row"><button type="button" class="text-link" :disabled="loading||page<=1" @click="page--">Anterior</button><span class="field-hint">Página {{page}} de {{result.totalPages||1}}</span><button type="button" class="text-link" :disabled="loading||page>=result.totalPages" @click="page++">Siguiente</button></div><p v-if="error" class="field-error" role="alert">{{error}}</p></fieldset></template>
