<template>
  <div class="app-container document-workspace-page">
    <div class="workspace-shell">
      <aside class="workspace-sidebar">
        <section class="directory-navigation" aria-label="我的目录">
          <div class="folder-heading">
            <div class="folder-heading-title"><strong>我的目录</strong><span>{{ folders.length }} 个 · 拖动排序</span></div>
            <el-button text circle aria-label="新建根目录" @click="createFolder(0)" v-hasPermi="[FILE_MANAGEMENT_PERMISSION]">
              <el-icon><Plus /></el-icon>
            </el-button>
          </div>
          <div
            class="root-folder-row"
            :class="dropTargetClass('FOLDER', 0)"
            @dragenter.prevent.stop="enterDragTarget($event, 'FOLDER', 0, '根目录')"
            @dragover.prevent.stop="enterDragTarget($event, 'FOLDER', 0, '根目录')"
            @dragleave.stop="leaveDragTarget($event, 'FOLDER', 0)"
            @drop.prevent.stop="dropOnRootFolder"
          >
            <button type="button" class="root-folder" :class="{ 'is-active': rootFolderActive }" @click="selectRootFolder">
              <el-icon class="root-folder-icon"><FolderOpened /></el-icon>
              <span class="root-folder-copy">
                <strong>根目录</strong>
                <small>
                  {{ workspaceSummary.fileCount }} 个文件 · {{ formatStorageMegabytes(workspaceSummary.totalSize) }}
                  <el-icon title="根目录不能挂载文件"><Lock /></el-icon>
                </small>
              </span>
            </button>
            <button
              type="button"
              class="storage-summary-trigger"
              aria-label="查看存储空间详情"
              :title="`已使用 ${formatFileSize(workspaceSummary.usedSize)}，点击查看存储空间详情`"
              @click.stop="storageDetailOpen = true"
            >
              <span><el-icon><PieChart /></el-icon>存储</span>
              <strong>{{ formatFileSize(workspaceSummary.usedSize) }}</strong>
              <i aria-hidden="true"><b :style="{ transform: `scaleX(${storagePercentage / 100})` }" /></i>
            </button>
          </div>
          <p v-if="workspaceSummary.unfiledCount > 0" class="root-folder-note is-warning">
            检测到 {{ workspaceSummary.unfiledCount }} 个历史根文档，请移入具体目录
          </p>
          <div class="folder-tree-caption"><span>文件夹</span><span>文件</span></div>
          <el-scrollbar class="folder-scroll">
            <el-tree
              v-loading="loading.folders"
              :data="folderTree"
              node-key="folderId"
              default-expand-all
              :expand-on-click-node="false"
              empty-text="还没有目录，点击上方 + 新建"
            >
              <template #default="{ data }">
                <div
                  class="folder-node"
                  :class="[{ 'is-active': Number(query.folderId) === Number(data.folderId) }, dropTargetClass('FOLDER', data.folderId), folderSortClass(data)]"
                  :style="{ '--folder-color': normalizeFolderColor(data.folderColor) }"
                  role="treeitem"
                  tabindex="0"
                  :aria-selected="Number(query.folderId) === Number(data.folderId)"
                  :aria-label="`打开目录 ${data.folderName}`"
                  @click="selectFolder(data)"
                  @keydown.enter.prevent="selectFolder(data)"
                  @keydown.space.prevent="selectFolder(data)"
                  @dragenter.prevent.stop="enterFolderNodeDrag($event, data)"
                  @dragover.prevent.stop="enterFolderNodeDrag($event, data)"
                  @dragleave.stop="leaveFolderNodeDrag($event, data)"
                  @drop.prevent.stop="dropOnFolderNode($event, data)"
                >
                  <button
                    type="button"
                    class="folder-drag-handle"
                    draggable="true"
                    :aria-label="`拖动“${data.folderName}”调整同级顺序`"
                    title="拖动调整同级顺序"
                    @click.stop
                    @dragstart.stop="startFolderDrag($event, data)"
                    @dragend.stop="endFolderDrag"
                  ><el-icon><Rank /></el-icon></button>
                  <span class="folder-color-icon" aria-hidden="true"><el-icon><Folder /></el-icon></span>
                  <span class="folder-name">{{ data.folderName }}</span>
                  <small class="folder-file-count" :title="`共 ${Number(data.documentCount || 0)} 个文件，${formatFileSize(data.totalSize)}`">{{ Number(data.documentCount || 0) }}</small>
                  <el-dropdown trigger="click" @command="(command) => folderCommand(command, data)">
                    <el-button text circle size="small" aria-label="目录操作" @click.stop><el-icon><MoreFilled /></el-icon></el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="child">新建子目录</el-dropdown-item>
                        <el-dropdown-item command="edit">编辑名称与颜色</el-dropdown-item>
                        <el-dropdown-item command="delete" divided>删除目录</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-tree>
          </el-scrollbar>
        </section>

        <div class="sidebar-divider" />
        <section class="workspace-navigation" aria-label="工作区">
          <div class="scope-heading">工作区</div>
          <nav class="scope-nav" aria-label="文档范围">
            <button
              v-for="scope in visibleDocumentScopes"
              :key="scope.value"
              type="button"
              :class="[{ 'is-active': query.scope === scope.value }, dropTargetClass('SCOPE', scope.value)]"
              @click="selectScope(scope.value)"
              @dragenter.prevent.stop="enterDragTarget($event, 'SCOPE', scope.value, scope.label)"
              @dragover.prevent.stop="enterDragTarget($event, 'SCOPE', scope.value, scope.label)"
              @dragleave.stop="leaveDragTarget($event, 'SCOPE', scope.value)"
              @drop.prevent.stop="dropOnScope(scope.value)"
            >
              <el-icon><component :is="scope.icon" /></el-icon>
              <span>{{ scope.label }}</span>
            </button>
          </nav>
          <div v-if="!showFolderNavigation" class="scope-context">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ scopeDescription }}</span>
          </div>
        </section>
      </aside>

      <main class="document-panel">
        <div class="panel-heading">
          <div class="panel-title">
            <h3>{{ contentTitle }}</h3>
            <el-breadcrumb v-if="showFolderNavigation && query.folderId != null" separator="/">
              <el-breadcrumb-item>{{ currentScope.label }}</el-breadcrumb-item>
              <el-breadcrumb-item v-if="Number(query.folderId) === 0">{{ query.scope === 'ARCHIVED' ? '全部归档' : '根目录（全部文件）' }}</el-breadcrumb-item>
              <el-breadcrumb-item v-for="folder in folderBreadcrumb" :key="folder.folderId">{{ folder.folderName }}</el-breadcrumb-item>
            </el-breadcrumb>
            <span v-else>{{ scopeDescription }}</span>
          </div>
          <div class="panel-heading-controls">
            <div class="panel-primary-actions" role="toolbar" aria-label="文档快捷操作">
              <el-button class="compact-panel-action" size="small" icon="Refresh" :loading="loading.documents" aria-label="刷新" title="刷新" @click="refreshWorkspace">
                <span class="compact-panel-action-label">刷新</span>
              </el-button>
              <el-button class="compact-panel-action" size="small" icon="FolderAdd" aria-label="新建目录" title="新建目录" @click="createFolder(0)" v-hasPermi="[FILE_MANAGEMENT_PERMISSION]">
                <span class="compact-panel-action-label">新建目录</span>
              </el-button>
              <el-button v-if="isDocumentAdmin" class="compact-panel-action" size="small" icon="Setting" aria-label="空间管理" title="空间管理" @click="storageDrawerOpen = true">
                <span class="compact-panel-action-label">空间管理</span>
              </el-button>
              <el-button class="upload-document-button" size="small" icon="Upload" @click="openUploadDialog" v-hasPermi="[FILE_MANAGEMENT_PERMISSION]">上传文件</el-button>
              <el-button class="create-word-button" size="small" type="primary" :loading="loading.mutation" @click="createNewDocument('docx')" v-hasPermi="['document:document:add']">
                <el-icon><DocumentAdd /></el-icon>新建 Word
              </el-button>
              <el-button class="create-excel-button" size="small" :loading="loading.mutation" @click="createNewDocument('xlsx')" v-hasPermi="['document:document:add']">
                <el-icon><Grid /></el-icon>新建 Excel
              </el-button>
            </div>
            <div class="view-switch" aria-label="视图切换">
              <button type="button" :class="{ 'is-active': viewMode === 'list' }" aria-label="列表视图" @click="setViewMode('list')"><el-icon><List /></el-icon></button>
              <button type="button" :class="{ 'is-active': viewMode === 'grid' }" aria-label="网格视图" @click="setViewMode('grid')"><el-icon><Grid /></el-icon></button>
            </div>
          </div>
        </div>

        <div class="document-toolbar">
          <el-input v-model="query.keyword" class="document-search" clearable prefix-icon="Search" placeholder="搜索文档名称" @input="scheduleSearch" />
          <el-select v-model="query.fileType" class="type-filter" placeholder="全部类型" clearable>
            <el-option label="Word 文档（DOCX）" value="docx" />
            <el-option label="Word 旧格式（DOC）" value="doc" />
            <el-option label="Excel 表格（XLSX）" value="xlsx" />
            <el-option label="Excel 旧格式（XLS）" value="xls" />
            <el-option label="PDF 文件（只读预览）" value="pdf" />
            <el-option label="ZIP 压缩包" value="zip" />
            <el-option label="RAR 压缩包" value="rar" />
          </el-select>
          <div v-if="query.scope === 'SHARED'" class="permission-filter" role="group" aria-label="共享权限筛选">
            <button v-for="item in SHARED_PERMISSION_FILTERS" :key="item.value" type="button" :class="{ 'is-active': query.accessPermission === item.value }" @click="query.accessPermission = item.value">{{ item.label }}</button>
          </div>
          <span class="toolbar-spacer" />
          <el-button
            v-if="canDownloadDocuments"
            class="batch-download-button"
            icon="Download"
            :loading="loading.batchDownload"
            :disabled="!selectedDocumentIds.length"
            @click="downloadSelectedDocuments"
          >批量下载<span v-if="selectedDocumentIds.length">（{{ selectedDocumentIds.length }}）</span></el-button>
          <button v-if="selectedDocumentIds.length" type="button" class="clear-selection" @click="selectedDocumentIds = []">清空选择</button>
          <span class="result-count">{{ documents.length }} 项</span>
        </div>

        <div v-loading="loading.documents" class="document-content">
          <div v-if="documents.length && viewMode === 'list'" class="document-list" role="table" aria-label="文档列表">
            <div class="document-list-header" role="row">
              <span class="selection-cell">
                <el-checkbox
                  v-if="canDownloadDocuments"
                  :model-value="allDownloadableSelected"
                  :indeterminate="partlySelected"
                  :disabled="!downloadableDocuments.length"
                  aria-label="选择当前列表全部可下载文档"
                  @change="toggleSelectAll"
                />
              </span>
              <span>名称</span><span>所在目录</span><span>所有者</span><span>协作状态</span><span>服务器版本</span><span>更新时间</span><span />
            </div>
            <div
              v-for="document in documents"
              :key="document.documentId"
              class="document-row"
              :class="{ 'is-selected': selectedDocumentSet.has(Number(document.documentId)), 'is-dragging': Number(draggedDocument?.documentId) === Number(document.documentId) }"
              :draggable="document.accessPermission === 'OWNER'"
              @dragstart="startDocumentDrag($event, document)"
              @dragend="endDocumentDrag"
            >
              <span class="selection-cell" @mousedown.stop @click.stop>
                <el-checkbox
                  v-if="canDownloadDocuments"
                  :model-value="selectedDocumentSet.has(Number(document.documentId))"
                  :disabled="document.lifecycleStatus === 'TRASH'"
                  :aria-label="`选择文档 ${document.title}`"
                  @change="(checked) => toggleDocumentSelection(document.documentId, checked)"
                />
              </span>
              <a class="document-name" :href="documentHref(document)" :target="isArchiveFile(document.fileType) ? null : '_blank'" rel="noopener noreferrer" @click="guardDocumentOpen($event, document)">
                <span class="file-mark" :class="`is-${document.fileType}`">{{ fileMarkLabel(document.fileType) }}</span>
                <span><strong>{{ document.title }}</strong><small>{{ String(document.fileType || '').toUpperCase() }} · {{ formatFileSize(document.fileSize) }}</small></span>
              </a>
              <span class="folder-location-cell" :title="documentFolderPath(document)"><el-icon><Folder /></el-icon><span>{{ documentFolderPath(document) }}</span></span>
              <span class="owner-cell">{{ document.ownerName || '-' }}</span>
              <span class="collaboration-cell">
                <span v-if="collaboratorNames(document.collaboratorNames).length" class="avatar-stack">
                  <el-avatar v-for="name in collaboratorNames(document.collaboratorNames).slice(0, 3)" :key="name" :size="25">{{ initials(name) }}</el-avatar>
                  <small v-if="Number(document.collaboratorCount) > 3">+{{ Number(document.collaboratorCount) - 3 }}</small>
                </span>
                <span v-else class="muted">仅自己</span>
                <button
                  v-if="document.accessPermission === 'OWNER'"
                  type="button"
                  class="permission-entry"
                  :aria-label="`编辑“${document.title}”的共享权限`"
                  title="查看并编辑共享权限"
                  @click.stop="openShare(document)"
                >
                  <el-tag size="small" effect="plain" :type="permissionMeta(document).type">{{ permissionMeta(document).label }}</el-tag>
                  <el-icon><ArrowRight /></el-icon>
                </button>
                <el-tag v-else size="small" effect="plain" :type="permissionMeta(document).type">{{ permissionMeta(document).label }}</el-tag>
              </span>
              <button
                type="button"
                class="saved-cell version-entry"
                :aria-label="`查看“${document.title}”的修改记录`"
                title="查看修改记录"
                @click.stop="openRecords(document)"
              ><el-icon><CircleCheckFilled /></el-icon>{{ recordLabel(document) }}</button>
              <time>{{ document.updateTime || document.createTime || '-' }}</time>
              <el-dropdown trigger="click" @command="(command) => documentCommand(command, document)">
                <el-button text circle aria-label="文档操作"><el-icon><MoreFilled /></el-icon></el-button>
                <template #dropdown><DocumentActionMenu :document="document" /></template>
              </el-dropdown>
            </div>
          </div>

          <div v-else-if="documents.length" class="document-grid">
            <article
              v-for="document in documents"
              :key="document.documentId"
              class="document-card"
              :class="{ 'is-selected': selectedDocumentSet.has(Number(document.documentId)), 'is-dragging': Number(draggedDocument?.documentId) === Number(document.documentId) }"
              :draggable="document.accessPermission === 'OWNER'"
              @dragstart="startDocumentDrag($event, document)"
              @dragend="endDocumentDrag"
            >
              <div class="document-card-top">
                <span class="card-file">
                  <el-checkbox
                    v-if="canDownloadDocuments"
                    :model-value="selectedDocumentSet.has(Number(document.documentId))"
                    :disabled="document.lifecycleStatus === 'TRASH'"
                    :aria-label="`选择文档 ${document.title}`"
                    @mousedown.stop
                    @click.stop
                    @change="(checked) => toggleDocumentSelection(document.documentId, checked)"
                  />
                  <span class="file-mark" :class="`is-${document.fileType}`">{{ fileMarkLabel(document.fileType) }}</span>
                </span>
                <el-dropdown trigger="click" @command="(command) => documentCommand(command, document)">
                  <el-button text circle aria-label="文档操作" title="文档操作"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown><DocumentActionMenu :document="document" compact /></template>
                </el-dropdown>
              </div>
              <a :href="documentHref(document)" :target="isArchiveFile(document.fileType) ? null : '_blank'" rel="noopener noreferrer" @click="guardDocumentOpen($event, document)">{{ document.title }}</a>
              <span class="document-card-meta">{{ String(document.fileType || '').toUpperCase() }} · {{ formatFileSize(document.fileSize) }}</span>
              <span class="document-card-location" :title="documentFolderPath(document)"><el-icon><Folder /></el-icon>{{ documentFolderPath(document) }}</span>
              <footer><el-tag size="small" effect="plain" :type="permissionMeta(document).type">{{ permissionMeta(document).label }}</el-tag><time>{{ document.updateTime || '-' }}</time></footer>
            </article>
          </div>

          <el-empty v-else :image-size="92" :description="emptyDescription">
            <el-button v-if="canCreateInCurrentFolder" type="primary" @click="createNewDocument('docx')" v-hasPermi="['document:document:add']">新建第一份文档</el-button>
          </el-empty>
        </div>
        <transition name="drag-feedback">
          <div v-if="draggedDocument" class="drag-feedback" :class="dragFeedback.tone" role="status" aria-live="polite">
            <span class="drag-feedback-icon">
              <el-icon v-if="dragFeedback.tone === 'is-ready'"><CircleCheckFilled /></el-icon>
              <el-icon v-else-if="dragFeedback.tone === 'is-blocked'"><WarningFilled /></el-icon>
              <el-icon v-else><Rank /></el-icon>
            </span>
            <span class="drag-feedback-copy"><strong>{{ dragFeedback.title }}</strong><small>{{ dragFeedback.detail }}</small></span>
          </div>
        </transition>
      </main>
    </div>

    <div ref="dragPreviewRef" class="document-drag-preview" aria-hidden="true"><el-icon><Document /></el-icon><span>文档</span></div>

    <transition name="drag-feedback">
      <div v-if="draggedFolder" class="folder-sort-feedback" :class="folderSortFeedback.tone" role="status" aria-live="polite">
        <el-icon><Rank /></el-icon>
        <span><strong>{{ folderSortFeedback.title }}</strong><small>{{ folderSortFeedback.detail }}</small></span>
      </div>
    </transition>

    <DocumentShareDrawer v-model="shareOpen" :document="activeDocument" @saved="shareSaved" />
    <DocumentRecordsDrawer v-model="recordsOpen" :document="activeDocument" />
    <DocumentUploadDialog
      v-model="uploadOpen"
      :folder-id="uploadFolderId"
      :folder-name="uploadFolderName"
      :max-upload-size="workspaceSummary.maxUploadSize"
      @uploaded="uploadCompleted"
    />
    <DocumentStorageDrawer v-model="storageDrawerOpen" @updated="loadFolders" />

    <el-dialog v-model="storageDetailOpen" class="storage-detail-dialog" title="存储空间详情" width="440px" append-to-body>
      <div class="storage-detail">
        <div class="storage-detail-heading">
          <span>已使用</span>
          <strong>{{ formatFileSize(workspaceSummary.usedSize) }}</strong>
          <small>共授权 {{ formatFileSize(workspaceSummary.quotaSize) }}</small>
        </div>
        <el-progress :percentage="storagePercentage" :stroke-width="8" :status="storageStatus" />
        <dl class="storage-detail-grid">
          <div><dt>剩余容量</dt><dd>{{ formatFileSize(workspaceSummary.remainingSize) }}</dd></div>
          <div><dt>单文件上传上限</dt><dd>{{ formatFileSize(workspaceSummary.maxUploadSize) }}</dd></div>
          <div><dt>当前文件</dt><dd>{{ workspaceSummary.fileCount }} 个</dd></div>
          <div><dt>当前文件大小</dt><dd>{{ formatStorageMegabytes(workspaceSummary.totalSize) }}</dd></div>
        </dl>
        <p class="storage-detail-note"><el-icon><InfoFilled /></el-icon>回收站中的文件仍计入已使用容量，彻底删除后才会释放空间。</p>
      </div>
      <template #footer>
        <el-button @click="storageDetailOpen = false">关闭</el-button>
        <el-button v-if="isDocumentAdmin" type="primary" plain @click="openStorageManagement">管理用户空间</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="folderDialog.open" :title="folderDialogTitle" width="440px" append-to-body destroy-on-close>
      <el-form label-position="top" @submit.prevent="submitFolderDialog">
        <el-form-item label="目录名称" required>
          <el-input
            v-model="folderDialog.folderName"
            maxlength="100"
            show-word-limit
            clearable
            autofocus
            placeholder="请输入目录名称"
            @keyup.enter="submitFolderDialog"
          />
        </el-form-item>
        <el-form-item label="目录颜色">
          <div class="folder-color-picker" role="radiogroup" aria-label="目录颜色">
            <button
              v-for="option in FOLDER_COLOR_OPTIONS"
              :key="option.value"
              type="button"
              role="radio"
              :aria-checked="folderDialog.folderColor === option.value"
              :class="{ 'is-active': folderDialog.folderColor === option.value }"
              @click="folderDialog.folderColor = option.value"
            >
              <span class="folder-color-swatch" :style="{ '--swatch-color': option.value }"><el-icon><Folder /></el-icon></span>
              <span>{{ option.label }}</span>
              <el-icon class="folder-color-check"><CircleCheckFilled /></el-icon>
            </button>
          </div>
          <p class="folder-color-note">颜色用于快速辨认目录，名称与层级关系保持不变。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialog.open = false">取消</el-button>
        <el-button type="primary" :loading="loading.mutation" @click="submitFolderDialog">{{ folderDialog.mode === 'create' ? '创建目录' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialog.open" title="选择目标目录" width="460px" append-to-body>
      <p class="move-dialog-note">根目录不能挂载文件，请选择一个具体目录。</p>
      <el-tree :data="folderTree" node-key="folderId" default-expand-all :expand-on-click-node="false" @node-click="(data) => moveDialog.folderId = data.folderId">
        <template #default="{ data }"><div class="move-node" :class="{ 'is-active': Number(moveDialog.folderId) === Number(data.folderId) }" :style="{ '--folder-color': normalizeFolderColor(data.folderColor) }"><el-icon><Folder /></el-icon>{{ data.folderName }}</div></template>
      </el-tree>
      <template #footer><el-button @click="moveDialog.open = false">取消</el-button><el-button type="primary" :loading="loading.mutation" :disabled="!moveDialog.folderId" @click="submitMove">移动到此处</el-button></template>
    </el-dialog>

    <transition name="undo-toast">
      <div v-if="undoState" class="undo-notice" role="status">
        <el-icon><CircleCheckFilled /></el-icon><span>{{ undoState.message }}</span><button type="button" @click="undoLastAction">撤销</button><button type="button" aria-label="关闭" @click="clearUndo"><el-icon><Close /></el-icon></button>
      </div>
    </transition>
  </div>
</template>

<script setup name="DocumentWorkspace">
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import DocumentActionMenu from '../components/DocumentActionMenu.vue'
import DocumentRecordsDrawer from '../components/DocumentRecordsDrawer.vue'
import DocumentShareDrawer from '../components/DocumentShareDrawer.vue'
import DocumentStorageDrawer from '../components/DocumentStorageDrawer.vue'
import DocumentUploadDialog from '../components/DocumentUploadDialog.vue'
import {
  copyDocument as copyDocumentRequest,
  createDocument as createDocumentRequest,
  createDocumentFolder,
  deleteDocumentFolder,
  getDocumentWorkspaceSummary,
  listDocumentFolders,
  listDocuments as listDocumentsRequest,
  reorderDocumentFolders,
  trashDocument,
  updateDocument,
  updateDocumentFolder
} from '@/api/document/workspace.js'
import {
  DOCUMENT_SCOPES,
  DEFAULT_FOLDER_COLOR,
  FILE_MANAGEMENT_PERMISSION,
  FOLDER_COLOR_OPTIONS,
  PERMISSION_META,
  buildFolderBreadcrumb,
  buildFolderTree,
  collaboratorNames,
  formatFileSize,
  formatStorageMegabytes,
  initials,
  isArchiveFile,
  isPdfFile,
  documentDropCapability,
  normalizeFolderColor,
  reorderSiblingFolderIds,
  resolveDocumentFolderPath
} from './documentWorkspaceRules.js'
import { checkPermi, checkRole } from '@/utils/permission.js'

const router = useRouter()
const { proxy } = getCurrentInstance()
const folders = ref([])
const documents = ref([])
const workspaceSummary = ref({
  fileCount: 0,
  totalSize: 0,
  unfiledCount: 0,
  usedSize: 0,
  quotaSize: 100 * 1024 ** 2,
  remainingSize: 100 * 1024 ** 2,
  maxUploadSize: 100 * 1024 ** 2,
  usagePercent: 0
})
const shareOpen = ref(false)
const recordsOpen = ref(false)
const uploadOpen = ref(false)
const storageDrawerOpen = ref(false)
const storageDetailOpen = ref(false)
const activeDocument = ref(null)
const draggedDocument = ref(null)
const draggedFolder = ref(null)
const dragPreviewRef = ref(null)
const dragHover = reactive({ kind: '', id: null, label: '', allowed: false, reason: '', action: '' })
const folderSortHover = reactive({ folderId: null, label: '', position: '', allowed: false, reason: '' })
const undoState = ref(null)
const selectedDocumentIds = ref([])
const viewMode = ref(window.localStorage.getItem('document-workspace-view') === 'grid' ? 'grid' : 'list')
const loading = reactive({ folders: false, documents: false, mutation: false, batchDownload: false, folderSort: false })
const query = reactive({ scope: 'MY', folderId: 0, keyword: '', fileType: '', accessPermission: '' })
const folderDialog = reactive({
  open: false,
  mode: 'create',
  folderId: null,
  parentId: 0,
  folderName: '',
  folderColor: DEFAULT_FOLDER_COLOR
})
const moveDialog = reactive({ open: false, document: null, folderId: null })
let searchTimer = null
let undoTimer = null
let loadSequence = 0

const folderTree = computed(() => buildFolderTree(folders.value))
const folderBreadcrumb = computed(() => buildFolderBreadcrumb(folders.value, query.folderId))
const folderDialogTitle = computed(() => folderDialog.mode === 'create' ? '新建目录' : '编辑目录')
const isDocumentAdmin = checkRole(['admin'])
const visibleDocumentScopes = computed(() => isDocumentAdmin
  ? [...DOCUMENT_SCOPES, { value: 'ADMIN_ALL', label: '全部用户文件', icon: 'Files' }]
  : DOCUMENT_SCOPES)
const currentScope = computed(() => visibleDocumentScopes.value.find((item) => item.value === query.scope) || DOCUMENT_SCOPES[0])
const showFolderNavigation = computed(() => ['MY', 'ARCHIVED'].includes(query.scope))
const rootFolderActive = computed(() => query.scope === 'MY' && Number(query.folderId) === 0)
const canCreateInCurrentFolder = computed(() => query.scope === 'MY' && Number(query.folderId) > 0)
const canDownloadDocuments = checkPermi([FILE_MANAGEMENT_PERMISSION])
const storagePercentage = computed(() => Math.max(0, Math.min(100, Number(workspaceSummary.value.usagePercent || 0))))
const storageStatus = computed(() => storagePercentage.value >= 95 ? 'exception' : (storagePercentage.value >= 80 ? 'warning' : undefined))
const selectedDocumentSet = computed(() => new Set(selectedDocumentIds.value.map(Number)))
const downloadableDocuments = computed(() => documents.value.filter((item) => item.lifecycleStatus !== 'TRASH'))
const allDownloadableSelected = computed(() => downloadableDocuments.value.length > 0
  && downloadableDocuments.value.every((item) => selectedDocumentSet.value.has(Number(item.documentId))))
const partlySelected = computed(() => selectedDocumentIds.value.length > 0 && !allDownloadableSelected.value)
const uploadFolderId = computed(() => canCreateInCurrentFolder.value ? Number(query.folderId) : 0)
const uploadFolderName = computed(() => {
  if (!uploadFolderId.value) return '请选择目录'
  return `我的文档 / ${folderBreadcrumb.value.map((item) => item.folderName).join(' / ')}`
})
const dragFeedback = computed(() => {
  if (dragHover.kind && dragHover.allowed) {
    const actionLabel = dragHover.action === 'ARCHIVE'
      ? `归档到“${dragHover.label}”`
      : (dragHover.action === 'RESTORE' ? `恢复到“${dragHover.label}”` : `移动到“${dragHover.label}”`)
    return { tone: 'is-ready', title: `松开即可${actionLabel}`, detail: '目标位置可以接收此文档' }
  }
  if (dragHover.kind) {
    const messages = {
      ROOT_BLOCKED: ['根目录不能放置文档', '请选择一个下级文件夹'],
      SAME_FOLDER: ['文档已在此目录', '请选择其他目录'],
      ALREADY_ARCHIVED: ['文档已经归档', '请选择一个具体文件夹进行恢复'],
      ALREADY_ACTIVE: ['文档已在“我的文档”', '请选择具体文件夹或“已归档”'],
      UNSUPPORTED_SCOPE: ['此工作区不能接收文档', '可拖到具体文件夹或“已归档”']
    }
    const [title, detail] = messages[dragHover.reason] || ['此位置不可放置', '请选择其他目标位置']
    return { tone: 'is-blocked', title, detail }
  }
  return { tone: 'is-guiding', title: '选择目标位置', detail: '拖到具体文件夹可移动，拖到“已归档”可归档' }
})
const folderSortFeedback = computed(() => {
  if (folderSortHover.folderId && folderSortHover.allowed) {
    const positionLabel = folderSortHover.position === 'BEFORE' ? '前面' : '后面'
    return {
      tone: 'is-ready',
      title: `放到“${folderSortHover.label}”${positionLabel}`,
      detail: '松开即可保存当前层级的新顺序'
    }
  }
  if (folderSortHover.folderId) {
    if (folderSortHover.reason === 'SELF') {
      return { tone: 'is-blocked', title: '当前位置没有变化', detail: '请拖到其他同级文件夹的上方或下方' }
    }
    return { tone: 'is-blocked', title: '不能跨层级排序', detail: '请拖到同一父目录下的其他文件夹' }
  }
  return { tone: 'is-guiding', title: '调整目录顺序', detail: '拖到同级文件夹的上半区或下半区' }
})
const contentTitle = computed(() => {
  if (!showFolderNavigation.value || query.folderId == null) return currentScope.value.label
  if (Number(query.folderId) === 0) return query.scope === 'ARCHIVED' ? '全部归档' : '全部文件'
  return folderBreadcrumb.value[folderBreadcrumb.value.length - 1]?.folderName || currentScope.value.label
})
const SHARED_PERMISSION_FILTERS = [
  { value: '', label: '全部' },
  { value: 'EDIT', label: '可编辑' },
  { value: 'VIEW', label: '仅查看' }
]
const scopeDescription = computed(() => ({
  MY: '你创建并拥有的全部文档',
  SHARED: '其他成员授权你查看或编辑的文档',
  RECENT: '最近打开和编辑过的文档',
  ARCHIVED: '已完成归档、仍可查看的文档',
  TRASH: '已移入回收站的文档',
  ADMIN_ALL: '拥有文档管理权限的全部用户文件；管理员仅做全局查看与下载'
})[query.scope])
const emptyDescription = computed(() => query.keyword
  ? '没有匹配当前搜索条件的文档'
  : (rootFolderActive.value
      ? '当前还没有文档，请先在具体目录中新建或上传'
      : ({ MY: '当前目录还没有文档', SHARED: '暂时没有共享给你的文档', RECENT: '还没有最近打开的文档', ARCHIVED: '还没有已归档文档', TRASH: '回收站为空', ADMIN_ALL: '暂无文档管理用户文件' })[query.scope]))

onMounted(refreshWorkspace)
onBeforeUnmount(() => {
  window.clearTimeout(searchTimer)
  window.clearTimeout(undoTimer)
})
watch(() => [query.scope, query.folderId, query.fileType, query.accessPermission], loadDocuments)

async function refreshWorkspace() {
  await Promise.all([loadFolders(), loadDocuments()])
}

async function loadFolders() {
  loading.folders = true
  try {
    const [folderResponse, summaryResponse] = await Promise.all([
      listDocumentFolders(),
      getDocumentWorkspaceSummary()
    ])
    folders.value = folderResponse.data || []
    workspaceSummary.value = {
      fileCount: Number(summaryResponse.data?.fileCount || 0),
      totalSize: Number(summaryResponse.data?.totalSize || 0),
      unfiledCount: Number(summaryResponse.data?.unfiledCount || 0),
      usedSize: Number(summaryResponse.data?.usedSize || 0),
      quotaSize: Number(summaryResponse.data?.quotaSize || 0),
      remainingSize: Number(summaryResponse.data?.remainingSize || 0),
      maxUploadSize: Number(summaryResponse.data?.maxUploadSize || 0),
      usagePercent: Number(summaryResponse.data?.usagePercent || 0)
    }
  } finally {
    loading.folders = false
  }
}

async function loadDocuments() {
  const sequence = ++loadSequence
  loading.documents = true
  try {
    const response = await listDocumentsRequest({ ...query })
    if (sequence === loadSequence) {
      documents.value = response.data || []
      const visibleIds = new Set(downloadableDocuments.value.map((item) => Number(item.documentId)))
      selectedDocumentIds.value = selectedDocumentIds.value.filter((id) => visibleIds.has(Number(id)))
    }
  } finally {
    if (sequence === loadSequence) loading.documents = false
  }
}

function scheduleSearch() {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(loadDocuments, 280)
}

function selectScope(scope) {
  selectedDocumentIds.value = []
  query.scope = scope
  query.folderId = scope === 'MY' ? 0 : null
  query.accessPermission = ''
}

function selectRootFolder() {
  selectedDocumentIds.value = []
  query.scope = 'MY'
  query.folderId = 0
}

function selectFolder(folder) {
  selectedDocumentIds.value = []
  if (query.scope !== 'ARCHIVED') query.scope = 'MY'
  query.folderId = folder.folderId
}

function documentFolderPath(document) {
  return resolveDocumentFolderPath(folders.value, document)
}

function setViewMode(mode) {
  viewMode.value = mode
  window.localStorage.setItem('document-workspace-view', mode)
}

async function createNewDocument(fileType) {
  if (!canCreateInCurrentFolder.value) {
    proxy.$modal.msgWarning('根目录不能挂载文件，请先新建并选择一个目录')
    return
  }
  const label = fileType === 'xlsx' ? 'Excel 表格' : 'Word 文档'
  const defaultTitle = fileType === 'xlsx' ? '未命名表格' : '未命名文档'
  try {
    const { value } = await ElMessageBox.prompt('请输入文档名称', `新建 ${label}`, {
      inputValue: defaultTitle,
      inputPattern: /\S+/,
      inputErrorMessage: '文档名称不能为空',
      confirmButtonText: '创建并打开',
      cancelButtonText: '取消'
    })
    const editorWindow = window.open('', '_blank')
    if (!editorWindow) {
      proxy.$modal.msgWarning('浏览器阻止了新窗口，请允许本站弹出窗口后重试')
      return
    }
    editorWindow.opener = null
    editorWindow.document.title = `正在创建${label}`
    editorWindow.document.body.textContent = '正在创建文档，请稍候…'
    loading.mutation = true
    try {
      const response = await createDocumentRequest({
        folderId: Number(query.folderId),
        title: value,
        fileType
      })
      editorWindow.location.replace(editorHref(response.data))
      await refreshWorkspace()
    } catch (error) {
      editorWindow.close()
      throw error
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  } finally {
    loading.mutation = false
  }
}

function openUploadDialog() {
  if (!canCreateInCurrentFolder.value) {
    proxy.$modal.msgWarning('根目录不能挂载文件，请先新建并选择一个目录')
    return
  }
  uploadOpen.value = true
}

function openStorageManagement() {
  storageDetailOpen.value = false
  storageDrawerOpen.value = true
}

function toggleDocumentSelection(documentId, checked) {
  const normalizedId = Number(documentId)
  const selected = new Set(selectedDocumentIds.value.map(Number))
  if (checked) selected.add(normalizedId)
  else selected.delete(normalizedId)
  selectedDocumentIds.value = [...selected]
}

function toggleSelectAll(checked) {
  selectedDocumentIds.value = checked
    ? downloadableDocuments.value.map((item) => Number(item.documentId))
    : []
}

async function downloadSelectedDocuments() {
  if (!selectedDocumentIds.value.length || loading.batchDownload) return
  loading.batchDownload = true
  try {
    const timestamp = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
    await proxy.download(
      '/document/workspace/documents/batch-download',
      { documentIds: selectedDocumentIds.value.join(',') },
      `文档批量下载_${timestamp}.zip`,
      { timeout: 0 }
    )
  } finally {
    loading.batchDownload = false
  }
}

async function uploadCompleted(result) {
  query.scope = 'MY'
  query.folderId = Number(result.folderId || 0)
  query.keyword = ''
  query.fileType = ''
  query.accessPermission = ''
  await Promise.all([loadFolders(), loadDocuments()])
  const warnings = Array.isArray(result.warnings) ? result.warnings.filter(Boolean) : []
  if (warnings.length) {
    await ElMessageBox.alert(warnings.map((item) => `• ${item}`).join('\n'), '上传成功，请留意', {
      confirmButtonText: '知道了',
      type: 'warning'
    })
  } else {
    proxy.$modal.msgSuccess('文件校验通过并已上传')
  }
}

function createFolder(parentId) {
  const normalizedParentId = Number(parentId || 0)
  const parent = folders.value.find((folder) => Number(folder.folderId) === normalizedParentId)
  Object.assign(folderDialog, {
    open: true,
    mode: 'create',
    folderId: null,
    parentId: normalizedParentId,
    folderName: '',
    folderColor: normalizeFolderColor(parent?.folderColor)
  })
}

function editFolder(folder) {
  Object.assign(folderDialog, {
    open: true,
    mode: 'edit',
    folderId: Number(folder.folderId),
    parentId: Number(folder.parentId || 0),
    folderName: folder.folderName || '',
    folderColor: normalizeFolderColor(folder.folderColor)
  })
}

async function submitFolderDialog() {
  if (loading.mutation) return
  const folderName = String(folderDialog.folderName || '').trim()
  if (!folderName) {
    proxy.$modal.msgWarning('目录名称不能为空')
    return
  }
  if (/[\\/]/.test(folderName)) {
    proxy.$modal.msgWarning('目录名称不能包含路径符号')
    return
  }
  loading.mutation = true
  try {
    const payload = {
      parentId: Number(folderDialog.parentId || 0),
      folderName,
      folderColor: normalizeFolderColor(folderDialog.folderColor)
    }
    if (folderDialog.mode === 'create') {
      const response = await createDocumentFolder(payload)
      await loadFolders()
      if (response.data?.folderId) {
        query.scope = 'MY'
        query.folderId = Number(response.data.folderId)
      }
      proxy.$modal.msgSuccess('目录已创建')
    } else {
      await updateDocumentFolder(folderDialog.folderId, payload)
      await loadFolders()
      proxy.$modal.msgSuccess('目录设置已保存')
    }
    folderDialog.open = false
  } finally {
    loading.mutation = false
  }
}

async function folderCommand(command, folder) {
  if (command === 'child') return createFolder(folder.folderId)
  if (command === 'edit') return editFolder(folder)
  await proxy.$modal.confirm(`确认删除目录“${folder.folderName}”吗？目录必须为空。`)
  await deleteDocumentFolder(folder.folderId)
  if (Number(query.folderId) === Number(folder.folderId)) query.folderId = query.scope === 'MY' ? 0 : null
  await loadFolders()
  proxy.$modal.msgSuccess('目录已删除')
}

function openDocument(document) {
  if (!document?.documentId || document.lifecycleStatus === 'TRASH') {
    proxy.$modal.msgWarning('请先将文件恢复后再打开')
    return
  }
  if (isArchiveFile(document.fileType)) {
    downloadDocument(document)
    return
  }
  const documentWindow = window.open(isPdfFile(document.fileType) ? previewHref(document) : editorHref(document), '_blank')
  if (!documentWindow) {
    proxy.$modal.msgWarning('浏览器阻止了新窗口，请允许本站弹出窗口后重试')
    return
  }
  documentWindow.opener = null
}

function editorHref(document) {
  if (!document?.documentId) return '#'
  return router.resolve({ name: 'DocumentEditor', params: { documentId: document.documentId } }).href
}

function previewHref(document) {
  if (!document?.documentId) return '#'
  return router.resolve({ name: 'DocumentPreview', params: { documentId: document.documentId } }).href
}

function documentHref(document) {
  if (isArchiveFile(document?.fileType)) return '#'
  return isPdfFile(document?.fileType) ? previewHref(document) : editorHref(document)
}

function guardDocumentOpen(event, document) {
  if (document?.lifecycleStatus === 'TRASH') {
    event.preventDefault()
    proxy.$modal.msgWarning('请先将文件恢复后再打开')
    return
  }
  if (isArchiveFile(document?.fileType)) {
    event.preventDefault()
    downloadDocument(document)
  }
}

function permissionMeta(document) {
  if (isArchiveFile(document?.fileType) && document?.accessPermission === 'VIEW') {
    return { label: '可下载', type: 'info' }
  }
  return PERMISSION_META[document.accessPermission] || PERMISSION_META.VIEW
}

function fileMarkLabel(fileType) {
  const normalized = String(fileType || '').toLowerCase()
  if (isArchiveFile(normalized)) return normalized.slice(0, 1).toUpperCase()
  if (isPdfFile(normalized)) return 'P'
  return ['xls', 'xlsx'].includes(normalized) ? 'X' : 'W'
}

function recordLabel(document) {
  return `${isArchiveFile(document?.fileType) || isPdfFile(document?.fileType) ? '记录' : '版本'} ${document?.contentVersion || 1}`
}

function downloadDocument(document) {
  proxy.download(`/document/workspace/documents/${document.documentId}/download`, {}, document.title)
}

function openShare(document) {
  activeDocument.value = document
  shareOpen.value = true
}

async function documentCommand(command, document) {
  if (command === 'open') return openDocument(document)
  if (command === 'share') return openShare(document)
  if (command === 'download') {
    downloadDocument(document)
    return
  }
  if (command === 'copy') return copyCurrentDocument(document)
  if (command === 'rename') return renameDocument(document)
  if (command === 'move') {
    moveDialog.document = document
    moveDialog.folderId = Number(document.folderId) > 0
      ? Number(document.folderId)
      : (folderTree.value[0]?.folderId || null)
    moveDialog.open = true
    return
  }
  if (command === 'records') return openRecords(document)
  if (command === 'archive') return updateLifecycle(document, 'ARCHIVED', '文档已归档')
  if (command === 'restore') return updateLifecycle(document, 'ACTIVE', '文档已恢复')
  if (command === 'trash') {
    await proxy.$modal.confirm(`确认将“${document.title}”移入回收站吗？`)
    await trashDocument(document.documentId)
    await refreshWorkspace()
    proxy.$modal.msgSuccess('已移入回收站')
  }
}

async function copyCurrentDocument(document) {
  if (loading.mutation) return
  loading.mutation = true
  try {
    const response = await copyDocumentRequest(document.documentId)
    const copy = response.data || {}
    query.scope = 'MY'
    query.folderId = Number(copy.folderId)
    query.keyword = ''
    query.fileType = ''
    query.accessPermission = ''
    await refreshWorkspace()
    proxy.$modal.msgSuccess(`已复制为“${copy.title || '新副本'}”`)
  } finally {
    loading.mutation = false
  }
}

async function renameDocument(document) {
  try {
    const { value } = await ElMessageBox.prompt('请输入新的文档名称', '重命名文档', {
      inputValue: document.title,
      inputPattern: /\S+/,
      inputErrorMessage: '文档名称不能为空'
    })
    await updateDocument(document.documentId, { title: value })
    await loadDocuments()
    proxy.$modal.msgSuccess('文档已重命名')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  }
}

async function submitMove() {
  if (!Number(moveDialog.folderId)) {
    proxy.$modal.msgWarning('请选择一个具体目录')
    return
  }
  loading.mutation = true
  try {
    const folder = folders.value.find((item) => Number(item.folderId) === Number(moveDialog.folderId))
    await moveDocument(moveDialog.document, Number(moveDialog.folderId), folder?.folderName || '所选目录')
    moveDialog.open = false
  } finally {
    loading.mutation = false
  }
}

async function moveDocument(document, folderId, folderName) {
  const previousFolderId = Number(document.folderId || 0)
  if (previousFolderId === Number(folderId)) return
  await updateDocument(document.documentId, { folderId })
  await refreshWorkspace()
  if (previousFolderId > 0) {
    showUndo({ message: `已移动到 ${folderName}`, run: () => updateDocument(document.documentId, { folderId: previousFolderId }) })
  }
}

async function updateLifecycle(document, lifecycleStatus, message) {
  const previousStatus = document.lifecycleStatus
  await updateDocument(document.documentId, { lifecycleStatus })
  await refreshWorkspace()
  showUndo({ message, run: () => updateDocument(document.documentId, { lifecycleStatus: previousStatus }) })
}

function clearDragTarget() {
  Object.assign(dragHover, { kind: '', id: null, label: '', allowed: false, reason: '', action: '' })
}

function clearFolderSortTarget() {
  Object.assign(folderSortHover, { folderId: null, label: '', position: '', allowed: false, reason: '' })
}

function startFolderDrag(event, folder) {
  if (loading.folderSort || !folder?.folderId) {
    event?.preventDefault()
    return
  }
  draggedFolder.value = folder
  clearFolderSortTarget()
  const transfer = event?.dataTransfer
  if (!transfer) return
  transfer.effectAllowed = 'move'
  transfer.setData('application/x-rynew-folder', String(folder.folderId))
  transfer.setData('text/plain', `folder:${folder.folderId}`)
}

function endFolderDrag() {
  draggedFolder.value = null
  clearFolderSortTarget()
}

function updateFolderSortTarget(event, target) {
  const dragged = draggedFolder.value
  if (!dragged || !target) return
  const sameFolder = Number(dragged.folderId) === Number(target.folderId)
  const sameParent = Number(dragged.parentId || 0) === Number(target.parentId || 0)
  const bounds = event?.currentTarget?.getBoundingClientRect?.()
  const position = bounds && Number.isFinite(event?.clientY)
    && event.clientY >= bounds.top + bounds.height / 2 ? 'AFTER' : 'BEFORE'
  const allowed = !sameFolder && sameParent
  if (event?.dataTransfer) event.dataTransfer.dropEffect = allowed ? 'move' : 'none'
  Object.assign(folderSortHover, {
    folderId: Number(target.folderId),
    label: target.folderName || '目录',
    position,
    allowed,
    reason: sameFolder ? 'SELF' : (sameParent ? '' : 'CROSS_PARENT')
  })
}

function folderSortClass(folder) {
  if (!draggedFolder.value) return {}
  const hovering = Number(folderSortHover.folderId) === Number(folder.folderId)
  return {
    'is-folder-dragging': Number(draggedFolder.value.folderId) === Number(folder.folderId),
    'is-sort-before': hovering && folderSortHover.allowed && folderSortHover.position === 'BEFORE',
    'is-sort-after': hovering && folderSortHover.allowed && folderSortHover.position === 'AFTER',
    'is-sort-blocked': hovering && !folderSortHover.allowed
  }
}

function enterFolderNodeDrag(event, folder) {
  if (draggedFolder.value) return updateFolderSortTarget(event, folder)
  enterDragTarget(event, 'FOLDER', folder.folderId, folder.folderName)
}

function leaveFolderNodeDrag(event, folder) {
  if (draggedFolder.value) {
    if (event?.relatedTarget && event.currentTarget?.contains(event.relatedTarget)) return
    if (Number(folderSortHover.folderId) === Number(folder.folderId)) clearFolderSortTarget()
    return
  }
  leaveDragTarget(event, 'FOLDER', folder.folderId)
}

function sortedSiblingFolderIds(parentId) {
  return folders.value
    .filter((folder) => Number(folder.parentId || 0) === Number(parentId || 0))
    .sort((left, right) => Number(left.sortOrder || 0) - Number(right.sortOrder || 0)
      || String(left.folderName || '').localeCompare(String(right.folderName || ''), 'zh-CN'))
    .map((folder) => Number(folder.folderId))
}

async function dropOnFolderNode(event, folder) {
  if (!draggedFolder.value) return dropOnFolder(folder)
  updateFolderSortTarget(event, folder)
  const dragged = draggedFolder.value
  const parentId = Number(dragged.parentId || 0)
  const folderIds = folderSortHover.allowed
    ? reorderSiblingFolderIds(folders.value, dragged.folderId, folder.folderId, folderSortHover.position)
    : []
  const blockedReason = folderSortHover.reason
  if (!folderIds.length) {
    endFolderDrag()
    proxy.$modal.msgWarning(blockedReason === 'SELF' ? '请选择其他同级目录作为排序位置' : '目录只能在同一层级内调整顺序')
    return
  }

  const currentIds = sortedSiblingFolderIds(parentId)
  endFolderDrag()
  if (folderIds.every((folderId, index) => folderId === currentIds[index])) return

  const snapshot = folders.value.map((item) => ({ ...item }))
  const orderById = new Map(folderIds.map((folderId, index) => [folderId, (index + 1) * 10]))
  folders.value = folders.value.map((item) => orderById.has(Number(item.folderId))
    ? { ...item, sortOrder: orderById.get(Number(item.folderId)) }
    : item)
  loading.folderSort = true
  try {
    await reorderDocumentFolders({ parentId, folderIds })
    await loadFolders()
    proxy.$modal.msgSuccess('目录顺序已更新')
  } catch (error) {
    folders.value = snapshot
    try {
      await loadFolders()
    } catch (_) {
      // 保留本次操作前的本地顺序，等待用户手动刷新。
    }
    proxy.$modal.msgError('目录排序未保存，请刷新后重试')
  } finally {
    loading.folderSort = false
  }
}

function startDocumentDrag(event, document) {
  if (document?.accessPermission !== 'OWNER') {
    event?.preventDefault()
    return
  }
  draggedDocument.value = document
  clearDragTarget()
  const transfer = event?.dataTransfer
  if (!transfer) return
  transfer.effectAllowed = 'move'
  transfer.setData('text/plain', String(document.documentId))
  const preview = dragPreviewRef.value
  if (preview) {
    const title = preview.querySelector('span')
    if (title) title.textContent = document.title || '文档'
    transfer.setDragImage(preview, 18, 18)
  }
}

function endDocumentDrag() {
  draggedDocument.value = null
  clearDragTarget()
}

function targetCapability(kind, id) {
  return documentDropCapability(draggedDocument.value, { kind, id })
}

function dropTargetClass(kind, id) {
  if (!draggedDocument.value) return {}
  const capability = targetCapability(kind, id)
  const hovering = dragHover.kind === kind && String(dragHover.id) === String(id)
  return {
    'is-drop-available': capability.allowed,
    'is-drop-disabled': !capability.allowed,
    'is-drop-ready': hovering && capability.allowed,
    'is-drop-rejected': hovering && !capability.allowed
  }
}

function enterDragTarget(event, kind, id, label) {
  if (!draggedDocument.value) return
  const capability = targetCapability(kind, id)
  if (event?.dataTransfer) event.dataTransfer.dropEffect = capability.allowed ? 'move' : 'none'
  Object.assign(dragHover, { kind, id, label, ...capability })
}

function leaveDragTarget(event, kind, id) {
  if (event?.relatedTarget && event.currentTarget?.contains(event.relatedTarget)) return
  if (dragHover.kind === kind && String(dragHover.id) === String(id)) clearDragTarget()
}

function warnRejectedDrop(reason) {
  const messages = {
    ROOT_BLOCKED: '根目录不能挂载文件，请拖到一个具体目录',
    SAME_FOLDER: '文档已在当前目录，请选择其他目录',
    ALREADY_ARCHIVED: '文档已经归档',
    ALREADY_ACTIVE: '文档已在“我的文档”',
    UNSUPPORTED_SCOPE: '此工作区不支持接收拖入的文档'
  }
  proxy.$modal.msgWarning(messages[reason] || '当前目标位置不可用')
}

async function dropOnScope(scope) {
  const document = draggedDocument.value
  if (!document) return
  const capability = documentDropCapability(document, { kind: 'SCOPE', id: scope })
  endDocumentDrag()
  if (!capability.allowed) return warnRejectedDrop(capability.reason)
  if (scope === 'ARCHIVED') await updateLifecycle(document, 'ARCHIVED', '文档已归档')
  if (scope === 'MY') {
    if (document.lifecycleStatus !== 'ACTIVE') await updateLifecycle(document, 'ACTIVE', '文档已恢复')
  }
}

async function dropOnFolder(folder) {
  const document = draggedDocument.value
  if (!document) return
  const capability = documentDropCapability(document, { kind: 'FOLDER', id: folder.folderId })
  endDocumentDrag()
  if (!capability.allowed) return warnRejectedDrop(capability.reason)
  const previousFolderId = Number(document.folderId || 0)
  const nextFolderId = Number(folder.folderId)
  if (!nextFolderId) return
  const previousStatus = document.lifecycleStatus
  if (previousFolderId === nextFolderId && previousStatus === 'ACTIVE') return
  await updateDocument(document.documentId, { folderId: nextFolderId, lifecycleStatus: 'ACTIVE' })
  await refreshWorkspace()
  if (previousFolderId > 0) {
    showUndo({
      message: `已移动到 ${folder.folderName}`,
      run: () => updateDocument(document.documentId, { folderId: previousFolderId, lifecycleStatus: previousStatus })
    })
  }
}

function dropOnRootFolder() {
  const document = draggedDocument.value
  if (!document) return
  const capability = documentDropCapability(document, { kind: 'FOLDER', id: 0 })
  endDocumentDrag()
  warnRejectedDrop(capability.reason)
}

async function openRecords(document) {
  activeDocument.value = document
  recordsOpen.value = true
}

async function shareSaved() {
  proxy.$modal.msgSuccess('协作权限已保存')
  await loadDocuments()
}

function showUndo(state) {
  undoState.value = state
  window.clearTimeout(undoTimer)
  undoTimer = window.setTimeout(clearUndo, 8000)
}

function clearUndo() {
  undoState.value = null
  window.clearTimeout(undoTimer)
}

async function undoLastAction() {
  const action = undoState.value
  clearUndo()
  if (!action) return
  await action.run()
  await refreshWorkspace()
  proxy.$modal.msgSuccess('操作已撤销')
}
</script>

<style scoped lang="scss" src="./DocumentWorkspace.scss"></style>
