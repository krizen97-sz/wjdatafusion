<template>
  <div class="app-container governance-workspace" :class="{ 'is-designing': activeTab === 'designer' }">
    <div class="governance-project-bar mb16">
      <el-form :inline="true" class="governance-project-form">
        <el-form-item label="治理项目">
          <el-select :model-value="projectId" filterable placeholder="请选择项目" class="governance-project-select" :loading="workspaceLoading" @change="changeProject">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="String(project.id)" />
          </el-select>
        </el-form-item>
        <el-form-item><el-button v-hasPermi="['governance:project:add']" icon="Plus" :disabled="!engineReady" @click="openProjectDialog">新建项目</el-button></el-form-item>
      </el-form>
      <div class="governance-engine-state" role="status">
        <el-tag :type="engineState.type">{{ engineState.label }}</el-tag>
        <el-text v-if="overview?.engine?.version" type="info">NiFi {{ overview.engine.version }}</el-text>
        <el-button icon="Refresh" :loading="workspaceLoading" @click="loadWorkspace">刷新状态</el-button>
      </div>
    </div>

    <el-alert v-if="workspaceError" :title="workspaceError" type="error" :closable="false" show-icon class="mb16" />
    <el-alert v-if="!engineReady && overview" :title="overview.engine?.message || '引擎暂不可用，请检查服务配置后刷新。'" type="warning" :closable="false" show-icon class="mb16" />
    <p v-if="currentProject?.description && activeTab !== 'designer'" class="governance-description">{{ currentProject.description }}</p>

    <el-tabs v-model="activeTab" class="motion-tabs" :before-leave="beforeTabChange">
      <el-tab-pane label="流程" name="flows">
        <template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">流程</span></span></template>
        <el-form v-show="showSearch" ref="queryRef" :inline="true" :model="query">
          <el-form-item label="流程名称" prop="name"><el-input v-model="query.name" placeholder="搜索流程名称" clearable class="governance-query-input" @keyup.enter="searchFlows" /></el-form-item>
          <el-form-item><el-button type="primary" icon="Search" @click="searchFlows">搜索</el-button><el-button icon="Refresh" @click="resetFlowQuery">重置</el-button></el-form-item>
        </el-form>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5"><el-button v-hasPermi="['governance:flow:add']" type="primary" plain icon="Plus" :disabled="!projectId || !engineReady" @click="openFlowDialog">新建流程</el-button></el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="loadFlows" />
        </el-row>
        <el-alert v-if="flowError" :title="flowError" type="error" :closable="false" class="mb16" />
        <el-table v-loading="flowsLoading" :data="visibleFlows" row-key="id" :empty-text="projectId ? '此项目还没有流程，可新建空白流程或从模板开始' : '请先新建或选择一个治理项目'">
          <el-table-column label="流程名称" prop="name" min-width="180" show-overflow-tooltip />
          <el-table-column label="说明" prop="description" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.description || templateFor(row)?.description || '未填写说明' }}</template></el-table-column>
          <el-table-column label="起始模板" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ templateFor(row)?.name || '空白流程' }}</template></el-table-column>
          <el-table-column label="设计器" width="130"><template #default="{ row }"><el-tag :type="row.engineId ? 'success' : 'info'">{{ row.engineId ? '已创建' : '尚未关联' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" icon="Edit" :disabled="!designerFor(row) || !engineReady" @click="openDesigner(row)">打开设计器</el-button>
              <el-button link type="primary" icon="VideoPlay" @click="selectFlowForTest(row)">样本测试</el-button>
            </template>
          </el-table-column>
        </el-table>
        <pagination v-show="filteredFlows.length > 0" :total="filteredFlows.length" v-model:page="query.pageNum" v-model:limit="query.pageSize" />
        <p class="governance-description">在平台内拖拽组件、配置节点、连接分支，并通过样本查看每个节点的真实输出。</p>
      </el-tab-pane>

      <el-tab-pane label="流程设计" name="designer" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="component" class="motion-control-label__icon" /><span class="motion-control-label__text">流程设计</span></span></template>
        <flow-designer v-if="activeTab === 'designer'" ref="designerRef" :flow="selectedFlow" :flows="flows" :templates="templates" :engine-ready="engineReady" @select-flow="selectedFlowId = String($event)" @updated="refreshOverview" />
      </el-tab-pane>

      <el-tab-pane label="样本测试" name="tests" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">样本测试</span></span></template>
        <el-form :inline="true">
          <el-form-item label="测试流程">
            <el-select v-model="selectedFlowId" filterable placeholder="请选择流程" class="governance-project-select" :loading="flowsLoading">
              <el-option v-for="flow in flows" :key="flow.id" :label="flow.name" :value="String(flow.id)" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button icon="Edit" :disabled="!selectedFlow || !designerFor(selectedFlow) || !engineReady" @click="openDesigner(selectedFlow)">打开设计器</el-button></el-form-item>
          <el-form-item><el-button icon="Refresh" :loading="flowsLoading" @click="loadFlows">刷新已保存流程</el-button></el-form-item>
        </el-form>
        <test-workbench :flow="selectedFlow" :templates="templates" :engine-ready="engineReady" @updated="refreshOverview" />
      </el-tab-pane>

      <el-tab-pane label="组件目录" name="catalog" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="component" class="motion-control-label__icon" /><span class="motion-control-label__text">组件目录</span></span></template>
        <el-alert title="组件可用性以服务端返回为准。待适配组件仅列出建设范围，不可当作已完成连接或业务验收。" type="info" :closable="false" class="mb16" />
        <el-form :inline="true">
          <el-form-item label="分类"><el-select v-model="catalogFilter" clearable placeholder="全部分类" class="governance-query-input"><el-option v-for="category in catalogCategories" :key="category" :label="catalogCategory(category)" :value="category" /></el-select></el-form-item>
          <el-form-item label="组件名称"><el-input v-model="catalogKeyword" clearable placeholder="搜索组件名称或说明" class="governance-query-input" /></el-form-item>
        </el-form>
        <el-table v-loading="workspaceLoading" :data="filteredCatalog" row-key="id" empty-text="没有符合条件的组件">
          <el-table-column label="组件" prop="name" min-width="180" show-overflow-tooltip />
          <el-table-column label="分类" width="140"><template #default="{ row }">{{ catalogCategory(row.category) }}</template></el-table-column>
          <el-table-column label="可用性" width="150"><template #default="{ row }"><el-tag :type="availabilityState(row.availability).type">{{ availabilityState(row.availability).label }}</el-tag></template></el-table-column>
          <el-table-column label="用途与限制" prop="description" min-width="300" show-overflow-tooltip />
          <el-table-column label="输入 / 输出" min-width="180"><template #default="{ row }">{{ (row.inputKinds || []).join('、') || '未声明' }} / {{ (row.outputKinds || []).join('、') || '未声明' }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="发布与定时任务" name="schedules" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="time" class="motion-control-label__icon" /><span class="motion-control-label__text">发布与定时任务</span></span></template>
        <schedule-workbench v-if="activeTab === 'schedules'" :flows="flows" :selected-flow-id="selectedFlowId" :engine-ready="engineReady" @select-flow="selectedFlowId = String($event)" />
      </el-tab-pane>
      <el-tab-pane label="数据连接" name="connections" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="server" class="motion-control-label__icon" /><span class="motion-control-label__text">数据连接</span></span></template>
        <connection-workbench v-if="activeTab === 'connections'" :engine-ready="engineReady" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="projectDialogOpen" title="新建治理项目" width="500px" append-to-body :close-on-click-modal="!projectSubmitting" :show-close="!projectSubmitting">
      <el-form ref="projectFormRef" :model="projectForm" :rules="projectRules" label-width="80px" :disabled="projectSubmitting">
        <el-form-item label="项目名称" prop="name"><el-input v-model="projectForm.name" maxlength="80" show-word-limit placeholder="例如：数据交换测试" /></el-form-item>
        <el-form-item label="项目说明" prop="description"><el-input v-model="projectForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <el-alert v-if="projectSubmitError" :title="projectSubmitError" type="error" :closable="false" />
      <template #footer><el-button :disabled="projectSubmitting" @click="projectDialogOpen = false">取消</el-button><el-button type="primary" :loading="projectSubmitting" @click="submitProject">创建项目</el-button></template>
    </el-dialog>

    <el-dialog v-model="flowDialogOpen" title="新建流程" width="600px" append-to-body :close-on-click-modal="!flowSubmitting" :show-close="!flowSubmitting">
      <el-form ref="flowFormRef" :model="flowForm" :rules="flowRules" label-width="100px" :disabled="flowSubmitting">
        <el-form-item label="所属项目">{{ currentProject?.name }}</el-form-item>
        <el-form-item label="流程名称" prop="name"><el-input v-model="flowForm.name" maxlength="80" show-word-limit placeholder="请输入流程名称" /></el-form-item>
        <el-form-item label="起始方式" prop="templateId">
          <el-select v-model="flowForm.templateId" class="governance-full-width" placeholder="空白流程">
            <el-option label="空白流程" value="" />
            <el-option v-for="template in templates" :key="template.id" :value="template.id" :label="`${template.name}（${availabilityState(template.availability).label}）`" :disabled="!availabilityState(template.availability).usable" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板说明"><el-text type="info">{{ creationTemplate?.description || '创建后进入流程设计器，从组件库拖入节点并配置。隔离样本测试仅执行已支持的节点。' }}</el-text></el-form-item>
      </el-form>
      <el-alert v-if="flowSubmitError" :title="flowSubmitError" type="error" :closable="false" />
      <template #footer><el-button :disabled="flowSubmitting" @click="flowDialogOpen = false">取消</el-button><el-button type="primary" :loading="flowSubmitting" @click="submitFlow">创建流程</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="GovernanceWorkspace">
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { createGovernanceFlow, createGovernanceProject, getGovernanceOverview, listGovernanceCatalog, listGovernanceFlows, listGovernanceProjects, listGovernanceTemplates } from '@/api/governance'
import { availabilityState, catalogCategory, errorMessage, safeDesignerPath } from '../workspaceRules'
import TestWorkbench from '../components/TestWorkbench.vue'
import FlowDesigner from '../components/FlowDesigner.vue'
import ScheduleWorkbench from '../components/ScheduleWorkbench.vue'
import ConnectionWorkbench from '../components/ConnectionWorkbench.vue'

