<script setup lang="ts">
import {ref,computed,onUnmounted} from 'vue'
import {api,session,notify,roles,uploadProfilePhoto} from '../api'
import Icon from '../components/Icon.vue'
const name=ref(session.user?.name||''),phone=ref(session.user?.phone||''),birthDate=ref(session.user?.birthDate||''),bio=ref(session.user?.bio||'')
const busy=ref(false),passwordBusy=ref(false),photoBusy=ref(false),currentPassword=ref(''),password=ref(''),showPassword=ref(false)
const error=ref(''),photoError=ref(''),passwordError=ref(''),preview=ref(''),selectedFile=ref<File|null>(null),fileInput=ref<HTMLInputElement>()
const today=new Date().toLocaleDateString('en-CA')
const initials=computed(()=>name.value.trim().split(/\s+/).slice(0,2).map((v:string)=>v[0]).join('').toUpperCase())
let reader:FileReader|null=null
onUnmounted(()=>reader?.abort())
async function save(){busy.value=true;error.value='';try{await api('/account','PUT',{name:name.value.trim(),phone:phone.value,birthDate:birthDate.value||null,bio:bio.value});session.user=await api('/auth/me');notify('Perfil actualizado')}catch(e:any){error.value=e.message}finally{busy.value=false}}
async function change(){passwordBusy.value=true;passwordError.value='';try{await api('/account/password','POST',{currentPassword:currentPassword.value,password:password.value});session.user=null;location.hash='/login';notify('Contraseña actualizada. Inicia sesión nuevamente.')}catch(e:any){passwordError.value=e.message}finally{passwordBusy.value=false}}
function discardPhoto(){reader?.abort();preview.value='';selectedFile.value=null;if(fileInput.value)fileInput.value.value=''}
function choosePhoto(event:Event){
  const file=(event.target as HTMLInputElement).files?.[0];discardPhoto();photoError.value='';if(!file)return
  if(!['image/jpeg','image/png'].includes(file.type)){photoError.value='Selecciona una imagen JPG o PNG.';return}
  if(file.size===0||file.size>15*1024*1024){photoError.value='La fotografía debe pesar entre 1 byte y 15 MB.';return}
  reader=new FileReader();reader.onload=()=>{selectedFile.value=file;preview.value=String(reader?.result||'')};reader.onerror=()=>{photoError.value='No se pudo leer la fotografía.'};reader.readAsDataURL(file)
}
async function savePhoto(){if(!selectedFile.value)return;photoBusy.value=true;photoError.value='';try{session.user=await uploadProfilePhoto(selectedFile.value);discardPhoto();notify('Fotografía actualizada')}catch(e:any){photoError.value=e.message}finally{photoBusy.value=false}}
async function removePhoto(){photoBusy.value=true;photoError.value='';try{await api('/account/photo','DELETE');session.user=await api('/auth/me');discardPhoto();notify('Fotografía eliminada')}catch(e:any){photoError.value=e.message}finally{photoBusy.value=false}}
</script>
<template>
  <div class="page-heading"><div><p class="eyebrow">TU ESPACIO PERSONAL</p><h1>Mi perfil</h1><p class="muted">Tu información, tu imagen y la seguridad de tu cuenta.</p></div><span class="page-tag"><Icon name="shield" :size="16"/>{{roles[session.user?.role]}}</span></div>
  <div class="profile-layout">
    <aside class="profile-aside">
      <section class="panel profile-photo-card"><div class="profile-cover"></div><div class="profile-photo-body">
        <img v-if="preview||session.user?.photoUrl" :src="preview||session.user?.photoUrl" class="profile-portrait" alt="Fotografía de perfil"><div v-else class="profile-portrait profile-initials">{{initials}}</div>
        <h2>{{session.user?.name}}</h2><p class="muted profile-email">{{session.user?.email}}</p><span class="page-tag">{{roles[session.user?.role]}}</span>
        <label class="upload-control"><Icon name="upload" :size="17"/>{{session.user?.photoUrl?'Cambiar fotografía':'Seleccionar fotografía'}}<input ref="fileInput" type="file" accept="image/jpeg,image/png" :disabled="photoBusy" aria-label="Seleccionar fotografía de perfil" @change="choosePhoto"></label><p class="field-hint">JPG o PNG · Hasta 15 MB y 16 megapíxeles</p>
        <div v-if="selectedFile" class="profile-photo-actions"><p class="photo-file-name">{{selectedFile.name}}</p><button class="btn primary full" :disabled="photoBusy" @click="savePhoto"><span v-if="photoBusy" class="loader small-loader"></span>{{photoBusy?'Guardando…':'Guardar fotografía'}}</button><button class="text-link" :disabled="photoBusy" @click="discardPhoto">Descartar selección</button></div>
        <button v-else-if="session.user?.photoUrl" class="text-link" :disabled="photoBusy" @click="removePhoto">Quitar fotografía</button><p v-if="photoError" class="error" role="alert">{{photoError}}</p>
      </div></section>
      <div class="profile-privacy"><Icon name="shield" :size="19"/><p>Tu fotografía pertenece a tu cuenta. Los permisos los administra el taller.</p></div>
    </aside>
    <div class="profile-sections">
      <section class="panel"><div class="panel-heading"><div><h2>Información personal</h2><p class="muted small">Así te identificas dentro de TallerMeco.</p></div><Icon name="users"/></div><form class="padded-form" @submit.prevent="save"><fieldset :disabled="busy"><div class="form-grid">
        <label class="span-two">Nombre completo<input v-model="name" required minlength="2" maxlength="160" autocomplete="name"></label>
        <label>Teléfono<input v-model="phone" type="tel" inputmode="tel" pattern="[+()0-9 \-]{7,30}" maxlength="30" autocomplete="tel" placeholder="55 1234 5678" @input="phone=phone.replace(/[^+()0-9 \-]/g,'')"></label>
        <label>Fecha de nacimiento<input v-model="birthDate" type="date" :max="today" autocomplete="bday"></label>
        <label class="span-two">Sobre mí <small>(opcional)</small><textarea v-model="bio" rows="3" maxlength="500" placeholder="Cuéntanos un poco de ti o de tu trabajo en el taller."></textarea><small class="field-hint">{{bio.length}} / 500 caracteres</small></label>
        <label class="span-two">Correo de acceso<input :value="session.user?.email" type="email" readonly><small class="field-hint">El correo y el rol no se modifican desde el perfil.</small></label>
      </div></fieldset><p v-if="error" class="error" role="alert">{{error}}</p><button class="btn primary" :disabled="busy"><span v-if="busy" class="loader small-loader"></span>{{busy?'Guardando…':'Guardar perfil'}}</button></form></section>
      <section class="panel"><div class="panel-heading"><div><h2>Seguridad</h2><p class="muted small">Usa una contraseña que solo tú conozcas.</p></div><Icon name="shield"/></div>
        <form class="padded-form" @submit.prevent="change"><div class="form-grid"><label>Contraseña actual<input v-model="currentPassword" type="password" required autocomplete="current-password"></label><label>Nueva contraseña<div class="password-field"><input v-model="password" :type="showPassword?'text':'password'" required minlength="12" maxlength="128" autocomplete="new-password"><button type="button" class="password-toggle" :aria-label="showPassword?'Ocultar nueva contraseña':'Mostrar nueva contraseña'" :aria-pressed="showPassword" @click="showPassword=!showPassword"><Icon :name="showPassword?'eye-off':'eye'" :size="18"/></button></div><small class="field-hint">Mínimo 12 caracteres. Al cambiarla, volverás a iniciar sesión.</small></label></div><p v-if="passwordError" class="error" role="alert">{{passwordError}}</p><button class="btn secondary" :disabled="passwordBusy">{{passwordBusy?'Actualizando…':'Cambiar contraseña'}}</button></form>
      </section>
    </div>
  </div>
</template>
