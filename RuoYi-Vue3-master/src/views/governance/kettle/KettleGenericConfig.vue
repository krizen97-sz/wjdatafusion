<template>
  <section class="kettle-generic">
    <el-form v-if="model.fields.length" label-position="top" :disabled="readonly" @submit.prevent>
      <el-form-item v-for="field in model.fields" :key="field.key" :for="controlId(field.key)">
        <template #label><span class="kettle-generic__label">{{ field.label }}<el-text v-if="field.label !== field.name" type="info" size="small">{{ field.key }}</el-text></span></template>
        <KettleValueInput :input-id="controlId(field.key)" :model-value="fieldValue(field)" :field="field" :context="inputContext" @update:model-value="mutate(() => writeField(field, $event, { readonly }))" />
      </el-form-item>
    </el-form>
    <el-collapse v-if="model.groups.length" v-model="expanded">
      <el-collapse-item v-for="group in model.groups" :key="group.key" :name="group.key">
        <template #title><span class="kettle-generic__group-title"><span>{{ group.label }}</span><el-text v-if="group.label !== group.name || group.kind === 'rows'" type="info" size="small">{{ group.label !== group.name ? group.name : '' }}{{ group.kind === 'rows' ? ` · ${group.rows.length} 项` : '' }}</el-text></span></template>
        <template v-if="group.kind === 'rows'">
          <div class="kettle-generic__toolbar">
            <el-button v-if="!readonly" icon="Plus" :disabled="!group.template" @click="mutate(() => addRow(group, { readonly }))">{{ group.nativeTemplate ? '新增一行' : '按已有行新增' }}</el-button>
            <el-button v-if="canExpand && group.columns.length > 1" link type="primary" icon="FullScreen" @click="emit('expand')">展开编辑</el-button>
          </div>
          <el-text type="info" size="small" class="kettle-generic__help">展开行首箭头可{{ readonly ? '查看' : '编辑' }}完整配置{{ canExpand && group.columns.length > 1 ? '，其余列可横向滚动查看' : '' }}。</el-text>
          <el-table :data="group.rows" :row-key="nodeKey" size="small" empty-text="暂无配置项，可使用原生模板新增">
            <el-table-column type="expand"><template #default="{ row }"><KettleGenericConfig :element="row" :template-xml="group.template" :readonly="readonly" :can-expand="canExpand" @change="changed" @expand="emit('expand')" /></template></el-table-column>
            <el-table-column v-for="column in group.columns" :key="column.key" :label="column.label" min-width="160">
              <template #default="{ row }"><KettleValueInput :model-value="fieldValue(cell(row, column, group.template))" :field="compact(cell(row, column, group.template))" :context="inputContext" @update:model-value="mutate(() => writeField(cell(row, column, group.template), $event, { readonly }))" /></template>
            </el-table-column>
            <el-table-column v-if="!readonly" label="操作" width="80" fixed="right"><template #default="{ row, $index }"><el-button link type="danger" :aria-label="`删除${group.label}第${$index + 1}行`" @click="mutate(() => removeRow(group, row, { readonly }))">删除</el-button></template></el-table-column>
          </el-table>
        </template>
        <KettleGenericConfig v-else-if="group.element" :element="group.element" :template-xml="group.template" :readonly="readonly" :can-expand="canExpand" @change="changed" @expand="emit('expand')" />
        <template v-else><el-text type="info">此配置组尚未添加</el-text><el-button class="ml10" :disabled="readonly" @click="mutate(() => addGroup(group, { readonly }))">添加{{ group.label }}</el-button></template>
      </el-collapse-item>
    </el-collapse>
    <el-empty v-if="!model.fields.length && !model.groups.length && !error" description="此处没有原生参数" :image-size="48" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt16" />
  </section>
</template>
<script setup>
import { computed, ref, useId, watch } from 'vue'
import KettleValueInput from './KettleValueInput.vue'
import { describe, fieldValue, writeField, addRow, removeRow, addGroup, rowField, nodeKey } from './genericConfig'
const props = defineProps({ element: Object, templateXml: { type: [String, Object], default: '' }, readonly: Boolean, canExpand: Boolean, externalRevision: Number })
const emit = defineEmits(['change', 'expand'])
const revision = ref(0), expanded = ref([]), error = ref('')
const controlPrefix = useId()
const controlId = key => `${controlPrefix}-${key}`
const inputContext = computed(() => ({ readonly: props.readonly }))
const model = computed(() => { revision.value; props.externalRevision; try { return describe(props.element, props.templateXml) } catch (cause) { return { fields: [], groups: [], error: cause.message } } })
watch(() => [props.element, props.templateXml], () => { error.value = ''; revision.value++; expanded.value = model.value.groups.slice(0, 2).map(group => group.key) }, { immediate: true })
watch(() => model.value.error, value => { if (value) error.value = value }, { immediate: true })
function changed() { revision.value++; emit('change') }
function mutate(operation) { if (props.readonly) return; try { operation(); error.value = ''; changed() } catch (cause) { error.value = cause.message } }
const cell = (row, column, template) => rowField(row, column, template)
const compact = field => ['code', 'textarea'].includes(field.type) ? { ...field, type: 'text' } : field
</script>
<style scoped>
.kettle-generic { min-width: 0; }
.kettle-generic__label, .kettle-generic__group-title, .kettle-generic__toolbar { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--el-font-size-small); }
.kettle-generic__label, .kettle-generic__group-title { min-width: 0; overflow-wrap: break-word; word-break: break-word; }
.kettle-generic__label .el-text { overflow-wrap: break-word; word-break: break-word; }
.kettle-generic__group-title { line-height: var(--el-component-size-small); padding-top: var(--el-font-size-extra-small); padding-bottom: var(--el-font-size-extra-small); }
.kettle-generic__toolbar { align-items: center; justify-content: space-between; margin-bottom: var(--el-font-size-small); }
.kettle-generic__toolbar .el-button + .el-button { margin-left: 0; }
.kettle-generic__help { display: block; margin-bottom: var(--el-font-size-small); }
.kettle-generic :deep(.el-collapse-item__header) { height: auto; min-height: var(--el-collapse-header-height); }
.kettle-generic :deep(.el-table__expanded-cell) { padding: var(--el-font-size-base); }
</style>
