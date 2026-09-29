<template>
  <div class="equipment-workbook-cell" :class="{ 'is-editor': editable, 'is-dirty': dirty, 'has-error': invalid }" :title="errorMessage" @keydown="editorKeydown">
    <template v-if="!editable">
      <span class="equipment-workbook-cell__text">{{ displayValue }}</span>
    </template>
    <el-switch v-else-if="column.kind === 'boolean'" ref="inputRef" :model-value="editValue === true" :disabled="model._saving"
      :aria-label="fieldLabel" @update:model-value="change" @mousedown.stop />
    <template v-else-if="column.kind === 'password'">
      <el-input
        :disabled="model._saving"
        ref="inputRef" :model-value="editValue" :type="model._showPasswords ? 'text' : 'password'"
        :placeholder="secretCleared ? '待清空' : (model[column.prop + 'Configured'] ? '已配置' : '未配置')"
        :aria-label="fieldLabel" autocomplete="new-password" :maxlength="128"
        @update:model-value="change" @mousedown.stop
      />
      <el-tooltip :content="dirty ? '撤销密码修改' : '清空已存密码'" placement="top">
        <el-button
          :disabled="model._saving || (!dirty && !model[column.prop + 'Configured'])"
          text size="small" :icon="dirty ? 'RefreshLeft' : 'Delete'" :type="dirty ? 'primary' : 'danger'"
          :aria-label="(dirty ? '撤销密码修改 ' : '清空已存密码 ') + model.assetName + ' ' + column.name"
          @click.stop="change(dirty ? '' : null)" @keydown.stop
        />
      </el-tooltip>
    </template>
    <el-select v-else-if="column.kind === 'platforms'" ref="inputRef"
      :disabled="model._saving"
      :model-value="editValue" multiple collapse-tags collapse-tags-tooltip filterable clearable
      :aria-label="fieldLabel" @visible-change="dropdownOpen = $event" @update:model-value="change" @mousedown.stop>
      <el-option v-for="option in platformOptions" :key="option.platformId" :value="option.platformId" :label="option.label" />
    </el-select>
    <el-select v-else-if="column.kind === 'room' || column.kind === 'cabinet'" ref="inputRef"
      :disabled="model._saving"
      :model-value="editValue" clearable filterable :aria-label="fieldLabel"
      :placeholder="column.kind === 'room' ? '未上架' : '未选择'"
      @visible-change="dropdownOpen = $event" @update:model-value="change($event || null)" @mousedown.stop>
      <el-option v-for="option in locationOptions" :key="option.value" :value="option.value" :label="option.label" />
    </el-select>
    <el-select v-else-if="column.kind === 'status'" ref="inputRef" :disabled="model._saving" :model-value="String(editValue)" :aria-label="fieldLabel"
      @visible-change="dropdownOpen = $event" @update:model-value="change" @mousedown.stop>
      <el-option label="正常" value="0" /><el-option label="停用" value="1" />
    </el-select>
    <el-select v-else-if="column.kind === 'network'" ref="inputRef" :disabled="model._saving" :model-value="editValue" :aria-label="fieldLabel"
      @visible-change="dropdownOpen = $event" @update:model-value="change" @mousedown.stop>
      <el-option v-for="option in model._options.networks || []" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <el-input v-else ref="inputRef" :disabled="model._saving" :model-value="editValue" :aria-label="fieldLabel"
      :inputmode="column.kind === 'number' ? 'numeric' : 'text'"
      :maxlength="FIELD_LENGTHS[column.prop] || 128"
      @update:model-value="change" @mousedown.stop />
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElButton, ElInput, ElOption, ElSelect, ElSwitch, ElTooltip } from 'element-plus'
import { FIELD_LENGTHS } from './equipmentWorkbook.rules'
import { workbookEditValue } from './equipmentWorkbook.grid'
const props = defineProps({ model: Object, column: Object, save: Function, close: Function, val: [String, Number] })
const editable = computed(() => Boolean(props.save))
const inputRef = ref(), dropdownOpen = ref(false)
const initialValue = workbookEditValue(props.model, props.column)
const editValue = ref(props.val && [undefined, 'number', 'password'].includes(props.column.kind) ? props.val : workbookEditValue(props.model, props.column))
if (props.column.editSession) props.column.editSession.value = editValue.value
const fieldLabel = computed(() => props.model.assetName + ' ' + props.column.name)
const dirty = computed(() => props.model._dirtyFields?.includes(props.column.prop)
  || (editable.value && JSON.stringify(editValue.value) !== JSON.stringify(initialValue)))
