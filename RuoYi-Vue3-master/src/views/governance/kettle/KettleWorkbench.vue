<template>
  <section ref="workbenchRoot" class="kettle-workbench" :class="{ 'is-compact': compactViewport }" :style="{ '--kettle-workspace-height': `${workspaceHeight}px` }" aria-label="Kettle数据开发工作台" v-loading="loading && !definition">
    <header class="kettle-toolbar">
      <div class="kettle-task-heading">
        <div class="kettle-title-line"><strong :title="graph.name || '数据开发工作台'">{{ graph.name || '数据开发工作台' }}</strong><el-tag v-if="definition" size="small" effect="plain">{{ graph.kind === 'job' ? '作业' : '转换' }} · V{{ definition.revision }}</el-tag></div>
        <div class="kettle-heading-meta"><el-text v-if="loading" type="info" size="small">正在加载工作台</el-text><el-text v-else-if="definition" :type="dirty || configDirty || connectionDirty ? 'warning' : 'info'" size="small">{{ dirty || configDirty || connectionDirty ? '有未保存修改' : '已保存' }}</el-text><el-tooltip :content="engineReady ? '执行服务可访问；数据源和节点配置仍需检查与测试' : '执行服务暂不可用，仍可查看和编辑已保存任务'" placement="bottom"><el-text :type="engineReady ? 'success' : 'info'" size="small">{{ engineReady ? '执行服务已连接' : '执行服务未连接' }}</el-text></el-tooltip><el-text v-if="executionProtected" type="warning" size="small">{{ activeRun ? '执行中，配置只读' : '执行状态待核查，配置只读' }}</el-text></div>
      </div>
      <div class="kettle-primary-actions">
        <el-button v-if="canEdit && definition" icon="DocumentChecked" :loading="saving" :disabled="!canChange || (!dirty && !configDirty && !connectionDirty)" @click="save">保存任务</el-button>
        <el-tooltip v-if="canRun && definition" :content="previewReason || '预览选中节点及其上游的实际数据'" placement="bottom"><span><el-button icon="View" :disabled="!canPreviewSelected" @click="execute('preview')">{{ dirty || configDirty ? '保存并预览' : '预览节点' }}</el-button></span></el-tooltip>
        <el-button v-if="canRun && definition" type="primary" icon="VideoPlay" :disabled="!engineReady || busy || executionProtected" :loading="starting" @click="execute('run')">{{ dirty || configDirty ? '保存并运行' : '运行任务' }}</el-button>
        <el-button v-if="executionProtected && run?.id && canRun" type="danger" plain icon="VideoPause" :loading="stopping" @click="stop">停止运行</el-button>
        <el-tooltip content="刷新执行服务与任务列表" placement="bottom"><el-button icon="Refresh" :loading="loading" :disabled="busy" aria-label="刷新执行服务与任务列表" @click="load" /></el-tooltip>
      </div>
    </header>
    <div v-if="submissionRevisionExpired || pendingSubmission || error || (!engineReady && !loading)" class="kettle-notices">
      <el-alert v-if="submissionRevisionExpired" title="原提交版本已过期，已确认没有执行该请求" type="warning" :closable="false"><el-button @click="openDefinition(definition.id, true)">重新读取任务</el-button></el-alert>
      <el-alert v-if="pendingSubmission" title="上次提交尚未确认，已保留原请求标识" type="warning" :closable="false"><el-space><el-button :loading="starting" @click="checkSubmission">核查提交记录</el-button><el-button v-if="canRun" :loading="starting" @click="retrySubmission">重试原提交</el-button></el-space></el-alert>
      <el-alert v-if="error" :title="error" type="error" show-icon closable @close="error = ''" />
      <el-alert v-if="!engineReady && !loading" title="执行服务暂不可用，可先编辑任务，连接恢复后再检查字段和运行" type="info" :closable="false" />
    </div>
    <div class="kettle-body" :class="{ 'has-library': libraryOpen && !resultsExpanded, 'has-inspector': inspectorVisible && !resultsExpanded, 'results-expanded': resultsExpanded && resultsOpen }">
      <aside v-show="libraryOpen && !resultsExpanded" class="kettle-library" aria-label="任务与工具">
        <div class="kettle-library-actions"><template v-if="canEdit"><el-dropdown trigger="click" @command="openNew"><el-button size="small" icon="Plus" :disabled="busy">新建<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="transformation">数据转换</el-dropdown-item><el-dropdown-item command="job">执行作业</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-upload action="#" :show-file-list="false" :http-request="importFile" accept=".ktr,.kjb,.zip" :disabled="busy"><el-button size="small" icon="Upload" :disabled="busy" title="导入KTR、KJB或ZIP">导入</el-button></el-upload></template><el-tooltip content="收起任务与工具"><el-button size="small" link icon="Fold" aria-label="收起任务与工具面板" @click="libraryOpen = false" /></el-tooltip></div>
        <el-tabs v-model="leftTab" class="motion-tabs kettle-library-tabs">
          <el-tab-pane label="任务" name="tasks"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">任务</span></span></template>
            <el-input v-model="taskSearch" clearable placeholder="搜索任务名称" aria-label="搜索任务" prefix-icon="Search" />
            <el-scrollbar class="kettle-library__scroll">
              <section v-for="kind in taskKinds" :key="kind.value" class="kettle-task-group"><h3>{{ kind.label }}<span>{{ filteredDefinitions.filter(row => row.kind === kind.value).length }}</span></h3><el-button v-for="item in filteredDefinitions.filter(row => row.kind === kind.value)" :key="item.id" text class="kettle-task" :class="{ 'is-current': definition?.id === item.id }" :disabled="busy" :aria-pressed="definition?.id === item.id" :title="item.name" @click="openDefinition(item.id)"><el-icon><component :is="item.kind === 'job' ? 'Connection' : 'SetUp'" /></el-icon><span class="kettle-task-label"><span class="kettle-task-name">{{ item.name }}</span><span class="kettle-task-meta">{{ item.nodeCount }} 个节点 · V{{ item.revision }}</span></span></el-button></section>
              <el-empty v-if="!filteredDefinitions.length" :description="taskSearch ? '没有找到匹配任务' : '还没有任务，可新建或导入已有流程'" :image-size="48" />
            </el-scrollbar>
          </el-tab-pane>
          <el-tab-pane label="工具" name="tools"><template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">工具</span></span></template>
            <el-input v-model="toolSearch" clearable placeholder="搜索输入、处理、输出" aria-label="搜索Kettle工具" prefix-icon="Search" />
            <el-select v-model="toolScope" aria-label="工具显示范围"><el-option value="common" label="常用工具" /><el-option value="available" label="全部可用" /><el-option value="catalog" label="完整目录" /></el-select>
            <el-text v-if="toolSearch.trim() && toolScope === 'common'" type="info" size="small">搜索全部可用工具</el-text>
            <el-scrollbar class="kettle-library__scroll">
              <el-collapse v-model="expandedGroups">
                <el-collapse-item v-for="group in toolGroups" :key="group.name" :name="group.name" :title="`${group.name} · ${group.items.length}`">
                  <div v-for="plugin in group.items" :key="pluginKey(plugin)" class="kettle-tool-entry"><el-button class="kettle-tool" text :draggable="Boolean(canChange && plugin.executable && definition)" :disabled="!canChange || !plugin.executable || !definition" :title="plugin.label || plugin.name || plugin.id" @dragstart="dragPlugin($event, plugin)" @click="insertPlugin(plugin)"><el-icon><component :is="presentation({ pluginId: plugin.id }).icon" /></el-icon><span>{{ plugin.label || plugin.name || plugin.id }}</span></el-button><el-tooltip :content="capability(plugin).detail" placement="right"><el-tag :type="capability(plugin).type" size="small" effect="plain" tabindex="0">{{ shortCapability(plugin) }}</el-tag></el-tooltip></div>
                </el-collapse-item>
              </el-collapse>
              <el-empty v-if="!toolGroups.length" :description="toolScope === 'catalog' ? '没有匹配工具' : '当前范围没有可用工具'" :image-size="48"><el-button v-if="toolScope !== 'catalog'" link type="primary" @click="toolScope = 'catalog'">查看完整目录</el-button></el-empty>
            </el-scrollbar>
            <el-text type="info" size="small">{{ definition ? '拖到画布或点击添加' : '先新建或选择一个任务' }}</el-text>
          </el-tab-pane>
        </el-tabs>
      </aside>
      <main class="kettle-main">
        <nav class="kettle-canvas-toolbar" aria-label="编排工作区操作">
          <div class="kettle-toolbar-group"><el-tooltip :content="libraryOpen ? '收起任务与工具' : '显示任务与工具'" placement="bottom"><el-button link :icon="libraryOpen ? 'Fold' : 'Expand'" :aria-label="libraryOpen ? '收起任务与工具' : '显示任务与工具'" :aria-expanded="libraryOpen" @click="toggleLibrary">任务与工具</el-button></el-tooltip><el-button v-if="definition && !graph.nodes.length && canEdit" link type="primary" :disabled="!canChange" icon="Plus" @click="showTools">选择第一个工具</el-button><el-text v-if="definition" type="info" size="small">{{ graph.kind === 'job' ? '作业 · 执行依赖' : '转换 · 数据流' }}</el-text></div>
          <div v-if="definition" class="kettle-toolbar-group"><el-button link icon="Sort" :disabled="!canChange" @click="arrange">整理</el-button><el-button v-if="canRun" link icon="CircleCheck" :disabled="busy || executionProtected || !engineReady" :loading="validating" @click="validate">{{ graph.kind === 'job' ? '检查结构' : '获取字段' }}</el-button><el-dropdown trigger="click" @command="resourceCommand"><el-button link icon="Coin" :disabled="busy">数据与连接<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="inputs" icon="Files">输入文件</el-dropdown-item><el-dropdown-item command="connections" icon="Coin">数据库连接</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-dropdown trigger="click" @command="taskCommand"><el-button link icon="MoreFilled" :disabled="busy" aria-label="任务设置与运行记录">更多<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="settings" icon="Setting">任务设置</el-dropdown-item><el-dropdown-item command="schedules" icon="Timer">定时计划</el-dropdown-item><el-dropdown-item command="history" icon="Clock">运行记录</el-dropdown-item><el-dropdown-item command="export" icon="Download" divided>导出流程</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button v-if="(selectedNode || selectedEdge) && (!inspectorVisible || resultsExpanded)" link icon="Setting" @click="showInspector">显示配置</el-button></div>
        </nav>
        <div v-show="!resultsExpanded || !resultsOpen" class="kettle-canvas" v-loading="loading">
          <FlowDiagram v-if="definition" ref="diagram" :nodes="graph.nodes" :connections="graph.connections" :selected-node-id="selectedNodeId" :selected-edge-id="selectedEdgeId" :editable="canChange && !configDirty" :presentation="presentation" :summary="nodeSummary" :source-predicate="isSource" :run="diagramRun" :result-stale="dirty || configDirty || run?.revision !== definition?.revision" show-auxiliary @select-node="selectNode" @select-edge="selectEdge" @move-node="moveNode" @add-node="dropPlugin" @connect="connectNodes" />

          <el-empty v-else description="选择一个已有任务，或开始新的数据处理流程" :image-size="80"><el-space v-if="canEdit" wrap><el-button type="primary" icon="Plus" @click="openNew('transformation')">新建数据转换</el-button><el-button icon="Connection" @click="openNew('job')">编排执行作业</el-button></el-space><el-text v-else type="info">从左侧选择有权限查看的任务</el-text></el-empty>
        </div>
        <footer v-if="definition" class="kettle-canvas-status"><el-text type="info" size="small">{{ graph.nodes.length }} 个节点 · {{ graph.connections.length }} 条连接<template v-if="selectedNode"> · 已选 {{ selectedNode.name }}</template></el-text><el-button link :icon="resultsOpen ? 'ArrowDown' : 'ArrowUp'" :aria-expanded="resultsOpen" @click="toggleResults">{{ resultsOpen ? '收起结果' : '测试与运行结果' }}</el-button></footer>
        <KettleResults v-if="resultsOpen && definition" :run="run" :stale="dirty || configDirty || run?.revision !== definition?.revision" :events="events" :selected-node="previewNodeName" :schema="selectedFields" :validation="validation" :validation-kind="graph.kind" :validation-stale="validationStale" :expanded="resultsExpanded" @toggle-expand="resultsExpanded = !resultsExpanded" @select-node="choosePreviewNode" @download="downloadOutput" @close="closeResults" />
      </main>
      <aside v-show="inspectorVisible && !resultsExpanded" class="kettle-inspector" aria-label="节点与连接配置">
        <KettleNodeConfig v-if="selectedNode && !inspectorExpanded" :key="`${selectedNode.id}-${editorGeneration}`" ref="nodeEditor" :node="selectedNode" :plugin="selectedPlugin" :kind="graph.kind" :title="presentation(selectedNode).label" :context="formContext" :readonly="!canChange" :can-run="canPreviewSelected" :preview-hint="previewReason" :commit="applyNode" bounded closable @close="hideInspector" @dirty-change="configDirty = $event" @preview="execute('preview')" @remove="deleteNode" @expand="expandInspector" />
        <section v-else-if="selectedEdge" class="kettle-edge-config"><header><strong>连接设置</strong><el-button link icon="Close" aria-label="收起连接配置" @click="hideInspector" /></header><el-descriptions :column="1"><el-descriptions-item label="上游">{{ nodeName(selectedEdge.sourceId) }}</el-descriptions-item><el-descriptions-item label="下游">{{ nodeName(selectedEdge.targetId) }}</el-descriptions-item></el-descriptions><el-form label-position="top" :disabled="!canChange"><el-form-item label="启用连接"><el-switch :model-value="selectedEdge.enabled" @update:model-value="setEdgeEnabled" /></el-form-item><el-form-item v-if="graph.kind === 'job'" label="执行条件"><el-select :model-value="edgeCondition" @update:model-value="setEdgeCondition"><el-option label="无条件" value="unconditional" /><el-option label="上游成功" value="success" /><el-option label="上游失败" value="failure" /></el-select></el-form-item></el-form><el-button v-if="canEdit" link type="danger" :disabled="!canChange" @click="deleteEdge">删除连接</el-button></section>
      </aside>
    </div>
    <el-dialog v-model="taskSettingsOpen" title="任务设置" width="min(92vw, 980px)" append-to-body destroy-on-close>
      <el-form label-position="top" :disabled="!canChange"><el-form-item label="任务名称" required><el-input v-model="taskName" maxlength="100" /></el-form-item><el-form-item label="执行时区"><el-select v-model="taskTimeZone" filterable allow-create default-first-option><el-option value="Asia/Shanghai" label="北京时间（Asia/Shanghai）" /><el-option value="UTC" label="协调世界时（UTC）" /></el-select></el-form-item><el-text type="info">日期、时间及文件名使用此时区；作业中的子转换继承作业时区。</el-text></el-form>
      <el-divider content-position="left">默认参数</el-divider>
      <el-text type="info">节点中使用 ${参数名} 引用默认值；定时计划保存时会一并冻结。</el-text>
      <el-button v-if="canEdit" icon="Plus" class="ml16" :disabled="!canChange" @click="addParameter">新增参数</el-button>
      <el-table :data="taskParameters" class="mt16" empty-text="尚未配置默认参数"><el-table-column v-for="field in [{key:'name',label:'参数名'},{key:'default_value',label:'默认值'},{key:'description',label:'说明'}]" :key="field.key" :label="field.label" min-width="190"><template #default="{row}"><el-input :disabled="!canChange" :aria-label="field.label" :model-value="parameterRead(row,field.key)" @update:model-value="parameterWrite(row,field.key,$event)" /></template></el-table-column><el-table-column label="操作" width="80"><template #default="{row}"><el-button link type="danger" :disabled="!canChange" @click="removeParameter(row)">删除</el-button></template></el-table-column></el-table>
      <template #footer><el-button @click="taskSettingsOpen = false">取消</el-button><el-button v-if="canEdit" type="primary" :loading="saving" :disabled="!canChange" @click="applyTaskSettings">应用任务设置</el-button></template>
    </el-dialog>
    <el-dialog v-model="inspectorExpanded" title="节点详细配置" width="90%" class="kettle-node-dialog" append-to-body destroy-on-close :before-close="closeInspector"><div class="kettle-expanded-editor"><KettleNodeConfig v-if="selectedNode && inspectorExpanded" :key="`${selectedNode.id}-${editorGeneration}`" ref="nodeEditor" :node="selectedNode" :plugin="selectedPlugin" :kind="graph.kind" :title="presentation(selectedNode).label" :context="formContext" :readonly="!canChange" :can-run="canPreviewSelected" :commit="applyNode" @dirty-change="configDirty = $event" @preview="execute('preview')" @remove="deleteNode" :preview-hint="previewReason" expanded bounded /></div></el-dialog>
    <el-dialog v-model="newOpen" :title="newKind === 'job' ? '新建Kettle作业' : '新建Kettle转换'" width="500px" append-to-body><el-form label-position="top" @submit.prevent="create"><el-form-item label="任务名称" required><el-input v-model="newName" maxlength="100" autofocus @keyup.enter="create" /></el-form-item></el-form><template #footer><el-button @click="newOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!newName.trim()" @click="create">创建</el-button></template></el-dialog>
    <el-dialog v-model="hopOpen" title="连接执行条件" width="500px" append-to-body><el-form label-position="top"><el-form-item v-if="graph.kind === 'job'" label="上游节点结束后"><el-radio-group v-model="hopCondition"><el-radio value="success">成功时执行</el-radio><el-radio value="failure">失败时执行</el-radio><el-radio value="unconditional">无条件执行</el-radio></el-radio-group></el-form-item><el-form-item v-else :label="hopSource?.pluginId === 'FilterRows' ? '条件结果' : '匹配方式'"><el-radio-group v-model="hopCondition"><template v-if="hopSource?.pluginId === 'FilterRows'"><el-radio value="true">满足条件</el-radio><el-radio value="false">不满足条件</el-radio></template><template v-else><el-radio value="case">匹配值</el-radio><el-radio value="default">默认分支</el-radio></template></el-radio-group></el-form-item><el-form-item v-if="hopCondition === 'case'" label="匹配值"><el-input v-model="hopValue" /></el-form-item></el-form><template #footer><el-button @click="hopOpen = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!canChange" @click="saveHop">添加连接</el-button></template></el-dialog>
    <el-drawer v-model="assetsOpen" title="任务输入文件" size="640px" append-to-body>
      <el-alert title="上传实际数据文件后，在输入节点的文件选择框中选用" description="每次执行将这些文件复制到独立输入目录，与输出文件分开保存。预览采样不会限制正式运行的行数。" type="info" :closable="false" class="mb16" />
      <el-upload action="#" :http-request="uploadFile" :show-file-list="false" :disabled="!definition || !canChange"><el-button type="primary" icon="Upload">上传输入文件</el-button></el-upload>
      <el-table :data="files" class="mt16" empty-text="尚未上传输入文件"><el-table-column prop="name" label="文件" min-width="200" /><el-table-column label="字节数" width="120"><template #default="{ row }">{{ row.bytes ?? row.size }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="danger" :disabled="!canChange" @click="removeFile(row)">移除</el-button></template></el-table-column></el-table>
    </el-drawer>
    <el-drawer v-model="schedulesOpen" title="Kettle定时任务" size="85%" append-to-body destroy-on-close><KettleSchedules v-if="schedulesOpen && definition" :key="definition.id" :definition="definition" :can-edit="canEdit" :can-run="canRun" :execution-blocked="executionProtected || busy" :before-save="save" @view-run="openScheduledRun" /></el-drawer>
    <el-drawer v-model="historyOpen" title="任务运行历史" size="780px" append-to-body>
      <el-button icon="Refresh" :loading="historyLoading" @click="loadHistory">刷新</el-button>
      <el-table :data="history" class="mt16" empty-text="此任务尚无运行记录" v-loading="historyLoading"><el-table-column label="开始时间" min-width="180"><template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template></el-table-column><el-table-column label="方式" width="90"><template #default="{ row }">{{ row.mode === 'preview' ? '预览' : '运行' }}</template></el-table-column><el-table-column label="版本" prop="revision" width="80" /><el-table-column label="状态" min-width="150"><template #default="{ row }"><el-tag :type="runStatus(row.state).type">{{ runStatus(row.state).label }}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="{ row }"><el-button link type="primary" :disabled="!canViewHistory(row)" @click="openRecordedRun(row)">查看结果</el-button></template></el-table-column></el-table>
    </el-drawer>
    <el-drawer v-model="connectionsOpen" title="任务数据库连接" size="720px" append-to-body :before-close="closeConnections" :close-on-click-modal="!connectionDirty">
      <el-button type="primary" icon="Plus" :disabled="!definition || !canChange" @click="addConnection">新建连接</el-button>
      <el-table :data="databaseConnections" class="mt16" empty-text="暂无连接；数据库输入、查询和输出共用这里的连接配置"><el-table-column prop="name" label="连接名称" min-width="150" /><el-table-column prop="type" label="类型" width="140" /><el-table-column label="操作" width="160"><template #default="{ row }"><el-button link type="primary" @click="editConnection(row)">配置</el-button><el-button link type="danger" :disabled="!canChange" @click="removeConnection(row)">删除</el-button></template></el-table-column></el-table>
      <el-alert v-if="connectionDirty" title="连接有未应用修改，请应用后再保存任务" type="warning" :closable="false" class="mt16" /><el-form v-if="connectionDraft" label-position="top" class="mt16" :disabled="!canChange"><el-form-item v-for="field in connectionSchema" :key="field.key" :label="field.label"><KettleValueInput :field="field" :context="{ readonly: !canChange }" :model-value="connectionRead(field.key)" @update:model-value="connectionWrite(field.key, $event)" /></el-form-item><el-button type="primary" :disabled="!canChange" @click="applyConnection">应用连接配置</el-button><el-button @click="cancelConnection">取消修改</el-button></el-form>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, getCurrentInstance, markRaw, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
