<template>
  <div class="document-editor-page">
    <header class="editor-header">
      <button type="button" class="close-button" aria-label="关闭编辑器" @click="closeEditor"><el-icon><Close /></el-icon></button>
      <span class="file-mark" :class="`is-${currentDocument?.fileType || 'docx'}`">{{ ['xls', 'xlsx'].includes(currentDocument?.fileType) ? 'X' : 'W' }}</span>
      <div class="editor-title">
        <strong>{{ currentDocument?.title || '正在打开文档…' }}</strong>
        <span>{{ permission === 'EDIT' ? '可编辑' : '仅查看' }} · 服务器版本 {{ currentDocument?.contentVersion || 1 }}</span>
      </div>
      <div class="editor-spacer" />
      <button
        type="button"
        class="save-state"
        :class="`is-${saveState.type}`"
        :disabled="saveState.type !== 'error' || permission !== 'EDIT'"
        :title="saveState.type === 'error' && permission === 'EDIT' ? '点击重新保存' : saveState.label"
        @click="retryServerSave"
      >
        <el-icon v-if="saveState.type === 'syncing'" class="is-loading"><Loading /></el-icon>
        <el-icon v-else-if="saveState.type === 'error'"><WarningFilled /></el-icon>
        <el-icon v-else-if="saveState.type === 'view'"><View /></el-icon>
        <el-icon v-else><CircleCheckFilled /></el-icon>
        {{ saveState.label }}
      </button>
      <div v-if="Number(currentDocument?.collaboratorCount)" class="online-summary">
        <el-icon><User /></el-icon>{{ currentDocument.collaboratorCount }} 名协作者
      </div>
      <el-button
        v-if="currentDocument?.accessPermission === 'OWNER'"
        icon="Share"
        @click="shareOpen = true"
        v-hasPermi="['document:document:share']"
      >共享</el-button>
    </header>

    <main class="editor-stage">
      <div v-if="loading" class="editor-placeholder">
        <el-icon class="is-loading"><Loading /></el-icon>
        <strong>正在连接内网编辑器</strong>
        <span>文档内容不会离开当前内网环境</span>
      </div>
      <div v-else-if="fatalError" class="editor-error">
        <el-icon><WarningFilled /></el-icon>
        <strong>在线编辑器暂时无法打开</strong>
        <span>{{ fatalError }}</span>
        <el-button type="primary" @click="initializeEditor">重新连接</el-button>
      </div>
      <div id="onlyoffice-editor" class="onlyoffice-editor" :class="{ 'is-hidden': loading || fatalError }" />
    </main>

    <DocumentShareDrawer v-model="shareOpen" :document="currentDocument" @saved="reloadDocument" />
  </div>
</template>

<script setup name="DocumentEditor">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DocumentShareDrawer from '../components/DocumentShareDrawer.vue'
import { forceSaveDocument, getDocument, getDocumentEditorConfig } from '@/api/document/workspace.js'

const FORCE_SAVE_DEBOUNCE_MS = 800
const SERVER_SAVE_TIMEOUT_MS = 45000
const PERSISTENCE_POLL_ACTIVE_MS = 1000
const PERSISTENCE_POLL_IDLE_MS = 15000

const route = useRoute()
const router = useRouter()
const currentDocument = ref(null)
const permission = ref('VIEW')
const loading = ref(true)
const fatalError = ref('')
const shareOpen = ref(false)
const saveState = reactive({ type: 'ready', label: '准备连接' })
let editorInstance = null
let persistenceTimer = null
let persistencePolling = false
let lastPersistedVersion = 0
let forceSaveTimer = null
let serverSaveTimeoutTimer = null
let forceSaveInFlight = false
let serverSavePending = false
let changeRevision = 0
let syncedRevision = 0
let requestedRevision = 0
let persistedRevision = 0

onMounted(initializeEditor)
onBeforeUnmount(() => {
  stopPersistencePolling()
  destroyEditor()
})

