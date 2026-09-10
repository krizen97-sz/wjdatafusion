<template>
  <section class="kettle-config" v-if="draft">
    <header><strong>{{ title }}</strong><el-tag size="small" type="info">{{ node.pluginId }}</el-tag><el-button v-if="!expanded" link icon="FullScreen" @click="emit('expand')">展开配置</el-button></header>
    <el-form label-position="top" :disabled="readonly">
      <el-form-item label="步骤名称"><el-input v-model="name" @input="write('name', $event)" /></el-form-item>
      <el-form-item label="说明"><el-input :model-value="read('description')" type="textarea" :rows="2" @update:model-value="write('description', $event)" /></el-form-item>
    </el-form>
    <el-tabs v-model="activeTab" :before-leave="beforeTabLeave" class="motion-tabs">
      <el-tab-pane label="参数" name="settings"><template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">参数</span></span></template>
        <el-form label-position="top" :disabled="readonly">
          <el-form-item v-for="field in schema.fields || []" :key="field.key" :label="field.label"><KettleValueInput :model-value="fieldValue(field)" :field="field" :context="context" @update:model-value="writeField(field, $event)" /></el-form-item>
          <el-form-item v-if="kind !== 'job'" label="并行副本数"><el-input :model-value="read('copies') || '1'" @update:model-value="write('copies', $event)" /></el-form-item>
        </el-form>
        <KettleConditionEditor v-if="schema.condition" :element="condition" root :readonly="readonly" :fields="context.fields" :revision="revision" @change="changed" />
        <KettleGenericConfig v-if="!hasDedicatedForm" :element="draft" :template-xml="genericTemplate" :external-revision="revision" :readonly="readonly" @change="changed" />
      </el-tab-pane>
      <el-tab-pane v-for="(section, index) in schema.tables || []" :key="section.path" :label="section.label" :name="`table-${index}`"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">{{ section.label }}</span></span></template>
        <el-button icon="Plus" class="mb16" :disabled="readonly" @click="addRow(section)">新增{{ section.columns.some(column => column.type === 'code') ? '脚本' : '一行' }}</el-button>
        <template v-if="section.columns.some(column => column.type === 'code')">
          <section v-for="(row, rowIndex) in rows(section.path)" :key="rowIndex" class="kettle-config__script">
            <el-form label-position="top" :disabled="readonly"><el-form-item v-for="column in section.columns" :key="column.key" :label="column.label"><KettleValueInput :model-value="rowText(row, column.key)" :field="column" :context="context" @update:model-value="writeRow(row, column.key, $event)" /></el-form-item></el-form>
            <el-button link type="danger" :disabled="readonly" @click="deleteRow(row)">删除脚本</el-button>
          </section>
        </template>
        <el-table v-else :data="rows(section.path)" size="small" empty-text="暂无字段，可新增或从上游获取">
          <el-table-column v-for="column in section.columns" :key="column.key" :label="column.label" :min-width="column.type === 'boolean' ? 105 : 150"><template #default="{ row }"><KettleValueInput :model-value="rowText(row, column.key)" :field="column" :context="context" @update:model-value="writeRow(row, column.key, $event)" /></template></el-table-column>
          <el-table-column label="操作" width="75" fixed="right"><template #default="{ row }"><el-button link type="danger" :disabled="readonly" @click="deleteRow(row)">删除</el-button></template></el-table-column>
        </el-table>
        <el-button v-if="!readonly && canFillFields(section)" link type="primary" class="mt16" @click="fillFields(section)">填入上游字段</el-button>
      </el-tab-pane>
      <el-tab-pane v-if="hasDedicatedForm" label="原生完整参数" name="native"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">原生完整参数</span></span></template><KettleGenericConfig :element="draft" :template-xml="genericTemplate" :external-revision="revision" :readonly="readonly" @change="changed" /></el-tab-pane>
      <el-tab-pane label="高级参数" name="advanced"><template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">高级参数</span></span></template>
        <el-text type="info">保留原插件全部XML配置。仅修改熟悉的字段；不会把未编辑的配置丢弃。</el-text>
        <el-input v-model="advancedXml" :disabled="readonly" type="textarea" :rows="18" aria-label="节点原生XML配置" class="mt16" @input="advancedDirty = true; changed()" />
      </el-tab-pane>
    </el-tabs>
    <el-alert v-if="node.pluginId === 'FTP_PUT' && read('only_new') === 'Y'" title="此原工具版本的“只发送新文件”仍可能覆盖同名文件，请使用独立目标目录" type="warning" :closable="false" class="mt16" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt16" />
    <footer><el-button type="primary" :disabled="readonly || !dirty" @click="apply">应用节点配置</el-button><el-button :disabled="!canRun || kind === 'job'" @click="emit('preview')">预览此节点</el-button><el-button link type="danger" :disabled="readonly" @click="emit('remove')">删除节点</el-button></footer>
  </section>
