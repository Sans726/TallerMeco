<script setup lang="ts">
withDefaults(defineProps<{label?:string}>(),{label:'Cargando registros'})
</script>
<template><div class="skeleton-table" role="status" :aria-label="label"><div v-for="n in 4" :key="n" class="skeleton-row" aria-hidden="true"><span class="skeleton skeleton-avatar"></span><span class="skeleton skeleton-line"></span><span class="skeleton skeleton-line"></span></div><span class="sr-only">{{label}}</span></div></template>
