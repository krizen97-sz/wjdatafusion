<template>
  <div class="pdf-preview-page">
    <header class="preview-header">
      <el-button text class="back-button" aria-label="返回文档管理" @click="closePreview">
        <el-icon><ArrowLeft /></el-icon><span>返回</span>
      </el-button>
      <span class="pdf-mark" aria-hidden="true">P</span>
      <div class="preview-title">
        <strong>{{ currentDocument?.title || 'PDF 在线预览' }}</strong>
        <span v-if="currentDocument">
          {{ formatFileSize(currentDocument.fileSize) }} · {{ currentDocument.ownerName || '未知所有者' }} · {{ permissionLabel }}
        </span>
        <span v-else>正在读取文件信息…</span>
      </div>
      <div class="preview-spacer" />
      <el-tag type="info" effect="plain">只读预览</el-tag>
      <el-button :loading="loading" icon="Refresh" @click="loadPreview">重新加载</el-button>
      <el-button type="primary" plain icon="Download" :disabled="!currentDocument" @click="downloadPdf">下载 PDF</el-button>
    </header>

    <main class="preview-stage">
      <div v-if="loading" class="preview-state" role="status">
        <span class="platform-loading-mark is-large" aria-hidden="true"></span>
        <strong>正在安全加载 PDF</strong>
        <span>{{ loadingDescription }}</span>
        <el-progress v-if="downloadProgress > 0" :percentage="downloadProgress" :stroke-width="5" :show-text="false" />
      </div>

      <div v-else-if="fatalError" class="preview-state is-error" role="alert">
        <span class="error-mark"><el-icon><WarningFilled /></el-icon></span>
        <strong>PDF 无法预览</strong>
        <span>{{ fatalError }}</span>
        <div><el-button @click="closePreview">返回文档管理</el-button><el-button type="primary" @click="loadPreview">重试</el-button></div>
      </div>

      <template v-else>
        <iframe
          class="pdf-frame"
          :class="{ 'is-ready': viewerReady }"
          :src="previewUrl"
          :title="`${currentDocument?.title || 'PDF'} 只读预览`"
          referrerpolicy="no-referrer"
          @load="viewerReady = true"
        />
        <div v-if="!viewerReady" class="viewer-loading" role="status">
          <span class="platform-loading-mark is-small" aria-hidden="true"></span><span>正在打开 PDF 阅读器…</span>
        </div>
      </template>
    </main>

    <footer class="preview-footer">
      <span><el-icon><Lock /></el-icon>当前页面不提供编辑或保存入口，PDF 原文件不会被修改</span>
      <span>文件通过登录权限校验后在本机浏览器中打开，不使用外部预览服务</span>
    </footer>
  </div>
</template>

<script setup name="DocumentPreview">
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDocument, getDocumentPreview } from '@/api/document/workspace.js'
import { formatFileSize, isPdfFile } from '../workspace/documentWorkspaceRules.js'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const currentDocument = ref(null)
const previewUrl = ref('')
const loading = ref(true)
const viewerReady = ref(false)
const fatalError = ref('')
const downloadProgress = ref(0)
const loadedBytes = ref(0)

const permissionLabel = computed(() => ({
  OWNER: '所有者',
  EDIT: '只读预览',
  VIEW: '仅查看',
  ADMIN: '管理员查看'
})[currentDocument.value?.accessPermission] || '仅查看')
const loadingDescription = computed(() => downloadProgress.value > 0
  ? `文件已加载 ${downloadProgress.value}%`
  : (loadedBytes.value > 0 ? `已读取 ${formatFileSize(loadedBytes.value)}` : '正在校验访问权限并读取文件内容…'))

onMounted(loadPreview)
onBeforeUnmount(revokePreviewUrl)

