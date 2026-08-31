<template>
  <el-drawer
    :model-value="modelValue"
    title="修改记录"
    size="520px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadRecords"
  >
    <div class="records-drawer">
      <section class="record-document">
        <span class="file-mark" :class="`is-${document?.fileType || 'docx'}`">
          {{ ['xls', 'xlsx'].includes(document?.fileType) ? 'X' : 'W' }}
        </span>
        <div>
          <strong>{{ document?.title || '未选择文档' }}</strong>
          <span>当前服务器版本 {{ currentVersionNo }} · 仅记录修改人员和时间</span>
        </div>
      </section>

      <el-tabs v-model="activeTab" class="record-tabs motion-tabs" stretch>
        <el-tab-pane name="versions">
          <template #label>
            <span class="motion-control-label">
              <svg-icon icon-class="keyline-clock-arrow-left" class="motion-control-label__icon" />
              <span class="motion-control-label__text">修改记录</span>
            </span>
          </template>
          <div v-loading="loading.records" class="version-list">
            <article v-for="version in versions" :key="version.versionId" class="version-item" :class="{ 'is-current': version.current }">
              <span class="version-dot" />
              <div class="version-copy">
                <div class="version-title">
                  <strong>版本 {{ version.versionNo }}</strong>
                  <el-tag v-if="version.current" size="small" type="success" effect="plain">当前版本</el-tag>
                </div>
                <span>{{ version.creatorName || '在线编辑器' }} 修改</span>
                <time>{{ version.createTime || '-' }}</time>
              </div>
            </article>
            <el-empty v-if="!loading.records && !versions.length" :image-size="72" description="暂无修改记录" />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="isOwner" name="operations">
          <template #label>
            <span class="motion-control-label">
              <svg-icon icon-class="keyline-list-sort" class="motion-control-label__icon" />
              <span class="motion-control-label__text">其他操作</span>
            </span>
          </template>
          <el-segmented v-model="operationFilter" class="operation-filter motion-segmented" :options="RECORD_FILTERS" block aria-label="操作记录筛选">
            <template #default="{ item }">
              <span class="motion-control-label">
                <svg-icon :icon-class="item.icon" class="motion-control-label__icon" />
                <span class="motion-control-label__text">{{ item.label }}</span>
              </span>
            </template>
          </el-segmented>
          <div v-loading="loading.records" class="operation-list">
            <article v-for="operation in visibleOperations" :key="operation.logId" class="operation-item">
              <span class="operation-icon"><el-icon><Clock /></el-icon></span>
              <div>
                <strong>{{ operationLabel(operation.actionType) }}</strong>
                <span>{{ operationSummary(operation) }}</span>
                <time>{{ operation.createTime || '-' }}</time>
              </div>
            </article>
            <el-empty v-if="!loading.records && !visibleOperations.length" :image-size="72" description="当前筛选下暂无操作记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import {
  listDocumentOperations,
  listDocumentVersions
} from '@/api/document/workspace.js'
import {
  RECORD_FILTERS,
  filterOperations,
  operationLabel,
  operationSummary
} from '../workspace/documentRecordRules.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  document: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])

const activeTab = ref('versions')
const operationFilter = ref('ALL')
const versions = ref([])
const operations = ref([])
const loading = reactive({ records: false })

const isOwner = computed(() => props.document?.accessPermission === 'OWNER')
const currentVersionNo = computed(() => versions.value.find((item) => item.current)?.versionNo || props.document?.contentVersion || 1)
const visibleOperations = computed(() => filterOperations(operations.value, operationFilter.value))

async function loadRecords() {
  if (!props.document?.documentId) return
  activeTab.value = 'versions'
  operationFilter.value = 'ALL'
  loading.records = true
  try {
    const requests = [listDocumentVersions(props.document.documentId)]
    if (isOwner.value) requests.push(listDocumentOperations(props.document.documentId))
    const [versionResponse, operationResponse] = await Promise.all(requests)
    versions.value = versionResponse.data || []
    operations.value = operationResponse?.data || []
  } finally {
    loading.records = false
  }
}

</script>

<style scoped lang="scss">
.records-drawer { color: var(--app-heading); }

.record-document {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

  > div { min-width: 0; }
  strong,
  span { display: block; }
  strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  span { margin-top: 3px; color: var(--app-muted); font-size: 12px; }
}

.file-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 6px;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  font-weight: 750;
  &.is-xls,
  &.is-xlsx { background: var(--el-color-success); }
}

.record-tabs { margin-top: 14px; }
.version-list,
.operation-list { min-height: 140px; }

.version-item {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr);
  align-items: flex-start;
  gap: 11px;
  padding: 14px 0;
  border-bottom: 1px solid var(--surface-muted);

  &.is-current { background: linear-gradient(90deg, color-mix(in srgb, var(--el-color-success) 5%, transparent), transparent 75%); }
}

.version-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border: 2px solid var(--el-color-primary);
  border-radius: 50%;
  background: var(--surface-strong);
}

.is-current .version-dot { border-color: var(--el-color-success); background: var(--el-color-success-light-9); }
.version-copy { min-width: 0; }
.version-title { display: flex; align-items: center; gap: 7px; }
.version-copy > span,
.version-copy > time { display: block; margin-top: 4px; color: var(--app-muted); font-size: 12px; }
.version-copy > time { color: var(--app-muted); }

.operation-filter {
  margin: 2px 0 8px;
}

.operation-item {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 0;
  border-bottom: 1px solid var(--surface-muted);
  > div { min-width: 0; flex: 1; }
  strong,
  span,
  time { display: block; }
  strong { font-size: 13px; }
  span { margin-top: 4px; color: var(--app-muted); font-size: 12px; line-height: 1.55; }
  time { margin-top: 4px; color: var(--app-muted); font-size: 11px; }
}

.operation-icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 6px;
  background: var(--surface-subtle);
  color: var(--el-color-primary);
}

@media (max-width: 640px) {
  .version-item { grid-template-columns: 12px minmax(0, 1fr); }
}
</style>
