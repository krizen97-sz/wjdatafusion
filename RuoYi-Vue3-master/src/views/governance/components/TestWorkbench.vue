<template>
  <section>
    <el-alert
      title="隔离样本测试"
      description="测试会读取已保存的流程，检查支持范围后创建独立测试副本。外部连接器、脚本或未支持的配置不会被执行。请仅输入合成或脱敏数据。"
      type="info" show-icon :closable="false" class="mb16"
    />
    <el-empty v-if="!flow" description="请先选择一个流程" />
    <template v-else>
      <div class="governance-test-heading mb16">
        <div>
          <el-text tag="strong">{{ flow.name }}</el-text>
          <p class="governance-help">{{ template?.description || '在设计器保存流程后，提交样本测试以校验当前节点和配置。' }}</p>
        </div>
        <el-button icon="Refresh" :loading="historyLoading" @click="loadHistory()">刷新运行记录</el-button>
      </div>

      <el-alert v-if="!engineReady" title="执行引擎当前不可用，请先在工作台刷新连接状态。" type="warning" :closable="false" class="mb16" />
      <el-alert v-if="template && !templateUsable" :title="`${template.name}：${availabilityState(template.availability).label}`" description="此模板尚不能执行隔离测试。已实现范围请查看组件目录。" type="warning" :closable="false" class="mb16" />
      <el-alert v-if="requestError" :title="requestError" type="error" :closable="false" class="mb16" />
      <el-alert v-if="pendingHistoryRun && !activeRun" title="此流程还有未结束的测试。请在运行记录中打开它，确认结果后再发起新测试。" type="warning" :closable="false" class="mb16" />

      <el-form label-position="top" :disabled="submitting || activeRun || !!pendingHistoryRun">
        <el-row :gutter="20">
          <el-col :xs="24" :lg="15">
            <el-form-item label="输入样本（JSON 对象或 0–100 条数组，最大 256 KB）" :error="inputError">
              <el-input v-model="inputText" type="textarea" :rows="10" placeholder='例如：{"message":"样本消息"}' aria-label="输入 JSON 样本" spellcheck="false" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :lg="9">
            <el-form-item label="可选参数覆盖（JSON 对象）">
              <el-input v-model="parameterText" type="textarea" :rows="10" placeholder="{}：沿用画布已保存配置" aria-label="可选参数覆盖 JSON 对象" spellcheck="false" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <p class="governance-help">可选参数留空或填写 {} 时沿用画布配置；仅显式填写的参数会覆盖本次测试。样本和参数草稿按流程保留在当前工作台内存中。</p>
      <el-descriptions v-if="parameterFields.length" :column="1" size="small" class="mb16">
        <el-descriptions-item v-for="field in parameterFields" :key="field.name" :label="field.name">{{ field.description || field.type || '模板参数' }}<span v-if="field.default !== undefined">；模板参考默认值（不自动提交）：{{ displayJson(field.default) || '空字符串' }}</span></el-descriptions-item>
      </el-descriptions>
      <div class="governance-test-actions mb16">
        <el-button v-hasPermi="['governance:flow:test']" type="primary" icon="VideoPlay" :loading="submitting" :disabled="!canStart" @click="startTest">运行样本测试</el-button>
        <el-button v-if="activeRun" v-hasPermi="['governance:flow:test']" type="danger" plain :loading="cancelling" @click="cancelTest">取消本次测试</el-button>
        <el-text type="info">运行结果对应提交时的样本和流程；编辑画布后请重新测试。</el-text>
      </div>

      <el-form :inline="true">
        <el-form-item label="运行记录">
          <el-select v-model="selectedRunId" class="governance-run-select" placeholder="选择运行记录" clearable :loading="historyLoading" @change="selectRun">
            <el-option v-for="item in history" :key="item.id" :value="String(item.id)" :label="`${runState(item.status, item.cleanupConfirmed).label} · ${item.createdAt || item.id}`" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="run">
          <el-button icon="Refresh" :loading="detailLoading" @click="refreshRun">刷新结果</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="pollError" :title="pollError" description="自动刷新已暂停，本次运行状态尚未确认。请点击“刷新结果”继续核查。" type="warning" :closable="false" class="mb16" />
      <div v-if="run" v-loading="detailLoading" aria-live="polite">
        <el-descriptions :column="2" border class="mb16">
          <el-descriptions-item label="运行状态"><el-tag :type="runState(run.status, run.cleanupConfirmed).type">{{ runState(run.status, run.cleanupConfirmed).label }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="运行编号">{{ run.id }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ run.createdAt || '未返回' }}</el-descriptions-item>
          <el-descriptions-item label="测试资源清理">{{ cleanupLabel }}</el-descriptions-item>
          <el-descriptions-item v-if="run.definitionHash" label="提交快照"><span class="governance-snapshot">{{ run.definitionHash }}</span></el-descriptions-item>
          <el-descriptions-item v-if="snapshotTime" label="快照时间">{{ snapshotTime }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="run.error" :title="run.error" type="error" :closable="false" class="mb16" />
        <el-alert v-if="run.status === 'CLEANUP_REQUIRED'" title="测试资源清理未确认，需核查引擎中的测试副本。此次运行不能视为完整验收通过。" type="error" :closable="false" class="mb16" />
        <el-tabs v-model="resultTab" class="motion-tabs">
          <el-tab-pane label="步骤与分支" name="steps">
            <template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">步骤与分支</span></span></template>
            <el-table :data="run.steps || []" row-key="id" empty-text="尚未返回步骤记录">
              <el-table-column label="步骤" prop="name" min-width="160" show-overflow-tooltip />
              <el-table-column label="组件" prop="type" min-width="150" show-overflow-tooltip />
              <el-table-column label="状态" width="180"><template #default="{ row }"><el-tag :type="runState(row.status).type">{{ runState(row.status).label }}</el-tag></template></el-table-column>
              <el-table-column label="输入 FlowFile" width="125"><template #default="{ row }">{{ optionalCount(row.inputCount) }}</template></el-table-column>
              <el-table-column label="输出 FlowFile" width="125"><template #default="{ row }">{{ optionalCount(row.outputCount) }}</template></el-table-column>
              <el-table-column label="运行消息" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ (row.messages || []).map((item) => typeof item === 'string' ? item : JSON.stringify(item)).join('；') || '未返回' }}</template></el-table-column>
              <el-table-column label="操作" width="125" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openStep(row)">输入 / 输出</el-button></template></el-table-column>
            </el-table>
            <p class="governance-help">以上计数来自引擎；一个 FlowFile 可能包含多条业务记录。点击步骤查看有界采样预览，内容可能已截断。</p>
          </el-tab-pane>
          <el-tab-pane label="结果输出" name="output">
            <template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">结果输出</span></span></template>
            <p class="governance-help">结果为服务端保留的有界采样预览，可能已截断，不代表完整产物。</p>
            <el-empty v-if="!run.output?.length" :description="run.status === 'EMPTY' ? '引擎已确认空批次，未产生输出数据' : '引擎未返回结果预览'" />
            <pre v-for="(item, index) in run.output || []" :key="index" class="governance-output">{{ displayJson(item) }}</pre>
          </el-tab-pane>
        </el-tabs>
      </div>
      <el-empty v-else-if="!historyLoading && !detailLoading" description="提交测试后，可在此查看真实步骤状态和结果" />
      <step-detail-drawer v-model="stepDrawerOpen" :step="selectedStep" />
    </template>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onDeactivated, onActivated, ref, watch } from 'vue'
