<template>
  <section class="equipment-inventory">
    <div class="inventory-toolbar">
      <el-checkbox :model-value="allSelected" :indeterminate="partSelected" :disabled="!sheetDevices.length" @change="selectAll">
        全选当前 Sheet
      </el-checkbox>
      <el-text type="info">共 {{ sheetDevices.length }} 台，已选 {{ selectedKeys.length }} 台</el-text>
      <el-switch v-if="canViewPlain" v-model="showPasswords" active-text="全部明文显示" aria-label="全部明文显示" :loading="passwordLoading" />
      <el-button v-if="canExport" icon="Download" :disabled="!sheetDevices.length" @click="exportRows">导出设备</el-button>
      <el-button
        v-if="canRemove" type="danger" plain icon="Delete" :disabled="!canRemoveSelected"
        :loading="deleting" @click="removeRows"
      >删除所选</el-button>
    </div>
    <el-alert v-if="passwordError" type="error" :title="passwordError" :closable="false" show-icon />
    <div class="inventory-grid" v-loading="loading">
      <Grid
        v-if="sheetDevices.length" class="inventory-grid__table" theme="equipment" :theme-definitions="EQUIPMENT_GRID_THEMES"
        :source="gridRows" :columns="columns" :readonly="true"
        :row-headers="true" :row-size="38" :range="true" :resize="true" :use-clipboard="true"
      />
      <el-empty v-else description="当前工作表没有设备" :image-size="72" />
    </div>
    <Pagination
      v-show="sheetDevices.length" :total="sheetDevices.length"
      v-model:page="page.pageNum" v-model:limit="page.pageSize"
      :page-sizes="[20, 50, 100]" :auto-scroll="false"
    />
    <el-tabs v-model="activeSheet" class="motion-tabs inventory-sheets" tab-position="bottom">
      <el-tab-pane v-for="sheet in sheets" :key="sheet.value" :label="sheet.label" :name="sheet.value">
        <template #label>
          <span class="motion-control-label">
            <svg-icon icon-class="keyline-file-spreadsheet" class="motion-control-label__icon" />
            <span class="motion-control-label__text">{{ sheet.label }}</span>
          </span>
        </template>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useWindowSize } from '@vueuse/core'
import Grid, { VGridVueTemplate } from '@revolist/vue3-datagrid'
import EquipmentInventoryCell from './EquipmentInventoryCell.vue'
import { readEquipmentPasswords } from '@/api/support/equipment'
import { EQUIPMENT_GRID_THEMES, buildEquipmentSheets, filterEquipmentSheet, inventoryDeviceKey, inventoryPasswordCells } from './equipmentInventory.rules'

const props = defineProps({
  siteId: { type: [Number, String], required: true },
  devices: { type: Array, default: () => [] },
  platforms: { type: Array, default: () => [] },
  links: { type: Array, default: () => [] },
  scopePlatformId: { type: [Number, String], default: null },
  sheet: { type: String, default: 'ALL' },
  revision: { type: Number, default: 0 },
  loading: Boolean, deleting: Boolean
})
const emit = defineEmits(['detail', 'edit', 'place', 'delete', 'export', 'update:sheet'])
const { proxy } = getCurrentInstance()
const { width: viewportWidth } = useWindowSize()
const activeSheet = computed({ get: () => props.sheet, set: value => emit('update:sheet', value) })
const selectedKeys = ref([])
const page = reactive({ pageNum: 1, pageSize: 20 })
const showPasswords = ref(false)
const passwordLoading = ref(false)
const passwordError = ref('')
const passwordCache = ref({})
let passwordRequest = 0
const canViewPlain = computed(() => proxy.$auth.hasPermi(['support:credential:viewPlain']))
const canExport = computed(() => proxy.$auth.hasPermi(['support:equipment:export', 'support:server:export', 'support:hardwareAsset:export']))
const canRemove = computed(() => proxy.$auth.hasPermi(['support:equipment:remove', 'support:server:remove', 'support:hardwareAsset:remove']))
const canRemoveSelected = computed(() => selectedKeys.value.length > 0 && sheetDevices.value
  .filter(device => selectedKeys.value.includes(inventoryDeviceKey(device)))
  .every(device => proxy.$auth.hasPermi(['support:equipment:remove', device.sourceType === 'SERVER' ? 'support:server:remove' : 'support:hardwareAsset:remove'])))
