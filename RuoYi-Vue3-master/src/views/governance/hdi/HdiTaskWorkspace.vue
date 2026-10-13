<template>
  <div ref="workspace" class="app-container hdi-task-workspace">
    <div v-if="(taskId || warned) && !failed" class="mb8">
      <el-button v-if="taskId" icon="Back" @click="backToList">任务列表</el-button>
      <el-button v-if="taskId" icon="Tickets" @click="historyOpen = true">运行记录</el-button>
      <el-button v-if="warned" icon="Refresh" @click="connect(true)">重新连接</el-button>
      <el-text v-if="taskId" type="info" class="ml10">本地文件目录填写“/平台输出”，运行后可在记录中下载。</el-text>
    </div>
    <HdiRunHistory v-model="historyOpen" :task-id="taskId" />
    <el-result v-if="failed" icon="warning" title="任务工作区暂不可用" sub-title="请重试；如果仍无法连接，请联系管理员。">
      <template #extra><el-button type="primary" @click="connect(true)">重新连接</el-button></template>
    </el-result>
    <div v-else ref="frameHost" v-loading="loading" class="hdi-frame-host" :style="{ height: `${frameHeight}px` }">
      <iframe v-if="frameUrl" ref="frame" :key="frameUrl" :src="frameUrl" title="数据流程编排、测试与运行" class="hdi-task-frame" @load="frameLoaded" @error="fail" />
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useElementBounding, useWindowSize } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { createHdiSession } from '@/api/governance/hdi'
import { getInfo } from '@/api/login'
import { hdiFrameUrl, isHdiMessage, platformTaskUrl, validHdiTaskId } from './hdiHostBridge'
import HdiRunHistory from './HdiRunHistory.vue'

