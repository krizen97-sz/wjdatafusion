<template>
  <div
    ref="dashboardRoot"
    class="ipam-overview-shell"
    :class="{ 'is-fullscreen': isFullscreen, 'is-refreshing': loading }"
  >
    <div class="dashboard-scan" aria-hidden="true" />

    <header class="command-deck">
      <div class="command-deck__identity">
        <IpamHudIcon icon-class="dashboard" size="brand" mode="radar" tone="cyan" :progress="usagePercent" />
        <div>
          <div class="command-deck__title-row">
            <h2>IP分配全域态势</h2>
            <span class="live-state"><i />数据在线</span>
          </div>
          <div class="command-deck__meta">
            <span>{{ scenarioType === 'INTERNAL' ? '公安内网场景' : '社会面场景' }}</span>
            <span>{{ selectedStation || '全部派出所' }}</span>
            <span>最后同步 {{ formatDateTime(refreshedAt) }}</span>
          </div>
        </div>
      </div>

      <div class="command-deck__controls">
        <div class="station-control">
          <span>派出所</span>
          <el-select
            v-model="selectedStation"
            clearable
            filterable
            :teleported="!isFullscreen"
            popper-class="ipam-station-popper"
            placeholder="全部派出所"
            aria-label="按派出所筛选总览"
            @change="handleStationChange"
          >
            <el-option v-for="station in stationOptions" :key="station" :label="station" :value="station" />
          </el-select>
        </div>
        <el-tooltip content="刷新数据" placement="bottom" :teleported="!isFullscreen">
          <el-button circle icon="Refresh" :loading="loading" aria-label="刷新数据" @click="loadDashboard" />
        </el-tooltip>
        <el-tooltip :content="isFullscreen ? '退出全屏' : '全屏看板'" placement="bottom" :teleported="!isFullscreen">
          <el-button
            circle
            :icon="isFullscreen ? 'ScaleToOriginal' : 'FullScreen'"
            :aria-label="isFullscreen ? '退出全屏' : '全屏看板'"
            @click="toggleFullscreen"
          />
        </el-tooltip>
        <el-button type="primary" icon="Grid" @click="router.push('/ipam/config')">IP分配配置</el-button>
      </div>
    </header>

    <el-alert v-if="dashboardError" type="error" show-icon :closable="false" :title="dashboardError" class="dashboard-alert">
      <template #default>
        <el-button link type="primary" @click="loadDashboard">重新加载</el-button>
      </template>
    </el-alert>

    <main class="dashboard-main" :aria-busy="loading">
      <section class="metric-rail" aria-label="IP分配核心指标">
        <article class="metric-cell metric-cell--usage">
          <IpamHudIcon
            icon-class="chart"
            mode="gauge"
            :tone="usagePercent >= 80 ? 'red' : 'cyan'"
            :progress="usagePercent"
            :alert="usagePercent >= 80"
          />
          <div class="metric-cell__content">
            <div class="metric-cell__label">
              <span>地址占用率</span>
              <b :class="usagePercent >= 80 ? 'is-risk' : 'is-normal'">{{ alertNetworkCount }} 个高负载网段</b>
            </div>
            <strong>{{ usagePercent }}<i>%</i></strong>
            <div class="metric-meter" aria-hidden="true"><span :style="{ width: `${usagePercent}%` }" /></div>
            <small>{{ formatNumber(summary.occupiedCount) }} / {{ formatNumber(summary.assignableCount) }} 个可分配地址</small>
          </div>
        </article>
        <article class="metric-cell">
          <IpamHudIcon icon-class="online" mode="pulse" tone="cyan" :progress="usagePercent" />
          <div class="metric-cell__content">
            <span>已使用IP</span>
            <strong>{{ formatNumber(summary.occupiedCount) }}</strong>
            <small>已登记的现场地址</small>
          </div>
        </article>
        <article class="metric-cell">
          <IpamHudIcon icon-class="number" mode="gauge" tone="green" :progress="freePercent" />
          <div class="metric-cell__content">
            <span>空闲IP</span>
            <strong>{{ formatNumber(summary.freeCount) }}</strong>
            <small>保留 {{ formatNumber(summary.reservedCount) }} · 禁用 {{ formatNumber(summary.disabledCount) }}</small>
          </div>
        </article>
        <article class="metric-cell">
          <IpamHudIcon icon-class="server" mode="radar" tone="violet" :progress="enabledNetworkPercent" />
          <div class="metric-cell__content">
            <span>管理网段</span>
            <strong>{{ formatNumber(summary.networkCount) }}</strong>
            <small>启用 {{ formatNumber(summary.enabledNetworkCount) }} 个</small>
          </div>
        </article>
        <article class="metric-cell">
          <IpamHudIcon icon-class="peoples" mode="pulse" tone="amber" :progress="100" />
          <div class="metric-cell__content">
            <span>{{ subjectShortLabel }}数量</span>
            <strong>{{ formatNumber(summary.communityCount) }}</strong>
            <small>覆盖 {{ formatNumber(summary.stationCount) }} 个派出所</small>
          </div>
        </article>
        <article class="metric-cell">
          <IpamHudIcon icon-class="component" mode="radar" tone="cyan" :progress="usagePercent" />
          <div class="metric-cell__content">
            <span>登记设备</span>
            <strong>{{ formatNumber(summary.deviceCount) }}</strong>
            <small>来自已使用IP</small>
          </div>
        </article>
      </section>

      <div class="dashboard-grid">
        <section class="dashboard-panel dashboard-panel--station" style="--panel-order: 0">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="peoples" size="panel" mode="radar" tone="cyan" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>派出所资源态势</h3>
                <span>按地址状态观察辖区容量构成</span>
              </div>
            </div>
            <span class="panel-chip">{{ selectedStation || '全域' }}</span>
          </div>
          <IpamDashboardChart
            v-if="networkRows.length"
            :option="stationOption"
            aria-label="各派出所IP资源状态对比"
            @chart-click="focusStation($event.name)"
          />
          <el-empty v-else :image-size="56" description="暂无网段数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--status" style="--panel-order: 1">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="chart" size="panel" mode="pulse" tone="green" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>地址状态构成</h3>
                <span>当前筛选范围的地址分布</span>
              </div>
            </div>
            <span class="panel-signal" aria-label="实时统计"><i /><i /><i /></span>
          </div>
          <IpamDashboardChart
            v-if="Number(summary.assignableCount || 0)"
            :option="statusOption"
            aria-label="IP地址状态构成"
          />
          <el-empty v-else :image-size="56" description="暂无地址数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--network" style="--panel-order: 2">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="server" size="panel" mode="gauge" tone="violet" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>网段负载分布</h3>
                <span>按网关IP顺序观察各网段占用率</span>
              </div>
            </div>
            <span class="panel-chip">80% 预警 · 90% 高危</span>
          </div>
          <IpamDashboardChart v-if="networkRows.length" :option="networkOption" aria-label="各网段IP占用率" />
          <el-empty v-else :image-size="56" description="暂无网段数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--pressure" style="--panel-order: 3">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon
                icon-class="monitor"
                size="panel"
                mode="pulse"
                :tone="alertNetworkCount ? 'red' : 'green'"
                :progress="usagePercent"
                :alert="alertNetworkCount > 0"
              />
              <div class="panel-head__copy">
                <h3>容量压力分层</h3>
                <span>按占用率区间识别容量风险</span>
              </div>
            </div>
            <span class="panel-chip panel-chip--danger">{{ alertNetworkCount }} 段预警</span>
          </div>
          <IpamDashboardChart v-if="networkRows.length" :option="pressureOption" aria-label="网段容量压力分层" />
          <el-empty v-else :image-size="56" description="暂无压力数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--community" style="--panel-order: 4">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="peoples" size="panel" mode="pulse" tone="amber" :progress="100" />
              <div class="panel-head__copy">
                <h3>{{ subjectShortLabel }}分配排行</h3>
                <span>按占用IP数量观察资源集中度</span>
              </div>
            </div>
            <span class="panel-chip">TOP 10</span>
          </div>
          <IpamDashboardChart
            v-if="dashboardData.communities.length"
            :option="communityOption"
            :aria-label="`${subjectShortLabel}占用IP排行`"
          />
          <el-empty v-else :image-size="56" :description="`暂无${subjectShortLabel}数据`" />
        </section>

        <section class="dashboard-panel dashboard-panel--device" style="--panel-order: 5">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="component" size="panel" mode="radar" tone="cyan" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>设备类别构成</h3>
                <span>按已使用IP统计设备类型</span>
              </div>
            </div>
          </div>
          <IpamDashboardChart
            v-if="dashboardData.targetTypes.length"
            :option="targetTypeOption"
            aria-label="设备类别分布"
          />
          <el-empty v-else :image-size="56" description="暂无设备类别数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--manufacturer" style="--panel-order: 6">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="build" size="panel" mode="gauge" tone="violet" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>设备品牌构成</h3>
                <span>观察主要品牌的地址占比</span>
              </div>
            </div>
          </div>
          <IpamDashboardChart
            v-if="dashboardData.manufacturers.length"
            :option="manufacturerOption"
            aria-label="设备品牌分布"
          />
          <el-empty v-else :image-size="56" description="暂无品牌数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--efficiency" style="--panel-order: 7">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="chart" size="panel" mode="radar" tone="green" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>派出所容量效率</h3>
                <span>气泡大小表示管理网段数量</span>
              </div>
            </div>
            <span class="panel-chip">占用率 × 已使用IP</span>
          </div>
          <IpamDashboardChart
            v-if="networkRows.length"
            :option="stationEfficiencyOption"
            aria-label="派出所容量效率气泡图"
          />
          <el-empty v-else :image-size="56" description="暂无效率数据" />
        </section>

        <section class="dashboard-panel dashboard-panel--matrix" style="--panel-order: 8">
          <div class="panel-head">
            <div class="panel-head__identity">
              <IpamHudIcon icon-class="dashboard" size="panel" mode="radar" tone="violet" :progress="usagePercent" />
              <div class="panel-head__copy">
                <h3>网段负载矩阵</h3>
                <span>横向为辖区网段序号，颜色表示实时占用率</span>
              </div>
            </div>
            <span class="panel-chip">0% - 100%</span>
          </div>
          <IpamDashboardChart
            v-if="networkRows.length"
            :option="networkMatrixOption"
            aria-label="派出所网段负载矩阵"
          />
          <el-empty v-else :image-size="56" description="暂无矩阵数据" />
        </section>
      </div>
    </main>
  </div>
