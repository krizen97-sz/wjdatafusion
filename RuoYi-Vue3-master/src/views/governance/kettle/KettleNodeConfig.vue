<template>
  <section v-if="draft" class="kettle-config" :class="{ 'is-bounded': bounded }">
    <header class="kettle-config__header">
      <strong :title="`${title} · ${node.pluginId}`">{{ title }}</strong>
      <el-space><el-button v-if="!expanded" link icon="FullScreen" @click="emit('expand')">展开配置</el-button><el-button v-if="closable" link icon="Close" aria-label="收起节点配置" @click="emit('close')" /></el-space>
    </header>
    <el-scrollbar class="kettle-config__body">
      <el-form label-position="top" :disabled="readonly" class="kettle-config__identity" @submit.prevent>
        <el-form-item label="节点名称" :for="controlId('name')" required>
          <el-input :id="controlId('name')" v-model="name" maxlength="200" @input="write('name', $event)" />
        </el-form-item>
        <el-collapse v-model="expandedIdentity" class="kettle-config__description">
          <el-collapse-item name="description">
            <template #title><span>节点说明</span><el-text type="info" size="small">{{ read('description') ? '已填写' : '选填' }}</el-text></template>
            <el-form-item label="说明内容" :for="controlId('description')">
              <el-input :id="controlId('description')" :model-value="read('description')" type="textarea" :autosize="{ minRows: 2, maxRows: 6 }" @update:model-value="write('description', $event)" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <el-tabs v-model="activeTab" :before-leave="beforeTabLeave" class="motion-tabs kettle-config__tabs">
        <el-tab-pane label="参数" name="settings"><template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">参数</span></span></template>
          <el-form label-position="top" :disabled="readonly" @submit.prevent>
            <el-form-item v-for="field in fieldGroups.primary" :key="field.key" :label="field.label" :for="controlId(field.key)">
              <KettleValueInput :input-id="controlId(field.key)" :model-value="fieldValue(field)" :field="field" :context="inputContext" @update:model-value="writeField(field, $event)" />
            </el-form-item>
          </el-form>
          <KettleConditionEditor v-if="schema.condition" :element="condition" root :readonly="readonly" :fields="context.fields" :revision="revision" @change="changed" />
          <KettleGenericConfig v-if="!hasDedicatedForm" :element="draft" :template-xml="genericTemplate" :external-revision="revision" :readonly="readonly" :can-expand="!expanded" @change="changed" @expand="emit('expand')" />
          <el-collapse v-if="fieldGroups.advanced.length || kind !== 'job'" v-model="expandedSettings">
            <el-collapse-item name="advanced" title="高级设置">
              <el-form label-position="top" :disabled="readonly" class="kettle-config__advanced-form" @submit.prevent>
                <el-form-item v-for="field in fieldGroups.advanced" :key="field.key" :label="field.label" :for="controlId(field.key)">
                  <KettleValueInput :input-id="controlId(field.key)" :model-value="fieldValue(field)" :field="field" :context="inputContext" @update:model-value="writeField(field, $event)" />
                </el-form-item>
                <el-form-item v-if="kind !== 'job'" label="并行副本数" :for="controlId('copies')"><el-input :id="controlId('copies')" :model-value="read('copies') || '1'" @update:model-value="write('copies', $event)" /></el-form-item>
              </el-form>
            </el-collapse-item>
          </el-collapse>
        </el-tab-pane>
        <el-tab-pane v-for="(section, index) in schema.tables || []" :key="section.path" :label="section.label" :name="`table-${index}`"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">{{ section.label }}</span></span></template>
          <div class="kettle-config__table-toolbar">
            <el-text type="info" size="small">{{ rows(section.path).length }} {{ section.columns.some(column => column.type === 'code') ? '个脚本' : '项配置' }}</el-text>
            <div class="kettle-config__table-actions">
              <el-button v-if="!readonly && canFillFields(section)" link type="primary" @click="fillFields(section)">填入上游字段</el-button>
              <el-button icon="Plus" :disabled="readonly" @click="addRow(section)">{{ section.columns.some(column => column.type === 'code') ? '新增脚本' : '新增一行' }}</el-button>
            </div>
          </div>
          <div v-if="!expanded && section.columns.length > 2" class="kettle-config__wide-hint">
            <el-text type="info" size="small">{{ section.columns.some(column => column.type === 'code') ? '脚本可在大窗口中编辑' : '横向滚动查看其余列' }}</el-text>
            <el-button link type="primary" icon="FullScreen" @click="emit('expand')">展开编辑</el-button>
          </div>
          <template v-if="section.columns.some(column => column.type === 'code')">
            <section v-for="(row, rowIndex) in rows(section.path)" :key="rowIndex" class="kettle-config__script">
              <el-form label-position="top" :disabled="readonly" @submit.prevent>
                <el-form-item v-for="column in section.columns" :key="column.key" :label="column.label" :for="controlId(`${section.path}-${rowIndex}-${column.key}`)">
                  <KettleValueInput :input-id="controlId(`${section.path}-${rowIndex}-${column.key}`)" :model-value="rowText(row, column.key)" :field="column" :context="inputContext" @update:model-value="writeRow(row, column.key, $event)" />
                </el-form-item>
              </el-form>
              <el-button link type="danger" :disabled="readonly" :aria-label="`删除第${rowIndex + 1}个脚本`" @click="deleteRow(row)">删除脚本</el-button>
            </section>
          </template>
          <el-table v-else :data="rows(section.path)" size="small" empty-text="暂无配置，点击“新增一行”开始">
            <el-table-column v-for="column in section.columns" :key="column.key" :label="column.label" :min-width="column.type === 'boolean' ? 105 : 150">
              <template #default="{ row, $index }"><KettleValueInput :model-value="rowText(row, column.key)" :field="{ ...column, label: `${column.label}，第${$index + 1}行` }" :context="inputContext" @update:model-value="writeRow(row, column.key, $event)" /></template>
            </el-table-column>
            <el-table-column v-if="!readonly" label="操作" width="75" fixed="right"><template #default="{ row, $index }"><el-button link type="danger" :aria-label="`删除${section.label}第${$index + 1}行`" @click="deleteRow(row)">删除</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane v-if="hasDedicatedForm" label="全部参数" name="native"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">全部参数</span></span></template>
          <el-text type="info" size="small" class="kettle-config__help">包含此工具的原生配置项；日常配置优先使用参数与字段页。</el-text>
          <KettleGenericConfig :element="draft" :template-xml="genericTemplate" :external-revision="revision" :readonly="readonly" :can-expand="!expanded" @change="changed" @expand="emit('expand')" />
        </el-tab-pane>
        <el-tab-pane label="高级 XML" name="advanced"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">高级 XML</span></span></template>
          <el-text type="info" size="small" class="kettle-config__help">仅在需要时编辑原生 XML；未修改的配置会保留。</el-text>
          <el-input v-model="advancedXml" :disabled="readonly" type="textarea" :rows="18" aria-label="节点原生XML配置" @input="advancedDirty = true; changed()" />
        </el-tab-pane>
      </el-tabs>
      <el-alert v-if="node.pluginId === 'FTP_PUT' && read('only_new') === 'Y'" title="此原工具版本的“只发送新文件”仍可能覆盖同名文件，请使用独立目标目录" type="warning" :closable="false" class="mt16" />
    </el-scrollbar>
    <footer class="kettle-config__footer">
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="kettle-config__error" />
      <div class="kettle-config__status" role="status" aria-live="polite">
        <el-text :type="dirty ? 'warning' : 'info'" size="small">{{ readonly ? '只读配置' : dirty ? '有改动待应用' : applied ? '已应用到画布' : '配置与画布一致' }}</el-text>
        <el-button link type="danger" :disabled="readonly" @click="emit('remove')">删除节点</el-button>
      </div>
      <div class="kettle-config__actions">
        <el-tooltip v-if="kind !== 'job'" :content="previewHint || '预览当前节点的实际数据'" placement="top"><span><el-button :disabled="!canRun" @click="emit('preview')">预览此节点</el-button></span></el-tooltip>
        <el-button type="primary" :disabled="readonly || !dirty" @click="apply">应用配置</el-button>
      </div>
    </footer>
  </section>
