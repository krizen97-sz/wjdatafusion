<template>
  <el-dialog v-model="visible" title="服务器导入确认" width="1180px" append-to-body destroy-on-close
    class="server-intake-review" :close-on-click-modal="false" :before-close="beforeClose">
    <el-steps :active="1" simple finish-status="success" class="review-steps">
      <el-step title="选择数据" icon="Document" />
      <el-step title="校验确认" icon="CircleCheck" />
      <el-step title="完成录入" icon="Finished" />
    </el-steps>
    <el-descriptions :column="2" size="small">
      <el-descriptions-item label="目标子平台">{{ context.platformName }}</el-descriptions-item>
      <el-descriptions-item label="数据来源">{{ context.sourceName || '批量IP录入' }}</el-descriptions-item>
    </el-descriptions>
    <div class="review-toolbar">
      <el-space wrap>
        <el-tag type="info">总计 {{ counts.total }}</el-tag>
        <el-tag v-if="dirty" type="warning">待重新校验</el-tag>
        <template v-else>
        <el-tag type="success">新增 {{ counts.create }}</el-tag>
        <el-tag type="primary">复用 {{ counts.reuse }}</el-tag>
        <el-tag type="info">跳过 {{ counts.skip }}</el-tag>
        <el-tag type="danger">错误 {{ counts.error }}</el-tag>
        </template>
        <el-checkbox v-model="showCredentials">登录凭据列</el-checkbox>
      </el-space>
      <el-checkbox v-model="reuseExisting" :disabled="busy">复用已有服务器并归属目标子平台</el-checkbox>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-else-if="dirty" title="清单已修改，尚未重新校验" type="warning" :closable="false" show-icon />
    <el-alert v-else-if="counts.error" :title="`${counts.error} 条记录需要修改或删除，当前未保存任何数据`" type="error" :closable="false" show-icon />
    <el-table :data="pagedRows" v-loading="validating" border max-height="430" class="review-table">
      <el-table-column label="行" width="55" prop="data.rowNumber" />
      <el-table-column label="校验" width="90">
        <template #default="{ row }"><el-tag :type="rowTone(row)">{{ rowLabel(row) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="服务器IP" min-width="165">
        <template #default="{ row }"><el-input v-model="row.data.serverAddress" :disabled="busy" aria-label="服务器IP" /></template>
      </el-table-column>
      <el-table-column label="名称" min-width="180">
        <template #default="{ row }"><el-input v-model="row.data.serverName" :disabled="profileReadOnly(row)" maxlength="120" aria-label="服务器名称" /></template>
      </el-table-column>
      <el-table-column label="SSH端口" width="110">
        <template #default="{ row }"><el-input v-model="row.data.sshPort" :disabled="profileReadOnly(row)" inputmode="numeric" aria-label="SSH端口" /></template>
      </el-table-column>
      <el-table-column label="校验详情 / 已有归属" min-width="230">
        <template #default="{ row }">
          <el-text v-if="row.errors.length" type="danger">{{ row.errors.join('；') }}</el-text>
          <el-text v-else-if="row.existingServerId" type="info">{{ row.alreadyBound ? '已在目标子平台；' : '' }}{{ row.existingScope }}；保留现有档案</el-text>
          <el-text v-else type="success">待新增</el-text>
        </template>
      </el-table-column>
      <el-table-column label="操作系统" width="140">
        <template #default="{ row }"><el-input v-model="row.data.osType" :disabled="profileReadOnly(row)" maxlength="64" aria-label="操作系统" /></template>
      </el-table-column>
      <el-table-column v-for="field in visibleCredentialFields" :key="field.key" :label="field.label" width="155">
        <template #default="{ row }"><el-input v-model="row.data[field.key]" :disabled="profileReadOnly(row)" :aria-label="field.label" autocomplete="off" maxlength="128" /></template>
      </el-table-column>
      <el-table-column label="运行状态" width="115">
        <template #default="{ row }">
          <el-select v-model="row.data.status" :disabled="profileReadOnly(row)" aria-label="运行状态">
            <el-option label="正常" value="0" /><el-option label="停用" value="1" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="75" fixed="right">
        <template #default="{ row }"><el-button link type="danger" :disabled="busy" @click="removeRow(row)">删除</el-button></template>
      </el-table-column>
      <template #empty><el-empty description="清单为空" :image-size="48" /></template>
    </el-table>
    <Pagination v-show="rows.length > 0" :total="rows.length" v-model:page="page.pageNum" v-model:limit="page.pageSize" />
    <template #footer>
      <el-button :disabled="saving" @click="close">返回修改</el-button>
      <el-button icon="Refresh" :loading="validating" :disabled="saving || !rows.length" @click="validate">重新校验</el-button>
      <el-button type="primary" icon="Upload" :loading="saving"
        :disabled="busy || dirty || !!counts.error || !(counts.create + counts.reuse)" @click="submit">确认上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { previewEquipmentServers, commitEquipmentServers } from '@/api/support/equipment'