async function loadPreview() {
  const documentId = Number(route.params.documentId)
  if (!documentId) {
    fatalError.value = '文档地址无效'
    loading.value = false
    return
  }
  loading.value = true
  viewerReady.value = false
  fatalError.value = ''
  downloadProgress.value = 0
  loadedBytes.value = 0
  revokePreviewUrl()
  try {
    const metadata = await getDocument(documentId)
    if (!isPdfFile(metadata.data?.fileType)) throw new Error('当前文件不是可在线预览的 PDF')
    currentDocument.value = metadata.data
    document.title = `${metadata.data.title} - PDF 预览`
    const payload = await getDocumentPreview(documentId, (event) => {
      loadedBytes.value = Number(event.loaded || 0)
      if (event.total) downloadProgress.value = Math.min(100, Math.round(event.loaded * 100 / event.total))
    })
    if (!(payload instanceof Blob) || payload.size === 0) throw new Error('服务器返回的 PDF 文件为空')
    const signature = await payload.slice(0, 5).text()
    if (signature !== '%PDF-') throw new Error(await responseErrorMessage(payload))
    previewUrl.value = URL.createObjectURL(new Blob([payload], { type: 'application/pdf' }))
  } catch (error) {
    fatalError.value = error?.message || String(error || 'PDF 加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function responseErrorMessage(blob) {
  try {
    const result = JSON.parse(await blob.text())
    return result.msg || '服务器没有返回有效的 PDF 文件'
  } catch {
    return '服务器没有返回有效的 PDF 文件'
  }
}

function revokePreviewUrl() {
  if (!previewUrl.value) return
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function downloadPdf() {
  if (!currentDocument.value?.documentId) return
  proxy.download(
    `/document/workspace/documents/${currentDocument.value.documentId}/download`,
    {},
    currentDocument.value.title
  )
}

function closePreview() {
  window.close()
  window.setTimeout(() => {
    if (!window.closed) router.push('/documents')
  }, 80)
}
</script>

<style scoped lang="scss">
.pdf-preview-page {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  flex-direction: column;
  background: var(--surface-muted);
  color: var(--app-heading);
}

.preview-header {
  display: flex;
  min-height: 58px;
  flex: 0 0 58px;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-strong);
  box-shadow: 0 1px 4px color-mix(in srgb, var(--app-heading) 6%, transparent);
}

.back-button { margin-left: -7px; }
.pdf-mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 7px;
  background: var(--el-color-danger);
  color: var(--el-color-white);
  font-weight: 750;
}
.preview-title {
  min-width: 0;
  strong,
  span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  strong { max-width: 520px; font-size: 14px; }
  span { margin-top: 3px; color: var(--app-muted); font-size: 11px; }
}
.preview-spacer { flex: 1; }

.preview-stage { position: relative; min-height: 0; flex: 1; }
.pdf-frame {
  width: 100%;
  height: 100%;
  border: 0;
  opacity: 0;
  background: var(--surface-muted);
  transition: opacity 160ms ease;
  &.is-ready { opacity: 1; }
}
.preview-state,
.viewer-loading {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  flex-direction: column;
  background: var(--surface-strong);
  color: var(--app-muted);
  text-align: center;
  strong { color: var(--app-heading); font-size: 16px; }
  > span:not(.platform-loading-mark, .error-mark) { max-width: 560px; line-height: 1.65; }
  :deep(.el-progress) { width: min(360px, 70vw); margin-top: 4px; }
}
.error-mark {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 24px;
}
.preview-state.is-error .error-mark { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.preview-state.is-error > div { margin-top: 8px; }
.viewer-loading { background: var(--surface-muted); }

.preview-footer {
  display: flex;
  min-height: 32px;
  flex: 0 0 32px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 16px;
  border-top: 1px solid var(--surface-border);
  background: var(--surface-strong);
  color: var(--app-muted);
  font-size: 11px;
  span { display: flex; align-items: center; gap: 5px; }
}

@media (max-width: 760px) {
  .preview-header { padding: 0 10px; }
  .preview-title strong { max-width: 210px; }
  .preview-header > .el-tag,
  .preview-header > .el-button:not(.back-button):not(:last-child),
  .preview-footer span:last-child { display: none; }
  .preview-footer { justify-content: center; }
}

@media (prefers-reduced-motion: reduce) {
  .pdf-frame { transition: none; }
}
</style>
