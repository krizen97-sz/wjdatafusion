<template>
  <el-dialog v-model="open" :title="applyToNode ? '加载数据库字典快照' : '读取数据库字典快照'" width="720px" append-to-body :close-on-click-modal="!loading" :close-on-press-escape="!loading" :show-close="!loading" @closed="resetResult">
    <el-alert title="批次快照" description="只读取明确选择的表和字段；加载到节点后随配置及发布版本冻结。数据库后续变化不会自动修改这份快照。" type="info" :closable="false" class="mb16" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="mb16" />
    <el-form label-position="top" :disabled="loading">
      <el-form-item label="数据库连接" required><el-select v-model="form.connectionId" class="snapshot-full" placeholder="选择已保存连接" aria-label="字典数据库连接" @change="resetResult"><el-option v-for="connection in connections" :key="connection.id" :label="connection.name" :value="connection.id" /></el-select></el-form-item>
      <el-row :gutter="16"><el-col :span="8"><el-form-item label="Schema"><el-input v-model="form.schema" @input="resetResult" /></el-form-item></el-col><el-col :span="16"><el-form-item label="字典表或业务视图" required><el-input v-model="form.table" placeholder="例如 governance_demo_camera" @input="resetResult" /></el-form-item></el-col></el-row>
      <el-form-item label="读取字段（逗号分隔）" required><el-input v-model="form.columns" placeholder="camera_code,platform_code,external_code,active" @input="resetResult" /></el-form-item>
      <el-form-item label="排序字段（可选，逗号分隔）"><el-input v-model="form.orderBy" placeholder="字段必须来自上方读取字段" @input="resetResult" /></el-form-item>
    </el-form>
    <el-button icon="Search" :loading="loading" :disabled="!form.connectionId || !form.table.trim() || !form.columns.trim()" @click="readSnapshot">读取快照</el-button>
    <template v-if="result">
      <el-descriptions :column="2" border size="small" class="mt16"><el-descriptions-item label="行数">{{ result.rowCount }}</el-descriptions-item><el-descriptions-item label="读取时间">{{ result.capturedAt }}</el-descriptions-item><el-descriptions-item label="快照摘要" :span="2"><span class="snapshot-hash">{{ result.sha256 }}</span></el-descriptions-item></el-descriptions>
      <p class="snapshot-help">预览保留原始数值文本；不会将大整数经浏览器解析后改写。</p>
      <pre class="snapshot-json">{{ result.rowsJson }}</pre>
    </template>
    <el-empty v-else-if="!loading && !connections.length" description="请先在数据连接页创建连接" :image-size="56" />
    <template #footer><el-button :disabled="loading" @click="open = false">关闭</el-button><el-button v-if="applyToNode" type="primary" :disabled="!result || loading" @click="apply">加载到当前节点</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { listGovernanceConnections, readGovernanceSnapshot } from '@/api/governance/connections'
import { errorMessage } from '../workspaceRules'
const props = defineProps({ modelValue: Boolean, initialConnectionId: { type: String, default: '' }, applyToNode: Boolean })
const emit = defineEmits(['update:modelValue', 'loaded'])
const open = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const form = reactive({ connectionId: '', schema: 'public', table: '', columns: '', orderBy: '' })
const connections = ref([]), result = ref(null), loading = ref(false), error = ref('')
let generation = 0, disposed = false
const columns = value => value.split(/[,，\n]/).map(item => item.trim()).filter(Boolean)
function resetResult() { result.value = null }
watch(() => props.modelValue, async value => {
  const sequence = ++generation
  result.value = null; error.value = ''; loading.value = false
  if (!value) return
  try { const response = await listGovernanceConnections(); if (sequence !== generation || disposed) return; connections.value = response.data || []; form.connectionId = props.initialConnectionId || form.connectionId || connections.value[0]?.id || '' }
  catch (cause) { if (sequence === generation && !disposed) error.value = errorMessage(cause, '连接列表读取失败') }
})
async function readSnapshot() {
  if (loading.value) return
  const sequence = generation
  loading.value = true; error.value = ''; result.value = null
  try {
    const response = await readGovernanceSnapshot(form.connectionId, { schema: form.schema, table: form.table.trim(), columns: columns(form.columns), orderBy: columns(form.orderBy) })
    if (sequence === generation && !disposed) result.value = response.data
  } catch (cause) { if (sequence === generation && !disposed) error.value = errorMessage(cause, '字典快照读取失败') }
  finally { if (sequence === generation && !disposed) loading.value = false }
}
function apply() { if (result.value && !loading.value) { emit('loaded', result.value); open.value = false } }
onBeforeUnmount(() => { disposed = true; ++generation })
</script>
<style scoped>
.snapshot-full { width: 100%; }
.snapshot-json { padding: var(--el-font-size-base); max-height: 240px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; background: var(--surface-subtle); color: var(--app-text); font-size: var(--el-font-size-small); }
.snapshot-help { color: var(--app-muted); font-size: var(--el-font-size-small); }
.snapshot-hash { overflow-wrap: anywhere; }
</style>
