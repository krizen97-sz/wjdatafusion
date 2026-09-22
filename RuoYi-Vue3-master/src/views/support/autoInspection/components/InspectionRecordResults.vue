<template>
  <div class="inspection-record-results">
    <el-empty v-if="!groups.length" description="暂无步骤结果" :image-size="64" />
    <section v-for="(group, groupIndex) in groups" :key="group.key" class="result-step">
      <header class="result-step__head">
        <span class="result-step__index">{{ groupIndex + 1 }}</span>
        <div class="result-step__name"><strong>{{ group.stepName || '未命名步骤' }}</strong><span>{{ group.toolName || '巡检步骤' }}</span></div>
        <span class="result-step__count">子项 {{ group.targets.length }} · 异常 {{ group.abnormalCount }}</span>
      </header>
      <el-table v-if="group.targets.length" :data="group.targets" class="inspection-target-table" size="small">
        <el-table-column label="检查子项" min-width="220">
          <template #default="{ row, $index }">
            <div class="target-name"><strong>{{ row.targetName || `检查子项 ${$index + 1}` }}</strong></div>
            <span v-if="row.baselineFlag === 'Y'" class="target-baseline">基线已建立</span>
          </template>
        </el-table-column>
        <el-table-column label="判定数据" min-width="230">
          <template #default="{ row }">
            <dl class="target-values">
              <div><dt>本次</dt><dd>{{ formatters.actual(row) }}</dd></div>
              <div><dt>上次</dt><dd>{{ formatters.metric(row.previousValue, row.actualUnit) }}</dd></div>
              <div><dt>变化</dt><dd>{{ formatters.change(row.changeValue, row.actualUnit) }}</dd></div>
            </dl>
          </template>
        </el-table-column>
        <el-table-column label="判定规则" min-width="210">
          <template #default="{ row }">
            <div class="target-rule"><span>{{ formatters.mode(row) }}</span><strong>{{ row.evaluationRule || formatters.threshold(group) }}</strong><small v-if="row.evaluationMode === 'PREVIOUS'">{{ formatters.window(row) }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="调用结果" min-width="270">
          <template #default="{ row }">
            <el-popover placement="left" :width="520" trigger="hover" :show-after="250" :hide-after="80">
              <template #reference>
                <div class="target-call" tabindex="0" aria-label="查看完整调用结果">
                  <span class="target-call__preview">{{ formatters.detail(row) }}</span>
                  <strong v-if="row.errorMessage" class="target-call__error">{{ row.errorMessage }}</strong>
                </div>
              </template>
              <div class="target-call-detail">
                <strong>{{ row.targetName || '调用结果' }}</strong>
                <el-scrollbar max-height="320px">
                  <p>{{ formatters.detail(row) }}</p>
                  <p v-if="row.errorMessage" class="target-call__error">{{ row.errorMessage }}</p>
                </el-scrollbar>
              </div>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" effect="plain" :type="resultTone(row.resultStatus)">{{ resultLabel(row.resultStatus) }}</el-tag></template>
        </el-table-column>
      </el-table>
      <div v-else class="result-step__empty">
        <el-tag size="small" effect="plain" :type="resultTone(group.resultStatus)">{{ resultLabel(group.resultStatus) }}</el-tag>
        <span>{{ group.errorMessage || group.resultSummary || '当前步骤暂无检查子项结果' }}</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { getRecordResultGroups, resultLabel, resultTone } from '../recordResultPresentation'
const props = defineProps({ record: { type: Object, default: () => ({}) }, formatters: { type: Object, required: true } })
const groups = computed(() => getRecordResultGroups(props.record))
</script>

<style scoped lang="scss">
.inspection-record-results { min-width: 0; width: 100%; padding: 2px 12px 12px; background: var(--surface-muted); color: var(--app-text); }
.result-step { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--surface-border); }
.result-step__head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.result-step__index {
  display: grid;
  flex: 0 0 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: var(--el-font-size-extra-small);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.result-step__name { display: grid; flex: 1; min-width: 0; gap: 2px; }
.result-step__name strong { color: var(--app-heading); overflow-wrap: anywhere; }
.result-step__name span, .result-step__count, .target-baseline { color: var(--app-muted); font-size: var(--el-font-size-extra-small); }
.result-step__count { flex: 0 0 auto; }
.inspection-target-table {
  --el-table-bg-color: var(--surface-bg);
  --el-table-tr-bg-color: var(--surface-bg);
  --el-table-header-bg-color: var(--surface-strong);
  width: 100%;
  :deep(th.el-table__cell) { background: var(--surface-strong); color: var(--app-heading); }
  :deep(.el-table__cell) { padding: 10px 0; vertical-align: top; }
  :deep(.cell) { white-space: normal; overflow-wrap: anywhere; }
}
.target-name { display: grid; min-width: 0; }
.target-name strong { min-width: 0; font-weight: 500; color: var(--app-heading); white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.target-values { display: grid; gap: 5px; margin: 0; }
.target-values > div {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: baseline;
  gap: 5px;
  padding: 4px 6px;
  border-radius: var(--el-border-radius-base);
  background: var(--surface-subtle);
}
.target-values dt { color: var(--app-muted); }
.target-values dd { margin: 0; color: var(--app-heading); overflow-wrap: anywhere; font-variant-numeric: tabular-nums; }
.target-rule { display: grid; gap: 4px; }
.target-rule span, .target-rule small { color: var(--app-muted); }
.target-rule strong { font-weight: 500; white-space: normal; overflow-wrap: anywhere; }
.target-call { cursor: help; min-width: 0; padding: 4px 6px; border-radius: var(--el-border-radius-base); }
.target-call:hover { background: var(--surface-subtle); }
.target-call:focus-visible { outline: 2px solid var(--el-color-primary); }
.target-call__preview, .target-call > .target-call__error { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; }
.target-call__error { color: var(--el-color-danger); font-weight: 500; }
.target-call-detail { color: var(--app-text); }
.target-call-detail strong, .target-call-detail p { overflow-wrap: anywhere; white-space: pre-wrap; }
.target-call-detail p { line-height: 1.6; }
.result-step__empty { display: flex; align-items: baseline; gap: 12px; color: var(--app-muted); overflow-wrap: anywhere; }
</style>
