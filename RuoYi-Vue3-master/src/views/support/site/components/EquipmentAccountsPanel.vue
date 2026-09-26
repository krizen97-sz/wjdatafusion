<template>
  <section class="equipment-accounts" aria-label="设备账号密码" v-loading="loading">
    <div class="equipment-accounts__toolbar">
      <strong>{{ editor ? (draft.credentialId || draft.legacyRegistration ? '编辑账号' : '新增账号') : '账号密码' }}</strong>
      <el-space wrap>
        <el-button v-if="server && canAdd && canRead && !editor" icon="Plus" :disabled="loading || loadFailed" @click="startEdit()">新增账号</el-button>
        <el-button v-if="!server && canEdit && !editor" icon="Edit" @click="emit('edit-hardware')">修改账号密码</el-button>
        <el-button v-if="canPlain && accounts.length && !editor" :icon="showPlain ? 'Hide' : 'View'" :loading="revealing" :disabled="saving" @click="togglePlain">{{ showPlain ? '隐藏密码' : '显示密码' }}</el-button>
      </el-space>
    </div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-button v-if="loadFailed" icon="Refresh" @click="load">重新加载账号</el-button>
    <el-alert v-if="!canRead" type="info" title="当前账号没有查看设备账号的权限" :closable="false" />
    <template v-else-if="editor">
      <el-form ref="formRef" :model="draft" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="draft.username" :disabled="draft.legacyRegistration" maxlength="128" autocomplete="off" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="draft.password" type="password" show-password maxlength="128" autocomplete="new-password" :placeholder="draft.credentialId || draft.legacyRegistration ? '未修改' : '请输入密码'" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="draft.status"><el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio></el-radio-group>
        </el-form-item>
        <el-space wrap>
          <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
          <el-button type="primary" icon="Check" :loading="saving" native-type="submit">保存</el-button>
        </el-space>
      </el-form>
    </template>
    <el-table v-else-if="canRead && !loadFailed" :data="accounts" :row-key="accountKey" empty-text="尚未登记账号" size="small">
      <el-table-column label="用户名" min-width="110">
        <template #default="{ row }">
          <span :title="'登记用户名：' + row.username">{{ displayAccountName(row.username) }}</span>
          <div v-if="accounts.filter(item => item.username?.toLowerCase() === row.username?.toLowerCase()).length > 1">
            <el-text type="info" size="small">{{ row.credentialName || '账号 #' + row.credentialId }}</el-text>
          </div>
          <el-tag v-if="row.status === '1'" type="info" size="small">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="密码" min-width="110">
        <template #default="{ row }"><span class="equipment-accounts__password">{{ showPlain ? plain[accountKey(row)] || '未设置' : row.password ? '******' : '未设置' }}</span></template>
      </el-table-column>
      <el-table-column v-if="server && (canEdit || canRemove)" label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" :disabled="saving || revealing" @click="startEdit(row)">编辑</el-button>
          <el-button v-if="canRemove" link type="danger" :disabled="saving || revealing" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { listServerCredentials, addServerCredential, updateServerCredential, delServerCredential, delLegacyServerCredential } from '@/api/support/server'
