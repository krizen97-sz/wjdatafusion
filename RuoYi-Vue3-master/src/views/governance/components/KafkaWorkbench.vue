<template>
  <section>
    <el-alert v-if="!status.enabled" title="当前环境尚未启用Kafka批次源" description="由服务端启用并登记允许的broker后，可建立连接和接收批次。" type="info" :closable="false" class="mb16" />
    <el-alert title="有界批次，显式确认" description="一次最多100条 / 256 KiB。取批不提交位点；处理和交付完成后再确认。当前支持IPv4明文协议、未压缩且非事务的UTF-8消息，读取模式为READ_UNCOMMITTED；不直接迁移旧ZooKeeper消费组。" type="info" :closable="false" class="mb16" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
    <el-tabs v-model="tab" class="motion-tabs">
      <el-tab-pane label="批次记录" name="receipts">
        <template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">批次记录</span></span></template>
        <el-form :inline="true">
          <el-form-item label="Kafka源"><el-select v-model="profileId" aria-label="Kafka取批来源" :disabled="!!busy"><el-option v-for="profile in profiles" :key="profile.id" :value="profile.id" :label="`${profile.name} · ${profile.topic}`" /></el-select></el-form-item>
          <el-form-item><el-button v-hasPermi="['governance:flow:test']" type="primary" icon="Download" :disabled="!status.enabled || !profileId || !!busy" :loading="busy === 'receive'" @click="receive">接收一批</el-button><el-button icon="Refresh" :loading="loading" @click="load">刷新批次</el-button></el-form-item>
        </el-form>
        <el-table :data="visibleReceipts" row-key="id" v-loading="loading" empty-text="暂无源批次，接收前不会修改消费位点">
          <el-table-column label="来源 / Topic" min-width="180"><template #default="{ row }"><strong>{{ row.profileName }}</strong><div>{{ row.topic }}</div></template></el-table-column>
          <el-table-column label="状态" width="170"><template #default="{ row }"><el-tag :type="kafkaState(row.status).type">{{ kafkaState(row.status).label }}</el-tag></template></el-table-column>
          <el-table-column label="消息数" prop="recordCount" width="100" />
          <el-table-column label="接收时间" min-width="180"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" width="210"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情与执行</el-button><el-button v-if="canReleaseReceipt(row)" v-hasPermi="['governance:flow:test']" link type="warning" :disabled="!!busy" @click="release(row)">放弃取批</el-button></template></el-table-column>
        </el-table>
        <pagination v-if="receipts.length" v-model:page="page" v-model:limit="limit" :total="receipts.length" />
      </el-tab-pane>
      <el-tab-pane label="源连接" name="profiles">
        <template #label><span class="motion-control-label"><svg-icon icon-class="server" class="motion-control-label__icon" /><span class="motion-control-label__text">源连接</span></span></template>
        <el-button v-hasPermi="['governance:flow:edit']" type="primary" icon="Plus" class="mb16" :disabled="!status.enabled" @click="editProfile()">新建Kafka源</el-button>
        <el-table :data="profiles" row-key="id"><el-table-column label="连接名称" prop="name" min-width="170" /><el-table-column label="Topic" prop="topic" min-width="200" /><el-table-column label="专属消费组" prop="groupId" min-width="280" show-overflow-tooltip /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-hasPermi="['governance:flow:edit']" link type="primary" @click="editProfile(row)">编辑</el-button></template></el-table-column></el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="profileOpen" :title="profileForm.id ? '编辑Kafka源' : '新建Kafka源'" width="600px" append-to-body :close-on-click-modal="!saving" :show-close="!saving">
      <el-form ref="profileRef" :model="profileForm" :rules="profileRules" label-position="top" :disabled="saving">
        <el-form-item label="连接名称" prop="name"><el-input v-model="profileForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="Broker地址（逗号分隔）" prop="servers"><el-input v-model="profileForm.servers" placeholder="127.0.0.1:19092" /></el-form-item>
        <el-form-item label="Topic" prop="topic"><el-input v-model="profileForm.topic" /></el-form-item>
        <el-form-item label="专属消费组" prop="groupId"><el-input v-model="profileForm.groupId" /></el-form-item>
        <el-text type="info" size="small">消费组需以rynew-governance-{{ status.ownerId }}-开头。新组从最早可用数据开始；有未完成批次时不能改动连接目标。</el-text>
      </el-form>
      <el-alert v-if="formError" :title="formError" type="error" :closable="false" class="mt16" />
      <template #footer><el-button :disabled="saving" @click="profileOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button></template>
    </el-dialog>
    <el-drawer v-model="detailOpen" title="Kafka批次详情与执行" size="760px" append-to-body @closed="stopDetail">
      <template v-if="detail">
        <el-alert v-if="detail.receipt.error || execution?.error" :title="execution?.error || detail.receipt.error" type="warning" :closable="false" class="mb16" />
        <el-descriptions :column="1" border size="small"><el-descriptions-item label="批次状态"><el-tag :type="kafkaState(detail.receipt.status).type">{{ kafkaState(detail.receipt.status).label }}</el-tag></el-descriptions-item><el-descriptions-item label="消息数">{{ detail.receipt.recordCount }}</el-descriptions-item><el-descriptions-item label="输入摘要"><span class="kafka-hash">{{ detail.receipt.inputSha256 }}</span></el-descriptions-item><el-descriptions-item label="本批次目标位点">{{ JSON.stringify(detail.receipt.nextOffsets) }}</el-descriptions-item></el-descriptions>
        <template v-if="canExecuteReceipt(detail.receipt) && !execution">
          <el-alert title="使用已发布处理配置" description="保留发布版本的处理节点、参数和字典，用本次Kafka批次替换固定样本，并记录新的执行快照。" type="info" :closable="false" class="mt16 mb16" />
          <el-form label-position="top">
            <el-form-item label="处理流程"><el-select :model-value="flowId" aria-label="Kafka批次处理流程" @update:model-value="emit('select-flow', $event)"><el-option v-for="flow in flows" :key="flow.id" :label="flow.name" :value="flow.id" /></el-select></el-form-item>
            <el-form-item label="发布版本"><el-select v-model="releaseId" aria-label="Kafka批次处理发布版本"><el-option v-for="release in flowReleases" :key="release.id" :label="`V${release.version} · ${release.name}`" :value="release.id" /></el-select></el-form-item>
            <el-form-item v-if="canEdit" label="成功后FTP交付（可选）"><el-select v-model="ftpId" clearable aria-label="Kafka批次FTP交付目标"><el-option v-for="target in ftpProfiles" :key="target.id" :label="target.name" :value="target.id" /></el-select></el-form-item>
          </el-form>
          <el-button v-hasPermi="['governance:flow:test']" type="primary" :disabled="!releaseId || !!busy" :loading="busy === 'execute'" @click="execute">执行本批次</el-button>
        </template>
        <template v-if="execution">
          <el-descriptions :column="1" border size="small" class="mt16"><el-descriptions-item label="处理状态"><el-tag :type="kafkaExecutionState(detail.receipt, execution).type">{{ kafkaExecutionState(detail.receipt, execution).label }}</el-tag></el-descriptions-item><el-descriptions-item label="运行编号">{{ execution.runId }}</el-descriptions-item><el-descriptions-item label="原发布摘要"><span class="kafka-hash">{{ execution.releaseHash }}</span></el-descriptions-item><el-descriptions-item label="本次执行摘要"><span class="kafka-hash">{{ execution.executionHash }}</span></el-descriptions-item><el-descriptions-item label="交付状态"><el-tag v-if="execution.deliveryStatus" :type="deliveryState(execution.deliveryStatus).type">{{ deliveryState(execution.deliveryStatus).label }}</el-tag><span v-else>尚未开始</span></el-descriptions-item></el-descriptions>
          <el-space wrap class="mt16"><el-button v-hasPermi="['governance:flow:test']" type="primary" :disabled="!canAcknowledgeReceipt(detail.receipt, execution) || !!busy" :loading="busy === 'commit'" @click="acknowledge">确认本批消费位点</el-button><el-button v-if="['RECOVERY_REQUIRED', 'FAILED'].includes(execution.status)" v-hasPermi="['governance:flow:test']" :disabled="!!busy" @click="recover">核查执行</el-button><el-button @click="refreshDetail">刷新状态</el-button></el-space>
        </template>
        <el-collapse class="mt16"><el-collapse-item title="输入预览" name="input"><pre class="kafka-preview">{{ displayJson(detail.inputJson, 8000) }}</pre></el-collapse-item></el-collapse>
      </template>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { checkPermi } from '@/utils/permission'