import { cancelGovernanceTestRun, getGovernanceTestRun, listGovernanceTestRuns, startGovernanceTest } from '@/api/governance'
import { availabilityState, createFlowDraftStore, displayJson, errorMessage, isRunActive, optionalCount, parseTestRequest, runState } from '../workspaceRules'
import StepDetailDrawer from './StepDetailDrawer.vue'

const props = defineProps({ flow: { type: Object, default: null }, templates: { type: Array, default: () => [] }, engineReady: Boolean })
const emit = defineEmits(['updated'])
const { proxy } = getCurrentInstance()
const template = computed(() => props.templates.find((item) => item.id === props.flow?.templateId))
const templateUsable = computed(() => !template.value || availabilityState(template.value.availability).usable === true)
const parameterFields = computed(() => Object.entries(template.value?.parametersSchema?.properties || {}).map(([name, field]) => ({ name, ...field })))
const inputText = ref('{\n  "message": "样本消息"\n}')
const parameterText = ref('{}')
const inputError = ref('')
const requestError = ref('')
const pollError = ref('')
const submitting = ref(false)
const cancelling = ref(false)
const historyLoading = ref(false)
const detailLoading = ref(false)
const history = ref([])
const selectedRunId = ref('')
const run = ref(null)
const resultTab = ref('steps')
const stepDrawerOpen = ref(false)
const selectedStepId = ref('')
const selectedStep = computed(() => run.value?.steps?.find((item) => item.id === selectedStepId.value) || null)
const activeRun = computed(() => isRunActive(run.value))
const pendingHistoryRun = computed(() => history.value.find((item) => isRunActive(item)))
const canStart = computed(() => props.engineReady && props.flow?.engineId && templateUsable.value && !submitting.value && !activeRun.value && !pendingHistoryRun.value && !historyLoading.value && run.value?.status !== 'CLEANUP_REQUIRED')
const cleanupLabel = computed(() => run.value?.cleanupConfirmed === true ? '已确认清理' : activeRun.value ? '运行中' : '尚未确认')
const snapshotTime = computed(() => run.value?.frozenAt || run.value?.definitionCapturedAt || '')
const draftStore = createFlowDraftStore()
let pollTimer
let generation = 0
let detailSequence = 0
let historySequence = 0
let suspended = false

function stopPolling() { clearTimeout(pollTimer) }
function schedulePoll() {
  stopPolling()
  if (!suspended && activeRun.value && selectedRunId.value) pollTimer = setTimeout(() => refreshRun(false), 2500)
}