</template>

<script setup name="IpamOverview">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import IpamDashboardChart from '../components/IpamDashboardChart.vue'
import IpamHudIcon from '../components/IpamHudIcon.vue'
import { useIpamDashboard } from '../useIpamDashboard.js'

const router = useRouter()
const dashboardRoot = ref(null)
const isFullscreen = ref(false)
const {
  loading,
  dashboardError,
  dashboardData,
  scenarioType,
  refreshedAt,
  summary,
  subjectShortLabel,
  usagePercent,
  selectedStation,
  stationOptions,
  networkRows,
  alertNetworkCount,
  statusOption,
  stationOption,
  networkOption,
  pressureOption,
  communityOption,
  targetTypeOption,
  manufacturerOption,
  stationEfficiencyOption,
  networkMatrixOption,
  loadDashboard,
  handleStationChange,
  focusStation
} = useIpamDashboard()

const freePercent = computed(() => percentage(summary.value.freeCount, summary.value.assignableCount))
const enabledNetworkPercent = computed(() => percentage(summary.value.enabledNetworkCount, summary.value.networkCount))

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await dashboardRoot.value?.requestFullscreen?.()
  } else {
    await document.exitFullscreen?.()
  }
}

function syncFullscreenState() {
  isFullscreen.value = document.fullscreenElement === dashboardRoot.value
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function percentage(part, total) {
  const normalizedTotal = Number(total || 0)
  return normalizedTotal ? Math.min(100, Math.round((Number(part || 0) / normalizedTotal) * 1000) / 10) : 0
}

function formatDateTime(value) {
  if (!(value instanceof Date)) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  }).format(value)
}

