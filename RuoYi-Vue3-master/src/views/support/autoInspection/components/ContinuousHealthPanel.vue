<template>
  <section class="continuous-health-panel">
    <el-form :inline="true" class="continuous-health-query-bar">
      <el-form-item label="月份">
        <el-date-picker
          :model-value="month"
          type="month"
          value-format="YYYY-MM"
          format="YYYY年MM月"
          :clearable="false"
          style="width: 180px"
          @update:model-value="$emit('update:month', $event)"
        />
      </el-form-item>
      <el-form-item label="健康归属">
        <el-tree-select
          :model-value="scopeKey"
          :data="scopeOptions"
          node-key="value"
          clearable
          filterable
          :render-after-expand="false"
          placeholder="全部现场与主平台"
          style="width: 250px"
          @update:model-value="$emit('update:scopeKey', $event)"
        />
      </el-form-item>
      <el-form-item label="计划">
        <el-tree-select
          :model-value="planId"
          :data="planOptions"
          node-key="value"
          clearable
          filterable
          :render-after-expand="false"
          placeholder="全部巡检计划"
          style="width: 210px"
          @update:model-value="$emit('update:planId', $event)"
        />
      </el-form-item>
    </el-form>

    <div class="continuous-health-metrics" aria-label="本月巡检健康范围">
      <div class="continuous-health-metric">
        <el-icon><OfficeBuilding /></el-icon>
        <span><em>现场范围</em><strong>{{ summary.siteCount }}<small>个</small></strong></span>
      </div>
      <div class="continuous-health-metric">
        <el-icon><Monitor /></el-icon>
        <span><em>主平台范围</em><strong>{{ summary.platformCount }}<small>个</small></strong></span>
      </div>
      <button
        type="button"
        class="continuous-health-metric continuous-health-metric--action is-danger"
        :disabled="!summary.abnormalSiteCount"
        @click="openMonthlyIssue('2')"
      >
        <el-icon><WarningFilled /></el-icon>
        <span><em>异常现场</em><strong>{{ summary.abnormalSiteCount }}<small>个</small></strong></span>
        <span class="continuous-health-metric__cue">定位异常<el-icon><ArrowRight /></el-icon></span>
      </button>
      <button
        type="button"
        class="continuous-health-metric continuous-health-metric--action is-warning"
        :disabled="!summary.warningSiteCount"
        @click="openMonthlyIssue('4')"
      >
        <el-icon><BellFilled /></el-icon>
        <span><em>关注现场</em><strong>{{ summary.warningSiteCount }}<small>个</small></strong></span>
        <span class="continuous-health-metric__cue">查看关注<el-icon><ArrowRight /></el-icon></span>
      </button>
      <button
        type="button"
        class="continuous-health-metric continuous-health-metric--action is-warning"
        :disabled="!summary.unassignedPlanCount"
        @click="$emit('manage-unassigned')"
      >
        <el-icon><Link /></el-icon>
        <span><em>待归属计划</em><strong>{{ summary.unassignedPlanCount }}<small>个</small></strong></span>
        <span class="continuous-health-metric__cue">前往配置<el-icon><ArrowRight /></el-icon></span>
      </button>
    </div>

    <div v-loading="loading" class="continuous-health-day-list">
      <el-empty v-if="!pagedRows.length" description="当前月份暂无巡检健康记录" :image-size="64" />
      <section
        v-for="day in pagedRows"
        v-else
        :key="day.healthDate"
        class="continuous-health-day"
        :class="{ 'continuous-health-day--abnormal': day.dayStatus === '2' }"
      >
        <header class="continuous-health-day__head">
          <div class="continuous-health-day__date">
            <strong>{{ datePresentation(day.healthDate).label }}</strong>
            <span>{{ datePresentation(day.healthDate).dateKey || '-' }} · {{ datePresentation(day.healthDate).weekday }}</span>
          </div>
          <div class="continuous-health-day__status">
            <el-tag :type="healthStatusType(day.dayStatus)" effect="plain">
              {{ healthStatusLabel(day.dayStatus, day.recovered) }}
            </el-tag>
            <el-popover placement="top" :width="360" trigger="click">
              <template #reference>
                <el-button text circle class="continuous-health-status-help" aria-label="查看当日结论说明">
                  <el-icon><QuestionFilled /></el-icon>
                </el-button>
              </template>
              <div class="continuous-health-status-guide">
                <strong>当日结论说明</strong>
                <div v-for="item in statusGuide" :key="item.label">
                  <el-tag size="small" effect="plain" :type="item.type">{{ item.label }}</el-tag>
                  <span>{{ item.description }}</span>
                </div>
              </div>
            </el-popover>
          </div>
          <div class="continuous-health-day__facts">
            <span>现场 <strong>{{ day.sites.length }}</strong></span>
            <span>计划 <strong>{{ day.planCount }}</strong></span>
            <span>完成 <strong>{{ day.completedCount }}/{{ day.expectedCount }}</strong></span>
            <el-button v-if="day.abnormalCount" link type="danger" @click="openDayResults(day, '2')">异常 {{ day.abnormalCount }}</el-button>
            <span v-else>异常 <strong>0</strong></span>
            <el-button v-if="day.warningCount" link type="warning" @click="openDayResults(day, '4')">关注 {{ day.warningCount }}</el-button>
            <span v-else>关注 <strong>0</strong></span>
            <el-button v-if="day.missingCount" link type="warning" @click="openDayResults(day, '3')">缺失 {{ day.missingCount }}</el-button>
            <span v-else>缺失 <strong>0</strong></span>
          </div>
          <el-button type="primary" link :icon="View" @click="openDayResults(day)">查看当天</el-button>
        </header>

        <div class="scope-health-detail">
          <el-alert
            v-if="day.unassignedPlans.length"
            type="warning"
            :closable="false"
            show-icon
            title="存在待归属计划"
          >
            <template #default>
              {{ day.unassignedPlans.map((plan) => plan.planName || '未命名计划').join('、') }}，请在巡检配置中补充所属现场或主平台；这些计划暂不参与健康度计算。
            </template>
          </el-alert>

          <el-empty v-if="!day.sites.length" description="当天还没有可归属到现场的巡检结果" :image-size="54" />
          <article v-for="site in day.sites" v-else :key="`${day.healthDate}-${site.siteId}`" class="site-health-block">
              <header class="site-health-head">
                <div class="site-health-title">
                  <span class="status-dot" :class="`status-dot--${site.dayStatus || '3'}`"></span>
                  <div>
                    <strong>{{ site.siteName }}</strong>
                    <span>现场公共计划 {{ site.sitePlans.length }} 个 · 主平台 {{ site.platforms.length }} 个</span>
                  </div>
                </div>
                <div class="site-health-score">
                  <el-progress :percentage="clampHealthScore(site.healthScore)" :stroke-width="7" :show-text="false" />
                  <strong>{{ clampHealthScore(site.healthScore) }}%</strong>
                </div>
                <el-tag :type="healthStatusType(site.dayStatus)" effect="plain">
                  {{ healthStatusLabel(site.dayStatus, site.recovered) }}
                </el-tag>
                <el-button
                  type="primary"
                  link
                  :icon="View"
                  @click="openSiteResults(day, site)"
                >查看现场</el-button>
              </header>

              <div v-if="site.sitePlans.length" class="site-public-plans">
                <span>现场公共巡检</span>
                <el-button
                  v-for="plan in site.sitePlans"
                  :key="plan.planId"
                  type="primary"
                  link
                  @click="openPlanResults(day, plan)"
                >{{ plan.planName || '未命名计划' }}</el-button>
              </div>

              <el-table :data="site.platforms" size="small" class="platform-health-table" empty-text="当前现场暂未配置主平台巡检计划">
                <el-table-column label="主平台" min-width="170">
                  <template #default="platformScope">
                    <div class="platform-health-name">
                      <strong>{{ platformScope.row.mainPlatformName }}</strong>
                      <span>{{ platformScope.row.plans.length }} 个计划</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="结论" width="112" align="center">
                  <template #default="platformScope">
                    <el-tag size="small" :type="healthStatusType(platformScope.row.dayStatus)" effect="plain">
                      {{ healthStatusLabel(platformScope.row.dayStatus, platformScope.row.recovered) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="健康度" width="160">
                  <template #default="platformScope">
                    <div class="continuous-health-score">
                      <el-progress :percentage="clampHealthScore(platformScope.row.healthScore)" :stroke-width="6" :show-text="false" />
                      <strong>{{ clampHealthScore(platformScope.row.healthScore) }}%</strong>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="巡检计划" min-width="220">
                  <template #default="platformScope">
                    <div class="continuous-health-plans">
                      <el-button
                        v-for="plan in platformScope.row.plans"
                        :key="plan.planId"
                        type="primary"
                        link
                        class="continuous-health-plan-link"
                        @click="openPlanResults(day, plan)"
                      >{{ plan.planName || '未命名计划' }}</el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="完成 / 应执行" width="120" align="center">
                  <template #default="platformScope">{{ platformScope.row.completedCount }} / {{ platformScope.row.expectedCount }}</template>
                </el-table-column>
                <el-table-column label="异常 / 关注 / 缺失" width="150" align="center">
                  <template #default="platformScope">
                    <div class="record-count-cell" aria-label="异常、关注和缺失数量">
                      <el-button v-if="platformScope.row.abnormalCount" link type="danger" @click="openPlatformResults(day, site, platformScope.row, '2')">{{ platformScope.row.abnormalCount }}</el-button>
                      <span v-else>0</span>
                      <el-button v-if="platformScope.row.warningCount" link type="warning" @click="openPlatformResults(day, site, platformScope.row, '4')">{{ platformScope.row.warningCount }}</el-button>
                      <span v-else>0</span>
                      <el-button v-if="platformScope.row.missingCount" link type="warning" @click="openPlatformResults(day, site, platformScope.row, '3')">{{ platformScope.row.missingCount }}</el-button>
                      <span v-else>0</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="88" align="center">
                  <template #default="platformScope">
                    <el-button
                      type="primary"
                      link
                      :icon="View"
                      @click="openPlatformResults(day, site, platformScope.row)"
                    >查看</el-button>
                  </template>
                </el-table-column>
              </el-table>
          </article>
        </div>
      </section>
    </div>

    <pagination
      v-show="groupedRows.length > 0"
      :total="groupedRows.length"
      v-model:page="pageNum"
      v-model:limit="pageSize"
      @pagination="handlePagination"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowRight, BellFilled, Link, Monitor, OfficeBuilding, QuestionFilled, View, WarningFilled } from '@element-plus/icons-vue'
import {
  clampHealthScore,
  groupDailyHealthRows,
  healthStatusLabel,
  healthStatusType,
  paginateDailyHealthRows,
  summarizeDailyHealth
} from '../continuousHealthPresentation'
import { presentInspectionDate } from '../overviewPresentation'

const props = defineProps({
  loading: { type: Boolean, default: false },
  rows: { type: Array, default: () => [] },
  month: { type: String, default: '' },
  scopeKey: { type: String, default: '' },
  scopeOptions: { type: Array, default: () => [] },
  planId: { type: [Number, String], default: undefined },
  planOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:month', 'update:scopeKey', 'update:planId', 'day-results', 'manage-unassigned'])

const groupedRows = computed(() => groupDailyHealthRows(props.rows))
const pageNum = ref(1)
const pageSize = ref(10)
const pagedRows = computed(() => paginateDailyHealthRows(groupedRows.value, pageNum.value, pageSize.value))
const summary = computed(() => summarizeDailyHealth(groupedRows.value))

watch(() => [props.month, props.scopeKey, props.planId], () => {
  pageNum.value = 1
})

watch(() => groupedRows.value.map((item) => item.healthDate), (dates) => {
  const lastPage = Math.max(1, Math.ceil(dates.length / pageSize.value))
  if (pageNum.value > lastPage) pageNum.value = lastPage
}, { immediate: true })

function datePresentation(value) {
  return presentInspectionDate(value)
}

function handlePagination({ page, limit }) {
  pageNum.value = page
  pageSize.value = limit
}

function emitResults(payload, resultStatus) {
  emit('day-results', { ...payload, resultStatus })
}

function openDayResults(day, resultStatus) {
  emitResults({ date: day.healthDate, group: day }, resultStatus)
}

function openSiteResults(day, site, resultStatus) {
  emitResults({ date: day.healthDate, group: site, siteId: site.siteId, siteName: site.siteName }, resultStatus)
}

function openPlanResults(day, plan) {
  emitResults({ date: day.healthDate, group: plan, planId: plan.planId, planName: plan.planName })
}

function openPlatformResults(day, site, platform, resultStatus) {
  emitResults({
    date: day.healthDate,
    group: platform,
    siteId: site.siteId,
    siteName: site.siteName,
    mainPlatformId: platform.mainPlatformId,
    mainPlatformName: platform.mainPlatformName
  }, resultStatus)
}

function openMonthlyIssue(status) {
  for (const day of groupedRows.value) {
    const site = day.sites.find((item) => item.dayStatus === status)
    if (site) {
      openSiteResults(day, site, status)
      return
    }
  }
}

const statusGuide = [
  { label: '正常', type: 'success', description: '当前现场或主平台的计划均按时完成，且没有异常或关注项。' },
  { label: '需要关注', type: 'warning', description: '存在关注结果或应执行但尚未完成的计划。' },
  { label: '异常持续中', type: 'danger', description: '至少一个计划出现异常，且最近一次检查仍未恢复。' },
  { label: '异常已恢复', type: 'danger', description: '当天出现过异常，但最近一次检查已经恢复正常。' },
  { label: '尚未执行', type: 'info', description: '计划尚未到执行时间，或者当天还没有形成有效结果。' }
]
</script>

<style scoped>
.continuous-health-panel {
  min-width: 0;
}

.continuous-health-query-bar {
  margin-bottom: 12px;
  padding: 12px 12px 0;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
}

.continuous-health-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(148px, 1fr));
  overflow: hidden;
  border-block: 1px solid var(--surface-border);
  background: var(--surface-strong);
}

.continuous-health-metric {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 0;
  min-height: 66px;
  padding: 10px 14px;
  border: 0;
  border-right: 1px solid var(--surface-border);
  background: transparent;
  color: var(--app-text);
  font: inherit;
  text-align: left;
}

.continuous-health-metric:last-child {
  border-right: 0;
}

.continuous-health-metric > .el-icon {
  color: var(--app-muted);
  font-size: 21px;
}

.continuous-health-metric > span:not(.continuous-health-metric__cue) {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.continuous-health-metric em,
.continuous-health-metric small {
  color: var(--app-muted);
  font-style: normal;
  font-weight: 500;
}

.continuous-health-metric em {
  font-size: 11px;
}

.continuous-health-metric strong {
  color: var(--app-heading);
  font-size: 21px;
  line-height: 1;
}

.continuous-health-metric small {
  margin-left: 4px;
  font-size: 11px;
}

.continuous-health-metric--action {
  grid-template-columns: 28px minmax(0, 1fr) auto;
  cursor: pointer;
  transition: background-color 160ms ease, color 160ms ease;
}

.continuous-health-metric--action:hover:not(:disabled),
.continuous-health-metric--action:focus-visible {
  background: var(--surface-muted);
}

.continuous-health-metric--action:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: -2px;
}

.continuous-health-metric--action:disabled {
  cursor: default;
  opacity: .62;
}

.continuous-health-metric--action.is-danger > .el-icon,
.continuous-health-metric--action.is-danger strong {
  color: var(--health-danger);
}

.continuous-health-metric--action.is-warning > .el-icon,
.continuous-health-metric--action.is-warning strong {
  color: var(--health-warning);
}

.continuous-health-metric__cue {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--app-muted);
  font-size: 11px;
  white-space: nowrap;
}

