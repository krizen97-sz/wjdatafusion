<template>
  <el-dialog
    :model-value="modelValue"
    title="上传文件"
    width="560px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="!validating"
    :close-on-press-escape="!validating"
    :before-close="beforeClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="upload-dialog-body">
      <div class="upload-target">
        <el-icon><FolderOpened /></el-icon>
        <span>上传到</span>
        <strong>{{ folderName || '请选择目录' }}</strong>
      </div>

      <el-upload
        ref="uploadRef"
        class="document-uploader"
        drag
        :auto-upload="false"
        :show-file-list="false"
        :limit="1"
        accept=".doc,.docx,.xls,.xlsx,.pdf,.zip,.rar"
        :disabled="validating"
        :on-change="handleFileChange"
        :on-remove="clearFile"
        :on-exceed="handleExceed"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-copy">
          <strong>{{ selectedFile ? '重新选择文件' : '选择需要上传的文件' }}</strong>
          <span>拖到此处，或点击选择文件</span>
        </div>
        <template #tip>
          <div class="upload-tip">支持 DOC、DOCX、XLS、XLSX、PDF、ZIP、RAR，当前单文件上限 {{ maximumLabel }}</div>
        </template>
      </el-upload>

      <div v-if="selectedFile" class="selected-file">
        <span class="file-mark" :class="{ 'is-sheet': spreadsheetFile, 'is-pdf': pdfFile, 'is-archive': archiveFile }">{{ fileMarkLabel }}</span>
        <div>
          <strong>{{ selectedFile.name }}</strong>
          <span>{{ formatFileSize(selectedFile.size) }} · {{ validationDescription }}</span>
        </div>
        <el-button v-if="!validating" text circle aria-label="移除已选文件" @click="clearFile"><el-icon><Close /></el-icon></el-button>
      </div>

      <div v-if="validating" class="validation-state is-validating" role="status">
        <el-icon class="is-loading"><Loading /></el-icon>
        <div><strong>文件校验中</strong><span>{{ validationProgress }}</span></div>
      </div>
      <div v-else-if="errorMessage" class="validation-state is-error" role="alert">
        <el-icon><CircleCloseFilled /></el-icon>
        <div><strong>校验失败</strong><span>{{ errorMessage }}</span></div>
      </div>
      <div v-else class="validation-note">
        校验通过后系统会直接完成上传；校验失败不会创建文档记录，也不会保留正式文件。
      </div>
    </div>

    <template #footer>
      <el-button :disabled="validating" @click="closeDialog">取消</el-button>
      <el-button type="primary" :loading="validating" :disabled="!selectedFile || !folderId" @click="submitUpload">
        {{ validating ? '文件校验中' : '开始上传' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { uploadDocument } from '@/api/document/workspace.js'
import { formatFileSize, isSpreadsheetFile } from '../workspace/documentWorkspaceRules.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  folderId: { type: Number, default: 0 },
  folderName: { type: String, default: '请选择目录' },
  maxUploadSize: { type: Number, default: 100 * 1024 * 1024 }
})
const emit = defineEmits(['update:modelValue', 'uploaded'])

const uploadRef = ref(null)
const selectedFile = ref(null)
const validating = ref(false)
const errorMessage = ref('')
const fileType = computed(() => String(selectedFile.value?.name || '').split('.').pop().toLowerCase())
const spreadsheetFile = computed(() => isSpreadsheetFile(fileType.value))
const pdfFile = computed(() => fileType.value === 'pdf')
const archiveFile = computed(() => ['zip', 'rar'].includes(fileType.value))
const fileMarkLabel = computed(() => archiveFile.value
  ? fileType.value.slice(0, 1).toUpperCase()
  : (pdfFile.value ? 'P' : (spreadsheetFile.value ? 'X' : 'W')))
const maximumBytes = computed(() => Math.min(100 * 1024 * 1024, Math.max(1, Number(props.maxUploadSize || 0))))
const maximumLabel = computed(() => formatFileSize(maximumBytes.value))
const validationDescription = computed(() => archiveFile.value
  ? '上传前将校验压缩包真实格式；仅用于文件管理与传输'
  : (pdfFile.value
      ? '上传前将校验真实格式、页面结构和安全状态；上传后只读预览'
      : '上传前将校验真实格式、文件结构和内部引用'))
const validationProgress = computed(() => archiveFile.value
  ? '正在核对压缩包签名与容器结构，请稍候…'
  : (pdfFile.value
      ? '正在核对 PDF 签名、加密状态、页面结构和活动内容，请稍候…'
      : '正在核对格式、加密状态、宏代码和内部引用，请稍候…'))

