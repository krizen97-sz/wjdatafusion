<template>
  <section class="kettle-generic">
    <el-form v-if="model.fields.length" label-position="top" :disabled="readonly">
      <el-form-item v-for="field in model.fields" :key="field.key">
        <template #label><el-space wrap><span>{{ field.label }}</span><el-text v-if="field.label !== field.name" type="info" size="small">{{ field.key }}</el-text></el-space></template>
        <KettleValueInput :model-value="fieldValue(field)" :field="field" :context="inputContext" @update:model-value="mutate(() => writeField(field, $event, { readonly }))" />
      </el-form-item>
    </el-form>
    <el-collapse v-if="model.groups.length" v-model="expanded">
      <el-collapse-item v-for="group in model.groups" :key="group.key" :name="group.key">
        <template #title><el-space><span>{{ group.label }}</span><el-text v-if="group.label !== group.name || group.kind === 'rows'" type="info" size="small">{{ group.label !== group.name ? group.name : '' }}{{ group.kind === 'rows' ? ` · ${group.rows.length} 项` : '' }}</el-text></el-space></template>
        <template v-if="group.kind === 'rows'">
          <el-button v-if="!readonly" icon="Plus" class="mb16" :disabled="!group.template" @click="mutate(() => addRow(group, { readonly }))">{{ group.nativeTemplate ? '新增一行' : '按已有行新增' }}</el-button>
          <el-table :data="group.rows" :row-key="nodeKey" size="small" empty-text="暂无配置项，可使用原生模板新增">
            <el-table-column type="expand"><template #default="{ row }"><KettleGenericConfig :element="row" :template-xml="group.template" :readonly="readonly" @change="changed" /></template></el-table-column>
            <el-table-column v-for="column in group.columns" :key="column.key" :label="column.label" min-width="160">
              <template #default="{ row }"><KettleValueInput :model-value="fieldValue(cell(row, column, group.template))" :field="compact(cell(row, column, group.template))" :context="inputContext" @update:model-value="mutate(() => writeField(cell(row, column, group.template), $event, { readonly }))" /></template>
            </el-table-column>
            <el-table-column v-if="!readonly" label="操作" width="80" fixed="right"><template #default="{ row, $index }"><el-button link type="danger" :aria-label="`删除${group.label}第${$index + 1}行`" @click="mutate(() => removeRow(group, row, { readonly }))">删除</el-button></template></el-table-column>
          </el-table>
        </template>
        <KettleGenericConfig v-else-if="group.element" :element="group.element" :template-xml="group.template" :readonly="readonly" @change="changed" />
        <template v-else><el-text type="info">此配置组尚未添加</el-text><el-button class="ml10" :disabled="readonly" @click="mutate(() => addGroup(group, { readonly }))">添加{{ group.label }}</el-button></template>
      </el-collapse-item>
    </el-collapse>
    <el-empty v-if="!model.fields.length && !model.groups.length && !error" description="此处没有原生参数" :image-size="48" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mt16" />
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import KettleValueInput from './KettleValueInput.vue'
import { describe, fieldValue, writeField, addRow, removeRow, addGroup, rowField, nodeKey } from './genericConfig'
const props = defineProps({ element: Object, templateXml: { type: [String, Object], default: '' }, readonly: Boolean })
const emit = defineEmits(['change'])
const revision = ref(0), expanded = ref([]), error = ref('')
const inputContext = computed(() => ({ readonly: props.readonly }))
const model = computed(() => { revision.value; try { return describe(props.element, props.templateXml) } catch (cause) { return { fields: [], groups: [], error: cause.message } } })
watch(() => [props.element, props.templateXml], () => { error.value = ''; revision.value++; expanded.value = model.value.groups.slice(0, 2).map(group => group.key) }, { immediate: true })
watch(() => model.value.error, value => { if (value) error.value = value }, { immediate: true })
function changed() { revision.value++; emit('change') }
function mutate(operation) { if (props.readonly) return; try { operation(); error.value = ''; changed() } catch (cause) { error.value = cause.message } }
const cell = (row, column, template) => rowField(row, column, template)
const compact = field => ['code', 'textarea'].includes(field.type) ? { ...field, type: 'text' } : field
</script>
<style scoped>
.kettle-generic { min-width: 0; }
</style>
