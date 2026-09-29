<template>
  <section class="equipment-inventory">
    <div class="inventory-toolbar">
      <template v-if="editing">
        <el-tag type="warning" effect="plain" role="status">正在编辑</el-tag>
        <el-text>已修改 {{ dirtyCount }} 台设备</el-text>
        <el-button type="primary" icon="Check" :loading="saving || confirming" :disabled="(!dirtyCount && !cellEditing) || starting" @click="saveDraft">保存</el-button>
        <el-button icon="Close" :disabled="saving || starting || confirming" @click="cancelEditing">取消</el-button>
        <el-button icon="Refresh" :loading="starting" :disabled="saving || confirming" @click="rebaseDraft">重新核对</el-button>
        <el-tooltip content="将选区首行的值复制到同列下方选中行" placement="top">
          <el-button icon="Bottom" :disabled="!canFillDown || saving || starting || confirming" @click="fillDown">向下填充</el-button>
        </el-tooltip>
      </template>
      <template v-else>
        <el-button v-if="canEditAny" type="primary" icon="Edit" :loading="starting" :disabled="!visibleDevices.length" @click="startEditing()">编辑</el-button>
        <el-checkbox :model-value="allSelected" :indeterminate="partSelected" :disabled="!visibleDevices.length || starting" @change="selectAll">全选当前列表</el-checkbox>
        <el-text type="info">共 {{ visibleDevices.length }} 台，已选 {{ selectedKeys.length }} 台</el-text>
        <el-button v-if="canExport" icon="Download" data-motion-direction="down" :disabled="!visibleDevices.length || starting" @click="exportRows">导出设备</el-button>
        <el-button v-if="canRemove" type="danger" plain icon="Delete" :disabled="!canRemoveSelected || starting" :loading="deleting" @click="removeRows">删除所选</el-button>
      </template>
      <el-select v-model="fieldGroup" class="inventory-field-group" aria-label="设备字段组" :disabled="saving || starting || confirming">
        <el-option label="常用字段" value="ALL" /><el-option label="基础档案" value="PROFILE" />
        <el-option label="平台与位置" value="LOCATION" /><el-option label="账号密码" value="CREDENTIAL" />
        <el-option v-if="hasHardware" label="硬件扩展属性" value="EXTENDED" />
      </el-select>
      <el-switch v-if="canViewPlain" v-model="showPasswords" active-text="全部明文显示" aria-label="全部明文显示" :disabled="saving || starting" :loading="passwordLoading" />
    </div>
    <el-alert v-if="saveError" type="error" :title="saveError" :closable="false" show-icon />
    <el-alert v-if="passwordError" type="error" :title="passwordError" :closable="false" show-icon />
    <div class="inventory-grid" v-loading="loading || starting" @copy.capture="copyRange" @setrange="selectRange">
      <Grid
        v-if="visibleDevices.length" class="inventory-grid__table" theme="equipment" :theme-definitions="EQUIPMENT_GRID_THEMES"
        :source="gridRows" :columns="columns" :readonly="!editing || saving || starting || confirming"
        :editors="gridEditors" :apply-on-close="true"
        :row-headers="true" :row-size="editing ? 42 : 38" :range="true" :resize="true" :use-clipboard="{ rangeFill: true }"
        @beforeedit="editCell" @beforerangeedit="editRange" @beforeautofill="autoFill" @afterfocus="focusCell"
      />
      <el-empty v-else description="当前分类没有符合条件的设备" :image-size="72" />
    </div>
    <Pagination v-show="visibleDevices.length" :total="visibleDevices.length"
      v-model:page="page.pageNum" v-model:limit="page.pageSize"
      :page-sizes="[20, 50, 100]" :auto-scroll="false" :disabled="saving || starting || confirming" />
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, markRaw, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import Grid, { VGridVueEditor, VGridVueTemplate } from '@revolist/vue3-datagrid'
import EquipmentInventoryCell from './EquipmentInventoryCell.vue'
import EquipmentWorkbookCell from './EquipmentWorkbookCell.vue'
import { getEquipmentWorkbook, readEquipmentPasswords, saveEquipmentWorkbook } from '@/api/support/equipment'
import { EQUIPMENT_GRID_THEMES, inventoryDeviceKey, inventoryPasswordCells } from './equipmentInventory.rules'
import { LOCATION_FIELDS, SECRET_FIELDS, WORKBOOK_FIELDS, setWorkbookValue, validateWorkbookDraft, workbookPayload, workbookRowChanges } from './equipmentWorkbook.rules'
import { canEditWorkbookCell, prepareWorkbookRange, workbookCopyValue, workbookEditValue } from './equipmentWorkbook.grid'