const route = useRoute(), router = useRouter()
const workspace = ref(), frameHost = ref(), frame = ref()
const frameUrl = ref(''), loading = ref(true), failed = ref(false)
const taskId = ref(route.query.hdiTask || null)
const readonly = ref(route.query.hdiReadonly === 'true')
const isNewCreate = ref(route.query.hdiNew === '1')
const nativeContext = ref(null), warned = ref(false)
const historyOpen = ref(false)
const { height: viewportHeight } = useWindowSize()
const { top } = useElementBounding(frameHost)
const frameHeight = computed(() => {
  const main = workspace.value?.closest('.app-main')
  const bottomPadding = main ? Number.parseFloat(getComputedStyle(main).paddingBottom) || 0 : 0
  return Math.max(360, viewportHeight.value - top.value - bottomPadding)
})
let attempt = 0, timeout, active = true, frameCleanup = () => {}, lastActivityRefresh = 0, refreshing = false
function fail() {
  frameCleanup()
  clearTimeout(timeout)
  loading.value = false
  failed.value = true
  frameUrl.value = ''
}
async function connect(renew = false) {
  const current = ++attempt
  frameCleanup()
  clearTimeout(timeout)
  frameUrl.value = ''
  loading.value = true
  failed.value = false
  try {
    const response = await createHdiSession(renew)
    if (current !== attempt) return
    nativeContext.value = response.data
    warned.value = false
    frameUrl.value = hdiFrameUrl(response.data?.frameUrl, window.location.origin, taskId.value, readonly.value, isNewCreate.value)
    timeout = setTimeout(() => { if (current === attempt) fail() }, 45000)
  } catch { if (current === attempt) fail() }
}
function syncTaskRoute(id, viewOnly = false, created = false) {
  taskId.value = id == null ? null : String(id)
  readonly.value = viewOnly
  isNewCreate.value = created
  const query = { ...route.query }
  delete query.hdiTask
  delete query.hdiReadonly
  delete query.hdiNew
  if (taskId.value) query.hdiTask = taskId.value
  if (taskId.value && viewOnly) query.hdiReadonly = 'true'
  else if (taskId.value && created) query.hdiNew = '1'
  router.replace({ query })
}
function backToList() {
  if (!frame.value?.contentWindow) return
  clearTimeout(timeout)
  loading.value = true
  frame.value.contentWindow.postMessage({ type: 'rynew-hdi:back' }, window.location.origin)
  timeout = setTimeout(fail, 45000)
}
function receiveHost(event) {
  if (!isHdiMessage(event, frame.value?.contentWindow, window.location.origin)) return
  const data = event.data
  if (data.type === 'rynew-hdi:error') return fail()
  if (data.type === 'rynew-hdi:ready') {
    // Readiness means the actual vendor component mounted, never that a task ran.
    if (frame.value?.contentDocument?.documentElement.dataset.hdiHostState !== 'ready') return
    clearTimeout(timeout)
    loading.value = false
  } else if (data.type === 'rynew-hdi:task') {
    if (data.taskId !== null && !validHdiTaskId(data.taskId)) return
    syncTaskRoute(data.taskId, data.readonly === true, data.isNewCreate === true)
  } else if (data.type === 'rynew-hdi:navigate') {
    const url = platformTaskUrl(data.href, window.location.origin, nativeContext.value?.workspaceId)
    if (url) router.push(url)
    else ElMessage.info('此操作尚未接入平台，请先使用任务编排、保存和测试。')
  }
}
async function refreshWhileEditing() {
  if (!active || window.document.hidden || refreshing || Date.now() - lastActivityRefresh < 60000) return
  const current = attempt
  lastActivityRefresh = Date.now()
  refreshing = true
  try {
    // Use the existing platform login refresh path, then touch the real native session.
    // Only user interaction triggers this; an abandoned background tab cannot keep a login alive.
    await getInfo()
    if (current !== attempt || !active) return
    const response = await fetch('/detl/_platform/context', {
      credentials: 'same-origin', cache: 'no-store',
      headers: { Workspace: String(nativeContext.value.workspaceId), 'X-Requested-With': 'XMLHttpRequest' }
    })
    if (!response.ok) throw new Error('Native session is unavailable')
    const native = await response.json()
    if (native?.code !== '0' || !native.data?.user) throw new Error('Native session was not confirmed')
    if (current !== attempt || !active) return
    warned.value = false
  } catch {
    if (current !== attempt || !active) return
    // Preserve the frame so the user can recover unsaved text before reconnecting.
    if (!warned.value) ElMessage.warning('任务会话需要重新连接，请先保留尚未保存的内容。')
    warned.value = true
  } finally { refreshing = false }
}
function frameLoaded() {
  frameCleanup()
  try {
    const child = frame.value?.contentWindow
    const document = frame.value?.contentDocument
    if (!child || child.location.origin !== window.location.origin || !document?.getElementById('app')) return fail()
    const nativeOpen = child.open
    const nativeClose = child.close
    function openTask(url, target, features) {
      const platformUrl = platformTaskUrl(url, window.location.origin, nativeContext.value?.workspaceId)
      return nativeOpen.call(child, platformUrl || url, target, features)
    }
    child.open = openTask
    // The native task page closes its window after a fatal task error. Preserve that
    // action only for platform tabs opened by the original task list, not the main app.
    function closeTask() {
      if (taskId.value && window.opener) window.close()
      else backToList()
    }
    child.close = closeTask
    document.addEventListener('pointerdown', refreshWhileEditing, true)
    document.addEventListener('keydown', refreshWhileEditing, true)
    const mounted = () => {
      const state = document.documentElement.dataset.hdiHostState
      if (state === 'error') return fail()
      if (state === 'ready') { clearTimeout(timeout); loading.value = false; observer.disconnect() }
    }
    const observer = new MutationObserver(mounted)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-hdi-host-state'] })
    frameCleanup = () => {
      observer.disconnect()
      document.removeEventListener('pointerdown', refreshWhileEditing, true)
      document.removeEventListener('keydown', refreshWhileEditing, true)
      try { if (child.open === openTask) child.open = nativeOpen } catch { /* Frame has navigated or closed. */ }
      try { if (child.close === closeTask) child.close = nativeClose } catch { /* Frame has navigated or closed. */ }
      frameCleanup = () => {}
    }
    mounted()
  } catch { fail() }
}
watch(() => [route.query.hdiTask, route.query.hdiReadonly, route.query.hdiNew], ([id, viewOnly, created]) => {
  const nextId = id || null, nextReadonly = viewOnly === 'true', nextNew = created === '1'
  if (String(nextId) === String(taskId.value) && nextReadonly === readonly.value && nextNew === isNewCreate.value) return
  taskId.value = nextId
  readonly.value = nextReadonly
  isNewCreate.value = nextNew
  if (nativeContext.value) {
    frameCleanup()
    clearTimeout(timeout)
    loading.value = true
    try { frameUrl.value = hdiFrameUrl(nativeContext.value.frameUrl, window.location.origin, nextId, nextReadonly, nextNew) }
    catch { return fail() }
    timeout = setTimeout(fail, 45000)
  }
})
onMounted(() => { window.addEventListener('message', receiveHost); connect() })
onActivated(() => { active = true })
onDeactivated(() => { active = false })
onBeforeUnmount(() => {
  active = false
  ++attempt
  clearTimeout(timeout)
  frameCleanup()
  window.removeEventListener('message', receiveHost)
})
</script>

<style scoped>
.hdi-frame-host { width: 100%; min-height: 0; }
.hdi-task-frame { display: block; width: 100%; height: 100%; border: 0; }
</style>
