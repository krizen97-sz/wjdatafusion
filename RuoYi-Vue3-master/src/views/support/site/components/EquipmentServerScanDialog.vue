<template>
  <el-dialog :model-value="modelValue" title="扫描服务器并同步" width="90%" append-to-body destroy-on-close
    :close-on-click-modal="false" :before-close="closeDialog" @closed="stopPolling">
    <el-steps :active="step" simple class="scan-steps">
      <el-step title="配置范围" /><el-step title="探测端口" /><el-step title="核验同步" /><el-step title="结果" />
    </el-steps>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-form v-if="!task" ref="formRef" :model="form" :rules="rules" label-position="top" v-loading="loading" class="scan-form">
      <el-form-item label="新服务器所属子平台" prop="platformId">
        <el-select v-model="form.platformId" filterable placeholder="请选择子平台" aria-label="扫描新增服务器归属">
          <el-option v-for="platform in subplatforms" :key="platform.platformId" :label="platform.label" :value="platform.platformId" />
        </el-select>
      </el-form-item>
      <el-form-item label="扫描范围" prop="targets">
        <el-input v-model="form.targets" type="textarea" :rows="3" :maxlength="8192" aria-label="扫描网段或IP"
          placeholder="192.168.1.0/24；192.168.2.10-192.168.2.20；192.168.3.10" />
      </el-form-item>
      <div class="scan-form__columns">
        <el-form-item label="SSH探测端口" prop="ports">
          <el-checkbox-group v-model="form.ports" aria-label="SSH探测端口">
            <el-checkbox :value="22">22</el-checkbox><el-checkbox :value="2343">2343</el-checkbox><el-checkbox :value="55555">55555</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="每个地址最多尝试的凭据组数">
          <el-input-number v-model="form.attemptLimit" :min="1" :max="Math.min(3, form.credentialIds.length || 1)" controls-position="right" aria-label="凭据尝试上限" />
        </el-form-item>
      </div>
      <el-form-item label="授权候选凭据（按选中顺序尝试）" prop="credentialIds">
        <div class="scan-credential-picker">
          <el-select v-model="form.credentialIds" multiple :multiple-limit="3" aria-label="授权候选凭据" placeholder="选择1至3组凭据">
            <el-option v-for="item in credentials" :key="item.credentialId" :value="item.credentialId" :label="item.name + ' · ' + modeLabel(item.mode)" />
          </el-select>
          <el-button icon="Key" @click="poolOpen = true">管理凭据</el-button>
        </div>
      </el-form-item>
    </el-form>

    <template v-else>
      <div class="scan-toolbar">
        <el-tag :type="task.state === 'FAILED' ? 'danger' : task.state === 'DONE' ? failedCount ? 'warning' : 'success' : 'info'">{{ taskLabel }}</el-tag>
        <el-text>{{ task.done }} / {{ task.total }}</el-text>
        <el-tag v-if="syncedCount" type="success">已同步 {{ syncedCount }} 台</el-tag>
        <el-tag v-if="failedCount" type="danger">失败 {{ failedCount }} 台</el-tag>
        <el-text v-if="task.state === 'REVIEW'">已选择 {{ selected.length }} 台</el-text>
        <el-button v-if="task.state === 'REVIEW'" icon="Select" @click="selectReady">全选可连接</el-button>
        <el-select v-model="resultFilter" class="scan-result-filter" aria-label="扫描结果筛选" @change="page = 1">
          <el-option label="全部目标" value="ALL" /><el-option label="可连接" value="READY" />
          <el-option label="已同步" value="SYNCED" /><el-option label="失败" value="FAILED" />
        </el-select>
      </div>
      <el-progress v-if="running" :percentage="progress" :stroke-width="6" />
      <el-alert v-if="task.state === 'REVIEW'" title="请与设备管理员核对所选SSH主机指纹。确认后将按凭据尝试上限登录，并自动新增或更新验证成功的服务器；失败不会覆盖原数据。" type="warning" :closable="false" show-icon />
      <el-alert v-if="task.error" :title="task.error" type="error" :closable="false" show-icon />
      <el-table ref="tableRef" :data="pageRows" row-key="address" border max-height="460" @selection-change="selected = $event">
        <el-table-column v-if="task.state === 'REVIEW'" type="selection" reserve-selection :selectable="row => row.state === 'READY'" width="45" />
        <el-table-column prop="address" label="IP地址" min-width="135" />
        <el-table-column label="服务器" min-width="135" show-overflow-tooltip>
          <template #default="{ row }">{{ serverLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="端口探测" min-width="170">
          <template #default="{ row }"><div class="scan-port-tags"><el-tag v-for="port in row.ports" :key="port.port" :type="port.status === 'READY' ? 'success' : port.open ? 'warning' : 'info'" size="small">{{ port.port }} {{ port.status === 'READY' ? 'SSH' : port.open ? '已开放' : '未连通' }}</el-tag></div></template>
        </el-table-column>
        <el-table-column v-if="task.state !== 'REVIEW'" label="登录端口" prop="port" width="100" />
        <el-table-column v-if="task.state === 'REVIEW'" label="登录端口 / 主机指纹" min-width="290">
          <template #default="{ row }">
            <el-select v-if="row.state === 'READY'" v-model="chosenPorts[row.address]" :aria-label="row.address + ' 登录端口'">
              <el-option v-for="port in row.ports.filter(item => item.status === 'READY')" :key="port.port" :label="String(port.port)" :value="port.port" />
            </el-select>
            <el-text class="scan-fingerprint" size="small">{{ fingerprint(row) || '-' }}</el-text>
          </template>
        </el-table-column>
        <el-table-column label="结果" min-width="220">
          <template #default="{ row }">
            <el-tag :type="row.state === 'SYNCED' ? 'success' : row.state === 'FAILED' ? 'danger' : 'info'" size="small">{{ stateLabel(row.state) }}</el-tag>
            <div>{{ row.message }}</div>
            <el-text v-if="row.attempts" type="info" size="small">{{ row.candidate }} · {{ modeLabel(row.mode) }} · 第{{ row.attempts }}组</el-text>
          </template>
        </el-table-column>
        <el-table-column label="系统版本" min-width="180">
          <template #default="{ row }">{{ [row.facts.osName, row.facts.osVersion].filter(Boolean).join(' ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="主机名 / 内核" min-width="180">
          <template #default="{ row }"><div>{{ row.facts.hostName || '-' }}</div><el-text type="info" size="small">{{ row.facts.kernelVersion || '-' }}</el-text></template>
        </el-table-column>
      </el-table>
      <Pagination v-show="filteredRows.length" :total="filteredRows.length" v-model:page="page" v-model:limit="pageSize" :auto-scroll="false" />
    </template>
    <template #footer>
      <el-button @click="closeDialog()">关闭</el-button>
      <el-button v-if="task && !running" icon="RefreshLeft" :loading="busy" @click="resetTask">重新配置</el-button>
      <el-button v-if="running" type="danger" plain icon="VideoPause" :loading="busy" :disabled="task.state === 'STOPPING'" @click="cancelTask">停止扫描</el-button>
      <el-button v-if="task && error" icon="Refresh" @click="poll">刷新状态</el-button>
      <el-button v-if="!task" type="primary" icon="Search" :loading="busy" :disabled="loading" @click="start">探测端口</el-button>
      <el-button v-if="task?.state === 'REVIEW'" type="primary" icon="Check" :loading="busy" :disabled="!selected.length" @click="confirm">确认指纹并扫描同步</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="poolOpen" title="现场授权候选凭据" size="min(720px, 96vw)" append-to-body :close-on-click-modal="false" :before-close="closePool" @closed="clearCredential">
    <el-button v-if="!credentialFormOpen" icon="Plus" type="primary" @click="editCredential()">新增凭据</el-button>
    <el-form v-if="credentialFormOpen" ref="credentialRef" :model="credential" :rules="credentialRules" :disabled="savingCredential" label-position="top" class="scan-form">
      <el-form-item label="凭据名称" prop="name"><el-input v-model="credential.name" maxlength="80" /></el-form-item>
      <el-form-item label="登录路径" prop="mode">
        <el-radio-group v-model="credential.mode"><el-radio value="ROOT_DIRECT">root直接登录</el-radio><el-radio value="HIK_SU_ROOT">hik登录后切换root</el-radio></el-radio-group>
      </el-form-item>
      <el-form-item v-if="credential.mode === 'HIK_SU_ROOT'" label="hik密码" prop="hikPassword">
        <el-input v-model="credential.hikPassword" type="password" show-password autocomplete="new-password" maxlength="128" :placeholder="credential.hikConfigured ? '已配置，留空保留' : '请输入授权密码'" />
      </el-form-item>
      <el-form-item label="root密码" prop="rootPassword">
        <el-input v-model="credential.rootPassword" type="password" show-password autocomplete="new-password" maxlength="128" :placeholder="credential.rootConfigured ? '已配置，留空保留' : '请输入授权密码'" />
      </el-form-item>
      <div class="scan-toolbar"><el-button :disabled="savingCredential" @click="clearCredential">取消</el-button><el-button type="primary" :loading="savingCredential" @click="saveCredential">保存凭据</el-button></div>
    </el-form>
    <el-table v-else :data="credentials" class="scan-form">
      <el-table-column prop="name" label="名称" min-width="130" />
      <el-table-column label="路径" min-width="150"><template #default="{ row }">{{ modeLabel(row.mode) }}</template></el-table-column>
      <el-table-column label="密码" width="90"><template #default>已加密</template></el-table-column>
      <el-table-column label="操作" width="145"><template #default="{ row }"><el-button link type="primary" icon="Edit" @click="editCredential(row)">编辑</el-button><el-button link type="danger" icon="Delete" @click="removeCredential(row)">删除</el-button></template></el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { getServerScanOptions, saveServerScanCredential, deleteServerScanCredential, startServerScan,
  getServerScan, getActiveServerScan, confirmServerScan, cancelServerScan } from '@/api/support/equipment'
