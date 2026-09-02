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

    <el-table
      v-loading="loading"
      :data="pagedRows"
      row-key="healthDate"
      :expand-row-keys="expandedDates"
      :row-class-name="dailyHealthRowClass"
      class="auto-table record-table record-table--daily continuous-health-table"
      empty-text="当前月份暂无巡检健康记录"
      @expand-change="handleExpandChange"
    >
      <el-table-column type="expand" width="46">
        <template #default="scope">
          <div class="scope-health-detail">
            <el-alert
              v-if="scope.row.unassignedPlans.length"
              type="warning"
              :closable="false"
              show-icon
              title="存在待归属计划"
            >
              <template #default>
                {{ scope.row.unassignedPlans.map((plan) => plan.planName || '未命名计划').join('、') }}，请在巡检配置中补充所属现场或主平台；这些计划暂不参与健康度计算。
              </template>
            </el-alert>

            <el-empty v-if="!scope.row.sites.length" description="当天还没有可归属到现场的巡检结果" :image-size="54" />
            <article v-for="site in scope.row.sites" v-else :key="`${scope.row.healthDate}-${site.siteId}`" class="site-health-block">
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
                  @click="$emit('day-results', { date: scope.row.healthDate, group: site, siteId: site.siteId, siteName: site.siteName })"
                >查看现场</el-button>
              </header>

              <div v-if="site.sitePlans.length" class="site-public-plans">
                <span>现场公共巡检</span>
                <el-button
                  v-for="plan in site.sitePlans"
                  :key="plan.planId"
                  type="primary"
                  link
                  @click="$emit('day-results', { date: scope.row.healthDate, group: plan, planId: plan.planId, planName: plan.planName })"
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
                        @click="$emit('day-results', { date: scope.row.healthDate, group: plan, planId: plan.planId, planName: plan.planName })"
                      >{{ plan.planName || '未命名计划' }}</el-button>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="完成 / 应执行" width="120" align="center">
                  <template #default="platformScope">{{ platformScope.row.completedCount }} / {{ platformScope.row.expectedCount }}</template>
                </el-table-column>
                <el-table-column label="异常 / 关注 / 缺失" width="150" align="center">
                  <template #default="platformScope">
                    <div class="record-count-cell">
                      <span :class="{ 'has-abnormal': platformScope.row.abnormalCount > 0 }">{{ platformScope.row.abnormalCount }}</span>
                      <span :class="{ 'has-warning': platformScope.row.warningCount > 0 }">{{ platformScope.row.warningCount }}</span>
                      <span :class="{ 'has-warning': platformScope.row.missingCount > 0 }">{{ platformScope.row.missingCount }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="88" align="center">
                  <template #default="platformScope">
                    <el-button
                      type="primary"
                      link
                      :icon="View"
                      @click="$emit('day-results', {
                        date: scope.row.healthDate,
                        group: platformScope.row,
                        siteId: site.siteId,
                        siteName: site.siteName,
                        mainPlatformId: platformScope.row.mainPlatformId,
                        mainPlatformName: platformScope.row.mainPlatformName
                      })"
                    >查看</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </article>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="归属日期" width="138" align="center" fixed="left">
        <template #default="scope">
          <div class="record-date-cell">
            <strong>{{ datePresentation(scope.row.healthDate).label }}</strong>
            <span>{{ datePresentation(scope.row.healthDate).dateKey || '-' }} {{ datePresentation(scope.row.healthDate).weekday }}</span>
            <em>现场 {{ scope.row.sites.length }} · 计划 {{ scope.row.planCount }}</em>
          </div>
        </template>
      </el-table-column>
      <el-table-column width="142" align="center">
        <template #header>
          <div class="continuous-health-status-header">
            <span>当日结论</span>
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
        </template>
        <template #default="scope">
          <el-tag :type="healthStatusType(scope.row.dayStatus)" effect="plain">
            {{ healthStatusLabel(scope.row.dayStatus, scope.row.recovered) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="现场状态" min-width="220">
        <template #default="scope">
          <div class="date-site-summary">
            <span v-for="site in scope.row.sites.slice(0, 3)" :key="site.siteId">
              <i :class="`status-dot status-dot--${site.dayStatus || '3'}`"></i>
              <strong>{{ site.siteName }}</strong>
              <em>{{ clampHealthScore(site.healthScore) }}%</em>
            </span>
            <small v-if="scope.row.sites.length > 3">另有 {{ scope.row.sites.length - 3 }} 个现场</small>
            <small v-if="!scope.row.sites.length">暂无已归属现场</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="完成 / 应执行" width="125" align="center">
        <template #default="scope">{{ scope.row.completedCount }} / {{ scope.row.expectedCount }}</template>
      </el-table-column>
      <el-table-column label="异常 / 关注 / 缺失" width="150" align="center">
        <template #default="scope">
          <div class="record-count-cell">
            <span :class="{ 'has-abnormal': scope.row.abnormalCount > 0 }">{{ scope.row.abnormalCount }}</span>
            <span :class="{ 'has-warning': scope.row.warningCount > 0 }">{{ scope.row.warningCount }}</span>
            <span :class="{ 'has-warning': scope.row.missingCount > 0 }">{{ scope.row.missingCount }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="240" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.abnormalSummary || '当天未记录异常' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="94" fixed="right" align="center">
        <template #default="scope">
          <el-button type="primary" link :icon="View" @click="$emit('day-results', { date: scope.row.healthDate, group: scope.row })">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

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
import { QuestionFilled, View } from '@element-plus/icons-vue'
import {
  clampHealthScore,
  groupDailyHealthRows,
  healthStatusLabel,
  healthStatusType,
  paginateDailyHealthRows
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

defineEmits(['update:month', 'update:scopeKey', 'update:planId', 'day-results'])

const groupedRows = computed(() => groupDailyHealthRows(props.rows))
const pageNum = ref(1)
const pageSize = ref(20)
const expandedDates = ref([])
const pagedRows = computed(() => paginateDailyHealthRows(groupedRows.value, pageNum.value, pageSize.value))

watch(() => [props.month, props.scopeKey, props.planId], () => {
  pageNum.value = 1
  expandedDates.value = []
})

watch(() => groupedRows.value.map((item) => item.healthDate), (dates) => {
  const lastPage = Math.max(1, Math.ceil(dates.length / pageSize.value))
  if (pageNum.value > lastPage) pageNum.value = lastPage
  if (!expandedDates.value.length && dates.length) expandedDates.value = [dates[0]]
}, { immediate: true })

function datePresentation(value) {
  return presentInspectionDate(value)
}

function dailyHealthRowClass({ row }) {
  return row?.dayStatus === '2' ? 'record-table-row--abnormal' : ''
}

function handlePagination({ page, limit }) {
  pageNum.value = page
  pageSize.value = limit
  expandedDates.value = []
}

function handleExpandChange(row, expandedRows = []) {
  expandedDates.value = expandedRows.some((item) => item.healthDate === row.healthDate) ? [row.healthDate] : []
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
.continuous-health-panel { min-width: 0; }
.continuous-health-query-bar { margin-bottom: 12px; padding: 12px 12px 0; border: 1px solid var(--surface-border); border-radius: 8px; background: var(--surface-muted); }
.continuous-health-table { margin-top: 14px; }
.record-table--daily :deep(.el-table__cell) { padding: 8px 0; }
.record-table--daily :deep(.record-table-row--abnormal > td.el-table__cell) { background: var(--el-color-danger-light-9); }
.scope-health-detail { display: grid; gap: 12px; margin: 8px 12px 14px 20px; padding-left: 14px; border-left: 1px solid var(--surface-border-strong); }
.site-health-block { overflow: hidden; border: 1px solid var(--surface-border); border-radius: 7px; background: var(--surface-raised); }
.site-health-head { display: grid; grid-template-columns: minmax(220px, 1fr) 180px auto auto; align-items: center; gap: 16px; padding: 12px 14px; border-bottom: 1px solid var(--surface-border); background: var(--surface-muted); }
.site-health-title { display: flex; align-items: center; gap: 10px; min-width: 0; }
.site-health-title > div, .platform-health-name { display: grid; gap: 2px; min-width: 0; }
.site-health-title strong, .platform-health-name strong { overflow: hidden; color: var(--app-heading); text-overflow: ellipsis; white-space: nowrap; }
.site-health-title span, .platform-health-name span { color: var(--app-muted); font-size: 11px; }
.site-health-score, .continuous-health-score { display: grid; grid-template-columns: minmax(70px, 1fr) 48px; align-items: center; gap: 10px; }
.site-health-score strong, .continuous-health-score strong { color: var(--app-text); font-size: 13px; text-align: right; }
.site-public-plans { display: flex; align-items: center; gap: 10px; min-height: 38px; padding: 6px 14px; border-bottom: 1px solid var(--surface-border); }
.site-public-plans > span { flex: none; color: var(--app-muted); font-size: 12px; }
.platform-health-table { width: 100%; }
.continuous-health-plans { display: grid; justify-items: start; gap: 1px; min-width: 0; padding: 2px 0; }
.continuous-health-plan-link { display: flex; justify-content: flex-start; max-width: 100%; height: auto; min-height: 24px; margin: 0; padding: 2px 0; font-size: 13px; }
.continuous-health-plan-link :deep(span) { display: block; overflow: hidden; max-width: 100%; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.record-date-cell { display: grid; gap: 2px; justify-items: center; line-height: 1.3; }
.record-date-cell strong { color: var(--app-heading); font-size: 15px; }
.record-date-cell span, .record-date-cell em { color: var(--app-muted); font-size: 10px; font-style: normal; }
.record-date-cell em { margin-top: 3px; color: var(--app-text); }
.date-site-summary { display: grid; gap: 4px; min-width: 0; }
.date-site-summary > span { display: grid; grid-template-columns: 8px minmax(0, 1fr) auto; align-items: center; gap: 7px; }
.date-site-summary strong { overflow: hidden; color: var(--app-heading); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.date-site-summary em, .date-site-summary small { color: var(--app-muted); font-size: 11px; font-style: normal; }
.date-site-summary .status-dot { width: 7px; height: 7px; }
.record-count-cell { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px; }
.record-count-cell span { padding: 3px 5px; border-radius: 4px; background: var(--surface-subtle); color: var(--app-muted); font-size: 11px; }
.record-count-cell .has-abnormal { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.record-count-cell .has-warning { background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.continuous-health-status-header { display: inline-flex; align-items: center; justify-content: center; gap: 3px; }
.continuous-health-status-help { width: 24px; height: 24px; color: var(--app-muted); }
.continuous-health-status-guide { display: grid; gap: 9px; }
.continuous-health-status-guide > strong { color: var(--app-heading); font-size: 14px; }
.continuous-health-status-guide > div { display: grid; grid-template-columns: 86px minmax(0, 1fr); align-items: start; gap: 10px; }
.continuous-health-status-guide span { color: var(--app-text); font-size: 12px; line-height: 1.55; }
</style>
