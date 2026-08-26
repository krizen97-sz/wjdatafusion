<template>
  <el-dialog v-model="open" title="关联现有文档" width="760px" append-to-body destroy-on-close>
    <div class="document-selector-toolbar">
      <el-input
        v-model="keyword"
        clearable
        prefix-icon="Search"
        placeholder="搜索我的文档和与我共享的文档"
        @keyup.enter="loadCandidates"
      />
      <el-button type="primary" :loading="loading" @click="loadCandidates">搜索</el-button>
    </div>
    <p class="document-selector-note">
      这里只建立文档ID关联，不复制文件或权限。阅读者打开附件时仍执行文档管理的实时权限校验。
    </p>
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="candidates"
      height="410"
      row-key="documentId"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" :selectable="canSelect" reserve-selection />
      <el-table-column label="文档" min-width="260">
        <template #default="{ row }">
          <div class="candidate-document">
            <el-tag size="small" effect="plain">{{ String(row.fileType || 'FILE').toUpperCase() }}</el-tag>
            <span><strong>{{ row.title }}</strong><small>{{ row.folderName || '根目录' }}</small></span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="ownerName" label="所有者" width="110" />
      <el-table-column label="权限" width="92">
        <template #default="{ row }">{{ permissionLabel(row) }}</template>
      </el-table-column>
      <el-table-column label="大小" width="90">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="150" />
    </el-table>
    <template #footer>
      <div class="document-selector-footer">
        <span>已选择 {{ selectedRows.length }} / 20 份文档</span>
        <div><el-button @click="open = false">取消</el-button><el-button type="primary" @click="confirmSelection">确认关联</el-button></div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { listKnowledgeDocumentCandidates } from '@/api/knowledge/index.js'
import { formatFileSize } from '@/views/document/workspace/documentWorkspaceRules.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  selectedDocuments: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const tableRef = ref()
const keyword = ref('')
const loading = ref(false)
const candidates = ref([])
const selectedById = ref(new Map())
const syncingSelection = ref(false)
const selectedRows = computed(() => [...selectedById.value.values()])

watch(open, async (value) => {
  if (!value) return
  keyword.value = ''
  selectedById.value = new Map(props.selectedDocuments.map((item) => [Number(item.documentId), item]))
  await loadCandidates()
})

async function loadCandidates() {
  loading.value = true
  try {
    const response = await listKnowledgeDocumentCandidates({ keyword: keyword.value || undefined })
    const fetched = response.data || []
    const fetchedIds = new Set(fetched.map((item) => Number(item.documentId)))
    const linkedOutsideResult = selectedRows.value.filter((item) => !fetchedIds.has(Number(item.documentId)))
    candidates.value = [...linkedOutsideResult, ...fetched]
    await syncTableSelection()
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows) {
  if (syncingSelection.value) return
  const next = new Map(selectedById.value)
  candidates.value.forEach((row) => next.delete(Number(row.documentId)))
  for (const row of rows) {
    const documentId = Number(row.documentId)
    if (next.has(documentId) || next.size < 20) next.set(documentId, row)
  }
  selectedById.value = next
  if (rows.some((row) => !next.has(Number(row.documentId)))) nextTick(syncTableSelection)
}

function canSelect(row) {
  const documentId = Number(row.documentId)
  return selectedById.value.has(documentId)
    || (row.lifecycleStatus !== 'TRASH' && !['NO_ACCESS', 'NO_MODULE_PERMISSION'].includes(row.accessStatus)
      && selectedById.value.size < 20)
}

function permissionLabel(row) {
  if (row.accessStatus === 'NO_ACCESS') return '不可访问'
  if (row.accessStatus === 'NO_MODULE_PERMISSION') return '无模块权限'
  if (row.lifecycleStatus === 'TRASH' || row.accessStatus === 'TRASH') return '回收站'
  if (row.lifecycleStatus === 'ARCHIVED' || row.accessStatus === 'ARCHIVED') return '已归档'
  return ({ OWNER: '所有者', EDIT: '可编辑', VIEW: '仅查看', ADMIN: '管理员' })[row.accessPermission] || '可访问'
}

function confirmSelection() {
  emit('confirm', selectedRows.value)
  open.value = false
}

async function syncTableSelection() {
  await nextTick()
  syncingSelection.value = true
  tableRef.value?.clearSelection()
  candidates.value.forEach((row) => {
    tableRef.value?.toggleRowSelection(row, selectedById.value.has(Number(row.documentId)))
  })
  await nextTick()
  syncingSelection.value = false
}
</script>

<style scoped>
.document-selector-toolbar { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.document-selector-note { margin: 10px 0 14px; color: var(--app-muted); font-size: 12px; line-height: 1.55; }
.candidate-document { display: grid; align-items: center; gap: 9px; grid-template-columns: auto minmax(0, 1fr); }
.candidate-document > span:last-child { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.candidate-document strong { overflow: hidden; color: var(--app-heading); text-overflow: ellipsis; white-space: nowrap; }
.candidate-document small { color: var(--app-muted); }
.document-selector-footer { display: flex; align-items: center; justify-content: space-between; color: var(--app-muted); font-size: 12px; }
.document-selector-footer > div { display: flex; gap: 8px; }
</style>
