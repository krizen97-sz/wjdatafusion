<template>
  <section v-loading="loading" aria-label="原生Kettle定时任务">
    <el-alert title="计划按保存时的任务版本执行" description="保存计划会冻结当前转换、子任务和输入文件。修改画布后，需更新计划版本才会生效。失败或执行状态不明时会暂停计划。" type="info" :closable="false" class="mb16" />
    <el-space><el-button icon="Refresh" :loading="loading" @click="load">刷新计划</el-button><el-button v-if="canEdit" type="primary" icon="Plus" :disabled="pending" @click="edit()">新增定时任务</el-button></el-space>
    <el-alert v-if="error" :title="error" type="error" class="mt16" @close="error = ''" />
    <el-table :data="items" class="mt16" empty-text="尚未配置定时任务">
      <el-table-column prop="name" label="计划名称" min-width="150" />
      <el-table-column label="任务版本" width="100"><template #default="{ row }">V{{ row.definitionRevision }}</template></el-table-column>
      <el-table-column label="执行时间" min-width="200"><template #default="{ row }"><div>{{ row.cron }}</div><el-text type="info" size="small">{{ row.timeZone }} · 下次 {{ formatTime(row.nextRunAt) }}</el-text></template></el-table-column>
      <el-table-column label="状态" min-width="150"><template #default="{ row }"><el-tag :type="statusFor(row).type">{{ statusFor(row).label }}</el-tag><div v-if="row.lastError"><el-text type="danger" size="small">{{ row.lastError }}</el-text></div></template></el-table-column>
      <el-table-column label="操作" min-width="300"><template #default="{ row }">
        <el-button v-if="canRun" link type="primary" :disabled="pending || (!row.enabled && (Boolean(row.activeRunId) || row.recoveryRequired))" @click="setState(row)">{{ row.enabled ? '暂停' : '启用' }}</el-button>
        <el-button v-if="canRun" link :disabled="pending || Boolean(row.activeRunId) || row.recoveryRequired" @click="runOnce(row)">立即执行</el-button>
        <el-button v-if="canEdit" link :disabled="pending || Boolean(row.activeRunId) || row.recoveryRequired" @click="edit(row)">更新计划</el-button>
        <el-button v-if="row.recoveryRequired && canRun" link type="warning" :disabled="pending" @click="recover(row)">核查原运行</el-button>
        <el-button v-if="row.activeRunId || row.lastRunId" link type="primary" @click="emit('view-run', { id: row.activeRunId || row.lastRunId })">运行详情</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="editorOpen" :title="cronOpen ? '生成Cron表达式' : form.id ? '更新定时任务' : '新增定时任务'" width="720px" append-to-body :close-on-click-modal="!pending" :show-close="!pending">
      <Crontab v-if="cronOpen" :expression="form.cron" preserve-field-values @fill="fillCron" @hide="cronOpen = false" />
      <el-form v-else label-position="top" :disabled="pending">
        <el-form-item label="计划名称" required><el-input v-model="form.name" maxlength="80" /></el-form-item>
        <el-form-item label="执行版本"><el-text>保存当前任务 V{{ definition?.revision }}，同时冻结子任务及输入文件</el-text></el-form-item>
        <el-form-item label="Cron表达式" required><el-input v-model="form.cron" placeholder="0 0 8 * * ?"><template #append><el-button @click="cronOpen = true">生成表达式</el-button></template></el-input></el-form-item>
        <el-form-item label="时区" required><el-select v-model="form.timeZone" filterable allow-create><el-option label="北京时间（Asia/Shanghai）" value="Asia/Shanghai" /><el-option label="协调世界时（UTC）" value="UTC" /></el-select></el-form-item>
      </el-form>
      <template v-if="!cronOpen" #footer><el-button :disabled="pending" @click="editorOpen = false">取消</el-button><el-button type="primary" :loading="pending" :disabled="!form.name.trim() || !form.cron.trim()" @click="save">保存为暂停计划</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import Crontab from '@/components/Crontab/index.vue'
import * as api from '@/api/governance/kettle'
import { errorMessage } from '../workspaceRules'
import { runStatus } from './runRules'
const props = defineProps({ definition: Object, canEdit: Boolean, canRun: Boolean, beforeSave: Function })
const emit = defineEmits(['view-run'])
const items = ref([]), loading = ref(false), pending = ref(false), error = ref(''), editorOpen = ref(false), cronOpen = ref(false)
const form = reactive({ id: '', revision: undefined, name: '', cron: '0 0 8 * * ?', timeZone: 'Asia/Shanghai' })
let timer, disposed = false
const formatTime = value => value ? new Date(value).toLocaleString() : '未启用'
const statusFor = row => row.status === 'READY' ? { label: '等待触发', type: 'success' } : row.status === 'PAUSED' ? { label: '已暂停', type: 'info' } : runStatus(row.status)
async function load(quiet = false) { clearTimeout(timer); if (!props.definition) return; if (!quiet) loading.value = true; try { items.value = (await api.kettleSchedules(props.definition.id)).data || [] } catch (cause) { error.value = errorMessage(cause, '计划读取失败') } finally { loading.value = false; if (!disposed && items.value.some(row => row.enabled || row.activeRunId)) timer = setTimeout(() => load(true), 2000) } }
function edit(row) { Object.assign(form, { id: row?.id || '', revision: row?.revision, name: row?.name || `${props.definition.name}定时任务`, cron: row?.cron || '0 0 8 * * ?', timeZone: row?.timeZone || 'Asia/Shanghai' }); editorOpen.value = true; cronOpen.value = false }
function fillCron(value) { form.cron = value.trim(); cronOpen.value = false }
async function perform(action) { if (pending.value) return; pending.value = true; error.value = ''; try { await action(); await load(true) } catch (cause) { error.value = errorMessage(cause, '计划操作失败') } finally { pending.value = false } }
async function save() { await perform(async () => { if (props.beforeSave && !await props.beforeSave()) return; const data = { name: form.name.trim(), revision: form.revision, definitionRevision: props.definition.revision, cron: form.cron.trim(), timeZone: form.timeZone }; if (form.id) await api.updateKettleSchedule(form.id, data); else await api.createKettleSchedule(props.definition.id, data); editorOpen.value = false }) }
const setState = row => perform(() => api.setKettleScheduleState(row.id, { enabled: !row.enabled, revision: row.revision }))
const recover = row => perform(() => api.recoverKettleSchedule(row.id))
const runOnce = row => perform(async () => { const result = (await api.runKettleSchedule(row.id)).data; if (result.activeRunId || result.lastRunId) emit('view-run', { id: result.activeRunId || result.lastRunId }) })
onMounted(load)
onBeforeUnmount(() => { disposed = true; clearTimeout(timer) })
</script>
