<template>
  <section class="etl-designer" aria-label="数据流程设计器">
    <header class="etl-designer__toolbar">
      <div class="etl-designer__identity">
        <el-icon :size="20"><Connection /></el-icon>
        <el-select :model-value="flow?.id" filterable placeholder="选择设计流程" aria-label="设计流程" @change="chooseFlow">
          <el-option v-for="item in flows" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-tag size="small" effect="plain">开发</el-tag>
      </div>
      <div class="etl-designer__actions">
        <el-text type="info" size="small" role="status">{{ busy ? '正在保存…' : configDirty ? '节点配置未保存' : saveMessage || '已保存的流程' }}</el-text>
        <el-button icon="Refresh" :disabled="busy" :loading="loading" @click="refreshGraph">刷新</el-button>
        <el-button icon="Sort" :disabled="!canWrite || !graph.nodes.length" @click="arrange">整理画布</el-button>
        <el-button type="primary" icon="VideoPlay" :disabled="!flow || !engineReady || busy || configDirty" @click="openTests">测试流程</el-button>
        <el-dropdown trigger="click" @command="advancedCommand">
          <el-button icon="MoreFilled" aria-label="更多设计器操作" :disabled="busy" />
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="engine" :disabled="!enginePath">高级引擎管理</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="graph.issues.length" :title="graph.issues.join('；')" type="warning" :closable="false" />
    <el-empty v-if="!flow" description="先创建或选择一个流程，再开始设计" />
    <div v-else class="etl-designer__body" :class="{ 'has-test-panel': testOpen }">
      <section class="etl-palette" role="complementary" aria-label="流程组件库">
        <header><strong>组件库</strong><el-text size="small" type="info">拖入画布</el-text></header>
        <el-input v-model="search" placeholder="搜索组件" aria-label="搜索流程组件" prefix-icon="Search" clearable />
        <el-scrollbar class="etl-palette__scroll">
          <section v-for="group in groups" :key="group.key" class="etl-palette__group">
            <h3>{{ group.label }}</h3>
            <el-button v-for="kind in group.items" :key="kind.key" class="etl-palette__item" :disabled="!canWrite || !available(kind)"
              :draggable="canWrite && available(kind)" :title="kind.description" @dragstart="dragKind($event, kind)" @click="addNode({ key: kind.key })">
              <span class="etl-palette__icon" :class="`is-${kind.category}`"><el-icon :size="19"><component :is="kind.icon" /></el-icon></span>
              <span>{{ kind.label }}</span>
              <el-icon class="etl-palette__add"><Plus /></el-icon>
            </el-button>
          </section>
          <el-empty v-if="!groups.length" description="没有匹配的组件" :image-size="44" />
          <section class="etl-palette__future">
            <h3>业务连接器</h3>
            <p>Kafka、数据库与海康适配器</p>
            <el-tag size="small" type="info" effect="plain">适配中</el-tag>
          </section>
        </el-scrollbar>
      </section>
      <main class="etl-designer__main">
        <div class="etl-designer__canvas" v-loading="loading">
          <flow-diagram ref="diagram" :key="flow.id" :nodes="graph.nodes" :connections="graph.connections" :selected-node-id="selectedNodeId" :selected-edge-id="selectedEdgeId"
            :editable="canWrite" :run="run" :result-stale="resultStale" @select-node="selectNode" @select-edge="selectEdge" @move-node="moveNode" @add-node="addNode" @connect="connectNodes" />
        </div>
        <footer class="etl-designer__status">
          <div><el-text size="small" type="info">{{ graph.nodes.length }} 个节点 · {{ graph.connections.length }} 条连接</el-text><el-text v-if="run" size="small" :type="resultStale ? 'info' : runState(run.status).type">{{ resultStale ? '历史测试结果，编辑后请重新测试' : runState(run.status).label }}</el-text></div>
          <el-button link :icon="testOpen ? 'ArrowDown' : 'ArrowUp'" @click="testOpen = !testOpen">{{ testOpen ? '收起测试台' : '样本与运行结果' }}</el-button>
        </footer>
        <div v-show="testOpen" class="etl-designer__tests">
          <test-workbench ref="testPanel" compact :flow="flow" :templates="templates" :engine-ready="engineReady" :selected-node-id="selectedNodeId" :external-busy="busy || configDirty"
            :before-start="prepareTest" @updated="emit('updated')" @run-change="runChanged" @test-started="testStarted" @submission-change="testSubmitting = $event" />
        </div>
      </main>
      <section class="etl-inspector" aria-label="配置区域">
        <node-config-panel v-if="selectedNode" ref="configPanel" :node="selectedNode" :saving="busy" :readonly="!canEditGraph || !selectedNode.editable"
          @save="saveNode" @remove="removeNode" @preview="previewNode" @dirty-change="configDirty = $event" />
        <section v-else-if="selectedEdge" class="etl-edge-inspector">
          <header><strong>分支连接</strong><el-icon :size="20"><Connection /></el-icon></header>
          <el-descriptions :column="1" direction="vertical">
            <el-descriptions-item label="上游节点">{{ nodeName(selectedEdge.sourceId) }}</el-descriptions-item>
            <el-descriptions-item label="下游节点">{{ nodeName(selectedEdge.targetId) }}</el-descriptions-item>
            <el-descriptions-item label="流转关系"><el-tag v-for="rel in selectedEdge.relationships" :key="rel" size="small" effect="plain">{{ relationshipLabel(rel) }}</el-tag></el-descriptions-item>
          </el-descriptions>
          <p>需要改变分支时，删除此连接后，从上游节点重新连线。</p>
          <el-button type="danger" plain icon="Delete" :disabled="!canWrite" :loading="busy" @click="removeEdge">删除连接</el-button>
        </section>
        <section v-else class="etl-inspector__empty">
          <el-icon :size="30"><SetUp /></el-icon><strong>选择一个节点</strong><p>在此设置字段、转换规则和分支条件。</p>
          <el-divider />
          <dl><dt>添加组件</dt><dd>从组件库拖入，或点击组件名称</dd><dt>连接节点</dt><dd>拖动输出端口，或点击端口后选择下游</dd><dt>移动节点</dt><dd>拖动节点；键盘可用 Alt + 方向键</dd></dl>
        </section>
      </section>
    </div>
    <el-dialog v-model="connectionOpen" title="添加分支连接" width="500px" append-to-body :close-on-click-modal="!busy" :show-close="!busy">
      <el-form label-position="top" :disabled="busy">
        <el-form-item label="流向"><el-text>{{ nodeName(connectionForm.sourceId) }} → {{ nodeName(connectionForm.targetId) }}</el-text></el-form-item>
        <el-form-item label="流转关系" required><el-select v-model="connectionForm.relationship" placeholder="选择分支关系" aria-label="连接关系"><el-option v-for="rel in sourceRelationships" :key="rel" :value="rel" :label="`${relationshipLabel(rel)} · ${rel}`" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="connectionOpen = false">取消</el-button><el-button type="primary" :loading="busy" :disabled="!connectionForm.relationship" @click="saveConnection">添加连接</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { createGovernanceDesignConnection, createGovernanceDesignNode, deleteGovernanceDesignConnection, deleteGovernanceDesignNode, getGovernanceDesign, listGovernanceDesignNodeTypes, updateGovernanceDesignNode } from '@/api/governance'