import { groupTools, toolCapability, validationFeedback } from './workbenchRules'
import { connectionDraftBelongsTo, definitionMutationAllowed, executionProtectsDraft, historySelectionAllowed, protectedHistoryRun, restoredTaskId, sameEditorContext } from './workbenchInteraction'
import { arrangeNodes, NODE_HEIGHT, NODE_WIDTH, POSITION_SCALE } from '../graphRules'
import { errorMessage } from '../workspaceRules'
const { proxy } = getCurrentInstance()
const route = useRoute(), router = useRouter()
const workbenchRoot = ref(), workspaceHeight = ref(660), compactViewport = ref(false), libraryOpen = ref(true), inspectorOpen = ref(true), resultsExpanded = ref(false)
const connectionDirty = ref(false), connectionOwner = ref(''), validationContext = ref(null)
let workspaceObserver, workspaceFrame
const canEdit = computed(() => proxy.$auth.hasPermi('governance:flow:edit'))
const canRun = computed(() => proxy.$auth.hasPermi('governance:flow:test'))
const definitions = ref([]), definition = ref(null), document = ref(null), version = ref(0), editorGeneration = ref(0)
const catalog = ref({ steps: [], jobs: [] }), status = ref({}), loading = ref(false), saving = ref(false), starting = ref(false), stopping = ref(false), validating = ref(false), error = ref('')
const dirty = ref(false), configDirty = ref(false), leftTab = ref('tasks'), taskSearch = ref(''), toolSearch = ref(''), selectedNodeId = ref(''), selectedEdgeId = ref(''), nodeEditor = ref(), diagram = ref()
const toolScope = ref('common')
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
const busy = computed(() => loading.value || saving.value || starting.value || validating.value || uploading.value || stopping.value)
const engineReady = computed(() => status.value.enabled === true && status.value.executionAvailable === true)
const activeRun = computed(() => runActive(run.value?.state) || runFinishing(run.value))
const pendingSubmission = ref(null), submissionRevisionExpired = ref(false)
const reviewRun = computed(() => Boolean(pendingSubmission.value) || runNeedsReview(run.value))
const executionProtected = computed(() => executionProtectsDraft(run.value, pendingSubmission.value))
const canChange = computed(() => definitionMutationAllowed({ canEdit: canEdit.value, busy: busy.value, run: run.value, pendingSubmission: pendingSubmission.value }))
const inspectorVisible = computed(() => inspectorOpen.value && !inspectorExpanded.value && Boolean(selectedNode.value || selectedEdge.value))
const validationStale = computed(() => Boolean(validation.value && !sameEditorContext(validationContext.value, editorContext())))
const previewReason = computed(() => !definition.value ? '请先选择任务' : graph.value.kind === 'job' ? '作业按执行依赖运行；请打开子转换预览数据' : !selectedNode.value ? '先选择一个输入或处理节点' : schemaFor(selectedNode.value.pluginId).group === '输出' ? '输出节点可能写入目标，请使用运行任务' : !engineReady.value ? '执行服务暂不可用' : executionProtected.value ? '先等待当前运行结束或核查执行状态' : busy.value ? '请等待当前操作完成' : '')
const canPreviewSelected = computed(() => canRun.value && !previewReason.value)
const graph = computed(() => { version.value; return document.value ? graphFromXml(document.value) : { kind: 'transformation', nodes: [], connections: [], name: '' } })
const selectedNode = computed(() => graph.value.nodes.find(node => node.id === selectedNodeId.value))
const selectedPlugin = computed(() => { const matches = item => item.id === selectedNode.value?.pluginId || item.aliases?.includes(selectedNode.value?.pluginId); return entries.value.find(item => matches(item) && item.loadable) || entries.value.find(matches) })
const selectedEdge = computed(() => graph.value.connections.find(edge => edge.id === selectedEdgeId.value))
const entries = computed(() => graph.value.kind === 'job' ? catalog.value.jobs || [] : catalog.value.steps || [])
const filteredDefinitions = computed(() => definitions.value.filter(item => item.name.includes(taskSearch.value.trim())))
const toolGroups = computed(() => groupTools(entries.value, { scope: toolScope.value, search: toolSearch.value, kind: graph.value.kind }))
const capability = plugin => toolCapability(plugin, catalog.value.workerAvailable !== false)
const shortCapability = plugin => ({ '专用表单': '表单', '原生参数': '原生' })[capability(plugin).label] || capability(plugin).label
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
const formContext = computed(() => ({ nodes: graph.value.nodes.map(node => node.name), definitions: definitions.value, readonly: !canChange.value, files: files.value, connections: databaseConnections.value.map(item => item.name), fields: [...new Set(inputFields.value.map(field => field.name))] }))
const edgeCondition = computed(() => selectedEdge.value ? textAt(selectedEdge.value.element, 'unconditional') === 'Y' ? 'unconditional' : textAt(selectedEdge.value.element, 'evaluation') === 'Y' ? 'success' : 'failure' : '')
const diagramRun = computed(() => run.value ? { ...run.value, status: run.value.state, steps: graph.value.nodes.map(node => {
  const metrics = (graph.value.kind === 'job' ? jobNodeStates(events.value, run.value.state) : run.value.nodes || []).filter(item => item.node === node.name && (graph.value.kind !== 'job' || String(item.copy ?? 0) === node.copyNr))
  if (graph.value.kind === 'job') return metrics.length ? { id: node.id, status: metrics[metrics.length - 1].status } : null
  return metrics.length ? { id: node.id, status: metrics.some(item => Number(item.errors) > 0) ? 'FAILED' : activeRun.value ? 'RUNNING' : run.value.state === 'SUCCEEDED' || run.value.state === 'PREVIEW_COMPLETE' ? 'SUCCEEDED' : run.value.state } : null
}).filter(Boolean) } : null)
function openTaskSettings() { if (configDirty.value && nodeEditor.value?.apply() === false) return; taskDraft.value = markRaw(document.value.cloneNode(true)); taskName.value = graph.value.name; taskTimeZone.value = document.value.documentElement.getAttribute('data-rynew-timezone') || 'Asia/Shanghai'; taskDraftVersion.value++; taskSettingsOpen.value = true }
function parameterRead(row, key) { taskDraftVersion.value; return textAt(row, key) }
function parameterWrite(row, key, value) { if (!canChange.value) return; setText(row, key, value); taskDraftVersion.value++ }
function addParameter() { if (!canChange.value) return; appendRow(taskDraft.value.documentElement, graph.value.kind === 'job' ? 'parameters/parameter' : 'info/parameters/parameter', { name: '', default_value: '', description: '' }); taskDraftVersion.value++ }
function removeParameter(row) { if (!canChange.value) return; row.remove(); taskDraftVersion.value++ }
function applyTaskSettings() {
  if (!canChange.value) return
  const name = taskName.value.trim(), names = taskParameters.value.map(row => textAt(row, 'name').trim())
  if (!name || names.some(value => !value) || new Set(names).size !== names.length || names.some(value => ['WORK_DIR', 'INPUT_DIR', 'JOB_DIR'].includes(value))) { proxy.$modal.msgWarning('任务与参数名称不能为空，参数不能重名或使用执行服务的保留名称'); return }
  try { new Intl.DateTimeFormat('zh-CN', { timeZone: taskTimeZone.value.trim() }).format(0) } catch { proxy.$modal.msgWarning('请选择有效的执行时区'); return }
  taskDraft.value.documentElement.setAttribute('data-rynew-timezone', taskTimeZone.value.trim())
  for (const row of taskParameters.value) setText(row, 'name', textAt(row, 'name').trim())
  setText(taskDraft.value.documentElement, graph.value.kind === 'job' ? 'name' : 'info/name', name); document.value = markRaw(taskDraft.value); editorGeneration.value++; changed(); taskSettingsOpen.value = false
}
function expandInspector() { if (configDirty.value && nodeEditor.value?.apply() === false) return; inspectorExpanded.value = true }
function closeInspector(done) { if (configDirty.value && nodeEditor.value?.apply() === false) return; done() }
function changed() { version.value++; dirty.value = true; validation.value = null; validationContext.value = null }
function setDocument(detail) { definition.value = detail; document.value = markRaw(parseXml(fromBase64(detail.xmlBase64))); version.value++; editorGeneration.value++; dirty.value = false; configDirty.value = false; selectedNodeId.value = ''; selectedEdgeId.value = ''; validation.value = null; validationContext.value = null; resetConnectionDraft(); connectionsOpen.value = false; taskSettingsOpen.value = false; hopOpen.value = false; pendingHop.value = null; inspectorOpen.value = true; resultsExpanded.value = false; pendingSubmission.value = readPending(detail.id); submissionRevisionExpired.value = false }
async function leaveDraft() {
  if (busy.value) { proxy.$modal.msgWarning('请等待本次提交结束'); return false }
  if (!dirty.value && !configDirty.value && !connectionDirty.value) return true
  try { await proxy.$modal.confirm(connectionDirty.value ? '连接有未应用修改，是否放弃本任务修改后切换？' : '当前任务有未保存修改，是否放弃后切换？'); return true } catch { return false }
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
  try { const [detail, inputs, previous] = await Promise.all([api.kettleDefinition(id), api.kettleInputFiles(id), api.kettleRunHistory(id)]); if (disposed || generation !== requestGeneration) return; setDocument(detail.data); files.value = inputs.data || []; history.value = previous.data || []; run.value = null; events.value = []; previewNodeName.value = ''; clearTimeout(pollTimer); const pendingRun = pendingSubmission.value && history.value.find(item => item.requestId === pendingSubmission.value.requestId); if (pendingRun) { clearPending(); await viewRun(pendingRun) } const protectedRun = protectedHistoryRun(history.value); if (!pendingRun && protectedRun) await viewRun(protectedRun); if (compactViewport.value) libraryOpen.value = false; rememberTask(detail.data.id); await nextTick(); diagram.value?.fit() }
  catch (cause) { error.value = errorMessage(cause, '任务读取失败') } finally { loading.value = false }
}
async function openNew(kind) { if (!await leaveDraft()) return; newKind.value = kind; newName.value = ''; newOpen.value = true }
async function create() {
  if (!canEdit.value || !newName.value.trim() || saving.value) return; saving.value = true; error.value = ''
  try { const xml = xmlText(emptyDocument(newKind.value, newName.value.trim())); const result = await api.createKettleDefinition({ name: newName.value.trim(), xmlBase64: toBase64(xml) }); setDocument(result.data); definitions.value = (await api.kettleDefinitions()).data; newOpen.value = false; leftTab.value = 'tools'; libraryOpen.value = true; files.value = []; run.value = null; events.value = []; history.value = []; rememberTask(result.data.id) }
  catch (cause) { error.value = errorMessage(cause, '创建失败') } finally { saving.value = false }
}
async function save() {
  if (!definition.value || saving.value || busy.value || executionProtected.value) return false
  if (connectionDirty.value) { connectionsOpen.value = true; proxy.$modal.msgWarning('请先应用或取消连接修改'); return false }
  if (!canEdit.value) return !dirty.value && !configDirty.value
  if (configDirty.value && nodeEditor.value?.apply() === false) return false
  if (!dirty.value) return true
  saving.value = true; error.value = ''; const selected = selectedNodeId.value, edge = selectedEdgeId.value, panelOpen = inspectorOpen.value, resultExpanded = resultsExpanded.value, owner = definition.value.id
  try { const result = await api.saveKettleDefinition(definition.value.id, { name: graph.value.name, revision: definition.value.revision, xmlBase64: toBase64(xmlText(document.value)) }); if (disposed || definition.value?.id !== owner) return false; setDocument(result.data); selectedNodeId.value = selected; selectedEdgeId.value = edge; inspectorOpen.value = panelOpen; resultsExpanded.value = resultExpanded; definitions.value = (await api.kettleDefinitions()).data; return true }
  catch (cause) { error.value = errorMessage(cause, '保存失败，请保留当前修改并核对版本'); return false } finally { saving.value = false }
}
async function importFile(options) {
  if (!canEdit.value) return
  if (!await leaveDraft()) { options.onError?.(new Error('已取消导入')); return }
  loading.value = true; error.value = ''
  try { const data = new FormData(); data.append('file', options.file); const result = await api.importKettleDefinitions(data); definitions.value = (await api.kettleDefinitions()).data; const imported = Array.isArray(result.data) ? result.data : result.data.definitions || []; if (imported.length) { const detail = await api.kettleDefinition(imported[0].id); setDocument(detail.data); files.value = []; run.value = null; events.value = []; history.value = []; clearTimeout(pollTimer); rememberTask(detail.data.id) } options.onSuccess?.(result); proxy.$modal.msgSuccess(`已导入${imported.length}个任务`) }
  catch (cause) { error.value = errorMessage(cause, '导入失败，未执行文件中的任务'); options.onError?.(cause) } finally { loading.value = false }
}
function dragPlugin(event, plugin) { if (!canChange.value) { event.preventDefault(); return } if (configDirty.value && nodeEditor.value?.apply() === false) { event.preventDefault(); return } event.dataTransfer.effectAllowed = 'copy'; event.dataTransfer.setData('application/x-governance-node', pluginKey(plugin)) }
async function insertPlugin(plugin, position) {
  if (!canChange.value || !document.value || !plugin.executable) return
  if (configDirty.value && nodeEditor.value?.apply() === false) return
  try {
    let location = position || diagram.value?.centerPosition() || { x: 120, y: 120 }
    if (!position) {
      const strideX = (NODE_WIDTH + 64) * POSITION_SCALE, strideY = (NODE_HEIGHT + 40) * POSITION_SCALE
      for (let attempt = 0; graph.value.nodes.some(node => Math.abs(node.position.x - location.x) < NODE_WIDTH * POSITION_SCALE && Math.abs(node.position.y - location.y) < NODE_HEIGHT * POSITION_SCALE); attempt++) location = { x: location.x + strideX, y: location.y + (attempt % 4 === 3 ? strideY : 0) }
    }
    selectedNodeId.value = addNode(document.value, plugin, location); selectedEdgeId.value = ''; inspectorOpen.value = true; if (compactViewport.value) libraryOpen.value = false; changed()
    if (!position) { await nextTick(); diagram.value?.fit() }
  } catch (cause) { error.value = cause.message }
}
function dropPlugin({ key, position }) { const plugin = entries.value.find(item => pluginKey(item) === key); if (plugin) insertPlugin(plugin, position) }
async function selectNode(node) { if (configDirty.value && nodeEditor.value?.apply() === false) return; selectedNodeId.value = node.id; selectedEdgeId.value = ''; previewNodeName.value = node.name; inspectorOpen.value = true; if (compactViewport.value) libraryOpen.value = false }
function selectEdge(edge) { if (configDirty.value && nodeEditor.value?.apply() === false) return; selectedNodeId.value = ''; selectedEdgeId.value = edge.id; inspectorOpen.value = true; if (compactViewport.value) libraryOpen.value = false }
function applyNode({ element, name }) {
  if (!canChange.value) return false
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
function moveNode({ id, position }) { if (!canChange.value) return; const node = graph.value.nodes.find(item => item.id === id); if (node) { setPosition(document.value, node, position); changed() } }
async function arrange() { if (!document.value || !canChange.value) return; if (configDirty.value && nodeEditor.value?.apply() === false) return; try { const arranged = arrangeNodes(graph.value.nodes, graph.value.connections); for (const node of arranged) setPosition(document.value, node, node.position); changed(); await nextTick(); diagram.value?.fit() } catch (cause) { error.value = cause.message } }
function connectNodes(value) {
  if (!canChange.value) return
  const source = graph.value.nodes.find(node => node.id === value.sourceId)
  if (graph.value.kind === 'job' || ['FilterRows', 'SwitchCase'].includes(source?.pluginId)) { pendingHop.value = value; hopCondition.value = graph.value.kind === 'job' ? 'success' : source.pluginId === 'FilterRows' ? 'true' : 'case'; hopValue.value = ''; hopOpen.value = true } else saveHop(value)
}
function saveHop(value) {
  const hop = pendingHop.value || value; if (!hop?.sourceId || !canChange.value) return
  try {
    const source = graph.value.nodes.find(node => node.id === hop.sourceId), target = graph.value.nodes.find(node => node.id === hop.targetId)
    if (source?.id === target?.id) throw new Error('分支不能指向自身')
    if (source?.pluginId === 'FilterRows') { setText(source.element, hopCondition.value === 'true' ? 'send_true_to' : 'send_false_to', target.name); syncBranchConnections(document.value, source) }
    else if (source?.pluginId === 'SwitchCase') { if (hopCondition.value === 'default') setText(source.element, 'default_target_step', target.name); else appendRow(source.element, 'cases/case', { value: hopValue.value, target_step: target.name }); syncBranchConnections(document.value, source) }
    else connect(document.value, hop.sourceId, hop.targetId, hopCondition.value)
    changed(); selectedEdgeId.value = graph.value.connections.find(edge => edge.sourceId === hop.sourceId && edge.targetId === hop.targetId)?.id || ''; selectedNodeId.value = ''; editorGeneration.value++; hopOpen.value = false; pendingHop.value = null
  } catch (cause) { error.value = cause.message }
}
async function deleteNode() { const node = selectedNode.value, context = editorContext(); if (!node || !canChange.value) return; try { await proxy.$modal.confirm(`删除“${node.name}”及相关连接？`); if (!canChange.value || !sameEditorContext(context, editorContext())) return; removeNode(document.value, node); selectedNodeId.value = ''; configDirty.value = false; changed() } catch { } }
function deleteEdge() { if (!canChange.value) return; if (selectedEdge.value) { removeGraphConnection(document.value, selectedEdge.value); selectedEdgeId.value = ''; changed() } }
function setEdgeEnabled(value) { if (!canChange.value) return; setText(selectedEdge.value.element, 'enabled', value ? 'Y' : 'N'); changed() }
function setEdgeCondition(value) { if (!canChange.value) return; setText(selectedEdge.value.element, 'unconditional', value === 'unconditional' ? 'Y' : 'N'); setText(selectedEdge.value.element, 'evaluation', value === 'failure' ? 'N' : 'Y'); changed() }
async function validate() {
  if (!canRun.value || busy.value || executionProtected.value || !await save()) return; validating.value = true; error.value = ''; validation.value = null; const context = editorContext()
  try {
    const result = (await api.validateKettleDefinition(context.id)).data; if (disposed || !sameEditorContext(context, editorContext())) return; const current = (await api.kettleDefinition(context.id)).data; if (disposed || !sameEditorContext(context, editorContext())) return; if (current.revision !== context.revision) { error.value = '任务已在其他页面更新，请重新读取任务后检查字段'; return } validationContext.value = context; validation.value = result; resultsOpen.value = true; if (compactViewport.value) inspectorOpen.value = false
    const feedback = validationFeedback(validation.value, graph.value.kind)
    if (feedback.type === 'success') proxy.$modal.msgSuccess(feedback.title)
    else if (feedback.type === 'error') proxy.$modal.msgError(feedback.title)
    else proxy.$modal.msgWarning(feedback.title)
  }
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
    run.value = result.data; clearPending(); events.value = []; cursor = 0; resultsOpen.value = true; previewNodeName.value = run.value.previewStep || target; inspectorExpanded.value = false; if (compactViewport.value) inspectorOpen.value = false; await poll()
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
  if (!canRun.value || busy.value || activeRun.value || reviewRun.value || submissionRevisionExpired.value || !definition.value || !engineReady.value) return
  if (mode === 'preview' && !canPreviewSelected.value) return
  if (!await save()) return
  pendingSubmission.value = { revision: definition.value.revision, mode, previewStep: mode === 'preview' ? selectedNode.value.name : undefined, rowLimit: 20, requestId: elementId(document.value.createElement('request')) }
  try { localStorage.setItem(`kettle:pending:${definition.value.id}`, JSON.stringify(pendingSubmission.value)) } catch { error.value = '无法保存提交标识，尚未启动任务；请允许本地存储后重试'; pendingSubmission.value = null; return }
  await submitPending()
}
async function poll() {
  if (!run.value || disposed) return; const id = run.value.id
  try { const [current, batch] = await Promise.all([api.kettleRun(id), api.kettleEvents(id, cursor)]); if (disposed || run.value?.id !== id) return; run.value = current.data; events.value = mergeEvents(events.value, batch.data.events || []); cursor = batch.data.nextCursor ?? cursor; if (runActive(run.value.state) || runFinishing(run.value)) pollTimer = setTimeout(poll, 1000) }
  catch (cause) { if (!disposed && run.value?.id === id) { error.value = errorMessage(cause, '运行状态读取失败，请刷新核查'); pollTimer = setTimeout(poll, 3000) } }
}
async function stop() { if (!run.value || stopping.value) return; const id = run.value.id; stopping.value = true; try { const current = (await api.stopKettleRun(id)).data; if (disposed || run.value?.id !== id) return; run.value = current; clearTimeout(pollTimer); await poll() } catch (cause) { if (!disposed && run.value?.id === id) error.value = errorMessage(cause, '停止尚未确认，请继续核查运行状态') } finally { stopping.value = false } }
async function loadHistory() { if (!definition.value) return; const owner = definition.value.id; historyLoading.value = true; try { const data = (await api.kettleRunHistory(owner)).data || []; if (!disposed && definition.value?.id === owner) history.value = data } catch (cause) { if (!disposed && definition.value?.id === owner) error.value = errorMessage(cause, '历史记录加载失败') } finally { historyLoading.value = false } }
async function openHistory() { historyOpen.value = true; await loadHistory() }
async function viewRun(row) { clearTimeout(pollTimer); run.value = row; events.value = []; cursor = 0; previewNodeName.value = row.previewStep || graph.value.nodes.slice(-1)[0]?.name || ''; resultsOpen.value = true; historyOpen.value = false; await poll() }
function choosePreviewNode(name) { if (configDirty.value && nodeEditor.value?.apply() === false) return; previewNodeName.value = name; const node = graph.value.nodes.find(item => item.name === name); if (node) { selectedNodeId.value = node.id; selectedEdgeId.value = ''; inspectorOpen.value = true } }
async function downloadOutput(file) { try { const blob = await api.downloadKettleOutput(run.value.id, file.name); if (blob.type?.includes('json')) throw new Error('文件下载返回错误，请核查运行记录'); saveAs(blob, file.name) } catch (cause) { error.value = errorMessage(cause, '下载失败') } }
function exportDefinition() { if (!document.value) return; if (connectionDirty.value) { connectionsOpen.value = true; proxy.$modal.msgWarning('请先应用或取消连接修改'); return } if (configDirty.value && nodeEditor.value?.apply() === false) return; saveAs(new Blob([xmlText(document.value)], { type: 'application/xml;charset=utf-8' }), graph.value.name + (graph.value.kind === 'job' ? '.kjb' : '.ktr')) }
async function uploadFile(options) { if (!definition.value || !canChange.value) return; uploading.value = true; try { const body = new FormData(); body.append('file', options.file); const result = await api.uploadKettleInputFile(definition.value.id, body); files.value = (await api.kettleInputFiles(definition.value.id)).data; options.onSuccess?.(result); proxy.$modal.msgSuccess('实际输入文件已上传，可在输入节点中选用') } catch (cause) { error.value = errorMessage(cause, '输入文件上传失败'); options.onError?.(cause) } finally { uploading.value = false } }
async function removeFile(file) {
  if (!canChange.value) return
  const owner = definition.value.id
  try {
    await proxy.$modal.confirm(`移除任务输入文件“${file.name}”？`)
    if (!canChange.value || definition.value?.id !== owner) return
    uploading.value = true
    await api.deleteKettleInputFile(owner, file.id)
    const inputs = (await api.kettleInputFiles(owner)).data
    if (!disposed && definition.value?.id === owner) files.value = inputs
  } catch (cause) { if (!disposed && definition.value?.id === owner && cause?.message) error.value = errorMessage(cause, '文件移除失败') }
  finally { uploading.value = false }
}
function openConnections() { if (configDirty.value && nodeEditor.value?.apply() === false) return; connectionsOpen.value = true }
function resetConnectionDraft() { connectionDraft.value = null; connectionId.value = ''; connectionOwner.value = ''; connectionDirty.value = false }
async function discardConnectionDraft() { if (!connectionDirty.value) return true; try { await proxy.$modal.confirm('连接修改尚未应用，是否放弃这次连接修改？'); return true } catch { return false } }
async function closeConnections(done) { if (!await discardConnectionDraft()) return; resetConnectionDraft(); done() }
async function cancelConnection() { if (await discardConnectionDraft()) resetConnectionDraft() }
async function addConnection() { const owner = definition.value?.id; if (!canChange.value || !await discardConnectionDraft() || !canChange.value || definition.value?.id !== owner) return; resetConnectionDraft(); connectionOwner.value = definition.value.id; connectionDraft.value = markRaw(document.value.createElement('connection')); setText(connectionDraft.value, 'name', '新数据库连接'); setText(connectionDraft.value, 'type', 'POSTGRESQL'); setText(connectionDraft.value, 'access', 'Native'); setText(connectionDraft.value, 'port', '5432'); connectionVersion.value++ }
async function editConnection(row) { const owner = definition.value?.id; if (configDirty.value && nodeEditor.value?.apply() === false) return; if (!databaseConnections.value.some(item => item.element === row.element) || !await discardConnectionDraft() || definition.value?.id !== owner) return; resetConnectionDraft(); connectionOwner.value = definition.value.id; connectionId.value = row.id; connectionDraft.value = markRaw(row.element.cloneNode(true)); connectionVersion.value++ }
const connectionRead = key => { connectionVersion.value; return textAt(connectionDraft.value, key) }
function connectionWrite(key, value) { if (!canChange.value || !connectionDraftBelongsTo(connectionOwner.value, definition.value?.id)) return; setText(connectionDraft.value, key, value); connectionDirty.value = true; connectionVersion.value++ }
function applyConnection() {
  if (!canChange.value || !connectionDraft.value || !connectionDraftBelongsTo(connectionOwner.value, definition.value?.id)) return
  if (configDirty.value && nodeEditor.value?.apply() === false) return
  const name = textAt(connectionDraft.value, 'name').trim()
  if (!name || databaseConnections.value.some(row => row.id !== connectionId.value && row.name === name)) { proxy.$modal.msgError('连接名称不能为空或重复'); return }
  const existing = databaseConnections.value.find(row => row.id === connectionId.value), replacement = document.value.importNode(connectionDraft.value, true)
  setText(replacement, 'name', name); elementId(replacement)
  if (existing) { for (const node of graph.value.nodes) if (textAt(node.element, 'connection') === existing.name) setText(node.element, 'connection', name); existing.element.replaceWith(replacement) } else document.value.documentElement.appendChild(replacement)
  resetConnectionDraft(); editorGeneration.value++; changed(); proxy.$modal.msgSuccess('连接配置已应用，保存任务后生效')
}
async function removeConnection(row) { if (!canChange.value) return; const context = editorContext(); if (graph.value.nodes.some(node => textAt(node.element, 'connection') === row.name)) { proxy.$modal.msgWarning('此连接仍被节点使用'); return } try { await proxy.$modal.confirm(`删除连接“${row.name}”？`); if (!canChange.value || !sameEditorContext(context, editorContext())) return; row.element.remove(); changed() } catch { } }
function editorContext() { return { id: definition.value?.id, revision: definition.value?.revision, documentVersion: version.value } }
function rememberTask(id) { if (route.query.task !== id) router.replace({ query: { ...route.query, tab: 'kettle', task: id } }).catch(() => {}) }
function hideInspector() { if (configDirty.value && nodeEditor.value?.apply() === false) return; inspectorOpen.value = false; if (compactViewport.value) libraryOpen.value = false }
function toggleLibrary() { if (resultsExpanded.value) { resultsExpanded.value = false; libraryOpen.value = true; return } const next = !libraryOpen.value; if (next && compactViewport.value) { if (configDirty.value && nodeEditor.value?.apply() === false) return; inspectorOpen.value = false } libraryOpen.value = next }
function showInspector() { resultsExpanded.value = false; inspectorOpen.value = true; if (compactViewport.value) libraryOpen.value = false }
function showTools() { if (configDirty.value && nodeEditor.value?.apply() === false) return; leftTab.value = 'tools'; if (compactViewport.value) inspectorOpen.value = false; libraryOpen.value = true }
function closeResults() { resultsOpen.value = false; resultsExpanded.value = false }
function toggleResults() { if (resultsOpen.value) closeResults(); else resultsOpen.value = true }
function resourceCommand(command) { if (command === 'inputs') assetsOpen.value = true; else if (command === 'connections') openConnections() }
function taskCommand(command) { if (command === 'settings') openTaskSettings(); else if (command === 'schedules') schedulesOpen.value = true; else if (command === 'history') openHistory(); else if (command === 'export') exportDefinition() }
const canViewHistory = row => historySelectionAllowed(run.value, row, pendingSubmission.value)
async function openRecordedRun(row) { if (!canViewHistory(row)) { proxy.$modal.msgWarning('请先等待当前执行结束或核查状态，再切换其他运行记录'); return } await viewRun(row) }
async function openScheduledRun(row) { if (!canViewHistory(row)) { proxy.$modal.msgWarning('当前执行尚未结束或待核查，请先处理当前运行'); return } schedulesOpen.value = false; await viewRun(row) }
function updateWorkspaceSize() {
  cancelAnimationFrame(workspaceFrame)
  workspaceFrame = requestAnimationFrame(() => {
    if (!workbenchRoot.value || disposed) return
    const compact = window.innerWidth <= 1100
    if (compact && !compactViewport.value && definition.value) libraryOpen.value = false
    compactViewport.value = compact
    workspaceHeight.value = Math.max(460, window.innerHeight - Math.max(0, workbenchRoot.value.getBoundingClientRect().top) - 16)
  })
}
function beforeUnload(event) { if (dirty.value || configDirty.value || connectionDirty.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(async () => { window.addEventListener('beforeunload', beforeUnload); window.addEventListener('resize', updateWorkspaceSize); await nextTick(); updateWorkspaceSize(); if (typeof ResizeObserver !== 'undefined' && workbenchRoot.value?.parentElement) { workspaceObserver = new ResizeObserver(updateWorkspaceSize); workspaceObserver.observe(workbenchRoot.value.parentElement) } await load(); if (!disposed) { const id = restoredTaskId(route.query.task, definitions.value); if (id) await openDefinition(id) } })
onBeforeUnmount(() => { disposed = true; requestGeneration++; clearTimeout(pollTimer); window.removeEventListener('beforeunload', beforeUnload); window.removeEventListener('resize', updateWorkspaceSize); workspaceObserver?.disconnect(); cancelAnimationFrame(workspaceFrame) })
defineExpose({ canLeave: leaveDraft })
</script>
<style scoped>
.kettle-workbench { display: flex; flex-direction: column; gap: var(--el-font-size-small); min-width: 0; height: var(--kettle-workspace-height, calc(100vh - 180px)); min-height: 460px; color: var(--app-text); }
.kettle-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--el-font-size-small); flex-shrink: 0; }
.kettle-task-heading { min-width: 0; flex: 1 1 260px; }
.kettle-title-line, .kettle-heading-meta, .kettle-primary-actions, .kettle-toolbar-group { display: flex; align-items: center; gap: var(--el-font-size-small); min-width: 0; }
.kettle-title-line strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--el-font-size-large); color: var(--app-heading); }
.kettle-title-line .el-tag { flex-shrink: 0; }
.kettle-heading-meta { flex-wrap: wrap; gap: var(--el-font-size-base); margin-top: 4px; }
.kettle-primary-actions { flex-wrap: wrap; }
.kettle-primary-actions > .el-button + .el-button { margin-left: 0; }
.kettle-notices { flex-shrink: 0; max-height: 160px; overflow: auto; display: grid; gap: var(--el-font-size-extra-small); }
.kettle-body { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(0, 1fr); grid-template-rows: minmax(0, 1fr); overflow: hidden; border: 1px solid var(--surface-border); border-radius: var(--el-border-radius-base); background: var(--surface-bg); }
.kettle-body.has-library { grid-template-columns: 240px minmax(0, 1fr); }
.kettle-body.has-inspector { grid-template-columns: minmax(0, 1fr) 360px; }
.kettle-body.has-library.has-inspector { grid-template-columns: 240px minmax(0, 1fr) 360px; }
.kettle-library, .kettle-inspector { min-width: 0; min-height: 0; margin: 0; padding: var(--el-font-size-small); border: 0; border-radius: 0; box-shadow: none; line-height: var(--el-font-line-height-primary); font-family: inherit; font-size: var(--el-font-size-base); }
.kettle-library { display: flex; flex-direction: column; border-right: 1px solid var(--surface-border); background: var(--surface-subtle); }
.kettle-inspector { border-left: 1px solid var(--surface-border); overflow: hidden; background: var(--surface-bg); }
.kettle-library-actions { display: flex; align-items: center; justify-content: space-between; gap: var(--el-font-size-extra-small); margin-bottom: var(--el-font-size-small); }
.kettle-library-tabs { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.kettle-library-tabs :deep(> .el-tabs__header) { flex-shrink: 0; margin-bottom: var(--el-font-size-small); }
.kettle-library-tabs :deep(> .el-tabs__content) { flex: 1; min-height: 0; }
.kettle-library-tabs :deep(.el-tab-pane) { height: 100%; display: flex; flex-direction: column; gap: var(--el-font-size-extra-small); }
.kettle-library__scroll { flex: 1; min-height: 0; }
.kettle-task-group h3 { display: flex; align-items: center; justify-content: space-between; font-size: var(--el-font-size-small); font-weight: 500; color: var(--app-muted); margin: var(--el-font-size-base) 0 var(--el-font-size-extra-small); }
.kettle-task.el-button { display: flex; justify-content: flex-start; width: 100%; height: auto; margin: 0 0 4px; padding: 8px; white-space: normal; }
.kettle-task.is-current { color: var(--el-color-primary); background: var(--el-fill-color); }
.kettle-task :deep(> span) { display: flex; min-width: 0; width: 100%; align-items: center; gap: 8px; }
.kettle-task-label { display: flex; flex-direction: column; gap: 4px; min-width: 0; text-align: left; }
.kettle-task-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; line-height: 1.4; }
.kettle-task-meta { color: var(--el-text-color-regular); font-size: var(--el-font-size-extra-small); line-height: 1.4; }
.kettle-tool-entry { display: flex; align-items: center; gap: 4px; margin-bottom: 4px; }
.kettle-tool.el-button { flex: 1; min-width: 0; justify-content: flex-start; height: auto; min-height: var(--el-component-size); padding: 4px; margin: 0; }
.kettle-tool :deep(> span) { display: flex; min-width: 0; width: 100%; gap: var(--el-font-size-extra-small); }
.kettle-tool :deep(> span > span) { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kettle-tool-entry > .el-tag { color: var(--el-text-color-regular); }
.kettle-tool-entry > .el-tag, .kettle-tool .el-icon, .kettle-task .el-icon { flex-shrink: 0; }
.kettle-main { display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; }
.kettle-canvas-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--el-font-size-small); padding: var(--el-font-size-small); border-bottom: 1px solid var(--surface-border); flex-shrink: 0; }
.kettle-toolbar-group { flex-wrap: wrap; gap: var(--el-font-size-base); }
.kettle-toolbar-group > .el-button + .el-button { margin-left: 0; }
.kettle-canvas { display: flex; flex: 1; min-height: 0; min-width: 0; }
.kettle-canvas > * { width: 100%; min-height: 0; }
.kettle-canvas > .el-empty { align-self: center; }
.kettle-canvas-status { display: flex; justify-content: space-between; align-items: center; gap: var(--el-font-size-small); padding: var(--el-font-size-extra-small) var(--el-font-size-small); border-top: 1px solid var(--surface-border); flex-shrink: 0; }
.kettle-canvas-status > .el-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kettle-main > .kettle-results { flex: 0 1 34vh; min-height: 200px; max-height: 340px; overflow: auto; }
.results-expanded .kettle-main > .kettle-results { flex: 1; max-height: none; }
.kettle-edge-config { height: 100%; overflow: auto; }
.kettle-edge-config header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--el-font-size-base); }
.kettle-expanded-editor { height: calc(100vh - 220px); min-height: 260px; max-height: 800px; }
.kettle-node-dialog { max-width: 1280px; }
@media (max-width: 1400px) { .kettle-body.has-library { grid-template-columns: 210px minmax(0, 1fr); } .kettle-body.has-inspector { grid-template-columns: minmax(0, 1fr) 320px; } .kettle-body.has-library.has-inspector { grid-template-columns: 210px minmax(0, 1fr) 320px; } }
@media (max-width: 1100px) { .kettle-body, .kettle-body.has-library, .kettle-body.has-inspector, .kettle-body.has-library.has-inspector { grid-template-columns: minmax(0, 1fr); } .kettle-body.has-library .kettle-main, .kettle-body.has-inspector .kettle-main, .kettle-body.has-inspector .kettle-library { display: none; } .kettle-inspector { border-left: 0; } .kettle-library { border-right: 0; } .kettle-workbench { min-height: 520px; } }
@media (max-width: 640px) { .kettle-task-heading { flex-basis: 100%; } .kettle-primary-actions { width: 100%; } .kettle-title-line strong { font-size: var(--el-font-size-medium); } }
</style>
