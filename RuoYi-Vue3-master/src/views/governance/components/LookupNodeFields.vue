<template>
  <div class="governance-lookup-fields">
    <section aria-label="字典快照">
      <div class="governance-lookup-fields__heading">
        <h4>字典快照</h4>
        <el-tag v-if="snapshot.valid" type="info" size="small">{{ snapshot.count }} 条记录</el-tag>
      </div>
      <el-alert v-if="!snapshot.valid" :title="snapshot.error" type="error" :closable="false" show-icon />
      <p class="governance-lookup-fields__help">使用当前已加载的字典数据。数据库变化后，需重新加载快照并保存节点。</p>
      <el-button :disabled="readonly" icon="Download" @click="loadSnapshot">从数据库加载快照</el-button>
      <el-collapse v-model="snapshotOpen">
        <el-collapse-item title="编辑字典 JSON" name="snapshot">
          <el-form-item label="字典记录（JSON 对象数组）" :error="snapshot.error">
            <el-input :model-value="property('Lookup Rows')" type="textarea" :autosize="{ minRows: 5, maxRows: 12 }" :readonly="readonly" spellcheck="false" aria-label="字典快照 JSON" @update:model-value="updateProperty('Lookup Rows', $event)" />
          </el-form-item>
          <p class="governance-lookup-fields__help">最多 1000 条记录、256 KiB。原文会完整保留，不在浏览器中重新序列化数字。</p>
        </el-collapse-item>
      </el-collapse>
    </section>

    <section aria-label="匹配条件">
      <div class="governance-lookup-fields__heading"><h4>匹配条件</h4></div>
      <p class="governance-lookup-fields__help">所有条件须命中同一条字典记录。字符串区分大小写，数字按十进制精确比较。</p>
      <el-alert v-if="!matches.valid || matches.advancedOnly" :title="matches.error || matches.warning" :type="matches.valid ? 'info' : 'error'" :closable="false" show-icon />
      <template v-else>
        <el-empty v-if="!matches.rows.length" description="尚未添加匹配条件" :image-size="40" />
        <div v-for="(row, index) in matches.rows" :key="index" class="governance-lookup-fields__rule">
          <div class="governance-lookup-fields__heading">
            <strong>条件 {{ index + 1 }}</strong>
            <el-button link type="danger" icon="Delete" :disabled="readonly" :aria-label="`移除匹配条件 ${index + 1}`" @click="editRules('Match Fields', { type: 'remove', index })">移除</el-button>
          </div>
          <el-form-item label="字典列名" :error="fieldError(row, 'lookup', 'Match Fields')">
            <el-input :model-value="row.lookup || ''" :disabled="readonly" maxlength="128" :aria-label="`条件 ${index + 1} 字典列名`" placeholder="例如 vehicle_plate" @update:model-value="editField('Match Fields', index, 'lookup', $event)" />
          </el-form-item>
          <el-form-item label="匹配方式" :error="fieldError(row, 'operator', 'Match Fields')">
            <el-select :model-value="row.operator" :disabled="readonly" :aria-label="`条件 ${index + 1} 匹配方式`" @update:model-value="editField('Match Fields', index, 'operator', $event)">
              <el-option label="相等" value="EQ" />
              <el-option label="字典列非空" value="IS_NOT_NULL" />
            </el-select>
          </el-form-item>
          <template v-if="row.operator !== 'IS_NOT_NULL'">
            <el-form-item label="输入字段路径（JSON Pointer）" :error="fieldError(row, 'input', 'Match Fields')">
              <el-input :model-value="row.input || ''" :disabled="readonly" maxlength="1024" :aria-label="`条件 ${index + 1} 输入字段路径`" placeholder="例如 /vehicle/plate" @update:model-value="editField('Match Fields', index, 'input', $event)" />
            </el-form-item>
          </template>
          <p v-else class="governance-lookup-fields__help">只检查字典列存在且非 null，不读取输入字段。空字符串也视为非 null。</p>
          <el-form-item label="字段类型" :error="fieldError(row, 'type', 'Match Fields')">
            <el-select :model-value="row.type" :disabled="readonly" :aria-label="`条件 ${index + 1} 字段类型`" @update:model-value="editField('Match Fields', index, 'type', $event)">
              <el-option label="字符串（精确匹配）" value="STRING" />
              <el-option label="数字（精确十进制）" value="NUMBER" />
            </el-select>
          </el-form-item>
        </div>
      </template>
      <el-button link type="primary" icon="Plus" :disabled="readonly || !matches.valid || matches.advancedOnly || matches.rows.length >= 64" @click="editRules('Match Fields', { type: 'add' })">添加匹配条件</el-button>
      <el-collapse v-model="matchOpen">
        <el-collapse-item title="高级：匹配规则 JSON" name="match">
          <el-form-item label="匹配规则原文" :error="matches.error">
            <el-input :model-value="property('Match Fields')" type="textarea" :autosize="{ minRows: 5, maxRows: 12 }" :readonly="readonly" spellcheck="false" aria-label="匹配规则 JSON" @update:model-value="updateProperty('Match Fields', $event)" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </section>

    <section aria-label="返回字段">
      <div class="governance-lookup-fields__heading"><h4>返回字段</h4></div>
      <p class="governance-lookup-fields__help">在保留输入字段的基础上写入根字段。同名字段会被覆盖；默认值只用于未命中且选择保留的记录。</p>
      <el-alert v-if="!returns.valid || returns.advancedOnly" :title="returns.error || returns.warning" :type="returns.valid ? 'info' : 'error'" :closable="false" show-icon />
      <template v-else>
        <el-empty v-if="!returns.rows.length" description="尚未添加返回字段" :image-size="40" />
        <div v-for="(row, index) in returns.rows" :key="index" class="governance-lookup-fields__rule">
          <div class="governance-lookup-fields__heading">
            <strong>字段 {{ index + 1 }}</strong>
            <el-button link type="danger" icon="Delete" :disabled="readonly" :aria-label="`移除返回字段 ${index + 1}`" @click="editRules('Return Fields', { type: 'remove', index })">移除</el-button>
          </div>
          <el-form-item label="字典列名" :error="fieldError(row, 'lookup', 'Return Fields')">
            <el-input :model-value="row.lookup || ''" :disabled="readonly" maxlength="128" :aria-label="`返回字段 ${index + 1} 字典列名`" placeholder="例如 code" @update:model-value="editField('Return Fields', index, 'lookup', $event)" />
          </el-form-item>
          <el-form-item label="输出根字段名" :error="fieldError(row, 'output', 'Return Fields')">
            <el-input :model-value="row.output || ''" :disabled="readonly" maxlength="128" :aria-label="`返回字段 ${index + 1} 输出根字段名`" placeholder="例如 mapped_code" @update:model-value="editField('Return Fields', index, 'output', $event)" />
          </el-form-item>
          <el-form-item label="未命中默认值类型">
            <el-select :model-value="typeof row.default === 'string' ? 'STRING' : 'NULL'" :disabled="readonly" :aria-label="`返回字段 ${index + 1} 默认值类型`" @update:model-value="editDefault(index, $event, typeof row.default === 'string' ? row.default : '')">
              <el-option label="空值（null，默认）" value="NULL" />
              <el-option label="字符串" value="STRING" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="typeof row.default === 'string'" label="默认字符串">
            <el-input :model-value="row.default" :disabled="readonly" :aria-label="`返回字段 ${index + 1} 默认字符串`" placeholder="例如 0，保存为字符串" @update:model-value="editDefault(index, 'STRING', $event)" />
          </el-form-item>
        </div>
      </template>
      <el-button link type="primary" icon="Plus" :disabled="readonly || !returns.valid || returns.advancedOnly || returns.rows.length >= 64" @click="editRules('Return Fields', { type: 'add' })">添加返回字段</el-button>
      <el-collapse v-model="returnOpen">
        <el-collapse-item title="高级：返回规则 JSON" name="return">
          <el-form-item label="返回规则原文" :error="returns.error">
            <el-input :model-value="property('Return Fields')" type="textarea" :autosize="{ minRows: 5, maxRows: 12 }" :readonly="readonly" spellcheck="false" aria-label="返回规则 JSON" @update:model-value="updateProperty('Return Fields', $event)" />
          </el-form-item>
          <p class="governance-lookup-fields__help">数字、布尔值、对象和数组默认值可直接在原文中编辑。高级配置期间，结构化增删会暂停。</p>
        </el-collapse-item>
      </el-collapse>
    </section>

    <section aria-label="查表处理策略">
      <div class="governance-lookup-fields__heading"><h4>处理策略</h4></div>
      <el-form-item label="未命中时">
        <el-select :model-value="property('Missing Match', 'KEEP')" :disabled="readonly" aria-label="未命中处理策略" @update:model-value="updateProperty('Missing Match', $event)">
          <el-option v-for="option in LOOKUP_MISSING_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="命中多条时">
        <el-select :model-value="property('Multiple Matches', 'FAIL')" :disabled="readonly" aria-label="重复匹配处理策略" @update:model-value="updateProperty('Multiple Matches', $event)">
          <el-option v-for="option in LOOKUP_MULTIPLE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
      </el-form-item>
      <p class="governance-lookup-fields__help">默认遇到多条匹配整批失败。首条、末条按当前快照顺序选择；数据库无排序查询不保证固定顺序。任一记录转换失败，整批进入失败分支。</p>
    </section>
    <el-alert v-if="editError" :title="editError" type="error" :closable="false" show-icon />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { LOOKUP_MISSING_OPTIONS, LOOKUP_MULTIPLE_OPTIONS, lookupPropertyText, lookupPropertyPatch, parseLookupSnapshot, parseLookupRules, patchLookupRules, lookupRuleFieldError } from '../lookupRules.js'

