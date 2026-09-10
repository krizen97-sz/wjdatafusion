<template>
  <section class="kettle-workbench" aria-label="Kettle数据开发工作台" v-loading="loading">
    <header class="kettle-toolbar">
      <el-space wrap><strong>数据开发</strong><el-tag :type="engineReady ? 'success' : 'info'">{{ engineReady ? 'Kettle已连接' : 'Kettle未连接' }}</el-tag><el-text v-if="definition" type="info">{{ graph.name }} · {{ graph.kind === 'job' ? '作业' : '转换' }} · V{{ definition.revision }}</el-text><el-text v-if="dirty || configDirty" type="warning">有未保存修改</el-text></el-space>
      <el-space wrap>
        <el-button icon="Refresh" :loading="loading" :disabled="saving || starting" @click="load">刷新服务</el-button>
        <el-dropdown v-if="canEdit" @command="openNew"><el-button icon="Plus" :disabled="busy">新建任务</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="transformation">新建转换</el-dropdown-item><el-dropdown-item command="job">新建作业</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        <el-upload v-if="canEdit" action="#" :show-file-list="false" :http-request="importFile" accept=".ktr,.kjb,.zip" :disabled="busy"><el-button icon="Upload" :disabled="busy">导入Kettle文件</el-button></el-upload>
        <el-button v-if="canEdit" icon="DocumentChecked" :loading="saving" :disabled="!definition || busy || (!dirty && !configDirty)" @click="save">保存</el-button>
        <el-button v-if="canRun" icon="View" :disabled="!selectedNode || !engineReady || busy || activeRun || reviewRun || graph.kind === 'job'" @click="execute('preview')">{{ dirty || configDirty ? '保存并预览' : '预览节点' }}</el-button>
        <el-button v-if="canRun" type="primary" icon="VideoPlay" :disabled="!definition || !engineReady || busy || activeRun || reviewRun" :loading="starting" @click="execute('run')">{{ dirty || configDirty ? '保存并运行' : '运行任务' }}</el-button>
        <el-button v-if="(activeRun || reviewRun) && run?.id && canRun" type="danger" plain icon="VideoPause" :loading="stopping" @click="stop">停止运行</el-button>
      </el-space>
    </header>
    <el-alert v-if="submissionRevisionExpired" title="原提交版本已过期，已确认没有执行该请求" type="warning" :closable="false"><el-button @click="openDefinition(definition.id, true)">重新读取任务</el-button></el-alert>
    <el-alert v-if="pendingSubmission" title="上次提交尚未确认，已保留原请求标识" type="warning" :closable="false"><el-space><el-button :loading="starting" @click="checkSubmission">核查提交记录</el-button><el-button v-if="canRun" :loading="starting" @click="retrySubmission">重试原提交</el-button></el-space></el-alert>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="true" @close="error = ''" />
    <el-alert v-if="!engineReady && !loading" title="配置原Kettle执行服务后，可进行字段获取、节点预览和运行" type="info" :closable="false" />
    <div class="kettle-body">
      <aside class="kettle-library" aria-label="任务与Kettle工具">
        <el-tabs v-model="leftTab" class="motion-tabs">
          <el-tab-pane label="任务" name="tasks"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">任务</span></span></template>
            <el-input v-model="taskSearch" clearable placeholder="搜索转换或作业" aria-label="搜索任务" prefix-icon="Search" />
            <el-scrollbar class="kettle-library__scroll">
              <section v-for="kind in taskKinds" :key="kind.value"><h3>{{ kind.label }}</h3><el-button v-for="item in filteredDefinitions.filter(row => row.kind === kind.value)" :key="item.id" text :type="definition?.id === item.id ? 'primary' : ''" class="kettle-task" :title="`${item.name} · ${item.nodeCount}个节点 · V${item.revision}`" @click="openDefinition(item.id)"><el-icon><component :is="item.kind === 'job' ? 'Connection' : 'SetUp'" /></el-icon><span>{{ item.name }} · {{ item.nodeCount }}节点</span></el-button></section>
              <el-empty v-if="!filteredDefinitions.length" description="新建任务，或导入磁盘中的KTR / KJB" :image-size="48" />
            </el-scrollbar>
          </el-tab-pane>
          <el-tab-pane label="工具" name="tools"><template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">工具</span></span></template>
            <el-input v-model="toolSearch" clearable placeholder="搜索输入、处理、输出工具" aria-label="搜索Kettle工具" prefix-icon="Search" />
            <el-scrollbar class="kettle-library__scroll">
              <el-collapse v-model="expandedGroups">
                <el-collapse-item v-for="group in toolGroups" :key="group.name" :name="group.name" :title="`${group.name} · ${group.items.length}`">
                  <el-button v-for="plugin in group.items" :key="pluginKey(plugin)" class="kettle-tool" :draggable="Boolean(canEdit && plugin.executable && definition && !busy)" :disabled="!canEdit || !plugin.executable || !definition || busy" :title="plugin.loadable ? plugin.executable ? (plugin.label || plugin.name) : `${plugin.label || plugin.name}：尚未开放执行` : `${plugin.label || plugin.name}：原插件依赖未就绪`" @dragstart="dragPlugin($event, plugin)" @click="insertPlugin(plugin)"><el-icon><component :is="presentation({ pluginId: plugin.id }).icon" /></el-icon><span>{{ plugin.label || plugin.name || plugin.id }}</span></el-button>
                </el-collapse-item>
              </el-collapse>
            </el-scrollbar>
            <el-text type="info" size="small">工具来自磁盘原包；灰色项缺少依赖或尚未开放执行。</el-text>
          </el-tab-pane>
        </el-tabs>
      </aside>
      <main class="kettle-main">
        <nav class="kettle-canvas-toolbar" v-if="definition"><el-space wrap><el-tag effect="plain">{{ graph.kind === 'job' ? '作业：按条件执行任务' : '转换：逐行传递数据' }}</el-tag><el-button link icon="Sort" :disabled="!canEdit || busy || configDirty" @click="arrange">整理画布</el-button><el-button link icon="CircleCheck" :disabled="busy || !engineReady" :loading="validating" @click="validate">{{ graph.kind === 'job' ? '检查作业结构' : '获取字段' }}</el-button><el-button link icon="Setting" :disabled="busy" @click="openTaskSettings">任务设置</el-button><el-button link icon="Files" @click="assetsOpen = true">输入文件</el-button><el-button link icon="Coin" @click="openConnections">数据库连接</el-button><el-button link icon="Timer" @click="schedulesOpen = true">定时任务</el-button><el-button link icon="Clock" @click="openHistory">运行历史</el-button><el-button link icon="Download" @click="exportDefinition">导出</el-button></el-space></nav>
        <div class="kettle-canvas">
          <FlowDiagram v-if="definition" ref="diagram" :nodes="graph.nodes" :connections="graph.connections" :selected-node-id="selectedNodeId" :selected-edge-id="selectedEdgeId" :editable="canEdit && !busy && !configDirty && !activeRun && !reviewRun" :presentation="presentation" :summary="nodeSummary" :source-predicate="isSource" :run="diagramRun" :result-stale="dirty || configDirty || run?.revision !== definition?.revision" show-auxiliary @select-node="selectNode" @select-edge="selectEdge" @move-node="moveNode" @add-node="dropPlugin" @connect="connectNodes" />
          <el-empty v-else description="选择一个任务，开始编排实际输入、处理和输出"><el-button type="primary" @click="openNew('transformation')">新建转换</el-button></el-empty>
        </div>
        <footer class="kettle-canvas-status"><el-text type="info" size="small">{{ graph.nodes.length }} 个节点 · {{ graph.connections.length }} 条连接</el-text><el-button link :icon="resultsOpen ? 'ArrowDown' : 'ArrowUp'" @click="resultsOpen = !resultsOpen">预览、指标与日志</el-button></footer>
        <KettleResults v-if="resultsOpen" :run="run" :stale="dirty || configDirty || run?.revision !== definition?.revision" :events="events" :selected-node="previewNodeName" :schema="selectedFields" @select-node="choosePreviewNode" @download="downloadOutput" @close="resultsOpen = false" />
      </main>
      <aside class="kettle-inspector" aria-label="原插件节点配置">
        <el-scrollbar class="kettle-inspector__scroll">
          <KettleNodeConfig v-if="selectedNode && !inspectorExpanded" :key="`${selectedNode.id}-${editorGeneration}`" ref="nodeEditor" :node="selectedNode" :plugin="selectedPlugin" :kind="graph.kind" :title="presentation(selectedNode).label" :context="formContext" :readonly="!canEdit" :can-run="canRun" :commit="applyNode" @dirty-change="configDirty = $event" @preview="execute('preview')" @remove="deleteNode" @expand="expandInspector" />
          <section v-else-if="selectedEdge" class="kettle-edge-config"><h3>连接配置</h3><el-descriptions :column="1"><el-descriptions-item label="上游">{{ nodeName(selectedEdge.sourceId) }}</el-descriptions-item><el-descriptions-item label="下游">{{ nodeName(selectedEdge.targetId) }}</el-descriptions-item></el-descriptions><el-form label-position="top" :disabled="!canEdit || busy || activeRun"><el-form-item label="启用连接"><el-switch :model-value="selectedEdge.enabled" @update:model-value="setEdgeEnabled" /></el-form-item><el-form-item v-if="graph.kind === 'job'" label="执行条件"><el-select :model-value="edgeCondition" @update:model-value="setEdgeCondition"><el-option label="无条件" value="unconditional" /><el-option label="上游成功" value="success" /><el-option label="上游失败" value="failure" /></el-select></el-form-item></el-form><el-button type="danger" plain :disabled="!canEdit || busy || activeRun" @click="deleteEdge">删除连接</el-button></section>
          <el-empty v-else description="选择节点，配置原工具参数；选中后可预览该节点的真实数据" :image-size="64" />
          <el-alert v-if="selectedValidation?.fieldError" :title="selectedValidation.fieldError" type="warning" :closable="false" class="mt16" />
        </el-scrollbar>
      </aside>
    </div>
    <el-dialog v-model="taskSettingsOpen" title="任务设置" width="min(92vw, 980px)" append-to-body destroy-on-close>
      <el-form label-position="top" :disabled="!canEdit"><el-form-item label="任务名称" required><el-input v-model="taskName" maxlength="100" /></el-form-item><el-form-item label="执行时区"><el-select v-model="taskTimeZone" filterable allow-create default-first-option><el-option value="Asia/Shanghai" label="北京时间（Asia/Shanghai）" /><el-option value="UTC" label="协调世界时（UTC）" /></el-select></el-form-item><el-text type="info">日期、时间及文件名使用此时区；作业中的子转换继承作业时区。</el-text></el-form>
      <el-divider content-position="left">默认参数</el-divider>
      <el-text type="info">节点中使用 ${参数名} 引用默认值；定时计划保存时会一并冻结。</el-text>
      <el-button v-if="canEdit" icon="Plus" class="ml16" @click="addParameter">新增参数</el-button>
      <el-table :data="taskParameters" class="mt16" empty-text="尚未配置默认参数"><el-table-column v-for="field in [{key:'name',label:'参数名'},{key:'default_value',label:'默认值'},{key:'description',label:'说明'}]" :key="field.key" :label="field.label" min-width="190"><template #default="{row}"><el-input :disabled="!canEdit" :aria-label="field.label" :model-value="parameterRead(row,field.key)" @update:model-value="parameterWrite(row,field.key,$event)" /></template></el-table-column><el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="danger" :disabled="!canEdit" @click="removeParameter(row)">删除</el-button></template></el-table-column></el-table>
      <template #footer><el-button @click="taskSettingsOpen = false">取消</el-button><el-button v-if="canEdit" type="primary" :loading="saving" @click="applyTaskSettings">应用任务设置</el-button></template>
    </el-dialog>
    <el-dialog v-model="inspectorExpanded" title="节点详细配置" width="min(94vw, 1280px)" append-to-body destroy-on-close :before-close="closeInspector"><KettleNodeConfig v-if="selectedNode && inspectorExpanded" :key="`${selectedNode.id}-${editorGeneration}`" ref="nodeEditor" :node="selectedNode" :plugin="selectedPlugin" :kind="graph.kind" :title="presentation(selectedNode).label" :context="formContext" :readonly="!canEdit" :can-run="canRun" :commit="applyNode" @dirty-change="configDirty = $event" @preview="execute('preview')" @remove="deleteNode" expanded /></el-dialog>
    <el-dialog v-model="newOpen" :title="newKind === 'job' ? '新建Kettle作业' : '新建Kettle转换'" width="500px" append-to-body><el-form label-position="top" @submit.prevent="create"><el-form-item label="任务名称" required><el-input v-model="newName" maxlength="100" autofocus @keyup.enter="create" /></el-form-item></el-form><template #footer><el-button @click="newOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!newName.trim()" @click="create">创建</el-button></template></el-dialog>
    <el-dialog v-model="hopOpen" title="连接执行条件" width="500px" append-to-body><el-form label-position="top"><el-form-item v-if="graph.kind === 'job'" label="上游节点结束后"><el-radio-group v-model="hopCondition"><el-radio value="success">成功时执行</el-radio><el-radio value="failure">失败时执行</el-radio><el-radio value="unconditional">无条件执行</el-radio></el-radio-group></el-form-item><el-form-item v-else :label="hopSource?.pluginId === 'FilterRows' ? '条件结果' : '匹配方式'"><el-radio-group v-model="hopCondition"><template v-if="hopSource?.pluginId === 'FilterRows'"><el-radio value="true">满足条件</el-radio><el-radio value="false">不满足条件</el-radio></template><template v-else><el-radio value="case">匹配值</el-radio><el-radio value="default">默认分支</el-radio></template></el-radio-group></el-form-item><el-form-item v-if="hopCondition === 'case'" label="匹配值"><el-input v-model="hopValue" /></el-form-item></el-form><template #footer><el-button @click="hopOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="busy" @click="saveHop">添加连接</el-button></template></el-dialog>
    <el-drawer v-model="assetsOpen" title="任务输入文件" size="640px" append-to-body>
      <el-alert title="上传实际数据文件后，在输入节点的文件选择框中选用" description="每次执行将这些文件复制到独立输入目录，与输出文件分开保存。预览采样不会限制正式运行的行数。" type="info" :closable="false" class="mb16" />
      <el-upload action="#" :http-request="uploadFile" :show-file-list="false" :disabled="!definition || busy || !canEdit"><el-button type="primary" icon="Upload">上传输入文件</el-button></el-upload>
      <el-table :data="files" class="mt16" empty-text="尚未上传输入文件"><el-table-column prop="name" label="文件" min-width="200" /><el-table-column label="字节数" width="120"><template #default="{ row }">{{ row.bytes ?? row.size }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="danger" :disabled="!canEdit || busy" @click="removeFile(row)">移除</el-button></template></el-table-column></el-table>
    </el-drawer>
    <el-drawer v-model="schedulesOpen" title="Kettle定时任务" size="85%" append-to-body destroy-on-close><KettleSchedules v-if="schedulesOpen && definition" :key="definition.id" :definition="definition" :can-edit="canEdit" :can-run="canRun" :before-save="save" @view-run="schedulesOpen = false; viewRun($event)" /></el-drawer>
    <el-drawer v-model="historyOpen" title="任务运行历史" size="780px" append-to-body>
      <el-button icon="Refresh" :loading="historyLoading" @click="loadHistory">刷新</el-button>
      <el-table :data="history" class="mt16" empty-text="此任务尚无运行记录" v-loading="historyLoading"><el-table-column label="开始时间" min-width="180"><template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template></el-table-column><el-table-column label="方式" width="90"><template #default="{ row }">{{ row.mode === 'preview' ? '预览' : '运行' }}</template></el-table-column><el-table-column label="版本" prop="revision" width="80" /><el-table-column label="状态" min-width="150"><template #default="{ row }"><el-tag :type="runStatus(row.state).type">{{ runStatus(row.state).label }}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" @click="viewRun(row)">查看结果</el-button></template></el-table-column></el-table>
    </el-drawer>
    <el-drawer v-model="connectionsOpen" title="任务数据库连接" size="720px" append-to-body>
      <el-button type="primary" icon="Plus" :disabled="!definition || configDirty || !canEdit" @click="addConnection">新建连接</el-button>
      <el-table :data="databaseConnections" class="mt16" empty-text="暂无连接；数据库输入、查询和输出共用这里的连接配置"><el-table-column prop="name" label="连接名称" min-width="150" /><el-table-column prop="type" label="类型" width="140" /><el-table-column label="操作" width="160"><template #default="{ row }"><el-button link type="primary" @click="editConnection(row)">配置</el-button><el-button link type="danger" :disabled="!canEdit || busy" @click="removeConnection(row)">删除</el-button></template></el-table-column></el-table>
      <el-form v-if="connectionDraft" label-position="top" class="mt16" :disabled="!canEdit || busy"><el-form-item v-for="field in connectionSchema" :key="field.key" :label="field.label"><KettleValueInput :field="field" :context="{ readonly: !canEdit || busy }" :model-value="connectionRead(field.key)" @update:model-value="connectionWrite(field.key, $event)" /></el-form-item><el-button type="primary" @click="applyConnection">应用连接配置</el-button></el-form>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, getCurrentInstance, markRaw, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { saveAs } from 'file-saver'