import { listGovernanceReleases } from '@/api/governance/schedules'
import { listFtpConnections } from '@/api/governance/deliveries'
import * as api from '@/api/governance/kafka'
import { displayJson, errorMessage } from '../workspaceRules'
import { deliveryState } from '../deliveryRules'
import { kafkaState, kafkaExecutionState, canExecuteReceipt, canReleaseReceipt, canAcknowledgeReceipt } from '../kafkaRules'
const props = defineProps({ flowId: { type: String, default: '' }, flows: { type: Array, default: () => [] } })
const emit = defineEmits(['select-flow'])
const { proxy } = getCurrentInstance()
const canEdit = computed(() => checkPermi(['governance:flow:edit']))
const status = reactive({ enabled: false, ownerId: '' }), tab = ref('receipts'), profiles = ref([]), profileId = ref(''), receipts = ref([]), releases = ref([]), ftpProfiles = ref([])
const loading = ref(false), busy = ref(''), error = ref(''), profileOpen = ref(false), saving = ref(false), formError = ref(''), profileRef = ref()
const profileForm = reactive({ id: '', revision: null, name: '', servers: '127.0.0.1:19092', topic: '', groupId: '' })
const required = text => [{ required: true, message: `请填写${text}`, trigger: 'blur' }]
const profileRules = { name: required('名称'), servers: required('broker地址'), topic: required('topic'), groupId: required('消费组') }
const page = ref(1), limit = ref(20), detailOpen = ref(false), detail = ref(null), execution = ref(null), releaseId = ref(''), ftpId = ref('')
const visibleReceipts = computed(() => receipts.value.slice((page.value - 1) * limit.value, page.value * limit.value))
const flowReleases = computed(() => releases.value.filter(release => release.flowId === props.flowId))
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
let disposed = false, timer, deadline = 0, generation = 0
async function load() {
  if (loading.value) return; loading.value = true; error.value = ''
  try { const s = await api.kafkaStatus(); if (disposed) return; Object.assign(status, s.data); if (status.enabled) { const [p, r] = await Promise.all([api.kafkaProfiles(), api.kafkaReceipts()]); if (!disposed) { profiles.value = p.data || []; receipts.value = r.data || []; if (!profileId.value) profileId.value = profiles.value[0]?.id || '' } } }
  catch (e) { if (!disposed) error.value = errorMessage(e, 'Kafka状态读取失败') } finally { if (!disposed) loading.value = false }
}
function editProfile(value) { Object.assign(profileForm, { id: '', revision: null, name: '', servers: '127.0.0.1:19092', topic: '', groupId: `rynew-governance-${status.ownerId}-` }, value || {}, { servers: value?.bootstrapServers?.join(',') || '127.0.0.1:19092' }); formError.value = ''; profileOpen.value = true; profileRef.value?.clearValidate() }
async function saveProfile() { if (saving.value || !await profileRef.value?.validate().catch(() => false)) return; saving.value = true; try { const { id, revision, name, servers, topic, groupId } = profileForm; await api.saveKafkaProfile(id, { revision, name, bootstrapServers: servers.split(',').map(s => s.trim()).filter(Boolean), topic, groupId }); if (!disposed) { profileOpen.value = false; await load() } } catch (e) { if (!disposed) formError.value = errorMessage(e, 'Kafka源保存失败') } finally { if (!disposed) saving.value = false } }
async function receive() { if (busy.value) return; busy.value = 'receive'; error.value = ''; try { const r = await api.receiveKafkaBatch(profileId.value); if (!disposed) { await load(); await openDetail(r.data) } } catch (e) { if (!disposed) error.value = errorMessage(e, '取批未确认，请先刷新记录，不要盲目重复接收') } finally { if (!disposed) busy.value = '' } }
async function release(row) { if (busy.value) return; try { await proxy.$modal.confirm('放弃本次本地取批并释放占用；Kafka位点不变，下次仍可能接收这些消息。确认继续？') } catch { return } busy.value = 'release'; try { await api.releaseKafkaBatch(row.id); await load() } catch (e) { if (!disposed) error.value = errorMessage(e, '批次不能释放') } finally { if (!disposed) busy.value = '' } }
async function openDetail(row) { generation++; clearTimeout(timer); detail.value = { receipt: row, inputJson: null }; execution.value = null; releaseId.value = ''; ftpId.value = ''; detailOpen.value = true; deadline = Date.now() + 300000; try { const [r, f] = await Promise.all([listGovernanceReleases(), canEdit.value ? listFtpConnections() : Promise.resolve({ data: [] })]); if (!disposed) { releases.value = r.data || []; ftpProfiles.value = f.data || [] } await refreshDetail() } catch (e) { if (!disposed) error.value = errorMessage(e, '批次详情读取失败') } }
async function refreshDetail() {
  if (!detail.value || !detailOpen.value) return
  const id = detail.value.receipt.id, sequence = generation; clearTimeout(timer)
  try { const [r, e] = await Promise.all([api.kafkaReceipt(id), api.kafkaExecution(id)]); if (disposed || sequence !== generation) return; detail.value = r.data; execution.value = e.data; if (Date.now() < deadline && execution.value && ['RUN_PLANNED', 'RUNNING', 'DELIVERING'].includes(execution.value.status)) timer = setTimeout(refreshDetail, 1500) }
  catch (e) { if (!disposed && sequence === generation) error.value = errorMessage(e, '执行状态未确认') }
}
async function execute() { if (busy.value || !releaseId.value) return; busy.value = 'execute'; try { await api.executeKafkaBatch(detail.value.receipt.id, { releaseId: releaseId.value, deliveryConnectionId: ftpId.value || null }); deadline = Date.now() + 300000; await refreshDetail(); await load() } catch (e) { if (!disposed) error.value = errorMessage(e, '批次提交未确认，请核查已有运行') } finally { if (!disposed) busy.value = '' } }
async function acknowledge() { if (busy.value) return; try { await proxy.$modal.confirm('确认后，此专属消费组会从本批次目标位点继续。服务端会复核真实运行、完整产物和交付结果；不允许跳过未处理消息。确认继续？') } catch { return } busy.value = 'commit'; try { await api.commitKafkaBatch(detail.value.receipt.id); await refreshDetail(); await load() } catch (e) { if (!disposed) error.value = errorMessage(e, '消费位点尚未确认') } finally { if (!disposed) busy.value = '' } }
async function recover() { if (busy.value) return; busy.value = 'recover'; try { await api.recoverKafkaExecution(detail.value.receipt.id); deadline = Date.now() + 300000; await refreshDetail() } catch (e) { if (!disposed) error.value = errorMessage(e, '执行核查未完成') } finally { if (!disposed) busy.value = '' } }
function stopDetail() { generation++; clearTimeout(timer) }
watch(() => props.flowId, () => { releaseId.value = '' })
onMounted(load)
onBeforeUnmount(() => { disposed = true; stopDetail() })
</script>
<style scoped>
.kafka-hash { overflow-wrap: anywhere; }
.kafka-preview { max-height: 280px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; background: var(--surface-subtle); padding: var(--el-font-size-base); font-size: var(--el-font-size-small); }
</style>
