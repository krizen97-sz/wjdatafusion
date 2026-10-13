<template>
  <div ref="workspace" class="app-container hdi-task-workspace">
    <div v-if="taskId && !failed" class="mb8">
      <el-button icon="Back" @click="backToList">任务列表</el-button>
    </div>
    <el-result v-if="failed" icon="warning" title="任务工作区暂不可用" sub-title="请重试；如果仍无法连接，请联系管理员。">
      <template #extra><el-button type="primary" @click="connect">重新连接</el-button></template>
    </el-result>
    <div v-else ref="frameHost" v-loading="loading" class="hdi-frame-host" :style="{ height: `${frameHeight}px` }">
      <iframe v-if="frameUrl" ref="frame" :key="frameUrl" :src="frameUrl" title="数据流程编排、测试与运行" class="hdi-task-frame" @error="fail" />
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useElementBounding, useWindowSize } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { createHdiSession } from '@/api/governance/hdi'
import { hdiFrameUrl, isHdiMessage } from './hdiHostBridge'

const route = useRoute(), router = useRouter()
const workspace = ref(), frameHost = ref(), frame = ref()
const frameUrl = ref(''), loading = ref(true), failed = ref(false)
const taskId = ref(route.query.hdiTask || null)
const readonly = ref(route.query.hdiReadonly === 'true')
const { height: viewportHeight } = useWindowSize()
const { top } = useElementBounding(frameHost)
const frameHeight = computed(() => {
  const main = workspace.value?.closest('.app-main')
  const bottomPadding = main ? Number.parseFloat(getComputedStyle(main).paddingBottom) || 0 : 0
  return Math.max(360, viewportHeight.value - top.value - bottomPadding)
})
let attempt = 0, timeout, active = true
function fail() {
  clearTimeout(timeout)
  loading.value = false
  failed.value = true
  frameUrl.value = ''
}
async function connect() {
  const current = ++attempt
  clearTimeout(timeout)
  frameUrl.value = ''
  loading.value = true
  failed.value = false
  try {
    const response = await createHdiSession()
    if (current !== attempt) return
    frameUrl.value = hdiFrameUrl(response.data?.frameUrl, window.location.origin, taskId.value, readonly.value)
    timeout = setTimeout(() => { if (current === attempt) fail() }, 45000)
  } catch { if (current === attempt) fail() }
}
function backToList() {
  frame.value?.contentWindow?.postMessage({ type: 'rynew-hdi:back' }, window.location.origin)
}
function message(event) {
  if (!isHdiMessage(event, frame.value?.contentWindow, window.location.origin)) return
  if (event.data.type === 'rynew-hdi:ready') {
    clearTimeout(timeout)
    loading.value = false
  } else if (event.data.type === 'rynew-hdi:error') {
    fail()
  } else if (event.data.type === 'rynew-hdi:task') {
    const nextId = event.data.taskId
    if (nextId != null && !/^[1-9]\d*$/.test(String(nextId))) return
    taskId.value = nextId || null
    readonly.value = Boolean(nextId && event.data.readonly === true)
    const query = { ...route.query }
    delete query.tab
    if (nextId) query.hdiTask = String(nextId)
    else delete query.hdiTask
    if (readonly.value) query.hdiReadonly = 'true'
    else delete query.hdiReadonly
    if (active) router.replace({ query })
  } else if (event.data.type === 'rynew-hdi:navigate') {
    if (active) {
      if (['flow', 'flowList', 'taskList'].includes(event.data.name)) backToList()
      else ElMessage.warning('该任务子页面暂不可用。')
    }
  }
}
onMounted(() => { window.addEventListener('message', message); connect() })
onActivated(() => { active = true })
onDeactivated(() => { active = false })
onBeforeUnmount(() => { ++attempt; clearTimeout(timeout); window.removeEventListener('message', message) })
</script>

<style scoped>
.hdi-frame-host { width: 100%; min-height: 0; }
.hdi-task-frame { display: block; width: 100%; height: 100%; border: 0; }
</style>