async function initializeEditor() {
  destroyEditor()
  resetSaveTracking()
  loading.value = true
  fatalError.value = ''
  saveState.type = 'syncing'
  saveState.label = '正在连接'
  try {
    const documentId = route.params.documentId
    const [detailResponse, bootstrapResponse] = await Promise.all([
      getDocument(documentId),
      getDocumentEditorConfig(documentId)
    ])
    currentDocument.value = detailResponse.data
    lastPersistedVersion = Number(currentDocument.value?.contentVersion || 0)
    const bootstrap = bootstrapResponse.data || {}
    permission.value = bootstrap.permission || 'VIEW'
    await loadOnlyOfficeApi(bootstrap.apiJsUrl)
    const config = {
      ...(bootstrap.config || {}),
      events: {
        onDocumentReady: () => {
          if (permission.value === 'EDIT') {
            saveState.type = 'ready'
            saveState.label = '编辑器已连接'
          } else {
            saveState.type = 'view'
            saveState.label = '仅查看，不会修改文档'
          }
        },
        onDocumentStateChange: handleDocumentStateChange,
        onError: (event) => {
          clearServerSaveWait()
          saveState.type = 'error'
          saveState.label = `保存失败${event?.data?.errorCode ? ` (${event.data.errorCode})` : ''}，点击重试`
        },
        onWarning: () => {
          saveState.type = 'syncing'
          saveState.label = '连接波动，正在重试'
        }
      }
    }
    loading.value = false
    await new Promise((resolve) => requestAnimationFrame(resolve))
    editorInstance = new window.DocsAPI.DocEditor('onlyoffice-editor', config)
    startPersistencePolling()
  } catch (error) {
    loading.value = false
    fatalError.value = error?.message || String(error || '请检查 ONLYOFFICE 内网服务和 JWT 配置')
    saveState.type = 'error'
    saveState.label = '连接失败'
  }
}

async function reloadDocument() {
  const response = await getDocument(route.params.documentId)
  currentDocument.value = response.data
}

function setServerSaved(document) {
  saveState.type = 'saved'
  const savedAt = document?.updateTime ? String(document.updateTime).slice(11, 19) : ''
  saveState.label = `已保存到服务器${savedAt ? ` · ${savedAt}` : ''}`
}

function handleDocumentStateChange(event) {
  if (permission.value !== 'EDIT') return
  if (event?.data === true) {
    changeRevision += 1
    clearForceSaveTimer()
    saveState.type = 'syncing'
    saveState.label = '正在同步到编辑器'
    return
  }

  // ONLYOFFICE 的 false 只表示内容已送达编辑服务。没有前置修改事件时，
  // 它可能是初始化或重复通知，不能把已保存状态覆盖成永久等待。
  if (changeRevision === 0) return
  syncedRevision = Math.max(syncedRevision, changeRevision)
  if (syncedRevision <= persistedRevision) return

  saveState.type = 'syncing'
  saveState.label = serverSavePending
    ? '正在保存到服务器'
    : '已同步到编辑器，正在保存到服务器'
  scheduleServerSave()
}

function scheduleServerSave(delay = FORCE_SAVE_DEBOUNCE_MS) {
  clearForceSaveTimer()
  if (permission.value !== 'EDIT' || serverSavePending || forceSaveInFlight
      || syncedRevision <= persistedRevision) return
  forceSaveTimer = window.setTimeout(requestServerSave, delay)
}