import { NODE_KINDS } from '../nodeCatalog'
import { arrangeNodes, connectionIssue, designSignature, relationshipLabel } from '../graphRules'
import { errorMessage, runState, safeDesignerPath } from '../workspaceRules'
import FlowDiagram from './FlowDiagram.vue'
import NodeConfigPanel from './NodeConfigPanel.vue'
import TestWorkbench from './TestWorkbench.vue'

const props = defineProps({ flow: Object, flows: { type: Array, default: () => [] }, templates: { type: Array, default: () => [] }, engineReady: Boolean })
const emit = defineEmits(['select-flow', 'updated'])
const { proxy } = getCurrentInstance()
const graph = ref({ nodes: [], connections: [], editable: false, issues: [] })
const loadedFlowId = ref(''), nodeTypes = ref([]), error = ref(''), loading = ref(false), busy = ref(false), saveMessage = ref('')
const search = ref(''), selectedNodeId = ref(''), selectedEdgeId = ref(''), configDirty = ref(false), configPanel = ref(), diagram = ref(), testPanel = ref()
const testOpen = ref(false), run = ref(null), trustedRun = ref(''), testedSignature = ref('')
const testSubmitting = ref(false), pendingTestSignature = ref('')
const connectionOpen = ref(false), connectionForm = reactive({ sourceId: '', targetId: '', relationship: '' })
let generation = 0, disposed = false
const canEditGraph = computed(() => props.engineReady && graph.value.editable && !testSubmitting.value && loadedFlowId.value === props.flow?.id && proxy.$auth.hasPermi('governance:flow:edit'))
const canWrite = computed(() => canEditGraph.value && !busy.value && !loading.value && !configDirty.value)
const selectedNode = computed(() => graph.value.nodes.find(n => n.id === selectedNodeId.value))
const selectedEdge = computed(() => graph.value.connections.find(e => e.id === selectedEdgeId.value))
const enginePath = computed(() => safeDesignerPath(props.flow?.designerPath))
const signature = computed(() => designSignature(graph.value))
const resultStale = computed(() => !run.value || trustedRun.value !== run.value.id || testedSignature.value !== signature.value)
const sourceRelationships = computed(() => graph.value.nodes.find(n => n.id === connectionForm.sourceId)?.relationships || [])
const groups = computed(() => [{ key: 'source', label: '数据输入' }, { key: 'transform', label: '数据转换' }, { key: 'route', label: '连接与分流' }, { key: 'output', label: '数据输出' }].map(group => ({ ...group, items: NODE_KINDS.filter(k => k.category === group.key && `${k.label} ${k.description}`.includes(search.value.trim())) })).filter(group => group.items.length))
const nodeName = id => graph.value.nodes.find(n => n.id === id)?.name || '未选择节点'
const available = kind => nodeTypes.value.some(type => type.type === kind.type && type.role === kind.role)