</template>
<script setup>
import { computed, markRaw, ref, watch } from 'vue'
import { appendRow, at, ensure, fromBase64, rowsAt, setText, textAt, xmlText } from './xmlModel'
import { schemaFor } from './nodeSchemas'
import KettleValueInput from './KettleValueInput.vue'
import KettleConditionEditor from './KettleConditionEditor.vue'
import KettleGenericConfig from './KettleGenericConfig.vue'
const props = defineProps({ node: Object, plugin: Object, title: String, kind: String, readonly: Boolean, canRun: Boolean, expanded: Boolean, commit: Function, context: { type: Object, default: () => ({ fields: [], nodes: [], connections: [], files: [], definitions: [] }) } })
const emit = defineEmits(['apply', 'dirty-change', 'preview', 'remove', 'expand'])
const draft = ref(), name = ref(''), dirty = ref(false), revision = ref(0), error = ref(''), activeTab = ref('settings'), advancedXml = ref(''), advancedDirty = ref(false)
const schema = computed(() => schemaFor(props.node?.pluginId))
const genericTemplate = computed(() => fromBase64(props.plugin?.configurationTemplateXmlBase64 || props.plugin?.defaultXmlBase64 || ''))
const hasDedicatedForm = computed(() => Boolean(schema.value.fields?.length || schema.value.tables?.length || schema.value.condition))
const condition = computed(() => draft.value ? markRaw(ensure(draft.value, 'compare/condition')) : null)
watch(() => props.node?.id, () => { draft.value = props.node ? markRaw(props.node.element.cloneNode(true)) : null; name.value = props.node?.name || ''; dirty.value = false; advancedDirty.value = false; activeTab.value = 'settings'; error.value = ''; revision.value++; advancedXml.value = draft.value ? xmlText(draft.value) : ''; emit('dirty-change', false) }, { immediate: true })
watch(activeTab, value => { if (value === 'advanced' && !advancedDirty.value) advancedXml.value = xmlText(draft.value) })
const read = path => { revision.value; return textAt(draft.value, path) }
const fieldValue = field => { revision.value; return field.type === 'presence' ? at(draft.value, field.key) ? 'Y' : 'N' : read(field.key) }
const rows = path => { revision.value; return rowsAt(draft.value, path).map(markRaw) }
const rowText = (row, path) => { revision.value; return textAt(row, path) }
function changed() { dirty.value = true; revision.value++; emit('dirty-change', true) }
function write(path, value) { setText(draft.value, path, value); changed() }
function writeField(field, value) {
  if (field.type === 'presence' && value === 'N') { at(draft.value, field.key)?.remove(); changed(); return }
  write(field.key, value)
  if (field.type === 'definition') {
    const definition = props.context.definitions?.find(item => '${INPUT_DIR}/' + (item.runtimeFilename || item.id + '.ktr') === value)
    const element = draft.value
    if (definition) element.setAttribute('data-rynew-definition-id', definition.id)
    else element.removeAttribute('data-rynew-definition-id')
  }
}
function writeRow(row, path, value) { setText(row, path, value); changed() }
function addRow(section) { appendRow(draft.value, section.path, section.defaults); changed() }
function deleteRow(row) { row.remove(); changed() }
function syncAdvanced() {
  if (!advancedDirty.value) return true
  try {
    if (/<!DOCTYPE|<!ENTITY/i.test(advancedXml.value)) throw new Error('不支持此XML声明')
    const parsed = new DOMParser().parseFromString(advancedXml.value, 'application/xml')
    if (parsed.getElementsByTagName('parsererror').length || parsed.documentElement.tagName !== props.node.element.tagName) throw new Error('节点XML结构无效')
    if (textAt(parsed.documentElement, 'type') !== props.node.pluginId) throw new Error('不能通过高级参数更换插件类型')
    parsed.documentElement.setAttribute('data-rynew-id', props.node.id)
    draft.value = markRaw(parsed.documentElement); name.value = textAt(draft.value, 'name'); advancedDirty.value = false; revision.value++; error.value = ''; return true
  } catch (cause) { error.value = cause.message; return false }
}
function beforeTabLeave(next, previous) { return previous !== 'advanced' || syncAdvanced() }
const canFillFields = section => props.context.fields?.length && section.path === 'fields/field' && !['Constant', 'RowGenerator'].includes(props.node?.pluginId)
function fillFields(section) {
  const names = new Set(rowsAt(draft.value, section.path).map(row => textAt(row, 'name')))
  for (const field of props.context.fields || []) if (!names.has(field)) appendRow(draft.value, section.path, { ...section.defaults, name: field })
  changed()
}
function apply() {
  try {
    if (!dirty.value) return true
    if (!syncAdvanced()) return false
    name.value = name.value.trim()
    if (!name.value.trim() || name.value.trim().length > 200) throw new Error('步骤名称不能为空且最多200字')
    if (name.value.trim() !== props.node.name && props.context.nodes?.includes(name.value.trim())) throw new Error('步骤名称已存在')
    if (['FilterRows', 'SwitchCase'].includes(props.node.pluginId)) {
      const targets = props.node.pluginId === 'FilterRows' ? [read('send_true_to'), read('send_false_to')] : [read('default_target_step'), ...rowsAt(draft.value, 'cases/case').map(row => textAt(row, 'target_step'))]
      for (const target of targets.filter(Boolean)) if (target === props.node.name || target === name.value || !props.context.nodes.includes(target)) throw new Error(`分支目标“${target}”不存在或指向自身`)
    }
    let element = draft.value
    if (advancedDirty.value) {
      if (/<!DOCTYPE|<!ENTITY/i.test(advancedXml.value)) throw new Error('不支持此XML声明')
      const parsed = new DOMParser().parseFromString(advancedXml.value, 'application/xml')
      if (parsed.getElementsByTagName('parsererror').length || parsed.documentElement.tagName !== props.node.element.tagName) throw new Error('节点XML结构无效')
      element = parsed.documentElement
      if (textAt(element, 'type') !== props.node.pluginId) throw new Error('不能通过高级参数更换插件类型')
    }
    setText(element, 'name', name.value); element.setAttribute('data-rynew-id', props.node.id)
    if (props.commit && props.commit({ element, name: name.value }) === false) { error.value = '配置未应用，请检查任务中的错误提示'; return false }
    if (!props.commit) emit('apply', { element, name: name.value })
    draft.value = markRaw(element.cloneNode(true)); dirty.value = false; advancedDirty.value = false; emit('dirty-change', false)
    return true
  } catch (cause) { error.value = cause.message; return false }
}
defineExpose({ apply })
</script>
<style scoped>
.kettle-config { min-width: 0; }
.kettle-config header { display: flex; justify-content: space-between; gap: var(--el-font-size-small); align-items: center; margin-bottom: var(--el-component-size-small); }
.kettle-config footer { display: flex; flex-wrap: wrap; gap: var(--el-font-size-small); margin-top: var(--el-component-size-small); }
.kettle-config__script + .kettle-config__script { border-top: 1px solid var(--el-border-color); padding-top: var(--el-component-size-small); margin-top: var(--el-component-size-small); }
</style>