async function requestServerSave() {
  clearForceSaveTimer()
  if (permission.value !== 'EDIT' || serverSavePending || forceSaveInFlight
      || syncedRevision <= persistedRevision) return

  const targetRevision = syncedRevision
  requestedRevision = Math.max(requestedRevision, targetRevision)
  forceSaveInFlight = true
  serverSavePending = true
  saveState.type = 'syncing'
  saveState.label = '正在保存到服务器'
  armServerSaveTimeout()
  startPersistencePolling(PERSISTENCE_POLL_ACTIVE_MS)
  try {
    const response = await forceSaveDocument(route.params.documentId)
    if (response.data?.queued === false) {
      // error=4 表示编辑服务中已无新增内容，通常是内置保存或并发回调已先完成。
      clearServerSaveWait()
      persistedRevision = Math.max(persistedRevision, targetRevision)
      await refreshPersistedState()
      settleSaveState()
      return
    }
    await refreshPersistedState()
  } catch (error) {
    clearServerSaveWait()
    if (persistedRevision >= targetRevision) {
      settleSaveState()
    } else {
      saveState.type = 'error'
      saveState.label = `${error?.msg || error?.message || '服务器保存请求失败'}，点击重试`
    }
  } finally {
    forceSaveInFlight = false
    if (!serverSavePending && saveState.type !== 'error' && syncedRevision > persistedRevision) {
      scheduleServerSave()
    }
  }
}

async function retryServerSave() {
  if (permission.value !== 'EDIT' || saveState.type !== 'error') return
  saveState.type = 'syncing'
  saveState.label = '正在重新确认服务器版本'
  await refreshPersistedState()
  if (syncedRevision > persistedRevision) {
    scheduleServerSave(0)
  } else {
    setServerSaved(currentDocument.value)
  }
}

function settleSaveState() {
  if (changeRevision > persistedRevision) {
    saveState.type = 'syncing'
    if (syncedRevision > persistedRevision) {
      saveState.label = '已同步到编辑器，正在保存到服务器'
      scheduleServerSave()
    } else {
      saveState.label = '正在同步到编辑器'
    }
    return
  }
  setServerSaved(currentDocument.value)
}

function startPersistencePolling(delay = PERSISTENCE_POLL_IDLE_MS) {
  stopPersistencePolling()
  persistenceTimer = window.setTimeout(async () => {
    persistenceTimer = null
    await refreshPersistedState()
    if (!loading.value && !fatalError.value) {
      startPersistencePolling(serverSavePending ? PERSISTENCE_POLL_ACTIVE_MS : PERSISTENCE_POLL_IDLE_MS)
    }
  }, delay)
}

function stopPersistencePolling() {
  window.clearTimeout(persistenceTimer)
  persistenceTimer = null
}

async function refreshPersistedState() {
  if (persistencePolling || loading.value || fatalError.value) return
  persistencePolling = true
  try {
    const response = await getDocument(route.params.documentId)
    const document = response.data || {}
    const nextVersion = Number(document.contentVersion || 0)
    currentDocument.value = document
    if (nextVersion > lastPersistedVersion) {
      lastPersistedVersion = nextVersion
      if (requestedRevision > persistedRevision) {
        persistedRevision = requestedRevision
        clearServerSaveWait()
        settleSaveState()
      }
    }
  } catch {
    if (permission.value === 'EDIT' && (serverSavePending || requestedRevision > persistedRevision)) {
      saveState.type = 'error'
      saveState.label = '暂时无法确认服务器保存状态，点击重试'
    }
  } finally {
    persistencePolling = false
  }
}

function armServerSaveTimeout() {
  window.clearTimeout(serverSaveTimeoutTimer)
  serverSaveTimeoutTimer = window.setTimeout(() => {
    if (!serverSavePending) return
    serverSavePending = false
    saveState.type = 'error'
    saveState.label = '服务器保存超时，点击重试'
  }, SERVER_SAVE_TIMEOUT_MS)
}

function clearForceSaveTimer() {
  window.clearTimeout(forceSaveTimer)
  forceSaveTimer = null
}

function clearServerSaveWait() {
  serverSavePending = false
  window.clearTimeout(serverSaveTimeoutTimer)
  serverSaveTimeoutTimer = null
}

function resetSaveTracking() {
  clearForceSaveTimer()
  clearServerSaveWait()
  forceSaveInFlight = false
  changeRevision = 0
  syncedRevision = 0
  requestedRevision = 0
  persistedRevision = 0
}

