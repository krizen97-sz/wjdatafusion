<template>
  <el-drawer
    :model-value="modelValue"
    title="用户空间管理"
    size="760px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadUsers"
  >
    <div v-loading="loading.list" class="storage-admin">
      <div class="storage-admin-summary">
        <div>
          <strong>{{ rows.length }} 位用户</strong>
          <span>仅统计已获得文档管理权限的用户</span>
        </div>
        <span>当前共使用 {{ formatFileSize(totalUsedSize) }}</span>
      </div>

      <div class="storage-table" role="table" aria-label="文档用户容量配置">
        <div class="storage-table-head" role="row">
          <span>用户</span><span>空间使用</span><span>可用空间</span><span>单文件上限</span><span />
        </div>
        <div v-for="row in rows" :key="row.userId" class="storage-table-row" role="row">
          <div class="storage-user">
            <el-avatar :size="34">{{ initials(row.nickName || row.userName) }}</el-avatar>
            <span>
              <strong>{{ row.nickName || row.userName }} <em v-if="row.adminUser">总权限</em></strong>
              <small>{{ row.deptName || '未设置部门' }} · {{ row.userName }}</small>
            </span>
          </div>
          <div class="storage-usage">
            <span><strong>{{ formatFileSize(row.usedSize) }}</strong><small>{{ row.fileCount }} 个文件</small></span>
            <el-progress :percentage="progressValue(row)" :stroke-width="6" :show-text="false" :status="progressStatus(row)" />
          </div>
          <label class="storage-limit-field">
            <span class="sr-only">{{ row.nickName || row.userName }} 可用空间（MB）</span>
            <el-input-number v-model="row.quotaMb" :min="minimumQuotaMb(row)" :max="102400" :step="100" controls-position="right" :disabled="row.adminUser" />
            <small>MB</small>
          </label>
          <label class="storage-limit-field">
            <span class="sr-only">{{ row.nickName || row.userName }} 单文件上传上限（MB）</span>
            <el-input-number v-model="row.maxUploadMb" :min="1" :max="100" :step="10" controls-position="right" :disabled="row.adminUser" />
            <small>MB</small>
          </label>
          <el-button type="primary" plain :disabled="row.adminUser" :loading="loading.userId === row.userId" @click="saveRow(row)">{{ row.adminUser ? '无需配置' : '保存' }}</el-button>
        </div>
        <el-empty v-if="!loading.list && !rows.length" :image-size="72" description="暂无已授权的文档用户" />
      </div>

      <p class="storage-policy-note">
        单个文件上传上限最高为 100MB；可用空间不能低于用户当前已使用容量。回收站中的文件仍占用空间。
      </p>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listDocumentStorageUsers, updateDocumentStoragePolicy } from '@/api/document/workspace.js'
import { formatFileSize, initials } from '../workspace/documentWorkspaceRules.js'

defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue', 'updated'])

const rows = ref([])
const loading = reactive({ list: false, userId: null })
const totalUsedSize = computed(() => rows.value.reduce((total, row) => total + Number(row.usedSize || 0), 0))

function toMegabytes(bytes) {
  return Math.max(1, Math.round(Number(bytes || 0) / 1024 ** 2))
}

function normalizeRow(row) {
  return {
    ...row,
    quotaMb: toMegabytes(row.quotaSize),
    maxUploadMb: Math.min(100, toMegabytes(row.maxUploadSize))
  }
}

async function loadUsers() {
  loading.list = true
  try {
    const response = await listDocumentStorageUsers()
    rows.value = (response.data || []).map(normalizeRow)
  } finally {
    loading.list = false
  }
}

function minimumQuotaMb(row) {
  return Math.max(1, Math.ceil(Number(row.usedSize || 0) / 1024 ** 2))
}

function progressValue(row) {
  return Math.max(0, Math.min(100, Number(row.usagePercent || 0)))
}

function progressStatus(row) {
  const value = progressValue(row)
  if (value >= 95) return 'exception'
  if (value >= 80) return 'warning'
  return undefined
}

async function saveRow(row) {
  if (loading.userId) return
  loading.userId = row.userId
  try {
    const response = await updateDocumentStoragePolicy(row.userId, {
      quotaMb: Number(row.quotaMb),
      maxUploadMb: Number(row.maxUploadMb)
    })
    Object.assign(row, normalizeRow(response.data || row))
    ElMessage.success(`已更新“${row.nickName || row.userName}”的空间配置`)
    emit('updated')
  } finally {
    loading.userId = null
  }
}
</script>

<style scoped lang="scss">
.storage-admin { color: var(--app-heading); }
.storage-admin-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 0 2px 16px;
  border-bottom: 1px solid var(--surface-border);

  strong, span { display: block; }
  strong { font-size: 18px; }
  span { color: #596579; font-size: 13px; }
  div span { margin-top: 3px; }
}
.storage-table { min-width: 0; }
.storage-table-head,
.storage-table-row {
  display: grid;
  grid-template-columns: minmax(150px, 1.4fr) minmax(126px, 1fr) 126px 126px 64px;
  align-items: center;
  gap: 14px;
}
.storage-table-head {
  min-height: 42px;
  border-bottom: 1px solid var(--surface-border);
  color: var(--app-muted);
  font-size: 12px;
  font-weight: 650;
}
.storage-table-row {
  min-height: 78px;
  border-bottom: 1px solid #edf0f4;
}
.storage-user,
.storage-usage,
.storage-user > span,
.storage-usage > span { min-width: 0; }
.storage-user { display: flex; align-items: center; gap: 10px; }
.storage-user strong,
.storage-user small,
.storage-usage strong,
.storage-usage small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.storage-user small,
.storage-usage small { margin-top: 2px; color: var(--app-muted); font-size: 12px; }
.storage-user em { margin-left: 4px; color: #b45309; font-size: 11px; font-style: normal; font-weight: 650; }
.storage-usage .el-progress { margin-top: 8px; }
.storage-limit-field { position: relative; display: flex; align-items: center; }
.storage-limit-field .el-input-number { width: 100%; }
.storage-limit-field small { position: absolute; right: 28px; color: var(--app-muted); pointer-events: none; }
.storage-policy-note { margin: 16px 2px 0; color: #596579; font-size: 12px; line-height: 1.7; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; }

@media (max-width: 760px) {
  .storage-admin-summary { align-items: flex-start; flex-direction: column; }
  .storage-table-head { display: none; }
  .storage-table-row {
    grid-template-columns: minmax(0, 1fr) minmax(110px, .8fr);
    gap: 12px;
    padding: 14px 0;
  }
  .storage-limit-field::before { content: '空间'; width: 42px; color: var(--app-muted); font-size: 12px; }
  .storage-limit-field:nth-of-type(2)::before { content: '单文件'; }
  .storage-table-row > .el-button { justify-self: end; }
}
</style>