onMounted(() => document.addEventListener('fullscreenchange', syncFullscreenState))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', syncFullscreenState))
</script>

<style scoped>
.ipam-overview-shell {
  position: relative;
  min-height: calc(100vh - 84px);
  padding: 16px 18px 28px;
  overflow: hidden;
  color: #dce8f5;
  background-color: #0b0f14;
  background-image:
    linear-gradient(rgba(76, 118, 153, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(76, 118, 153, 0.055) 1px, transparent 1px);
  background-size: 32px 32px;
  font-variant-numeric: tabular-nums;
}

.ipam-overview-shell.is-fullscreen {
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: auto;
  padding: 18px 22px 30px;
}

.dashboard-scan {
  position: absolute;
  z-index: 4;
  top: 0;
  right: 0;
  left: 0;
  height: 2px;
  opacity: 0;
  pointer-events: none;
  background: linear-gradient(90deg, transparent, #39a0ff 25%, #32c98c 65%, transparent);
  box-shadow: 0 0 18px rgba(57, 160, 255, 0.7);
}

.is-refreshing .dashboard-scan {
  opacity: 0.88;
  animation: dashboard-scan 1.45s cubic-bezier(0.22, 1, 0.36, 1) infinite;
}

.command-deck,
.command-deck__identity,
.command-deck__title-row,
.command-deck__meta,
.command-deck__controls,
.station-control,
.metric-cell,
.metric-cell__label,
.panel-head__identity,
.panel-head {
  display: flex;
  align-items: center;
}

.command-deck {
  position: relative;
  z-index: 3;
  justify-content: space-between;
  min-height: 68px;
  padding: 11px 14px;
  border: 1px solid #2d3946;
  border-radius: 6px;
  background: #111820;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.2);
}

.command-deck::after {
  position: absolute;
  right: 14px;
  bottom: -1px;
  width: 88px;
  height: 1px;
  content: '';
  background: #39a0ff;
  box-shadow: 0 0 10px rgba(57, 160, 255, 0.7);
}

.command-deck__identity {
  min-width: 0;
  gap: 12px;
}

.command-deck__title-row {
  flex-wrap: wrap;
  gap: 8px;
}

.command-deck h2 {
  margin: 0;
  color: #f4f9ff;
  font-size: 20px;
  font-weight: 680;
  letter-spacing: 0;
}

.live-state {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #55dca6;
  font-size: 11px;
}

.live-state i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #32c98c;
  box-shadow: 0 0 0 4px rgba(50, 201, 140, 0.12);
  animation: live-pulse 2s ease-out infinite;
}

.command-deck__meta {
  flex-wrap: wrap;
  gap: 0;
  margin-top: 4px;
  color: #8092a6;
  font-size: 11px;
}

.command-deck__meta span + span::before {
  margin: 0 7px;
  color: #3c4c5c;
  content: '·';
}

.command-deck__controls {
  justify-content: flex-end;
  gap: 8px;
}

.station-control {
  height: 34px;
  border: 1px solid #34414f;
  border-radius: 5px;
  background: #171e26;
}

.station-control > span {
  padding: 0 10px;
  color: #91a2b4;
  font-size: 11px;
}

.station-control :deep(.el-select) {
  width: 145px;
}

.station-control :deep(.el-select__wrapper) {
  min-height: 32px;
  box-shadow: none !important;
  background-color: transparent !important;
}

.station-control :deep(.el-select__selected-item),
.station-control :deep(.el-select__placeholder) {
  color: #dce8f5;
}

:global(.ipam-station-popper.el-popper) {
  border-color: #34414f !important;
  background: #171e26 !important;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.34) !important;
}

:global(.ipam-station-popper .el-select-dropdown__item) {
  color: #b9c8d8;
}

:global(.ipam-station-popper .el-select-dropdown__item.is-hovering),
:global(.ipam-station-popper .el-select-dropdown__item.is-selected) {
  color: #ecf7ff;
  background: #233444;
}

:global(.ipam-station-popper.el-popper .el-popper__arrow::before) {
  border-color: #34414f !important;
  background: #171e26 !important;
}

.command-deck__controls :deep(.el-button) {
  border-color: #34414f;
  color: #b8c7d7;
  background: #171e26;
}

.command-deck__controls :deep(.el-button:hover),
.command-deck__controls :deep(.el-button:focus-visible) {
  border-color: #39a0ff;
  color: #78c7ff;
  background: #172737;
}

.command-deck__controls :deep(.el-button--primary) {
  border-color: #2f8df0;
  color: #fff;
  background: #2f8df0;
}

.dashboard-alert {
  margin-top: 12px;
}

.dashboard-main {
  position: relative;
  z-index: 1;
  margin-top: 14px;
}

.metric-rail {
  display: grid;
  grid-template-columns: 1.34fr repeat(5, minmax(0, 1fr));
  border: 1px solid #2d3946;
  border-radius: 6px;
  background: #111820;
  overflow: hidden;
}

.metric-cell {
  position: relative;
  min-width: 0;
  min-height: 112px;
  gap: 10px;
  padding: 14px 15px;
  border-right: 1px solid #2d3946;
}

.metric-cell:last-child {
  border-right: 0;
}

.metric-cell::after {
  position: absolute;
  right: 14px;
  bottom: 0;
  left: 14px;
  height: 1px;
  content: '';
  opacity: 0;
  background: #39a0ff;
  transition: opacity 180ms ease-out;
}

.metric-cell:hover::after {
  opacity: 0.7;
}

.metric-cell__content {
  min-width: 0;
  width: 100%;
}

.metric-cell__content > span,
.metric-cell small,
.metric-cell__label span {
  color: #8294a8;
  font-size: 11px;
}

.metric-cell strong {
  display: block;
  margin: 4px 0 3px;
  color: #edf5ff;
  font-size: 26px;
  font-weight: 650;
  line-height: 1.1;
}

.metric-cell strong i {
  margin-left: 2px;
  color: #90a4b9;
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
}

.metric-cell small {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.metric-cell--usage {
  align-items: flex-start;
}

.metric-cell__label {
  justify-content: space-between;
  gap: 6px;
}

.metric-cell__label b {
  white-space: nowrap;
  font-size: 10px;
  font-weight: 600;
}

.metric-cell__label b.is-normal {
  color: #55dca6;
}

.metric-cell__label b.is-risk {
  color: #ff7d8a;
}

.metric-meter {
  position: relative;
  height: 4px;
  margin: 7px 0;
  overflow: hidden;
  border-radius: 2px;
  background: #25313d;
}

.metric-meter span {
  position: relative;
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #39a0ff;
  transition: width 420ms cubic-bezier(0.22, 1, 0.36, 1);
}

.is-refreshing .metric-meter span::after {
  position: absolute;
  inset: 0;
  content: '';
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.72), transparent);
  animation: meter-refresh 1s ease-out infinite;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.dashboard-panel {
  --hud-play-state: paused;
  position: relative;
  min-width: 0;
  min-height: 350px;
  padding: 12px 13px 10px;
  border: 1px solid #2d3946;
  border-radius: 6px;
  background: #111820;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14);
  transition: border-color 180ms ease-out, box-shadow 180ms ease-out;
}

.dashboard-panel::before {
  position: absolute;
  z-index: 3;
  top: 0;
  left: 0;
  width: 92px;
  height: 1px;
  content: '';
  opacity: 0;
  pointer-events: none;
  background: linear-gradient(90deg, transparent, #78c7ff 44%, #55dca6 68%, transparent);
  box-shadow: 0 0 12px rgba(57, 160, 255, 0.75);
  transform: translate3d(-120px, 0, 0);
}

.dashboard-panel::after {
  position: absolute;
  right: 12px;
  bottom: 0;
  width: 54px;
  height: 1px;
  content: '';
  background: #2f8dcc;
  box-shadow: 0 0 10px rgba(57, 160, 255, 0.45);
}

.dashboard-panel:hover {
  --hud-play-state: running;
  border-color: #3c5368;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.2), 0 0 0 1px rgba(57, 160, 255, 0.04) inset;
}

.is-refreshing .dashboard-panel {
  --hud-play-state: running;
}

.is-refreshing .dashboard-panel::before {
  animation: panel-data-flow 1.05s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: calc(var(--panel-order, 0) * 70ms);
}

.dashboard-panel--station,
.dashboard-panel--network {
  grid-column: span 8;
}

.dashboard-panel--status,
.dashboard-panel--pressure {
  grid-column: span 4;
}

.dashboard-panel--community {
  grid-column: span 6;
}

.dashboard-panel--device,
.dashboard-panel--manufacturer {
  grid-column: span 3;
}

.dashboard-panel--efficiency {
  grid-column: span 5;
  min-height: 390px;
}

.dashboard-panel--matrix {
  grid-column: span 7;
  min-height: 390px;
}

.dashboard-panel--station :deep(.ipam-dashboard-chart),
.dashboard-panel--status :deep(.ipam-dashboard-chart) {
  height: 320px;
}

.dashboard-panel--network :deep(.ipam-dashboard-chart),
.dashboard-panel--pressure :deep(.ipam-dashboard-chart) {
  height: 310px;
}

.dashboard-panel--community :deep(.ipam-dashboard-chart),
.dashboard-panel--device :deep(.ipam-dashboard-chart),
.dashboard-panel--manufacturer :deep(.ipam-dashboard-chart) {
  height: 305px;
}

.dashboard-panel--efficiency :deep(.ipam-dashboard-chart),
.dashboard-panel--matrix :deep(.ipam-dashboard-chart) {
  height: 345px;
}

.panel-head {
  position: relative;
  z-index: 1;
  justify-content: space-between;
  min-height: 42px;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #25303b;
}

.panel-head__identity {
  min-width: 0;
  gap: 8px;
}

.panel-head__copy {
  min-width: 0;
}

.panel-head h3 {
  margin: 0;
  color: #e6eff8;
  font-size: 14px;
  font-weight: 650;
  letter-spacing: 0;
}

.panel-head__copy > span {
  display: block;
  margin-top: 3px;
  color: #718398;
  font-size: 10px;
}

.panel-chip {
  flex: 0 0 auto;
  padding: 4px 7px;
  border: 1px solid #31516d;
  border-radius: 4px;
  color: #70c5ff;
  background: #132230;
  font-size: 9px;
}

.panel-chip--danger {
  border-color: #63343d;
  color: #ff8b97;
  background: #27171c;
}

.panel-signal {
  display: inline-flex;
  align-items: flex-end;
  height: 18px;
  gap: 3px;
}

.panel-signal i {
  display: block;
  width: 3px;
  border-radius: 2px 2px 0 0;
  background: #39a0ff;
  transform-origin: 50% 100%;
  animation: panel-signal 1.15s ease-in-out infinite alternate;
  animation-play-state: var(--hud-play-state, paused);
}

.panel-signal i:nth-child(1) {
  height: 7px;
}

.panel-signal i:nth-child(2) {
  height: 12px;
  background: #28c2d1;
  animation-delay: -380ms;
}

.panel-signal i:nth-child(3) {
  height: 17px;
  background: #32c98c;
  animation-delay: -760ms;
}

.dashboard-panel :deep(.el-empty) {
  height: 280px;
  padding: 0;
}

.dashboard-panel :deep(.el-empty__description p) {
  color: #718398;
}

@keyframes dashboard-scan {
  0% { transform: translateY(0); }
  100% { transform: translateY(calc(100vh - 2px)); }
}

@keyframes live-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(50, 201, 140, 0.12); }
  50% { box-shadow: 0 0 0 6px rgba(50, 201, 140, 0.02); }
}