const { proxy } = getCurrentInstance()
const route = useRoute()
const router = useRouter()
const activeTab = ref(['flows', 'designer', 'tests', 'catalog', 'schedules', 'connections'].includes(route.query.tab) ? route.query.tab : 'flows')
const designerRef = ref()
const projectId = ref('')
const selectedFlowId = ref('')
const projects = ref([])
const flows = ref([])
const templates = ref([])
const catalog = ref([])
const overview = ref(null)
const workspaceLoading = ref(false)
const flowsLoading = ref(false)
const workspaceError = ref('')
const flowError = ref('')
const showSearch = ref(true)
const query = reactive({ name: '', pageNum: 1, pageSize: 20 })
const appliedName = ref('')
const catalogFilter = ref('')
const catalogKeyword = ref('')
const currentProject = computed(() => projects.value.find((item) => String(item.id) === projectId.value))
const selectedFlow = computed(() => flows.value.find((item) => String(item.id) === selectedFlowId.value) || null)
const filteredFlows = computed(() => flows.value.filter((flow) => String(flow.name || '').toLowerCase().includes(appliedName.value.toLowerCase())))
const visibleFlows = computed(() => filteredFlows.value.slice((query.pageNum - 1) * query.pageSize, query.pageNum * query.pageSize))
const engineReady = computed(() => overview.value?.engine?.configured === true && overview.value?.engine?.reachable === true)
const engineState = computed(() => engineReady.value ? { label: '引擎已连接', type: 'success' } : !overview.value ? { label: '连接状态待确认', type: 'info' } : !overview.value.engine?.configured ? { label: '引擎未配置', type: 'info' } : { label: '引擎不可达', type: 'warning' })
const catalogCategories = computed(() => [...new Set(catalog.value.map((item) => item.category).filter(Boolean))])
const filteredCatalog = computed(() => catalog.value.filter((item) => (!catalogFilter.value || item.category === catalogFilter.value) && `${item.name || ''} ${item.description || ''}`.toLowerCase().includes(catalogKeyword.value.trim().toLowerCase())))
const projectDialogOpen = ref(false)
const projectSubmitting = ref(false)
const projectSubmitError = ref('')
const projectFormRef = ref()
const projectForm = reactive({ name: '', description: '' })
const projectRules = { name: [{ required: true, whitespace: true, message: '请填写项目名称', trigger: 'blur' }] }
const flowDialogOpen = ref(false)
const flowSubmitting = ref(false)
const flowSubmitError = ref('')
const flowFormRef = ref()
const flowForm = reactive({ name: '', templateId: '' })
const flowRules = { name: [{ required: true, whitespace: true, message: '请填写流程名称', trigger: 'blur' }] }
const creationTemplate = computed(() => templates.value.find((item) => item.id === flowForm.templateId))
let workspaceSequence = 0
let flowSequence = 0
let disposed = false

