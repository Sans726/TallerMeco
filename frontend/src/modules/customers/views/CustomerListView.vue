<script setup lang="ts">
import {ref,onMounted,computed,nextTick} from 'vue'
import {customerFacade} from '../facade/customerFacade'
import CustomerForm from '../components/CustomerForm.vue'
import Icon from '../../../components/Icon.vue'
import {session} from '../../../api'
const rows=ref<any[]>([]),selected=ref<any|null>(null),editing=ref(false),workshops=ref<any[]>([]),query=ref('')
const loading=ref(true),opening=ref<number|null>(null),error=ref(''),detail=ref<HTMLElement|null>(null)
const canManage=computed(()=>['ADMIN','RECEPTIONIST'].includes(session.user?.role))
const filtered=computed(()=>rows.value.filter(r=>JSON.stringify(r).toLowerCase().includes(query.value.toLowerCase())))
const contactCount=computed(()=>rows.value.filter(r=>r.personal_phone||r.personal_email).length)
const initials=(name:string)=>String(name||'').trim().split(/\s+/).map(s=>s[0]).slice(0,2).join('').toUpperCase()
async function load(){loading.value=true;error.value='';try{[rows.value,workshops.value]=await Promise.all([customerFacade.list(),customerFacade.workshopsList()])}catch(e:any){error.value=e.message}finally{loading.value=false}}
async function select(row:any){
  opening.value=row.id;error.value=''
  try{const [customer,associated]=await Promise.all([customerFacade.get(row.id),customerFacade.workshops(row.id)]);selected.value={...customer,workshops:associated};editing.value=false;await nextTick();detail.value?.scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth',block:'start'});detail.value?.focus({preventScroll:true})}
  catch(e:any){error.value=e.message}finally{opening.value=null}
}
async function saved(){const id=selected.value.id;await load();await select({id})}
onMounted(load)
</script>
<template>
  <section class="page customer-list">
    <div class="page-heading"><div><p class="eyebrow">DIRECTORIO DEL TALLER</p><h1>Clientes</h1><p class="muted">Conoce a las personas que confían en tu taller.</p></div><RouterLink v-if="canManage" to="/customers/new" class="btn primary"><Icon name="plus" :size="18"/>Registrar cliente</RouterLink></div>
    <div class="directory-summary"><div><span class="summary-symbol"><Icon name="users"/></span><span><strong>{{loading?'—':rows.length}}</strong><small>Clientes registrados</small></span></div><div><span class="summary-symbol"><Icon name="mail"/></span><span><strong>{{loading?'—':contactCount}}</strong><small>Con datos de contacto</small></span></div><div><span class="summary-symbol"><Icon name="wrench"/></span><span><strong>{{loading?'—':workshops.length}}</strong><small>Talleres disponibles</small></span></div></div>
    <p v-if="error" class="error" role="alert">{{error}} <button class="text-link" @click="load">Reintentar</button></p>
    <section class="panel directory-panel" aria-label="Directorio de clientes" :aria-busy="loading">
      <div class="table-toolbar"><div class="search"><Icon name="search" :size="19"/><input v-model="query" placeholder="Buscar por nombre, teléfono o email…" aria-label="Buscar cliente"><button v-if="query" class="icon-button" @click="query=''" aria-label="Limpiar búsqueda"><Icon name="close" :size="16"/></button></div><span class="result-count">{{filtered.length}} {{filtered.length===1?'cliente':'clientes'}}</span></div>
      <div v-if="loading" class="skeleton-table" role="status" aria-label="Cargando clientes"><div v-for="n in 4" :key="n" class="skeleton-row"><span class="skeleton skeleton-avatar"></span><span class="skeleton skeleton-line"></span><span class="skeleton skeleton-line"></span></div></div>
      <div v-else class="table-wrap"><table v-if="filtered.length"><thead><tr><th>Cliente</th><th>Contacto</th><th>Municipio</th><th class="align-right">Ficha del cliente</th></tr></thead><tbody><tr v-for="r in filtered" :key="r.id" :class="{'selected-row':selected?.id===r.id}"><td><div class="customer-identity"><img v-if="r.photo_reference" class="customer-avatar" :src="'/api/customers/photos/'+r.photo_reference" alt=""><span v-else class="customer-avatar">{{initials(r.full_name)}}</span><div><strong>{{r.full_name}}</strong><small>{{r.alias||'Cliente #'+r.id}}</small></div></div></td><td><span>{{r.personal_phone||'Sin teléfono'}}</span><small>{{r.personal_email||'Sin email'}}</small></td><td>{{r.municipality||'—'}}</td><td class="align-right"><button class="btn secondary small-btn" :disabled="opening!==null" @click="select(r)"><span v-if="opening===r.id" class="loader small-loader"></span>{{opening===r.id?'Abriendo…':'Ver ficha'}}<Icon name="arrow" :size="15"/></button></td></tr></tbody></table>
        <div v-else class="empty directory-empty"><span class="empty-symbol"><Icon :name="query?'search':'users'" :size="30"/></span><h3>{{query?'No encontramos coincidencias':'Tu directorio empieza aquí'}}</h3><p>{{query?'Prueba con otro nombre, teléfono o correo electrónico.':'Reúne la información de cada cliente y ten sus datos siempre a mano.'}}</p><button v-if="query" class="btn secondary" @click="query=''">Limpiar búsqueda</button><RouterLink v-else-if="canManage" to="/customers/new" class="btn secondary"><Icon name="plus" :size="17"/>Registrar primer cliente</RouterLink></div>
      </div>
    </section>
    <Transition name="reveal">
      <section v-if="selected" :key="selected.id" ref="detail" tabindex="-1" class="customer-detail" aria-label="Ficha del cliente">
        <header class="customer-detail-header"><div class="customer-identity"><img v-if="selected.photoReference" class="customer-avatar large" :src="'/api/customers/photos/'+selected.photoReference" alt="Fotografía del cliente"><span v-else class="customer-avatar large">{{initials(selected.fullName)}}</span><div><p class="eyebrow">FICHA DEL CLIENTE · #{{selected.id}}</p><h2>{{selected.fullName}}</h2></div></div><div class="button-row"><button v-if="canManage" class="btn secondary" @click="editing=!editing"><Icon :name="editing?'close':'edit'" :size="17"/>{{editing?'Cancelar edición':'Editar cliente'}}</button><button class="icon-button" aria-label="Cerrar ficha" @click="selected=null"><Icon name="close"/></button></div></header>
        <CustomerForm v-if="editing" :initial="selected" :workshops="workshops" :customer-id="selected.id" @created="saved" @cancel="editing=false"/>
        <div v-else class="customer-info-grid">
          <section class="info-section"><h3><Icon name="users" :size="18"/>Información personal</h3><dl><div><dt>Nombre completo</dt><dd>{{selected.fullName}}</dd></div><div><dt>Alias</dt><dd>{{selected.alias||'Sin alias'}}</dd></div><div><dt>Contacto alternativo</dt><dd>{{selected.alternativeContactName||'No registrado'}}</dd></div><div><dt>Fecha de nacimiento</dt><dd>{{selected.birthDate||'No registrada'}}</dd></div></dl></section>
          <section class="info-section"><h3><Icon name="mail" :size="18"/>Contacto</h3><dl><div><dt>Teléfono personal</dt><dd>{{selected.personalPhone||'No registrado'}}</dd></div><div><dt>Teléfono de trabajo</dt><dd>{{selected.workPhone||'No registrado'}}</dd></div><div><dt>Email personal</dt><dd>{{selected.personalEmail||'No registrado'}}</dd></div><div><dt>Email de trabajo</dt><dd>{{selected.workEmail||'No registrado'}}</dd></div></dl></section>
          <section class="info-section"><h3><Icon name="pin" :size="18"/>Dirección</h3><dl><div><dt>Calle / colonia</dt><dd>{{[selected.street,selected.neighborhood].filter(Boolean).join(', ')||'No registrada'}}</dd></div><div><dt>Municipio / estado</dt><dd>{{[selected.municipality,selected.state].filter(Boolean).join(', ')||'No registrado'}}</dd></div><div><dt>Código postal</dt><dd>{{selected.postalCode||'No registrado'}}</dd></div></dl></section>
          <section class="info-section"><h3><Icon name="wrench" :size="18"/>Talleres asociados</h3><div class="workshop-tags"><span v-for="w in selected.workshops" :key="w.id" class="page-tag"><Icon name="pin" :size="15"/>{{w.name}}</span><p v-if="!selected.workshops.length" class="muted">Sin talleres asociados.</p></div></section>
        </div>
      </section>
    </Transition>
  </section>
</template>
