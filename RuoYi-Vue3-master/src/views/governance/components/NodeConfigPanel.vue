<template>
  <section class="governance-node-config" role="complementary" aria-label="节点配置">
    <template v-if="node">
      <header class="governance-node-config__heading">
        <div class="governance-node-config__title">
          <el-icon><component :is="kind.icon" /></el-icon>
          <h3>{{ kind.label }}</h3>
          <el-tag v-if="dirty" size="small" type="warning" effect="plain">未保存</el-tag>
        </div>
        <p>{{ kind.description }}</p>
      </header>

      <el-scrollbar class="governance-node-config__scroll">
        <div class="governance-node-config__body">
          <el-alert v-if="readonly || node.editable === false" title="当前节点只读" type="info" :closable="false" show-icon />
          <el-alert v-if="node.issues?.length" title="节点配置待检查" type="warning" :closable="false" show-icon>
            <ul class="governance-node-config__issues"><li v-for="issue in node.issues" :key="issue">{{ issue }}</li></ul>
          </el-alert>

          <el-form ref="formRef" :model="draft" label-position="top" :disabled="locked" @submit.prevent="save">
            <el-form-item label="节点名称" prop="name" :rules="nameRules">
              <el-input v-model="draft.name" maxlength="80" placeholder="输入便于识别的名称" />
            </el-form-item>

            <template v-if="kind.key === 'source'">
              <el-alert title="样本由测试区提供" description="运行测试时，将使用下方测试区的 JSON 内容。此节点每次注入一份样本。" type="info" :closable="false" show-icon />
            </template>

            <template v-else-if="dynamicFields">
              <el-form-item :label="rowsLabel" prop="rows" :rules="rowRules">
                <el-table :data="draft.rows" class="governance-node-config__fields" size="small" empty-text="尚未配置字段" table-layout="fixed">
                  <el-table-column :label="kind.key === 'route' ? '分支与条件' : '字段与取值'">
                    <template #default="{ row, $index }">
                      <div class="governance-node-config__field">
                        <el-input v-model="row.name" :aria-label="`${rowsLabel} ${$index + 1} 名称`" :placeholder="kind.key === 'route' ? '例如 accepted' : '例如 sample.value'" />
                        <el-input :model-value="row.value ?? ''" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" :aria-label="`${rowsLabel} ${$index + 1} ${valueLabel}`" :placeholder="valuePlaceholder" @update:model-value="row.value = $event" />
                        <el-button link type="danger" icon="Delete" :aria-label="`移除第 ${$index + 1} 个${rowsLabel}`" @click="removeRow($index)">移除</el-button>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
                <el-button link type="primary" icon="Plus" @click="addRow">{{ kind.key === 'route' ? '添加条件分支' : '添加字段' }}</el-button>
              </el-form-item>
              <p class="governance-node-config__help">{{ dynamicHelp }}</p>
              <template v-if="kind.key === 'jsonpath'">
                <el-form-item label="返回类型">
                  <el-select :model-value="property('Return Type', 'scalar')" @update:model-value="setProperty('Return Type', $event)">
                    <el-option label="标量（文本、数字等）" value="scalar" />
                    <el-option label="JSON 对象或数组" value="json" />
                    <el-option label="自动判断" value="auto-detect" />
                  </el-select>
                </el-form-item>
              </template>
            </template>

            <lookup-node-fields v-else-if="kind.key === 'lookup'" :properties="draft.properties" :readonly="locked" @update="({ key, value }) => setProperty(key, value)" @load-snapshot="emit('load-snapshot')" />
            <template v-else-if="kind.key === 'jolt'">
              <el-form-item label="JSON 转换规则">
                <el-input :model-value="property('Jolt Specification')" type="textarea" :autosize="{ minRows: 10, maxRows: 22 }" spellcheck="false" placeholder='[{ "operation": "shift", "spec": { "message": "content" } }]' @update:model-value="setProperty('Jolt Specification', $event)" />
              </el-form-item>
              <p class="governance-node-config__help">使用 Jolt Chain 数组，支持 shift、default、remove、cardinality 和 sort。规则作用于 JSON 内容，测试时检查语法和结果。</p>
              <el-form-item label="格式化输出">
                <el-switch :model-value="property('Pretty Print', 'false')" active-value="true" inactive-value="false" active-text="开启" inactive-text="关闭" @update:model-value="setProperty('Pretty Print', $event)" />
              </el-form-item>
            </template>

            <template v-else-if="kind.key === 'delimited'">
              <el-form-item label="字段顺序">
                <el-input :model-value="property('Field Order')" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" placeholder="message,picture" @update:model-value="setProperty('Field Order', $event)" />
                <span class="governance-node-config__help">按输出顺序填写 JSON 字段名，以英文逗号分隔。</span>
              </el-form-item>
              <el-form-item label="分隔符（十六进制）">
                <el-input :model-value="property('Delimiter Hex', '7C 1F')" placeholder="例如 7C 1F" @update:model-value="setProperty('Delimiter Hex', $event)" />
              </el-form-item>
              <el-form-item label="写入表头">
                <el-switch :model-value="property('Include Header', 'true')" active-value="true" inactive-value="false" active-text="包含" inactive-text="不包含" @update:model-value="setProperty('Include Header', $event)" />
              </el-form-item>
              <el-form-item label="分片计数上限">
                <el-input :model-value="property('Split Limit', '75')" inputmode="numeric" placeholder="0 表示不按条数分片" @update:model-value="setProperty('Split Limit', $event)" />
              </el-form-item>
              <el-form-item label="计数方式">
                <el-select :model-value="property('Count Basis', 'DATA_RECORDS')" @update:model-value="setProperty('Count Basis', $event)">
                  <el-option label="仅计算数据记录" value="DATA_RECORDS" />
                  <el-option label="兼容 Kettle（计入表头）" value="KETTLE_HEADER_INCLUSIVE" />
                </el-select>
                <span class="governance-node-config__help">兼容模式计入表头写入。例如上限 75，首片可包含 74 条数据。</span>
              </el-form-item>
              <el-form-item label="文件名前缀">
                <el-input :model-value="property('Filename Prefix', 'dataset')" placeholder="例如 dataset" @update:model-value="setProperty('Filename Prefix', $event)" />
              </el-form-item>
              <el-collapse>
                <el-collapse-item title="按记录到达时间分片" name="age">
                  <el-form-item label="最大文件时长（毫秒）">
                    <el-input :model-value="property('Maximum File Age Millis', '0')" inputmode="numeric" placeholder="0 表示关闭" @update:model-value="setProperty('Maximum File Age Millis', $event)" />
                  </el-form-item>
                  <el-form-item label="到达时间字段">
                    <el-input :model-value="property('Arrival Time Field')" placeholder="例如 arrivalMillis" @update:model-value="setProperty('Arrival Time Field', $event)" />
                  </el-form-item>
                  <p class="governance-node-config__help">启用时需提供单调递增的毫秒时间戳字段；下一条记录超过时长才分片。此配置用于封闭批次，不是定时任务。</p>
                </el-collapse-item>
              </el-collapse>
            </template>

            <el-alert v-else-if="kind.key === 'capture'" title="流程结果观察点" description="将需要观察的成功、空批次和失败分支连接到此处，在测试区查看真实结果。此节点不修改数据，也不会写入外部系统。" type="info" :closable="false" show-icon />
            <el-alert v-else title="此节点暂不支持浏览器配置" description="已保留现有定义。当前面板不会修改其引擎参数。" type="info" :closable="false" show-icon />
          </el-form>

          <el-collapse class="governance-node-config__technical">
            <el-collapse-item title="技术信息" name="technical">
              <el-descriptions :column="1" direction="vertical" size="small">
                <el-descriptions-item label="引擎组件">{{ node.type }}</el-descriptions-item>
                <el-descriptions-item label="节点标识">{{ node.id }}</el-descriptions-item>
                <el-descriptions-item label="定义版本">{{ node.version ?? '未返回' }}</el-descriptions-item>
                <el-descriptions-item label="引擎状态">{{ node.state || '未返回' }}</el-descriptions-item>
                <el-descriptions-item label="输出关系">{{ node.relationships?.join(' / ') || '流程终点' }}</el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-scrollbar>

      <footer class="governance-node-config__footer">
        <div class="governance-node-config__actions">
          <el-button icon="View" :disabled="busy" @click="preview">预览结果</el-button>
          <el-button type="primary" :loading="busy" :disabled="locked || !dirty" @click="save">保存配置</el-button>
        </div>
        <div class="governance-node-config__secondary">
          <el-button link :disabled="busy || !dirty" @click="discard">放弃修改</el-button>
          <el-button link type="danger" icon="Delete" :disabled="locked" @click="remove">删除节点</el-button>
        </div>
      </footer>
    </template>
    <el-empty v-else description="选择画布节点以配置" :image-size="72" />
  </section>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { createNodeDraft, nodeDraftChanged, nodeDraftPayload, nodeDraftRowsError, nodeKind } from '../nodeCatalog.js'