function loadOnlyOfficeApi(url) {
  if (window.DocsAPI?.DocEditor) return Promise.resolve()
  if (!url) return Promise.reject(new Error('后端未返回 ONLYOFFICE API 地址'))
  const existing = [...window.document.querySelectorAll('script[data-onlyoffice-api]')]
    .find((script) => script.dataset.onlyofficeApi === url)
  if (existing) {
    return new Promise((resolve, reject) => {
      existing.addEventListener('load', resolve, { once: true })
      existing.addEventListener('error', () => reject(new Error('内网编辑器脚本加载失败')), { once: true })
    })
  }
  return new Promise((resolve, reject) => {
    const script = window.document.createElement('script')
    script.src = url
    script.async = true
    script.dataset.onlyofficeApi = url
    script.onload = () => window.DocsAPI?.DocEditor ? resolve() : reject(new Error('ONLYOFFICE API 未正确初始化'))
    script.onerror = () => reject(new Error('无法访问内网 ONLYOFFICE 服务'))
    window.document.head.appendChild(script)
  })
}

function destroyEditor() {
  stopPersistencePolling()
  clearForceSaveTimer()
  clearServerSaveWait()
  try {
    editorInstance?.destroyEditor?.()
  } catch {
    // 编辑器 iframe 已被浏览器回收时无需额外处理。
  }
  editorInstance = null
}

function closeEditor() {
  window.close()
  window.setTimeout(() => {
    if (!window.closed) router.replace('/documents')
  }, 150)
}
</script>

<style scoped lang="scss">
.document-editor-page { display: flex; width: 100vw; height: 100vh; overflow: hidden; flex-direction: column; background: var(--surface-muted); color: var(--app-heading); }
.editor-header { display: flex; height: 54px; flex: 0 0 54px; align-items: center; gap: 10px; padding: 0 14px; border-bottom: 1px solid var(--surface-border); background: var(--surface-strong); box-shadow: 0 1px 3px color-mix(in srgb, var(--app-heading) 5%, transparent); }
.close-button { display: grid; width: 34px; height: 34px; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--app-muted); cursor: pointer; }
.close-button:hover,
.close-button:focus-visible { outline: none; background: var(--surface-subtle); color: var(--el-color-primary); }
.file-mark { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 5px; background: var(--el-color-primary); color: var(--el-color-white); font-size: 12px; font-weight: 750; }
.file-mark.is-xls,
.file-mark.is-xlsx { background: var(--el-color-success); }
.editor-title { min-width: 0; }
.editor-title strong,
.editor-title span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.editor-title strong { max-width: 440px; font-size: 13px; }
.editor-title span { margin-top: 2px; color: var(--app-muted); font-size: 11px; }
.editor-spacer { flex: 1; }
.save-state,
.online-summary { display: flex; align-items: center; gap: 5px; padding: 5px 8px; border-radius: 5px; color: var(--app-muted); background: var(--surface-muted); font-size: 12px; }
.save-state { appearance: none; border: 0; font-family: inherit; }
.save-state:not(:disabled) { cursor: pointer; }
.save-state.is-saved { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.save-state.is-ready,
.save-state.is-view { color: var(--app-muted); background: var(--surface-muted); }
.save-state.is-syncing { color: var(--el-color-primary); background: var(--surface-subtle); }
.save-state.is-error { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.editor-stage { position: relative; min-height: 0; flex: 1; }
.onlyoffice-editor { width: 100%; height: 100%; }
.onlyoffice-editor.is-hidden { visibility: hidden; }
.editor-placeholder,
.editor-error { position: absolute; z-index: 2; inset: 0; display: flex; align-items: center; justify-content: center; gap: 10px; flex-direction: column; background: var(--surface-muted); color: var(--app-muted); }
.editor-placeholder > .el-icon,
.editor-error > .el-icon { color: var(--el-color-primary); font-size: 28px; }
.editor-error > .el-icon { color: var(--el-color-danger); }
.editor-placeholder strong,
.editor-error strong { color: var(--app-heading); font-size: 15px; }
.editor-placeholder span,
.editor-error span { max-width: 580px; text-align: center; }
@media (max-width: 760px) { .online-summary { display: none; } .editor-title strong { max-width: 220px; } }
</style>
