<template>
  <section>
    <el-alert title="FTP目标连接" description="只允许服务端登记的目标。密码加密保存；目标目录须已存在。每次交付建立独立批次文件夹，服务端需支持MLSD列表及文件回读校验。" type="info" :closable="false" class="mb16" />
    <el-space wrap class="mb16"><el-button type="primary" icon="Plus" @click="edit()">新建FTP连接</el-button><el-button icon="Refresh" :loading="loading" @click="load">刷新连接</el-button></el-space>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
    <el-table :data="profiles" v-loading="loading" row-key="id" empty-text="暂无FTP目标连接">
      <el-table-column label="连接名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="目标地址" min-width="180"><template #default="{ row }">{{ row.host }}:{{ row.port }}</template></el-table-column>
      <el-table-column label="目录" prop="directory" min-width="160" show-overflow-tooltip />
      <el-table-column label="传输模式" prop="transferMode" width="120" />
      <el-table-column label="操作" width="200"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="primary" :loading="testing === row.id" :disabled="!!testing" @click="test(row)">测试连接</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialog" :title="form.id ? '编辑FTP连接' : '新建FTP连接'" width="600px" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="form.password = ''">
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="mb16" />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving">
        <el-form-item label="连接名称" prop="name"><el-input v-model="form.name" maxlength="80" /></el-form-item>
        <el-row :gutter="16"><el-col :span="16"><el-form-item label="FTP服务器IP" prop="host"><el-input v-model="form.host" /></el-form-item></el-col><el-col :span="8"><el-form-item label="端口" prop="port"><el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" /></el-form-item></el-col></el-row>
        <el-form-item label="账号" prop="username"><el-input v-model="form.username" autocomplete="off" /></el-form-item>
        <el-form-item :label="form.id ? '新密码（留空保留原密码）' : '密码'" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="目标根目录" prop="directory"><el-input v-model="form.directory" placeholder="/" /></el-form-item>
        <el-form-item label="传输模式"><el-select v-model="form.transferMode"><el-option value="BINARY" label="二进制（保留字节）" /><el-option value="ASCII" label="ASCII文本" /></el-select></el-form-item>
        <el-form-item label="控制通道编码"><el-select v-model="form.controlEncoding"><el-option value="UTF-8" label="UTF-8" /><el-option value="ISO-8859-1" label="ISO-8859-1" /></el-select></el-form-item>
        <el-text type="info" size="small">上传后回读内容必须与本地产物逐字节一致。ASCII模式若改变换行，校验将失败并保留原件。</el-text>
      </el-form>
      <template #footer><el-button :disabled="saving" @click="dialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存连接</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { listFtpConnections, saveFtpConnection, testFtpConnection } from '@/api/governance/deliveries'
import { errorMessage } from '../workspaceRules'
const { proxy } = getCurrentInstance()
const profiles = ref([]), loading = ref(false), error = ref(''), testing = ref(''), dialog = ref(false), saving = ref(false), formError = ref(''), formRef = ref()
const defaults = () => ({ id: '', revision: null, name: '', host: '127.0.0.1', port: 2121, username: '', password: '', directory: '/', transferMode: 'BINARY', controlEncoding: 'UTF-8' })
const form = reactive(defaults())
const required = label => [{ required: true, message: `请填写${label}`, trigger: 'blur' }]
const rules = { name: required('连接名称'), host: required('服务器IP'), username: required('账号'), directory: required('目标目录'), port: [{ validator: (_r, v, done) => Number.isInteger(v) && v > 0 && v <= 65535 ? done() : done(new Error('端口须为1至65535')), trigger: 'blur' }], password: [{ validator: (_r, v, done) => form.id || v ? done() : done(new Error('请填写密码')), trigger: 'blur' }] }
let disposed = false
async function load() { if (loading.value) return; loading.value = true; error.value = ''; try { const r = await listFtpConnections(); if (!disposed) profiles.value = r.data || [] } catch (e) { if (!disposed) error.value = errorMessage(e, 'FTP连接读取失败') } finally { if (!disposed) loading.value = false } }
function edit(row) { Object.assign(form, defaults(), row || {}, { password: '' }); formError.value = ''; dialog.value = true; formRef.value?.clearValidate() }
async function save() {
  if (saving.value || !await formRef.value?.validate().catch(() => false)) return
  saving.value = true; formError.value = ''
  try { const { id, revision, name, host, port, username, password, directory, transferMode, controlEncoding } = form; await saveFtpConnection(id, { revision, name, host, port, username, password, directory, transferMode, controlEncoding }); if (!disposed) { dialog.value = false; form.password = ''; proxy.$modal.msgSuccess('FTP连接已加密保存'); await load() } }
  catch (e) { if (!disposed) formError.value = errorMessage(e, 'FTP连接保存失败') } finally { if (!disposed) saving.value = false }
}
async function test(row) { if (testing.value) return; testing.value = row.id; error.value = ''; try { const r = await testFtpConnection(row.id); if (!disposed && r.data?.success) proxy.$modal.msgSuccess(`FTP连接验证通过，耗时 ${r.data.elapsedMillis} ms`) } catch (e) { if (!disposed) error.value = errorMessage(e, 'FTP连接检查失败') } finally { if (!disposed) testing.value = '' } }
onMounted(load)
onBeforeUnmount(() => { disposed = true; form.password = '' })
</script>
