<template>
  <el-drawer
    :model-value="modelValue"
    title="共享与权限"
    size="520px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @open="loadDrawer"
  >
    <div v-loading="loading.initial" class="share-drawer">
      <section class="share-document">
        <span class="file-mark" :class="`is-${document?.fileType || 'docx'}`">
          {{ fileMarkLabel }}
        </span>
        <div>
          <strong>{{ document?.title || '未选择文档' }}</strong>
          <span>{{ permissionDescription }}</span>
        </div>
      </section>

      <section class="share-section">
        <div class="section-heading">
          <div>
            <strong>已授权成员</strong>
            <span>{{ selected.length }} 人</span>
          </div>
        </div>
        <div v-if="selected.length" class="member-list">
          <div v-for="member in selected" :key="member.userId" class="member-row" :class="{ 'is-expired': expirationState(member.expiresAt).expired }">
            <div class="member-avatar">
              <el-avatar :size="34">{{ initials(member.nickName || member.userName) }}</el-avatar>
              <i v-if="member.online" aria-label="在线" />
            </div>
            <div class="member-copy">
              <strong>{{ member.nickName || member.userName }}</strong>
              <span>{{ member.online ? '当前在线' : '离线用户，权限仍会正常生效' }}</span>
            </div>
            <el-button text type="danger" aria-label="移除协作者" @click="removeMember(member.userId)">
              <el-icon><Close /></el-icon>
            </el-button>
            <div class="member-controls">
              <el-select v-model="member.permission" class="permission-select" size="small" aria-label="协作者权限">
                <el-option v-if="!viewOnlyFile" label="可编辑" value="EDIT" />
                <el-option :label="archiveFile ? '可下载' : '仅查看'" value="VIEW" />
              </el-select>
              <el-select v-model="member.expiryMode" class="expiry-select" size="small" aria-label="权限有效期" @change="applyExpiryMode(member)">
                <el-option v-for="option in EXPIRY_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-date-picker
                v-if="member.expiryMode === 'CUSTOM'"
                v-model="member.expiresAt"
                class="expiry-picker"
                type="datetime"
                size="small"
                format="YYYY-MM-DD HH:mm"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择到期时间"
                :disabled-date="disablePastDate"
                :default-time="expiryDefaultTime"
              />
              <span class="expiry-status" :class="`is-${expirationState(member.expiresAt).type}`">
                <el-icon><Clock /></el-icon>{{ expirationState(member.expiresAt).label }}
              </span>
            </div>
          </div>
        </div>
        <el-empty v-else :image-size="64" description="尚未添加协作者" />
      </section>

      <section class="share-section candidate-section">
        <div class="section-heading">
          <div>
            <strong>添加成员</strong>
            <span>仅显示已获得文档管理权限的用户；无需在线即可赋权</span>
          </div>
        </div>
        <el-input
          v-model="keyword"
          clearable
          prefix-icon="Search"
          placeholder="搜索姓名、账号或部门"
          @input="searchCandidates"
        />
        <div v-loading="loading.candidates" class="candidate-list">
          <button
            v-for="candidate in availableCandidates"
            :key="candidate.userId"
            type="button"
            class="candidate-row"
            @click="addMember(candidate)"
          >
            <span class="member-avatar">
              <el-avatar :size="32">{{ initials(candidate.nickName || candidate.userName) }}</el-avatar>
              <i v-if="candidate.online" aria-label="在线" />
            </span>
            <span class="member-copy">
              <strong>{{ candidate.nickName || candidate.userName }}</strong>
              <span>{{ candidate.deptName || '未设置部门' }} · {{ candidate.userName }} · {{ candidate.online ? '在线' : '离线' }}</span>
            </span>
            <el-icon><Plus /></el-icon>
          </button>
          <el-empty
            v-if="!loading.candidates && !availableCandidates.length"
            :image-size="56"
            description="没有符合条件的可授权用户"
          />
        </div>
      </section>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button type="primary" :loading="loading.save" @click="save">保存权限</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  listCollaboratorCandidates,
  listDocumentCollaborators,
  saveDocumentCollaborators
} from '@/api/document/workspace.js'
import {
  EXPIRY_OPTIONS,
  expirationForMode,
  expirationState,
  initials
} from '../workspace/documentWorkspaceRules.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  document: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'saved'])

const selected = ref([])
const candidates = ref([])
const keyword = ref('')
const loading = reactive({ initial: false, candidates: false, save: false })
const expiryDefaultTime = new Date(2000, 0, 1, 23, 59, 59)
let searchTimer = null

onBeforeUnmount(() => {
  window.clearTimeout(searchTimer)
})

const archiveFile = computed(() => ['zip', 'rar'].includes(String(props.document?.fileType || '').toLowerCase()))
const pdfFile = computed(() => String(props.document?.fileType || '').toLowerCase() === 'pdf')
const viewOnlyFile = computed(() => archiveFile.value || pdfFile.value)
const fileMarkLabel = computed(() => archiveFile.value
  ? String(props.document?.fileType || '').slice(0, 1).toUpperCase()
  : (pdfFile.value ? 'P' : (['xls', 'xlsx'].includes(props.document?.fileType) ? 'X' : 'W')))
