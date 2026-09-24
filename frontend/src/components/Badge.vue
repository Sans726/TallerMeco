<script setup lang="ts">import {statuses} from '../api';defineProps<{status:string}>()</script>
<template><span class="badge" :class="status.toLowerCase()"><i></i>{{statuses[status]||status}}</span></template>