</template>
<script setup>
import { computed, markRaw, ref, useId, watch } from 'vue'
import { appendRow, at, ensure, fromBase64, rowsAt, setText, textAt, xmlText } from './xmlModel'
import { hasDedicatedForm as dedicatedFormAvailable, nodeFieldGroups, schemaFor } from './nodeSchemas'
import KettleValueInput from './KettleValueInput.vue'
import KettleConditionEditor from './KettleConditionEditor.vue'
import KettleGenericConfig from './KettleGenericConfig.vue'
const props = defineProps({ node: Object, plugin: Object, title: String, kind: String, readonly: Boolean, canRun: Boolean, expanded: Boolean, bounded: Boolean, closable: Boolean, previewHint: { type: String, default: '' }, commit: Function, context: { type: Object, default: () => ({ fields: [], nodes: [], connections: [], files: [], definitions: [] }) } })
const emit = defineEmits(['apply', 'dirty-change', 'preview', 'remove', 'expand', 'close'])
const draft = ref(), name = ref(''), dirty = ref(false), revision = ref(0), error = ref(''), activeTab = ref('settings'), advancedXml = ref(''), advancedDirty = ref(false)
const schema = computed(() => schemaFor(props.node?.pluginId))
const fieldGroups = computed(() => nodeFieldGroups(props.node?.pluginId))
const expandedSettings = ref([]), expandedIdentity = ref([]), applied = ref(false)
const controlPrefix = useId()
const controlId = key => `${controlPrefix}-${key}`
const inputContext = computed(() => ({ ...props.context, readonly: props.readonly || props.context.readonly }))
const genericTemplate = computed(() => fromBase64(props.plugin?.configurationTemplateXmlBase64 || props.plugin?.defaultXmlBase64 || ''))
const hasDedicatedForm = computed(() => dedicatedFormAvailable(props.node?.pluginId))
const condition = computed(() => draft.value ? markRaw(ensure(draft.value, 'compare/condition')) : null)
watch(() => props.node?.id, () => { draft.value = props.node ? markRaw(props.node.element.cloneNode(true)) : null; name.value = props.node?.name || ''; dirty.value = false; advancedDirty.value = false; activeTab.value = schema.value.initialTab || 'settings'; expandedSettings.value = []; expandedIdentity.value = []; applied.value = false; error.value = ''; revision.value++; advancedXml.value = draft.value ? xmlText(draft.value) : ''; emit('dirty-change', false) }, { immediate: true })
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
    draft.value = markRaw(element.cloneNode(true)); error.value = ''; applied.value = true; dirty.value = false; advancedDirty.value = false; emit('dirty-change', false)
    return true
  } catch (cause) { error.value = cause.message; return false }
}
defineExpose({ apply })
</script>
<style scoped>
.kettle-config { min-width: 0; }
.kettle-config.is-bounded { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.kettle-config__body { height: auto; }
.is-bounded .kettle-config__body { flex: 1; height: 0; min-height: 0; }
.is-bounded .kettle-config__body :deep(.el-scrollbar__view) { padding-right: var(--el-font-size-small); }
.kettle-config__header, .kettle-config__footer { flex-shrink: 0; }
.kettle-config__header { display: flex; justify-content: space-between; gap: var(--el-font-size-small); align-items: flex-start; margin-bottom: var(--el-font-size-base); }
.kettle-config__header strong { min-width: 0; overflow-wrap: break-word; word-break: break-word; color: var(--el-text-color-primary); line-height: var(--el-component-size-small); }
.kettle-config__header .el-button { flex: none; }
.kettle-config__identity :deep(.el-form-item) { margin-bottom: var(--el-font-size-small); }
.kettle-config__description { border-top: 0; margin-bottom: var(--el-font-size-small); }
.kettle-config__description :deep(.el-collapse-item__header) { height: var(--el-component-size); gap: var(--el-font-size-small); }
.kettle-config__description :deep(.el-collapse-item__content) { padding-bottom: var(--el-font-size-small); }
.kettle-config__tabs { min-width: 0; }
.kettle-config__advanced-form { padding-top: var(--el-font-size-small); }
.kettle-config__table-toolbar, .kettle-config__table-actions, .kettle-config__wide-hint { display: flex; align-items: center; flex-wrap: wrap; gap: var(--el-font-size-small); }
.kettle-config__table-toolbar, .kettle-config__wide-hint { justify-content: space-between; margin-bottom: var(--el-font-size-small); }
.kettle-config__table-toolbar .el-button + .el-button { margin-left: 0; }
.kettle-config__help { display: block; margin-bottom: var(--el-font-size-base); }
.kettle-config__footer { margin-top: var(--el-component-size-small); padding-top: var(--el-font-size-base); border-top: 1px solid var(--el-border-color-lighter); }
.kettle-config__error { margin-bottom: var(--el-font-size-small); }
.kettle-config__status { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--el-font-size-small); margin-bottom: var(--el-font-size-small); }
.kettle-config__actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: var(--el-font-size-small); }
.kettle-config__actions .el-button + .el-button { margin-left: 0; }
.kettle-config__script + .kettle-config__script { border-top: 1px solid var(--el-border-color); padding-top: var(--el-component-size-small); margin-top: var(--el-component-size-small); }
</style>