async function loadHistory(selectLatest = false) {
  if (!props.flow?.id) return
  const currentGeneration = generation
  const sequence = ++historySequence
  const flowId = props.flow.id
  historyLoading.value = true
  try {
    const response = await listGovernanceTestRuns(flowId)
    if (currentGeneration !== generation || sequence !== historySequence) return
    history.value = response.data || []
    if (selectLatest && history.value.length) {
      selectedRunId.value = String((history.value.find((item) => isRunActive(item)) || history.value[0]).id)
      await refreshRun()
    }
  } catch (error) {
    if (currentGeneration === generation) requestError.value = errorMessage(error, '运行记录加载失败。')
  } finally {
    if (currentGeneration === generation && sequence === historySequence) historyLoading.value = false
  }
}

async function refreshRun(showLoading = true) {
  stopPolling()
  const id = selectedRunId.value
  if (!id) return
  const currentGeneration = generation
  const sequence = ++detailSequence
  if (showLoading) detailLoading.value = true
  try {
    const response = await getGovernanceTestRun(id)
    if (currentGeneration !== generation || sequence !== detailSequence || id !== selectedRunId.value) return
    run.value = response.data
    pollError.value = ''
    history.value = history.value.map((item) => String(item.id) === id ? { ...item, ...run.value } : item)
    schedulePoll()
  } catch (error) {
    if (currentGeneration === generation && sequence === detailSequence) pollError.value = errorMessage(error, '运行状态暂时无法获取。')
  } finally {
    if (currentGeneration === generation && sequence === detailSequence) detailLoading.value = false
  }
}

async function selectRun() {
  stopPolling()
  ++detailSequence
  run.value = null
  pollError.value = ''
  stepDrawerOpen.value = false
  if (selectedRunId.value) await refreshRun()
}

async function startTest() {
  if (!canStart.value) return
  inputError.value = ''
  requestError.value = ''
  let data
  try { data = parseTestRequest(inputText.value, parameterText.value) } catch (error) {
    inputError.value = error.message
    return
  }
  const currentGeneration = generation
  const flowId = props.flow.id
  submitting.value = true
  try {
    const response = await startGovernanceTest(flowId, data)
    if (currentGeneration !== generation) return
    run.value = response.data
    selectedRunId.value = String(run.value.id)
    resultTab.value = 'steps'
    history.value = [run.value, ...history.value.filter((item) => String(item.id) !== selectedRunId.value)]
    pollError.value = ''
    emit('updated')
    await refreshRun()
  } catch (error) {
    if (currentGeneration === generation) requestError.value = errorMessage(error, '测试提交失败。')
  } finally {
    if (currentGeneration === generation) submitting.value = false
  }
}

async function cancelTest() {
  if (!activeRun.value || cancelling.value) return
  const id = run.value.id
  const currentGeneration = generation
  try { await proxy.$modal.confirm('确认取消本次隔离测试？系统将停止测试副本并返回实际清理状态。') } catch { return }
  if (currentGeneration !== generation || id !== run.value?.id) return
  cancelling.value = true
  try {
    await cancelGovernanceTestRun(id)
    if (currentGeneration === generation && String(id) === selectedRunId.value) await refreshRun()
  } catch (error) {
    if (currentGeneration === generation) requestError.value = errorMessage(error, '取消请求未确认，请刷新运行状态。')
  } finally {
    if (currentGeneration === generation) cancelling.value = false
  }
}

function openStep(step) { selectedStepId.value = step.id; stepDrawerOpen.value = true }

watch(() => props.flow?.id, (flowId, previousFlowId) => {
  draftStore.write(previousFlowId, { inputText: inputText.value, parameterText: parameterText.value })
  ++generation
  stopPolling()
  selectedRunId.value = ''
  run.value = null
  history.value = []
  inputError.value = requestError.value = pollError.value = ''
  submitting.value = cancelling.value = detailLoading.value = historyLoading.value = false
  stepDrawerOpen.value = false
  const draft = draftStore.read(flowId, template.value)
  inputText.value = draft.inputText
  parameterText.value = draft.parameterText
  if (flowId) loadHistory(true)
}, { immediate: true })

onDeactivated(() => { suspended = true; stopPolling() })
onActivated(() => { suspended = false; if (selectedRunId.value) refreshRun(false) })
onBeforeUnmount(() => { ++generation; suspended = true; stopPolling() })
</script>

<style scoped>
.governance-test-heading, .governance-test-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--el-font-size-base); }
.governance-test-actions { justify-content: flex-start; }
.governance-help { color: var(--el-text-color-secondary); font-size: var(--el-font-size-small); line-height: 1.6; }
.governance-snapshot { overflow-wrap: anywhere; }
.governance-run-select { width: min(100%, 520px); min-width: 240px; }
.governance-output { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--app-text); background: var(--surface-subtle); padding: var(--el-component-size-small); border-radius: var(--el-border-radius-base); max-height: 50vh; overflow: auto; }
</style>
