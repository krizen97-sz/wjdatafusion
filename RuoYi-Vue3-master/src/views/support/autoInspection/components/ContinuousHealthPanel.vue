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
      <el-form-item label="计划">
        <el-tree-select
          :model-value="planId"
          :data="planOptions"
          node-key="value"
          clearable
          filterable
          :render-after-expand="false"
          placeholder="全部高频计划"
          style="width: 210px"
          @update:model-value="$emit('update:planId', $event)"
        />
      </el-form-item>
    </el-form>

    <div class="continuous-health-summary">
      <span><em>本月健康度</em><strong>{{ summary.healthScore }}%</strong></span>
      <span><em>已监测天数</em><strong>{{ summary.dayCount }}</strong></span>
      <span class="is-danger"><em>异常日期</em><strong>{{ summary.abnormalDays }}</strong></span>
      <span class="is-warning"><em>关注日期</em><strong>{{ summary.warningDays }}</strong></span>
    </div>

    <el-table
      v-loading="loading"
      :data="pagedRows"
      row-key="healthDate"
      :row-class-name="dailyHealthRowClass"
      class="auto-table record-table record-table--daily continuous-health-table"
      empty-text="当前月份暂无高频健康记录"
    >
      <el-table-column label="归属日期" width="132" align="center" fixed="left">
        <template #default="scope">
          <div class="record-date-cell">
            <strong>{{ datePresentation(scope.row.healthDate).label }}</strong>
            <span>{{ datePresentation(scope.row.healthDate).dateKey || '-' }} {{ datePresentation(scope.row.healthDate).weekday }}</span>
            <em>共 {{ scope.row.plans.length }} 个计划 · 异常 {{ scope.row.abnormalCount }}</em>
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
      <el-table-column label="健康度" width="170">
        <template #default="scope">
          <div class="continuous-health-score">
            <el-progress :percentage="clampHealthScore(scope.row.healthScore)" :stroke-width="8" :show-text="false" />
            <strong>{{ clampHealthScore(scope.row.healthScore) }}%</strong>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="执行计划" min-width="220">
        <template #default="scope">
          <div class="continuous-health-plans">
            <el-button
              v-for="plan in scope.row.plans"
              :key="`${scope.row.healthDate}-${plan.planId}`"
              type="primary"
              link
              class="continuous-health-plan-link"
              @click.stop="$emit('day-results', {
                date: scope.row.healthDate,
                group: plan,
                planId: plan.planId,
                planName: plan.planName
              })"
            >{{ plan.planName || '未命名计划' }}</el-button>
            <span v-if="!scope.row.plans.length" class="continuous-health-plan-empty">-</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="完成 / 应执行" width="125" align="center">
        <template #default="scope">{{ scope.row.completedCount }} / {{ scope.row.expectedCount }}</template>
      </el-table-column>
      <el-table-column label="异常 / 关注 / 缺失" width="150" align="center">
        <template #default="scope">
          <div class="record-count-cell">
            <span :class="{ 'has-abnormal': Number(scope.row.abnormalCount || 0) > 0 }" title="异常">{{ scope.row.abnormalCount }}</span>
            <span :class="{ 'has-warning': Number(scope.row.warningCount || 0) > 0 }" title="关注">{{ scope.row.warningCount }}</span>
            <span :class="{ 'has-warning': Number(scope.row.missingCount || 0) > 0 }" title="缺失">{{ scope.row.missingCount }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="240">
        <template #default="scope">
          <div class="record-result-summary" :class="{ 'has-abnormal': scope.row.dayStatus === '2' }">
            <strong>{{ scope.row.abnormalSummary || '当天未记录异常' }}</strong>
            <span>完成 {{ scope.row.completedCount }} 次，正常 {{ scope.row.normalCount }} 次</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="94" fixed="right" align="center" class-name="continuous-health-action-column">
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
import { computed } from 'vue'
import { QuestionFilled, View } from '@element-plus/icons-vue'
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
  planId: { type: [Number, String], default: undefined },
  planOptions: { type: Array, default: () => [] }
})

defineEmits(['update:month', 'update:planId', 'day-results'])

