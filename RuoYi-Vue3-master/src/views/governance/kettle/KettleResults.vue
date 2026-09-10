<template>
  <section class="kettle-results">
    <header><el-space wrap><strong>{{ run?.mode === 'preview' ? '节点实时预览' : '运行结果' }}</strong><el-tag v-if="run" :type="runStatus(run.state).type">{{ runStatus(run.state).label }}</el-tag><el-text v-if="run" type="info" size="small">{{ run.id }}</el-text></el-space><el-button link icon="Close" aria-label="收起运行结果" @click="emit('close')" /></header>
    <el-alert v-if="run && stale" :title="`以下结果来自任务 V${run.revision}，当前画布修改尚未运行`" type="warning" :closable="false" class="mb16" />
    <el-alert v-if="run?.error || run?.message" :title="run.error || run.message" type="error" :closable="false" class="mb16" />
    <el-tabs v-model="tab" class="motion-tabs">
      <el-tab-pane label="数据预览" name="data"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">数据预览</span></span></template>
        <el-space wrap class="mb16">
          <el-select class="kettle-results__node-select" :model-value="selectedNode" clearable placeholder="选择预览节点" aria-label="预览节点" @update:model-value="emit('select-node', $event)"><el-option v-for="name in nodeNames" :key="name" :value="name" :label="name" /></el-select>
          <el-radio-group v-model="direction"><el-radio-button value="read">输入</el-radio-button><el-radio-button value="written">输出</el-radio-button><el-radio-button value="error">错误行</el-radio-button></el-radio-group>
          <el-text type="info" size="small">当前显示观察样本，实际处理总量见步骤指标</el-text>
        </el-space>
        <el-table :data="rows" size="small" max-height="340" empty-text="此节点尚无该方向的数据，请先预览或运行任务">
          <el-table-column label="行" prop="rowNumber" width="70" />
          <el-table-column label="副本" prop="copy" width="70" />
          <el-table-column v-for="column in columns" :key="column.name" :min-width="160" show-overflow-tooltip>
            <template #header><span>{{ column.name }}</span><div class="kettle-results__meta">{{ column.type }}<template v-if="column.length != null"> · {{ column.length }}</template></div></template>
            <template #default="{ row }"><el-text v-if="row.fields?.[column.name] === null" type="info">NULL</el-text><el-tooltip v-else-if="fieldIssue(row, column.name)" :content="fieldIssue(row, column.name).message" placement="top"><el-text type="danger">{{ fieldIssue(row, column.name).errorClass }}：无法显示此值</el-text></el-tooltip><span v-else>{{ row.fields?.[column.name] }}</span></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="字段结构" name="fields"><template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">字段结构</span></span></template><el-table :data="metadata" size="small" max-height="340" empty-text="选择节点并获取字段或运行预览"><el-table-column prop="name" label="字段" min-width="160" /><el-table-column prop="type" label="类型" width="130" /><el-table-column prop="length" label="长度" width="100" /><el-table-column prop="precision" label="精度" width="100" /><el-table-column prop="origin" label="来源" min-width="180" /></el-table></el-tab-pane>
      <el-tab-pane label="步骤指标" name="metrics"><template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">步骤指标</span></span></template><el-table :data="metricsRows" size="small" max-height="340" empty-text="暂无执行指标"><el-table-column prop="node" label="步骤" min-width="180" /><el-table-column prop="copy" label="副本" width="70" /><el-table-column prop="status" label="原引擎状态" min-width="130" /><el-table-column v-for="metric in metrics" :key="metric.key" :prop="metric.key" :label="metric.label" width="90" /></el-table></el-tab-pane>
      <el-tab-pane label="运行日志" name="logs"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">运行日志</span></span></template><el-table :data="logs" size="small" max-height="340" empty-text="暂无日志"><el-table-column label="时间" width="145"><template #default="{ row }">{{ new Date(row.time).toLocaleTimeString() }}</template></el-table-column><el-table-column label="步骤" min-width="130"><template #default="{ row }">{{ row.node || row.entry || '任务' }}</template></el-table-column><el-table-column label="事件" width="130"><template #default="{ row }">{{ eventLabel(row.type) }}</template></el-table-column><el-table-column label="内容" min-width="450"><template #default="{ row }">{{ logText(row) }}</template></el-table-column></el-table></el-tab-pane>
      <el-tab-pane label="输出文件" name="files"><template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">输出文件</span></span></template><el-table :data="outputFiles" size="small" max-height="340" empty-text="任务目录没有输出文件"><el-table-column prop="name" label="文件" min-width="240" /><el-table-column prop="bytes" label="字节数" width="120" /><el-table-column label="状态" width="140"><template #default="{ row }"><el-tag :type="row.partial ? 'warning' : 'success'">{{ row.partial ? '部分输出' : '已生成' }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="emit('download', row)">下载</el-button></template></el-table-column></el-table></el-tab-pane>
    </el-tabs>
  </section>
</template>
<script setup>
import { computed, ref } from 'vue'
import { jobNodeStates, nodeRows, rowColumns, runStatus } from './runRules'
const props = defineProps({ run: Object, stale: Boolean, events: { type: Array, default: () => [] }, selectedNode: String, schema: { type: Array, default: () => [] } })
const emit = defineEmits(['select-node', 'download', 'close'])
const tab = ref('data'), direction = ref('written')
const rows = computed(() => nodeRows(props.events, props.selectedNode, direction.value))
const columns = computed(() => rowColumns(rows.value))
const metadata = computed(() => props.schema.length ? props.schema : columns.value)
const nodeNames = computed(() => [...new Set([...props.events.map(event => event.node), ...(props.run?.nodes || []).map(node => node.node)].filter(Boolean))])
const metricsRows = computed(() => props.run?.kind === 'job' ? jobNodeStates(props.events, props.run.state) : props.run?.nodes || [])
const fieldIssue = (row, name) => row.fieldErrors?.find(field => field.name === name)
const logs = computed(() => props.events.filter(event => !['row', 'metrics'].includes(event.type)))
const outputFiles = computed(() => (props.run?.files || []).filter(file => file.role !== 'input'))
const metrics = [{ key: 'files', label: '文件数' }, { key: 'read', label: '读入行' }, { key: 'written', label: '写出行' }, { key: 'input', label: '源读取' }, { key: 'output', label: '目标写入' }, { key: 'rejected', label: '拒绝行' }, { key: 'errors', label: '错误数' }]
const eventLabel = type => ({ state: '运行状态', terminal: '运行结束', log: '引擎日志', native_log: '引擎日志', error: '错误', validation: '节点检查', job_entry: '作业节点', jobEntry: '作业节点', 'job-entry': '作业节点', 'job-transformation': '子转换', 'ftp-batch': 'FTP批次' })[type] || type
function logText(event) { if (event.type === 'job-entry') return event.phase === 'BEFORE' ? '开始执行' : `${event.resultBoolean ? '完成' : '未成功'} · 错误 ${event.errors ?? 0} · 文件 ${event.files ?? 0}`; return event.message || event.line || event.error || event.state || event.status || (event.errors != null ? `错误数 ${event.errors}` : '') }
</script>
<style scoped>
.kettle-results { border-top: 1px solid var(--el-border-color); padding: var(--el-component-size-small); background: var(--surface-bg); }
.kettle-results header { display: flex; align-items: center; justify-content: space-between; gap: var(--el-font-size-base); margin-bottom: var(--el-font-size-base); }
.kettle-results__node-select { width: 220px; }
.kettle-results__meta { font-size: var(--el-font-size-extra-small); color: var(--app-muted); font-weight: 400; }
</style>
