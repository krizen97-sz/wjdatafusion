<template>
  <div ref="workspace" class="app-container hdi-task-workspace">
    <div v-if="taskId && !failed" class="mb8">
      <el-button icon="Back" @click="backToList">任务列表</el-button>
    </div>
    <el-result v-if="failed" icon="warning" title="任务工作区暂不可用" sub-title="请重试；如果仍无法连接，请联系管理员。">
      <template #extra><el-button type="primary" @click="connect(true)">重新连接</el-button></template>
    </el-result>
    <div v-else ref="frameHost" v-loading="loading" class="hdi-frame-host" :style="{ height: `${frameHeight}px` }">
      <iframe v-if="frameUrl" ref="frame" :key="frameUrl" :src="frameUrl" title="数据流程编排、测试与运行" class="hdi-task-frame" @load="frameLoaded" @error="fail" />
    </div>
  </div>
</template>

<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useElementBounding, useWindowSize } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { createHdiSession } from '@/api/governance/hdi'
import { getInfo } from '@/api/login'
import { hdiFrameUrl, platformTaskUrl } from './hdiHostBridge'

const route = useRoute(), router = useRouter()
const workspace = ref(), frameHost = ref(), frame = ref()
const frameUrl = ref(''), loading = ref(true), failed = ref(false)
const taskId = ref(route.query.hdiTask || null)
const readonly = ref(route.query.hdiReadonly === 'true')
const isNewCreate = ref(route.query.hdiNew === '1')
const nativeContext = ref(null)
const { height: viewportHeight } = useWindowSize()
const { top } = useElementBounding(frameHost)
const frameHeight = computed(() => {
  const main = workspace.value?.closest('.app-main')
  const bottomPadding = main ? Number.parseFloat(getComputedStyle(main).paddingBottom) || 0 : 0
  return Math.max(360, viewportHeight.value - top.value - bottomPadding)
})
let attempt = 0, timeout, active = true, frameCleanup = () => {}, lastActivityRefresh = 0, refreshing = false, warned = false
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
    warned = false
    frameUrl.value = hdiFrameUrl(response.data?.frameUrl, window.location.origin, taskId.value, readonly.value, isNewCreate.value)
    timeout = setTimeout(() => { if (current === attempt) fail() }, 45000)
  } catch { if (current === attempt) fail() }
}
function backToList() {
  taskId.value = null
  readonly.value = false
  isNewCreate.value = false
  const query = { ...route.query }
  delete query.hdiTask
  delete query.hdiReadonly
  delete query.hdiNew
  router.replace({ query })
  if (nativeContext.value) {
    frameCleanup()
    clearTimeout(timeout)
    loading.value = true
    frameUrl.value = hdiFrameUrl(nativeContext.value.frameUrl, window.location.origin)
    timeout = setTimeout(fail, 45000)
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
    const response = await fetch('/detl/getEtlUserInfo', {
      credentials: 'same-origin', cache: 'no-store',
      headers: { Workspace: String(nativeContext.value.workspaceId), 'X-Requested-With': 'XMLHttpRequest' }
    })
    if (!response.ok) throw new Error('Native session is unavailable')
    const native = await response.json()
    if (String(native?.code) !== '0') throw new Error('Native session was not confirmed')
    if (current !== attempt || !active) return
    warned = false
  } catch {
    if (current !== attempt || !active) return
    // Preserve the frame so the user can recover unsaved text before reconnecting.
    if (!warned) ElMessage.warning('任务会话需要重新连接，请先保留尚未保存的内容。')
    warned = true
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
    // This is the original application's mounted shell, not an invented task-success signal.
    // Native table/canvas loaders and API failures continue to be displayed by the original UI.
    const mounted = () => {
      if (document.querySelector('main#app')) { clearTimeout(timeout); loading.value = false; observer.disconnect() }
    }
    const observer = new MutationObserver(mounted)
    observer.observe(document.documentElement, { childList: true, subtree: true })
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
onMounted(connect)
onActivated(() => { active = true })
onDeactivated(() => { active = false })
onBeforeUnmount(() => {
  active = false
  ++attempt
  clearTimeout(timeout)
  frameCleanup()
})
</script>

<style scoped>
.hdi-frame-host { width: 100%; min-height: 0; }
.hdi-task-frame { display: block; width: 100%; height: 100%; border: 0; }
</style>
