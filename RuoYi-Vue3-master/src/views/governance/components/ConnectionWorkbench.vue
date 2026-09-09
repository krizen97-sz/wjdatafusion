<template>
  <section>
    <el-alert title="数据库连接与字典快照" description="数据库密码加密保存，页面不回显。只读查询由表与字段生成；当前快照最多1000行 / 64 KiB，超限会拒绝，避免把截断数据当成完整字典。" type="info" :closable="false" class="mb16" />
    <div class="connection-toolbar mb16"><el-button v-hasPermi="['governance:flow:edit']" type="primary" icon="Plus" :disabled="!engineReady" @click="edit()">新建连接</el-button><el-button icon="Refresh" :loading="loading" @click="load">刷新连接</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
    <el-table :data="connections" v-loading="loading" row-key="id" empty-text="暂无数据库连接，可先建立独立测试连接">
      <el-table-column prop="name" label="连接名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" width="140"><template #default>PostgreSQL</template></el-table-column>
      <el-table-column label="地址" min-width="200"><template #default="{ row }">{{ row.host }}:{{ row.port }}</template></el-table-column>
      <el-table-column label="数据库" prop="database" min-width="140" show-overflow-tooltip />
      <el-table-column label="密码" width="100"><template #default="{ row }"><el-tag size="small" type="info">{{ row.passwordConfigured ? '已配置' : '未配置' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="230"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="primary" :loading="testingId === row.id" :disabled="!!testingId" @click="test(row)">测试连接</el-button><el-button link type="primary" @click="preview(row)">读取快照</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="form.id ? '编辑数据库连接' : '新建数据库连接'" width="600px" append-to-body :close-on-click-modal="!saving" :show-close="!saving" @closed="clearPassword">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="mb16" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving">
        <el-form-item label="连接名称" prop="name"><el-input v-model="form.name" maxlength="80" /></el-form-item>
        <el-row :gutter="16"><el-col :span="16"><el-form-item label="数据库IP" prop="host"><el-input v-model="form.host" placeholder="例如127.0.0.1" /></el-form-item></el-col><el-col :span="8"><el-form-item label="端口" prop="port"><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" /></el-form-item></el-col></el-row>
        <el-form-item label="数据库名称" prop="database"><el-input v-model="form.database" /></el-form-item>
        <el-form-item label="数据库账号" prop="username"><el-input v-model="form.username" autocomplete="off" /></el-form-item>
        <el-form-item :label="form.id ? '新密码（留空保留原密码）' : '数据库密码'" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="传输安全" prop="sslMode"><el-select v-model="form.sslMode"><el-option label="校验证书与主机（远程连接）" value="verify-full" /><el-option label="独立本机测试连接" value="disable" /></el-select></el-form-item>
        <el-text type="info" size="small">地址和端口必须属于服务端允许范围；默认仅开放独立测试 PostgreSQL 的15432端口。</el-text>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存连接</el-button></template>
    </el-dialog>
    <snapshot-picker v-model="snapshotOpen" :initial-connection-id="snapshotConnectionId" />
  </section>
</template>
<script setup>
import { getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { listGovernanceConnections, saveGovernanceConnection, testGovernanceConnection } from '@/api/governance/connections'
import { errorMessage } from '../workspaceRules'
import SnapshotPicker from './SnapshotPicker.vue'
defineProps({ engineReady: Boolean })
const { proxy } = getCurrentInstance()
const connections = ref([]), loading = ref(false), saving = ref(false), testingId = ref(''), error = ref(''), formError = ref(''), dialog = ref(false), formRef = ref()
const snapshotOpen = ref(false), snapshotConnectionId = ref('')
const form = reactive({ id: '', revision: null, name: '', host: '127.0.0.1', port: 15432, database: '', username: '', password: '', sslMode: 'disable' })
const required = label => [{ required: true, message: `请填写${label}`, trigger: 'blur' }]
const rules = { name: required('连接名称'), host: required('数据库IP'), port: [{ validator: (_rule, value, callback) => Number.isInteger(value) && value >= 1 && value <= 65535 ? callback() : callback(new Error('请填写1至65535的端口')), trigger: 'blur' }], database: required('数据库名称'), username: required('数据库账号'), password: [{ validator: (_rule, value, callback) => !form.id && !value ? callback(new Error('请填写数据库密码')) : callback(), trigger: 'blur' }] }
let disposed = false
async function load() {
  if (loading.value) return
  loading.value = true; error.value = ''
  try { const response = await listGovernanceConnections(); if (!disposed) connections.value = response.data || [] }
  catch (cause) { if (!disposed) error.value = errorMessage(cause, '连接列表读取失败') }
  finally { if (!disposed) loading.value = false }
}
function edit(row) {
  Object.assign(form, { id: '', revision: null, name: '', host: '127.0.0.1', port: 15432, database: '', username: '', sslMode: 'disable' }, row || {}, { password: '' })
  formError.value = ''; dialog.value = true; formRef.value?.clearValidate()
}
function clearPassword() { form.password = '' }
async function save() {
  if (saving.value || !await formRef.value?.validate().catch(() => false)) return
  saving.value = true; formError.value = ''
  try {
    const { id, revision, name, host, port, database, username, password, sslMode } = form
    await saveGovernanceConnection(id, { revision, name, host, port, database, username, password, sslMode })
    if (!disposed) { dialog.value = false; clearPassword(); proxy.$modal.msgSuccess('连接已加密保存'); await load() }
  } catch (cause) { if (!disposed) formError.value = errorMessage(cause, '连接保存失败') }
  finally { if (!disposed) saving.value = false }
}
async function test(row) {
  if (testingId.value) return
  testingId.value = row.id
  try { const response = await testGovernanceConnection(row.id); if (!disposed && response.data?.success) proxy.$modal.msgSuccess(`只读连接验证通过，耗时 ${response.data.elapsedMillis} ms`) }
  catch (cause) { if (!disposed) error.value = errorMessage(cause, '连接测试失败') }
  finally { if (!disposed) testingId.value = '' }
}
function preview(row) { snapshotConnectionId.value = row.id; snapshotOpen.value = true }
onMounted(load)
onBeforeUnmount(() => { disposed = true; clearPassword() })
</script>
<style scoped>
.connection-toolbar { display: flex; align-items: center; justify-content: space-between; gap: var(--el-font-size-base); }
</style>
