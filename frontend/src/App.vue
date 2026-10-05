<script setup lang="ts">
import {computed,ref,watch,nextTick,onUnmounted} from 'vue';import {useRoute} from 'vue-router';import {session,logout,roles,alert} from './api';import Icon from './components/Icon.vue'
const route=useRoute();const mobile=ref(false)
function focusContent(){document.getElementById('main-content')?.focus()}
watch(()=>route.path,()=>{mobile.value=false})
const menuMedia=matchMedia('(max-width:800px)')
const previousOverflow=document.body.style.overflow
watch(mobile,async open=>{
  document.body.style.overflow=open&&menuMedia.matches?'hidden':previousOverflow
  await nextTick()
  if(menuMedia.matches){
    const target=document.querySelector<HTMLElement>(open?'#sidebar a':'.mobile-toggle')
    target?.focus()
  }
})
function resized(){if(!menuMedia.matches)mobile.value=false}
menuMedia.addEventListener('change',resized)
onUnmounted(()=>{menuMedia.removeEventListener('change',resized);document.body.style.overflow=previousOverflow})
function trapMenu(event:KeyboardEvent){
  if(!mobile.value||!menuMedia.matches)return
  const items=document.querySelectorAll<HTMLElement>('#sidebar a, #sidebar button:not(:disabled)')
  const first=items[0],last=items[items.length-1]
  if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus()}
  else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus()}
}
const nav=computed(()=>session.user?.role==='RECEPTIONIST'?[{to:'/customers',label:'Clientes',icon:'users'},{to:'/account',label:'Mi cuenta',icon:'shield'}]:[{to:'/',label:'Resumen',icon:'dashboard'},{to:'/orders',label:session.user?.role==='MECHANIC'?'Mis trabajos':'Órdenes de servicio',icon:'orders'},...(session.user?.role==='ADMIN'?[{to:'/customers',label:'Clientes',icon:'users'}]:[]),{to:'/vehicles',label:'Vehículos',icon:'car'},...(session.user?.role!=='CLIENT'?[{to:'/inventory',label:'Inventario',icon:'box'}]:[]),...(session.user?.role==='ADMIN'?[{to:'/employees',label:'Equipo',icon:'wrench'},{to:'/reports',label:'Reportes',icon:'chart'},{to:'/audit',label:'Auditoría',icon:'shield'}]:[])])
const section=computed(()=>nav.value.find(n=>n.to==='/'?route.path==='/':route.path.startsWith(n.to))?.label||'Mi cuenta')
</script>
<template><a v-if="session.user" class="skip-link" href="#main-content" @click.prevent="focusContent">Ir al contenido</a><div v-if="session.loading" class="loading-screen"><span class="loader" aria-hidden="true"></span>Preparando el taller…</div><template v-else-if="session.user&&!['/forgot','/reset','/register'].includes(route.path)"><aside id="sidebar" class="sidebar" :class="{open:mobile}" @keydown.esc="mobile=false" @keydown.tab="trapMenu"><RouterLink class="brand" to="/"><span class="brand-icon"><Icon name="wrench" :size="25"/></span><span>Taller<span class="brand-light">Meco</span><small>GESTIÓN DEL TALLER</small></span></RouterLink><p class="nav-caption">ESPACIO DE TRABAJO</p><nav aria-label="Navegación principal"><RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{selected:item.to==='/'?route.path==='/':route.path.startsWith(item.to)}" @click="mobile=false"><Icon :name="item.icon"/>{{item.label}}</RouterLink></nav><div class="sidebar-bottom"><div class="local-note"><span class="status-dot"></span>Tu taller, en orden.<small>Todo lo importante, en un lugar.</small></div><RouterLink to="/account" class="profile-link"><img v-if="session.user.photoUrl" :src="session.user.photoUrl" class="avatar profile-avatar" alt=""><span v-else class="avatar">{{session.user.name?.slice(0,2).toUpperCase()}}</span><span><strong>{{session.user.name}}</strong><small>{{roles[session.user.role]}}</small></span></RouterLink><button class="signout" @click="logout"><Icon name="logout" :size="17"/>Cerrar sesión</button></div></aside><button v-if="mobile" class="sidebar-overlay" aria-label="Cerrar menú" @click="mobile=false"></button><div class="workspace"><header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-toggle" aria-label="Abrir menú" :aria-expanded="mobile" aria-controls="sidebar" @click="mobile=!mobile"><Icon name="menu"/></button><span>Mi taller</span><Icon name="chevron" :size="14"/><strong>{{section}}</strong></div><div class="topbar-right"><span class="today">{{new Date().toLocaleDateString('es-MX',{day:'numeric',month:'long',year:'numeric'})}}</span><span class="role-pill">{{roles[session.user.role]}}</span></div></header><main id="main-content" tabindex="-1"><RouterView v-slot="{Component}"><Transition name="page" mode="out-in"><div :key="route.path" class="route-page"><component :is="Component"/></div></Transition></RouterView></main><footer class="app-footer"><span>TallerMeco</span><span>Hecho para mantener tu taller en movimiento.</span></footer></div></template><RouterView v-else :key="route.path"/><Transition name="toast"><div v-if="alert.message" class="toast" :class="alert.type" role="status"><Icon :name="alert.type==='success'?'check':'shield'"/>{{alert.message}}<button class="icon-button" aria-label="Cerrar notificación" @click="alert.message=''"><Icon name="close" :size="16"/></button></div></Transition></template>
