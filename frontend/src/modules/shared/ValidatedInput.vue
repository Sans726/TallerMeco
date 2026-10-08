<script setup lang="ts">
import {computed,ref,useId} from 'vue'
import {acceptsInput,validate,limits,birthDateToDisplay,birthDateFromDisplay,acceptsBirthDateInput,type InputKind} from './validation'
const props=withDefaults(defineProps<{label:string;name:string;kind?:InputKind;required?:boolean;max?:number;error?:string;placeholder?:string;birth?:string;autocomplete?:string}>(),{kind:'text',required:false})
const value=defineModel<string>({default:''})
const id=useId(),touched=ref(false),blocked=ref('')
const maximum=computed(()=>props.max??limits[props.kind])
const message=computed(()=>blocked.value||props.error||(touched.value?validate(props.kind,value.value,props.required,maximum.value,props.birth):''))
const type=computed(()=>props.kind==='email'?'email':props.kind==='phone'?'tel':'text')
const displayed=computed(()=>props.kind==='date'?birthDateToDisplay(value.value):value.value)
function accepts(raw:string){return props.kind==='date'?acceptsBirthDateInput(raw):acceptsInput(props.kind,raw,maximum.value)}
function proposed(input:HTMLInputElement,text:string){const start=input.selectionStart??value.value.length,end=input.selectionEnd??start;return input.value.slice(0,start)+text+input.value.slice(end)}
function allowed(input:HTMLInputElement,text:string){
 if(accepts(proposed(input,text))){blocked.value='';return true}
 touched.value=true;blocked.value=props.kind==='date'?'Usa día/mes/año (DD/MM/AAAA); el contenido incompatible no se agregó.':props.kind==='postal'?'Solo se aceptan 5 dígitos. El contenido incompatible no se agregó.':`El contenido tiene caracteres incompatibles o supera ${maximum.value} caracteres; no se agregó.`;return false
}
function before(e:InputEvent){if(e.data!==null&&!allowed(e.target as HTMLInputElement,e.data))e.preventDefault()}
function paste(e:ClipboardEvent){const raw=e.clipboardData?.getData('text')??'';if(!allowed(e.target as HTMLInputElement,raw))e.preventDefault()}
function input(e:Event){const el=e.target as HTMLInputElement;if(!accepts(el.value)){el.value=displayed.value;blocked.value='Contenido incompatible; corrige el valor.';return}value.value=props.kind==='date'?birthDateFromDisplay(el.value):el.value;if(props.kind==='date')el.value=birthDateToDisplay(value.value);touched.value=true;blocked.value=''}
</script>
<template>
 <label :for="id">{{label}} <span v-if="required" class="required-mark">*</span>
  <input :id="id" :name="name" :value="displayed" :type="type" :required="required" :maxlength="maximum" :minlength="kind==='postal'?5:kind==='date'?10:undefined" :pattern="kind==='postal'?'[0-9]{5}':kind==='date'?'[0-9]{2}/[0-9]{2}/[0-9]{4}':undefined" :inputmode="kind==='postal'||kind==='phone'||kind==='date'||kind==='year'||kind==='odometer'?'numeric':kind==='email'?'email':undefined" :placeholder="placeholder||(kind==='date'?'DD/MM/AAAA':undefined)" :autocomplete="autocomplete" :aria-invalid="!!message" :aria-describedby="`${id}-error`" @beforeinput="before" @paste="paste" @input="input" @blur="touched=true">
  <small v-if="kind==='date'" class="field-hint">Día/mes/año (DD/MM/AAAA)</small>
  <small v-if="message" :id="`${id}-error`" class="field-error" role="alert">{{message}}</small>
 </label>
</template>