const templateFor = (flow) => templates.value.find((item) => item.id === flow.templateId)
function designerFor(flow) { return flow?.engineId ? safeDesignerPath(flow.designerPath) : '' }
function openDesigner(flow) {
  if (!flow || !engineReady.value) return
  selectedFlowId.value = String(flow.id)
  activeTab.value = 'designer'
}
async function beforeTabChange() { return await designerRef.value?.confirmLeave() ?? true }
async function changeProject(id) { if (id !== projectId.value && await beforeTabChange()) projectId.value = id }

function syncRoute() {
  router.replace({ query: { ...route.query, tab: activeTab.value, projectId: projectId.value || undefined, flowId: selectedFlowId.value || undefined } }).catch(() => {})
}

async function refreshOverview() {
  try {
    const response = await getGovernanceOverview()
    if (!disposed) overview.value = response.data
  } catch { if (!disposed) overview.value = null }
}

async function loadWorkspace() {
  if (workspaceLoading.value) return
  const sequence = ++workspaceSequence
  workspaceLoading.value = true
  workspaceError.value = ''
  const result = await Promise.allSettled([getGovernanceOverview(), listGovernanceProjects(), listGovernanceTemplates(), listGovernanceCatalog()])
  if (disposed || sequence !== workspaceSequence) return
  const stores = [overview, projects, templates, catalog]
  const labels = ['引擎状态', '治理项目', '模板', '组件目录']
  const errors = []
  result.forEach((item, index) => {
    if (item.status === 'fulfilled') stores[index].value = item.value.data || (index === 0 ? null : [])
    else {
      if (index === 0) overview.value = null
      errors.push(`${labels[index]}：${errorMessage(item.reason)}`)
    }
  })
  workspaceError.value = errors.join('；')
  const previous = projectId.value
  const requested = projectId.value || String(route.query.projectId || '')
  projectId.value = projects.value.some((item) => String(item.id) === requested) ? requested : String(projects.value[0]?.id || '')
  workspaceLoading.value = false
  if (previous === projectId.value && projectId.value) await loadFlows()
}

