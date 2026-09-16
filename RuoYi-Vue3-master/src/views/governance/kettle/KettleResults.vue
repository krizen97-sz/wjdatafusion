<template>
  <section class="kettle-results" :class="{ 'is-expanded': expanded }" aria-label="测试与运行结果">
    <header class="kettle-results__header">
      <el-space wrap>
        <strong>{{ viewState.title }}</strong>
        <el-tag :type="viewState.status.type" size="small">{{ viewState.status.label }}</el-tag>
        <el-text v-if="run && !viewState.validationView && run.revision != null" type="info" size="small">V{{ run.revision }}</el-text>
      </el-space>
      <el-space class="kettle-results__actions">
        <el-tooltip v-if="run && !viewState.validationView" content="查看运行编号、版本与时间" placement="top">
          <el-button link icon="InfoFilled" @click="traceOpen = true">详情</el-button>
        </el-tooltip>
        <el-button link :icon="expanded ? 'ScaleToOriginal' : 'FullScreen'" :aria-expanded="expanded" @click="emit('toggle-expand')">{{ expanded ? '恢复结果' : '扩大结果' }}</el-button>
        <el-tooltip content="收起结果面板" placement="top"><el-button link icon="Close" aria-label="收起结果面板" @click="emit('close')" /></el-tooltip>
      </el-space>
    </header>

    <el-alert v-if="run && stale && !viewState.validationView" :title="`当前显示${run.revision != null ? `任务 V${run.revision} 的` : '历史'}结果，画布已修改，请重新测试`" type="warning" show-icon :closable="false" class="kettle-results__notice" />
    <el-alert v-if="validation && validationStale && viewState.validationView" title="字段检查结果已过期，请重新获取字段" description="以下结构和诊断来自修改前的配置，不能作为当前画布的检查结果。" type="warning" show-icon :closable="false" class="kettle-results__notice" />
    <el-alert v-if="runMessage && !viewState.validationView" :title="run.error ? '执行异常' : '运行消息'" :type="run.error ? 'error' : 'info'" show-icon :closable="false" class="kettle-results__notice">
      <div class="kettle-results__message-line"><span>{{ resultMessageSummary(runMessage) }}</span><el-button link type="primary" @click="showDetail(run.error ? '执行异常详情' : '运行消息', runMessage)">查看完整内容</el-button></div>
    </el-alert>

    <el-tabs v-model="tab" class="motion-tabs" @tab-click="tabChosen = true">
      <el-tab-pane v-if="validation" label="检查诊断" name="validation"><template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">检查诊断</span></span></template>
        <el-alert :title="validationStale ? '以下为历史检查诊断' : validationState.title" :description="validationStale ? '修改后的配置需要重新检查；历史诊断仅供排查参考。' : validationState.description" :type="validationStale ? 'info' : validationState.type" show-icon :closable="false" class="kettle-results__notice" />
        <el-table v-if="validationState.diagnostics.length" :data="validationState.diagnostics" size="small" :max-height="tableHeight" aria-label="原引擎字段诊断">
          <el-table-column prop="node" label="节点" min-width="140" show-overflow-tooltip />
          <el-table-column prop="directionLabel" label="位置" width="100" />
          <el-table-column prop="errorClass" label="错误类型" min-width="160" show-overflow-tooltip />
          <el-table-column label="原因" min-width="260"><template #default="{ row }"><span class="kettle-results__message">{{ resultMessageSummary(row.message) }}</span><el-button link type="primary" @click="showDetail(`${row.node || '节点'} · ${row.directionLabel}`, row.message, row.errorClass)">完整原因</el-button></template></el-table-column>
          <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="!row.node" @click="emit('select-node', row.node)">定位节点</el-button></template></el-table-column>
        </el-table>
        <el-button v-if="validation.fieldsResolved === true" link type="primary" @click="selectTab('fields')">{{ validationStale ? '查看历史字段结构' : '查看字段结构' }}</el-button>
      </el-tab-pane>

      <el-tab-pane label="数据样本" name="data"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">数据样本</span></span></template>
        <div class="kettle-results__filters">
          <el-select class="kettle-results__node-select" :model-value="selectedNode" clearable filterable placeholder="全部已记录节点" aria-label="数据样本节点" @update:model-value="emit('select-node', $event)"><el-option v-for="name in nodeNames" :key="name" :value="name" :label="name" /></el-select>
          <span id="kettle-result-direction" class="kettle-results__meta">方向</span>
          <el-radio-group v-model="direction" aria-labelledby="kettle-result-direction"><el-radio-button value="read">输入</el-radio-button><el-radio-button value="written">输出</el-radio-button><el-radio-button value="error">错误行</el-radio-button></el-radio-group>
          <el-text type="info" size="small">当前 {{ rows.length }} 条{{ sampleDirectionLabel(direction) }}样本</el-text>
          <el-tooltip content="此处仅显示服务保留的观察样本；实际处理计数请查看步骤指标，不能用样本条数替代。" placement="top"><el-button link type="primary" @click="selectTab('metrics')">查看处理计数</el-button></el-tooltip>
        </div>
        <el-table :data="rows" size="small" :max-height="tableHeight" :empty-text="sampleEmptyText(run, direction)" aria-label="节点数据样本">
          <el-table-column v-if="!selectedNode" prop="node" label="节点" min-width="140" show-overflow-tooltip />
          <el-table-column label="样本序号" prop="rowNumber" width="90" />
          <el-table-column label="副本" prop="copy" width="70" />
          <el-table-column v-for="column in columns" :key="column.name" :min-width="160" show-overflow-tooltip>
            <template #header><span>{{ column.name }}</span><div class="kettle-results__meta">{{ column.type }}<template v-if="column.length != null && column.length >= 0"> · {{ column.length }}</template></div></template>
            <template #default="{ row }"><el-button v-if="fieldIssue(row, column.name)" link type="danger" @click="showDetail(`${column.name} · 样本值读取失败`, fieldIssue(row, column.name).message, fieldIssue(row, column.name).errorClass)">值读取失败 · 查看原因</el-button><el-text v-else-if="row.fields?.[column.name] === null" type="info">NULL</el-text><span v-else>{{ row.fields?.[column.name] }}</span></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="字段结构" name="fields"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">字段结构</span></span></template>
        <div class="kettle-results__filters">
          <el-select class="kettle-results__node-select" :model-value="selectedNode" clearable filterable placeholder="选择字段所属节点" aria-label="字段结构节点" @update:model-value="emit('select-node', $event)"><el-option v-for="name in nodeNames" :key="name" :value="name" :label="name" /></el-select>
          <el-text type="info" size="small">{{ validation ? '字段检查返回的输出结构' : `来自${sampleDirectionLabel(direction)}样本的字段结构` }} · {{ metadata.length }} 个字段</el-text>
        </div>
        <el-table :data="metadata" size="small" :max-height="tableHeight" :empty-text="validation ? '此节点尚未取得输出字段，请查看检查诊断或选择其他节点' : '尚无字段结构，请获取字段或运行节点预览'" aria-label="节点字段结构"><el-table-column prop="name" label="字段" min-width="160" show-overflow-tooltip /><el-table-column prop="type" label="类型" width="130" /><el-table-column label="长度" width="100"><template #default="{ row }">{{ fieldDimension(row.length) }}</template></el-table-column><el-table-column label="精度" width="100"><template #default="{ row }">{{ fieldDimension(row.precision) }}</template></el-table-column><el-table-column prop="origin" label="来源" min-width="180" show-overflow-tooltip /></el-table>
      </el-tab-pane>

      <el-tab-pane label="步骤指标" name="metrics"><template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">步骤指标</span></span></template>
        <p class="kettle-results__help">按节点及副本显示原引擎累计计数；不同节点的计数不能相加作为业务总量。未返回的指标显示“未返回”。</p>
        <el-table :data="metricsRows" size="small" :max-height="tableHeight" :empty-text="run ? '执行服务尚未返回步骤指标' : '运行任务后，可在此查看每个节点的真实处理计数'" aria-label="原引擎步骤指标"><el-table-column prop="node" label="步骤" min-width="180" show-overflow-tooltip /><el-table-column prop="copy" label="副本" width="70" /><el-table-column prop="status" label="原引擎状态" min-width="130" /><el-table-column v-for="metric in metrics" :key="metric.key" :label="metric.label" width="90"><template #default="{ row }">{{ row[metric.key] ?? '未返回' }}</template></el-table-column></el-table>
      </el-tab-pane>

      <el-tab-pane label="运行日志" name="logs"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">运行日志</span></span></template>
        <p class="kettle-results__help">显示已接收的运行事件。点击“查看详情”阅读完整消息和节点信息。</p>
        <el-table :data="logs" size="small" :max-height="tableHeight" :empty-text="run ? '执行服务尚未返回日志；如有执行异常，请查看上方完整原因' : '预览或运行任务后，可在此查看执行日志'" aria-label="运行日志">
          <el-table-column label="时间" width="165"><template #default="{ row }">{{ formatResultTime(row.time) }}</template></el-table-column>
          <el-table-column label="步骤" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ row.node || row.entry || '任务' }}</template></el-table-column>
          <el-table-column label="事件" width="120"><template #default="{ row }">{{ eventLabel(row.type) }}</template></el-table-column>
          <el-table-column label="内容" min-width="300"><template #default="{ row }"><span class="kettle-results__message">{{ resultMessageSummary(resultLogText(row)) || '事件未附消息' }}</span></template></el-table-column>
          <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="showDetail(`${row.node || row.entry || '任务'} · ${eventLabel(row.type)}`, resultLogText(row) || '事件未附消息', formatResultTime(row.time))">查看详情</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="输出文件" name="files"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">输出文件</span></span></template>
        <p class="kettle-results__help">仅列出本次执行返回的文件；数据库或消息队列中的写入结果不会显示在此处。</p>
        <el-table :data="outputFiles" size="small" :max-height="tableHeight" :empty-text="run ? '本次执行尚未返回输出文件，请结合步骤指标和日志核对输出目标' : '运行后可在此下载生成的输出文件'" aria-label="运行输出文件"><el-table-column prop="name" label="文件" min-width="240" show-overflow-tooltip /><el-table-column prop="bytes" label="字节数" width="120" /><el-table-column label="状态" width="140"><template #default="{ row }"><el-tag :type="row.partial ? 'warning' : 'success'" size="small">{{ row.partial ? '部分输出' : '已生成' }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="emit('download', row)">下载</el-button></template></el-table-column></el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailOpen" :title="detail.title" width="720px" append-to-body class="kettle-result-detail-dialog">
      <el-text v-if="detail.context" type="info" class="kettle-results__detail-context">{{ detail.context }}</el-text>
      <pre class="kettle-results__detail-message">{{ detail.text }}</pre>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button></template>
    </el-dialog>
    <el-dialog v-model="traceOpen" title="运行详情" width="720px" append-to-body>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="运行编号"><span class="kettle-results__trace-value">{{ run?.id || '未返回' }}</span></el-descriptions-item>
        <el-descriptions-item label="任务版本">{{ run?.revision != null ? `V${run.revision}` : '未返回' }}</el-descriptions-item>
        <el-descriptions-item label="执行类型">{{ run?.mode === 'preview' ? '节点预览' : '任务运行' }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">{{ viewState.status.label }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatResultTime(run?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatResultTime(run?.finishedAt) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="traceOpen = false">关闭</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { jobNodeStates, nodeRows, rowColumns } from './runRules'
import { validationFeedback } from './workbenchRules'
import { formatResultTime, preferredResultTab, resultLogText, resultMessageSummary, resultMessageText, resultViewState, sampleDirectionLabel, sampleEmptyText } from './resultPresentation'

const props = defineProps({ run: Object, stale: Boolean, expanded: Boolean, events: { type: Array, default: () => [] }, selectedNode: String, schema: { type: Array, default: () => [] }, validation: Object, validationKind: String, validationStale: Boolean })
const emit = defineEmits(['select-node', 'download', 'close', 'toggle-expand'])
const tab = ref('data'), direction = ref('written'), tabChosen = ref(false), autoTabSource = ref('run')
const detailOpen = ref(false), traceOpen = ref(false), detail = ref({ title: '', text: '', context: '' })
const validationState = computed(() => validationFeedback(props.validation, props.validationKind))
const viewState = computed(() => resultViewState({ ...props, tab: tab.value }))
const tableHeight = computed(() => props.expanded ? 520 : 240)
const runMessage = computed(() => resultMessageText(props.run?.error || props.run?.message))
watch(() => props.run?.id, id => {
  if (!id) return
  autoTabSource.value = 'run'; tabChosen.value = false; tab.value = preferredResultTab(props.run)
}, { immediate: true })
watch(() => props.run?.state, () => {
  if (autoTabSource.value === 'run' && !tabChosen.value) tab.value = preferredResultTab(props.run)
})
watch(() => props.validation, value => {
  if (value) { autoTabSource.value = 'validation'; tabChosen.value = false; tab.value = 'validation' }
  else if (tab.value === 'validation') { autoTabSource.value = 'run'; tab.value = preferredResultTab(props.run) }
}, { immediate: true })
const rows = computed(() => nodeRows(props.events, props.selectedNode, direction.value))
const columns = computed(() => rowColumns(rows.value))
const metadata = computed(() => props.validation || props.schema.length ? props.schema : columns.value)
const nodeNames = computed(() => [...new Set([...props.events.map(event => event.node), ...(props.run?.nodes || []).map(node => node.node), ...(props.validation?.nodes || []).map(node => node.name)].filter(Boolean))])
const metricsRows = computed(() => props.run?.kind === 'job' || props.run?.mode === 'job' ? jobNodeStates(props.events, props.run.state) : props.run?.nodes || [])
const fieldIssue = (row, name) => row.fieldErrors?.find(field => field.name === name)
const fieldDimension = value => value == null || value < 0 ? '未指定' : value
const logs = computed(() => props.events.filter(event => !['row', 'metrics'].includes(event.type)))
const outputFiles = computed(() => (props.run?.files || []).filter(file => file.role !== 'input'))
const metrics = [{ key: 'files', label: '文件数' }, { key: 'read', label: '读入行' }, { key: 'written', label: '写出行' }, { key: 'input', label: '源读取' }, { key: 'output', label: '目标写入' }, { key: 'rejected', label: '拒绝行' }, { key: 'errors', label: '错误数' }]
const eventLabel = type => ({ state: '运行状态', terminal: '运行结束', log: '引擎日志', native_log: '引擎日志', error: '错误', validation: '节点检查', job_entry: '作业节点', jobEntry: '作业节点', 'job-entry': '作业节点', 'job-transformation': '子转换', 'ftp-batch': 'FTP批次' })[type] || type
function selectTab(name) { tabChosen.value = true; tab.value = name }
function showDetail(title, text, context = '') { detail.value = { title, text: resultMessageText(text) || '未返回具体原因', context }; detailOpen.value = true }
</script>
<style scoped>
.kettle-results { min-width: 0; border-top: 1px solid var(--el-border-color); padding: var(--el-font-size-base); background: var(--surface-bg); }
.kettle-results__header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--el-font-size-small); margin-bottom: var(--el-font-size-small); }
.kettle-results__header strong { color: var(--app-heading); }
.kettle-results__actions { flex-shrink: 0; }
.kettle-results__notice { margin-bottom: var(--el-font-size-small); }
.kettle-results__filters { display: flex; flex-wrap: wrap; align-items: center; gap: var(--el-font-size-small); margin-bottom: var(--el-font-size-small); }
.kettle-results__node-select { width: 220px; max-width: 100%; }
.kettle-results__meta { font-size: var(--el-font-size-extra-small); color: var(--app-muted); font-weight: 400; }
.kettle-results__help { margin: 0 0 var(--el-font-size-small); color: var(--app-muted); font-size: var(--el-font-size-extra-small); line-height: var(--el-font-line-height-primary); }
.kettle-results__message { white-space: pre-wrap; overflow-wrap: break-word; overflow-wrap: anywhere; }
.kettle-results__message-line { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--el-font-size-small); overflow-wrap: break-word; overflow-wrap: anywhere; }
.kettle-results__detail-context, .kettle-results__trace-value { overflow-wrap: break-word; overflow-wrap: anywhere; }
.kettle-results__detail-message { max-height: 60vh; overflow: auto; padding: var(--el-font-size-base); background: var(--surface-muted); color: var(--app-text); border-radius: var(--el-border-radius-base); font-size: var(--el-font-size-small); line-height: var(--el-font-line-height-primary); white-space: pre-wrap; overflow-wrap: break-word; overflow-wrap: anywhere; }
</style>
