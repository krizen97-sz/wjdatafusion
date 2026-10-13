<template>
  <el-drawer v-model="open" title="任务运行记录" size="85%" append-to-body destroy-on-close>
    <div class="mb16">
      <el-button icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      <el-text type="info" class="ml10">最近 100 次运行；正在运行的任务自动更新状态。</el-text>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
    <el-table v-loading="loading" :data="rows" row-key="id" empty-text="暂无运行记录，请先在画布中调试或运行任务。" highlight-current-row @current-change="select">
      <el-table-column label="提交时间" min-width="175"><template #default="{ row }">{{ time(row.createdAt) }}</template></el-table-column>
      <el-table-column label="类型" width="90"><template #default="{ row }">{{ modes[row.mode] || row.mode }}</template></el-table-column>
      <el-table-column label="版本" prop="revision" width="80" />
      <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag :type="state(row.state).type">{{ state(row.state).label }}</el-tag></template></el-table-column>
      <el-table-column label="说明" prop="error" min-width="230" show-overflow-tooltip />
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="select(row)">查看详情</el-button></template></el-table-column>
    </el-table>
    <template v-if="selected">
      <el-divider />
      <el-descriptions :column="2" border class="mb16">
        <el-descriptions-item label="运行编号">{{ selected.id }}</el-descriptions-item>
        <el-descriptions-item label="资源清理">{{ detail?.finalized === true ? '已确认' : '尚未确认' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" class="mb16" />
      <el-alert v-if="detail?.error" :title="detail.error" type="warning" :closable="false" class="mb16" />
      <el-tabs v-loading="detailLoading" class="motion-tabs">
        <el-tab-pane label="输出文件">
          <template #label><span class="motion-control-label"><svg-icon icon-class="documentation" class="motion-control-label__icon" /><span class="motion-control-label__text">输出文件</span></span></template>
          <el-text type="info">文件通过完整性校验后归档；文件大小与摘要对应完整产物。</el-text>
          <el-table :data="files" row-key="artifactId" :empty-text="detail?.state === 'SUCCEEDED' ? '尚无通过归档校验的文件，请检查运行说明和日志。' : '运行成功并完成归档后，可在这里下载文件。'">
            <el-table-column label="文件" prop="name" min-width="180" show-overflow-tooltip />
            <el-table-column label="大小" width="120"><template #default="{ row }">{{ row.bytes.toLocaleString() }} 字节</template></el-table-column>
            <el-table-column label="SHA-256" prop="sha256" min-width="240" show-overflow-tooltip />
            <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :loading="downloading === row.artifactId" :disabled="!!downloading" @click="download(row)">下载</el-button></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="运行日志">
          <template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">运行日志</span></span></template>
          <div class="mb8"><el-button :loading="logLoading" @click="loadLogs">{{ logText ? '继续读取' : '读取日志' }}</el-button><el-text type="info" class="ml10">按实际日志位置读取，本窗口最多展示 256 KB。</el-text></div>
          <el-alert v-if="logError" :title="logError" type="error" :closable="false" class="mb8" />
          <el-empty v-if="!logText" description="点击读取日志查看原执行器输出。" />
          <pre v-else class="hdi-run-log">{{ logText }}</pre>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { listHdiRuns, getHdiRun, getHdiArtifacts, getHdiLogs, downloadHdiArtifact } from '@/api/governance/hdi'
import { blobValidate } from '@/utils/ruoyi'
const props = defineProps({ modelValue: Boolean, taskId: { type: [String, Number], default: null } })
const emit = defineEmits(['update:modelValue'])
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const { proxy } = getCurrentInstance()
const rows = ref([]), selected = ref(null), detail = ref(null), files = ref([])
const error = ref(''), detailError = ref(''), loading = ref(false), detailLoading = ref(false), downloading = ref('')
const logText = ref(''), logError = ref(''), logLoading = ref(false)
let generation = 0, selection = 0, timer, cursor = 0
const modes = { DEBUG: '调试', RUN: '手动', SCHEDULE: '定时' }
const states = {
  QUEUED: ['等待准备', 'info'], PREPARING: ['准备环境', 'info'], COMPILING: ['生成配置', 'info'], STARTING: ['启动中', 'primary'],
  RUNNING: ['运行中', 'primary'], STOPPING: ['停止中', 'warning'], SUCCEEDED: ['成功', 'success'], FAILED: ['失败', 'danger'], CANCELLED: ['已取消', 'info'], UNKNOWN: ['需要核查', 'warning']
}
function state(value) { const [label, type] = states[value] || ['未知状态', 'warning']; return { label, type } }
function time(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function message(cause, fallback) { return cause?.message || fallback }
function active(row) { return !['SUCCEEDED', 'FAILED', 'CANCELLED', 'UNKNOWN'].includes(row.state) }
async function load() {
  if (!open.value || !props.taskId || loading.value) return
  const epoch = generation; loading.value = true; error.value = ''; clearTimeout(timer)
  try {
    const response = await listHdiRuns(props.taskId)
    if (epoch !== generation) return
    rows.value = response.data || []
    if (selected.value) {
      selected.value = rows.value.find(row => row.id === selected.value.id) || null
      if (selected.value) await loadDetail()
    }
    if (rows.value.some(active)) timer = setTimeout(load, 2500)
  } catch (cause) { if (epoch === generation) error.value = message(cause, '运行记录读取失败') }
  finally { if (epoch === generation) loading.value = false }
}
function select(row) {
  if (!row || row.id === selected.value?.id) return
  selection++; selected.value = row; detail.value = null; files.value = []; logText.value = ''; logError.value = ''; logLoading.value = false; cursor = 0
  loadDetail()
}
async function loadDetail() {
  if (!selected.value) return
  const epoch = generation, pick = selection, id = selected.value.id
  detailLoading.value = true; detailError.value = ''
  try {
    const [status, artifacts] = await Promise.all([getHdiRun(id), getHdiArtifacts(id)])
    if (epoch !== generation || pick !== selection) return
    detail.value = status.data; files.value = artifacts.data?.artifacts || []
  } catch (cause) { if (epoch === generation && pick === selection) detailError.value = message(cause, '运行详情读取失败') }
  finally { if (epoch === generation && pick === selection) detailLoading.value = false }
}
async function loadLogs() {
  if (!selected.value || logLoading.value) return
  const epoch = generation, pick = selection; logLoading.value = true; logError.value = ''
  try {
    const response = await getHdiLogs(selected.value.id, cursor)
    if (epoch !== generation || pick !== selection) return
    logText.value = (logText.value + response.data.text).slice(-262144); cursor = response.data.nextCursor
    if (!logText.value) logError.value = '当前尚无日志输出。'
  } catch (cause) { if (epoch === generation && pick === selection) logError.value = message(cause, '日志读取失败') }
  finally { if (epoch === generation && pick === selection) logLoading.value = false }
}
async function download(row) {
  if (downloading.value || !selected.value) return
  const epoch = generation, pick = selection, id = selected.value.id; downloading.value = row.artifactId
  try {
    const blob = await downloadHdiArtifact(id, row.artifactId)
    if (!blobValidate(blob)) { await proxy.$download.printErrMsg(blob); return }
    proxy.$download.saveAs(blob, row.name)
  } catch (cause) { if (epoch === generation && pick === selection) detailError.value = message(cause, '文件下载失败') }
  finally { if (epoch === generation) downloading.value = '' }
}
function reset() { generation++; selection++; clearTimeout(timer); loading.value = false; detailLoading.value = false; logLoading.value = false; downloading.value = ''; rows.value = []; selected.value = null; detail.value = null; files.value = []; error.value = ''; detailError.value = '' }
watch(() => [props.modelValue, props.taskId], () => { reset(); if (open.value && props.taskId) load() }, { immediate: true })
onDeactivated(() => { open.value = false; reset() })
onBeforeUnmount(reset)
</script>

<style scoped>
.hdi-run-log { max-height: 45vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; padding: var(--el-component-size-small); background: var(--el-fill-color-light); color: var(--el-text-color-regular); border-radius: var(--el-border-radius-base); }
</style>
