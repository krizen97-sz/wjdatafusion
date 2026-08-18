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

      <el-tabs v-model="activeTab" class="record-tabs" stretch>
        <el-tab-pane label="修改记录" name="versions">
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

        <el-tab-pane v-if="isOwner" label="其他操作" name="operations">
          <div class="operation-filter" role="group" aria-label="操作记录筛选">
            <button
              v-for="filter in RECORD_FILTERS"
              :key="filter.value"
              type="button"
              :class="{ 'is-active': operationFilter === filter.value }"
              @click="operationFilter = filter.value"
            >{{ filter.label }}</button>
          </div>
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
.records-drawer { color: #172033; }

.record-document {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #dde4ee;
  border-radius: 8px;
  background: #f7f9fc;

  > div { min-width: 0; }
  strong,
  span { display: block; }
  strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  span { margin-top: 3px; color: #667085; font-size: 12px; }
}

.file-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
  font-weight: 750;
  &.is-xls,
  &.is-xlsx { background: #15803d; }
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
  border-bottom: 1px solid #edf0f4;

  &.is-current { background: linear-gradient(90deg, rgba(21, 128, 61, 0.045), transparent 75%); }
}

.version-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border: 2px solid #1677ff;
  border-radius: 50%;
  background: #fff;
}

.is-current .version-dot { border-color: #15803d; background: #dcfce7; }
.version-copy { min-width: 0; }
.version-title { display: flex; align-items: center; gap: 7px; }
.version-copy > span,
.version-copy > time { display: block; margin-top: 4px; color: #667085; font-size: 12px; }
.version-copy > time { color: #98a2b3; }

.operation-filter {
  display: flex;
  gap: 4px;
  margin: 2px 0 8px;
  padding: 3px;
  border-radius: 7px;
  background: #f2f4f7;

  button {
    flex: 1;
    padding: 6px 4px;
    border: 0;
    border-radius: 5px;
    background: transparent;
    color: #667085;
    cursor: pointer;
    font-size: 12px;
  }
  button:hover,
  button:focus-visible,
  button.is-active { outline: none; background: #fff; color: #0f5eba; box-shadow: 0 1px 2px rgba(16, 24, 40, 0.08); }
}

.operation-item {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 13px 0;
  border-bottom: 1px solid #edf0f4;
  > div { min-width: 0; flex: 1; }
  strong,
  span,
  time { display: block; }
  strong { font-size: 13px; }
  span { margin-top: 4px; color: #475467; font-size: 12px; line-height: 1.55; }
  time { margin-top: 4px; color: #98a2b3; font-size: 11px; }
}

.operation-icon {
  display: grid;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  place-items: center;
  border-radius: 6px;
  background: #edf4ff;
  color: #0f5eba;
}

@media (max-width: 640px) {
  .version-item { grid-template-columns: 12px minmax(0, 1fr); }
}
</style>