import { serverIntakePayload, serverIntakeSignature, serverIntakeCounts } from './serverIntakeReview.rules'

const emit = defineEmits(['saved'])
const { proxy } = getCurrentInstance()
const visible = ref(false)
const context = ref({})
const rows = ref([])
const reuseExisting = ref(false)
const validatedSignature = ref('')
const validating = ref(false)
const saving = ref(false)
const error = ref('')
const showCredentials = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10 })
let validationSequence = 0
const credentialFields = [
  { key: 'hikPassword', label: 'hik密码' }, { key: 'rootPassword', label: 'root密码' },
  { key: 'otherUsername', label: '其他账号' }, { key: 'otherPassword', label: '其他密码' }
]
const visibleCredentialFields = computed(() => showCredentials.value ? credentialFields : [])
const busy = computed(() => saving.value || validating.value)
const counts = computed(() => serverIntakeCounts(rows.value))
const signature = computed(() => serverIntakeSignature(context.value, rows.value, reuseExisting.value))
const dirty = computed(() => signature.value !== validatedSignature.value)
const pagedRows = computed(() => rows.value.slice((page.pageNum - 1) * page.pageSize, page.pageNum * page.pageSize))
const labels = { CREATE: '待新增', REUSE: '待复用', SKIP: '已存在', ERROR: '需修正' }
const tones = { CREATE: 'success', REUSE: 'primary', SKIP: 'info', ERROR: 'danger' }
function rowLabel(row) { return dirty.value ? '待校验' : labels[row.state] }
function rowTone(row) { return dirty.value ? 'warning' : tones[row.state] }
function profileReadOnly(row) { return busy.value || Boolean(row.existingServerId && row.data.serverAddress === row.validatedAddress) }
function applyPreview(preview) {
  rows.value = (preview.rows || []).map(row => ({ ...row, validatedAddress: row.data.serverAddress }))
  validatedSignature.value = signature.value
}
function open(target, preview) {
  validationSequence++
  validating.value = false
  context.value = { ...target }
  reuseExisting.value = false
  error.value = ''
  page.pageNum = 1
  applyPreview(preview)
  showCredentials.value = rows.value.some(row => credentialFields.some(field => row.data[field.key]))
  visible.value = true
}
function removeRow(row) {
  rows.value = rows.value.filter(item => item !== row)
  page.pageNum = Math.min(page.pageNum, Math.max(1, Math.ceil(rows.value.length / page.pageSize)))
}
function beforeClose(done) {
  if (saving.value) return
  validationSequence++
  validating.value = false
  done()
}
function close() {
  if (saving.value) return
  validationSequence++
  validating.value = false
  visible.value = false
}
async function validate() {
  if (busy.value || !rows.value.length) return
  const sequence = ++validationSequence
  const payload = serverIntakePayload(context.value, rows.value, reuseExisting.value)
  validating.value = true
  error.value = ''
  try {
    const response = await previewEquipmentServers(payload)
    if (sequence !== validationSequence || !visible.value) return
    applyPreview(response.data)
  } catch (failure) {
    if (sequence === validationSequence) {
      validatedSignature.value = ''
      error.value = failure?.message || '清单校验失败'
    }
  } finally {
    if (sequence === validationSequence) validating.value = false
  }
}
async function submit() {
  if (busy.value || dirty.value || counts.value.error || !(counts.value.create + counts.value.reuse)) return
  saving.value = true
  error.value = ''
  try {
    const response = await commitEquipmentServers(serverIntakePayload(context.value, rows.value, reuseExisting.value))
    proxy.$modal.msgSuccess(`新增 ${response.data.createdCount} 台，复用归属 ${response.data.boundCount - response.data.createdCount} 台，跳过 ${response.data.skippedCount} 台`)
    visible.value = false
    emit('saved', response.data)
  } catch (failure) {
    validatedSignature.value = ''
    error.value = failure?.message || '上传失败，清单已保留，请重新校验'
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
:global(.server-intake-review) { max-width: calc(100vw - 32px); }
.review-steps { margin-bottom: 20px; }
.review-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin: 16px 0; }
.review-table { margin-top: 16px; }
.review-toolbar :deep(.el-checkbox__label) { white-space: normal; }
</style>