const groupedRows = computed(() => groupDailyHealthRows(props.rows))
const pageNum = ref(1)
const pageSize = ref(20)
const pagedRows = computed(() => paginateDailyHealthRows(groupedRows.value, pageNum.value, pageSize.value))
const summary = computed(() => summarizeDailyHealth(groupedRows.value))

watch(() => [props.month, props.planId], () => {
  pageNum.value = 1
})

watch(() => groupedRows.value.length, (total) => {
  const lastPage = Math.max(1, Math.ceil(total / pageSize.value))
  if (pageNum.value > lastPage) pageNum.value = lastPage
})

function datePresentation(value) {
  return presentInspectionDate(value)
}

function dailyHealthRowClass({ row }) {
  return row?.dayStatus === '2' ? 'record-table-row--abnormal' : ''
}

function handlePagination({ page, limit }) {
  pageNum.value = page
  pageSize.value = limit
}

const statusGuide = [
  { label: '正常', type: 'success', description: '当天已执行，且没有异常、关注或缺失采样。' },
  { label: '需要关注', type: 'warning', description: '当天存在关注结果或缺失采样，但没有确认异常。' },
  { label: '异常持续中', type: 'danger', description: '当天出现异常，且至少一个计划最近一次执行仍然异常。' },
  { label: '异常已恢复', type: 'danger', description: '当天出现过异常，但相关计划最近一次执行已经恢复。' },
  { label: '尚未执行', type: 'info', description: '当天还没有形成有效执行结果。' }
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

.continuous-health-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  overflow: hidden;
  border: 1px solid var(--surface-border);
  border-radius: 7px;
  background: var(--surface-muted);
}

.continuous-health-summary > span {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-right: 1px solid var(--surface-border);
}

.continuous-health-summary > span:last-child {
  border-right: 0;
}

.continuous-health-summary em {
  color: var(--app-muted);
  font-style: normal;
  font-size: 12px;
}

.continuous-health-summary strong {
  color: var(--app-heading);
  font-size: 20px;
}

.continuous-health-summary .is-danger strong {
  color: var(--health-danger);
}

.continuous-health-summary .is-warning strong {
  color: var(--health-warning);
}

.continuous-health-status-header {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
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

.continuous-health-plan-empty {
  color: var(--app-muted);
}

.continuous-health-table {
  margin-top: 14px;
}

.record-table--daily :deep(.el-table__cell) {
  padding: 8px 0;
}

.record-table--daily :deep(.record-table-row--abnormal > td.el-table__cell) {
  background: var(--el-color-danger-light-9);
}

.record-date-cell {
  display: grid;
  gap: 2px;
  justify-items: center;
  line-height: 1.3;
}

.record-date-cell strong {
  color: var(--app-heading);
  font-size: 15px;
}

.record-date-cell span,
.record-date-cell em {
  color: var(--app-muted);
  font-size: 10px;
  font-style: normal;
}

.record-date-cell em {
  margin-top: 3px;
  color: var(--app-text);
}

.continuous-health-score {
  display: grid;
  grid-template-columns: minmax(70px, 1fr) 48px;
  align-items: center;
  gap: 10px;
}

.continuous-health-score strong {
  color: var(--app-text);
  font-size: 13px;
  text-align: right;
}

.record-result-summary {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.record-result-summary strong,
.record-result-summary span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-result-summary strong {
  color: var(--app-heading);
  font-size: 13px;
}

.record-result-summary span {
  color: var(--app-muted);
  font-size: 11px;
}

.record-result-summary.has-abnormal strong {
  color: var(--el-color-danger);
}

.record-count-cell {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
}

.record-count-cell span {
  padding: 3px 5px;
  border-radius: 4px;
  background: var(--surface-subtle);
  color: var(--app-muted);
  font-size: 11px;
}

.record-count-cell .has-abnormal {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.record-count-cell .has-warning {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
}

.continuous-health-table :deep(.continuous-health-action-column .cell) {
  overflow: visible;
  padding-right: 8px;
  padding-left: 8px;
  text-overflow: clip;
}

</style>
