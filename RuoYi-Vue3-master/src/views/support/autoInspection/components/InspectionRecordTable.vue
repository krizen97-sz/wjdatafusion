<template>
  <el-table
    v-loading="loading"
    :data="tableRows"
    row-key="recordId"
    :span-method="spanMethod"
    :row-class-name="rowClassName"
    :expand-row-keys="expandedKeys"
    class="inspection-record-table"
    :empty-text="emptyText"
    @expand-change="(row, rows) => emit('expand-change', row, rows)"
  >
    <el-table-column v-if="expandable" type="expand" width="44">
      <template #default="scope"><slot name="expand" :row="scope.row" /></template>
    </el-table-column>
    <el-table-column label="归属日期" prop="ownershipDateKey" width="120" align="center" class-name="record-date-column">
      <template #default="{ row }">
        <div class="record-date-cell">
          <strong>{{ row.ownershipDateLabel }}</strong>
          <span>{{ row.ownershipDateKey || '-' }}</span>
          <span>{{ row.ownershipWeekday }}</span>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="时间" width="86" align="center">
      <template #default="{ row }"><strong class="record-clock">{{ timeLabel(row.inspectionTime) }}</strong></template>
    </el-table-column>
    <el-table-column label="结果" width="70" align="center">
      <template #default="{ row }"><el-tag size="small" effect="plain" :type="resultTone(row.resultStatus)">{{ resultLabel(row.resultStatus) }}</el-tag></template>
    </el-table-column>
    <el-table-column label="来源" width="60" align="center">
      <template #default="{ row }"><span class="record-source">{{ row.sourceType === 'MANUAL' ? '手动' : '自动' }}</span></template>
    </el-table-column>
    <el-table-column label="巡检模板" min-width="160">
      <template #default="{ row }">
        <div class="record-name-cell">
          <strong>{{ row.templateName || '未命名模板' }}</strong>
          <el-tag v-if="templateLabel(row.templateId)" size="small" effect="plain" type="info">{{ templateLabel(row.templateId) }}</el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="执行计划" min-width="170">
      <template #default="{ row }">
        <div class="record-name-cell">
          <strong>{{ row.planName || (row.sourceType === 'MANUAL' ? '手动执行' : '未命名计划') }}</strong>
          <el-tag v-if="planLabel(row.planId)" size="small" effect="plain" type="info">{{ planLabel(row.planId) }}</el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="结果摘要" min-width="200">
      <template #default="{ row }">
        <el-tooltip :content="row.abnormalSummary || row.summary || '暂无摘要'" :show-after="300" placement="top">
          <div class="record-result-summary" :class="{ 'is-abnormal': String(row.resultStatus) === '2' }" tabindex="0">
            {{ row.abnormalSummary || row.summary || '暂无摘要' }}
          </div>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column label="步骤 / 子项 / 异常" width="144" align="center">
      <template #default="{ row }">
        <span class="record-counts">{{ row.enabledStepCount || 0 }} / {{ row.targetCount || 0 }} / <strong :class="{ 'is-abnormal': Number(row.abnormalCount) > 0 }">{{ row.abnormalCount || 0 }}</strong></span>
      </template>
    </el-table-column>
    <el-table-column v-if="$slots.actions" label="操作" width="118" align="center">
      <template #default="{ row }"><div class="record-actions"><slot name="actions" :row="row" /></div></template>
    </el-table-column>
    <el-table-column v-else label="耗时" width="90" align="center">
      <template #default="{ row }">{{ row.durationMs == null ? '-' : `${row.durationMs}ms` }}</template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { computed } from 'vue'
import { buildInspectionRecordTableRows } from '../overviewPresentation'
import { resultLabel, resultTone } from '../recordResultPresentation'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: Boolean,
  expandable: Boolean,
  expandedKeys: { type: Array, default: () => [] },
  emptyText: { type: String, default: '暂无巡检记录' },
  templateLabel: { type: Function, default: () => '' },
  planLabel: { type: Function, default: () => '' }
})
const emit = defineEmits(['expand-change'])
const tableRows = computed(() => buildInspectionRecordTableRows(props.rows))
function spanMethod({ row, column }) {
  // Expanded rows span the full table; a merged date cell would overlap them.
  if (props.expandable || column.property !== 'ownershipDateKey') return [1, 1]
  return row.ownershipRowspan ? [row.ownershipRowspan, 1] : [0, 0]
}
function rowClassName({ row }) {
  return String(row.resultStatus) === '2' ? 'record-row--abnormal' : ''
}
function timeLabel(value) {
  return String(value || '').match(/\d{2}:\d{2}(?::\d{2})?/)?.[0] || '-'
}
</script>

<style scoped lang="scss">
.inspection-record-table {
  width: 100%;
  :deep(.el-table__cell) { padding: 10px 0; }
  :deep(.record-date-column) { vertical-align: top; }
  :deep(.record-row--abnormal > td) { background: var(--el-color-danger-light-9); }
  :deep(.el-table__expanded-cell) { padding: 0 12px 12px; background: var(--surface-muted); }
  :deep(th.el-table__cell) { background: var(--surface-strong); color: var(--app-heading); }
}
.record-date-cell { display: grid; gap: 2px; line-height: 1.5; }
.record-date-cell strong, .record-clock { color: var(--app-heading); font-variant-numeric: tabular-nums; }
.record-date-cell span, .record-source { color: var(--app-muted); font-size: var(--el-font-size-extra-small); }
.record-name-cell { display: grid; justify-items: start; min-width: 0; gap: 4px; }
.record-name-cell strong { font-weight: 500; color: var(--app-heading); white-space: normal; overflow-wrap: anywhere; }
.record-name-cell .el-tag { max-width: 100%; height: auto; min-height: var(--el-tag-height); line-height: 1.4; }
.record-name-cell :deep(.el-tag__content) { white-space: normal; overflow-wrap: anywhere; }
.record-result-summary { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; white-space: normal; overflow-wrap: anywhere; }
.record-result-summary:focus-visible { outline: 2px solid var(--el-color-primary); }
.record-counts { white-space: nowrap; font-variant-numeric: tabular-nums; }
.record-counts strong { font-weight: 500; }
.is-abnormal { color: var(--el-color-danger); }
.record-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px; }
.record-actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>