const props = defineProps({
  siteId: { type: [Number, String], required: true },
  devices: { type: Array, default: () => [] }, links: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] }, cabinets: { type: Array, default: () => [] },
  platforms: { type: Array, default: () => [] },
  contextKey: { type: String, default: 'ALL' }, revision: { type: Number, default: 0 },
  loading: Boolean, deleting: Boolean
})
const emit = defineEmits(['configure', 'delete', 'export', 'editing-change', 'saved'])
const { proxy } = getCurrentInstance()
const { width: viewportWidth } = useWindowSize()
const selectedKeys = ref([])
const page = reactive({ pageNum: 1, pageSize: 20 })
const fieldGroup = ref('ALL')
const editing = ref(false), starting = ref(false), saving = ref(false)
const confirming = ref(false)
const saveError = ref('')
const originals = ref({})
const drafts = ref([])
const editKeys = ref([])
const options = ref({ rooms: [], cabinets: [], platforms: [] })
const showPasswords = ref(false), passwordLoading = ref(false), passwordError = ref('')
const passwordCache = ref({})
const selection = ref(null)
const cellEditing = ref(false)
let activeEditor = null
const vueEditor = VGridVueEditor(EquipmentWorkbookCell)
const gridEditors = { equipment: (schema, save, close) => {
  const editSession = { value: workbookEditValue(schema.model, schema.column) }
  const editor = vueEditor({ ...schema.column, editSession }, save, close)
  const session = { key: schema.model.deviceKey, field: schema.prop, initialValue: editSession.value, editSession }
  activeEditor = session
  cellEditing.value = true
  editor.getValue = () => editSession.value
  const destroy = editor.disconnectedCallback.bind(editor)
  editor.disconnectedCallback = () => { if (activeEditor === session) { activeEditor = null; cellEditing.value = false }; destroy() }
  return editor
} }
let passwordRequest = 0, editRequest = 0
const canViewPlain = computed(() => proxy.$auth.hasPermi(['support:credential:viewPlain']))
const canEditAny = computed(() => props.devices.some(device => mayEdit(device)))
const canExport = computed(() => proxy.$auth.hasPermi(['support:equipment:export', 'support:server:export', 'support:hardwareAsset:export']))
const canRemove = computed(() => proxy.$auth.hasPermi(['support:equipment:remove', 'support:server:remove', 'support:hardwareAsset:remove']))
const canRemoveSelected = computed(() => selectedKeys.value.length > 0 && visibleDevices.value
  .filter(device => selectedKeys.value.includes(inventoryDeviceKey(device)))
  .every(device => proxy.$auth.hasPermi(['support:equipment:remove', device.sourceType === 'SERVER' ? 'support:server:remove' : 'support:hardwareAsset:remove'])))