async function loadFlows() {
  const sequence = ++flowSequence
  const id = projectId.value
  const requested = selectedFlowId.value || String(route.query.flowId || '')
  if (!id) { flows.value = []; flowsLoading.value = false; return }
  flowsLoading.value = true
  flowError.value = ''
  try {
    const response = await listGovernanceFlows(id)
    if (disposed || sequence !== flowSequence || id !== projectId.value) return
    flows.value = response.data || []
    selectedFlowId.value = flows.value.some((item) => String(item.id) === requested) ? requested : String(flows.value[0]?.id || '')
  } catch (error) {
    if (sequence === flowSequence && !disposed) flowError.value = errorMessage(error, '流程加载失败，请刷新重试。')
  } finally {
    if (sequence === flowSequence && !disposed) flowsLoading.value = false
  }
}

function searchFlows() { appliedName.value = query.name.trim(); query.pageNum = 1 }
function resetFlowQuery() { query.name = ''; searchFlows() }
function selectFlowForTest(flow) { selectedFlowId.value = String(flow.id); activeTab.value = 'tests' }
function openProjectDialog() {
  projectForm.name = ''; projectForm.description = ''; projectSubmitError.value = ''
  projectDialogOpen.value = true
  projectFormRef.value?.clearValidate()
}
function openFlowDialog() {
  flowForm.name = ''; flowForm.templateId = ''; flowSubmitError.value = ''
  flowDialogOpen.value = true
  flowFormRef.value?.clearValidate()
}