@keyframes meter-refresh {
  from { transform: translateX(-100%); }
  to { transform: translateX(100%); }
}

@keyframes panel-data-flow {
  0% { opacity: 0; transform: translate3d(-120px, 0, 0); }
  18% { opacity: 1; }
  100% { opacity: 0; transform: translate3d(calc(100vw + 120px), 0, 0); }
}

@keyframes panel-signal {
  from { opacity: 0.45; transform: scaleY(0.42); }
  to { opacity: 1; transform: scaleY(1); }
}

@media (max-width: 1380px) {
  .metric-rail {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-cell:nth-child(3) {
    border-right: 0;
  }

  .metric-cell:nth-child(-n + 3) {
    border-bottom: 1px solid #2d3946;
  }

  .dashboard-panel--community {
    grid-column: span 12;
  }

  .dashboard-panel--device,
  .dashboard-panel--manufacturer {
    grid-column: span 6;
  }
}

@media (max-width: 1180px) {
  .command-deck {
    align-items: flex-start;
    gap: 14px;
  }

  .command-deck__controls {
    flex-wrap: wrap;
  }

  .dashboard-panel--station,
  .dashboard-panel--status,
  .dashboard-panel--network,
  .dashboard-panel--pressure,
  .dashboard-panel--efficiency,
  .dashboard-panel--matrix {
    grid-column: span 12;
  }
}

@media (max-width: 820px) {
  .ipam-overview-shell,
  .ipam-overview-shell.is-fullscreen {
    padding: 12px;
  }

  .command-deck {
    display: block;
  }

  .command-deck__controls {
    justify-content: flex-start;
    margin-top: 12px;
  }

  .metric-rail {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-cell:nth-child(3) {
    border-right: 1px solid #2d3946;
  }

  .metric-cell:nth-child(even) {
    border-right: 0;
  }

  .metric-cell:nth-child(-n + 4) {
    border-bottom: 1px solid #2d3946;
  }

  .dashboard-panel--device,
  .dashboard-panel--manufacturer {
    grid-column: span 12;
  }
}

@media (max-width: 560px) {
  .station-control {
    width: 100%;
  }

  .station-control :deep(.el-select) {
    flex: 1;
    width: auto;
  }

  .command-deck__controls :deep(.el-button--primary) {
    flex: 1;
  }

  .metric-rail {
    display: block;
  }

  .metric-cell,
  .metric-cell:nth-child(3),
  .metric-cell:nth-child(even) {
    border-right: 0;
    border-bottom: 1px solid #2d3946;
  }

  .metric-cell:last-child {
    border-bottom: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dashboard-scan,
  .live-state i,
  .is-refreshing .metric-meter span::after,
  .is-refreshing .dashboard-panel::before,
  .panel-signal i {
    animation: none !important;
  }

  .metric-meter span,
  .dashboard-panel,
  .metric-cell::after {
    transition-duration: 0.01ms !important;
  }
}
</style>
