<template>
  <section class="kettle-condition">
    <el-space wrap>
      <el-select :disabled="readonly" v-if="!root" :model-value="read('operator')" aria-label="条件组合" @update:model-value="write('operator', $event)"><el-option label="并且" value="AND" /><el-option label="或者" value="OR" /></el-select>
      <el-checkbox :disabled="readonly" :model-value="read('negated') === 'Y'" @update:model-value="write('negated', $event ? 'Y' : 'N')">取反</el-checkbox>
      <el-button :disabled="readonly" link type="primary" @click="addChild">增加子条件</el-button>
      <el-button :disabled="readonly" v-if="!root" link type="danger" @click="emit('remove')">删除条件</el-button>
    </el-space>
    <template v-if="children.length">
      <KettleConditionEditor v-for="(child, index) in children" :key="index" :element="child" :readonly="readonly" :fields="fields" :revision="revision" @change="emit('change')" @remove="removeChild(child)" />
    </template>
    <el-form v-else label-position="top">
      <el-form-item label="左侧字段"><el-select :disabled="readonly" :model-value="read('leftvalue')" filterable allow-create default-first-option @update:model-value="write('leftvalue', $event)"><el-option v-for="name in fields" :key="name" :label="name" :value="name" /></el-select></el-form-item>
      <el-form-item label="比较条件"><el-select :disabled="readonly" :model-value="read('function')" @update:model-value="write('function', $event)"><el-option v-for="op in operators" :key="op" :label="op" :value="op" /></el-select></el-form-item>
      <el-form-item label="右侧字段（可选）"><el-select :disabled="readonly" :model-value="read('rightvalue')" clearable filterable allow-create default-first-option @update:model-value="write('rightvalue', $event)"><el-option v-for="name in fields" :key="name" :label="name" :value="name" /></el-select></el-form-item>
      <el-form-item label="比较常量类型"><el-select :disabled="readonly" :model-value="read('value/type') || 'String'" @update:model-value="write('value/type', $event)"><el-option v-for="type in valueTypes" :key="type" :label="type" :value="type" /></el-select></el-form-item>
      <el-form-item label="比较常量"><el-input :disabled="readonly" :model-value="read('value/text')" @update:model-value="write('value/text', $event)" /></el-form-item>
    </el-form>
  </section>
</template>
<script setup>
import { computed } from 'vue'
import { at, direct, ensure, setText, textAt } from './xmlModel'
import { valueTypes } from './nodeSchemas'
defineOptions({ name: 'KettleConditionEditor' })
const props = defineProps({ element: Object, root: Boolean, readonly: Boolean, fields: { type: Array, default: () => [] }, revision: Number })
const emit = defineEmits(['change', 'remove'])
const operators = ['=', '<>', '<', '<=', '>', '>=', 'IS NULL', 'IS NOT NULL', 'IN LIST', 'CONTAINS', 'STARTS WITH', 'ENDS WITH', 'REGEXP', 'TRUE']
const read = path => { props.revision; return textAt(props.element, path) }
const children = computed(() => { props.revision; return direct(at(props.element, 'conditions'), 'condition') })
function write(path, value) { setText(props.element, path, value); emit('change') }
function addChild() {
  const list = ensure(props.element, 'conditions')
  if (!children.value.length && textAt(props.element, 'leftvalue')) {
    const previous = props.element.cloneNode(true); direct(previous, 'conditions').forEach(item => item.remove()); setText(previous, 'operator', 'AND'); list.appendChild(previous)
    for (const name of ['leftvalue', 'function', 'rightvalue', 'value']) direct(props.element, name).forEach(item => item.remove())
    setText(props.element, 'negated', 'N')
  }
  const child = props.element.ownerDocument.createElement('condition'); setText(child, 'operator', 'AND'); setText(child, 'negated', 'N'); setText(child, 'function', '='); list.appendChild(child); emit('change')
}
function removeChild(child) { child.remove(); emit('change') }
</script>
<style scoped>
.kettle-condition { padding: var(--el-font-size-small); border-left: 1px solid var(--el-border-color); }
.kettle-condition .kettle-condition { margin-top: var(--el-font-size-small); }
.kettle-condition .el-form { margin-top: var(--el-font-size-small); }
</style>