watch(() => props.modelValue, (open) => {
  if (open) reset()
})

function handleFileChange(uploadFile) {
  const file = uploadFile?.raw
  errorMessage.value = ''
  if (!file) {
    selectedFile.value = null
    return
  }
  const failure = validateSelection(file)
  if (failure) {
    selectedFile.value = null
    errorMessage.value = failure
    uploadRef.value?.clearFiles()
    return
  }
  selectedFile.value = file
}

function handleExceed(files) {
  uploadRef.value?.clearFiles()
  if (files?.[0]) uploadRef.value?.handleStart(files[0])
}

function validateSelection(file) {
  const extension = String(file.name || '').split('.').pop().toLowerCase()
  if (!['doc', 'docx', 'xls', 'xlsx', 'pdf', 'zip', 'rar'].includes(extension)) return '仅支持 DOC、DOCX、XLS、XLSX、PDF、ZIP 和 RAR 文件'
  if (!file.size) return '所选文件为空，请重新选择'
  if (file.size > maximumBytes.value) return `文件大小超过当前 ${maximumLabel.value} 限制`
  return ''
}

async function submitUpload() {
  if (!selectedFile.value || validating.value) return
  if (!Number(props.folderId)) {
    errorMessage.value = '根目录不能挂载文件，请先新建并选择一个目录'
    return
  }
  errorMessage.value = ''
  validating.value = true
  try {
    const response = await uploadDocument(selectedFile.value, props.folderId)
    emit('uploaded', { ...(response.data || {}), folderId: Number(props.folderId) })
    emit('update:modelValue', false)
  } catch (error) {
    errorMessage.value = error?.message || '文件校验或上传失败，请检查文件后重试'
  } finally {
    validating.value = false
  }
}

function clearFile() {
  selectedFile.value = null
  errorMessage.value = ''
  uploadRef.value?.clearFiles()
}

function reset() {
  validating.value = false
  selectedFile.value = null
  errorMessage.value = ''
  uploadRef.value?.clearFiles()
}

function closeDialog() {
  if (!validating.value) emit('update:modelValue', false)
}

function beforeClose(done) {
  if (!validating.value) done()
}
</script>

<style scoped lang="scss">
.upload-dialog-body { color: #172033; }
.upload-target {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 14px;
  padding: 9px 11px;
  border-radius: 7px;
  background: #f4f7fb;
  color: #667085;
  font-size: 12px;
  .el-icon { color: #1677ff; }
  strong { color: #344054; }
}

.document-uploader :deep(.el-upload) { width: 100%; }
.document-uploader :deep(.el-upload-dragger) {
  display: flex;
  min-height: 158px;
  align-items: center;
  justify-content: center;
  gap: 15px;
  border-color: #b8c7dc;
  border-radius: 8px;
  background: #fbfdff;
  &:hover { border-color: #1677ff; background: #f7fbff; }
}
.upload-icon { color: #1677ff; font-size: 42px; }
.upload-copy {
  text-align: left;
  strong,
  span { display: block; }
  strong { font-size: 15px; }
  span { margin-top: 6px; color: #667085; font-size: 12px; }
}
.upload-tip { margin-top: 7px; color: #98a2b3; font-size: 12px; text-align: center; }

.selected-file {
  display: flex;
  align-items: center;
  gap: 11px;
  margin-top: 14px;
  padding: 11px;
  border: 1px solid #dde4ee;
  border-radius: 7px;
  > div { min-width: 0; flex: 1; }
  strong,
  span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  strong { font-size: 13px; }
  span { margin-top: 4px; color: #667085; font-size: 11px; }
}
.file-mark {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 6px;
  background: #1677ff;
  color: #fff;
  font-weight: 750;
  &.is-sheet { background: #15803d; }
  &.is-pdf { background: #c2413b; }
  &.is-archive { background: #7c3aed; }
}

.validation-state {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 14px;
  padding: 11px;
  border-radius: 7px;
  .el-icon { margin-top: 2px; font-size: 18px; }
  strong,
  span { display: block; }
  strong { font-size: 13px; }
  span { margin-top: 3px; font-size: 12px; line-height: 1.55; }
  &.is-validating { background: #eef6ff; color: #0f5eba; }
  &.is-error { background: #fff2f0; color: #b42318; }
}
.validation-note { margin-top: 12px; color: #667085; font-size: 12px; line-height: 1.6; }
</style>
