<template>
  <section class="local-flow-workbench" aria-label="平台内置数据流程" v-loading="loading && !definition">
    <header class="local-flow-toolbar">
      <div><strong>{{ definition?.name || '数据流程' }}</strong><el-tag v-if="definition" effect="plain" class="ml8">V{{ definition.revision }}</el-tag><el-text class="ml8" :type="dirty || configDraft ? 'warning' : 'info'">{{ dirty || configDraft ? '有未保存修改' : definition ? '已保存' : '平台内置执行' }}</el-text></div>
      <el-space wrap><el-button v-if="canEdit" icon="Plus" :disabled="busy || active || pending" @click="openCreate(true)">新建流程</el-button><el-button v-if="definition && canEdit" icon="DocumentChecked" :loading="saving" :disabled="busy || active || pending || (!dirty && !configDraft)" @click="save">保存</el-button><el-button v-if="definition && canRun" icon="View" :disabled="!ready || !selectedNode || busy || active || pending" @click="execute('preview')">预览节点</el-button><el-button v-if="definition && canRun" type="primary" icon="VideoPlay" :loading="submitting" :disabled="!ready || busy || active || pending" @click="execute('run')">{{ dirty || configDraft ? '保存并运行' : '运行流程' }}</el-button><el-button v-if="active && canRun" type="danger" plain icon="VideoPause" :loading="stopping" @click="stop">停止</el-button><el-button icon="Refresh" :disabled="busy" aria-label="刷新任务与引擎状态" @click="load" /></el-space>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="true" @close="error = ''" />
    <el-alert v-if="!ready && !loading" title="内置执行服务暂不可用" description="可以保留草稿，刷新状态后再运行；管理员可检查平台后台日志。" type="warning" :closable="false" />
    <el-alert v-if="pending" title="上次提交结果尚未确认，已保留原请求" description="重试会使用同一个请求标识，不会另建一次运行。" type="warning" :closable="false"><el-button :loading="submitting" :disabled="busy || !ready" @click="submitPending">核查并重试原请求</el-button></el-alert>
    <div class="local-flow-body" :class="{ 'has-inspector': inspectorOpen && (selectedNode || selectedEdge) }">
      <aside class="local-flow-library" aria-label="任务与组件">
        <el-tabs v-model="libraryTab" class="motion-tabs"><el-tab-pane name="tasks"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">任务</span></span></template><el-input v-model="search" placeholder="搜索流程" aria-label="搜索流程" clearable /><el-scrollbar class="local-library-scroll"><el-button v-for="item in filteredDefinitions" :key="item.id" text class="local-task" :class="{ 'is-current': definition?.id === item.id }" :disabled="busy || active || pending" :title="item.name" @click="openDefinition(item.id)"><span>{{ item.name }}</span><el-text type="info" size="small">{{ item.nodeCount ?? item.graph?.nodes?.length ?? 0 }} 节点 · V{{ item.revision }}</el-text></el-button><el-empty v-if="!filteredDefinitions.length" :image-size="48" :description="search ? '没有匹配流程' : '尚未创建流程'" /></el-scrollbar></el-tab-pane><el-tab-pane name="tools"><template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">组件</span></span></template><el-scrollbar class="local-library-scroll"><section v-for="group in ['输入', '处理', '输出']" :key="group"><h3>{{ group }}</h3><el-button v-for="item in LOCAL_NODE_TYPES.filter(value => value.group === group)" :key="item.type" text class="local-tool" :icon="item.icon" :draggable="editable" :disabled="!editable" @dragstart="dragNode($event, item.type)" @click="insertNode(item.type)">{{ item.label }}</el-button></section></el-scrollbar><el-text type="info" size="small">拖入画布或点击添加</el-text></el-tab-pane></el-tabs>
      </aside>
      <main class="local-flow-main">
        <nav class="local-canvas-toolbar" aria-label="流程操作"><el-space wrap><el-dropdown v-if="definition && canEdit" trigger="click" @command="insertNode"><el-button link icon="Plus" :disabled="!editable">添加节点</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="item in LOCAL_NODE_TYPES" :key="item.type" :command="item.type" :icon="item.icon">{{ item.label }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button v-if="definition" link icon="Sort" :disabled="!editable" @click="arrange">整理画布</el-button><el-button v-if="definition && canRun" link icon="CircleCheck" :loading="validating" :disabled="busy || active || pending || !ready" @click="validate">检查流程</el-button><el-button v-if="definition" link icon="Clock" :disabled="busy" @click="showHistory">运行记录</el-button><el-button v-if="definition" link icon="Timer" :disabled="busy || active || pending" @click="openSchedules">定时计划</el-button><el-button v-if="(selectedNode || selectedEdge) && !inspectorOpen" link icon="Setting" @click="inspectorOpen = true">节点配置</el-button></el-space><el-text type="info" size="small">最多 {{ limits.maxRows }} 行 · {{ limits.maxNodes }} 节点</el-text></nav>
        <div class="local-flow-canvas" v-loading="loading">
          <FlowDiagram v-if="definition" ref="diagram" :nodes="diagramNodes" :connections="diagramEdges" :selected-node-id="selectedNodeId" :selected-edge-id="selectedEdgeId" :editable="editable" :presentation="localNodeKind" :summary="localNodeSummary" :source-predicate="localSource" :run="diagramRun" :result-stale="stale" show-auxiliary @select-node="selectNode" @select-edge="selectEdge" @move-node="moveNode" @add-node="dropNode" @connect="connectNodes" />
          <el-empty v-else description="在平台内完成输入、处理与输出" :image-size="72"><el-space v-if="canEdit" wrap><el-button type="primary" icon="Document" :disabled="busy" @click="openCreate(true)">从示例开始</el-button><el-button icon="Plus" :disabled="busy" @click="openCreate(false)">创建空白流程</el-button></el-space><el-text v-else type="info">从左侧选择一个已有流程</el-text></el-empty>
        </div>
        <footer v-if="definition" class="local-canvas-footer"><el-text type="info" size="small">{{ graph.nodes.length }} 个节点 · {{ graph.connections.length }} 条连接</el-text><el-button link :icon="resultsOpen ? 'ArrowDown' : 'ArrowUp'" @click="resultsOpen = !resultsOpen">{{ resultsOpen ? '收起结果' : '显示结果' }}</el-button></footer>
      </main>
      <aside v-if="inspectorOpen && (selectedNode || selectedEdge)" class="local-flow-inspector" aria-label="参数配置"><LocalFlowNodeConfig v-if="selectedNode" :key="selectedNode.id" ref="nodeEditor" :node="selectedNode" :readonly="!editable" :can-run="canRun && ready" :max-bytes="limits.maxBytes" @update="updateNode" @draft-change="configDraft = $event" @close="closeInspector" @remove="removeNode" @preview="execute('preview')" @error="error = $event" /><template v-else><el-button link icon="Close" aria-label="收起连接配置" @click="inspectorOpen = false" /><el-descriptions :column="1"><el-descriptions-item label="上游">{{ nodeName(selectedEdge.sourceId) }}</el-descriptions-item><el-descriptions-item label="下游">{{ nodeName(selectedEdge.targetId) }}</el-descriptions-item></el-descriptions><el-button link type="danger" :disabled="!editable" @click="removeEdge">删除连接</el-button></template></aside>
    </div>
    <section v-if="resultsOpen && definition" class="local-flow-results" aria-label="运行结果">
      <header><el-space wrap><strong>测试与运行结果</strong><el-tag v-if="run" :type="localRunState(run.state)[1]">{{ localRunState(run.state)[0] }}</el-tag><el-text v-if="run" type="info">V{{ run.revision }} · {{ run.mode === 'preview' ? '节点预览' : '流程运行' }}</el-text><el-tag v-if="stale" type="warning" effect="plain">结果来自旧版本</el-tag></el-space><el-button link icon="Close" aria-label="关闭运行结果" @click="resultsOpen = false" /></header>
      <el-alert v-if="run?.error" :title="run.error" type="error" :closable="false"><el-button v-if="run.errorNodeId" link type="primary" @click="locateError">定位失败节点</el-button></el-alert>
      <el-tabs v-model="resultTab" class="motion-tabs">
        <el-tab-pane name="data"><template #label><span class="motion-control-label"><svg-icon icon-class="table" class="motion-control-label__icon" /><span class="motion-control-label__text">数据预览</span></span></template><el-space v-if="run?.steps?.length" wrap><el-select v-model="resultNodeId" aria-label="查看节点结果" @change="selectResultNode"><el-option v-for="step in run.steps" :key="step.id" :label="step.name" :value="step.id" /></el-select><el-text type="info" size="small">输入 {{ resultStep?.inputRows ?? '未记录' }} 行，输出 {{ resultStep?.outputRows ?? '未记录' }} 行；采样 {{ resultStep?.sampleRows?.length || 0 }} 行</el-text></el-space><el-table :data="resultStep?.sampleRows || []" max-height="220" :empty-text="!run ? '选中节点并预览，或运行整个流程' : active ? '正在执行，请稍候' : '该节点没有返回采样数据'"><el-table-column v-for="field in resultFields" :key="field" :label="field" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ cell(row[field]) }}</template></el-table-column></el-table></el-tab-pane>
        <el-tab-pane name="steps"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">节点进度</span></span></template><el-table :data="run?.steps || []" max-height="220" empty-text="尚无执行记录" @row-click="selectResultNodeFromRow"><el-table-column prop="name" label="节点" min-width="180" /><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="localRunState(row.status)[1]">{{ localRunState(row.status)[0] }}</el-tag></template></el-table-column><el-table-column prop="inputRows" label="输入行数" width="110" /><el-table-column prop="outputRows" label="输出行数" width="110" /></el-table></el-tab-pane>
        <el-tab-pane name="files"><template #label><span class="motion-control-label"><svg-icon icon-class="download" class="motion-control-label__icon" /><span class="motion-control-label__text">输出文件</span></span></template><el-table :data="run?.outputs || []" max-height="220" :empty-text="run?.mode === 'preview' ? '节点预览不发布文件，请正式运行流程后下载' : '成功执行输出节点后显示文件'"><el-table-column prop="filename" label="文件名" min-width="200" /><el-table-column prop="bytes" label="字节数" width="110" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" icon="Download" @click="download(row)">下载</el-button></template></el-table-column></el-table></el-tab-pane>
        <el-tab-pane name="validation"><template #label><span class="motion-control-label"><svg-icon icon-class="validCode" class="motion-control-label__icon" /><span class="motion-control-label__text">流程检查</span></span></template><el-alert v-if="validation?.valid" title="流程结构与配置检查通过，实际数据处理请预览或运行" type="success" :closable="false" /><el-table v-else :data="validation?.errors || []" max-height="220" empty-text="点击检查流程查看配置问题"><el-table-column prop="message" label="问题" min-width="260" /><el-table-column label="节点" width="180"><template #default="{ row }"><el-button v-if="row.nodeId" link type="primary" @click="focusNode(row.nodeId)">{{ nodeName(row.nodeId) || '定位节点' }}</el-button></template></el-table-column></el-table></el-tab-pane>
      </el-tabs>
    </section>
    <el-dialog v-model="createOpen" title="新建数据流程" width="500px" append-to-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><el-form label-position="top" :disabled="saving" @submit.prevent="create"><el-form-item label="流程名称" required><el-input v-model="newName" maxlength="80" @keyup.enter="create" /></el-form-item><el-form-item label="起步方式"><el-radio-group v-model="useSample"><el-radio :value="true">本地处理示例</el-radio><el-radio :value="false">空白流程</el-radio></el-radio-group></el-form-item></el-form><el-alert v-if="useSample" title="CSV输入、字段整理、条件过滤、排序、文件输出" description="附带3行合成数据，可以修改过滤条件后预览对比；创建不会自动运行。" type="info" :closable="false" /><template #footer><el-button :disabled="saving" @click="createOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!newName.trim()" @click="create">创建</el-button></template></el-dialog>
    <el-drawer v-model="historyOpen" title="运行记录" size="720px" append-to-body><el-button icon="Refresh" :loading="historyLoading" @click="loadHistory">刷新</el-button><el-table :data="history" v-loading="historyLoading" empty-text="暂无运行记录"><el-table-column prop="createdAt" label="开始时间" min-width="190" /><el-table-column prop="revision" label="版本" width="75" /><el-table-column label="状态" width="115"><template #default="{ row }"><el-tag :type="localRunState(row.state)[1]">{{ localRunState(row.state)[0] }}</el-tag></template></el-table-column><el-table-column label="操作" width="105"><template #default="{ row }"><el-button link type="primary" :disabled="busy || active && row.id !== run?.id || Boolean(pending)" @click="viewRun(row)">查看结果</el-button></template></el-table-column></el-table></el-drawer>
    <el-drawer v-model="schedulesOpen" title="定时计划" size="85%" append-to-body destroy-on-close><LocalFlowSchedules v-if="schedulesOpen && definition" :key="definition.id" :definition="definition" :can-edit="canEdit" :can-run="canRun" :blocked="busy || active || Boolean(pending)" :before-save="save" @view-run="viewRun" /></el-drawer>
  </section>
</template>
<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { saveAs } from 'file-saver'
import * as api from '@/api/governance/local'
import FlowDiagram from '../components/FlowDiagram.vue'
import LocalFlowNodeConfig from './LocalFlowNodeConfig.vue'
import LocalFlowSchedules from './LocalFlowSchedules.vue'
import { arrangeNodes } from '../graphRules'
import { errorMessage } from '../workspaceRules'
import { LOCAL_NODE_TYPES, cloneFlowValue, flowId, localNodeKind, localNodeSummary, localSource, localActive, localRunState, newLocalNode, localStarterGraph, connectLocalNodes, localResultFields } from './localFlowRules'
const { proxy } = getCurrentInstance(), route = useRoute(), router = useRouter()
const canEdit = computed(() => proxy.$auth.hasPermi('governance:flow:edit')), canRun = computed(() => proxy.$auth.hasPermi('governance:flow:test'))
const status = ref({}), definitions = ref([]), definition = ref(null), graph = ref({ nodes: [], connections: [] }), dirty = ref(false), configDraft = ref(false)
const loading = ref(false), saving = ref(false), submitting = ref(false), validating = ref(false), stopping = ref(false), error = ref('')
const selectedNodeId = ref(''), selectedEdgeId = ref(''), inspectorOpen = ref(false), nodeEditor = ref(), diagram = ref()
const libraryTab = ref('tasks'), search = ref(''), run = ref(null), pending = ref(null), history = ref([]), historyOpen = ref(false), historyLoading = ref(false), validation = ref(null)
const createOpen = ref(false), useSample = ref(true), newName = ref(''), schedulesOpen = ref(false)
const resultsOpen = ref(false), resultTab = ref('data'), resultNodeId = ref('')
let disposed = false, requestGeneration = 0, pollTimer
const busy = computed(() => loading.value || saving.value || submitting.value || validating.value || stopping.value), active = computed(() => localActive(run.value))
const ready = computed(() => status.value.enabled === true && status.value.executionAvailable === true)
const editable = computed(() => Boolean(definition.value) && canEdit.value && !busy.value && !active.value && !pending.value)
const limits = computed(() => ({ maxRows: 1000, maxNodes: 32, maxBytes: 1048576, ...(status.value.limits || {}) }))
const selectedNode = computed(() => graph.value.nodes.find(node => node.id === selectedNodeId.value)), selectedEdge = computed(() => graph.value.connections.find(edge => edge.id === selectedEdgeId.value))
const filteredDefinitions = computed(() => definitions.value.filter(item => item.name.includes(search.value.trim())))
const diagramNodes = computed(() => graph.value.nodes.map(node => ({ ...node, editable: editable.value, role: node.type === 'OUTPUT' ? 'CAPTURE' : 'PROCESSOR', issues: (validation.value?.errors || []).filter(item => item.nodeId === node.id) })))
const diagramEdges = computed(() => graph.value.connections.map(edge => ({ ...edge, relationships: ['success'], enabled: true })))
const stale = computed(() => Boolean(run.value && (run.value.revision !== definition.value?.revision || dirty.value || configDraft.value)))
const diagramRun = computed(() => run.value ? { ...run.value, status: run.value.state } : null)
const resultStep = computed(() => run.value?.steps?.find(step => step.id === resultNodeId.value)), resultFields = computed(() => localResultFields(resultStep.value))
const nodeName = id => graph.value.nodes.find(node => node.id === id)?.name || ''
const cell = value => value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value)
function changed() { dirty.value = true; validation.value = null }
function applyNodeDraft() { return !configDraft.value || nodeEditor.value?.apply() !== false }
function remember() { router.replace({ query: { ...route.query, tab: 'local', localTask: definition.value.id } }).catch(() => {}) }
function pendingKey() { return `local-flow:pending:${definition.value?.id}` }
function setDefinition(value) { definition.value = value; graph.value = cloneFlowValue(value.graph); dirty.value = false; configDraft.value = false; selectedNodeId.value = ''; selectedEdgeId.value = ''; inspectorOpen.value = false; validation.value = null; run.value = null; resultsOpen.value = false; clearTimeout(pollTimer); try { pending.value = JSON.parse(localStorage.getItem(pendingKey()) || 'null') } catch { pending.value = { invalid: true } } remember() }
async function canLeave() {
  if (busy.value || active.value || pending.value) { proxy.$modal.msgWarning('请先等待当前操作结束，或停止并确认本次运行'); return false }
  if (!dirty.value && !configDraft.value) return true
  try { await proxy.$modal.confirm('流程有未保存修改，是否放弃修改后离开？'); return true } catch { return false }
}
async function load() {
  if (loading.value) return
  loading.value = true
  try { const [state, list] = await Promise.all([api.localStatus(), api.localDefinitions()]); if (disposed) return; status.value = state.data || {}; definitions.value = list.data || [] }
  catch (cause) { if (!disposed) { status.value = {}; error.value = errorMessage(cause, '工作台加载失败') } }
  finally { loading.value = false }
}
async function openDefinition(id) {
  if (id === definition.value?.id || !await canLeave()) return
  const generation = ++requestGeneration; loading.value = true; error.value = ''
  try {
    const [detail, records] = await Promise.all([api.localDefinition(id), api.localRunHistory(id)])
    if (disposed || generation !== requestGeneration) return
    setDefinition(detail.data); history.value = records.data || []
    const current = history.value.find(item => localActive(item)) || (pending.value && history.value.find(item => item.requestId === pending.value.requestId))
    if (current) { pending.value = null; localStorage.removeItem(pendingKey()); run.value = current; resultsOpen.value = true; startPolling(); run.value = (await api.localRun(current.id)).data; startPolling() }
    await nextTick(); diagram.value?.fit()
  } catch (cause) { if (!disposed) error.value = errorMessage(cause, '流程读取失败') }
  finally { loading.value = false }
}
async function openCreate(sample) { if (!canEdit.value || !await canLeave()) return; useSample.value = sample; newName.value = sample ? '本地数据处理示例' : ''; createOpen.value = true }
async function create() {
  if (!canEdit.value || busy.value || !newName.value.trim()) return
  saving.value = true; error.value = ''
  try { const result = await api.createLocalDefinition({ name: newName.value.trim(), graph: useSample.value ? localStarterGraph() : { nodes: [], connections: [] } }); if (disposed) return; setDefinition(result.data); definitions.value = [result.data, ...definitions.value]; history.value = []; createOpen.value = false; libraryTab.value = 'tools'; if (useSample.value) focusNode(graph.value.nodes.find(node => node.type === 'FILTER')?.id); await nextTick(); diagram.value?.fit(); proxy.$modal.msgSuccess('流程已创建，可以修改节点后预览或运行') }
  catch (cause) { if (!disposed) error.value = errorMessage(cause, '创建流程失败') }
  finally { saving.value = false }
}
async function save() {
  if (!definition.value || busy.value || active.value || pending.value || !applyNodeDraft()) return false
  if (!dirty.value) return true
  if (!canEdit.value) return false
  const owner = definition.value.id; saving.value = true; error.value = ''
  try { const result = await api.saveLocalDefinition(owner, { name: definition.value.name, revision: definition.value.revision, graph: cloneFlowValue(graph.value) }); if (disposed || definition.value?.id !== owner) return false; definition.value = result.data; graph.value = cloneFlowValue(result.data.graph); dirty.value = false; definitions.value = definitions.value.map(item => item.id === owner ? result.data : item); return true }
  catch (cause) { error.value = errorMessage(cause, '保存失败，请保留当前修改并检查版本冲突'); return false }
  finally { saving.value = false }
}
function selectNode(node) { if (!applyNodeDraft()) return; selectedNodeId.value = node.id; selectedEdgeId.value = ''; inspectorOpen.value = true; if (run.value?.steps?.some(step => step.id === node.id)) resultNodeId.value = node.id }
function selectEdge(edge) { if (!applyNodeDraft()) return; selectedEdgeId.value = edge.id; selectedNodeId.value = ''; inspectorOpen.value = true }
function focusNode(id) { const node = graph.value.nodes.find(item => item.id === id); if (node) selectNode(node) }
function closeInspector() { if (applyNodeDraft()) inspectorOpen.value = false }
function updateNode(node) { if (!editable.value) return; graph.value.nodes = graph.value.nodes.map(item => item.id === node.id ? node : item); changed() }
function dragNode(event, type) { if (!editable.value) { event.preventDefault(); return } event.dataTransfer.effectAllowed = 'copy'; event.dataTransfer.setData('application/x-governance-node', type) }
async function insertNode(type, position) { if (!editable.value || !applyNodeDraft()) return; if (graph.value.nodes.length >= limits.value.maxNodes) { error.value = `最多支持${limits.value.maxNodes}个节点`; return } try { const node = newLocalNode(type, graph.value.nodes, position || diagram.value?.centerPosition()); graph.value.nodes.push(node); changed(); selectNode(node); if (!position) { await nextTick(); diagram.value?.fit() } } catch (cause) { error.value = cause.message } }
function dropNode({ key, position }) { insertNode(key, position) }
function moveNode({ id, position }) { if (!editable.value) return; const node = graph.value.nodes.find(item => item.id === id); if (node) { node.position = position; changed() } }
function connectNodes({ sourceId, targetId }) { if (!editable.value || !applyNodeDraft()) return; try { graph.value.connections.push(connectLocalNodes(graph.value, sourceId, targetId)); changed() } catch (cause) { error.value = cause.message } }
async function arrange() { if (!editable.value || !applyNodeDraft()) return; graph.value.nodes = arrangeNodes(graph.value.nodes, graph.value.connections); changed(); await nextTick(); diagram.value?.fit() }
async function removeNode() { if (!editable.value || !selectedNode.value) return; const id = selectedNodeId.value; try { await proxy.$modal.confirm(`删除“${selectedNode.value.name}”及相关连接？`); if (!editable.value || selectedNodeId.value !== id) return; graph.value.nodes = graph.value.nodes.filter(node => node.id !== id); graph.value.connections = graph.value.connections.filter(edge => edge.sourceId !== id && edge.targetId !== id); selectedNodeId.value = ''; configDraft.value = false; changed() } catch { } }
function removeEdge() { if (!editable.value) return; graph.value.connections = graph.value.connections.filter(edge => edge.id !== selectedEdgeId.value); selectedEdgeId.value = ''; changed() }
async function validate() { if (!canRun.value || !ready.value || !await save()) return; const owner = definition.value.id; validating.value = true; error.value = ''; try { const result = await api.validateLocalDefinition(owner); if (!disposed && definition.value?.id === owner) { validation.value = result.data; resultsOpen.value = true; resultTab.value = 'validation' } } catch (cause) { error.value = errorMessage(cause, '流程检查失败') } finally { validating.value = false } }
async function execute(mode) {
  if (!ready.value || !canRun.value || busy.value || active.value || pending.value || !definition.value || mode === 'preview' && !selectedNode.value || !await save()) return
  pending.value = { revision: definition.value.revision, mode, ...(mode === 'preview' ? { previewNodeId: selectedNodeId.value } : {}), sampleLimit: 20, requestId: flowId() }
  try { localStorage.setItem(pendingKey(), JSON.stringify(pending.value)) } catch { pending.value = null; error.value = '浏览器无法保存提交标识，未启动运行；请检查浏览器存储设置'; return }
  await submitPending()
}
async function submitPending() {
  if (!pending.value?.requestId || !canRun.value || !ready.value || busy.value) return
  const owner = definition.value.id; submitting.value = true; error.value = ''
  try { let result = await api.localRunByRequest(owner, pending.value.requestId); if (!result.data) { const current = (await api.localDefinition(owner)).data; if (current.revision !== pending.value.revision) { result = await api.localRunByRequest(owner, pending.value.requestId); if (!result.data) { pending.value = null; localStorage.removeItem(pendingKey()); error.value = '原提交版本已过期，未找到执行记录。请重新读取流程后运行。'; return } } else result = await api.runLocalDefinition(owner, pending.value) } if (disposed || definition.value?.id !== owner) return; run.value = result.data; resultNodeId.value = pending.value.previewNodeId || graph.value.nodes.find(node => node.type === 'OUTPUT')?.id || ''; pending.value = null; localStorage.removeItem(pendingKey()); resultsOpen.value = true; resultTab.value = 'data'; startPolling() }
  catch (cause) { if (!disposed) error.value = errorMessage(cause, '提交结果尚未确认，请使用原请求核查') }
  finally { submitting.value = false }
}
function startPolling() { clearTimeout(pollTimer); if (!disposed && localActive(run.value)) pollTimer = setTimeout(poll, 1000) }
async function poll() {
  const owner = definition.value?.id, id = run.value?.id
  if (!id) return
  try { const result = await api.localRun(id); if (disposed || definition.value?.id !== owner || run.value?.id !== id) return; run.value = result.data; if (!localActive(run.value) && run.value.state === 'FAILED') resultTab.value = 'steps' }
  catch (cause) { if (!disposed && run.value?.id === id) error.value = errorMessage(cause, '运行状态读取失败，未确认停止；系统将继续核查') }
  finally { if (!disposed && run.value?.id === id) startPolling() }
}
async function stop() { if (!active.value || !canRun.value || stopping.value) return; const id = run.value.id; stopping.value = true; try { const result = await api.stopLocalRun(id); if (!disposed && run.value?.id === id) { run.value = result.data; startPolling() } } catch (cause) { error.value = errorMessage(cause, '停止尚未确认，请继续核查运行状态') } finally { stopping.value = false } }
async function loadHistory() { if (!definition.value || historyLoading.value) return; const owner = definition.value.id; historyLoading.value = true; try { const result = await api.localRunHistory(owner); if (!disposed && definition.value?.id === owner) history.value = result.data || [] } catch (cause) { error.value = errorMessage(cause, '运行记录读取失败') } finally { historyLoading.value = false } }
function showHistory() { historyOpen.value = true; loadHistory() }
async function viewRun(record) { if (busy.value || pending.value || active.value && run.value?.id !== record.id) return; const owner = definition.value?.id; loading.value = true; try { const result = await api.localRun(record.id); if (disposed || definition.value?.id !== owner) return; run.value = result.data; resultNodeId.value = run.value.steps?.slice(-1)[0]?.id || ''; historyOpen.value = false; resultsOpen.value = true; resultTab.value = run.value.state === 'FAILED' ? 'steps' : 'data'; startPolling() } catch (cause) { error.value = errorMessage(cause, '运行结果读取失败') } finally { loading.value = false } }
function selectResultNode() { focusNode(resultNodeId.value) }
function selectResultNodeFromRow(row) { resultNodeId.value = row.id; resultTab.value = 'data'; focusNode(row.id) }
function locateError() { focusNode(run.value?.errorNodeId) }
async function download(file) { if (!run.value?.id) return; try { const result = await api.downloadLocalOutput(run.value.id, file.id); saveAs(result, file.filename) } catch (cause) { error.value = errorMessage(cause, '下载失败') } }
function openSchedules() { if (definition.value) schedulesOpen.value = true }
function beforeUnload(event) { if (dirty.value || configDraft.value || pending.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(async () => { window.addEventListener('beforeunload', beforeUnload); await load(); const id = route.query.localTask; if (!disposed && definitions.value.some(item => item.id === id)) await openDefinition(id) })
onBeforeUnmount(() => { disposed = true; requestGeneration++; clearTimeout(pollTimer); window.removeEventListener('beforeunload', beforeUnload) })
defineExpose({ canLeave })
</script>
<style scoped>
.local-flow-workbench { display: flex; flex-direction: column; gap: var(--el-font-size-small); height: calc(100vh - 190px); min-height: 520px; color: var(--app-text); }
.local-flow-toolbar, .local-canvas-toolbar, .local-canvas-footer, .local-flow-results header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: var(--el-font-size-small); flex-shrink: 0; }
.local-flow-toolbar strong { font-size: var(--el-font-size-large); color: var(--app-heading); }
.local-flow-body { flex: 1; min-height: 280px; display: grid; grid-template-columns: 220px minmax(0, 1fr); border: 1px solid var(--surface-border); border-radius: var(--el-border-radius-base); overflow: hidden; background: var(--surface-bg); }
.local-flow-body.has-inspector { grid-template-columns: 220px minmax(0, 1fr) 340px; }
.local-flow-library, .local-flow-inspector { margin: 0; min-width: 0; min-height: 0; padding: var(--el-font-size-small); border: 0; border-radius: 0; box-shadow: none; font-family: inherit; font-size: var(--el-font-size-base); line-height: var(--el-font-line-height-primary); background: var(--surface-subtle); }
.local-flow-library { border-right: 1px solid var(--surface-border); }
.local-flow-inspector { border-left: 1px solid var(--surface-border); }
.local-flow-library > .el-tabs { display: flex; flex-direction: column; height: 100%; }
.local-flow-library :deep(.el-tabs__content) { flex: 1; min-height: 0; }
.local-flow-library :deep(.el-tab-pane) { display: flex; flex-direction: column; height: 100%; gap: var(--el-font-size-small); }
.local-library-scroll { flex: 1; min-height: 0; }
.local-flow-library h3 { color: var(--app-muted); font-weight: 500; font-size: var(--el-font-size-small); margin-block: var(--el-font-size-small); }
.local-task.el-button { display: flex; width: 100%; height: auto; margin: 0; justify-content: flex-start; padding: var(--el-font-size-small); }
.local-task :deep(> span) { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; overflow: hidden; }
.local-task :deep(> span > span:first-child) { overflow: hidden; text-overflow: ellipsis; max-width: 100%; }
.local-task.is-current { background: var(--surface-hover); color: var(--el-color-primary); }
.local-tool.el-button { display: flex; width: 100%; justify-content: flex-start; margin: 0; }
.local-flow-main { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.local-canvas-toolbar, .local-canvas-footer { padding: var(--el-font-size-small); }
.local-canvas-toolbar { border-bottom: 1px solid var(--surface-border); }
.local-canvas-footer { border-top: 1px solid var(--surface-border); }
.local-flow-canvas { display: flex; flex: 1; min-height: 0; min-width: 0; }
.local-flow-canvas > * { width: 100%; }
.local-flow-canvas > .el-empty { align-self: center; }
.local-flow-results { flex: 0 1 320px; min-height: 180px; overflow: auto; border: 1px solid var(--surface-border); border-radius: var(--el-border-radius-base); padding: var(--el-font-size-small); background: var(--surface-bg); }
.local-flow-results header strong { color: var(--app-heading); }
@media (max-width: 1100px) { .local-flow-body.has-inspector { grid-template-columns: minmax(0, 1fr) 320px; } .local-flow-body.has-inspector .local-flow-library { display: none; } }
@media (max-width: 800px) { .local-flow-body, .local-flow-body.has-inspector { grid-template-columns: minmax(0, 1fr); } .local-flow-library, .local-flow-body.has-inspector .local-flow-main { display: none; } .local-flow-inspector { border-left: 0; } }
</style>
