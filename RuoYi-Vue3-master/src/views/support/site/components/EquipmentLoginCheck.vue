<template>
  <section class="equipment-login-check" aria-label="SSH密码登录校验">
    <el-form label-position="top">
      <el-form-item label="是否 root 直接登录">
        <el-switch v-model="rootDirect" aria-label="是否root直接登录" :disabled="!canEdit || running"
          :loading="changing" :before-change="changePolicy" />
      </el-form-item>
    </el-form>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="SSH目标">{{ device.ipAddress }}:{{ device.sshPort || 55555 }}</el-descriptions-item>
      <el-descriptions-item label="登录策略">{{ rootDirect ? 'root直接登录' : '优先HIK登录后切换root；仅登记root时直接登录' }}</el-descriptions-item>
    </el-descriptions>
    <div class="equipment-login-check__actions">
      <el-button v-if="canVerify" type="primary" icon="Key" :loading="running" :disabled="changing" @click="check()">
        {{ running ? '正在校验' : '校验登录' }}
      </el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <template v-if="result">
      <el-alert :title="result.message" :type="resultType" :closable="false" show-icon />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="校验时间">{{ result.checkedAt }}</el-descriptions-item>
        <el-descriptions-item label="登录账号">{{ result.loginUsername }}</el-descriptions-item>
        <el-descriptions-item label="root密码">{{ result.rootPasswordVerified ? '校验通过' : '未通过或未完成验证' }}</el-descriptions-item>
        <el-descriptions-item v-if="result.hostFingerprint" label="主机指纹">
          <span class="equipment-login-check__fingerprint">{{ result.hostFingerprint }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <el-button v-if="result.status === 'HOST_KEY_REQUIRED' && canVerify" icon="Select" :disabled="running || changing"
        @click="confirmHost">确认指纹并校验</el-button>
      <el-table :data="result.steps" size="small">
        <el-table-column prop="name" label="步骤" width="110" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="160" />
      </el-table>
    </template>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
import { checkServerLogin, getEquipmentWorkbook, saveEquipmentWorkbook } from '@/api/support/equipment'
const props = defineProps({ siteId: [Number, String], device: { type: Object, required: true } })
const emit = defineEmits(['saved'])
const { proxy } = getCurrentInstance()
const rootDirect = ref(false)
const changing = ref(false)
const running = ref(false)
const result = ref(null)
const error = ref('')
let generation = 0
const canEdit = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', 'support:server:edit']))
const canVerify = computed(() => proxy.$auth.hasPermi(['support:credential:verify']))
const resultType = computed(() => result.value?.status === 'SUCCESS' ? 'success'
  : ['HOST_KEY_REQUIRED', 'PASSWORD_NOT_VERIFIED'].includes(result.value?.status) ? 'warning' : 'error')
const statusType = status => ({ success: 'success', warning: 'warning', error: 'danger' }[status] || 'info')
const statusLabel = status => ({ success: '通过', warning: '未验证', error: '未通过' }[status] || '待确认')
watch([() => props.siteId, () => props.device.sourceId, () => props.device.rootDirectLogin, () => props.device.ipAddress, () => props.device.sshPort],
  () => { generation++; rootDirect.value = props.device.rootDirectLogin === true; result.value = null; error.value = '' }, { immediate: true })
onBeforeUnmount(() => { generation++ })

async function changePolicy() {
  if (changing.value || running.value || !canEdit.value) return false
  changing.value = true
  const token = generation
  const nextValue = !rootDirect.value
  try {
    await proxy.$modal.confirm(nextValue ? '开启后将直接使用root登录，不再先登录HIK。确认修改？' : '关闭后有HIK凭据时先登录HIK，再切换root。确认修改？')
    const snapshot = (await getEquipmentWorkbook(props.siteId)).data
    if (token !== generation) return false
    const row = snapshot.rows.find(item => item.sourceType === 'SERVER' && Number(item.sourceId) === Number(props.device.sourceId))
    if (!row || !row.canEdit) throw new Error('服务器不存在或没有修改权限')
    if (row.rootDirectLogin !== rootDirect.value) throw new Error('登录配置已变化，请刷新后重新操作')
    await saveEquipmentWorkbook({ siteId: props.siteId, rows: [{
      sourceType: 'SERVER', sourceId: row.sourceId, revision: row.revision, changes: { rootDirectLogin: nextValue }
    }] })
    if (token !== generation) return false
    result.value = null
    emit('saved')
    proxy.$modal.msgSuccess('登录配置已保存')
    return true
  } catch (failure) {
    if (failure !== 'cancel' && failure !== 'close' && token === generation) error.value = failure.message || '登录配置保存失败'
    return false
  } finally { changing.value = false }
}
async function check(fingerprint) {
  if (running.value || changing.value || !canVerify.value) return
  const token = generation
  running.value = true
  error.value = ''
  result.value = null
  try {
    const response = await checkServerLogin({ siteId: props.siteId, serverId: props.device.sourceId, hostFingerprint: fingerprint || null })
    if (token === generation) result.value = response.data
  } catch (failure) {
    if (token === generation) error.value = failure.message || '登录校验请求失败'
  } finally { running.value = false }
}
async function confirmHost() {
  const fingerprint = result.value?.hostFingerprint
  const token = generation
  if (!fingerprint) return
  try {
    await proxy.$modal.confirm('请先与设备管理员核对上述SSH指纹。本次将使用登记凭据进行一次真实登录；错误密码可能触发设备账号锁定策略。确认继续？')
    if (token === generation) await check(fingerprint)
  } catch (failure) {
    if (failure !== 'cancel' && failure !== 'close') error.value = '主机确认未完成'
  }
}
</script>

<style scoped>
.equipment-login-check { display: grid; gap: var(--el-font-size-base); margin-top: var(--el-font-size-base); }
.equipment-login-check__actions { display: flex; flex-wrap: wrap; gap: var(--el-font-size-base); }
.equipment-login-check__fingerprint { overflow-wrap: anywhere; }
</style>