const props = defineProps({ properties: { type: Object, default: () => ({}) }, readonly: Boolean })
const emit = defineEmits(['update', 'load-snapshot'])
const editError = ref('')
const snapshotOpen = ref([])
const matchOpen = ref([])
const returnOpen = ref([])
const snapshot = computed(() => parseLookupSnapshot(props.properties['Lookup Rows']))
const matches = computed(() => parseLookupRules(props.properties['Match Fields'], 'Match Fields'))
const returns = computed(() => parseLookupRules(props.properties['Return Fields'], 'Return Fields'))
const fieldError = lookupRuleFieldError

watch(() => snapshot.value.valid, valid => { if (!valid) snapshotOpen.value = ['snapshot'] }, { immediate: true })
watch(() => !matches.value.valid || matches.value.advancedOnly, advanced => { if (advanced) matchOpen.value = ['match'] }, { immediate: true })
watch(() => !returns.value.valid || returns.value.advancedOnly, advanced => { if (advanced) returnOpen.value = ['return'] }, { immediate: true })
watch(() => props.properties, () => { editError.value = '' })

function property(key, fallback = '[]') { return lookupPropertyText(props.properties[key], fallback) }
function updateProperty(key, value) {
  if (props.readonly) return
  try { emit('update', lookupPropertyPatch(key, value)); editError.value = '' }
  catch (error) { editError.value = error.message }
}
function editRules(key, operation) {
  if (props.readonly) return
  try { emit('update', patchLookupRules(property(key), key, operation)); editError.value = '' }
  catch (error) { editError.value = error.message }
}
function editField(key, index, field, value) { editRules(key, { type: 'field', index, key: field, value }) }
function editDefault(index, kind, value) { editRules('Return Fields', { type: 'default', index, kind, value }) }
function loadSnapshot() { if (!props.readonly) emit('load-snapshot') }
</script>

<style scoped>
.governance-lookup-fields { width: 100%; max-width: 100%; min-width: 0; display: flex; flex-direction: column; gap: calc(var(--el-component-size) / 2); color: var(--app-text); }
.governance-lookup-fields section { min-width: 0; }
.governance-lookup-fields__heading { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: calc(var(--el-component-size) / 4); margin-bottom: calc(var(--el-component-size) / 4); }
.governance-lookup-fields__heading h4 { margin: 0; font-size: var(--el-font-size-base); font-weight: var(--el-font-weight-primary); color: var(--app-heading); }
.governance-lookup-fields__heading strong { color: var(--app-heading); font-size: var(--el-font-size-small); font-weight: var(--el-font-weight-primary); }
.governance-lookup-fields__help { margin: calc(var(--el-component-size) / 4) 0; color: var(--app-muted); font-size: var(--el-font-size-small); line-height: 1.6; overflow-wrap: anywhere; }
.governance-lookup-fields__rule { border-bottom: 1px solid var(--surface-border); padding-top: calc(var(--el-component-size) / 2); margin-bottom: calc(var(--el-component-size) / 4); }
</style>
