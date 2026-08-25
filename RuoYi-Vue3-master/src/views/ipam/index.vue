<template>
  <div class="app-container ipam-page">
    <div class="ipam-head">
      <div>
        <h2>IP分配配置</h2>
        <div class="ipam-head__meta">
          <el-tag size="small" type="info">本机政务网服务器：2.57.0.250</el-tag>
          <el-tag v-if="selectedNetwork" size="small" type="success">{{ formatNetworkRange(selectedNetwork) }}</el-tag>
          <el-tag v-if="selectedSegment" size="small" type="warning">网关：{{ selectedSegment.gatewayIp || '待配置' }}</el-tag>
          <el-tag v-if="selectedSegment" size="small" type="info">掩码：{{ selectedSegment.subnetMask || '待配置' }}</el-tag>
        </div>
      </div>
      <div class="ipam-head__actions">
        <el-button icon="Refresh" @click="loadNetworks">刷新</el-button>
        <el-button type="primary" icon="Plus" @click="openAddNetwork" v-hasPermi="['ipam:network:add']">新增网段</el-button>
      </div>
    </div>

    <div class="scenario-bar">
      <div class="scenario-bar__label">
        <strong>使用场景</strong>
        <el-tag size="small" type="info" effect="plain">全部网段</el-tag>
      </div>
      <el-segmented
        :model-value="globalScenarioType"
        :options="scenarioTypeOptions"
        :disabled="loading.scenario || !canEditNetwork"
        @change="handleScenarioChange"
      />
    </div>

    <div class="ipam-shell">
      <aside class="network-pane">
        <div class="network-search">
          <div class="network-search__row">
            <el-input
              v-model="networkQuery.keyword"
              prefix-icon="Search"
              clearable
              :placeholder="`搜索派出所、网段、${subjectNameLabel}或具体IP`"
              @input="handleNetworkSearchInput"
              @clear="handleNetworkQuery"
              @keyup.enter="handleNetworkQuery"
            />
            <el-tooltip content="重置检索" placement="top">
              <el-button icon="Refresh" aria-label="重置检索" :disabled="!networkQuery.keyword" @click="resetNetworkQuery" />
            </el-tooltip>
          </div>
        </div>

        <div class="network-list__head">
          <strong>网段列表</strong>
          <span>{{ networkTree.length }} 组 · {{ networkTotal }} 个网段</span>
        </div>

        <IpamNetworkTree
          :tree-data="networkTree"
          :selected-network-id="selectedNetworkId"
          :keyword="networkQuery.keyword || ''"
          :loading="loading.network"
          @select="selectNetwork"
          @edit="openEditNetwork"
          @remove="removeNetwork"
        />
      </aside>

      <main class="ipam-main">
        <nav class="workflow-nav" aria-label="IP分配配置流程">
          <button
            v-for="(step, index) in workflowSteps"
            :key="step.name"
            type="button"
            class="workflow-step"
            :class="{ active: activeTab === step.name }"
            :disabled="step.requiresNetwork && !canConfigureNetwork"
            @click="switchWorkflowStep(step)"
          >
            <span class="workflow-step__index">{{ index + 1 }}</span>
            <span class="workflow-step__copy">
              <strong>{{ step.title }}</strong>
              <em>{{ step.desc }}</em>
            </span>
          </button>
        </nav>

        <el-tabs v-model="activeTab" class="ipam-tabs">
          <el-tab-pane label="网段管理" name="network" lazy>
            <section v-if="selectedNetwork" class="network-detail">
              <div class="network-detail__head">
                <div>
                  <div class="network-detail__title">
                    <h3>{{ selectedNetwork.networkName }}</h3>
                    <el-tag type="primary" effect="plain">{{ selectedNetwork.policeStationName || '未分类' }}</el-tag>
                    <el-tag :type="selectedNetwork.status === '0' ? 'success' : 'info'">{{ selectedNetwork.status === '0' ? '启用' : '停用' }}</el-tag>
                  </div>
                  <p>{{ formatNetworkRange(selectedNetwork) }}</p>
                </div>
                <div class="network-detail__actions">
                  <el-button type="primary" icon="Grid" :disabled="!canConfigureNetwork" @click="openNetworkConfig" v-hasPermi="['ipam:address:list']">配置IP</el-button>
                </div>
              </div>

              <el-alert
                v-if="!selectedSegment"
                title="该历史网段不是单层地址池结构，需拆分为独立网段后才能继续配置IP"
                type="warning"
                show-icon
                :closable="false"
              />
              <el-alert
                v-else-if="!selectedSegment.gatewayIp || !selectedSegment.subnetMask"
                title="该网段尚未配置网关IP和子网掩码，请先编辑网段；保存后系统会自动计算地址范围并保留网关"
                type="warning"
                show-icon
                :closable="false"
              />

              <div class="network-facts">
                <div><span>网关IP</span><strong>{{ selectedNetwork.gatewayIp || '待配置' }}</strong></div>
                <div><span>子网掩码</span><strong>{{ selectedNetwork.subnetMask || '待配置' }}</strong></div>
                <div><span>网络地址</span><strong>{{ selectedNetwork.startIp || '待计算' }}</strong></div>
                <div><span>广播地址</span><strong>{{ selectedNetwork.endIp || '待计算' }}</strong></div>
              </div>

              <div class="network-stats">
                <div><span>地址总数</span><strong>{{ selectedNetwork.totalCount || 0 }}</strong></div>
                <div><span>可分配容量</span><strong>{{ getAssignableCapacity(selectedNetwork) }}</strong></div>
                <div><span>空闲</span><strong>{{ selectedNetwork.freeCount || 0 }}</strong></div>
                <div><span>保留</span><strong>{{ selectedNetwork.reservedCount || 0 }}</strong></div>
                <div><span>已占用</span><strong>{{ selectedNetwork.allocatedCount || 0 }}</strong></div>
                <div><span>已下发</span><strong>{{ selectedNetwork.issuedCount || 0 }}</strong></div>
                <div><span>禁用</span><strong>{{ selectedNetwork.disabledCount || 0 }}</strong></div>
              </div>
              <div class="network-usage">
                <span>IP占用率 {{ getUsagePercent(selectedNetwork) }}%</span>
                <el-progress :percentage="getUsagePercent(selectedNetwork)" :stroke-width="10" />
              </div>
            </section>
            <el-empty v-else :image-size="96" description="请先选择网段" />
          </el-tab-pane>

          <el-tab-pane label="IP配置" name="config" lazy>
            <IpamConfigWorkspace
              v-if="selectedSegment && !configFullscreen"
              :selected-segment="selectedSegment"
              :display-mode="gridDisplayMode"
              :status-options="statusOptions"
              :loading="loading"
              :grid-summary="gridSummary"
              :grid-page="gridPage"
              :address-rows="addressRows"
              :selected-sheet-ip="selectedSheetIp"
              :scan-job="scanJob"
              :selected-ip-count="selectedIpCount"
              :max-config-selection="maxConfigSelection"
              :sheet-changed-count="sheetChangedCount"
              :sheet-error-count="sheetErrorCount"
              :sheet-rows="sheetRows"
              :target-type-options="targetTypeOptions"
              :manufacturer-options="manufacturerOptions"
              :subject-name-label="subjectNameLabel"
              :show-internal-ip-field="showInternalIpField"
              :get-status-meta="getStatusMeta"
              :is-grid-selected="isGridSelected"
              :get-sheet-row-class-name="getSheetRowClassName"
              :load-credential="loadAddressCredential"
              @toggle-fullscreen="openConfigFullscreen"
              @update:display-mode="setGridDisplayMode"
              @scan-network="startNetworkScan"
              @open-paste="openPasteDialog"
              @generate-downward="generateDownwardRows"
              @clear-selected="clearSelectedIps"
              @reset="resetConfigSheet"
              @submit="submitConfigSheet"
              @start-grid-selection="startGridSelection"
              @extend-grid-selection="extendGridSelection"
              @toggle-grid-selection="toggleGridSelection"
              @change-grid-page="changeGridPage"
              @sheet-status-change="handleSheetStatusChange"
              @mark-row-dirty="markSheetRowDirty"
              @remove-selected-ip="removeSelectedIp"
            />
            <el-empty v-else-if="!selectedSegment" :image-size="96" description="请选择网段" />
          </el-tab-pane>

          <el-tab-pane label="地址台账" name="ledger" lazy>
            <div class="ledger-panel">
              <el-form :model="ledgerQuery" :inline="true" v-show="showSearch" class="compact-filter">
                <el-form-item label="状态">
                  <el-select v-model="ledgerQuery.status" placeholder="全部状态" clearable style="width: 130px">
                    <el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="subjectNameLabel">
                  <el-input v-model="ledgerQuery.communityName" :placeholder="subjectNameLabel" clearable @keyup.enter="handleLedgerQuery" />
                </el-form-item>
                <el-form-item v-if="showInternalIpField" label="内网IP">
                  <el-input v-model="ledgerQuery.internalIpAddress" placeholder="192.168.x.x" clearable @keyup.enter="handleLedgerQuery" />
                </el-form-item>
                <el-form-item label="设备类别">
                  <el-select v-model="ledgerQuery.targetType" placeholder="全部类型" clearable style="width: 145px">
                    <el-option v-for="option in targetTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="品牌">
                  <el-select v-model="ledgerQuery.manufacturer" placeholder="全部品牌" clearable style="width: 130px">
                    <el-option v-for="option in manufacturerOptions" :key="option.value" :label="option.label" :value="option.value" />
                  </el-select>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" icon="Search" @click="handleLedgerQuery">搜索</el-button>
                  <el-button icon="Refresh" @click="resetLedgerQuery">重置</el-button>
                </el-form-item>
              </el-form>

              <div class="ledger-actions">
                <el-button type="warning" plain icon="Download" @click="exportLedger" v-hasPermi="['ipam:address:export']">导出台账</el-button>
                <right-toolbar v-model:showSearch="showSearch" @queryTable="loadLedger" />
              </div>

              <el-table v-loading="loading.ledger" :data="ledgerList" border>
                <el-table-column label="现场IP" prop="ipAddress" min-width="130" />
                <el-table-column v-if="showInternalIpField" label="内网IP" prop="internalIpAddress" min-width="140" show-overflow-tooltip />
                <el-table-column label="网段范围" min-width="210">
                  <template #default="scope">{{ getNetworkRangeLabel(scope.row.networkId) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="95">
                  <template #default="scope">
                    <el-tag :type="getStatusMeta(scope.row.status).type">{{ getStatusMeta(scope.row.status).label }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="subjectNameLabel" prop="communityName" min-width="140" show-overflow-tooltip />
                <el-table-column label="设备类别" min-width="120">
                  <template #default="scope">{{ getTargetTypeLabel(scope.row.targetType) }}</template>
                </el-table-column>
                <el-table-column label="设备名称" prop="targetName" min-width="150" show-overflow-tooltip />
                <el-table-column label="品牌" prop="manufacturer" min-width="100" />
                <el-table-column label="接入单位" prop="accessUnit" min-width="110" show-overflow-tooltip />
                <el-table-column label="责任人" prop="ownerName" min-width="100" />
                <el-table-column label="映射地址" prop="mappingAddress" min-width="130" show-overflow-tooltip />
                <el-table-column label="映射端口" prop="mappingPort" min-width="100" />
                <el-table-column label="映射说明" prop="mappingDescription" min-width="150" show-overflow-tooltip />
              </el-table>
              <pagination v-show="ledgerTotal > 0" :total="ledgerTotal" v-model:page="ledgerQuery.pageNum" v-model:limit="ledgerQuery.pageSize" @pagination="loadLedger" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </main>
    </div>

    <el-dialog v-model="networkOpen" :title="networkTitle" width="720px" append-to-body>
      <el-alert title="填写网关IP和子网掩码后，系统自动计算网络地址、广播地址和可分配容量" type="info" show-icon :closable="false" />
      <el-form ref="networkRef" :model="networkForm" :rules="networkRules" label-width="110px" class="guide-form">
        <el-form-item label="所属派出所" prop="policeStationName">
          <el-select
            v-model="networkForm.policeStationName"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="选择已有名称或输入新名称"
            style="width: 100%"
          >
            <el-option v-for="option in stationOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="网段名称" prop="networkName">
          <el-input v-model="networkForm.networkName" placeholder="例如：湖塘街道现场IP" maxlength="100" />
        </el-form-item>
        <el-form-item label="网关IP" prop="gatewayIp">
          <Ipv4Input v-model="networkForm.gatewayIp" label="网关IP" />
        </el-form-item>
        <el-form-item label="子网掩码" prop="subnetMask">
          <Ipv4Input v-model="networkForm.subnetMask" label="子网掩码" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="networkForm.status">
            <el-radio label="0">启用</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <div v-if="networkPreview.valid" class="preview-box">
          <div class="preview-box__head">
            <strong>{{ networkPreview.networkAddress }} - {{ networkPreview.broadcastAddress }}</strong>
            <span>共 {{ networkPreview.totalCount }} 个地址，可分配 {{ networkPreview.assignableCount }} 个</span>
          </div>
          <div class="network-preview-grid">
            <span><small>网络地址</small>{{ networkPreview.networkAddress }}</span>
            <span><small>广播地址</small>{{ networkPreview.broadcastAddress }}</span>
            <span><small>网关保留</small>{{ networkPreview.gatewayIp }}</span>
            <span><small>子网掩码</small>{{ networkPreview.subnetMask }}</span>
            <span><small>系统保留</small>网络地址、广播地址、网关</span>
          </div>
        </div>
        <el-form-item label="备注">
          <el-input v-model="networkForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="networkOpen = false">取 消</el-button>
        <el-button type="primary" :loading="loading.submit" @click="submitNetwork">确 定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pasteOpen" title="粘贴Excel数据" width="760px" append-to-body>
      <el-input
        v-model="pasteText"
        type="textarea"
        :rows="12"
        :placeholder="pastePlaceholder"
      />
      <template #footer>
        <el-button @click="pasteOpen = false">取 消</el-button>
        <el-button type="primary" @click="applyPasteRows">写入表格</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configFullscreen" fullscreen append-to-body destroy-on-close class="ipam-config-fullscreen" :show-close="false">
      <IpamConfigWorkspace
        v-if="selectedSegment"
        fullscreen
        :selected-segment="selectedSegment"
        :display-mode="gridDisplayMode"
        :status-options="statusOptions"
        :loading="loading"
        :grid-summary="gridSummary"
        :grid-page="gridPage"
        :address-rows="addressRows"
        :selected-sheet-ip="selectedSheetIp"
        :scan-job="scanJob"
        :selected-ip-count="selectedIpCount"
        :max-config-selection="maxConfigSelection"
        :sheet-changed-count="sheetChangedCount"
        :sheet-error-count="sheetErrorCount"
        :sheet-rows="sheetRows"
        :target-type-options="targetTypeOptions"
        :manufacturer-options="manufacturerOptions"
        :subject-name-label="subjectNameLabel"
        :show-internal-ip-field="showInternalIpField"
        :get-status-meta="getStatusMeta"
        :is-grid-selected="isGridSelected"
        :get-sheet-row-class-name="getSheetRowClassName"
        :load-credential="loadAddressCredential"
        @toggle-fullscreen="closeConfigFullscreen"
        @update:display-mode="setGridDisplayMode"
        @scan-network="startNetworkScan"
        @open-paste="openPasteDialog"
        @generate-downward="generateDownwardRows"
        @clear-selected="clearSelectedIps"
        @reset="resetConfigSheet"
        @submit="submitConfigSheet"
        @start-grid-selection="startGridSelection"
        @extend-grid-selection="extendGridSelection"
        @toggle-grid-selection="toggleGridSelection"
        @change-grid-page="changeGridPage"
        @sheet-status-change="handleSheetStatusChange"
        @mark-row-dirty="markSheetRowDirty"
        @remove-selected-ip="removeSelectedIp"
      />
    </el-dialog>
  </div>
</template>

<script setup name="IpamConfig">
import { computed } from 'vue'
import { useIpamManager } from './useIpamManager'
import IpamConfigWorkspace from './components/IpamConfigWorkspace.vue'
import IpamNetworkTree from './components/IpamNetworkTree.vue'
import Ipv4Input from './components/Ipv4Input.vue'

const {
  showSearch,
  activeTab,
  loading,
  networkRef,
  networkOpen,
  pasteOpen,
  configFullscreen,
  networkTitle,
  selectedNetworkId,
  selectedNetwork,
  selectedSegment,
  selectedSheetIp,
  scanJob,
  gridDisplayMode,
  selectedIpCount,
  maxConfigSelection,
  networkTree,
  networkTotal,
  stationOptions,
  globalScenarioType,
  addressRows,
  sheetRows,
  sheetChangedCount,
  sheetErrorCount,
  ledgerList,
  ledgerTotal,
  pasteText,
  gridSummary,
  gridPage,
  networkQuery,
  ledgerQuery,
  networkForm,
  statusOptions,
  targetTypeOptions,
  manufacturerOptions,
  scenarioTypeOptions,
  networkRules,
  networkPreview,
  subjectNameLabel,
  showInternalIpField,
  canEditNetwork,
  canConfigureNetwork,
  handleScenarioChange,
  loadNetworks,
  selectNetwork,
  openNetworkConfig,
  loadAddressGrid,
  loadLatestNetworkScanJob,
  startNetworkScan,
  changeGridPage,
  loadLedger,
  handleNetworkQuery,
  handleNetworkSearchInput,
  resetNetworkQuery,
  handleLedgerQuery,
  resetLedgerQuery,
  openAddNetwork,
  openEditNetwork,
  submitNetwork,
  removeNetwork,
  startGridSelection,
  extendGridSelection,
  toggleGridSelection,
  removeSelectedIp,
  clearSelectedIps,
  openConfigFullscreen,
  closeConfigFullscreen,
  setGridDisplayMode,
  isGridSelected,
  generateDownwardRows,
  handleSheetStatusChange,
  markSheetRowDirty,
  submitConfigSheet,
  loadAddressCredential,
  resetConfigSheet,
  getSheetRowClassName,
  openPasteDialog,
  applyPasteRows,
  exportLedger,
  getStatusMeta,
  getTargetTypeLabel,
  getUsagePercent,
  getAssignableCapacity,
  formatNetworkRange,
  getNetworkRangeLabel
} = useIpamManager()

const pastePlaceholder = computed(() => {
  const fields = ['现场IP', subjectNameLabel.value]
  if (showInternalIpField.value) {
    fields.push('小区内网IP')
  }
  fields.push('设备类别', '设备名称', '品牌', '用户名', '密码', '接入单位', '联系人', '电话', '映射地址', '映射端口', '映射说明', '备注')
  return fields.join('\t')
})

const workflowSteps = computed(() => [
  { name: 'network', title: '网段管理', desc: '管理网关与掩码' },
  { name: 'config', title: 'IP配置', desc: '点选IP并批量编辑', requiresNetwork: true },
  { name: 'ledger', title: '地址台账', desc: '检索、筛选与导出' }
])

function switchWorkflowStep(step) {
  if (step.requiresNetwork && !canConfigureNetwork.value) return
  activeTab.value = step.name
  handleTabChange(step.name)
}

function handleTabChange(name) {
  if (name === 'config' && selectedSegment.value) {
    loadAddressGrid()
    loadLatestNetworkScanJob()
  } else if (name === 'ledger') {
    loadLedger()
  }
}
</script>

<style scoped>
.ipam-page {
  color: #1f2937;
}

.ipam-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.ipam-head h2 {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: 0;
}

.ipam-head__meta,
.ipam-head__actions,
.grid-actions,
.ledger-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.scenario-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  margin-bottom: 12px;
  padding: 7px 10px 7px 14px;
  border-top: 1px solid var(--surface-border);
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-muted);
}

