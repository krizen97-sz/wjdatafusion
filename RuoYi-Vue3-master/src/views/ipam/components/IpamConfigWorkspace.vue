<template>
  <div
    class="config-panel"
    :class="{
      'is-fullscreen': fullscreen,
      'is-detailed-mode': displayMode === 'detailed'
    }"
  >
    <div class="grid-toolbar">
      <div>
        <h3>{{ selectedSegment.startIp }} - {{ selectedSegment.endIp }}</h3>
        <span>{{ selectedSegment.segmentName }} / 网关 {{ selectedSegment.gatewayIp || '待配置' }} / 掩码 {{ selectedSegment.subnetMask || '待配置' }}</span>
      </div>
      <div class="grid-actions">
        <el-segmented
          class="grid-mode-toggle"
          :model-value="displayMode"
          :options="gridDisplayModeOptions"
          aria-label="IP网格显示方式"
          @change="$emit('update:display-mode', $event)"
        />
        <div class="legend">
          <span v-for="option in statusOptions" :key="option.value" :class="['legend-dot', statusMeta(option.value).className]">{{ option.label }}</span>
        </div>
        <el-button
          icon="Search"
          :loading="loading.scan"
          :disabled="sheetChangedCount > 0"
          @click="$emit('scan-network')"
          v-hasPermi="['ipam:network:scan']"
        >{{ scanButtonLabel }}</el-button>
        <el-button :icon="fullscreen ? 'Close' : 'FullScreen'" @click="$emit('toggle-fullscreen')">{{ fullscreen ? '退出全屏' : '全屏配置' }}</el-button>
        <el-button
          icon="Upload"
          :disabled="!editReady || selectedIpCount === 0"
          @click="$emit('open-paste')"
          v-hasPermi="['ipam:address:allocate']"
        >粘贴Excel</el-button>
        <el-button icon="Delete" :disabled="selectedIpCount === 0" @click="$emit('clear-selected')">清空选择</el-button>
        <el-button icon="Refresh" @click="$emit('reset')">重载</el-button>
      </div>
    </div>

    <div class="summary-strip">
      <span>总数 {{ gridSummary.total }}</span>
      <span>空闲 {{ gridSummary.FREE }}</span>
      <span>保留 {{ gridSummary.RESERVED }}</span>
      <span>已占用 {{ gridSummary.ALLOCATED }}</span>
      <span>已下发 {{ gridSummary.ISSUED }}</span>
      <span>禁用 {{ gridSummary.DISABLED }}</span>
      <span>已选 {{ selectedIpCount }} / {{ maxConfigSelection }}</span>
      <span>待保存 {{ sheetChangedCount }}</span>
      <span>校验 {{ sheetErrorCount }}</span>
      <span v-if="scanJob">在线 {{ scanJob.onlineCount || 0 }}</span>
      <span v-if="scanJob">离线 {{ scanJob.offlineCount || 0 }}</span>
      <span v-if="scanJob?.finishedTime">最近扫描 {{ scanJob.finishedTime }}</span>
    </div>

    <div
      v-loading="loading.grid"
      class="mini-grid"
      :class="{
        'is-wide-address': Number(selectedSegment.prefixLength) < 24,
        'is-detailed': displayMode === 'detailed'
      }"
    >
      <button
        v-for="item in addressRows"
        :key="item.ipAddress"
        v-memo="[
          displayMode,
          item.status,
          item.reservedReason,
          item.boundaryAddress,
          item.connectivityStatus,
          item.scanResponseTimeMs,
          item.lastScanTime,
          selectedSheetIp === item.ipAddress,
          isGridSelected(item.ipAddress),
          getGridField(item, 'communityName'),
          getGridField(item, 'targetType'),
          getGridField(item, 'manufacturer')
        ]"
        type="button"
        class="mini-cell"
        :title="getGridCellTitle(item)"
        :aria-label="getGridAriaLabel(item)"
        :aria-pressed="isGridSelected(item.ipAddress)"
        :disabled="isGridAddressDisabled(item)"
        :class="[statusMeta(item.status).className, { active: selectedSheetIp === item.ipAddress, selected: isGridSelected(item.ipAddress), 'is-boundary': item.boundaryAddress, 'is-gateway': item.reservedReason === '网关' }]"
        @mousedown.prevent="$emit('start-grid-selection', item)"
        @mouseenter="$emit('extend-grid-selection', item)"
        @click="handleGridKeyboardSelect(item, $event)"
      >
        <span v-if="displayMode === 'detailed'" class="mini-cell__community">{{ getGridCommunityDisplayName(item) }}</span>
        <span class="mini-cell__address-row">
          <span class="mini-cell__address">{{ formatGridAddress(item.ipAddress) }}</span>
          <span
            v-if="item.connectivityStatus"
            :class="['mini-cell__connectivity', connectivityMeta(item.connectivityStatus).className]"
            :aria-label="connectivityMeta(item.connectivityStatus).label"
          >{{ connectivityMeta(item.connectivityStatus).label }}</span>
        </span>
        <span v-if="displayMode === 'detailed'" class="mini-cell__meta">
          <span v-if="getGridTargetTypeLabel(item)" class="mini-cell__tag is-type">{{ getGridTargetTypeLabel(item) }}</span>
          <span v-if="getGridManufacturer(item)" class="mini-cell__tag is-brand">{{ getGridManufacturer(item) }}</span>
        </span>
      </button>
    </div>

    <div class="grid-window">
      <span>当前显示 {{ gridPage.rangeStartIp || '-' }} - {{ gridPage.rangeEndIp || '-' }}</span>
      <el-pagination
        v-if="gridSummary.total > gridPage.pageSize"
        small
        background
        layout="total, prev, pager, next, jumper"
        :total="Number(gridSummary.total || 0)"
        :page-size="gridPage.pageSize"
        :current-page="gridPage.pageNum"
        @current-change="$emit('change-grid-page', $event)"
      />
    </div>

    <section class="sheet-section">
      <div class="sheet-head">
        <div>
          <div class="sheet-title">
            <h4>填写IP对应信息</h4>
            <el-tag size="small" :type="isEditing ? 'warning' : 'info'" effect="plain">{{ editModeLabel }}</el-tag>
          </div>
          <span class="sheet-subtitle">{{ expanded ? '显示全部字段' : '显示基础字段' }} / 已选 {{ selectedIpCount }} / 待保存 {{ sheetChangedCount }}</span>
        </div>
        <div class="sheet-actions">
          <el-button
            type="primary"
            plain
            icon="Edit"
            :disabled="isEditing || sheetRows.length === 0"
            @click="startEditing"
            v-hasPermi="['ipam:address:allocate']"
          >{{ editButtonLabel }}</el-button>
          <el-button :icon="expanded ? 'ArrowUp' : 'ArrowDown'" @click="expanded = !expanded">{{ expanded ? '收起扩展' : '扩展字段' }}</el-button>
          <el-button
            type="primary"
            icon="Check"
            :disabled="!editReady || sheetErrorCount > 0 || sheetChangedCount === 0"
            :loading="loading.submit"
            @click="$emit('submit')"
            v-hasPermi="['ipam:address:allocate']"
          >保存 {{ sheetChangedCount }}</el-button>
        </div>
      </div>

      <datalist :id="manufacturerListId">
        <option v-for="option in manufacturerOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
      </datalist>

    <el-table
      v-if="sheetRows.length"
      ref="sheetTableRef"
      :key="tableStructureKey"
      v-loading="loading.grid"
      :data="sheetRows"
      row-key="ipAddress"
      :height="tableHeight"
      :fit="true"
      border
      :class="['sheet-table', expanded ? 'is-expanded' : 'is-basic', isEditing ? 'is-editing' : 'is-viewing']"
      :row-class-name="getSheetRowClassName"
    >
      <el-table-column label="现场IP" prop="ipAddress" width="132" fixed>
        <template #default="scope">
          <span class="mono">{{ scope.row.ipAddress }}</span>
          <el-tag v-if="scope.row.reservedReason" size="small" type="warning">{{ scope.row.reservedReason }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="116" fixed>
        <template #default="scope">
          <select
            v-if="isEditing"
            v-model="scope.row.status"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 状态`"
            @change="$emit('sheet-status-change', scope.row)"
          >
            <option v-for="option in statusOptions" :key="option.value" :value="option.value" :disabled="option.disabled">{{ option.label }}</option>
          </select>
          <el-tag v-else size="small" :type="getStatusTagType(scope.row.status)" effect="plain">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="subjectNameLabel" min-width="150">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.communityName"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            placeholder="必填"
            :aria-label="`${scope.row.ipAddress} ${subjectNameLabel}`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.communityName }]" :title="getDisplayTitle(scope.row.communityName)">{{ getDisplayValue(scope.row.communityName) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="showInternalIpField" label="小区内网IP" min-width="145">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.internalIpAddress"
            class="sheet-native-control mono"
            :disabled="scope.row._readonly"
            inputmode="decimal"
            spellcheck="false"
            :aria-label="`${scope.row.ipAddress} 小区内网IP`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', 'mono', { 'is-empty': !scope.row.internalIpAddress }]" :title="getDisplayTitle(scope.row.internalIpAddress)">{{ getDisplayValue(scope.row.internalIpAddress) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="设备类别" min-width="142">
        <template #default="scope">
          <select
            v-if="isEditing"
            v-model="scope.row.targetType"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 设备类别`"
            @change="$emit('mark-row-dirty', scope.row, true)"
          >
            <option :value="null">请选择</option>
            <option v-for="option in targetTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.targetType }]" :title="getDisplayTitle(getTargetTypeDisplayLabel(scope.row.targetType))">{{ getDisplayValue(getTargetTypeDisplayLabel(scope.row.targetType)) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="设备名称" min-width="160">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.targetName"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 设备名称`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.targetName }]" :title="getDisplayTitle(scope.row.targetName)">{{ getDisplayValue(scope.row.targetName) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="品牌" min-width="128">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.manufacturer"
            class="sheet-native-control"
            :list="manufacturerListId"
            :disabled="scope.row._readonly"
            placeholder="请选择或输入"
            :aria-label="`${scope.row.ipAddress} 品牌`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.manufacturer }]" :title="getDisplayTitle(scope.row.manufacturer)">{{ getDisplayValue(scope.row.manufacturer) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用户名" min-width="130">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.loginUsername"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            autocomplete="off"
            spellcheck="false"
            :aria-label="`${scope.row.ipAddress} 用户名`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.loginUsername }]" :title="getDisplayTitle(scope.row.loginUsername)">{{ getDisplayValue(scope.row.loginUsername) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="密码" min-width="140">
        <template #default="scope">
          <div v-if="isEditing" class="sheet-native-password">
            <input
              v-model="scope.row.loginPassword"
              class="sheet-native-control"
              :type="isPasswordVisible(scope.row) ? 'text' : 'password'"
              :disabled="scope.row._readonly"
              autocomplete="new-password"
              spellcheck="false"
              :placeholder="scope.row.credentialConfigured && !scope.row.loginPassword ? '留空则不修改' : ''"
              :aria-label="`${scope.row.ipAddress} 密码`"
              @input="$emit('mark-row-dirty', scope.row, true)"
            />
            <el-button
              v-if="scope.row.loginPassword"
              class="password-toggle"
              link
              type="primary"
              :icon="isPasswordVisible(scope.row) ? 'Hide' : 'View'"
              :title="isPasswordVisible(scope.row) ? '隐藏密码' : '查看密码'"
              :aria-label="isPasswordVisible(scope.row) ? '隐藏密码' : '查看密码'"
              :aria-pressed="isPasswordVisible(scope.row)"
              @click.stop="togglePasswordVisibility(scope.row)"
            />
          </div>
          <div v-else class="password-display">
            <span :class="['sheet-display-value', { 'is-empty': !scope.row.credentialConfigured && !scope.row._revealedPassword }]">{{ getPasswordDisplayValue(scope.row) }}</span>
            <el-button
              v-if="scope.row._revealedPassword || scope.row.credentialConfigured"
              class="password-toggle"
              link
              type="primary"
              :loading="scope.row._credentialLoading"
              :icon="isPasswordVisible(scope.row) ? 'Hide' : 'View'"
              :title="isPasswordVisible(scope.row) ? '隐藏密码' : '查看密码'"
              :aria-label="isPasswordVisible(scope.row) ? '隐藏密码' : '查看密码'"
              :aria-pressed="isPasswordVisible(scope.row)"
              @click.stop="togglePasswordVisibility(scope.row)"
              v-hasPermi="['ipam:credential:view']"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="映射地址" min-width="145">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.mappingAddress"
            class="sheet-native-control mono"
            :disabled="scope.row._readonly"
            inputmode="decimal"
            spellcheck="false"
            :aria-label="`${scope.row.ipAddress} 映射地址`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', 'mono', { 'is-empty': !scope.row.mappingAddress }]" :title="getDisplayTitle(scope.row.mappingAddress)">{{ getDisplayValue(scope.row.mappingAddress) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="映射端口" min-width="110">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.mappingPort"
            class="sheet-native-control mono"
            :disabled="scope.row._readonly"
            inputmode="numeric"
            spellcheck="false"
            :aria-label="`${scope.row.ipAddress} 映射端口`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', 'mono', { 'is-empty': !scope.row.mappingPort }]" :title="getDisplayTitle(scope.row.mappingPort)">{{ getDisplayValue(scope.row.mappingPort) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="映射说明" min-width="180">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.mappingDescription"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 映射说明`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.mappingDescription }]" :title="getDisplayTitle(scope.row.mappingDescription)">{{ getDisplayValue(scope.row.mappingDescription) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="接入单位" min-width="135">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.accessUnit"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 接入单位`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.accessUnit }]" :title="getDisplayTitle(scope.row.accessUnit)">{{ getDisplayValue(scope.row.accessUnit) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="联系人" min-width="105">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.ownerName"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 联系人`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.ownerName }]" :title="getDisplayTitle(scope.row.ownerName)">{{ getDisplayValue(scope.row.ownerName) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="电话" min-width="130">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.ownerPhone"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            inputmode="tel"
            spellcheck="false"
            :aria-label="`${scope.row.ipAddress} 电话`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.ownerPhone }]" :title="getDisplayTitle(scope.row.ownerPhone)">{{ getDisplayValue(scope.row.ownerPhone) }}</span>
        </template>
      </el-table-column>
      <el-table-column v-if="expanded" label="备注" min-width="180">
        <template #default="scope">
          <input
            v-if="isEditing"
            v-model="scope.row.remark"
            class="sheet-native-control"
            :disabled="scope.row._readonly"
            :aria-label="`${scope.row.ipAddress} 备注`"
            @input="$emit('mark-row-dirty', scope.row, true)"
          />
          <span v-else :class="['sheet-display-value', { 'is-empty': !scope.row.remark }]" :title="getDisplayTitle(scope.row.remark)">{{ getDisplayValue(scope.row.remark) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="校验" width="96" fixed="right">
        <template #default="scope">
          <el-tag v-if="scope.row._error" size="small" type="danger">{{ scope.row._error }}</el-tag>
          <el-tag v-else-if="scope.row._dirty" size="small" type="success">待保存</el-tag>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="176" fixed="right" align="center">
        <template #default="scope">
          <div class="row-actions">
            <el-button
              v-if="isEditing"
              link
              type="primary"
              icon="Bottom"
              :disabled="!editReady || scope.row._readonly || scope.$index >= sheetRows.length - 1"
              @click="$emit('generate-downward', scope.row)"
              v-hasPermi="['ipam:address:allocate']"
            >向下生成</el-button>
            <el-button link type="danger" icon="Close" @click="$emit('remove-selected-ip', scope.row.ipAddress)">移出</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
      <el-empty v-else class="sheet-empty" :image-size="96" description="请在上方网格选择IP" />
    </section>
  </div>
</template>

<script setup name="IpamConfigWorkspace">
import { computed, nextTick, ref, useId, watch } from 'vue'

const expanded = ref(false)
const isEditing = ref(false)
const visiblePasswordIps = ref(new Set())
const sheetTableRef = ref(null)
const manufacturerListId = `ipam-manufacturer-${useId()}`
const maxGridCommunityNameLength = 14
const gridDisplayModeOptions = [
  { label: '简约', value: 'compact' },
  { label: '详细', value: 'detailed' }
]
const gridTargetTypeShortLabels = {
  RECORDER: '录像',
  CAMERA: '摄像',
  NVR: 'NVR',
  CVR: 'CVR',
  PLATFORM: '平台',
  STORAGE_SERVER: '存储',
  DECODER: '解码',
  ACCESS_CONTROL: '门禁',
  FACE_DEVICE: '人脸',
  BARRIER_GATE: '道闸',
  IAC: 'IAC',
  MAPPING_DEVICE: '映射',
  OTHER: '其他'
}

const props = defineProps({
  fullscreen: { type: Boolean, default: false },
  selectedSegment: { type: Object, required: true },
  displayMode: { type: String, default: 'compact' },
  statusOptions: { type: Array, default: () => [] },
  loading: { type: Object, required: true },
  gridSummary: { type: Object, required: true },
  gridPage: { type: Object, required: true },
  addressRows: { type: Array, default: () => [] },
  selectedSheetIp: { type: String, default: null },
  scanJob: { type: Object, default: null },
  selectedIpCount: { type: Number, default: 0 },
  maxConfigSelection: { type: Number, default: 256 },
  sheetChangedCount: { type: Number, default: 0 },
  sheetErrorCount: { type: Number, default: 0 },
  sheetRows: { type: Array, default: () => [] },
  subjectNameLabel: { type: String, default: '小区名称' },
  showInternalIpField: { type: Boolean, default: true },
  targetTypeOptions: { type: Array, default: () => [] },
  manufacturerOptions: { type: Array, default: () => [] },
  getStatusMeta: { type: Function, required: true },
  isGridSelected: { type: Function, required: true },
  getSheetRowClassName: { type: Function, required: true },
  loadCredential: { type: Function, required: true }
})

const emit = defineEmits([
  'toggle-fullscreen',
  'update:display-mode',
  'scan-network',
  'open-paste',
  'generate-downward',
  'clear-selected',
  'reset',
  'submit',
  'start-grid-selection',
  'extend-grid-selection',
  'toggle-grid-selection',
  'change-grid-page',
  'sheet-status-change',
  'mark-row-dirty',
  'remove-selected-ip'
])

function statusMeta(status) {
  return props.getStatusMeta(status)
}

function formatGridAddress(ipAddress) {
  const parts = String(ipAddress || '').split('.')
  if (parts.length !== 4) return ipAddress || '-'
  return Number(props.selectedSegment.prefixLength) < 24 ? parts.slice(2).join('.') : `.${parts[3]}`
}

const sheetRowMap = computed(() => new Map(
  props.sheetRows.map((row) => [row.ipAddress, row])
))
const targetTypeLabelMap = computed(() => new Map(
  props.targetTypeOptions.map((option) => [option.value, option.label])
))
const scanButtonLabel = computed(() => {
  if (!props.scanJob || !['QUEUED', 'RUNNING'].includes(props.scanJob.scanStatus)) return '扫描网段'
  if (props.scanJob.scanStatus === 'QUEUED') return '等待扫描'
  return `扫描 ${props.scanJob.completedCount || 0}/${props.scanJob.totalCount || 0}`
})
const editReady = computed(() => isEditing.value)
const editModeLabel = computed(() => isEditing.value ? '编辑模式' : '查看模式')
const editButtonLabel = computed(() => isEditing.value ? '编辑中' : '开始编辑')

function getGridField(item, field) {
  const sheetRow = sheetRowMap.value.get(item.ipAddress)
  return sheetRow ? (sheetRow[field] || '') : (item[field] || '')
}

function getGridCommunityName(item) {
  return getGridField(item, 'communityName')
}

function getGridCommunityDisplayName(item) {
  const communityName = getGridCommunityName(item)
  const characters = Array.from(communityName)
  if (characters.length <= maxGridCommunityNameLength) return communityName
  return `${characters.slice(0, maxGridCommunityNameLength).join('')}…`
}

function getGridTargetTypeLabel(item) {
  const targetType = getGridField(item, 'targetType')
  if (!targetType) return ''
  if (gridTargetTypeShortLabels[targetType]) return gridTargetTypeShortLabels[targetType]
  const label = targetTypeLabelMap.value.get(targetType) || targetType
  return Array.from(label).slice(0, 3).join('')
}

function getGridManufacturer(item) {
  return getGridField(item, 'manufacturer')
}

function getGridCellTitle(item) {
  const communityName = getGridCommunityName(item)
  const targetType = getGridTargetTypeLabel(item)
  const manufacturer = getGridManufacturer(item)
  const connectivity = item.connectivityStatus
    ? `${connectivityMeta(item.connectivityStatus).label}${item.scanResponseTimeMs == null ? '' : ` ${item.scanResponseTimeMs}ms`}`
    : ''
  const lastScanTime = item.lastScanTime ? `检测 ${item.lastScanTime}` : ''
  return [communityName, targetType, manufacturer, item.ipAddress, connectivity, lastScanTime].filter(Boolean).join(' / ')
}

function getGridAriaLabel(item) {
  const selection = props.isGridSelected(item.ipAddress) ? '已选择' : '未选择'
  return [item.ipAddress, getGridCommunityName(item), getGridTargetTypeLabel(item), getGridManufacturer(item), statusMeta(item.status).label, selection]
    .filter(Boolean)
    .join('，')
}

function isGridAddressDisabled(item) {
  return Boolean(item?.boundaryAddress) || item?.reservedReason === '网关'
}

function handleGridKeyboardSelect(item, event) {
  if (event.detail !== 0 || isGridAddressDisabled(item)) return
  emit('toggle-grid-selection', item)
}

function connectivityMeta(status) {
  if (status === 'ONLINE') return { label: '在线', className: 'is-online' }
  if (status === 'OFFLINE') return { label: '离线', className: 'is-offline' }
  return { label: '异常', className: 'is-unknown' }
}

function startEditing() {
  if (!props.sheetRows.length) return
  visiblePasswordIps.value = new Set()
  props.sheetRows.forEach((row) => {
    row._revealedPassword = null
  })
  isEditing.value = true
}

function getDisplayValue(value) {
  return value == null || String(value).trim() === '' ? '-' : String(value)
}

function getDisplayTitle(value) {
  return value == null ? '' : String(value)
}

function isPasswordVisible(row) {
  return visiblePasswordIps.value.has(row.ipAddress)
}

async function togglePasswordVisibility(row) {
  const nextVisibleIps = new Set(visiblePasswordIps.value)
  if (nextVisibleIps.has(row.ipAddress)) {
    nextVisibleIps.delete(row.ipAddress)
  } else {
    if (!isEditing.value && !row._revealedPassword && row.credentialConfigured && row.addressId) {
      try {
        await props.loadCredential(row)
      } catch {
        return
      }
    }
    const password = isEditing.value ? row.loginPassword : row._revealedPassword
    if (!password) return
    nextVisibleIps.add(row.ipAddress)
  }
  visiblePasswordIps.value = nextVisibleIps
}

function getPasswordDisplayValue(row) {
  const value = row._revealedPassword
  if (value == null || String(value).trim() === '') return row.credentialConfigured ? '******' : '-'
  return isPasswordVisible(row) ? String(value) : '******'
}

function getTargetTypeDisplayLabel(value) {
  if (!value) return ''
  return targetTypeLabelMap.value.get(value) || value
}

function getStatusLabel(status) {
  return props.statusOptions.find((option) => option.value === status)?.label || status || '-'
}

function getStatusTagType(status) {
  if (status === 'RESERVED') return 'warning'
  if (status === 'ALLOCATED') return 'primary'
  if (status === 'ISSUED') return 'success'
  if (status === 'DISABLED') return 'danger'
  return 'info'
}

const tableStructureKey = computed(() => props.showInternalIpField ? 'with-internal' : 'no-internal')

const tableHeight = computed(() => {
  if (props.fullscreen) {
    return expanded.value ? 'max(360px, calc(100vh - 334px))' : 'max(400px, calc(100vh - 318px))'
  }
  return expanded.value ? 'max(420px, calc(100vh - 520px))' : 'max(460px, calc(100vh - 500px))'
})

watch(() => props.selectedSegment.segmentId, () => {
  isEditing.value = false
  visiblePasswordIps.value = new Set()
})

watch(() => props.sheetRows.map((row) => row.ipAddress).join(','), () => {
  visiblePasswordIps.value = new Set()
  if (!props.sheetRows.length) {
    isEditing.value = false
  }
})

watch([expanded, () => props.fullscreen, () => props.displayMode, () => props.showInternalIpField, () => props.sheetRows.length], () => {
  if (!props.sheetRows.length) {
    isEditing.value = false
  }
  nextTick(() => {
    sheetTableRef.value?.doLayout?.()
  })
})
</script>

<style scoped>
.config-panel {
  display: flex;
  flex-direction: column;
  min-height: 560px;
}

.config-panel.is-fullscreen {
  height: calc(100vh - 32px);
  min-height: 0;
}

.config-panel.is-fullscreen.is-detailed-mode {
  height: auto;
  min-height: calc(100vh - 32px);
}

.config-panel.is-fullscreen.is-detailed-mode .sheet-section,
.config-panel.is-fullscreen.is-detailed-mode .sheet-table {
  flex: 0 0 auto;
}

.grid-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.grid-toolbar h3 {
  margin: 0 0 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 18px;
  font-weight: 650;
}

.grid-toolbar span {
  color: #6b7280;
}

.grid-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.grid-mode-toggle {
  flex: 0 0 auto;
}

.grid-mode-toggle :deep(.el-segmented__item) {
  min-width: 52px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.legend-dot {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #4b5563;
}

.legend-dot::before {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  content: '';
}

.summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.summary-strip span {
  padding: 6px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
  color: #374151;
  font-size: 12px;
}

.mini-grid {
  display: grid;
  grid-template-columns: repeat(32, minmax(24px, 1fr));
  gap: 4px;
  margin-bottom: 12px;
  user-select: none;
}

.mini-grid.is-wide-address {
  grid-template-columns: repeat(16, minmax(42px, 1fr));
}

.mini-grid.is-detailed {
  grid-template-columns: repeat(16, minmax(62px, 1fr));
  gap: 4px;
}

.mini-grid.is-detailed.is-wide-address {
  grid-template-columns: repeat(12, minmax(72px, 1fr));
}

.mini-cell {
  position: relative;
  min-width: 0;
  height: 26px;
  padding: 0;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  line-height: 24px;
  cursor: pointer;
}

.mini-cell__address {
  display: block;
}

.mini-grid.is-detailed .mini-cell {
  display: flex;
  height: auto;
  min-height: 58px;
  padding: 2px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0;
  line-height: 1.2;
}

.mini-cell__community {
  display: block;
  width: 100%;
  min-height: 24px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 9px;
  font-weight: 600;
  line-height: 12px;
  overflow-wrap: anywhere;
  text-align: center;
  white-space: normal;
  word-break: break-all;
}

.mini-cell__address-row {
  pointer-events: none;
}

.mini-cell__connectivity {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 7px;
  height: 7px;
  overflow: hidden;
  border-radius: 50%;
  font-size: 0;
  line-height: 0;
  pointer-events: none;
}

.mini-grid.is-detailed .mini-cell__connectivity {
  position: static;
  flex: 0 0 auto;
  width: auto;
  height: 14px;
  padding: 0 3px;
  border: 1px solid currentColor;
  border-radius: 3px;
  background: rgb(255 255 255 / 92%);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 9px;
  font-weight: 600;
  line-height: 12px;
}

.mini-cell__connectivity.is-online {
  background: #16a34a;
  color: #15803d;
}

.mini-cell__connectivity.is-offline {
  background: #94a3b8;
  color: #64748b;
}

.mini-cell__connectivity.is-unknown {
  background: #f59e0b;
  color: #b45309;
}

.mini-grid.is-detailed .mini-cell__connectivity.is-online,
.mini-grid.is-detailed .mini-cell__connectivity.is-offline,
.mini-grid.is-detailed .mini-cell__connectivity.is-unknown {
  background: rgb(255 255 255 / 94%);
}

.mini-grid.is-detailed .mini-cell__address {
  line-height: 15px;
}

.mini-grid.is-detailed .mini-cell__address-row {
  display: flex;
  width: 100%;
  min-height: 15px;
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.mini-cell__community,
.mini-cell__address,
.mini-cell__meta {
  pointer-events: none;
}

.mini-cell__meta {
  display: flex;
  width: 100%;
  min-height: 15px;
  align-items: center;
  align-content: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 2px;
  overflow: visible;
}

.mini-cell__tag {
  display: inline-flex;
  max-width: 100%;
  min-width: 0;
  padding: 0 2px;
  align-items: center;
  justify-content: center;
  overflow: visible;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  background: rgb(255 255 255 / 78%);
  color: #475569;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 9px;
  font-weight: 600;
  line-height: 13px;
  overflow-wrap: anywhere;
  text-align: center;
  white-space: normal;
}

.mini-cell__tag.is-type {
  border-color: #bfdbfe;
  background: rgb(239 246 255 / 88%);
  color: #1d4ed8;
}

.mini-cell__tag.is-brand {
  border-color: #a7f3d0;
  background: rgb(236 253 245 / 88%);
  color: #047857;
}

.grid-window {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  margin: -4px 0 12px;
  color: #64748b;
  font-size: 12px;
}

.mini-cell.selected {
  border-color: #111827;
  box-shadow: inset 0 0 0 1px #111827;
}

.mini-cell.active {
  outline: 2px solid #2563eb;
  outline-offset: 1px;
}

.sheet-section {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
  background: #f8fafc;
}

.sheet-head h4 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 650;
}

.sheet-title {
  display: flex;
  min-height: 24px;
  align-items: center;
  gap: 8px;
  margin-bottom: 3px;
}

.sheet-subtitle {
  color: #6b7280;
  font-size: 12px;
}

.sheet-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.sheet-table {
  flex: 1 1 auto;
  width: 100%;
}

.sheet-table.is-basic {
  min-width: 100%;
}

.sheet-table :deep(.el-table__cell) {
  padding: 5px 0;
}

.sheet-table.is-viewing :deep(.el-table__cell) {
  padding: 7px 0;
}

.sheet-native-control {
  box-sizing: border-box;
  width: 100%;
  height: 24px;
  min-width: 0;
  padding: 0 7px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: var(--el-border-radius-base, 4px);
  outline: none;
  background: var(--el-fill-color-blank, #fff);
  color: var(--el-text-color-regular, #606266);
  font-family: inherit;
  font-size: 12px;
  line-height: 22px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

select.sheet-native-control {
  padding-right: 3px;
  cursor: pointer;
}

.sheet-native-control:hover:not(:disabled) {
  border-color: var(--el-border-color-hover, #c0c4cc);
}

.sheet-native-control:focus-visible {
  border-color: var(--el-color-primary, #409eff);
  box-shadow: 0 0 0 1px var(--el-color-primary, #409eff) inset;
}

.sheet-native-control:disabled {
  border-color: var(--el-disabled-border-color, #e4e7ed);
  background: var(--el-disabled-bg-color, #f5f7fa);
  color: var(--el-disabled-text-color, #a8abb2);
  cursor: not-allowed;
}

.sheet-native-password {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
}

.sheet-native-password .sheet-native-control {
  flex: 1 1 auto;
}

.sheet-display-value {
  display: block;
  min-height: 24px;
  color: #374151;
  line-height: 24px;
  overflow-wrap: anywhere;
  white-space: normal;
}

.sheet-display-value.is-empty {
  color: #9ca3af;
}

.password-display {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
}

.password-display .sheet-display-value {
  min-width: 0;
  flex: 0 1 auto;
}

.password-toggle {
  flex: 0 0 auto;
}

.sheet-table :deep(.sheet-row-readonly td) {
  background: #fffbeb;
}

.sheet-table :deep(.sheet-row-dirty td) {
  background: #f0fdf4;
}

.sheet-table :deep(.sheet-row-error td) {
  background: #fef2f2;
}

.sheet-table :deep(.sheet-row-selected td) {
  background: #eff6ff;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.muted {
  color: #9ca3af;
}

.sheet-empty {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  border: 1px solid #e5e7eb;
  border-radius: 0 0 8px 8px;
}

.mini-cell.is-free {
  background: #f8fafc;
  border-color: #d1d5db;
  color: #64748b;
}

.mini-cell.is-reserved {
  background: #fffbeb;
  border-color: #f59e0b;
  color: #92400e;
}

.mini-cell.is-allocated {
  background: #eff6ff;
  border-color: #3b82f6;
  color: #1d4ed8;
}

.mini-cell.is-issued {
  background: #ecfdf5;
  border-color: #10b981;
  color: #047857;
}

.mini-cell.is-disabled {
  background: #fef2f2;
  border-color: #ef4444;
  color: #b91c1c;
}

.legend-dot.is-free::before {
  background: #94a3b8;
}

.legend-dot.is-reserved::before {
  background: #f59e0b;
}

.legend-dot.is-allocated::before {
  background: #3b82f6;
}

.legend-dot.is-issued::before {
  background: #10b981;
}

.legend-dot.is-disabled::before {
  background: #ef4444;
}

.is-boundary,
.is-gateway {
  border-style: dashed;
  cursor: not-allowed;
}

@media (max-width: 1440px) {
  .mini-grid.is-detailed {
    grid-template-columns: repeat(14, minmax(60px, 1fr));
  }

  .mini-grid.is-detailed.is-wide-address {
    grid-template-columns: repeat(10, minmax(68px, 1fr));
  }
}

@media (max-width: 1280px) {
  .mini-grid {
    grid-template-columns: repeat(16, minmax(26px, 1fr));
  }

  .mini-grid.is-wide-address {
    grid-template-columns: repeat(12, minmax(42px, 1fr));
  }

}

@media (max-width: 720px) {
  .grid-toolbar,
  .grid-actions,
  .sheet-head,
  .sheet-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .mini-grid {
    grid-template-columns: repeat(8, minmax(28px, 1fr));
  }

  .mini-grid.is-wide-address {
    grid-template-columns: repeat(4, minmax(42px, 1fr));
  }

  .mini-grid.is-detailed,
  .mini-grid.is-detailed.is-wide-address {
    grid-template-columns: repeat(4, minmax(64px, 1fr));
  }

  .grid-window {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
