<script setup lang="ts">
import {ref,onMounted,onUnmounted,watch,computed} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {customerFacade} from '../facade/customerFacade'
import type {Customer,CustomerPage,WorkshopOption,CustomerWorkshop} from '../types/customer'
import CustomerForm from '../components/CustomerForm.vue'
import CustomerAssociations from '../components/CustomerAssociations.vue'
import Modal from '../../../components/Modal.vue'
import StatusSelector from '../../statuses/components/StatusSelector.vue'
import {statusApi} from '../../statuses/api/statusApi'
import {statusLabel,type EntityStatus} from '../../statuses/types/status'
import Icon from '../../../components/Icon.vue'
import {notify,session} from '../../../api'
import {displayText,displayIdentifier,displayBirthDate,displayPhone} from '../../shared/presentation'
const route=useRoute(),router=useRouter()
const result=ref<CustomerPage>({items:[],page:1,pageSize:10,totalItems:0,totalPages:0}),selected=ref<Customer|null>(null),associated=ref<CustomerWorkshop[]>([]),editing=ref(false),workshops=ref<WorkshopOption[]>([]),workshopId=ref<number|''>(''),query=ref(''),direction=ref('ASC'),page=ref(1),loading=ref(false),busy=ref(false),error=ref('')
const statuses=ref<EntityStatus[]>([]),statusFilter=ref<number|''>('')
const pending=ref(false),initialTarget=ref<number|''>('')
const initials=(name:string)=>name.split(/\s+/).map(s=>s[0]).slice(0,2).join('').toUpperCase()
let generation=0,timer:ReturnType<typeof setTimeout>|undefined
const pages=computed(()=>{const start=Math.max(1,Math.min(page.value-2,result.value.totalPages-4));return Array.from({length:Math.min(5,result.value.totalPages)},(_,i)=>start+i)})
async function load(){if(!workshopId.value&&!pending.value)return;const request=++generation;loading.value=true;error.value='';try{const data=pending.value?await customerFacade.unassigned(page.value,direction.value,query.value):await customerFacade.list(Number(workshopId.value),page.value,direction.value,query.value,statusFilter.value||undefined);if(request===generation)result.value=data}catch(e){if(request===generation)error.value=(e as Error).message}finally{if(request===generation)loading.value=false}}
async function initialize(){loading.value=true;error.value='';try{[workshops.value,statuses.value]=await Promise.all([customerFacade.workshopsList(),statusApi.list('customers')]);if(workshops.value.length)workshopId.value=workshops.value.find(w=>w.id===Number(route.query.workshopId))?.id??workshops.value[0]!.id}catch(e){error.value=(e as Error).message}finally{loading.value=false}}
watch(pending,()=>{selected.value=null;editing.value=false;page.value=1;void load()})
watch(workshopId,()=>{void router.replace({path:'/customers',query:{workshopId:workshopId.value}});selected.value=null;editing.value=false;page.value=1;void load()})
watch(()=>route.query.workshopId,id=>{const found=workshops.value.find(w=>w.id===Number(id));if(found&&workshopId.value!==found.id)workshopId.value=found.id})
watch(statusFilter,()=>{if(page.value!==1)page.value=1;else void load()})
watch([page,direction],()=>{void load()})
watch(query,()=>{clearTimeout(timer);timer=setTimeout(()=>{if(page.value!==1)page.value=1;else void load()},300)})
async function select(id:number){if(busy.value||!workshopId.value)return;busy.value=true;error.value='';try{const [customer,links]=await Promise.all([customerFacade.get(id,Number(workshopId.value)),customerFacade.workshops(id,Number(workshopId.value))]);selected.value=customer;associated.value=links;editing.value=false}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
async function saved(){const id=selected.value?.id;await load();if(id)await select(id)}
async function changed(){const id=selected.value?.id;selected.value=null;await load();if(id&&result.value.items.some(c=>c.id===id))await select(id);notify('Asociación actualizada')}
async function initial(id:number){if(busy.value||!initialTarget.value)return;busy.value=true;error.value='';try{await customerFacade.initialWorkshop(id,Number(initialTarget.value));await load();notify('Cliente asociado al taller')}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
async function status(target:number){if(!selected.value||busy.value)return;busy.value=true;error.value='';try{selected.value=await customerFacade.status(selected.value.id,Number(workshopId.value),target,selected.value.version);notify('Estatus actualizado');await load()}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
function toggleStatus(){if(!selected.value)return;const code=selected.value.allowsOperations?'SUSPENDED':'ACTIVE';const target=statuses.value.find(s=>s.code===code);if(target)void status(target.id)}

onMounted(initialize);onUnmounted(()=>{generation++;clearTimeout(timer)})
</script>
<template>
 <section class="page customer-list">
  <div class="page-heading"><div><p class="eyebrow">DIRECTORIO DEL TALLER</p><h1>Clientes</h1><p class="muted">Consulta y administra los clientes del taller seleccionado.</p></div><RouterLink to="/customers/new" class="btn primary"><Icon name="plus" :size="18"/>Registrar cliente</RouterLink></div>
  <div v-if="session.user?.role==='ADMIN'" class="button-row"><button class="btn secondary" :disabled="busy" @click="pending=!pending">{{pending?'Volver al directorio por taller':'Clientes pendientes de taller'}}</button><p v-if="pending" class="field-hint">Registros anteriores o cuentas de cliente sin asociación activa. Asigna un taller para administrar su ficha.</p></div>
  <p v-if="error" class="error" role="alert">{{error}} <button class="text-link" @click="workshopId?load():initialize()">Reintentar</button></p>
  <section class="panel directory-panel" :aria-busy="loading">
   <div class="table-toolbar"><label v-if="!pending">Taller<select v-model="workshopId" :disabled="busy||editing"><option value="" disabled>Selecciona un taller</option><option v-for="w in workshops" :key="w.id" :value="w.id">{{displayText(w.name)}}</option></select></label><label v-if="pending">Taller de destino<select v-model="initialTarget" :disabled="busy"><option value="" disabled>Selecciona un taller</option><option v-for="w in workshops" :key="w.id" :value="w.id">{{displayText(w.name)}}</option></select></label><div class="search"><Icon name="search"/><input v-model="query" maxlength="160" placeholder="Nombre, teléfono o email" aria-label="Buscar cliente"></div><label>Orden por nombre<select v-model="direction"><option value="ASC">A → Z</option><option value="DESC">Z → A</option></select></label><label v-if="!pending">Estatus<select v-model="statusFilter"><option value="">Todos</option><option v-for="s in statuses" :key="s.id" :value="s.id">{{statusLabel(s)}}</option></select></label><span class="result-count">{{result.totalItems}} clientes</span></div>
   <div v-if="loading" class="loading" role="status">Cargando clientes…</div>
   <div v-else-if="!workshops.length" class="empty"><h3>No tienes talleres disponibles</h3><p>Administración debe registrar un taller o asignarte acceso.</p></div>
   <div v-else class="table-wrap"><table v-if="result.items.length"><thead><tr><th>Cliente</th><th>Contacto</th><th>Estado</th><th>Ficha</th></tr></thead><tbody><tr v-for="c in result.items" :key="c.id"><td><div class="customer-identity"><img v-if="c.photoReference&&!pending" class="customer-avatar" :src="customerFacade.photoUrl(c.photoReference,Number(workshopId))" alt=""><span v-else class="customer-avatar">{{initials(c.fullName)}}</span><strong>{{displayText(c.fullName)}}</strong></div></td><td>{{displayPhone(c.personalPhone||c.cellPhone||c.workPhone)||'Sin teléfono'}}<small>{{c.personalEmail||c.workEmail||'Sin email'}}</small></td><td><span class="page-tag">{{statusLabel({code:c.statusCode})}}</span></td><td><button v-if="pending" class="btn secondary small-btn" :disabled="busy||!initialTarget" @click="initial(c.id)">Asignar taller</button><button v-else class="btn secondary small-btn" :disabled="busy" @click="select(c.id)">Ver ficha</button></td></tr></tbody></table><div v-else class="empty"><h3>Sin clientes en esta consulta</h3><p>Prueba otra búsqueda o registra un cliente.</p></div></div>
   <nav v-if="result.totalPages" class="pagination" aria-label="Páginas de clientes"><button class="btn secondary small-btn" :disabled="loading||page<=1" @click="page--">Anterior</button><button v-for="p in pages" :key="p" class="btn secondary small-btn" :aria-current="p===page?'page':undefined" :disabled="loading||p===page" @click="page=p">{{p}}</button><button class="btn secondary small-btn" :disabled="loading||page>=result.totalPages" @click="page++">Siguiente</button><span class="field-hint">Página {{result.page}} de {{result.totalPages}} · Hasta 10 por página</span></nav>
  </section>
  <section v-if="selected" :key="selected.id" class="customer-detail" aria-label="Ficha del cliente">
   <header class="customer-detail-header"><div><p class="eyebrow">FICHA · #{{selected.id}} · {{statusLabel({code:selected.statusCode})}}</p><h2>{{displayText(selected.fullName)}}</h2></div><div class="button-row"><button class="btn secondary" :disabled="busy" @click="editing=!editing">{{editing?'Cancelar edición':'Editar cliente'}}</button><button class="btn secondary" :disabled="busy||editing" @click="toggleStatus">{{selected.allowsOperations?'Suspender':'Reactivar'}}</button><button class="icon-button" :disabled="busy" aria-label="Cerrar ficha" @click="selected=null"><Icon name="close"/></button></div></header>
   <Modal v-if="editing" title="Editar cliente" :busy="busy" embedded-form wide @close="editing=false"><CustomerForm :initial="selected" :workshops="workshops" :customer-id="selected.id" :workshop-id="Number(workshopId)" @created="saved" @cancel="editing=false" @busy="busy=$event" /></Modal>
   <div class="customer-info-grid">
    <section class="info-section"><StatusSelector :statuses="statuses" :status-id="selected.statusId" :busy="busy||editing" @change="status"/><p>{{selected.statusDescription}}</p><h3>Identidad</h3><dl><div><dt>Nombre</dt><dd>{{displayText(selected.fullName)}}</dd></div><div><dt>CURP</dt><dd>{{displayIdentifier(selected.curp)||'No registrada'}}</dd></div><div><dt>RFC</dt><dd>{{displayIdentifier(selected.rfc)||'No registrado'}}</dd></div><div><dt>Nacimiento / edad</dt><dd>{{displayBirthDate(selected.birthDate)||'No registrado'}} · {{selected.age===null?'—':selected.age+' años'}}</dd></div><div><dt>Alias</dt><dd>{{displayText(selected.alias)||'Sin alias'}}</dd></div><div><dt>Contacto alternativo</dt><dd>{{displayText(selected.alternativeContactName)||'No registrado'}}</dd></div></dl></section>
    <section class="info-section"><h3>Contacto</h3><dl><div><dt>Email</dt><dd>{{selected.personalEmail||'No registrado'}}</dd></div><div><dt>Email de trabajo</dt><dd>{{selected.workEmail||'No registrado'}}</dd></div><div><dt>Personal</dt><dd>{{displayPhone(selected.personalPhone)||'No registrado'}}</dd></div><div><dt>Celular</dt><dd>{{displayPhone(selected.cellPhone)||'No registrado'}}</dd></div><div><dt>Trabajo</dt><dd>{{displayPhone(selected.workPhone)||'No registrado'}}</dd></div></dl></section>
    <section class="info-section"><h3>Dirección</h3><p>{{displayText(selected.street)}}<br>{{displayText(selected.neighborhood)}}<br>{{displayText(selected.municipality)}}, {{displayText(selected.state)}}<br>CP {{selected.postalCode}}</p></section>
    <CustomerAssociations :id="selected.id" :workshop-id="Number(workshopId)" :associated="associated" :workshops="workshops" @changed="changed" @busy="busy=$event" />
   </div>
  </section>
 </section>
</template>