.continuous-health-metric__cue .el-icon {
  font-size: 12px;
}

.continuous-health-day-list {
  display: grid;
  gap: 18px;
  min-height: 160px;
  margin-top: 16px;
}

.continuous-health-day {
  min-width: 0;
  border-top: 2px solid var(--surface-border-strong);
}

.continuous-health-day--abnormal {
  border-top-color: var(--health-danger);
}

.continuous-health-day__head {
  display: grid;
  grid-template-columns: 154px 132px minmax(420px, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 64px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-muted);
}

.continuous-health-day__date {
  display: grid;
  gap: 3px;
}

.continuous-health-day__date strong {
  color: var(--app-heading);
  font-size: 16px;
}

.continuous-health-day__date span {
  color: var(--app-muted);
  font-size: 11px;
}

.continuous-health-day__status {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.continuous-health-day__facts {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  min-width: 0;
  color: var(--app-muted);
  font-size: 12px;
}

.continuous-health-day__facts > span,
.continuous-health-day__facts > .el-button {
  flex: none;
}

.continuous-health-day__facts strong {
  margin-left: 3px;
  color: var(--app-heading);
}

.continuous-health-day__facts :deep(.el-button) {
  height: auto;
  padding: 2px 0;
}

.scope-health-detail {
  display: grid;
  gap: 12px;
  padding: 12px 0 0;
}

.site-health-block {
  overflow: hidden;
  border: 1px solid var(--surface-border);
  border-radius: 7px;
  background: var(--surface-raised);
}

.site-health-head {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px auto auto;
  align-items: center;
  gap: 16px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-muted);
}