import { getHardwareAsset } from '@/api/support/hardwareAsset'
import { readEquipmentPasswords } from '@/api/support/equipment'
import { accountKey, accountMutation, displayAccountName, orderAccounts } from './equipmentAccounts.rules'
const props = defineProps({ siteId: [Number, String], device: { type: Object, required: true } })
const emit = defineEmits(['saved', 'editing-change', 'edit-hardware'])
const { proxy } = getCurrentInstance()
const accounts = ref([]), loading = ref(false), saving = ref(false), revealing = ref(false)
const loadFailed = ref(false)
const editor = ref(false), showPlain = ref(false), plain = ref({}), error = ref('')
const draft = reactive({}), original = ref(''), formRef = ref(null)
let generation = 0
const server = computed(() => props.device.sourceType === 'SERVER')
const typedPermission = suffix => 'support:' + (server.value ? 'server:' : 'hardwareAsset:') + suffix
const canRead = computed(() => proxy.$auth.hasPermi(['support:equipment:query', typedPermission('query')]))
const canEdit = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', typedPermission('edit')]))
const canAdd = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', 'support:server:add']))
const canRemove = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', 'support:server:remove']))
const canPlain = computed(() => proxy.$auth.hasPermi(['support:credential:viewPlain']))
const rules = {
  username: [{ required: true, whitespace: true, message: '请填写用户名', trigger: 'blur' }],
  password: [{ validator: (_, value, done) => {
    if (!draft.credentialId && !draft.legacyRegistration && !value) return done(new Error('请填写密码'))
    if (value === '******') return done(new Error('不能将密码占位符作为新密码'))
    done()
  }, trigger: 'blur' }]
}
watch([() => props.siteId, () => props.device.sourceType, () => props.device.sourceId], load, { immediate: true })
watch(() => editor.value || saving.value || revealing.value, value => emit('editing-change', value), { flush: 'sync' })
onBeforeUnmount(() => { generation++; plain.value = {}; draft.password = ''; emit('editing-change', false) })
async function load() {
  const token = ++generation
  loading.value = true
  error.value = ''
  loadFailed.value = false
  accounts.value = []
  plain.value = {}
  showPlain.value = false
  editor.value = false
  draft.password = ''
  try {
    if (!canRead.value) return
    if (server.value) {
      const response = await listServerCredentials(props.device.sourceId)
      if (token === generation) accounts.value = orderAccounts(response.data || [])
    } else {
      const response = await getHardwareAsset(props.device.sourceId)
      if (token === generation && response.data) accounts.value = [{
        username: response.data.loginUsername || '', password: response.data.loginPassword, status: response.data.status
      }]
    }
  } catch (failure) {
    if (token === generation) { error.value = failure.message || '账号读取失败'; loadFailed.value = true }
  } finally { if (token === generation) loading.value = false }
}
function startEdit(row) {
  if (loading.value || loadFailed.value || saving.value || revealing.value || (row ? !canEdit.value : !canAdd.value)) return
  plain.value = {}; showPlain.value = false; error.value = ''
  Object.keys(draft).forEach(key => delete draft[key])
  Object.assign(draft, row || { username: '', status: '0', isDefault: '0' }, { password: '' })
  original.value = JSON.stringify(draft)
  editor.value = true
}
async function confirmClose() {
  if (saving.value || revealing.value) return false
  if (editor.value && JSON.stringify(draft) !== original.value) {
    try { await proxy.$modal.confirm('账号修改尚未保存，确认放弃修改？') } catch { return false }
  }
  editor.value = false; draft.password = ''; plain.value = {}; showPlain.value = false
  return true
}
async function cancelEdit() { await confirmClose() }
async function save() {
  if (saving.value || !await formRef.value.validate().catch(() => false)) return
  saving.value = true
  error.value = ''
  const token = generation
  try {
    const data = accountMutation(props.device.sourceId, draft)
    await (data.credentialId ? updateServerCredential(data) : addServerCredential(data))
    if (token !== generation) return
    draft.password = ''; editor.value = false
    emit('saved')
    proxy.$modal.msgSuccess('账号已保存')
    await load()
  } catch (failure) {
    if (token === generation) error.value = failure.message || '账号保存失败'
  } finally { saving.value = false }
}
async function remove(row) {
  if (!canRemove.value || saving.value) return
  saving.value = true
  try {
    await proxy.$modal.confirm('确认删除登录账号 ' + displayAccountName(row.username) + '？设备本身不会删除。')
    await (row.legacyRegistration ? delLegacyServerCredential(props.device.sourceId, row.username) : delServerCredential(row.credentialId))
    emit('saved')
    proxy.$modal.msgSuccess('账号已删除')
    await load()
  } catch (failure) {
    if (failure !== 'cancel' && failure !== 'close') error.value = failure.message || '账号删除失败'
  } finally { saving.value = false }
}
async function togglePlain() {
  if (!canPlain.value || revealing.value || saving.value) return
  if (showPlain.value) { plain.value = {}; showPlain.value = false; return }
  const token = generation
  revealing.value = true
  error.value = ''
  try {
    const response = await readEquipmentPasswords({ siteId: props.siteId, devices: [{ sourceType: props.device.sourceType, sourceId: props.device.sourceId }] })
    if (token !== generation) return
    plain.value = Object.fromEntries((response.data?.[0]?.credentials || []).map(row => [accountKey(row), row.password]))
    showPlain.value = true
  } catch (failure) {
    if (token === generation) error.value = failure.message || '密码读取失败'
  } finally { revealing.value = false }
}
defineExpose({ confirmClose })
</script>

<style scoped>
.equipment-accounts { display: grid; gap: var(--el-font-size-base); min-width: 0; }
.equipment-accounts__toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: var(--el-font-size-base); }
.equipment-accounts__password { overflow-wrap: anywhere; }
</style>
