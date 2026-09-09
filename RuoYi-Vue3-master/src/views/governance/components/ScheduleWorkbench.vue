<template>
  <section class="governance-schedules">
    <div class="governance-schedules__toolbar mb16">
      <el-form inline class="governance-schedules__filter">
        <el-form-item label="当前流程">
          <el-select :model-value="selectedFlowId" filterable placeholder="请选择流程" aria-label="发布与定时任务的流程" class="governance-schedules__flow" @change="emit('select-flow', $event)">
            <el-option v-for="flow in flows" :key="flow.id" :value="String(flow.id)" :label="flow.name" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-button icon="Refresh" :loading="loading" @click="refreshAll()">刷新版本与任务</el-button>
    </div>

    <el-alert title="固定批次任务" description="先发布已保存的流程与固定 JSON 批次，再配置定时执行。每次运行使用相同批次；当前不包含 Kafka 持续消费或外部数据源增量采集。" type="info" show-icon :closable="false" class="mb16" />
    <el-alert v-if="actionError || requestError" :title="actionError || requestError" type="error" :closable="false" class="mb16" />
    <el-alert v-if="pollNotice" :title="pollNotice" description="列表状态尚未再次确认，可点击“刷新版本与任务”继续核查。" type="warning" :closable="false" class="mb16" />
    <el-alert v-if="!engineReady" title="执行引擎当前不可用，暂不能发布、启用或立即运行。已有任务状态以服务端返回为准。" type="warning" :closable="false" class="mb16" />
    <el-empty v-if="!selectedFlowId" description="选择流程后，发布版本并配置定时任务" />

    <el-tabs v-else v-model="activeTab" class="motion-tabs">
      <el-tab-pane label="定时任务" name="schedules">
        <template #label><span class="motion-control-label"><svg-icon icon-class="job" class="motion-control-label__icon" /><span class="motion-control-label__text">定时任务</span></span></template>
        <div class="governance-schedules__toolbar mb16">
          <el-text type="info">新任务默认暂停；禁止并发，错过的触发不补跑。</el-text>
          <el-button v-hasPermi="['governance:flow:edit']" type="primary" icon="Plus" :disabled="!flowReleases.length || loading" @click="openSchedule()">新建定时任务</el-button>
        </div>
        <el-table v-loading="loading" :data="pagedSchedules" row-key="id" empty-text="暂无定时任务。请先在“发布版本”中发布当前流程。">
          <el-table-column label="任务 / 发布版本" min-width="200" show-overflow-tooltip>
            <template #default="{ row }"><strong>{{ row.name }}</strong><div class="governance-schedules__hint">{{ releaseLabel(row.releaseId) }}</div></template>
          </el-table-column>
          <el-table-column label="任务状态" width="150">
            <template #default="{ row }"><el-tag :type="scheduleState(row).type">{{ scheduleState(row).label }}</el-tag><div class="governance-schedules__hint">{{ row.enabled ? '已启用' : '已暂停触发' }}</div></template>
          </el-table-column>
          <el-table-column label="执行时间" min-width="210" show-overflow-tooltip>
            <template #default="{ row }"><div>{{ row.cron }}</div><div class="governance-schedules__hint">{{ row.timeZone }}</div><div class="governance-schedules__hint">下次：{{ row.nextRunAt ? formatScheduleTime(row.nextRunAt, row.timeZone) : '暂无触发时间' }}</div></template>
          </el-table-column>
          <el-table-column label="最近运行" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.lastRunStatus" :type="runState(row.lastRunStatus).type" size="small">{{ runState(row.lastRunStatus).label }}</el-tag>
              <el-text v-else type="info">尚未运行</el-text>
              <div v-if="row.lastRunAt" class="governance-schedules__hint">{{ formatScheduleTime(row.lastRunAt, row.timeZone) }}</div>
              <div v-if="row.lastError" class="governance-schedules__hint">{{ row.lastError }}</div>
              <div v-if="row.skippedCount" class="governance-schedules__hint">已跳过 {{ row.skippedCount }} 次：{{ row.lastSkippedReason || '查看任务状态' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button v-hasPermi="['governance:flow:edit']" link type="primary" :disabled="!!rowBusy[row.id] || !!row.activeRunId || row.recoveryRequired" @click="openSchedule(row)">编辑</el-button>
              <el-button v-hasPermi="['governance:flow:test']" link :type="row.enabled ? 'warning' : 'primary'" :loading="rowBusy[row.id] === 'state'" :disabled="!!rowBusy[row.id] || (!row.enabled && (!engineReady || row.recoveryRequired))" @click="toggleSchedule(row)">{{ row.enabled ? '暂停' : '启用' }}</el-button>
              <el-button v-hasPermi="['governance:flow:test']" link type="primary" :loading="rowBusy[row.id] === 'run'" :disabled="!!rowBusy[row.id] || !canRunSchedule(row, engineReady)" @click="runSchedule(row)">立即运行</el-button>
              <el-button v-if="row.activeRunId || row.lastRunId" link type="primary" @click="openRun(row.activeRunId || row.lastRunId)">运行详情</el-button>
              <el-button v-if="row.recoveryRequired" v-hasPermi="['governance:flow:test']" link type="warning" :loading="rowBusy[row.id] === 'recover'" :disabled="!!rowBusy[row.id]" @click="recoverSchedule(row)">核查恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-if="flowSchedules.length" v-model:page="schedulePage" v-model:limit="scheduleLimit" :total="flowSchedules.length" />
      </el-tab-pane>
      <el-tab-pane label="发布版本" name="releases">
        <template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">发布版本</span></span></template>
        <div class="governance-schedules__toolbar mb16">
          <el-text type="info">版本保存流程、输入批次与参数；后续修改画布不会改变已发布版本。</el-text>
          <el-button v-hasPermi="['governance:flow:edit']" type="primary" icon="Plus" :disabled="!engineReady || !selectedFlow || loading" @click="openRelease()">发布当前流程</el-button>
        </div>
        <el-table v-loading="loading" :data="pagedReleases" row-key="id" empty-text="暂无发布版本。先保存并测试画布，再发布当前流程。">
          <el-table-column label="版本" width="90"><template #default="{ row }">V{{ row.version }}</template></el-table-column>
          <el-table-column label="版本名称" prop="name" min-width="180" show-overflow-tooltip />
          <el-table-column label="输入方式" width="140"><template #default>固定 JSON 批次</template></el-table-column>
          <el-table-column label="发布时间" min-width="175"><template #default="{ row }">{{ formatScheduleTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="流程快照" prop="definitionHash" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showRelease(row)">查看版本</el-button><el-button v-hasPermi="['governance:flow:edit']" link type="primary" @click="openSchedule(null, row)">创建任务</el-button></template></el-table-column>
        </el-table>
        <pagination v-if="flowReleases.length" v-model:page="releasePage" v-model:limit="releaseLimit" :total="flowReleases.length" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="releaseOpen" title="发布固定批次版本" width="720px" append-to-body destroy-on-close :close-on-click-modal="!publishing" :close-on-press-escape="!publishing" :show-close="!publishing">
      <el-alert title="发布后不可修改该版本；计划每次都会重复处理这里保存的批次。请仅使用合成或脱敏数据。" type="info" :closable="false" class="mb16" />
      <el-alert v-if="releaseError" :title="releaseError" type="error" :closable="false" class="mb16" />
      <el-form ref="releaseFormRef" :model="releaseForm" :rules="releaseFormRules" label-position="top" :disabled="publishing">
        <el-form-item label="流程"><el-text>{{ flowName(releaseForm.flowId) }}</el-text></el-form-item>
        <el-form-item label="版本名称" prop="name"><el-input v-model="releaseForm.name" maxlength="80" show-word-limit placeholder="例如：每日协议文本校验 V1" /></el-form-item>
        <el-form-item label="固定 JSON 批次（对象或 0–100 条数组，最大 256 KB）" prop="inputText"><el-input v-model="releaseForm.inputText" type="textarea" :rows="8" aria-label="发布版本的固定 JSON 批次" spellcheck="false" /></el-form-item>
        <el-form-item label="参数覆盖（JSON 对象）"><el-input v-model="releaseForm.parameterText" type="textarea" :rows="3" aria-label="发布版本的参数覆盖" spellcheck="false" /><el-text type="info" size="small">{} 沿用已保存的画布配置；只保存明确填写的覆盖参数。</el-text></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="publishing" @click="releaseOpen = false">取消</el-button><el-button type="primary" :loading="publishing" @click="publishRelease">发布版本</el-button></template>
    </el-dialog>

    <el-dialog v-model="scheduleOpen" :title="cronEditorOpen ? '生成 Cron 表达式' : scheduleForm.id ? '编辑定时任务' : '新建定时任务'" width="720px" append-to-body destroy-on-close :close-on-click-modal="!savingSchedule" :close-on-press-escape="!savingSchedule" :show-close="!savingSchedule">
      <template v-if="cronEditorOpen">
        <el-alert title="生成器中的运行时间仅供参考；保存后以下方列表中服务端返回的时区与下次执行时间为准。" type="info" :closable="false" class="mb16" />
        <Crontab :expression="scheduleForm.cron" preserve-field-values @fill="fillCron" @hide="cronEditorOpen = false" />
      </template>
      <template v-else>
        <el-alert :title="scheduleForm.id ? '保存修改后任务自动暂停，需要再次显式启用。' : '保存后任务为暂停状态，需要在列表中显式启用。'" description="同一任务禁止并发，错过的触发不补跑。暂停只停止后续触发，已提交的运行仍会继续完成。" type="info" :closable="false" class="mb16" />
        <el-alert v-if="scheduleError" :title="scheduleError" type="error" :closable="false" class="mb16" />
        <el-form ref="scheduleFormRef" :model="scheduleForm" :rules="scheduleFormRules" label-position="top" :disabled="savingSchedule">
          <el-form-item label="任务名称" prop="name"><el-input v-model="scheduleForm.name" maxlength="80" show-word-limit placeholder="例如：每日固定批次检查" /></el-form-item>
          <el-form-item label="已发布版本" prop="releaseId"><el-select v-model="scheduleForm.releaseId" class="governance-schedules__full" filterable placeholder="选择该流程的发布版本"><el-option v-for="release in formReleases" :key="release.id" :value="String(release.id)" :label="`V${release.version} · ${release.name}`" /></el-select></el-form-item>
          <el-form-item label="Cron 表达式" prop="cron"><el-input v-model="scheduleForm.cron" placeholder="0 0 8 * * ?"><template #append><el-button @click="cronEditorOpen = true">生成表达式</el-button></template></el-input></el-form-item>
          <el-form-item label="执行时区" prop="timeZone"><el-select v-model="scheduleForm.timeZone" filterable allow-create default-first-option class="governance-schedules__full" placeholder="Asia/Shanghai"><el-option v-for="zone in timeZones" :key="zone" :value="zone" :label="zone" /></el-select><el-text type="info" size="small">服务端校验时区并计算下次执行时间，避免浏览器所在时区影响计划。</el-text></el-form-item>
        </el-form>
      </template>
      <template v-if="!cronEditorOpen" #footer><el-button :disabled="savingSchedule" @click="scheduleOpen = false">取消</el-button><el-button type="primary" :loading="savingSchedule" @click="saveSchedule">保存为暂停任务</el-button></template>
    </el-dialog>

    <el-drawer v-model="releaseDetailOpen" title="发布版本详情" size="70%" append-to-body destroy-on-close @closed="closeReleaseDetail">
      <div v-loading="releaseDetailLoading">
        <el-alert v-if="releaseDetailError" :title="releaseDetailError" type="error" :closable="false" class="mb16" />
        <template v-if="releaseDetail">
          <el-descriptions :column="1" border class="mb16"><el-descriptions-item label="版本">V{{ releaseDetail.release.version }} · {{ releaseDetail.release.name }}</el-descriptions-item><el-descriptions-item label="输入方式">固定 JSON 批次</el-descriptions-item><el-descriptions-item label="发布时间">{{ formatScheduleTime(releaseDetail.release.createdAt) }}</el-descriptions-item><el-descriptions-item label="流程快照"><span class="governance-schedules__hash">{{ releaseDetail.release.definitionHash }}</span></el-descriptions-item></el-descriptions>
          <el-alert title="这里显示已发布的固定输入与参数；修改画布不会改变此版本。以下为有界内容预览。" type="info" :closable="false" class="mb16" />
          <el-tabs class="motion-tabs">
            <el-tab-pane label="固定批次"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">固定批次</span></span></template><pre class="governance-schedules__output">{{ displayJson(releaseDetail.inputJson) }}</pre></el-tab-pane>
            <el-tab-pane label="参数覆盖"><template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">参数覆盖</span></span></template><pre class="governance-schedules__output">{{ displayJson(releaseDetail.parameters) }}</pre></el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="runOpen" title="任务运行详情" size="80%" append-to-body destroy-on-close @closed="closeRun">
      <div class="governance-schedules__toolbar mb16"><el-text type="info">实际执行记录，计数单位为 FlowFile</el-text><el-button icon="Refresh" :loading="runLoading" @click="refreshRun()">刷新结果</el-button></div>
      <el-alert v-if="runError" :title="runError" type="error" :closable="false" class="mb16" />
      <div v-loading="runLoading">
        <template v-if="run">
          <el-descriptions :column="2" border class="mb16">
            <el-descriptions-item label="运行状态"><el-tag :type="runState(run.status, run.cleanupConfirmed).type">{{ runState(run.status, run.cleanupConfirmed).label }}</el-tag></el-descriptions-item><el-descriptions-item label="资源清理">{{ run.cleanupConfirmed === true ? '已确认清理' : isRunActive(run) ? '运行中' : '尚未确认' }}</el-descriptions-item>
            <el-descriptions-item label="运行编号">{{ run.id }}</el-descriptions-item><el-descriptions-item label="提交时间">{{ formatScheduleTime(run.createdAt) }}</el-descriptions-item><el-descriptions-item v-if="run.definitionHash" label="执行快照" :span="2"><span class="governance-schedules__hash">{{ run.definitionHash }}</span></el-descriptions-item>
          </el-descriptions>
          <el-alert v-if="run.error" :title="run.error" type="error" :closable="false" class="mb16" />
          <el-alert v-if="run.status === 'CLEANUP_REQUIRED' || (run.cleanupConfirmed === false && !isRunActive(run))" title="本次运行的资源清理尚未确认。请返回任务列表检查恢复状态，不能视为完整执行通过。" type="warning" :closable="false" class="mb16" />
          <el-tabs class="motion-tabs">
            <el-tab-pane label="步骤与分支">
              <template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">步骤与分支</span></span></template>
              <el-table :data="run.steps || []" row-key="id" empty-text="尚未返回步骤记录"><el-table-column label="步骤" prop="name" min-width="150" show-overflow-tooltip /><el-table-column label="状态" width="160"><template #default="{ row }"><el-tag :type="runState(row.status).type">{{ runState(row.status).label }}</el-tag></template></el-table-column><el-table-column label="输入 / 输出 FlowFile" width="180"><template #default="{ row }">{{ optionalCount(row.inputCount) }} / {{ optionalCount(row.outputCount) }}</template></el-table-column><el-table-column label="操作" width="130"><template #default="{ row }"><el-button link type="primary" @click="openStep(row)">输入 / 输出</el-button></template></el-table-column></el-table>
            </el-tab-pane>
            <el-tab-pane label="结果输出"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">结果输出</span></span></template><el-text type="info">服务端保留的有界采样，可能已截断，不代表完整产物。</el-text><el-empty v-if="!run.output?.length" :description="run.status === 'EMPTY' ? '引擎已确认空批次，未产生输出数据' : '引擎未返回输出预览'" /><pre v-for="(item, index) in run.output || []" :key="index" class="governance-schedules__output">{{ displayJson(item) }}</pre></el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
    <StepDetailDrawer v-model="stepOpen" :step="selectedStep" />
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue'
import Crontab from '@/components/Crontab/index.vue'
import { getGovernanceTestRun } from '@/api/governance'
import { changeGovernanceScheduleState, createGovernanceRelease, createGovernanceSchedule, getGovernanceRelease, listGovernanceReleases, listGovernanceSchedules, recoverGovernanceSchedule, runGovernanceSchedule, updateGovernanceSchedule } from '@/api/governance/schedules'
import { displayJson, errorMessage, isRunActive, optionalCount, runState } from '../workspaceRules'
import { canRunSchedule, formatScheduleTime, releaseRequest, scheduleRequest, scheduleState, shouldPollSchedules } from '../scheduleRules'
import StepDetailDrawer from './StepDetailDrawer.vue'

const props = defineProps({ flows: { type: Array, default: () => [] }, selectedFlowId: { type: String, default: '' }, engineReady: Boolean })
const emit = defineEmits(['select-flow'])
const { proxy } = getCurrentInstance()
const releases = ref([])
const schedules = ref([])
const activeTab = ref('schedules')
const loading = ref(false)
const requestError = ref('')
const actionError = ref('')
const pollNotice = ref('')
const rowBusy = reactive({})
const releasePage = ref(1)
const releaseLimit = ref(20)
const schedulePage = ref(1)
const scheduleLimit = ref(20)
const selectedFlow = computed(() => props.flows.find((item) => String(item.id) === props.selectedFlowId))
const flowReleases = computed(() => releases.value.filter((item) => String(item.flowId) === props.selectedFlowId))
const flowSchedules = computed(() => schedules.value.filter((item) => String(item.flowId) === props.selectedFlowId))
const pagedReleases = computed(() => flowReleases.value.slice((releasePage.value - 1) * releaseLimit.value, releasePage.value * releaseLimit.value))
const pagedSchedules = computed(() => flowSchedules.value.slice((schedulePage.value - 1) * scheduleLimit.value, schedulePage.value * scheduleLimit.value))
const releaseOpen = ref(false)
const publishing = ref(false)
const releaseError = ref('')
const releaseFormRef = ref()
const releaseForm = reactive({ flowId: '', name: '', inputText: '{\n  "message": "样本消息"\n}', parameterText: '{}' })
const releaseFormRules = { name: [{ required: true, message: '请填写版本名称', trigger: 'blur' }], inputText: [{ required: true, message: '请填写固定 JSON 批次', trigger: 'blur' }] }
const scheduleOpen = ref(false)
const savingSchedule = ref(false)
const scheduleError = ref('')
const scheduleFormRef = ref()
const scheduleForm = reactive({ id: '', revision: undefined, flowId: '', name: '', releaseId: '', cron: '0 0 8 * * ?', timeZone: 'Asia/Shanghai' })
const formReleases = computed(() => releases.value.filter((item) => String(item.flowId) === scheduleForm.flowId))
const scheduleFormRules = Object.fromEntries(['name', 'releaseId', 'cron', 'timeZone'].map((field) => [field, [{ required: true, message: '请填写此项', trigger: 'blur' }]]))
const timeZones = ['Asia/Shanghai', 'UTC', 'Asia/Tokyo', 'Europe/London', 'America/New_York']
const cronEditorOpen = ref(false)
const releaseDetailOpen = ref(false)
const releaseDetailLoading = ref(false)
const releaseDetailError = ref('')
const releaseDetail = ref(null)
const runOpen = ref(false)
const runLoading = ref(false)
const runError = ref('')
const runId = ref('')
const run = ref(null)
const stepOpen = ref(false)
const selectedStepId = ref('')
const selectedStep = computed(() => run.value?.steps?.find((item) => item.id === selectedStepId.value) || null)
let suspended = false
let pollTimer
let pollAttempts = 0
let listSequence = 0
let runSequence = 0
let releaseSequence = 0

function flowName(id) { return props.flows.find((item) => String(item.id) === String(id))?.name || id }
function releaseLabel(id) { const item = releases.value.find((release) => release.id === id); return item ? `V${item.version} · ${item.name}` : '版本信息未返回' }
function stopPolling() { clearTimeout(pollTimer) }
function schedulePoll() {
  stopPolling()
  if (suspended || requestError.value || runError.value) return
  const active = shouldPollSchedules(flowSchedules.value, runOpen.value ? run.value : null, 0)
  if (!active) return
  if (!shouldPollSchedules(flowSchedules.value, runOpen.value ? run.value : null, pollAttempts)) { pollNotice.value = '自动刷新已达到本轮上限。'; return }
  pollTimer = setTimeout(async () => {
    pollAttempts += 1
    await refreshAll(false)
    if (runOpen.value && isRunActive(run.value)) await refreshRun(false)
    schedulePoll()
  }, 5000)
}

async function refreshAll(manual = true) {
  if (suspended) return
  if (manual) { pollAttempts = 0; pollNotice.value = ''; actionError.value = ''; stopPolling() }
  const sequence = ++listSequence
  loading.value = manual
  try {
    const results = await Promise.allSettled([listGovernanceReleases(), listGovernanceSchedules()])
    if (suspended || sequence !== listSequence) return
    if (results[0].status === 'fulfilled') releases.value = Array.isArray(results[0].value.data) ? results[0].value.data : []
    if (results[1].status === 'fulfilled') schedules.value = Array.isArray(results[1].value.data) ? results[1].value.data : []
    const failure = results.find((item) => item.status === 'rejected')
    if (failure) throw failure.reason
    requestError.value = ''
    if (manual) schedulePoll()
  } catch (error) {
    if (sequence === listSequence && !suspended) { requestError.value = errorMessage(error); pollNotice.value = '自动刷新已暂停。' }
  } finally { if (sequence === listSequence) loading.value = false }
}

function openRelease() {
  if (!selectedFlow.value || publishing.value) return
  Object.assign(releaseForm, { flowId: props.selectedFlowId, name: `${selectedFlow.value.name} V${flowReleases.value.length + 1}`, inputText: '{\n  "message": "样本消息"\n}', parameterText: '{}' })
  releaseError.value = ''
  releaseOpen.value = true
}
async function publishRelease() {
  if (publishing.value || !await releaseFormRef.value?.validate().catch(() => false)) return
  let data
  try { data = releaseRequest(releaseForm.flowId, releaseForm.name, releaseForm.inputText, releaseForm.parameterText) }
  catch (error) { releaseError.value = errorMessage(error); return }
  publishing.value = true
  releaseError.value = ''
  try {
    await createGovernanceRelease(data)
    releaseOpen.value = false
    proxy.$modal.msgSuccess('已发布固定批次版本')
    await refreshAll()
  } catch (error) { releaseError.value = errorMessage(error) }
  finally { publishing.value = false }
}
function openSchedule(item = null, release = null) {
  if (savingSchedule.value) return
  Object.assign(scheduleForm, { id: item?.id || '', revision: item?.revision, flowId: String(item?.flowId || release?.flowId || props.selectedFlowId), name: item?.name || '', releaseId: String(item?.releaseId || release?.id || flowReleases.value[0]?.id || ''), cron: item?.cron || '0 0 8 * * ?', timeZone: item?.timeZone || 'Asia/Shanghai' })
  scheduleError.value = ''
  cronEditorOpen.value = false
  scheduleOpen.value = true
}
function fillCron(value) { scheduleForm.cron = value; cronEditorOpen.value = false }
async function saveSchedule() {
  if (savingSchedule.value || !await scheduleFormRef.value?.validate().catch(() => false)) return
  let data
  try { data = scheduleRequest(scheduleForm) }
  catch (error) { scheduleError.value = errorMessage(error); return }
  savingSchedule.value = true
  scheduleError.value = ''
  try {
    if (scheduleForm.id) await updateGovernanceSchedule(scheduleForm.id, data)
    else await createGovernanceSchedule(data)
    scheduleOpen.value = false
    activeTab.value = 'schedules'
    proxy.$modal.msgSuccess('任务已保存，请确认计划后显式启用')
    await refreshAll()
  } catch (error) {
    scheduleError.value = `${errorMessage(error)} 修改未确认保存；若提示版本冲突，请关闭弹窗、刷新后重新编辑。`
    await refreshAll()
  } finally { savingSchedule.value = false }
}

async function toggleSchedule(item) {
  if (rowBusy[item.id]) return
  actionError.value = ''
  rowBusy[item.id] = 'state'
  try {
    if (!item.enabled) {
      await proxy.$modal.confirm(`启用“${item.name}”后，将按 ${item.timeZone} 的计划重复执行已发布的同一固定批次，是否启用？`)
    }
    await changeGovernanceScheduleState(item.id, { enabled: !item.enabled, revision: item.revision })
    await refreshAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') { await refreshAll(); actionError.value = errorMessage(error) }
  } finally { delete rowBusy[item.id] }
}
async function runSchedule(item) {
  if (rowBusy[item.id] || !canRunSchedule(item, props.engineReady)) return
  actionError.value = ''
  rowBusy[item.id] = 'run'
  try {
    const response = await runGovernanceSchedule(item.id)
    await refreshAll()
    if (response.data?.id) await openRun(response.data.id)
    else actionError.value = '服务端未返回运行编号，请刷新任务确认提交结果，勿重复点击运行。'
  } catch (error) { await refreshAll(); actionError.value = errorMessage(error) }
  finally { delete rowBusy[item.id] }
}
async function recoverSchedule(item) {
  if (rowBusy[item.id]) return
  actionError.value = ''
  rowBusy[item.id] = 'recover'
  try {
    await proxy.$modal.confirm(`“${item.name}”需要恢复：${item.lastError || '上次执行或资源清理尚未确认'}。继续将由服务端核查真实运行和清理情况；恢复后仍需显式启用。是否核查恢复？`)
    await recoverGovernanceSchedule(item.id)
    await refreshAll()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') { await refreshAll(); actionError.value = errorMessage(error) }
  } finally { delete rowBusy[item.id] }
}

async function showRelease(item) {
  const sequence = ++releaseSequence
  releaseDetail.value = null
  releaseDetailError.value = ''
  releaseDetailOpen.value = true
  releaseDetailLoading.value = true
  try {
    const response = await getGovernanceRelease(item.id)
    if (!suspended && sequence === releaseSequence && releaseDetailOpen.value) releaseDetail.value = response.data
  } catch (error) { if (sequence === releaseSequence) releaseDetailError.value = errorMessage(error) }
  finally { if (sequence === releaseSequence) releaseDetailLoading.value = false }
}
function closeReleaseDetail() { if (!releaseDetailOpen.value) { releaseSequence += 1; releaseDetail.value = null; releaseDetailLoading.value = false } }
async function openRun(id) {
  runSequence += 1
  run.value = null
  runId.value = id
  runOpen.value = true
  runError.value = ''
  await refreshRun()
}
async function refreshRun(manual = true) {
  if (suspended || !runId.value) return
  const sequence = ++runSequence
  const id = runId.value
  runLoading.value = manual
  if (manual) { pollAttempts = 0; pollNotice.value = '' }
  try {
    const response = await getGovernanceTestRun(id)
    if (suspended || sequence !== runSequence || id !== runId.value) return
    run.value = response.data
    runError.value = ''
    if (manual) schedulePoll()
  } catch (error) {
    if (!suspended && sequence === runSequence) { runError.value = errorMessage(error); pollNotice.value = '运行详情自动刷新已暂停。' }
  } finally { if (sequence === runSequence) runLoading.value = false }
}
function closeRun() { if (runOpen.value) return; runSequence += 1; runId.value = ''; run.value = null; runError.value = ''; stepOpen.value = false; schedulePoll() }
function openStep(step) { selectedStepId.value = step.id; stepOpen.value = true }
function suspend() { suspended = true; listSequence += 1; runSequence += 1; releaseDetailOpen.value = false; closeReleaseDetail(); stopPolling() }
watch(() => props.selectedFlowId, () => { releasePage.value = 1; schedulePage.value = 1; runOpen.value = false; closeRun(); refreshAll() }, { immediate: true })
onActivated(() => { if (suspended) { suspended = false; refreshAll(); if (runOpen.value) refreshRun() } })
onDeactivated(suspend)
onBeforeUnmount(suspend)
</script>

<style scoped>
.governance-schedules { min-width: 0; }
.governance-schedules__toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--el-font-size-base); }
.governance-schedules__filter :deep(.el-form-item) { margin-bottom: 0; }
.governance-schedules__flow { width: 300px; }
.governance-schedules__full { width: 100%; }
.governance-schedules__hint { color: var(--app-muted); font-size: var(--el-font-size-small); line-height: 1.7; }
.governance-schedules__hash { overflow-wrap: anywhere; }
.governance-schedules__output { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--app-text); background: var(--surface-subtle); padding: var(--el-component-size-small); border-radius: var(--el-border-radius-base); max-height: 50vh; overflow: auto; }
@media (max-width: 768px) { .governance-schedules__flow { width: 200px; } }
</style>