.scenario-bar__label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #334155;
  font-size: 13px;
}

.scenario-bar :deep(.el-segmented) {
  --el-segmented-item-selected-bg-color: #fff;
  --el-segmented-item-selected-color: #1d4ed8;
  min-width: 270px;
}

.ipam-shell {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.network-pane,
.ipam-main {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
}

.network-pane {
  display: flex;
  flex-direction: column;
  min-height: 680px;
  padding: 12px;
}

.network-search {
  display: grid;
  gap: 8px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eef2f7;
}

.network-search__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  gap: 8px;
}

.network-search__row .el-button {
  width: 32px;
  min-width: 32px;
  padding: 0;
}

.network-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  color: #334155;
  font-size: 13px;
}

.network-list__head span {
  color: var(--app-muted);
  font-size: 12px;
}

.ipam-main {
  min-width: 0;
  padding: 0 14px 14px;
}

.workflow-nav {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  padding: 8px 0 0;
  border-bottom: 1px solid var(--surface-border);
}

.workflow-step {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 52px;
  padding: 8px 12px 10px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: var(--surface-strong);
  color: #374151;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background-color 0.16s ease, color 0.16s ease;
}

.workflow-step.active {
  border-bottom-color: #2563eb;
  background: var(--surface-muted);
  color: #1d4ed8;
}