import LookupNodeFields from './LookupNodeFields.vue'

const props = defineProps({ node: { type: Object, default: null }, saving: Boolean, readonly: Boolean })
const emit = defineEmits(['save', 'remove', 'preview', 'dirty-change', 'load-snapshot'])
const formRef = ref(null)
const draft = ref(createNodeDraft(props.node))
const submitting = ref(false)
const kind = computed(() => nodeKind(props.node))
const dirty = computed(() => nodeDraftChanged(props.node, draft.value))
const busy = computed(() => props.saving || submitting.value)
const locked = computed(() => busy.value || props.readonly || props.node?.editable === false || kind.value.key === 'unknown')
const dynamicFields = computed(() => ['jsonpath', 'route', 'attributes'].includes(kind.value.key))
const rowsLabel = computed(() => kind.value.key === 'route' ? '条件分支' : '字段映射')
const valueLabel = computed(() => kind.value.key === 'jsonpath' ? 'JSONPath' : kind.value.key === 'route' ? '条件表达式' : '字段值')
const valuePlaceholder = computed(() => kind.value.key === 'jsonpath' ? '$.message' : kind.value.key === 'route' ? '${sample.value:isEmpty():not()}' : '固定文本或 ${sample.value:trim()}')
const dynamicHelp = computed(() => ({
  jsonpath: '字段名使用 sample. 前缀，例如 sample.value；JSONPath 从 $ 开始。提取值写入字段属性，原始 JSON 内容保持不变。',
  route: '填写分支名称与条件表达式，再从画布连接对应分支。未命中数据进入 unmatched；多个条件命中时可进入多个分支。',
  attributes: '只设置 sample.* 字段属性。可使用固定文本，或对已有字段做 trim、toUpper、toLower 等转换。'
}[kind.value.key] || ''))
const nameRules = [{ required: true, whitespace: true, message: '请填写节点名称', trigger: 'blur' }]
const rowRules = [{ validator: (_rule, rows, callback) => {
  const error = nodeDraftRowsError(props.node, rows)
  callback(error ? new Error(error) : undefined)
}, trigger: 'blur' }]