import * as api from '@/api/governance/kettle'
import FlowDiagram from '../components/FlowDiagram.vue'
import KettleNodeConfig from './KettleNodeConfig.vue'
import KettleResults from './KettleResults.vue'
import KettleSchedules from './KettleSchedules.vue'
import KettleValueInput from './KettleValueInput.vue'
import { addNode, appendRow, at, connect, direct, elementId, emptyDocument, fromBase64, graphFromXml, parseXml, rowsAt, removeConnection as removeGraphConnection, removeNode, renameNode, syncBranchConnections, setPosition, setText, textAt, toBase64, xmlText } from './xmlModel'
import { connectionSchema, presentationFor, schemaFor } from './nodeSchemas'
import { jobNodeStates, mergeEvents, runActive, runFinishing, runNeedsReview, runStatus } from './runRules'
import { arrangeNodes, NODE_HEIGHT, NODE_WIDTH, POSITION_SCALE } from '../graphRules'
import { errorMessage } from '../workspaceRules'
const { proxy } = getCurrentInstance()
const canEdit = computed(() => proxy.$auth.hasPermi('governance:flow:edit'))
const canRun = computed(() => proxy.$auth.hasPermi('governance:flow:test'))
const definitions = ref([]), definition = ref(null), document = ref(null), version = ref(0), editorGeneration = ref(0)
const catalog = ref({ steps: [], jobs: [] }), status = ref({}), loading = ref(false), saving = ref(false), starting = ref(false), stopping = ref(false), validating = ref(false), error = ref('')
const dirty = ref(false), configDirty = ref(false), leftTab = ref('tasks'), taskSearch = ref(''), toolSearch = ref(''), selectedNodeId = ref(''), selectedEdgeId = ref(''), nodeEditor = ref(), diagram = ref()
const expandedGroups = ref(['输入', '处理', '输出', '作业']), files = ref([]), assetsOpen = ref(false), connectionsOpen = ref(false), connectionDraft = ref(null), connectionId = ref(''), connectionVersion = ref(0)
const run = ref(null), events = ref([]), resultsOpen = ref(false), previewNodeName = ref(''), validation = ref(null), newOpen = ref(false), newKind = ref('transformation'), newName = ref('')
const taskSettingsOpen = ref(false), taskDraft = ref(null), taskName = ref(''), taskTimeZone = ref('Asia/Shanghai'), taskDraftVersion = ref(0)
const taskParameters = computed(() => { taskDraftVersion.value; return taskDraft.value ? rowsAt(taskDraft.value.documentElement, graph.value.kind === 'job' ? 'parameters/parameter' : 'info/parameters/parameter').map(markRaw) : [] })
const inspectorExpanded = ref(false)
const schedulesOpen = ref(false)
const history = ref([]), historyOpen = ref(false), historyLoading = ref(false)
const hopOpen = ref(false), hopCondition = ref('success'), hopValue = ref(''), pendingHop = ref(null)
const hopSource = computed(() => graph.value.nodes.find(node => node.id === pendingHop.value?.sourceId))
const taskKinds = [{ value: 'transformation', label: '转换' }, { value: 'job', label: '作业' }]
let disposed = false, requestGeneration = 0, pollTimer, cursor = 0
const uploading = ref(false)
const busy = computed(() => loading.value || saving.value || starting.value || validating.value || uploading.value)
const engineReady = computed(() => status.value.enabled === true && status.value.executionAvailable === true)
const activeRun = computed(() => runActive(run.value?.state) || runFinishing(run.value))
const pendingSubmission = ref(null), submissionRevisionExpired = ref(false)
const reviewRun = computed(() => Boolean(pendingSubmission.value) || runNeedsReview(run.value))
const graph = computed(() => { version.value; return document.value ? graphFromXml(document.value) : { kind: 'transformation', nodes: [], connections: [], name: '' } })
const selectedNode = computed(() => graph.value.nodes.find(node => node.id === selectedNodeId.value))
const selectedPlugin = computed(() => { const matches = item => item.id === selectedNode.value?.pluginId || item.aliases?.includes(selectedNode.value?.pluginId); return entries.value.find(item => matches(item) && item.loadable) || entries.value.find(matches) })
const selectedEdge = computed(() => graph.value.connections.find(edge => edge.id === selectedEdgeId.value))
const entries = computed(() => graph.value.kind === 'job' ? catalog.value.jobs || [] : catalog.value.steps || [])
const filteredDefinitions = computed(() => definitions.value.filter(item => item.name.includes(taskSearch.value.trim())))
const toolGroups = computed(() => {
  const groups = new Map()
  for (const plugin of entries.value) {
    if (toolSearch.value && !`${plugin.id} ${plugin.name} ${plugin.label}`.toLowerCase().includes(toolSearch.value.toLowerCase())) continue
    let group = schemaFor(plugin.id).group
    if (group === '其他') { const category = String(plugin.categoryLabel || plugin.category || ''); group = graph.value.kind === 'job' ? '作业' : /Input|输入/i.test(category) ? '输入' : /Output|输出/i.test(category) ? '输出' : '处理' }
    if (!groups.has(group)) groups.set(group, [])
    groups.get(group).push(plugin)
  }
  return ['输入', '处理', '输出', '作业', '其他工具'].filter(name => groups.has(name)).map(name => ({ name, items: groups.get(name) }))
})
const pluginKey = plugin => plugin.catalogKey || `${plugin.kind}:${plugin.id}:${plugin.className}`
const presentation = node => presentationFor(node, [...catalog.value.steps || [], ...catalog.value.jobs || []])
const nodeSummary = node => node.pluginId === 'CsvInput' ? textAt(node.element, 'filename').split('/').pop() || '选择实际输入文件' : node.pluginId === 'TableInput' ? textAt(node.element, 'connection') || '选择数据库连接' : presentation(node).label
const isSource = node => node.pluginId === 'SPECIAL' && textAt(node.element, 'start') === 'Y'
const nodeName = id => graph.value.nodes.find(node => node.id === id)?.name || ''
const selectedValidation = computed(() => (validation.value?.nodes || []).find(node => node.name === (previewNodeName.value || selectedNode.value?.name)))
const selectedFields = computed(() => selectedValidation.value?.fields || [])
const databaseConnections = computed(() => { version.value; return document.value ? direct(document.value.documentElement, 'connection').map(element => ({ id: elementId(element), name: textAt(element, 'name'), type: textAt(element, 'type'), element })) : [] })
const inputFields = computed(() => {
  const native = (validation.value?.nodes || []).find(node => node.name === selectedNode.value?.name)
  if (native?.inputFields) return native.inputFields
  const incoming = graph.value.connections.filter(edge => edge.enabled && edge.targetId === selectedNodeId.value).map(edge => nodeName(edge.sourceId))
  return (validation.value?.nodes || []).filter(node => incoming.includes(node.name)).flatMap(node => node.fields || [])
})
const formContext = computed(() => ({ nodes: graph.value.nodes.map(node => node.name), definitions: definitions.value, readonly: !canEdit.value, files: files.value, connections: databaseConnections.value.map(item => item.name), fields: [...new Set(inputFields.value.map(field => field.name))] }))
const edgeCondition = computed(() => selectedEdge.value ? textAt(selectedEdge.value.element, 'unconditional') === 'Y' ? 'unconditional' : textAt(selectedEdge.value.element, 'evaluation') === 'Y' ? 'success' : 'failure' : '')
const diagramRun = computed(() => run.value ? { ...run.value, status: run.value.state, steps: graph.value.nodes.map(node => {
  const metrics = (graph.value.kind === 'job' ? jobNodeStates(events.value, run.value.state) : run.value.nodes || []).filter(item => item.node === node.name && (graph.value.kind !== 'job' || String(item.copy ?? 0) === node.copyNr))
  if (graph.value.kind === 'job') return metrics.length ? { id: node.id, status: metrics[metrics.length - 1].status } : null
  return metrics.length ? { id: node.id, status: metrics.some(item => Number(item.errors) > 0) ? 'FAILED' : activeRun.value ? 'RUNNING' : run.value.state === 'SUCCEEDED' || run.value.state === 'PREVIEW_COMPLETE' ? 'SUCCEEDED' : run.value.state } : null
}).filter(Boolean) } : null)
function openTaskSettings() { if (configDirty.value && nodeEditor.value?.apply() === false) return; taskDraft.value = markRaw(document.value.cloneNode(true)); taskName.value = graph.value.name; taskTimeZone.value = document.value.documentElement.getAttribute('data-rynew-timezone') || 'Asia/Shanghai'; taskDraftVersion.value++; taskSettingsOpen.value = true }
function parameterRead(row, key) { taskDraftVersion.value; return textAt(row, key) }
function parameterWrite(row, key, value) { setText(row, key, value); taskDraftVersion.value++ }
function addParameter() { appendRow(taskDraft.value.documentElement, graph.value.kind === 'job' ? 'parameters/parameter' : 'info/parameters/parameter', { name: '', default_value: '', description: '' }); taskDraftVersion.value++ }
function removeParameter(row) { row.remove(); taskDraftVersion.value++ }
function applyTaskSettings() {
  const name = taskName.value.trim(), names = taskParameters.value.map(row => textAt(row, 'name').trim())
  if (!name || names.some(value => !value) || new Set(names).size !== names.length || names.some(value => ['WORK_DIR', 'INPUT_DIR', 'JOB_DIR'].includes(value))) { proxy.$modal.msgWarning('任务与参数名称不能为空，参数不能重名或使用执行服务的保留名称'); return }
  try { new Intl.DateTimeFormat('zh-CN', { timeZone: taskTimeZone.value.trim() }).format(0) } catch { proxy.$modal.msgWarning('请选择有效的执行时区'); return }
  taskDraft.value.documentElement.setAttribute('data-rynew-timezone', taskTimeZone.value.trim())
  for (const row of taskParameters.value) setText(row, 'name', textAt(row, 'name').trim())
  setText(taskDraft.value.documentElement, graph.value.kind === 'job' ? 'name' : 'info/name', name); document.value = markRaw(taskDraft.value); editorGeneration.value++; changed(); taskSettingsOpen.value = false
}
function expandInspector() { if (configDirty.value && nodeEditor.value?.apply() === false) return; inspectorExpanded.value = true }
function closeInspector(done) { if (configDirty.value && nodeEditor.value?.apply() === false) return; done() }
function changed() { version.value++; dirty.value = true; validation.value = null }
function setDocument(detail) { definition.value = detail; document.value = markRaw(parseXml(fromBase64(detail.xmlBase64))); version.value++; editorGeneration.value++; dirty.value = false; configDirty.value = false; selectedNodeId.value = ''; selectedEdgeId.value = ''; validation.value = null; pendingSubmission.value = readPending(detail.id); submissionRevisionExpired.value = false }
async function leaveDraft() {
  if (busy.value) { proxy.$modal.msgWarning('请等待本次提交结束'); return false }
  if (!dirty.value && !configDirty.value) return true
  try { await proxy.$modal.confirm('当前任务有未保存修改，是否放弃后切换？'); return true } catch { return false }
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const [state, list] = await Promise.all([api.kettleStatus(), api.kettleDefinitions()]); if (disposed) return
    status.value = state.data; definitions.value = list.data || []
    const data = await api.kettleCatalog(); if (!disposed) catalog.value = data.data || { steps: [], jobs: [] }
  } catch (cause) { if (!disposed) error.value = errorMessage(cause, '工作台加载失败') }
  finally { if (!disposed) loading.value = false }
}
async function openDefinition(id, force = false) {
  if (!force && definition.value?.id === id || !await leaveDraft()) return
  const generation = ++requestGeneration; loading.value = true; error.value = ''
  try { const [detail, inputs, previous] = await Promise.all([api.kettleDefinition(id), api.kettleInputFiles(id), api.kettleRunHistory(id)]); if (disposed || generation !== requestGeneration) return; setDocument(detail.data); files.value = inputs.data || []; history.value = previous.data || []; run.value = null; events.value = []; previewNodeName.value = ''; clearTimeout(pollTimer); const pendingRun = pendingSubmission.value && history.value.find(item => item.requestId === pendingSubmission.value.requestId); if (pendingRun) { clearPending(); await viewRun(pendingRun) } const recent = history.value[0]; if (!pendingRun && recent && (runActive(recent.state) || recent.state === 'SUBMISSION_UNKNOWN' || recent.state === 'RECOVERY_REQUIRED')) await viewRun(recent); await nextTick(); diagram.value?.fit() }
  catch (cause) { error.value = errorMessage(cause, '任务读取失败') } finally { loading.value = false }
}
async function openNew(kind) { if (!await leaveDraft()) return; newKind.value = kind; newName.value = ''; newOpen.value = true }
async function create() {
  if (!newName.value.trim() || saving.value) return; saving.value = true; error.value = ''
  try { const xml = xmlText(emptyDocument(newKind.value, newName.value.trim())); const result = await api.createKettleDefinition({ name: newName.value.trim(), xmlBase64: toBase64(xml) }); setDocument(result.data); definitions.value = (await api.kettleDefinitions()).data; newOpen.value = false; leftTab.value = 'tools'; files.value = []; run.value = null; events.value = [] }
  catch (cause) { error.value = errorMessage(cause, '创建失败') } finally { saving.value = false }
}
async function save() {
  if (!definition.value || saving.value) return false
  if (!canEdit.value) return !dirty.value && !configDirty.value
  if (configDirty.value && nodeEditor.value?.apply() === false) return false
  if (!dirty.value) return true
  saving.value = true; error.value = ''; const selected = selectedNodeId.value
  try { const result = await api.saveKettleDefinition(definition.value.id, { name: graph.value.name, revision: definition.value.revision, xmlBase64: toBase64(xmlText(document.value)) }); setDocument(result.data); selectedNodeId.value = selected; definitions.value = (await api.kettleDefinitions()).data; return true }
  catch (cause) { error.value = errorMessage(cause, '保存失败，请保留当前修改并核对版本'); return false } finally { saving.value = false }
}
async function importFile(options) {
  if (!await leaveDraft()) { options.onError?.(new Error('已取消导入')); return }
  loading.value = true; error.value = ''
  try { const data = new FormData(); data.append('file', options.file); const result = await api.importKettleDefinitions(data); definitions.value = (await api.kettleDefinitions()).data; const imported = Array.isArray(result.data) ? result.data : result.data.definitions || []; if (imported.length) { const detail = await api.kettleDefinition(imported[0].id); setDocument(detail.data); files.value = [] } options.onSuccess?.(result); proxy.$modal.msgSuccess(`已导入${imported.length}个任务`) }
  catch (cause) { error.value = errorMessage(cause, '导入失败，未执行文件中的任务'); options.onError?.(cause) } finally { loading.value = false }
}
function dragPlugin(event, plugin) { if (configDirty.value && nodeEditor.value?.apply() === false) { event.preventDefault(); return } event.dataTransfer.effectAllowed = 'copy'; event.dataTransfer.setData('application/x-governance-node', pluginKey(plugin)) }
async function insertPlugin(plugin, position) {
  if (!canEdit.value || !document.value || !plugin.executable || busy.value) return
  if (configDirty.value && nodeEditor.value?.apply() === false) return
  try {
    let location = position || diagram.value?.centerPosition() || { x: 120, y: 120 }
    if (!position) {
      const strideX = (NODE_WIDTH + 64) * POSITION_SCALE, strideY = (NODE_HEIGHT + 40) * POSITION_SCALE
      for (let attempt = 0; graph.value.nodes.some(node => Math.abs(node.position.x - location.x) < NODE_WIDTH * POSITION_SCALE && Math.abs(node.position.y - location.y) < NODE_HEIGHT * POSITION_SCALE); attempt++) location = { x: location.x + strideX, y: location.y + (attempt % 4 === 3 ? strideY : 0) }
    }
    selectedNodeId.value = addNode(document.value, plugin, location); selectedEdgeId.value = ''; changed()
    if (!position) { await nextTick(); diagram.value?.fit() }
  } catch (cause) { error.value = cause.message }
}
function dropPlugin({ key, position }) { const plugin = entries.value.find(item => pluginKey(item) === key); if (plugin) insertPlugin(plugin, position) }
async function selectNode(node) { if (configDirty.value && nodeEditor.value?.apply() === false) return; selectedNodeId.value = node.id; selectedEdgeId.value = ''; previewNodeName.value = node.name }
function selectEdge(edge) { if (configDirty.value && nodeEditor.value?.apply() === false) return; selectedNodeId.value = ''; selectedEdgeId.value = edge.id }
function applyNode({ element, name }) {
  const node = selectedNode.value; if (!node) return false
  try {
    const candidate = document.value.cloneNode(true), candidateNode = graphFromXml(candidate).nodes.find(item => item.id === node.id)
    renameNode(candidate, candidateNode, name)
    const replacement = candidate.importNode(element, true); setText(replacement, 'name', name.trim())
    for (const axis of ['xloc', 'yloc']) { const path = graph.value.kind === 'job' ? axis : `GUI/${axis}`; setText(replacement, path, textAt(candidateNode.element, path)) }
    candidateNode.element.replaceWith(replacement)
    syncBranchConnections(candidate, { ...candidateNode, name: name.trim(), element: replacement })
    document.value = markRaw(candidate); changed(); return true
  } catch (cause) { error.value = cause.message; return false }
}
function moveNode({ id, position }) { const node = graph.value.nodes.find(item => item.id === id); if (node) { setPosition(document.value, node, position); changed() } }
async function arrange() { if (!document.value || configDirty.value) return; try { const arranged = arrangeNodes(graph.value.nodes, graph.value.connections); for (const node of arranged) setPosition(document.value, node, node.position); changed(); await nextTick(); diagram.value?.fit() } catch (cause) { error.value = cause.message } }
function connectNodes(value) {
  const source = graph.value.nodes.find(node => node.id === value.sourceId)
  if (graph.value.kind === 'job' || ['FilterRows', 'SwitchCase'].includes(source?.pluginId)) { pendingHop.value = value; hopCondition.value = graph.value.kind === 'job' ? 'success' : source.pluginId === 'FilterRows' ? 'true' : 'case'; hopValue.value = ''; hopOpen.value = true } else saveHop(value)
}
function saveHop(value) {
  const hop = pendingHop.value || value; if (!hop?.sourceId || !canEdit.value) return
  try {
    const source = graph.value.nodes.find(node => node.id === hop.sourceId), target = graph.value.nodes.find(node => node.id === hop.targetId)
    if (source?.id === target?.id) throw new Error('分支不能指向自身')
    if (source?.pluginId === 'FilterRows') { setText(source.element, hopCondition.value === 'true' ? 'send_true_to' : 'send_false_to', target.name); syncBranchConnections(document.value, source) }
    else if (source?.pluginId === 'SwitchCase') { if (hopCondition.value === 'default') setText(source.element, 'default_target_step', target.name); else appendRow(source.element, 'cases/case', { value: hopValue.value, target_step: target.name }); syncBranchConnections(document.value, source) }
    else connect(document.value, hop.sourceId, hop.targetId, hopCondition.value)
    changed(); selectedEdgeId.value = graph.value.connections.find(edge => edge.sourceId === hop.sourceId && edge.targetId === hop.targetId)?.id || ''; selectedNodeId.value = ''; editorGeneration.value++; hopOpen.value = false; pendingHop.value = null
  } catch (cause) { error.value = cause.message }
}
async function deleteNode() { const node = selectedNode.value; if (!node) return; try { await proxy.$modal.confirm(`删除“${node.name}”及相关连接？`); removeNode(document.value, node); selectedNodeId.value = ''; configDirty.value = false; changed() } catch { } }
function deleteEdge() { if (selectedEdge.value) { removeGraphConnection(document.value, selectedEdge.value); selectedEdgeId.value = ''; changed() } }
function setEdgeEnabled(value) { setText(selectedEdge.value.element, 'enabled', value ? 'Y' : 'N'); changed() }
function setEdgeCondition(value) { setText(selectedEdge.value.element, 'unconditional', value === 'unconditional' ? 'Y' : 'N'); setText(selectedEdge.value.element, 'evaluation', value === 'failure' ? 'N' : 'Y'); changed() }
async function validate() {
  if (!canRun.value || !await save()) return; validating.value = true; error.value = ''
  try { validation.value = (await api.validateKettleDefinition(definition.value.id)).data; resultsOpen.value = true; if (validation.value.valid === false) error.value = validation.value.error || '原引擎检查未通过，请核对节点配置'; else proxy.$modal.msgSuccess('已取得原引擎字段结构，可继续预览或运行') }
  catch (cause) { error.value = errorMessage(cause, '字段获取或校验失败') } finally { validating.value = false }
}
function readPending(id) { try { return JSON.parse(localStorage.getItem(`kettle:pending:${id}`) || 'null') } catch { return { blocked: true } } }
function clearPending() { localStorage.removeItem(`kettle:pending:${definition.value.id}`); pendingSubmission.value = null }
async function submitPending() {
  starting.value = true; error.value = ''; clearTimeout(pollTimer)
  try {
    if (!pendingSubmission.value?.requestId) throw new Error('本地提交标识损坏，请核查历史记录后处理')
    const target = pendingSubmission.value.previewStep || selectedNode.value?.name || graph.value.nodes.slice(-1)[0]?.name || ''
    const result = await api.runKettleDefinition(definition.value.id, pendingSubmission.value)
    run.value = result.data; clearPending(); events.value = []; cursor = 0; resultsOpen.value = true; previewNodeName.value = run.value.previewStep || target; inspectorExpanded.value = false; await poll()
  } catch (cause) { error.value = errorMessage(cause, '提交尚未确认，请核查原请求记录'); if (pendingSubmission.value) run.value = { state: 'SUBMISSION_UNKNOWN', mode: pendingSubmission.value.mode } }
  finally { starting.value = false }
}
async function checkSubmission() {
  if (!pendingSubmission.value || starting.value) return; starting.value = true
  try {
    history.value = (await api.kettleRunHistory(definition.value.id)).data || []
    let previous = history.value.find(item => item.requestId === pendingSubmission.value.requestId)
    if (previous) { clearPending(); await viewRun(previous); return }
    const current = (await api.kettleDefinition(definition.value.id)).data
    if (Number(current.revision) > Number(pendingSubmission.value.revision)) {
      // Read again after observing a newer revision. A delayed request for the old immutable
      // revision can no longer start, and any earlier accepted request must already be recorded.
      history.value = (await api.kettleRunHistory(definition.value.id)).data || []
      previous = history.value.find(item => item.requestId === pendingSubmission.value.requestId)
      if (previous) { clearPending(); await viewRun(previous); return }
      clearPending(); run.value = null; submissionRevisionExpired.value = true; error.value = ''; return
    }
    error.value = '未找到该请求的运行记录；可重试原提交，请勿另建请求重复启动'
  } catch (cause) { error.value = errorMessage(cause, '提交状态核查未完成，原请求标识已保留') }
  finally { starting.value = false }
}
async function retrySubmission() { if (canRun.value && pendingSubmission.value && !starting.value) await submitPending() }
async function execute(mode) {
  if (!canRun.value || activeRun.value || reviewRun.value || submissionRevisionExpired.value || !definition.value || !engineReady.value) return
  if (mode === 'preview' && (!selectedNode.value || graph.value.kind === 'job')) return
  if (!await save()) return
  pendingSubmission.value = { revision: definition.value.revision, mode, previewStep: mode === 'preview' ? selectedNode.value.name : undefined, rowLimit: 20, requestId: elementId(document.value.createElement('request')) }
  try { localStorage.setItem(`kettle:pending:${definition.value.id}`, JSON.stringify(pendingSubmission.value)) } catch { error.value = '无法保存提交标识，尚未启动任务；请允许本地存储后重试'; pendingSubmission.value = null; return }
  await submitPending()
}
async function poll() {
  if (!run.value || disposed) return; const id = run.value.id
  try { const [current, batch] = await Promise.all([api.kettleRun(id), api.kettleEvents(id, cursor)]); if (disposed || run.value?.id !== id) return; run.value = current.data; events.value = mergeEvents(events.value, batch.data.events || []); cursor = batch.data.nextCursor ?? cursor; if (runActive(run.value.state) || runFinishing(run.value)) pollTimer = setTimeout(poll, 1000) }
  catch (cause) { if (!disposed) { error.value = errorMessage(cause, '运行状态读取失败，请刷新核查'); pollTimer = setTimeout(poll, 3000) } }
}
async function stop() { if (!run.value || stopping.value) return; stopping.value = true; try { run.value = (await api.stopKettleRun(run.value.id)).data; clearTimeout(pollTimer); await poll() } catch (cause) { error.value = errorMessage(cause, '停止尚未确认，请继续核查运行状态') } finally { stopping.value = false } }
async function loadHistory() { if (!definition.value) return; historyLoading.value = true; try { history.value = (await api.kettleRunHistory(definition.value.id)).data || [] } catch (cause) { error.value = errorMessage(cause, '历史记录加载失败') } finally { historyLoading.value = false } }
async function openHistory() { historyOpen.value = true; await loadHistory() }
async function viewRun(row) { clearTimeout(pollTimer); run.value = row; events.value = []; cursor = 0; previewNodeName.value = row.previewStep || graph.value.nodes.slice(-1)[0]?.name || ''; resultsOpen.value = true; historyOpen.value = false; await poll() }
function choosePreviewNode(name) { if (configDirty.value && nodeEditor.value?.apply() === false) return; previewNodeName.value = name; const node = graph.value.nodes.find(item => item.name === name); if (node) selectedNodeId.value = node.id }
async function downloadOutput(file) { try { const blob = await api.downloadKettleOutput(run.value.id, file.name); if (blob.type?.includes('json')) throw new Error('文件下载返回错误，请核查运行记录'); saveAs(blob, file.name) } catch (cause) { error.value = errorMessage(cause, '下载失败') } }
function exportDefinition() { if (!document.value) return; if (configDirty.value && nodeEditor.value?.apply() === false) return; saveAs(new Blob([xmlText(document.value)], { type: 'application/xml;charset=utf-8' }), graph.value.name + (graph.value.kind === 'job' ? '.kjb' : '.ktr')) }
async function uploadFile(options) { if (!definition.value || !canEdit.value || busy.value) return; uploading.value = true; try { const body = new FormData(); body.append('file', options.file); const result = await api.uploadKettleInputFile(definition.value.id, body); files.value = (await api.kettleInputFiles(definition.value.id)).data; options.onSuccess?.(result); proxy.$modal.msgSuccess('实际输入文件已上传，可在输入节点中选用') } catch (cause) { error.value = errorMessage(cause, '输入文件上传失败'); options.onError?.(cause) } finally { uploading.value = false } }
async function removeFile(file) { try { await proxy.$modal.confirm(`移除任务输入文件“${file.name}”？`); await api.deleteKettleInputFile(definition.value.id, file.id); files.value = (await api.kettleInputFiles(definition.value.id)).data } catch (cause) { if (cause?.message) error.value = errorMessage(cause, '文件移除失败') } }
function openConnections() { if (configDirty.value && nodeEditor.value?.apply() === false) return; connectionsOpen.value = true }
function addConnection() { connectionId.value = ''; connectionDraft.value = markRaw(document.value.createElement('connection')); setText(connectionDraft.value, 'name', '新数据库连接'); setText(connectionDraft.value, 'type', 'POSTGRESQL'); setText(connectionDraft.value, 'access', 'Native'); setText(connectionDraft.value, 'port', '5432'); connectionVersion.value++ }
function editConnection(row) { if (configDirty.value && nodeEditor.value?.apply() === false) return; connectionId.value = row.id; connectionDraft.value = markRaw(row.element.cloneNode(true)); connectionVersion.value++ }
const connectionRead = key => { connectionVersion.value; return textAt(connectionDraft.value, key) }
function connectionWrite(key, value) { setText(connectionDraft.value, key, value); connectionVersion.value++ }
function applyConnection() { if (configDirty.value && nodeEditor.value?.apply() === false) return; const name = textAt(connectionDraft.value, 'name').trim(); if (!name || databaseConnections.value.some(row => row.id !== connectionId.value && row.name === name)) { proxy.$modal.msgError('连接名称不能为空或重复'); return } const existing = databaseConnections.value.find(row => row.id === connectionId.value); const replacement = document.value.importNode(connectionDraft.value, true); setText(replacement, 'name', name); elementId(replacement); if (existing) { for (const node of graph.value.nodes) if (textAt(node.element, 'connection') === existing.name) setText(node.element, 'connection', name); existing.element.replaceWith(replacement) } else document.value.documentElement.appendChild(replacement); connectionDraft.value = null; editorGeneration.value++; changed(); proxy.$modal.msgSuccess('连接配置已应用，保存任务后生效') }
async function removeConnection(row) { if (graph.value.nodes.some(node => textAt(node.element, 'connection') === row.name)) { proxy.$modal.msgWarning('此连接仍被节点使用'); return } try { await proxy.$modal.confirm(`删除连接“${row.name}”？`); row.element.remove(); changed() } catch { } }
function beforeUnload(event) { if (dirty.value || configDirty.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => { load(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { disposed = true; requestGeneration++; clearTimeout(pollTimer); window.removeEventListener('beforeunload', beforeUnload) })
defineExpose({ canLeave: leaveDraft })
</script>
<style scoped>
.kettle-workbench { min-width: 0; }
.kettle-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--el-font-size-base); padding-bottom: var(--el-component-size-small); }
.kettle-body { display: grid; grid-template-columns: 220px minmax(0, 1fr) 380px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); background: var(--surface-bg); }
.kettle-library, .kettle-inspector { min-width: 0; padding: var(--el-font-size-base); }
.kettle-library { border-right: 1px solid var(--el-border-color); }
.kettle-inspector { border-left: 1px solid var(--el-border-color); }
.kettle-library__scroll { height: min(65vh, 800px); margin-top: var(--el-font-size-base); }
.kettle-inspector__scroll { height: min(78vh, 1000px); }
.kettle-library h3 { font-size: var(--el-font-size-small); font-weight: 500; color: var(--app-muted); margin: var(--el-font-size-base) 0; }
.kettle-task.el-button, .kettle-tool.el-button { display: flex; justify-content: flex-start; width: 100%; margin: 0 0 var(--el-font-size-extra-small); }
.kettle-task :deep(span), .kettle-tool :deep(span) { min-width: 0; }
.kettle-task span, .kettle-tool span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kettle-task .el-icon, .kettle-tool .el-icon { margin-right: var(--el-font-size-extra-small); flex-shrink: 0; }
.kettle-main { display: flex; flex-direction: column; min-width: 0; }
.kettle-canvas-toolbar { padding: var(--el-font-size-small) var(--el-font-size-base); border-bottom: 1px solid var(--el-border-color); }
.kettle-canvas { display: flex; min-height: 590px; flex: 1; min-width: 0; }
.kettle-canvas > * { width: 100%; }
.kettle-canvas-status { display: flex; justify-content: space-between; align-items: center; gap: var(--el-font-size-small); padding: var(--el-font-size-small) var(--el-font-size-base); }
.kettle-edge-config h3 { margin-top: 0; font-size: var(--el-font-size-base); }
@media (min-width: 1101px) { .kettle-body { height: max(620px, calc(100dvh - 240px)); } .kettle-main { overflow: hidden; } .kettle-canvas { min-height: 220px; } .kettle-library__scroll { height: calc(100dvh - 360px); min-height: 460px; } .kettle-inspector__scroll { height: 100%; } .kettle-main > .kettle-results { flex: 0 0 320px; max-height: 320px; overflow: auto; } }
@media (max-width: 1400px) { .kettle-body { grid-template-columns: 180px minmax(0, 1fr) 320px; } }
@media (max-width: 1100px) { .kettle-body { grid-template-columns: 170px minmax(0, 1fr); } .kettle-inspector { grid-column: 1 / -1; border-left: 0; border-top: 1px solid var(--el-border-color); } .kettle-inspector__scroll { height: auto; max-height: 650px; } }
@media (max-width: 640px) { .kettle-body { grid-template-columns: minmax(0, 1fr); } .kettle-library { border-right: 0; border-bottom: 1px solid var(--el-border-color); } .kettle-library__scroll { height: 210px; } .kettle-canvas { min-height: 440px; } }
</style>
