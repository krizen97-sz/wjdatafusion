<template>
  <el-switch :aria-label="field.label" :disabled="context.readonly" v-if="['boolean', 'presence'].includes(field.type)" :model-value="modelValue || 'N'" active-value="Y" inactive-value="N" @update:model-value="update" />
  <el-switch :aria-label="field.label" :disabled="context.readonly" v-else-if="field.type === 'truefalse'" :model-value="modelValue || 'false'" active-value="true" inactive-value="false" @update:model-value="update" />
  <template v-else-if="field.type === 'password' && modelValue?.startsWith('__RYNEW_SECRET_')">
    <el-space><el-tag type="info">已配置</el-tag><el-button :disabled="context.readonly" link type="primary" @click="update('')">重新设置</el-button></el-space>
  </template>
  <el-input :aria-label="field.label" :disabled="context.readonly" v-else-if="field.type === 'password'" :model-value="modelValue" type="password" show-password autocomplete="new-password" @update:model-value="update" />
  <el-input :disabled="context.readonly" v-else-if="['textarea', 'code'].includes(field.type)" :model-value="modelValue" type="textarea" :rows="field.type === 'code' ? 12 : 4" :class="{ 'kettle-code': field.type === 'code' }" :aria-label="field.label" @update:model-value="update" />
  <el-select :disabled="context.readonly" v-else-if="options.length || ['field', 'node', 'connection', 'inputFile', 'definition', 'select'].includes(field.type)" :model-value="modelValue" filterable allow-create default-first-option clearable :aria-label="field.label" @update:model-value="update">
    <el-option v-for="option in options" :key="option.value" :value="option.value" :label="option.label" />
  </el-select>
  <el-input :disabled="context.readonly" v-else :model-value="modelValue" :aria-label="field.label" @update:model-value="update" />
</template>
<script setup>
import { computed } from 'vue'
const props = defineProps({ modelValue: { type: String, default: '' }, field: { type: Object, required: true }, context: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue'])
const update = value => emit('update:modelValue', String(value ?? ''))
const options = computed(() => {
  const type = props.field.type, context = props.context
  if (type === 'inputFile') return (context.files || []).map(file => ({ value: '${INPUT_DIR}/' + file.name, label: file.name }))
  if (type === 'definition') return (context.definitions || []).filter(item => item.kind === 'transformation').map(item => ({ value: '${INPUT_DIR}/' + (item.runtimeFilename || item.id + '.ktr'), label: `${item.name} · ${item.nodeCount}节点` }))
  const items = type === 'node' ? context.nodes : type === 'connection' ? context.connections : type === 'field' ? context.fields : props.field.options
  return (items || []).map(item => typeof item === 'string' ? { value: item, label: item } : { value: item.value ?? item.name, label: item.label ?? item.name })
})
</script>
<style scoped>
.kettle-code :deep(textarea) { font-family: var(--el-font-family); line-height: 1.7; }
</style>