const props = defineProps({ modelValue: Boolean, siteId: [Number, String], platformId: [Number, String], targets: { type: String, default: '' } })
const emit = defineEmits(['update:modelValue', 'synced'])
const { proxy } = getCurrentInstance()
const loading = ref(false), busy = ref(false), error = ref(''), task = ref(null)
const credentials = ref([]), platforms = ref([]), selected = ref([]), chosenPorts = reactive({})
const formRef = ref(), tableRef = ref(), credentialRef = ref()
const form = reactive({ platformId: null, targets: '', ports: [22, 2343, 55555], credentialIds: [], attemptLimit: 1 })
const page = ref(1), pageSize = ref(20), resultFilter = ref('ALL')
const poolOpen = ref(false), credentialFormOpen = ref(false), savingCredential = ref(false)
const credential = reactive({ credentialId: null, name: '', mode: 'HIK_SU_ROOT', hikPassword: '', rootPassword: '', hikConfigured: false, rootConfigured: false })
let credentialBefore = ''
const required = message => ({ required: true, message, trigger: 'change' })
const rules = { platformId: [required('请选择子平台')], targets: [required('请填写扫描范围')], ports: [required('请选择端口')], credentialIds: [required('请选择授权凭据')] }
const credentialRules = { name: [required('请填写名称')], mode: [required('请选择登录路径')] }
const subplatforms = computed(() => platforms.value.filter(p => p.platformLevel === 'SUB').map(p => ({ ...p,
  label: (platforms.value.find(parent => Number(parent.platformId) === Number(p.parentPlatformId))?.platformName || '') + ' / ' + p.platformName })))
