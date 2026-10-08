<script setup lang="ts">
import {ref,watch} from 'vue'
import type {EntityStatus} from '../types/status'
import {statusLabel} from '../types/status'
const props=defineProps<{statuses:EntityStatus[];statusId:number;busy?:boolean}>()
const emit=defineEmits<{change:[number]}>(),chosen=ref(props.statusId)
watch(()=>props.statusId,v=>chosen.value=v)
</script>
<template><div class="button-row"><label>Estatus<select v-model="chosen" :disabled="busy"><option v-for="s in statuses" :key="s.id" :value="s.id">{{statusLabel(s)}} · {{s.allowsOperations?'Permite operaciones':'Sin operaciones'}}</option></select></label><button class="btn secondary" :disabled="busy||chosen===statusId" @click="emit('change',Number(chosen))">Aplicar estatus</button></div></template>