watch(() => [props.node?.id, props.node?.version], ([id, version], previous) => {
  if (!previous || id !== previous[0] || version !== previous[1]) resetDraft()
})
watch(() => props.saving, (saving) => { if (!saving) submitting.value = false })
watch(dirty, value => emit('dirty-change', value), { immediate: true })

function resetDraft() {
  draft.value = createNodeDraft(props.node)
  submitting.value = false
  nextTick(() => formRef.value?.clearValidate())
}

function property(key, fallback = '') {
  return draft.value.properties[key] ?? fallback
}

function setProperty(key, value) {
  if (!locked.value) draft.value.properties[key] = value
}

function addRow() {
  if (locked.value) return
  const prefix = kind.value.key === 'route' ? 'branch' : 'sample.field'
  let index = draft.value.rows.length + 1
  while (draft.value.rows.some(row => row.name === `${prefix}${index}`)) index++
  draft.value.rows.push({ name: `${prefix}${index}`, value: '' })
}

function removeRow(index) {
  if (!locked.value) draft.value.rows.splice(index, 1)
}

async function save() {
  if (locked.value || !dirty.value) return
  submitting.value = true
  try {
    await formRef.value?.validate()
    emit('save', nodeDraftPayload(props.node, draft.value))
    await nextTick()
  } catch {
    // Element Plus keeps validation feedback next to the affected field.
  } finally {
    if (!props.saving) submitting.value = false
  }
}

function discard() { if (!busy.value) resetDraft() }
function isDirty() { return dirty.value }
function preview() { if (!busy.value) emit('preview') }
function remove() { if (!locked.value) emit('remove') }
function applyLookupSnapshot(value) { if (kind.value.key === 'lookup' && !locked.value) setProperty('Lookup Rows', value) }
defineExpose({ isDirty, discard, applyLookupSnapshot })
</script>

<style scoped>
.governance-node-config { width: 100%; min-width: 0; max-width: 100%; height: 100%; min-height: 0; display: flex; flex-direction: column; background: var(--surface-bg); color: var(--app-text); }
.governance-node-config__heading, .governance-node-config__body, .governance-node-config__footer { padding: calc(var(--el-component-size) / 2); }
.governance-node-config__heading { border-bottom: 1px solid var(--surface-border); }
.governance-node-config__title { display: flex; align-items: center; gap: calc(var(--el-component-size) / 4); }
.governance-node-config__title h3 { flex: 1; min-width: 0; margin: 0; color: var(--app-heading); font-size: var(--el-font-size-base); font-weight: var(--el-font-weight-primary); }
.governance-node-config__heading p, .governance-node-config__help { margin: calc(var(--el-component-size) / 4) 0 0; color: var(--app-muted); font-size: var(--el-font-size-small); line-height: 1.6; overflow-wrap: anywhere; }
.governance-node-config__scroll { flex: 1; min-height: 0; }
.governance-node-config__body { display: flex; flex-direction: column; gap: calc(var(--el-component-size) / 2); }
.governance-node-config__field { display: flex; flex-direction: column; align-items: flex-start; gap: calc(var(--el-component-size) / 4); padding: calc(var(--el-component-size) / 4) 0; }
.governance-node-config__fields { width: 100%; }
.governance-node-config__technical { overflow-wrap: anywhere; }
.governance-node-config__issues { padding-left: var(--el-component-size-small); margin: calc(var(--el-component-size) / 4) 0 0; }
.governance-node-config__footer { border-top: 1px solid var(--surface-border); }
.governance-node-config__actions, .governance-node-config__secondary { display: flex; align-items: center; justify-content: space-between; gap: calc(var(--el-component-size) / 4); }
.governance-node-config__actions { flex-wrap: wrap; }
.governance-node-config__secondary { margin-top: calc(var(--el-component-size) / 4); }
</style>