const canPlace = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', 'support:hardwareAsset:edit']))
const sheets = computed(() => buildEquipmentSheets(props.platforms, props.scopePlatformId))
const sheetDevices = computed(() => filterEquipmentSheet(props.devices, activeSheet.value, props.platforms))
const pageDevices = computed(() => sheetDevices.value.slice((page.pageNum - 1) * page.pageSize, page.pageNum * page.pageSize))
const allSelected = computed(() => sheetDevices.value.length > 0 && selectedKeys.value.length === sheetDevices.value.length)
const partSelected = computed(() => selectedKeys.value.length > 0 && !allSelected.value)
const gridRows = computed(() => pageDevices.value.map(device => {
  const key = inventoryDeviceKey(device)
  const outgoing = props.links.filter(link => link.sourceType === device.sourceType && Number(link.sourceId) === Number(device.sourceId))
  return {
    ...device, deviceKey: key, _selected: selectedKeys.value.includes(key),
    _canEdit: proxy.$auth.hasPermi(['support:equipment:edit', device.sourceType === 'SERVER' ? 'support:server:edit' : 'support:hardwareAsset:edit']),
    _canPlace: canPlace.value,
    statusLabel: device.status === '1' ? '停用' : '正常',
    rackRange: device.rackUStart ? device.rackUStart + '-' + device.rackUEnd + 'U' : '-',
    uplink: outgoing.map(link => link.targetName || link.targetIp || link.targetId).join(' / ') || device.legacyUplinkDevice || '-',
    ...inventoryPasswordCells(device, passwordCache.value[key], showPasswords.value && canViewPlain.value)
  }
}))
const cell = VGridVueTemplate(EquipmentInventoryCell, {
  onSelect: selectRow, onDetail: row => emit('detail', originalDevice(row)),
  onEdit: row => emit('edit', originalDevice(row)), onPlace: row => emit('place', originalDevice(row))
})
const columns = computed(() => [
  { prop: '_selected', name: '选择', size: 60, pin: 'colPinStart', cellTemplate: cell },
  { prop: 'assetName', name: '设备名称', size: 190, pin: viewportWidth.value >= 768 ? 'colPinStart' : undefined },
  ...[
    ['assetTypeLabel', '类型', 85], ['ipAddress', 'IP 地址', 150], ['sshPort', 'SSH 端口', 95],
    ['osType', '操作系统', 125], ['loginUsername', '登录账号', 120],
    ...(canViewPlain.value ? [
      ['loginPassword', '设备登录密码', 170], ['hikPassword', 'hik 密码', 170], ['rootPassword', 'root 密码', 170],
      ['otherUsername', '其他账号', 140], ['otherPassword', '其他密码', 180]
    ] : []),
    ['bindingLabel', '平台归属', 180], ['equipmentRoom', '机房', 170], ['cabinetNo', '机柜', 100],
    ['rackRange', 'U 位', 100], ['uplink', '上联设备', 150], ['manufacturer', '厂商', 120],
    ['assetModel', '型号', 130], ['statusLabel', '状态', 80]
  ].map(([prop, name, size]) => ({ prop, name, size })),
  { prop: '_actions', name: '操作', size: 150, pin: viewportWidth.value >= 768 ? 'colPinEnd' : undefined, cellTemplate: cell }
])
function originalDevice(row) {
  return props.devices.find(device => inventoryDeviceKey(device) === row.deviceKey)
}
function selectRow(row, checked) {
  const keys = new Set(selectedKeys.value)
  if (checked) keys.add(row.deviceKey)
  else keys.delete(row.deviceKey)
  selectedKeys.value = [...keys]
}
function selectAll(checked) {
  selectedKeys.value = checked ? sheetDevices.value.map(inventoryDeviceKey) : []
}
function removeRows() {
  emit('delete', sheetDevices.value.filter(device => selectedKeys.value.includes(inventoryDeviceKey(device))))
}
function exportRows() {
  emit('export', {
    platformId: /^\d+$/.test(activeSheet.value) ? Number(activeSheet.value) : props.scopePlatformId,
    deviceKeys: selectedKeys.value.length ? selectedKeys.value : sheetDevices.value.map(inventoryDeviceKey)
  })
}
function clearPasswords() {
  passwordRequest += 1
  passwordCache.value = {}
  passwordLoading.value = false
}
async function loadPasswords() {
  clearPasswords()
  passwordError.value = ''
  if (!showPasswords.value || !canViewPlain.value || !pageDevices.value.length) return
  const request = passwordRequest
  const siteId = props.siteId
  passwordLoading.value = true
  try {
    const response = await readEquipmentPasswords({
      siteId,
      devices: pageDevices.value.map(device => ({ sourceType: device.sourceType, sourceId: device.sourceId }))
    })
    if (request !== passwordRequest || siteId !== props.siteId || !showPasswords.value) return
    passwordCache.value = Object.fromEntries((response.data || []).map(row => [row.deviceKey, row.credentials]))
  } catch {
    if (request !== passwordRequest) return
    showPasswords.value = false
    passwordError.value = '读取密码失败，未展示任何明文。请检查权限后重新开启。'
  } finally {
    if (request === passwordRequest) passwordLoading.value = false
  }
}
watch([activeSheet, () => props.scopePlatformId], () => {
  page.pageNum = 1
  selectedKeys.value = []
})
watch(sheets, items => {
  if (!items.some(item => item.value === activeSheet.value)) activeSheet.value = 'ALL'
})
watch(() => props.devices.map(inventoryDeviceKey).join(','), () => {
  const available = new Set(sheetDevices.value.map(inventoryDeviceKey))
  selectedKeys.value = selectedKeys.value.filter(key => available.has(key))
  page.pageNum = 1
})
watch(() => props.siteId, () => { showPasswords.value = false; activeSheet.value = 'ALL'; clearPasswords() })
watch(() => props.revision, () => { showPasswords.value = false; clearPasswords() })
watch([showPasswords, canViewPlain, () => pageDevices.value.map(inventoryDeviceKey).join(',')], loadPasswords)
onBeforeUnmount(clearPasswords)
defineExpose({ resetPage: () => { page.pageNum = 1; selectedKeys.value = [] } })
</script>

<style scoped>
.equipment-inventory {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  max-width: 100%;
  height: 100%;
}
.inventory-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; padding-bottom: 12px; }
.inventory-grid { flex: 1; min-height: 200px; min-width: 0; overflow: hidden; border: 1px solid var(--el-border-color); }
.inventory-grid__table { width: 100%; height: 100%; }
.inventory-sheets { flex: 0 0 auto; }
.inventory-sheets :deep(.el-tabs__header) { margin: 0; }
</style>