.workflow-step:disabled {
  color: #9ca3af;
  cursor: not-allowed;
  opacity: 0.68;
}

.workflow-step__index {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e5e7eb;
  color: #4b5563;
  font-weight: 650;
}

.workflow-step.active .workflow-step__index {
  background: #2563eb;
  color: #fff;
}

.workflow-step__copy {
  min-width: 0;
}

.workflow-step__copy strong,
.workflow-step__copy em {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-step__copy strong {
  margin-bottom: 2px;
  font-size: 14px;
  font-style: normal;
  font-weight: 650;
}

.workflow-step__copy em {
  color: var(--app-muted);
  font-size: 12px;
  font-style: normal;
}

.ipam-tabs :deep(.el-tabs__header) {
  display: none;
}

.workbar,
.grid-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.compact-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
}

.network-detail {
  min-height: 520px;
  padding: 20px 4px;
}

.network-detail__head,
.network-detail__title,
.network-detail__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.network-detail__head {
  justify-content: space-between;
  margin-bottom: 18px;
}

.network-detail__title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
}

.network-detail__head p {
  margin: 6px 0 0;
  color: var(--app-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.network-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 18px;
  border-top: 1px solid var(--surface-border);
  border-bottom: 1px solid var(--surface-border);
}

.network-facts div {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 15px 12px;
}

.network-facts span,
.network-stats span {
  color: var(--app-muted);
  font-size: 12px;
}

.network-facts strong {
  overflow-wrap: anywhere;
  color: #1f2937;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
}

.network-stats {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-top: 18px;
  background: var(--surface-muted);
}

.network-stats div {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-right: 1px solid var(--surface-border);
}

.network-stats div:last-child {
  border-right: 0;
}

.network-stats strong {
  font-size: 22px;
  font-weight: 650;
}

.network-usage {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  color: #475569;
  font-size: 13px;
}

.config-panel,
.ledger-panel {
  min-height: 560px;
}

.grid-toolbar h3 {
  margin: 0 0 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 18px;
  font-weight: 650;
}

.grid-toolbar span {
  color: var(--app-muted);
}

.grid-actions {
  align-items: center;
  justify-content: flex-end;
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
  border: 1px solid var(--surface-border);
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

.mini-cell {
  min-width: 0;
  height: 26px;
  padding: 0;
  border: 1px solid var(--surface-border);
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  line-height: 24px;
  cursor: pointer;
}

.mini-cell.selected {
  border-color: var(--app-heading);
  box-shadow: inset 0 0 0 1px #111827;
}

.mini-cell.active {
  outline: 2px solid #2563eb;
  outline-offset: 1px;
}

.sheet-table {
  width: 100%;
}

.sheet-table :deep(.el-table__cell) {
  padding: 5px 0;
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
  background: var(--surface-subtle);
}

.muted {
  color: #9ca3af;
}

.is-free {
  background: var(--surface-muted);
  border-color: #d1d5db;
  color: var(--app-muted);
}

.is-reserved {
  background: #fffbeb;
  border-color: #f59e0b;
  color: #92400e;
}

.is-allocated {
  background: var(--surface-subtle);
  border-color: #3b82f6;
  color: #1d4ed8;
}

.is-disabled {
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

.legend-dot.is-disabled::before {
  background: #ef4444;
}

.is-boundary,
.is-gateway {
  border-style: dashed;
  cursor: not-allowed;
}

.ledger-actions {
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.guide-form {
  margin-top: 14px;
}

.guide-steps {
  margin-bottom: 14px;
}

.preview-box {
  margin: 0 0 18px 110px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: var(--surface-muted);
}

.preview-box__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  color: #334155;
  font-size: 13px;
}

.network-preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 8px;
}

.network-preview-grid span {
  display: grid;
  gap: 4px;
  padding: 6px 8px;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background: var(--surface-strong);
  color: #475569;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.network-preview-grid small {
  color: var(--app-muted);
  font-family: system-ui, sans-serif;
}

:global(.ipam-config-fullscreen .el-dialog__header) {
  display: none;
}

:global(.ipam-config-fullscreen .el-dialog__body) {
  padding: 14px 18px 18px;
}

@media (max-width: 1280px) {
  .mini-grid {
    grid-template-columns: repeat(16, minmax(26px, 1fr));
  }
}

@media (max-width: 1180px) {
  .ipam-shell {
    grid-template-columns: 1fr;
  }

  .workflow-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .network-pane {
    min-height: 0;
  }

}

@media (max-width: 720px) {
  .ipam-head,
  .scenario-bar,
  .workbar,
  .grid-toolbar,
  .grid-actions,
  .network-detail__head,
  .preview-box__head {
    align-items: stretch;
    flex-direction: column;
  }

  .scenario-bar :deep(.el-segmented) {
    width: 100%;
    min-width: 0;
  }

  .workflow-nav {
    grid-template-columns: 1fr;
  }

  .mini-grid {
    grid-template-columns: repeat(8, minmax(28px, 1fr));
  }

  .preview-box {
    margin-left: 0;
  }

  .network-facts,
  .network-stats {
    grid-template-columns: 1fr;
  }

  .network-stats div {
    border-right: 0;
    border-bottom: 1px solid var(--surface-border);
  }
}
</style>
