<template>
  <div
    class="ipv4-input"
    :class="{ 'is-disabled': disabled, 'has-invalid': hasInvalidOctet }"
    role="group"
    :aria-label="label"
    @paste="handlePaste"
  >
    <template v-for="(_, index) in octets" :key="index">
      <input
        :ref="(element) => setInputRef(element, index)"
        :value="octets[index]"
        :disabled="disabled"
        type="text"
        inputmode="numeric"
        pattern="[0-9]*"
        maxlength="3"
        autocomplete="off"
        :aria-label="`${label}第${index + 1}段`"
        :aria-invalid="octets[index] !== '' && !isIpv4OctetValid(octets[index])"
        placeholder="0"
        @input="handleInput(index, $event)"
        @keydown="handleKeydown(index, $event)"
        @blur="emit('blur', currentValue())"
      />
      <span v-if="index < 3" aria-hidden="true">.</span>
    </template>
  </div>
</template>

<script setup name="Ipv4Input">
import { computed, nextTick, ref, watch } from 'vue'
import { isIpv4OctetValid, normalizeIpv4Octet, splitIpv4Value } from '../ipamRules.js'

const props = defineProps({
  modelValue: { type: String, default: '' },
  label: { type: String, default: 'IPv4地址' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur'])
const octets = ref(splitIpv4Value(props.modelValue))
const inputRefs = ref([])
const hasInvalidOctet = computed(() => octets.value.some((part) => part !== '' && !isIpv4OctetValid(part)))

watch(() => props.modelValue, (value) => {
  const next = splitIpv4Value(value)
  if (next.join('.') !== octets.value.join('.')) {
    octets.value = next
  }
})

function setInputRef(element, index) {
  if (element) inputRefs.value[index] = element
}

function currentValue() {
  return octets.value.every((part) => part === '') ? '' : octets.value.join('.')
}

function updateValue() {
  const value = currentValue()
  emit('update:modelValue', value)
  emit('change', value)
}

function focusOctet(index, select = false) {
  if (index < 0 || index > 3) return
  nextTick(() => {
    inputRefs.value[index]?.focus()
    if (select) inputRefs.value[index]?.select()
  })
}

function handleInput(index, event) {
  const normalized = normalizeIpv4Octet(event.target.value)
  octets.value[index] = normalized
  event.target.value = normalized
  updateValue()
  if (normalized.length === 3 && isIpv4OctetValid(normalized) && index < 3) {
    focusOctet(index + 1, true)
  }
}

function handleKeydown(index, event) {
  const input = event.currentTarget
  if ((event.key === '.' || event.key === 'Decimal') && index < 3) {
    event.preventDefault()
    focusOctet(index + 1, true)
    return
  }
  if (event.key === 'Backspace' && !octets.value[index] && index > 0) {
    event.preventDefault()
    focusOctet(index - 1)
    return
  }
  if (event.key === 'ArrowLeft' && input.selectionStart === 0 && index > 0) {
    event.preventDefault()
    focusOctet(index - 1)
    return
  }
  if (event.key === 'ArrowRight' && input.selectionStart === input.value.length && index < 3) {
    event.preventDefault()
    focusOctet(index + 1)
  }
}

function handlePaste(event) {
  const text = event.clipboardData?.getData('text')?.trim()
  const parts = String(text || '').split('.')
  if (parts.length !== 4 || parts.some((part) => !/^\d{1,3}$/.test(part) || Number(part) > 255)) {
    return
  }
  event.preventDefault()
  octets.value = parts.map((part) => String(Number(part)))
  updateValue()
  focusOctet(3)
}
</script>

<style scoped>
.ipv4-input {
  display: inline-grid;
  grid-template-columns: repeat(3, 58px 12px) 58px;
  align-items: center;
  width: max-content;
  max-width: 100%;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.ipv4-input:focus-within {
  border-color: #409eff;
  box-shadow: 0 0 0 1px #409eff inset;
}

.ipv4-input.has-invalid {
  border-color: #f56c6c;
}

.ipv4-input.is-disabled {
  background: #f5f7fa;
  color: #a8abb2;
}

.ipv4-input input {
  width: 58px;
  min-width: 0;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #303133;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  line-height: 30px;
  text-align: center;
}

.ipv4-input input::placeholder {
  color: #c0c4cc;
}

.ipv4-input input:disabled {
  color: #a8abb2;
  cursor: not-allowed;
}

.ipv4-input span {
  color: #606266;
  font-weight: 650;
  text-align: center;
}

@media (max-width: 480px) {
  .ipv4-input {
    grid-template-columns: repeat(3, 48px 10px) 48px;
  }

  .ipv4-input input {
    width: 48px;
  }
}
</style>
