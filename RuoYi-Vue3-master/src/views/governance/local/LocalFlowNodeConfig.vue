<template>
  <section class="local-node-config" aria-label="节点配置">
    <header><strong>{{ localNodeKind(node).label }}</strong><el-button link icon="Close" aria-label="收起节点配置" @click="$emit('close')" /></header>
    <el-scrollbar class="local-node-scroll">
      <el-form label-position="top" :disabled="readonly">
        <el-form-item label="节点名称"><el-input :model-value="node.name" maxlength="80" @update:model-value="rename" /></el-form-item>
        <template v-if="['CSV_INPUT', 'JSON_INPUT'].includes(node.type)">
          <el-form-item label="输入数据"><el-input type="textarea" :rows="10" :model-value="config.content" :placeholder="node.type === 'CSV_INPUT' ? '粘贴包含表头的CSV数据' : '粘贴JSON对象数组'" @update:model-value="patch('content', $event)" /></el-form-item>
          <el-upload action="#" :http-request="readFile" :show-file-list="false" :disabled="readonly || reading" :accept="node.type === 'CSV_INPUT' ? '.csv,.txt' : '.json,.txt'"><el-button icon="Upload" :loading="reading">读取本地文本文件</el-button></el-upload>
          <el-text type="info" size="small">文件内容保存到此节点，最大 {{ Math.floor(maxBytes / 1024) }} KiB；仅接受UTF-8文本。</el-text>
          <template v-if="node.type === 'CSV_INPUT'"><el-form-item label="分隔符"><el-input :model-value="config.delimiter" maxlength="1" @update:model-value="patch('delimiter', $event)" /></el-form-item><el-form-item label="首行是字段名"><el-switch :model-value="config.header" @update:model-value="patch('header', $event)" /></el-form-item></template>
        </template>
        <template v-else-if="node.type === 'FIELD_MAP'">
          <el-form-item label="保留未映射字段"><el-switch :model-value="config.keepUnmapped" @update:model-value="patch('keepUnmapped', $event)" /></el-form-item>
          <el-text type="info" size="small">按顺序选择输入字段、输出名称与空白处理。</el-text>
          <div v-for="(row, index) in config.fields" :key="index" class="local-config-row">
            <el-form-item :label="`映射 ${index + 1}：输入字段`"><el-input :model-value="row.source" @update:model-value="rowPatch('fields', index, 'source', $event)" /></el-form-item>
            <el-form-item label="输出字段"><el-input :model-value="row.target" @update:model-value="rowPatch('fields', index, 'target', $event)" /></el-form-item>
            <el-form-item label="去除首尾空白"><el-switch :model-value="row.trim" @update:model-value="rowPatch('fields', index, 'trim', $event)" /></el-form-item>
            <el-form-item label="默认值（可选）"><el-input :model-value="row.defaultValue" clearable @update:model-value="rowPatch('fields', index, 'defaultValue', $event)" /></el-form-item>
            <el-button link type="danger" @click="removeRow('fields', index)">移除此映射</el-button>
          </div>
          <el-button icon="Plus" @click="addRow('fields', { source: '', target: '', trim: true })">添加字段映射</el-button>
        </template>
        <template v-else-if="node.type === 'FILTER'">
          <el-form-item label="过滤字段"><el-input :model-value="config.field" @update:model-value="patch('field', $event)" /></el-form-item>
          <el-form-item label="比较方式"><el-select :model-value="config.operator" @update:model-value="patch('operator', $event)"><el-option v-for="item in operators" :key="item.value" :value="item.value" :label="item.label" /></el-select></el-form-item>
          <template v-if="!['IS_NULL', 'IS_NOT_NULL'].includes(config.operator)"><el-form-item label="比较值类型"><el-select :model-value="config.valueType" @update:model-value="patch('valueType', $event)"><el-option label="文本" value="STRING" /><el-option label="数字" value="NUMBER" /></el-select></el-form-item><el-form-item label="比较值"><el-input :model-value="String(config.value ?? '')" @update:model-value="patch('value', $event)" /></el-form-item></template>
          <el-text type="info" size="small">只保留满足条件的数据。</el-text>
        </template>
        <template v-else-if="node.type === 'SORT'">
          <div v-for="(row, index) in config.fields" :key="index" class="local-config-row"><el-form-item :label="`排序字段 ${index + 1}`"><el-input :model-value="row.field" @update:model-value="rowPatch('fields', index, 'field', $event)" /></el-form-item><el-form-item label="排序方向"><el-select :model-value="row.direction" @update:model-value="rowPatch('fields', index, 'direction', $event)"><el-option label="升序" value="ASC" /><el-option label="降序" value="DESC" /></el-select></el-form-item><el-form-item label="字段类型"><el-select :model-value="row.valueType" @update:model-value="rowPatch('fields', index, 'valueType', $event)"><el-option label="文本" value="STRING" /><el-option label="数字" value="NUMBER" /></el-select></el-form-item><el-button link type="danger" @click="removeRow('fields', index)">移除此排序字段</el-button></div>
          <el-button icon="Plus" @click="addRow('fields', { field: '', direction: 'ASC', valueType: 'STRING' })">添加排序字段</el-button>
        </template>
        <template v-else-if="node.type === 'DEDUP'"><el-form-item label="去重字段，每行一个"><el-input type="textarea" :rows="5" :model-value="config.fields?.join('\n')" @update:model-value="patch('fields', $event.split('\n'))" /></el-form-item><el-text type="info" size="small">组合字段完全相同时保留第一条记录。</el-text></template>
        <template v-else-if="node.type === 'LOOKUP'"><el-alert title="字典关联使用当前节点中的静态JSON数据" description="rows为字典行，matches配置匹配字段，returns配置返回字段；不连接外部数据库。" type="info" :closable="false" /><el-form-item label="关联配置JSON"><el-input v-model="lookupDraft" type="textarea" :rows="18" @input="lookupDirty = true; $emit('draft-change', true)" /></el-form-item><el-button type="primary" :disabled="!lookupDirty" @click="applyLookup">应用关联配置</el-button><el-text v-if="lookupError" type="danger">{{ lookupError }}</el-text></template>
        <template v-else-if="node.type === 'UNION'"><el-alert title="连接两个或更多上游节点，按连接顺序合并数据" description="合并不会自动去重；需要去重时添加独立去重节点。" type="info" :closable="false" /></template>
        <template v-else-if="node.type === 'OUTPUT'"><el-form-item label="输出格式"><el-select :model-value="config.format" @update:model-value="patch('format', $event)"><el-option label="CSV表格" value="CSV" /><el-option label="JSON数据" value="JSON" /></el-select></el-form-item><el-form-item label="文件名"><el-input :model-value="config.filename" placeholder="result.csv" @update:model-value="patch('filename', $event)" /></el-form-item><el-text type="info" size="small">正式运行成功后在“输出文件”下载。预览只显示采样数据，不发布文件。</el-text></template>
      </el-form>
    </el-scrollbar>
    <footer><el-button v-if="canRun" icon="View" :disabled="readonly" @click="$emit('preview')">预览此节点</el-button><el-button link type="danger" :disabled="readonly" @click="$emit('remove')">删除节点</el-button></footer>
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { cloneFlowValue, localNodeKind } from './localFlowRules'
const props = defineProps({ node: { type: Object, required: true }, readonly: Boolean, canRun: Boolean, maxBytes: { type: Number, default: 1048576 } })
const emit = defineEmits(['update', 'close', 'remove', 'preview', 'error', 'draft-change'])
const config = computed(() => props.node.config || {}), reading = ref(false), lookupDraft = ref(''), lookupDirty = ref(false), lookupError = ref('')
const operators = [{ value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }, { value: 'IS_NULL', label: '为空' }, { value: 'IS_NOT_NULL', label: '不为空' }, { value: 'GT', label: '大于' }, { value: 'GE', label: '大于等于' }, { value: 'LT', label: '小于' }, { value: 'LE', label: '小于等于' }, { value: 'CONTAINS', label: '包含' }]
watch(() => props.node.id, () => { lookupDraft.value = JSON.stringify(props.node.config || {}, null, 2); lookupDirty.value = false; lookupError.value = '' }, { immediate: true })
function update(change) { if (!props.readonly) emit('update', { ...cloneFlowValue(props.node), ...change }) }
function rename(name) { update({ name }) }
function patch(key, value) { update({ config: { ...cloneFlowValue(config.value), [key]: value } }) }
function rowPatch(key, index, field, value) { const rows = cloneFlowValue(config.value[key]); rows[index][field] = value; patch(key, rows) }
function addRow(key, value) { patch(key, [...(config.value[key] || []), value]) }
function removeRow(key, index) { patch(key, config.value[key].filter((_, i) => i !== index)) }
function applyLookup() {
  if (!lookupDirty.value) return true
  if (props.readonly) return false
  try { const value = JSON.parse(lookupDraft.value); if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error(); update({ config: value }); lookupDirty.value = false; lookupError.value = ''; emit('draft-change', false); return true }
  catch { lookupError.value = '请填写有效的JSON配置对象，再应用或切换节点'; return false }
}
async function readFile(options) {
  const owner = props.node.id
  if (props.readonly || reading.value) return
  if (options.file.size > props.maxBytes) { const error = new Error(`文本文件不能超过${Math.floor(props.maxBytes / 1024)} KiB`); emit('error', error.message); options.onError?.(error); return }
  reading.value = true
  try {
    const content = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(new Error('文件读取失败')); reader.readAsText(options.file, 'UTF-8') })
    if (owner !== props.node.id || props.readonly) throw new Error('节点已切换，未覆盖其他节点的数据')
    if (content.includes('\uFFFD')) throw new Error('文件含无法识别的字符，请转换为UTF-8后重试')
    patch('content', content.replace(/^\uFEFF/, '')); options.onSuccess?.({})
  } catch (error) { emit('error', error.message); options.onError?.(error) }
  finally { reading.value = false }
}
defineExpose({ apply: applyLookup })
</script>
<style scoped>
.local-node-config { display: flex; flex-direction: column; height: 100%; min-height: 0; gap: var(--el-font-size-small); }
.local-node-config header, .local-node-config footer { display: flex; align-items: center; justify-content: space-between; gap: var(--el-font-size-small); flex-shrink: 0; }
.local-node-config header strong { color: var(--app-heading); }
.local-node-scroll { flex: 1; min-height: 0; }
.local-config-row { padding-block: var(--el-font-size-small); border-bottom: 1px solid var(--surface-border); margin-bottom: var(--el-font-size-small); }
.local-node-config .el-select { width: 100%; }
.local-node-config .el-text { display: block; margin-block: var(--el-font-size-small); }
</style>
