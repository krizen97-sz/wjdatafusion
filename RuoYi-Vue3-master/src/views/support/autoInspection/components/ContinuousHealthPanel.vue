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
      <el-table-column type="expand" width="44">
        <template #default="scope">
          <div class="continuous-plan-list">
            <div v-for="plan in scope.row.plans" :key="`${scope.row.healthDate}-${plan.planId}`" class="continuous-plan-row">
              <el-tag :type="healthStatusType(plan.dayStatus)" effect="plain">
                {{ healthStatusLabel(plan.dayStatus, plan.dayStatus === '2' && plan.lastResultStatus !== '2') }}
              </el-tag>
              <div class="continuous-plan-row__name">
                <strong>{{ plan.planName || '未命名计划' }}</strong>
                <span>{{ plan.templateName || '未绑定模板' }}</span>
              </div>
              <div class="continuous-plan-row__progress">
                <el-progress :percentage="clampHealthScore(plan.healthScore)" :stroke-width="8" :show-text="false" />
                <span>{{ clampHealthScore(plan.healthScore) }}%</span>
              </div>
              <div class="continuous-plan-row__counts">
                <span>完成 {{ plan.completedCount }}/{{ plan.expectedCount }}</span>
                <span>异常 {{ plan.abnormalCount }} · 关注 {{ plan.warningCount }} · 缺失 {{ plan.missingCount }}</span>
              </div>
              <p>{{ plan.abnormalSummary || '当天未记录异常内容' }}</p>
              <el-button link type="primary" icon="View" @click="$emit('samples', { date: scope.row.healthDate, plan })">查看采样</el-button>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="日期" prop="healthDate" width="130">
        <template #default="scope"><strong class="continuous-health-date">{{ scope.row.healthDate }}</strong></template>
      </el-table-column>
      <el-table-column label="当日结论" width="130" align="center">
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
      <el-table-column label="计划" width="80" align="center">
        <template #default="scope">{{ scope.row.plans.length }}</template>
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
    </el-table>
  </section>
</template>

<script setup>
import { computed } from 'vue'
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

defineEmits(['update:month', 'update:planId', 'samples'])

const groupedRows = computed(() => groupDailyHealthRows(props.rows))
const summary = computed(() => summarizeDailyHealth(groupedRows.value))
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
  border-top: 1px solid var(--surface-border);
  border-bottom: 1px solid var(--surface-border);
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

.continuous-plan-list {
  display: grid;
  gap: 8px;
  padding: 4px 18px 8px 54px;
}

.continuous-plan-row {
  display: grid;
  grid-template-columns: 108px minmax(170px, 1fr) 150px 190px minmax(220px, 1.4fr) 82px;
  align-items: center;
  gap: 14px;
  min-height: 60px;
  padding: 9px 12px;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background: var(--surface-strong);
}

.continuous-plan-row__name,
.continuous-plan-row__counts {
  display: grid;
  gap: 3px;
}

.continuous-plan-row__name strong {
  color: var(--app-heading);
  font-size: 13px;
}

.continuous-plan-row__name span,
.continuous-plan-row__counts span,
.continuous-plan-row p {
  color: var(--app-muted);
  font-size: 12px;
}

.continuous-plan-row__progress {
  display: grid;
  grid-template-columns: 1fr 42px;
  align-items: center;
  gap: 8px;
}

.continuous-plan-row__progress span {
  color: var(--app-text);
  font-size: 12px;
  text-align: right;
}

.continuous-plan-row p {
  overflow: hidden;
  margin: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
