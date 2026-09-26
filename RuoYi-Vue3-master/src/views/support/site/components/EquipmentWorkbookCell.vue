<template>
  <div class="equipment-workbook-cell" :class="{ 'is-dirty': dirty, 'has-error': invalid }" :title="errorMessage">
    <template v-if="!editable">
      <span class="equipment-workbook-cell__text">{{ displayValue }}</span>
    </template>
    <template v-else-if="column.kind === 'password'">
      <el-input
        :disabled="model._saving"
        :model-value="secretValue" :type="model._showPasswords ? 'text' : 'password'"
        :placeholder="secretCleared ? '待清空' : (model[column.prop + 'Configured'] ? '已配置' : '未配置')"
        :aria-label="fieldLabel" autocomplete="new-password" size="small" :maxlength="128"
        @update:model-value="change" @keydown.stop @mousedown.stop
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
    <el-select v-else-if="column.kind === 'platforms'"
      :disabled="model._saving"
      :model-value="model.platformIds" multiple collapse-tags collapse-tags-tooltip filterable clearable
      :aria-label="fieldLabel" size="small" @update:model-value="change" @keydown.stop @mousedown.stop>
      <el-option v-for="option in platformOptions" :key="option.platformId" :value="option.platformId" :label="option.label" />
    </el-select>
    <el-select v-else-if="column.kind === 'room' || column.kind === 'cabinet'"
      :disabled="model._saving"
      :model-value="model[column.prop]" clearable filterable :aria-label="fieldLabel" size="small"
      :placeholder="column.kind === 'room' ? '未上架' : '未选择'"
      @update:model-value="change($event || null)" @keydown.stop @mousedown.stop>
      <el-option v-for="option in locationOptions" :key="option.value" :value="option.value" :label="option.label" />
    </el-select>
    <el-select v-else-if="column.kind === 'status'" :disabled="model._saving" :model-value="String(model.status)" :aria-label="fieldLabel" size="small"
      @update:model-value="change" @keydown.stop @mousedown.stop>
      <el-option label="正常" value="0" /><el-option label="停用" value="1" />
    </el-select>
    <el-select v-else-if="column.kind === 'network'" :disabled="model._saving" :model-value="model.networkEnv" :aria-label="fieldLabel" size="small"
      @update:model-value="change" @keydown.stop @mousedown.stop>
      <el-option v-for="option in model._options.networks || []" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <el-input v-else :disabled="model._saving" :model-value="model[column.prop]" :aria-label="fieldLabel" size="small"
      :inputmode="column.kind === 'number' ? 'numeric' : 'text'"
      :maxlength="FIELD_LENGTHS[column.prop] || 128"
      @update:model-value="change" @keydown.stop @mousedown.stop />
  </div>
</template>
<script setup>
import { computed } from 'vue'
import { ElButton, ElInput, ElOption, ElSelect, ElTooltip } from 'element-plus'
import { FIELD_LENGTHS } from './equipmentWorkbook.rules'
const props = defineProps({ model: Object, column: Object, onChange: Function })
const editable = computed(() => props.model._editing && props.model.canEdit && !props.column.readonly
  && (!props.column.source || props.column.source === props.model.sourceType)
  && (!props.column.assetTypes || props.column.assetTypes.includes(props.model.assetType))
  && (props.column.kind !== 'password' || props.model[props.column.prop + 'Editable'] !== false)
  && (!props.column.permission || props.model[props.column.permission]))
const fieldLabel = computed(() => props.model.assetName + ' ' + props.column.name)
const dirty = computed(() => props.model._dirtyFields?.includes(props.column.prop))
const errorMessage = computed(() => props.model._errors?.[props.column.prop] || '')
const invalid = computed(() => Boolean(errorMessage.value))
const secretValue = computed(() => props.model._passwordInputs?.[props.column.prop] ?? props.model._plainValues?.[props.column.prop] ?? '')
const secretCleared = computed(() => props.model._secrets?.[props.column.prop] === null)
const displayValue = computed(() => {
  if (props.column.source && props.column.source !== props.model.sourceType) return '-'
  if (props.column.assetTypes && !props.column.assetTypes.includes(props.model.assetType)) return '-'
  const row = props.model
  if (props.column.kind === 'platforms') return row.bindingLabel || (row.platformIds || []).map(id => row._platformLabels?.[id] || id).join(' / ') || '未归属'
  if (props.column.kind === 'room') return row._options?.rooms.find(item => Number(item.roomId) === Number(row.roomId))?.roomName || row.equipmentRoom || '未上架'
  if (props.column.kind === 'cabinet') return row._options?.cabinets.find(item => Number(item.cabinetId) === Number(row.cabinetId))?.cabinetNo || row.cabinetNo || '-'
  if (props.column.kind === 'status') return String(row.status) === '1' ? '停用' : '正常'
  if (props.column.kind === 'password' && row._editing) return row[props.column.prop + 'Editable'] === false ? '多份凭据' : row[props.column.prop + 'Configured'] ? '******' : '-'
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
function change(value) { props.onChange(props.model.deviceKey, props.column.prop, value) }
</script>
<style scoped>
.equipment-workbook-cell { display: flex; align-items: center; gap: 2px; min-width: 0; width: 100%; height: 100%; }
.equipment-workbook-cell :deep(.el-input), .equipment-workbook-cell :deep(.el-select) { min-width: 0; width: 100%; }
.equipment-workbook-cell :deep(.el-button) { flex: none; margin: 0; }
.equipment-workbook-cell__text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.equipment-workbook-cell.is-dirty { background: var(--el-color-warning-light-9); }
.equipment-workbook-cell.has-error { outline: 1px solid var(--el-color-danger); outline-offset: -1px; }
</style>