async function loadGraph() {
  const id = props.flow?.id, current = generation
  if (!id) return false
  loading.value = true; error.value = ''
  try {
    const response = await getGovernanceDesign(id)
    if (disposed || current !== generation || id !== props.flow?.id) return false
    graph.value = { nodes: [], connections: [], editable: false, issues: [], ...response.data }
    loadedFlowId.value = id
    if (!graph.value.nodes.some(n => n.id === selectedNodeId.value)) selectedNodeId.value = ''
    if (!graph.value.connections.some(e => e.id === selectedEdgeId.value)) selectedEdgeId.value = ''
    return true
  } catch (cause) {
    if (current === generation && !disposed) { error.value = errorMessage(cause, '流程读取失败，请刷新重试。'); graph.value.editable = false }
    return false
  } finally { if (current === generation && !disposed) loading.value = false }
}
async function confirmLeave() {
  if (busy.value || testSubmitting.value) { proxy.$modal.msgWarning('正在提交更改，请稍候。'); return false }
  if (!configDirty.value) return true
  try { await proxy.$modal.confirm('节点配置尚未保存，是否放弃修改？'); configPanel.value?.discard(); configDirty.value = false; return true } catch { return false }
}
async function refreshGraph() { if (await confirmLeave()) { await loadGraph(); trustedRun.value = '' } }
async function chooseFlow(id) { if (id !== props.flow?.id && await confirmLeave()) emit('select-flow', id) }
async function selectNode(node) { if (node.id !== selectedNodeId.value && !await confirmLeave()) return; selectedEdgeId.value = ''; selectedNodeId.value = node.id }
async function selectEdge(edge) { if (!await confirmLeave()) return; selectedNodeId.value = ''; selectedEdgeId.value = edge.id }
async function mutate(action, success) {
  if (busy.value || !canEditGraph.value) return false
  const current = generation, id = props.flow.id
  busy.value = true; error.value = ''; saveMessage.value = ''
  try {
    const response = await action(id)
    const value = response?.data ?? response
    if (disposed || current !== generation) return false
    saveMessage.value = success
    if (!await loadGraph()) { error.value = '更改已提交，但流程重新读取失败。请刷新核对，不要重复提交。'; return false }
    emit('updated')
    return value || true
  } catch (cause) {
    if (!disposed && current === generation) {
      // A stale entity is never retried with a newer version. Keep the user's form for review.
      error.value = errorMessage(cause, '保存未完成，请核对流程后重试。')
      if (!configDirty.value) { const message = error.value; await loadGraph(); error.value = message }
    }
    return false
  } finally { if (!disposed && current === generation) busy.value = false }
}
function dragKind(event, kind) {
  if (!canWrite.value || !available(kind)) { event.preventDefault(); return }
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-governance-node', kind.key)
}
async function addNode({ key, position }) {
  const kind = NODE_KINDS.find(item => item.key === key)
  if (!canWrite.value || !kind || !available(kind)) return
  if (graph.value.nodes.length >= 12) { proxy.$modal.msgWarning('当前隔离测试最多支持 12 个节点。'); return }
  const duplicates = graph.value.nodes.filter(n => n.name?.startsWith(kind.label)).length
  const value = await mutate(id => createGovernanceDesignNode(id, { type: kind.type, role: kind.role, name: `${kind.label}${duplicates ? ` ${duplicates + 1}` : ''}`, position: position || diagram.value.centerPosition(), properties: { ...kind.defaults } }), '节点已保存')
  if (value?.id) { selectedEdgeId.value = ''; selectedNodeId.value = value.id }
}
async function saveNode(data) {
  const node = selectedNode.value
  if (!node) return
  const saved = await mutate(id => updateGovernanceDesignNode(id, node.id, { version: node.version, ...data }), '配置已保存')
  if (saved) { configPanel.value?.discard(); configDirty.value = false }
}
async function moveNode({ id, position }) {
  const node = graph.value.nodes.find(n => n.id === id)
  if (!canWrite.value || !node) return
  const old = node.position
  node.position = position
  const success = await mutate(flowId => updateGovernanceDesignNode(flowId, id, { version: node.version, position }), '位置已保存')
  if (!success) node.position = old
}
async function arrange() {
  if (!canWrite.value) return
  let positions
  try { positions = arrangeNodes(graph.value.nodes, graph.value.connections) } catch (cause) { proxy.$modal.msgWarning(cause.message); return }
  await mutate(async id => {
    for (const node of positions) await updateGovernanceDesignNode(id, node.id, { version: node.version, position: node.position })
    return true
  }, '画布位置已保存')
  await nextTick(); diagram.value?.fit()
}
function connectNodes(value) {
  if (!canWrite.value) return
  const issue = connectionIssue(graph.value.nodes, graph.value.connections, value.sourceId, value.targetId)
  if (issue) { proxy.$modal.msgWarning(issue); return }
  Object.assign(connectionForm, value, { relationship: '' })
  if (sourceRelationships.value.length === 1) connectionForm.relationship = sourceRelationships.value[0]
  connectionOpen.value = true
}
async function saveConnection() {
  const issue = connectionIssue(graph.value.nodes, graph.value.connections, connectionForm.sourceId, connectionForm.targetId, connectionForm.relationship)
  if (issue) { proxy.$modal.msgWarning(issue); return }
  if (await mutate(id => createGovernanceDesignConnection(id, { ...connectionForm }), '分支连接已保存')) connectionOpen.value = false
}
async function removeEdge() {
  const edge = selectedEdge.value
  if (!edge || !canWrite.value) return
  try { await proxy.$modal.confirm(`删除“${nodeName(edge.sourceId)} → ${nodeName(edge.targetId)}”这条连接？`); } catch { return }
  await mutate(id => deleteGovernanceDesignConnection(id, edge.id, edge.version), '连接已删除')
}
async function removeNode() {
  const node = selectedNode.value
  if (!node || !canEditGraph.value || busy.value) return
  const edges = graph.value.connections.filter(e => e.sourceId === node.id || e.targetId === node.id)
  try { await proxy.$modal.confirm(`删除节点“${node.name}”及其 ${edges.length} 条连接？配置将从流程中移除。`); } catch { return }
  configPanel.value?.discard(); configDirty.value = false
  await mutate(async id => {
    for (const edge of edges) await deleteGovernanceDesignConnection(id, edge.id, edge.version)
    return deleteGovernanceDesignNode(id, node.id, node.version)
  }, '节点已删除')
}
function openTests() { testOpen.value = true; nextTick(() => testPanel.value?.showSample()) }
function previewNode() { testOpen.value = true; nextTick(() => testPanel.value?.showResult()) }
async function prepareTest() {
  if (busy.value || configDirty.value || !await loadGraph()) return false
  pendingTestSignature.value = signature.value
  return true
}
function testStarted(value) { trustedRun.value = value.id; testedSignature.value = pendingTestSignature.value }
function runChanged(value) { run.value = value }
function advancedCommand(command) { if (command === 'engine' && enginePath.value) window.open(enginePath.value, '_blank', 'noopener,noreferrer') }
function beforeUnload(event) { if (configDirty.value || busy.value || testSubmitting.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
watch(testOpen, async () => { await nextTick(); diagram.value?.fit() })
watch(() => props.flow?.id, async () => {
  ++generation; loadedFlowId.value = ''; configDirty.value = false; selectedNodeId.value = selectedEdgeId.value = ''; error.value = saveMessage.value = ''; trustedRun.value = ''; run.value = null
  graph.value = { nodes: [], connections: [], editable: false, issues: [] }
  await loadGraph()
}, { immediate: true })
listGovernanceDesignNodeTypes().then(response => { if (!disposed) nodeTypes.value = response.data || [] }).catch(() => { if (!disposed) error.value = '组件库读取失败，请刷新页面重试。' })
onBeforeUnmount(() => { disposed = true; ++generation; window.removeEventListener('beforeunload', beforeUnload) })
defineExpose({ confirmLeave, hasUnsaved: () => configDirty.value || busy.value || testSubmitting.value })
</script>

<style scoped>
.etl-designer { display: flex; flex-direction: column; height: calc(100vh - 236px); min-height: 660px; min-width: 0; border: 1px solid var(--surface-border); background: var(--surface-bg); border-radius: var(--el-border-radius-base); overflow: hidden; }
.etl-designer__toolbar { display: flex; justify-content: space-between; align-items: center; gap: var(--el-font-size-base); min-height: 58px; padding: var(--el-font-size-small) var(--el-component-size-small); border-bottom: 1px solid var(--surface-border); flex-wrap: wrap; }
.etl-designer__identity, .etl-designer__actions { display: flex; align-items: center; gap: var(--el-font-size-small); }
.etl-designer__identity > .el-select { width: 240px; }
.etl-designer__identity > .el-icon { color: var(--el-color-primary); }
.etl-designer__body { display: grid; grid-template-columns: 204px minmax(0, 1fr) 320px; min-height: 0; flex: 1; }
.etl-palette { display: flex; flex-direction: column; min-height: 0; border-right: 1px solid var(--surface-border); padding: var(--el-font-size-base); }
.etl-palette > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--el-component-size-small); }
.etl-palette__scroll { flex: 1; margin-top: var(--el-font-size-small); }
.etl-palette__group h3, .etl-palette__future h3 { margin: var(--el-font-size-base) 0 var(--el-font-size-small); font-size: var(--el-font-size-extra-small); color: var(--app-muted); font-weight: 500; }
.etl-palette__item.el-button { width: 100%; margin: 0 0 var(--el-font-size-extra-small); justify-content: flex-start; height: var(--el-component-size-large); }
.etl-palette__icon { display: inline-flex; align-items: center; margin-right: var(--el-font-size-small); color: var(--el-color-primary); }
.etl-palette__icon.is-route { color: var(--el-color-success); }
.etl-palette__icon.is-output { color: var(--el-color-warning); }
.etl-palette__add { margin-left: var(--el-font-size-small); color: var(--app-muted); }
.etl-palette__future { border-top: 1px solid var(--surface-border); margin-top: var(--el-component-size-small); color: var(--app-muted); font-size: var(--el-font-size-extra-small); line-height: 1.6; }
.etl-designer__main { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.etl-designer__canvas { display: flex; flex: 1; min-height: 260px; }
.etl-designer__canvas > .flow-diagram { flex: 1; }
.etl-designer__status { display: flex; justify-content: space-between; align-items: center; gap: var(--el-font-size-base); padding: 0 var(--el-font-size-base); min-height: var(--el-component-size-large); border-top: 1px solid var(--surface-border); }
.etl-designer__status > div { display: flex; align-items: center; gap: var(--el-font-size-base); flex-wrap: wrap; }
.etl-designer__tests { height: 290px; min-height: 210px; overflow: auto; border-top: 1px solid var(--surface-border); padding: var(--el-font-size-base); }
.etl-inspector { min-height: 0; min-width: 0; overflow: auto; border-left: 1px solid var(--surface-border); }
.etl-inspector__empty, .etl-edge-inspector { padding: var(--el-component-size-small); }
.etl-inspector__empty { display: flex; flex-direction: column; gap: var(--el-font-size-small); color: var(--app-muted); font-size: var(--el-font-size-small); }
.etl-inspector__empty > .el-icon { margin-top: var(--el-component-size-small); }
.etl-inspector__empty strong { color: var(--app-heading); font-size: var(--el-font-size-base); }
.etl-inspector__empty p, .etl-edge-inspector p { line-height: 1.7; color: var(--app-muted); font-size: var(--el-font-size-small); }
.etl-inspector__empty dl { margin: 0; }
.etl-inspector__empty dt { color: var(--app-text); margin-bottom: var(--el-font-size-extra-small); }
.etl-inspector__empty dd { margin: 0 0 var(--el-component-size-small); line-height: 1.6; }
.etl-edge-inspector > header { display: flex; justify-content: space-between; margin-bottom: var(--el-component-size-small); }
@media (max-width: 1280px) { .etl-designer__body { grid-template-columns: 172px minmax(0, 1fr) 288px; } .etl-designer__actions { gap: 6px; } .etl-designer__actions > .el-text, .etl-palette__add { display: none; } }
@media (max-width: 1000px) { .etl-designer { height: auto; min-height: 720px; } .etl-designer__body { grid-template-columns: 156px minmax(0, 1fr); } .etl-inspector { grid-column: 1 / -1; border-left: 0; border-top: 1px solid var(--surface-border); max-height: 360px; } .etl-designer__canvas { min-height: 400px; } .etl-designer__toolbar { padding: var(--el-font-size-small); } }
@media (max-width: 640px) { .etl-designer__body { grid-template-columns: 128px minmax(0, 1fr); } .etl-palette { padding: var(--el-font-size-extra-small); } .etl-palette__add, .etl-palette header > .el-text { display: none; } .etl-designer__identity > .el-select { width: 200px; } .etl-designer__status { flex-wrap: wrap; } }
</style>
