<template>
  <section class="continuous-health-panel">
    <header class="continuous-health-toolbar">
      <div class="continuous-health-toolbar__filters">
        <el-date-picker
          :model-value="month"
          type="month"
          value-format="YYYY-MM"
          format="YYYY年MM月"
          :clearable="false"
          style="width: 150px"
          @update:model-value="$emit('update:month', $event)"
        />
        <el-tree-select
          :model-value="planId"
          :data="planOptions"
          node-key="value"
          clearable
          filterable
          :render-after-expand="false"
          placeholder="全部高频计划"
          style="width: 220px"
          @update:model-value="$emit('update:planId', $event)"
        />
      </div>
    </header>

    <div class="continuous-health-summary">
      <span><em>本月健康度</em><strong>{{ summary.healthScore }}%</strong></span>
      <span><em>已监测天数</em><strong>{{ summary.dayCount }}</strong></span>
      <span class="is-danger"><em>异常日期</em><strong>{{ summary.abnormalDays }}</strong></span>
      <span class="is-warning"><em>关注日期</em><strong>{{ summary.warningDays }}</strong></span>
    </div>

    <el-table v-loading="loading" :data="groupedRows" row-key="healthDate" class="continuous-health-table" empty-text="当前月份暂无高频健康记录">
      <el-table-column label="日期" prop="healthDate" width="130">
        <template #default="scope"><strong class="continuous-health-date">{{ scope.row.healthDate }}</strong></template>
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
      <el-table-column label="健康度" width="190">
        <template #default="scope">
          <div class="continuous-health-score">
            <el-progress :percentage="clampHealthScore(scope.row.healthScore)" :stroke-width="8" :show-text="false" />
            <strong>{{ clampHealthScore(scope.row.healthScore) }}%</strong>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="计划" min-width="240">
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
      <el-table-column label="完成 / 应执行" width="130" align="center">
        <template #default="scope">{{ scope.row.completedCount }} / {{ scope.row.expectedCount }}</template>
      </el-table-column>
      <el-table-column label="异常 / 关注 / 缺失" width="155" align="center">
        <template #default="scope">
          <span class="continuous-health-counts"><b>{{ scope.row.abnormalCount }}</b> / {{ scope.row.warningCount }} / {{ scope.row.missingCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="异常摘要" min-width="260" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.abnormalSummary || '当天未记录异常' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="94" fixed="right" align="center" class-name="continuous-health-action-column">
        <template #default="scope">
          <el-button type="primary" link :icon="View" @click="$emit('day-results', { date: scope.row.healthDate, group: scope.row })">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
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
  summarizeDailyHealth
} from '../continuousHealthPresentation'

const props = defineProps({
  loading: { type: Boolean, default: false },
  rows: { type: Array, default: () => [] },
  month: { type: String, default: '' },
  planId: { type: [Number, String], default: undefined },
  planOptions: { type: Array, default: () => [] }
})

defineEmits(['update:month', 'update:planId', 'day-results'])

const groupedRows = computed(() => groupDailyHealthRows(props.rows))
const summary = computed(() => summarizeDailyHealth(groupedRows.value))

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

.continuous-health-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
  padding: 12px 0;
}

.continuous-health-toolbar__filters {
  display: flex;
  align-items: center;
  gap: 10px;
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

.continuous-health-summary .is-danger strong,
.continuous-health-counts b {
  color: #d84a4a;
}

.continuous-health-summary .is-warning strong {
  color: #c78322;
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

.continuous-health-date {
  color: var(--app-heading);
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

.continuous-health-counts b {
  font-weight: 700;
}

.continuous-health-table :deep(.continuous-health-action-column .cell) {
  overflow: visible;
  padding-right: 8px;
  padding-left: 8px;
  text-overflow: clip;
}

</style>
