<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue';import Icon from './Icon.vue'
defineProps<{title:string;busy?:boolean;error?:string}>();const emit=defineEmits(['close','submit']);const dialog=ref<HTMLDialogElement>();let previous:Element|null=null
onMounted(()=>{previous=document.activeElement;dialog.value?.showModal()});onUnmounted(()=>{if(previous instanceof HTMLElement)previous.focus()})
</script>
<template><dialog ref="dialog" class="modal" @cancel.prevent="!busy&&emit('close')"><form @submit.prevent="emit('submit')"><header><div><p class="eyebrow">TALLERMECO</p><h2>{{title}}</h2></div><button type="button" class="icon-button" aria-label="Cerrar" :disabled="busy" @click="emit('close')"><Icon name="close"/></button></header><div class="modal-body"><slot/><p v-if="error" class="error" role="alert">{{error}}</p></div><footer><button type="button" class="btn secondary" :disabled="busy" @click="emit('close')">Cancelar</button><button class="btn primary" :disabled="busy">{{busy?'Guardando…':'Guardar cambios'}}</button></footer></form></dialog></template>