const running = computed(() => ['DISCOVERING', 'SCANNING', 'STOPPING'].includes(task.value?.state))
const step = computed(() => !task.value ? 0 : task.value.state === 'DISCOVERING' ? 1 : ['REVIEW', 'SCANNING', 'STOPPING'].includes(task.value.state) ? 2 : 3)
const progress = computed(() => Math.min(100, Math.floor((task.value?.done || 0) * 100 / (task.value?.total || 1))))
const filteredRows = computed(() => (task.value?.rows || []).filter(row => resultFilter.value === 'ALL' || row.state === resultFilter.value))
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const syncedCount = computed(() => (task.value?.rows || []).filter(row => row.state === 'SYNCED').length)
const failedCount = computed(() => (task.value?.rows || []).filter(row => row.state === 'FAILED').length)
const modeLabel = mode => mode === 'ROOT_DIRECT' ? 'root直接登录' : mode === 'HIK_SU_ROOT' ? 'hik切换root' : '-'
const serverLabel = row => row.existing ? row.serverName || '已登记服务器' : row.state === 'SYNCED' ? row.facts.hostName?.slice(0, 120) || '服务器-' + row.address : '待新建'
const stateLabel = state => ({ PENDING: '等待中', READY: '可连接', NO_SSH: '未发现SSH', VERIFYING: '验证中', SYNCED: '已同步', FAILED: '失败', SKIPPED: '跳过' }[state] || state)
const taskLabel = computed(() => ({ DISCOVERING: '端口探测中', REVIEW: '待核对指纹', SCANNING: '核验并同步中', STOPPING: '正在停止', CANCELLED: '已停止', DONE: '已完成', FAILED: '任务未完成', EXPIRED: '任务已过期' }[task.value?.state] || ''))
let timer, generation = 0
watch(() => form.credentialIds.length, size => { form.attemptLimit = Math.min(form.attemptLimit, Math.max(1, size)) })
watch(() => props.modelValue, value => { if (value) openDialog(); else stopPolling() }, { immediate: true })
function stopPolling() { generation++; clearTimeout(timer) }
onBeforeUnmount(stopPolling)
async function loadOptions() {
  const token = generation, siteId = props.siteId
  const response = await getServerScanOptions(props.siteId)
  if (token !== generation || siteId !== props.siteId) return
  credentials.value = response.data.credentials || []; platforms.value = response.data.platforms || []
  form.credentialIds = form.credentialIds.filter(id => credentials.value.some(c => c.credentialId === id))
}
async function openDialog() {
  stopPolling(); const token = generation
  loading.value = true; error.value = ''; task.value = null; selected.value = []; page.value = 1; resultFilter.value = 'ALL'
  form.platformId = props.platformId ? Number(props.platformId) : null; form.targets = props.targets
  try {
    await loadOptions()
    if (token !== generation) return
    if (!subplatforms.value.some(p => p.platformId === form.platformId)) form.platformId = subplatforms.value.length === 1 ? subplatforms.value[0].platformId : null
    const response = await getActiveServerScan(props.siteId)
    if (token !== generation) return
    if (response.data) acceptTask(response.data)
  } catch { error.value = '扫描配置加载失败，请检查扫描权限和数据库升级状态' }
  finally { if (token === generation) loading.value = false }
}
function acceptTask(value) {
  const previous = task.value?.state
  task.value = value
  for (const row of value.rows || []) {
    if (!chosenPorts[row.address]) chosenPorts[row.address] = row.ports.find(p => p.status === 'READY' && Number(p.port) === Number(row.registeredPort))?.port
      || row.ports.find(p => p.status === 'READY')?.port
  }
  clearTimeout(timer)
  if (running.value && props.modelValue) timer = setTimeout(poll, 2000)
  if (previous !== value.state && ['DONE', 'CANCELLED', 'FAILED'].includes(value.state)) emit('synced')
}
async function poll() {
  clearTimeout(timer)
  if (!task.value || !props.modelValue) return
  const token = generation, id = task.value.taskId
  try { const response = await getServerScan(id); if (token === generation) { error.value = ''; acceptTask(response.data) } }
  catch (failure) {
    if (token !== generation) return
    error.value = '暂时无法读取任务状态；服务重启后需重新扫描，不会自动重试密码'
    if (String(failure.message || '').includes('任务不存在')) task.value = { ...task.value, state: 'EXPIRED' }
  }
}
async function start() {
  if (busy.value || !await formRef.value.validate().catch(() => false)) return
  try { await proxy.$modal.confirm('确认只扫描已获授权的地址范围？本阶段仅探测端口和SSH指纹，不尝试密码。') } catch { return }
  busy.value = true; error.value = ''
  try { acceptTask((await startServerScan({ ...form, siteId: Number(props.siteId) })).data) }
  catch (failure) { error.value = failure.message || '扫描任务提交失败' }
  finally { busy.value = false }
}
function fingerprint(row) { return row.ports.find(p => p.port === chosenPorts[row.address])?.fingerprint }
function selectReady() {
  tableRef.value?.clearSelection()
  for (const row of (task.value?.rows || []).filter(row => row.state === 'READY').slice(0, 128)) tableRef.value?.toggleRowSelection(row, true)
}
async function confirm() {
  if (busy.value || !selected.value.length) return
  if (selected.value.length > 128) { proxy.$modal.msgWarning('每次最多核验同步128台服务器'); return }
  try { await proxy.$modal.confirm('确认已核对所选主机指纹，并使用授权凭据尝试登录？错误密码可能触发设备锁定；验证成功后自动同步账号、端口、登录方式和系统资料。') } catch { return }
  busy.value = true; error.value = ''
  try { acceptTask((await confirmServerScan(task.value.taskId, { fingerprintsConfirmed: true, targets: selected.value.map(row => ({ address: row.address, port: chosenPorts[row.address] })) })).data) }
  catch (failure) { error.value = failure.message || '核验提交失败' }
  finally { busy.value = false }
}
async function cancelTask() {
  try { await proxy.$modal.confirm('停止后不再登录后续目标。已开始的单台操作可能完成，已同步的数据不会撤销。确认停止？') } catch { return }
  busy.value = true
  try { acceptTask((await cancelServerScan(task.value.taskId)).data) } finally { busy.value = false }
}
async function resetTask() {
  if (busy.value) return
  busy.value = true
  try {
    if (task.value?.state === 'REVIEW') await cancelServerScan(task.value.taskId)
    stopPolling(); task.value = null; selected.value = []; page.value = 1; error.value = ''; resultFilter.value = 'ALL'
    for (const key of Object.keys(chosenPorts)) delete chosenPorts[key]
    await loadOptions()
  } finally { busy.value = false }
}
async function closeDialog(done) {
  if (running.value) { try { await proxy.$modal.confirm('关闭窗口后任务继续运行，可重新打开查看；需要停止时请使用停止扫描。确认关闭窗口？') } catch { return } }
  stopPolling(); poolOpen.value = false; clearCredential(); emit('update:modelValue', false)
  if (typeof done === 'function') done()
}
function clearCredential() {
  credentialFormOpen.value = false
  credentialBefore = ''
  Object.assign(credential, { credentialId: null, name: '', mode: 'HIK_SU_ROOT', hikPassword: '', rootPassword: '', hikConfigured: false, rootConfigured: false })
}
function editCredential(row) { clearCredential(); if (row) Object.assign(credential, row); credentialBefore = JSON.stringify(credential); credentialFormOpen.value = true }
async function closePool(done) {
  if (savingCredential.value) return
  if (credentialFormOpen.value && credentialBefore !== JSON.stringify(credential)) {
    try { await proxy.$modal.confirm('授权凭据有未保存修改，确认放弃？') } catch { return }
  }
  done()
}
async function saveCredential() {
  if (savingCredential.value || !await credentialRef.value.validate().catch(() => false)) return
  if ((!credential.rootPassword && !credential.rootConfigured) || (credential.mode === 'HIK_SU_ROOT' && !credential.hikPassword && !credential.hikConfigured)) { proxy.$modal.msgWarning('请补全当前路径所需密码'); return }
  savingCredential.value = true
  try { await saveServerScanCredential({ credentialId: credential.credentialId, name: credential.name, mode: credential.mode,
    hikPassword: credential.hikPassword, rootPassword: credential.rootPassword, siteId: Number(props.siteId) }); clearCredential(); await loadOptions(); proxy.$modal.msgSuccess('授权凭据已加密保存') }
  finally { savingCredential.value = false }
}
async function removeCredential(row) {
  try { await proxy.$modal.confirm('删除授权凭据“' + row.name + '”？后续验证将停止使用该凭据，已开始的单台操作可能完成。') } catch { return }
  await deleteServerScanCredential(props.siteId, row.credentialId); await loadOptions()
}
</script>

<style scoped>
.scan-form { margin-top: var(--el-font-size-base); }
.scan-form__columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--el-font-size-base); }
.scan-credential-picker { display: flex; width: 100%; gap: var(--el-font-size-base); }
.scan-credential-picker .el-select { flex: 1; min-width: 0; }
.scan-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: var(--el-font-size-base); margin: var(--el-font-size-base) 0; }
.scan-result-filter { width: 140px; }
.scan-port-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.scan-fingerprint { display: block; overflow-wrap: anywhere; }
@media (max-width: 768px) { .scan-steps { display: none; } .scan-form__columns { grid-template-columns: minmax(0, 1fr); } .scan-credential-picker { flex-wrap: wrap; } }
</style>