const errorMessage = computed(() => props.model._errors?.[props.column.prop] || '')
const invalid = computed(() => Boolean(errorMessage.value))
const secretCleared = computed(() => editable.value ? editValue.value === null : props.model._secrets?.[props.column.prop] === null)
const displayValue = computed(() => {
  if (props.column.source && props.column.source !== props.model.sourceType) return '-'
  if (props.column.assetTypes && !props.column.assetTypes.includes(props.model.assetType)) return '-'
  const row = props.model
  if (props.column.kind === 'boolean') return row[props.column.prop] === true || row[props.column.prop] === 1 ? '开启' : '关闭'
  if (props.column.kind === 'platforms') return row.bindingLabel || (row.platformIds || []).map(id => row._platformLabels?.[id] || id).join(' / ') || '未归属'
  if (props.column.kind === 'room') return row._options?.rooms.find(item => Number(item.roomId) === Number(row.roomId))?.roomName || row.equipmentRoom || '未上架'
  if (props.column.kind === 'cabinet') return row._options?.cabinets.find(item => Number(item.cabinetId) === Number(row.cabinetId))?.cabinetNo || row.cabinetNo || '-'
  if (props.column.kind === 'status') return String(row.status) === '1' ? '停用' : '正常'
  if (props.column.kind === 'password' && row._editing) {
    if (row[props.column.prop + 'Editable'] === false) return '多份凭据'
    if (row._secrets?.[props.column.prop] === null) return '待清空'
    const value = workbookEditValue(row, props.column)
    return row._showPasswords && value ? value : value || row[props.column.prop + 'Configured'] ? '******' : '-'
  }
  return props.model[props.column.prop] ?? '-'
})
const platformOptions = computed(() => (props.model._options?.platforms || [])
  .filter(item => props.model.sourceType !== 'SERVER' || item.platformLevel === 'SUB'
    || (props.model.platformIds || []).some(id => Number(id) === Number(item.platformId)))
  .map(item => ({ ...item, label: props.model._platformLabels?.[item.platformId] || item.platformName })))
const locationOptions = computed(() => props.column.kind === 'room'
  ? (props.model._options?.rooms || []).map(item => ({ value: item.roomId, label: item.roomName }))
  : (props.model._options?.cabinets || []).filter(item => Number(item.roomId) === Number(props.model.roomId))
    .map(item => ({ value: item.cabinetId, label: item.cabinetNo + ' / ' + (item.uCapacity || 45) + 'U' })))
function change(value) {
  editValue.value = value
  props.column.editSession.value = value
}
function editorKeydown(event) {
  if (!editable.value || event.isComposing) return
  if (event.key === 'Escape' && !dropdownOpen.value) { event.stopPropagation(); props.close() }
  else if (event.key === 'Enter' && !dropdownOpen.value) { event.preventDefault(); event.stopPropagation(); props.save(editValue.value) }
  else if (event.key === 'Tab') { props.save(editValue.value, true); props.close() }
  else event.stopPropagation()
}
onMounted(async () => {
  if (!editable.value) return
  await nextTick()
  inputRef.value?.focus?.()
  inputRef.value?.select?.()
})
</script>
<style scoped>
.equipment-workbook-cell { display: flex; align-items: center; min-width: 0; width: 100%; height: 100%; box-sizing: border-box; font-size: var(--el-font-size-base); }
.equipment-workbook-cell :deep(.el-input), .equipment-workbook-cell :deep(.el-select) { min-width: 0; width: 100%; }
.equipment-workbook-cell :deep(.el-button) { flex: none; margin: 0; }
.equipment-workbook-cell__text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding: 0 12px; }
.equipment-workbook-cell.is-editor { background: var(--el-bg-color); outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.equipment-workbook-cell.is-editor :deep(.el-input), .equipment-workbook-cell.is-editor :deep(.el-select) { height: 100%; flex: 1; }
.equipment-workbook-cell.is-editor :deep(.el-input__wrapper), .equipment-workbook-cell.is-editor :deep(.el-select__wrapper) { height: 100%; min-height: 100%; border-radius: 0; box-shadow: none; background: transparent; padding: 0 12px; }
.equipment-workbook-cell.is-editor :deep(.el-input__inner) { height: 100%; border: 0; outline: 0; box-shadow: none; background: transparent; }
.equipment-workbook-cell.is-editor :deep(.el-switch) { margin: 0 12px; }
.equipment-workbook-cell.is-dirty { background: var(--el-color-warning-light-9); }
.equipment-workbook-cell.has-error { outline: 1px solid var(--el-color-danger); outline-offset: -1px; }
</style>