const canPlace = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', 'support:hardwareAsset:edit']))
const canBind = computed(() => proxy.$auth.hasPermi(['support:equipment:edit']))
const visibleDevices = computed(() => editing.value ? editKeys.value.map(key => drafts.value.find(row => row.deviceKey === key)).filter(Boolean) : props.devices)
const pageDevices = computed(() => visibleDevices.value.slice((page.pageNum - 1) * page.pageSize, page.pageNum * page.pageSize))
const hasHardware = computed(() => visibleDevices.value.some(device => device.sourceType === 'HARDWARE'))
const allSelected = computed(() => visibleDevices.value.length > 0 && selectedKeys.value.length === visibleDevices.value.length)
const partSelected = computed(() => selectedKeys.value.length > 0 && !allSelected.value)
const dirtyCount = computed(() => editing.value ? workbookPayload(props.siteId, originals.value, drafts.value).rows.length : 0)
const readOptions = computed(() => ({ rooms: props.rooms, cabinets: props.cabinets, platforms: props.platforms }))
const gridRows = computed(() => {
  if (editing.value) return pageDevices.value
  return pageDevices.value.map(device => {
    const key = inventoryDeviceKey(device)
    return {
      ...device, deviceKey: key, _selected: selectedKeys.value.includes(key), _editing: false,
      _options: readOptions.value, _platformLabels: platformLabels(props.platforms),
      ...inventoryPasswordCells(device, passwordCache.value[key], showPasswords.value && canViewPlain.value)
    }
  })
})
const actionCell = VGridVueTemplate(EquipmentInventoryCell, {
  onSelect: selectRow, onConfigure: row => emit('configure', originalDevice(row))
})
const valueCell = VGridVueTemplate(EquipmentWorkbookCell)
const columns = computed(() => [
  { prop: '_selected', name: '选择', size: 60, pin: 'colPinStart', cellTemplate: actionCell, readonly: true },
  ...WORKBOOK_FIELDS.filter(field => {
    if (field.source && !visibleDevices.value.some(device => device.sourceType === field.source)) return false
    if (field.kind === 'password' && !canViewPlain.value && !editing.value) return false
    if (['assetName', 'assetTypeLabel', 'ipAddress'].includes(field.prop)) return true
    if (field.group === 'EXTENDED') return fieldGroup.value === 'EXTENDED'
    if (fieldGroup.value === 'EXTENDED') return false
    if (fieldGroup.value === 'ALL') return true
    const location = field.prop === 'platformIds' || LOCATION_FIELDS.includes(field.prop)
    const credential = SECRET_FIELDS.includes(field.prop) || ['loginUsername', 'otherUsername', 'rootDirectLogin'].includes(field.prop)
    return fieldGroup.value === 'LOCATION' ? location : fieldGroup.value === 'CREDENTIAL' ? (credential || field.prop === 'sshPort') : !location && !credential
  }).map(field => ({
    ...field, cellTemplate: valueCell, editor: 'equipment', readonly: ({ model }) => !canEditWorkbookCell(model, field),
    pin: (field.prop === 'assetName' && viewportWidth.value >= 768) || (field.prop === 'ipAddress' && viewportWidth.value >= 1280) ? 'colPinStart' : undefined
  })),
  { prop: '_actions', name: '操作', size: 95, pin: viewportWidth.value >= 768 ? 'colPinEnd' : undefined, cellTemplate: actionCell, readonly: true }
])
const canFillDown = computed(() => editing.value && selection.value && selection.value.x === selection.value.x1 && selection.value.y1 > selection.value.y)
function focusCell({ detail }) {
  selection.value = { x: detail.colIndex, x1: detail.colIndex, y: detail.rowIndex, y1: detail.rowIndex, colType: detail.colType }
}
function selectRange({ detail }) {
  selection.value = { ...detail, colType: selection.value?.colType || 'rgCol' }
}
function flushEditor() {
  if (activeEditor && JSON.stringify(activeEditor.initialValue) !== JSON.stringify(activeEditor.editSession.value)) {
    changeValue(activeEditor.key, activeEditor.field, activeEditor.editSession.value)
  }
}
function editCell(event) {
  event.preventDefault()
  if (activeEditor?.key === event.detail.model.deviceKey && activeEditor.field === event.detail.prop
    && JSON.stringify(activeEditor.initialValue) === JSON.stringify(event.detail.val)) return
  changeValue(event.detail.model.deviceKey, event.detail.prop, event.detail.val)
}
function editRange(event) {
  event.preventDefault()
  applyRange(event.detail.data)
}
function applyRange(data) {
  if (!editing.value || saving.value || starting.value || confirming.value) return
  try {
    const changes = prepareWorkbookRange(data, pageDevices.value, options.value)
    changes.forEach(change => changeValue(change.key, change.field, change.value))
    if (!changes.length) proxy.$modal.msgWarning('选区没有可编辑的单元格')
  } catch (error) { proxy.$modal.msgWarning(error.message) }
}
function copyRange(event) {
  if (event.target.closest?.('input, textarea, [contenteditable="true"]') || !selection.value || !event.clipboardData) return
  // ClipboardEvent data must be written synchronously; the grid's async component method can miss this window.
  event.preventDefault()
  event.stopImmediatePropagation()
  try {
    const range = selection.value
    const columnsInRange = columns.value.filter(column => (column.pin || 'rgCol') === range.colType).slice(range.x, range.x1 + 1)
    const data = []
    for (let y = range.y; y <= range.y1; y++) data.push(columnsInRange.map(column =>
      workbookCopyValue(gridRows.value[y], WORKBOOK_FIELDS.find(field => field.prop === column.prop))).join('\t'))
    event.clipboardData.setData('text/plain', data.join('\n'))
  } catch (error) { proxy.$modal.msgWarning(error.message) }
}
function autoFill(event) {
  if (!editing.value || saving.value || starting.value || confirming.value) { event.preventDefault(); return }
  try {
    const { oldRange, newRange, mapping, newData } = event.detail
    if (oldRange.x !== oldRange.x1 || newRange.x !== oldRange.x || newRange.x1 !== oldRange.x1) throw new Error('请在同一列内向下拖动填充')
    for (const [index, fields] of Object.entries(mapping)) {
      for (const [prop, source] of Object.entries(fields)) {
        const field = WORKBOOK_FIELDS.find(item => item.prop === source.colProp)
        newData[index][prop] = workbookCopyValue(pageDevices.value[source.rowIndex], field)
      }
    }
  } catch (error) { event.preventDefault(); proxy.$modal.msgWarning(error.message) }
}
function fillDown() {
  if (!canFillDown.value) return
  const range = selection.value
  const fields = columns.value.filter(column => (column.pin || 'rgCol') === range.colType)
  const field = WORKBOOK_FIELDS.find(item => item.prop === fields[range.x]?.prop)
  if (!field) return
  try {
    const value = workbookCopyValue(pageDevices.value[range.y], field)
    const data = {}
    for (let y = range.y + 1; y <= range.y1; y++) data[y] = { [field.prop]: value }
    applyRange(data)
  } catch (error) { proxy.$modal.msgWarning(error.message) }
}
function mayEdit(device) {
  return proxy.$auth.hasPermi(['support:equipment:edit', device.sourceType === 'SERVER' ? 'support:server:edit' : 'support:hardwareAsset:edit'])
}
function platformLabels(platforms) {
  return Object.fromEntries(platforms.map(item => {
    const parent = platforms.find(p => Number(p.platformId) === Number(item.parentPlatformId))
    return [item.platformId, item.displayName || (parent ? parent.platformName + ' / ' : '') + item.platformName]
  }))
}
function installSnapshot(snapshot) {
  const labels = platformLabels(snapshot.platforms || [])
  options.value = markRaw({ rooms: snapshot.rooms || [], cabinets: snapshot.cabinets || [], platforms: snapshot.platforms || [], networks: snapshot.networks || [] })
  originals.value = Object.fromEntries(snapshot.rows.map(row => [row.deviceKey, row]))
  drafts.value = snapshot.rows.map(row => ({
    ...row, platformIds: [...(row.platformIds || [])],
    assetTypeLabel: props.devices.find(device => inventoryDeviceKey(device) === row.deviceKey)?.assetTypeLabel || row.assetType,
    _editing: true, _saving: false, _options: options.value, _platformLabels: labels,
    _canBind: canBind.value, _canPlace: canPlace.value, _dirtyFields: [], _errors: {},
    _secrets: {}, _passwordInputs: {}, _plainValues: {}, _showPasswords: false
  }))
}
async function startEditing(deviceKey, group = 'ALL') {
  if (editing.value || starting.value || !canEditAny.value) return
  const sequence = ++editRequest
  starting.value = true
  emit('editing-change', true)
  saveError.value = ''
  try {
    const response = await getEquipmentWorkbook(props.siteId)
    if (sequence !== editRequest) return
    installSnapshot(response.data)
    const wanted = typeof deviceKey === 'string' ? [deviceKey] : props.devices.map(inventoryDeviceKey)
    editKeys.value = wanted.filter(key => originals.value[key])
    if (!editKeys.value.length) throw new Error('没有可编辑的设备，请刷新清单')
    showPasswords.value = false
    clearPasswords()
    selectedKeys.value = []
    page.pageNum = 1
    fieldGroup.value = ['ALL', 'PROFILE', 'LOCATION', 'CREDENTIAL', 'EXTENDED'].includes(group) ? group : 'ALL'
    editing.value = true
  } catch (error) {
    if (sequence !== editRequest) return
    saveError.value = error?.msg || error?.message || '进入编辑模式失败'
    emit('editing-change', false)
  } finally { if (sequence === editRequest) starting.value = false }
}
function changeValue(key, field, value) {
  if (saving.value || starting.value || confirming.value || !editing.value) return
  const row = drafts.value.find(item => item.deviceKey === key)
  if (!row || !mayEdit(row) || !canEditWorkbookCell(row, WORKBOOK_FIELDS.find(item => item.prop === field))) return
  if ((field === 'platformIds' && !canBind.value) || (LOCATION_FIELDS.includes(field) && !canPlace.value)) return
  setWorkbookValue(row, field, value)
  row._dirtyFields = Object.keys(workbookRowChanges(originals.value[key], row))
  row._errors = {}
  saveError.value = ''
}
function finishEditing() {
  activeEditor = null
  cellEditing.value = false
  selection.value = null
  editRequest++
  editing.value = false
  starting.value = false
  drafts.value = []
  originals.value = {}
  editKeys.value = []
  saveError.value = ''
  showPasswords.value = false
  clearPasswords()
  emit('editing-change', false)
}
async function confirmClose() {
  if (saving.value || starting.value || confirming.value) { proxy.$modal.msgWarning('正在处理，请稍候'); return false }
  flushEditor()
  if (dirtyCount.value) {
    try { await proxy.$modal.confirm('当前设备清单有未保存修改，是否放弃这些修改？') } catch { return false }
  }
  if (editing.value) finishEditing()
  return true
}
async function cancelEditing() { await confirmClose() }
async function saveDraft() {
  flushEditor()
  if (saving.value || starting.value || confirming.value || !dirtyCount.value) return
  const errors = validateWorkbookDraft(originals.value, drafts.value, options.value)
  drafts.value.forEach(row => { row._errors = {} })
  errors.forEach(error => { const row = drafts.value.find(item => item.deviceKey === error.key); row._errors[error.field] = error.message })
  if (errors.length) { saveError.value = errors[0].message + (errors.length > 1 ? '，另有' + (errors.length - 1) + '处待修正' : ''); return }
  const payload = workbookPayload(props.siteId, originals.value, drafts.value)
  if (payload.rows.length > 500) { saveError.value = '单次最多保存500台设备'; return }
  const clearCount = payload.rows.filter(row => SECRET_FIELDS.some(field => field in row.changes && row.changes[field] === null)).length
  confirming.value = true
  try { await proxy.$modal.confirm('确认保存 ' + payload.rows.length + ' 台设备的修改？' + (clearCount ? '其中 ' + clearCount + ' 台将清空指定密码。' : '')) }
  catch { return }
  finally { confirming.value = false }
  saving.value = true
  drafts.value.forEach(row => { row._saving = true })
  try {
    const response = await saveEquipmentWorkbook(payload)
    finishEditing()
    proxy.$modal.msgSuccess('已保存 ' + response.data.updated + ' 台设备')
    emit('saved')
  } catch (error) {
    saveError.value = error?.msg || error?.message || '保存失败，草稿已保留'
  } finally {
    saving.value = false
    drafts.value.forEach(row => { row._saving = false })
  }
}
async function rebaseDraft() {
  if (saving.value || starting.value || confirming.value || !editing.value) return
  try { await proxy.$modal.confirm('将重新读取设备数据并保留你的修改，请核对后再保存。') } catch { return }
  flushEditor()
  const pending = workbookPayload(props.siteId, originals.value, drafts.value).rows
  starting.value = true
  drafts.value.forEach(row => { row._saving = true })
  try {
    const response = await getEquipmentWorkbook(props.siteId)
    const keys = new Set(response.data.rows.map(row => row.deviceKey))
    if (pending.some(row => !keys.has(row.sourceType + ':' + row.sourceId))) throw new Error('草稿中的设备已被删除，请取消编辑后刷新')
    installSnapshot(response.data)
    for (const change of pending) {
      const row = drafts.value.find(item => item.deviceKey === change.sourceType + ':' + change.sourceId)
      for (const [field, value] of Object.entries(change.changes)) {
        if (SECRET_FIELDS.includes(field)) { row._secrets[field] = value; row._passwordInputs[field] = value || '' }
        else row[field] = value
      }
      row._dirtyFields = Object.keys(workbookRowChanges(originals.value[row.deviceKey], row))
    }
    showPasswords.value = false
    clearPasswords()
    saveError.value = ''
  } catch (error) { saveError.value = error?.msg || error?.message || '重新核对失败，草稿已保留' }
  finally { starting.value = false; drafts.value.forEach(row => { row._saving = false }) }
}
function originalDevice(row) { return props.devices.find(device => inventoryDeviceKey(device) === row.deviceKey) }
function selectRow(row, checked) {
  if (editing.value) return
  const keys = new Set(selectedKeys.value)
  if (checked) keys.add(row.deviceKey)
  else keys.delete(row.deviceKey)
  selectedKeys.value = [...keys]
}
function selectAll(checked) { selectedKeys.value = checked ? visibleDevices.value.map(inventoryDeviceKey) : [] }
function removeRows() { if (!editing.value) emit('delete', visibleDevices.value.filter(device => selectedKeys.value.includes(inventoryDeviceKey(device)))) }
function exportRows() { if (!editing.value) emit('export', { deviceKeys: selectedKeys.value.length ? selectedKeys.value : visibleDevices.value.map(inventoryDeviceKey) }) }
function clearPasswords() {
  passwordRequest++
  passwordCache.value = {}
  passwordLoading.value = false
  drafts.value.forEach(row => {
    row._plainValues = {}
    row._showPasswords = false
    for (const field of SECRET_FIELDS) {
      if (!Object.prototype.hasOwnProperty.call(row._secrets, field)) delete row._passwordInputs[field]
    }
  })
}
async function loadPasswords() {
  clearPasswords()
  passwordError.value = ''
  if (!showPasswords.value || !canViewPlain.value || !pageDevices.value.length) return
  const request = passwordRequest, siteId = props.siteId
  passwordLoading.value = true
  try {
    const response = await readEquipmentPasswords({ siteId, devices: pageDevices.value.map(device => ({ sourceType: device.sourceType, sourceId: device.sourceId })) })
    if (request !== passwordRequest || siteId !== props.siteId || !showPasswords.value) return
    passwordCache.value = Object.fromEntries((response.data || []).map(row => [row.deviceKey, row.credentials]))
    for (const row of drafts.value) {
      const accounts = passwordCache.value[row.deviceKey]
      row._showPasswords = true
      if (!accounts) continue
      row._plainValues = inventoryPasswordCells(row, accounts, true)
      if (row.sourceType === 'SERVER') {
        for (const [field, username] of [['hikPassword', 'hik'], ['rootPassword', 'root'], ['otherPassword', row.otherUsername]]) {
          const credential = accounts.find(account => row[field + 'AccountName'] && account.name === row[field + 'AccountName'])
            || accounts.find(account => String(account.username).toLowerCase() === String(username).toLowerCase())
          row._plainValues[field] = credential?.password || ''
        }
      }
    }
  } catch {
    if (request !== passwordRequest) return
    showPasswords.value = false
    passwordError.value = '读取密码失败，未展示任何明文。请检查权限后重新开启。'
  } finally { if (request === passwordRequest) passwordLoading.value = false }
}
function beforeUnload(event) {
  flushEditor()
  if (!dirtyCount.value) return
  event.preventDefault()
  event.returnValue = ''
}
watch(() => props.contextKey, () => {
  if (editing.value) return
  page.pageNum = 1
  selectedKeys.value = []
  showPasswords.value = false
  clearPasswords()
})
watch(hasHardware, value => { if (!value && fieldGroup.value === 'EXTENDED') fieldGroup.value = 'ALL' })
watch(() => props.devices.map(inventoryDeviceKey).join(','), () => {
  if (editing.value) return
  const available = new Set(visibleDevices.value.map(inventoryDeviceKey))
  selectedKeys.value = selectedKeys.value.filter(key => available.has(key))
  page.pageNum = 1
})
watch(() => props.siteId, finishEditing)
watch([fieldGroup, () => page.pageNum, () => page.pageSize], () => { selection.value = null })
watch(() => props.revision, () => { if (!editing.value) { showPasswords.value = false; clearPasswords() } })
watch([showPasswords, canViewPlain, () => pageDevices.value.map(inventoryDeviceKey).join(',')], loadPasswords)
onBeforeRouteLeave(confirmClose)
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => { editRequest++; clearPasswords(); window.removeEventListener('beforeunload', beforeUnload) })
defineExpose({ confirmClose, startEditing, resetPage: () => { if (!editing.value) { page.pageNum = 1; selectedKeys.value = [] } } })
</script>

<style scoped>
.equipment-inventory { display: flex; flex-direction: column; min-height: 0; min-width: 0; max-width: 100%; height: 100%; }
.inventory-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding-bottom: 12px; }
.inventory-toolbar :deep(.el-button + .el-button) { margin-left: 0; }
.inventory-field-group { width: 140px; }
.inventory-grid { flex: 1; min-height: 200px; min-width: 0; overflow: hidden; border: 1px solid var(--el-border-color); }
.inventory-grid__table { width: 100%; height: 100%; }
.inventory-grid .inventory-grid__table :deep(revogr-data .rgCell) { padding: 0; }
.inventory-grid__table :deep(.rgCell > span), .inventory-grid__table :deep(revogr-edit > span) { display: block; height: 100%; }
</style>