const permissionDescription = computed(() => {
  if (archiveFile.value) return '压缩包仅共享下载权限，不提供在线预览或编辑'
  if (pdfFile.value) return 'PDF 仅共享只读预览与下载权限，不提供在线编辑'
  return '仅文档所有者可以调整协作者权限'
})
const availableCandidates = computed(() => {
  const selectedIds = new Set(selected.value.map((item) => Number(item.userId)))
  return candidates.value.filter((item) => !selectedIds.has(Number(item.userId)))
})

async function loadDrawer() {
  if (!props.document?.documentId) return
  keyword.value = ''
  loading.initial = true
  try {
    const [members, onlineUsers] = await Promise.all([
      listDocumentCollaborators(props.document.documentId),
      listCollaboratorCandidates(props.document.documentId, '')
    ])
    selected.value = (members.data || []).map((item) => ({
      ...item,
      permission: viewOnlyFile.value ? 'VIEW' : item.permission,
      expiryMode: item.expiresAt ? 'CUSTOM' : 'PERMANENT'
    }))
    candidates.value = onlineUsers.data || []
  } finally {
    loading.initial = false
  }
}

function searchCandidates() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(async () => {
    loading.candidates = true
    try {
      const response = await listCollaboratorCandidates(props.document.documentId, keyword.value)
      candidates.value = response.data || []
    } finally {
      loading.candidates = false
    }
  }, 260)
}

function addMember(candidate) {
  selected.value.push({ ...candidate, permission: 'VIEW', expiryMode: 'PERMANENT', expiresAt: null })
}

function removeMember(userId) {
  selected.value = selected.value.filter((item) => Number(item.userId) !== Number(userId))
}

function applyExpiryMode(member) {
  if (member.expiryMode === 'CUSTOM') {
    member.expiresAt = null
    return
  }
  member.expiresAt = expirationForMode(member.expiryMode)
}

function disablePastDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

async function save() {
  const invalidMember = selected.value.find((member) => member.expiresAt && expirationState(member.expiresAt).expired)
  const missingCustomTime = selected.value.find((member) => member.expiryMode === 'CUSTOM' && !member.expiresAt)
  if (missingCustomTime) {
    ElMessage.warning(`请为“${missingCustomTime.nickName || missingCustomTime.userName}”选择到期时间`)
    return
  }
  if (invalidMember) {
    ElMessage.warning(`“${invalidMember.nickName || invalidMember.userName}”的到期时间必须晚于当前时间`)
    return
  }
  loading.save = true
  try {
    await saveDocumentCollaborators(
      props.document.documentId,
      selected.value.map(({ userId, permission, expiresAt }) => ({ userId, permission, expiresAt: expiresAt || null }))
    )
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    loading.save = false
  }
}
</script>

<style scoped lang="scss">
.share-drawer {
  color: #172033;
}

.share-document {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid #dde4ee;
  background: #f7f9fc;
  border-radius: 8px;

  > div {
    min-width: 0;
  }

  strong,
  span {
    display: block;
  }

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 3px;
    color: #667085;
    font-size: 12px;
  }
}

.file-mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: 6px;
  color: #fff;
  background: #1677ff;
  font-weight: 750;

  &.is-xls,
  &.is-xlsx {
    background: #15803d;
  }

  &.is-zip,
  &.is-rar {
    background: #7c3aed;
  }

  &.is-pdf {
    background: #c2413b;
  }
}

.share-section {
  margin-top: 24px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;

  strong,
  span {
    display: block;
  }

  span {
    margin-top: 3px;
    color: #667085;
    font-size: 12px;
  }
}

.member-list,
.candidate-list {
  border-top: 1px solid #edf0f4;
}

.candidate-row {
  display: flex;
  width: 100%;
  min-height: 58px;
  align-items: center;
  gap: 10px;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  background: transparent;
  color: inherit;
  text-align: left;
}

.member-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 11px 2px 12px;
  border-bottom: 1px solid #edf0f4;

  &.is-expired { background: linear-gradient(90deg, rgba(220, 38, 38, 0.045), transparent 78%); }
}

.candidate-row {
  padding: 0 6px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    outline: none;
    background: #f2f7ff;
  }

  > .el-icon {
    color: #1677ff;
  }
}

.member-avatar {
  position: relative;
  flex: 0 0 auto;

  i {
    position: absolute;
    right: -1px;
    bottom: 1px;
    width: 9px;
    height: 9px;
    border: 2px solid #fff;
    border-radius: 50%;
    background: #15803d;
  }
}

.member-copy {
  min-width: 0;
  flex: 1;

  strong,
  span {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 2px;
    color: #667085;
    font-size: 12px;
  }
}

.permission-select {
  width: 92px;
}

.member-controls {
  grid-column: 2 / 4;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.expiry-select { width: 104px; }
.expiry-picker { width: 182px; }

.expiry-status {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 4px;
  color: #667085;
  font-size: 12px;
  white-space: nowrap;

  &.is-success { color: #15803d; }
  &.is-danger { color: #b42318; }
}

.candidate-section .el-input {
  margin-bottom: 8px;
}

.candidate-list {
  min-height: 100px;
  max-height: 260px;
  overflow: auto;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .member-controls { flex-wrap: wrap; }
  .expiry-picker { width: 100%; }
}
</style>