.site-health-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.site-health-title > div,
.platform-health-name {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.site-health-title strong,
.platform-health-name strong {
  overflow: hidden;
  color: var(--app-heading);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-health-title span,
.platform-health-name span {
  color: var(--app-muted);
  font-size: 11px;
}

.site-health-score,
.continuous-health-score {
  display: grid;
  grid-template-columns: minmax(70px, 1fr) 48px;
  align-items: center;
  gap: 10px;
}

.site-health-score strong,
.continuous-health-score strong {
  color: var(--app-text);
  font-size: 13px;
  text-align: right;
}

.site-public-plans {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 6px 14px;
  border-bottom: 1px solid var(--surface-border);
}

.site-public-plans > span {
  flex: none;
  color: var(--app-muted);
  font-size: 12px;
}

.platform-health-table {
  width: 100%;
}

.continuous-health-plans {
  display: grid;
  justify-items: start;
  gap: 1px;
  min-width: 0;
  padding: 2px 0;
}

.continuous-health-plan-link {
  display: flex;
  justify-content: flex-start;
  max-width: 100%;
  height: auto;
  min-height: 24px;
  margin: 0;
  padding: 2px 0;
  font-size: 13px;
}

.continuous-health-plan-link :deep(span) {
  display: block;
  overflow: hidden;
  max-width: 100%;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-count-cell {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: center;
  gap: 4px;
}

.record-count-cell span,
.record-count-cell :deep(.el-button) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  margin: 0;
  padding: 2px 5px;
  border-radius: 4px;
  background: var(--surface-subtle);
  color: var(--app-muted);
  font-size: 11px;
}

.record-count-cell :deep(.el-button--danger) {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.record-count-cell :deep(.el-button--warning) {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
}

.continuous-health-status-help {
  width: 24px;
  height: 24px;
  color: var(--app-muted);
}

.continuous-health-status-guide {
  display: grid;
  gap: 9px;
}

.continuous-health-status-guide > strong {
  color: var(--app-heading);
  font-size: 14px;
}

.continuous-health-status-guide > div {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  align-items: start;
  gap: 10px;
}

.continuous-health-status-guide span {
  color: var(--app-text);
  font-size: 12px;
  line-height: 1.55;
}

@media (max-width: 1280px) {
  .continuous-health-metric__cue {
    display: none;
  }

  .continuous-health-metric--action {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .continuous-health-day__head {
    grid-template-columns: 142px 124px minmax(360px, 1fr) auto;
  }

  .continuous-health-day__facts {
    gap: 10px;
  }
}
</style>
