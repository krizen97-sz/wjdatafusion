<template>
  <section>
    <el-tabs v-model="tab" class="motion-tabs">
      <el-tab-pane label="产物与交付" name="artifacts">
        <template #label><span class="motion-control-label"><svg-icon icon-class="download" class="motion-control-label__icon" /><span class="motion-control-label__text">产物与交付</span></span></template>
        <el-alert title="完整批次产物" description="仅整批成功且确认清理的运行可交付。上传使用临时名、回读校验和批次完成标记；接收方需以_SUCCESS.json作为整批完成依据。失败保留本地产物，不自动重试。" type="info" :closable="false" class="mb16" />
        <el-form :inline="true">
          <el-form-item label="流程"><el-select :model-value="flowId" aria-label="交付流程" @update:model-value="emit('select-flow', $event)"><el-option v-for="flow in flows" :key="flow.id" :value="flow.id" :label="flow.name" /></el-select></el-form-item>
          <el-form-item label="运行记录"><el-select v-model="runId" aria-label="完整产物运行记录" placeholder="选择运行记录" @change="loadArtifacts"><el-option v-for="run in runs" :key="run.id" :value="run.id" :label="`${formatTime(run.createdAt)} · ${run.artifactCount || 0} 个产物 · ${run.id.slice(0, 8)}`" /></el-select></el-form-item>
          <el-form-item><el-button icon="Refresh" :loading="loading" @click="load">刷新记录</el-button></el-form-item>
        </el-form>
        <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
        <el-empty v-if="!eligibleRuns.length && !loading" description="暂无完整产物，请先执行流程。旧版仅保留预览的运行需重新执行。" :image-size="70" />
        <el-alert v-else-if="runId && !manifest && !artifactLoading" title="所选运行尚无可交付的完整产物" description="请确认该次运行已成功结束并完成清理；失败与空批次不会发布交付清单。" type="info" :closable="false" class="mb16" />
        <template v-if="manifest">
          <el-space wrap class="mb16"><el-text>{{ manifest.artifacts.length }} 个完整产物</el-text><el-button v-if="canEdit" v-hasPermi="['governance:flow:test']" type="primary" icon="Upload" :disabled="!manifest.artifacts.length" @click="openDelivery">交付完整产物</el-button></el-space>
          <el-table :data="manifest.artifacts" row-key="id" v-loading="artifactLoading">
            <el-table-column label="文件名" prop="filename" min-width="180" show-overflow-tooltip />
            <el-table-column label="字节数" prop="byteSize" width="130" />
            <el-table-column label="SHA-256" prop="sha256" min-width="260" show-overflow-tooltip />
            <el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" :loading="downloading === row.id" :disabled="!!downloading" @click="download(row)">下载原件</el-button></template></el-table-column>
          </el-table>
        </template>
        <h3 class="delivery-heading">交付记录</h3>
        <el-table :data="visibleJobs" row-key="id" v-loading="loading" empty-text="暂无交付记录">
          <el-table-column label="目标连接" prop="connectionName" min-width="160" />
          <el-table-column label="状态" width="160"><template #default="{ row }"><el-tag :type="deliveryState(row.status).type">{{ deliveryState(row.status).label }}</el-tag></template></el-table-column>
          <el-table-column label="已核对 / 总文件" width="150"><template #default="{ row }">{{ row.entries.filter(item => item.status === 'VERIFIED').length }} / {{ row.entries.length }}</template></el-table-column>
          <el-table-column label="更新时间" min-width="180"><template #default="{ row }">{{ formatTime(row.updatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="180"><template #default="{ row }"><el-button link type="primary" @click="detail = row; detailOpen = true">详情</el-button><el-button v-if="deliveryState(row.status).retry" v-hasPermi="['governance:flow:test']" link type="warning" :loading="busy === row.id" :disabled="!!busy" @click="retry(row)">核对后重试</el-button></template></el-table-column>
        </el-table>
        <pagination v-if="jobs.length" v-model:page="page" v-model:limit="limit" :total="jobs.length" />
      </el-tab-pane>
      <el-tab-pane v-if="canEdit" label="FTP连接" name="ftp" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="server" class="motion-control-label__icon" /><span class="motion-control-label__text">FTP连接</span></span></template>
        <ftp-connections v-if="tab === 'ftp'" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="deliveryOpen" title="交付完整产物" width="600px" append-to-body :close-on-click-modal="!submitting" :show-close="!submitting">
      <el-alert title="交付所选运行的完整文件" description="目标内建立独立批次目录；所有文件核对完成后才发布完成标记。本地产物持续保留。" type="info" :closable="false" class="mb16" />
      <el-form label-position="top"><el-form-item label="目标FTP连接" required><el-select v-model="connectionId" aria-label="交付目标FTP连接" :disabled="submitting"><el-option v-for="profile in profiles" :key="profile.id" :value="profile.id" :label="`${profile.name} · ${profile.transferMode}`" /></el-select></el-form-item></el-form>
      <el-empty v-if="!profiles.length" description="请先在FTP连接页建立连接" :image-size="50" />
      <el-alert v-if="submitError" :title="submitError" type="error" :closable="false" />
      <template #footer><el-button :disabled="submitting" @click="deliveryOpen = false">取消</el-button><el-button type="primary" :disabled="!connectionId" :loading="submitting" @click="submit">开始交付</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailOpen" title="完整产物交付详情" size="720px" append-to-body>
      <template v-if="detail">
        <el-alert v-if="detail.error" :title="detail.error" type="warning" :closable="false" class="mb16" />
        <el-descriptions :column="1" border><el-descriptions-item label="交付状态"><el-tag :type="deliveryState(detail.status).type">{{ deliveryState(detail.status).label }}</el-tag></el-descriptions-item><el-descriptions-item label="运行编号">{{ detail.runId }}</el-descriptions-item><el-descriptions-item label="目标目录"><span class="delivery-path">{{ detail.remoteDirectory }}</span></el-descriptions-item><el-descriptions-item label="连接版本">{{ detail.connectionName }} · V{{ detail.connectionRevision }}</el-descriptions-item><el-descriptions-item label="凭据版本">V{{ detail.credentialRevision || detail.connectionRevision }}</el-descriptions-item><el-descriptions-item label="传输模式">{{ detail.transferMode }}</el-descriptions-item><el-descriptions-item label="尝试次数">{{ detail.attempts }}</el-descriptions-item></el-descriptions>
        <el-table :data="detail.entries" class="mt16"><el-table-column label="文件" prop="filename" min-width="160" /><el-table-column label="字节数" prop="byteSize" width="120" /><el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="row.status === 'VERIFIED' ? 'success' : 'info'">{{ row.status === 'VERIFIED' ? '已回读核对' : '待交付' }}</el-tag></template></el-table-column></el-table>
      </template>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue'
