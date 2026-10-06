<script setup lang="ts">
import {ref,onMounted} from 'vue'
import {useRouter} from 'vue-router'
import type {Customer} from '../types/customer'
import CustomerForm from '../components/CustomerForm.vue'
import Icon from '../../../components/Icon.vue'
import {customerFacade} from '../facade/customerFacade'
const router=useRouter()
function created(_customer:Customer,workshopId:number){void router.push({path:'/customers',query:{workshopId}})}
const workshops=ref<any[]>([]),loading=ref(true),error=ref('')
async function load(){loading.value=true;error.value='';try{workshops.value=await customerFacade.workshopsList()}catch(e:any){error.value=e.message}finally{loading.value=false}}
onMounted(load)
</script>
<template>
  <section class="page customer-create">
    <RouterLink to="/customers" class="back-link"><Icon name="back" :size="16"/>Volver a clientes</RouterLink>
    <div class="page-heading"><div><p class="eyebrow">DIRECTORIO DE CLIENTES</p><h1>Registrar cliente</h1><p class="muted">Un perfil completo para ofrecer un mejor servicio.</p></div><span class="page-tag"><Icon name="users" :size="16"/>Nuevo perfil</span></div>
    <div v-if="loading" class="loading panel" role="status"><span class="loader"></span>Preparando formulario…</div>
    <div v-else-if="error" class="error" role="alert">{{error}} <button class="text-link" @click="load">Reintentar</button></div>
    <CustomerForm v-else :workshops="workshops" @created="created" @cancel="$router.push('/customers')"/>
  </section>
</template>
