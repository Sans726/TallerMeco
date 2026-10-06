<script setup lang="ts">
import { computed, onUnmounted, ref, useId, watch } from 'vue'
import { api } from '../../api'
import ValidatedInput from '../../modules/shared/ValidatedInput.vue'
const props=defineProps<{errors?:Record<string,string>}>()

type Address = { postalCode: string; street: string; neighborhood: string; municipality: string; state: string }
type Settlement = Pick<Address, 'neighborhood' | 'municipality' | 'state'>
type Lookup = { postalCode: string; settlements: Settlement[] }
const address = defineModel<Address>({ required: true })
const id = useId()
const settlements = ref<Settlement[]>([])
const loading = ref(false)
const message = ref('Escribe un código postal mexicano de cinco dígitos para completar la ubicación.')
const manual = ref(false)
let generation = 0
let timer: ReturnType<typeof setTimeout> | undefined

const selected = computed(() => {
  const index = settlements.value.findIndex(item => item.neighborhood === address.value.neighborhood
    && item.municipality === address.value.municipality && item.state === address.value.state)
  return index < 0 ? '' : String(index)
})

watch(() => address.value.postalCode, (raw, previous) => {
  clearTimeout(timer)
  const request = ++generation
  settlements.value = []
  loading.value = false
  manual.value = false
  const code = String(raw || '')
  message.value = 'Escribe un código postal mexicano de cinco dígitos para completar la ubicación.'
  if (!/^[0-9]{5}$/.test(code)) return
  const before = { ...address.value }
  const changedCode = previous !== undefined
  loading.value = true
  message.value = 'Buscando estado, municipio y colonias…'
  timer = setTimeout(async () => {
    try {
      const result = await api<Lookup>(`/addresses/postal-codes/${code}`)
      if (request !== generation) return
      settlements.value = result.settlements
      // Keep saved addresses on opening an edit form, and respect typing during a lookup.
      for (const field of ['state', 'municipality', 'neighborhood'] as const) {
        const options = [...new Set(result.settlements.map(item => item[field]))]
        if (address.value[field] !== before[field]) continue
        if (!changedCode && address.value[field]) continue
        if (options.length === 1) address.value[field] = options[0]!
        else if (changedCode && !options.includes(address.value[field])) address.value[field] = ''
      }
      manual.value = Boolean(address.value.neighborhood && selected.value === '')
      message.value = result.settlements.length === 1
        ? 'Ubicación completada. Agrega la calle y el número; puedes corregir cualquier dato.'
        : `${result.settlements.length} colonias o asentamientos disponibles. Elige el que corresponde a la dirección.`
    } catch (error) {
      if (request !== generation) return
      message.value = (error instanceof Error ? error.message : 'No se pudo consultar el código postal.')
        + ' La captura manual sigue disponible.'
    } finally {
      if (request === generation) loading.value = false
    }
  }, 300)
}, { immediate: true })

function choose(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value === '') return
  const settlement = settlements.value[Number(value)]
  if (settlement) Object.assign(address.value, settlement)
}

onUnmounted(() => { generation++; clearTimeout(timer) })
</script>

<template>
  <div class="form-grid">
    <ValidatedInput v-model="address.postalCode" name="postalCode" label="Código postal" kind="postal" required placeholder="Ej. 01000" autocomplete="postal-code" :error="props.errors?.postalCode" />
    <div class="postal-help" :id="`${id}-postal-help`" role="status" aria-live="polite" :aria-busy="loading">
      <span class="postal-badge">{{ loading ? 'Consultando…' : 'Catálogo postal de México' }}</span>
      <p>{{ message }}</p>
    </div>
    <ValidatedInput v-model="address.state" name="state" label="Estado" kind="name" required :max="120" autocomplete="address-level1" :error="props.errors?.state" />
    <ValidatedInput v-model="address.municipality" name="municipality" label="Municipio" kind="name" required :max="120" autocomplete="address-level2" :error="props.errors?.municipality" />
    <label v-if="settlements.length && !manual" class="span-two">Colonia o asentamiento
      <select name="neighborhood" required :value="selected" :aria-invalid="!!props.errors?.neighborhood" @change="choose">
        <option value="" disabled>Selecciona una colonia</option>
        <option v-for="(item, index) in settlements" :key="index" :value="String(index)">
          {{ item.neighborhood }} · {{ item.municipality }}
        </option>
      </select><small v-if="props.errors?.neighborhood" class="field-error" role="alert">{{props.errors.neighborhood}}</small>
    </label>
    <ValidatedInput v-else v-model="address.neighborhood" class="span-two" name="neighborhood" label="Colonia" kind="address" required :max="120" :error="props.errors?.neighborhood" />
    <div v-if="settlements.length" class="span-two">
      <button type="button" class="text-link" @click="manual = !manual">
        {{ manual ? 'Elegir del catálogo' : 'Capturar otra colonia manualmente' }}
      </button>
    </div>
    <ValidatedInput v-model="address.street" class="span-two" name="street" label="Calle y número" kind="address" required :max="180" autocomplete="street-address" placeholder="Calle, número exterior e interior" :error="props.errors?.street" />
  </div>
</template>

<style scoped>
.postal-help{align-self:center;padding:12px 14px;background:#f0f5ee;border:1px solid #dce7d8;border-radius:9px}
.postal-badge{font-size:10px;font-weight:700;color:#3b6651;letter-spacing:.3px}
.postal-help p{font-size:12px;color:#52675b;margin-top:4px;line-height:1.5}
</style>