async function submitProject() {
  if (projectSubmitting.value || !await projectFormRef.value?.validate().catch(() => false)) return
  projectSubmitting.value = true
  projectSubmitError.value = ''
  try {
    if (!await beforeTabChange()) return
    const response = await createGovernanceProject({ name: projectForm.name.trim(), description: projectForm.description.trim() })
    if (disposed) return
    projects.value = [...projects.value, response.data]
    projectId.value = String(response.data.id)
    projectDialogOpen.value = false
    proxy.$modal.msgSuccess('治理项目已创建')
    await refreshOverview()
  } catch (error) { projectSubmitError.value = errorMessage(error, '项目创建失败。') }
  finally { projectSubmitting.value = false }
}

async function submitFlow() {
  if (flowSubmitting.value || !projectId.value || !await flowFormRef.value?.validate().catch(() => false)) return
  const id = projectId.value
  flowSubmitting.value = true
  flowSubmitError.value = ''
  try {
    const response = await createGovernanceFlow({ projectId: id, name: flowForm.name.trim(), templateId: flowForm.templateId || null })
    if (disposed) return
    flowDialogOpen.value = false
    if (id === projectId.value) {
      selectedFlowId.value = String(response.data.id)
      await loadFlows()
    }
    activeTab.value = 'designer'
    proxy.$modal.msgSuccess('流程已创建，可拖入组件继续设计')
    await refreshOverview()
  } catch (error) { flowSubmitError.value = errorMessage(error, '流程创建失败。') }
  finally { flowSubmitting.value = false }
}

watch(projectId, () => {
  selectedFlowId.value = ''
  flows.value = []
  query.pageNum = 1
  loadFlows()
  syncRoute()
})
watch([activeTab, selectedFlowId], syncRoute)
watch(() => route.query, (value) => {
  if (['flows', 'designer', 'tests', 'catalog', 'schedules', 'connections'].includes(value.tab)) activeTab.value = value.tab
  const id = String(value.projectId || '')
  if (id && projects.value.some((item) => String(item.id) === id)) projectId.value = id
  const flowId = String(value.flowId || '')
  if (flowId && flows.value.some((item) => String(item.id) === flowId)) selectedFlowId.value = flowId
})
watch(() => filteredFlows.value.length, (length) => { query.pageNum = Math.min(query.pageNum, Math.max(1, Math.ceil(length / query.pageSize))) })
onMounted(loadWorkspace)
onBeforeUnmount(() => { disposed = true; ++workspaceSequence; ++flowSequence })
onBeforeRouteLeave(beforeTabChange)
onBeforeRouteUpdate((to, from) => {
  if (['projectId', 'flowId', 'tab'].some(key => to.query[key] !== from.query[key]) && designerRef.value?.hasUnsaved()) return beforeTabChange()
  return true
})
</script>

<style scoped>
.governance-project-bar, .governance-engine-state { display: flex; align-items: center; flex-wrap: wrap; gap: var(--el-font-size-base); }
.governance-project-bar { justify-content: space-between; }
.governance-project-form { flex: 1; }
.governance-project-select { width: 260px; max-width: 100%; }
.governance-query-input { width: 200px; }
.governance-description { color: var(--el-text-color-secondary); font-size: var(--el-font-size-small); line-height: 1.6; }
.governance-full-width { width: 100%; }
.governance-workspace.is-designing .governance-project-bar { margin-bottom: 0; }
</style>