import { checkPermi } from '@/utils/permission'
import { blobValidate } from '@/utils/ruoyi'
import { listGovernanceTestRuns } from '@/api/governance'
import { listFtpConnections, getArtifacts, downloadArtifact, listDeliveries, submitDelivery, retryDelivery } from '@/api/governance/deliveries'
import { errorMessage } from '../workspaceRules'
import { deliverableRun, deliveryState } from '../deliveryRules'
import FtpConnections from './FtpConnections.vue'
const props = defineProps({ flowId: { type: String, default: '' }, flows: { type: Array, default: () => [] }, initialRunId: { type: String, default: '' }, initialDeliveryId: { type: String, default: '' } })
const emit = defineEmits(['select-flow'])
const { proxy } = getCurrentInstance()
const canEdit = computed(() => checkPermi(['governance:flow:edit']))
const tab = ref('artifacts'), runs = ref([]), runId = ref(''), jobs = ref([]), manifest = ref(null), profiles = ref([]), connectionId = ref('')
const loading = ref(false), artifactLoading = ref(false), error = ref(''), downloading = ref(''), busy = ref(''), deliveryOpen = ref(false), submitting = ref(false), submitError = ref(''), detail = ref(null), detailOpen = ref(false)
const page = ref(1), limit = ref(20)
const eligibleRuns = computed(() => runs.value.filter(deliverableRun))
const visibleJobs = computed(() => jobs.value.slice((page.value - 1) * limit.value, page.value * limit.value))
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
let disposed = false, generation = 0, timer, pollUntil = 0
async function load() {
  if (loading.value || !props.flowId) return
  const sequence = generation; loading.value = true; error.value = ''
  try {
    const response = await listGovernanceTestRuns(props.flowId)
    if (disposed || sequence !== generation) return
    runs.value = response.data || []
    if (props.initialRunId && !runId.value) {
      if (!runs.value.some(run => run.id === props.initialRunId)) throw new Error('指定运行不在当前流程中，或当前账号无权查看')
      runId.value = props.initialRunId
    }
    if (!runs.value.some(run => run.id === runId.value)) runId.value = eligibleRuns.value[0]?.id || runs.value[0]?.id || ''
    await loadArtifacts()
  } catch (e) { if (!disposed && sequence === generation) error.value = errorMessage(e, '运行记录读取失败') }
  finally { if (!disposed && sequence === generation) loading.value = false }
}
async function loadArtifacts() {
  const sequence = generation, selected = runId.value
  if (manifest.value?.runId !== selected) manifest.value = null
  artifactLoading.value = true; error.value = ''; clearTimeout(timer)
  try {
    const canRead = deliverableRun(runs.value.find(run => run.id === selected))
    const [artifactResponse, deliveryResponse] = await Promise.all([canRead ? getArtifacts(selected) : Promise.resolve({ data: null }), listDeliveries(selected || undefined)])
    if (disposed || sequence !== generation || selected !== runId.value) return
    manifest.value = artifactResponse.data
    jobs.value = (deliveryResponse.data || []).filter(job => selected || runs.value.some(run => run.id === job.runId))
    page.value = Math.min(page.value, Math.max(1, Math.ceil(jobs.value.length / limit.value)))
    if (detail.value) detail.value = jobs.value.find(job => job.id === detail.value.id) || detail.value
    if (props.initialDeliveryId && !detail.value) { detail.value = jobs.value.find(job => job.id === props.initialDeliveryId) || null; detailOpen.value = !!detail.value }
    schedulePoll()
  } catch (e) { if (!disposed && sequence === generation && selected === runId.value) error.value = errorMessage(e, '完整产物读取失败') }
  finally { if (!disposed && sequence === generation && selected === runId.value) artifactLoading.value = false }
}
function schedulePoll() { clearTimeout(timer); if (!disposed && jobs.value.some(job => deliveryState(job.status).active) && Date.now() < pollUntil) timer = setTimeout(loadArtifacts, 2000) }
async function openDelivery() {
  submitError.value = ''; profiles.value = []; connectionId.value = ''; deliveryOpen.value = true
  try { const response = await listFtpConnections(); if (!disposed) profiles.value = response.data || [] } catch (e) { if (!disposed) submitError.value = errorMessage(e, 'FTP连接读取失败') }
}
async function submit() {
  if (submitting.value || !connectionId.value || !manifest.value) return
  submitting.value = true; submitError.value = ''
  try { await submitDelivery({ runId: manifest.value.runId, connectionId: connectionId.value }); if (!disposed) { deliveryOpen.value = false; pollUntil = Date.now() + 240000; await loadArtifacts(); proxy.$modal.msgSuccess('交付已提交，请查看真实运行状态') } }
  catch (e) { if (!disposed) submitError.value = errorMessage(e, '交付提交失败') } finally { if (!disposed) submitting.value = false }
}
async function retry(row) {
  if (busy.value) return
  try { await proxy.$modal.confirm('使用当前连接的账号密码核对重试，目标与传输设置须保持不变。若远端已有完成标记，只核对、不补传；本地产物保留。确认继续？') } catch { return }
  busy.value = row.id
  try { await retryDelivery(row.id, row.revision); if (!disposed) { pollUntil = Date.now() + 240000; await loadArtifacts() } } catch (e) { if (!disposed) error.value = errorMessage(e, '交付重试失败') } finally { if (!disposed) busy.value = '' }
}
async function download(row) {
  if (downloading.value) return
  downloading.value = row.id
  try { const blob = await downloadArtifact(manifest.value.runId, row.id); if (!blobValidate(blob)) { await proxy.$download.printErrMsg(blob); return } proxy.$download.saveAs(blob, row.filename) }
  catch (e) { if (!disposed) error.value = errorMessage(e, '完整产物下载失败') } finally { if (!disposed) downloading.value = '' }
}
watch(() => [props.flowId, props.initialRunId, props.initialDeliveryId], () => { generation++; clearTimeout(timer); loading.value = false; runs.value = []; runId.value = ''; manifest.value = null; jobs.value = []; detail.value = null; detailOpen.value = false; pollUntil = Date.now() + 240000; load() }, { immediate: true })
onBeforeUnmount(() => { disposed = true; generation++; clearTimeout(timer) })
</script>
<style scoped>
.delivery-heading { color: var(--app-heading); font-size: var(--el-font-size-base); margin-top: var(--el-component-size); }
.delivery-path { overflow-wrap: anywhere; }
</style>
