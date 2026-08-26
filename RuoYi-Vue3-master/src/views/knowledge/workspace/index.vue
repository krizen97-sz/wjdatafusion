<template>
  <div class="knowledge-workspace-page">
    <div class="knowledge-shell">
      <aside class="knowledge-sidebar">
        <div class="space-row">
          <el-select v-model="currentSpaceId" :disabled="mode !== 'read'" placeholder="选择知识空间" @change="handleSpaceChange">
            <el-option v-for="space in spaces" :key="space.spaceId" :label="space.spaceName" :value="Number(space.spaceId)" />
          </el-select>
          <el-dropdown v-if="canManageSpace" :disabled="mode !== 'read'" trigger="click" @command="handleSpaceCommand">
            <el-button text circle aria-label="知识空间设置"><el-icon><Setting /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="create">新建知识空间</el-dropdown-item>
                <el-dropdown-item command="edit" :disabled="!currentSpace">编辑当前空间</el-dropdown-item>
                <el-dropdown-item command="folder" divided :disabled="!currentSpace">新建根目录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <el-button v-if="canWrite && mode === 'read'" class="sidebar-new-page" text icon="Plus" @click="startCreatePage">新建知识</el-button>

        <div class="knowledge-search">
          <el-input
            v-model="searchKeyword"
            clearable
            prefix-icon="Search"
            placeholder="搜索本空间"
            @input="scheduleSearch"
            @keyup.enter="runSearch"
          />
          <div v-if="searchPanelVisible" class="knowledge-search-results" v-loading="loading.search">
            <header><span>搜索结果</span><b>{{ searchResults.length }}</b></header>
            <button v-for="item in searchResults" :key="item.pageId" type="button" @click="selectSearchResult(item)">
              <el-icon><Document /></el-icon>
              <span><strong>{{ item.title }}</strong><small>{{ item.tagNames || currentSpace?.spaceName }} · V{{ item.contentVersion }}</small></span>
            </button>
            <el-empty v-if="!searchResults.length && !loading.search" description="没有匹配的知识" :image-size="56" />
          </div>
        </div>

        <el-radio-group v-model="currentScope" :disabled="mode !== 'read'" class="scope-switch" size="small" @change="handleScopeChange">
          <el-radio-button v-for="scope in visibleScopes" :key="scope.value" :value="scope.value">{{ scope.label }}</el-radio-button>
        </el-radio-group>

        <div class="knowledge-tree-wrap" v-loading="loading.tree">
          <el-tree
            ref="treeRef"
            :data="treeData"
            node-key="pageId"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            :props="{ label: 'title', children: 'children' }"
            @node-click="handleTreeNodeClick"
          >
            <template #default="{ data }">
              <div class="knowledge-tree-node" :class="{ 'is-folder': data.pageType === 'FOLDER' }">
                <el-icon><FolderOpened v-if="data.pageType === 'FOLDER'" /><Document v-else /></el-icon>
                <span>{{ data.title }}</span>
                <small v-if="data.pageType === 'ARTICLE'">V{{ data.contentVersion }}</small>
                <el-dropdown
                  v-if="canManageSpace && data.pageType === 'FOLDER'"
                  trigger="click"
                  @click.stop
                  @command="(command) => handleFolderCommand(command, data)"
                >
                  <el-button text circle size="small" aria-label="目录操作" @click.stop><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="child">新建子目录</el-dropdown-item>
                      <el-dropdown-item command="edit">编辑目录</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>删除空目录</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-tree>
          <el-empty v-if="!treeData.length && !loading.tree" description="当前空间还没有知识" :image-size="72">
            <el-button v-if="canWrite" type="primary" @click="startCreatePage">新建第一篇知识</el-button>
          </el-empty>
        </div>
        <footer><el-icon><CircleCheckFilled /></el-icon><span>本地数据 · {{ articleCount }} 篇知识</span></footer>
      </aside>

      <template v-if="mode === 'read'">
        <main class="knowledge-reader" v-loading="loading.detail">
          <el-empty v-if="!activePage && !loading.detail" description="请选择一篇知识开始阅读" />
          <div v-else-if="activePage" class="knowledge-reader-scroll">
            <header class="knowledge-article-header">
              <div class="article-title-block">
                <div><h1>{{ activePage.title }}</h1><el-tag size="small" effect="plain">V{{ activePage.contentVersion }}</el-tag></div>
                <p><span>当前版本 V{{ activePage.contentVersion }} · {{ activePage.modifierName || activePage.updateBy }}修改于 {{ activePage.updateTime }}</span><span>创建人 {{ activePage.creatorName || activePage.createBy }}</span></p>
              </div>
              <div class="article-actions">
                <el-button icon="Clock" @click="historyOpen = true">修改记录</el-button>
                <el-button v-if="canWrite && activePage.lifecycleStatus !== 'TRASH'" type="primary" icon="Edit" @click="startEditPage">编辑知识</el-button>
                <el-dropdown trigger="click" @command="handlePageCommand">
                  <el-button circle aria-label="更多知识操作"><el-icon><MoreFilled /></el-icon></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canWrite && activePage.lifecycleStatus === 'ACTIVE'" command="archive">归档知识</el-dropdown-item>
                      <el-dropdown-item v-if="canRemove && activePage.lifecycleStatus !== 'TRASH'" command="trash" divided>移入回收站</el-dropdown-item>
                      <el-dropdown-item v-if="canRemove && activePage.lifecycleStatus === 'ARCHIVED'" command="restore">恢复为当前知识</el-dropdown-item>
                      <el-dropdown-item v-if="canRemove && activePage.lifecycleStatus === 'TRASH'" command="restore">恢复知识</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </header>

            <div class="knowledge-tag-row"><el-tag v-for="tag in activeDetail.tags" :key="tag" size="small">{{ tag }}</el-tag></div>
            <p v-if="activePage.summary" class="knowledge-summary"><el-icon><InfoFilled /></el-icon><span>{{ activePage.summary }}</span></p>
            <article ref="articleBodyRef" class="knowledge-content" v-html="activePage.content" />

            <div class="knowledge-footer-grid" :class="{ 'without-documents': !activeDetail.documents.length }">
              <section v-if="activeDetail.documents.length" class="linked-documents">
                <h3>附件（{{ activeDetail.documents.length }}）<small>联动文档管理</small></h3>
                <button
                  v-for="document in activeDetail.documents"
                  :key="document.documentId"
                  type="button"
                  :disabled="resolveKnowledgeDocumentAction(document) === 'NONE'"
                  @click="openLinkedDocument(document)"
                >
                  <span class="linked-file-mark" :class="`is-${String(document.fileType || '').toLowerCase()}`">{{ knowledgeFileMark(document.fileType) }}</span>
                  <span><strong>{{ document.title }}</strong><small v-if="documentAccessMessage(document)">{{ documentAccessMessage(document) }}</small><small v-else>文档版本 {{ document.contentVersion || 1 }} · {{ permissionLabel(document.accessPermission) }}</small></span>
                  <em v-if="document.accessStatus === 'ARCHIVED'">已归档</em>
                  <el-icon><Download v-if="resolveKnowledgeDocumentAction(document) === 'DOWNLOAD'" /><TopRight v-else /></el-icon>
                </button>
              </section>
              <nav class="article-pager" aria-label="上一篇与下一篇">
                <button type="button" :disabled="!previousArticle" @click="selectPage(previousArticle?.pageId)"><el-icon><ArrowLeft /></el-icon><span><small>上一篇</small><strong>{{ previousArticle?.title || '没有上一篇' }}</strong></span></button>
                <button type="button" :disabled="!nextArticle" @click="selectPage(nextArticle?.pageId)"><span><small>下一篇</small><strong>{{ nextArticle?.title || '没有下一篇' }}</strong></span><el-icon><ArrowRight /></el-icon></button>
              </nav>
            </div>
          </div>
        </main>

        <aside class="knowledge-context">
          <section><h3>本文目录</h3><nav class="article-outline"><button v-for="item in outline" :key="item.id" type="button" :style="{ paddingLeft: `${8 + (item.level - 1) * 10}px` }" @click="scrollToHeading(item.id)">{{ item.text }}</button><p v-if="!outline.length">暂无标题目录</p></nav></section>
          <section><h3>关联知识</h3><div class="related-knowledge"><button v-for="item in relatedArticles" :key="item.pageId" type="button" @click="selectPage(item.pageId)"><el-icon><Document /></el-icon><span>{{ item.title }}</span></button><p v-if="!relatedArticles.length">暂无关联知识</p></div></section>
          <section><h3>修改记录</h3><div class="recent-versions"><button v-for="version in recentVersions" :key="version.versionNo" type="button" @click="historyOpen = true"><span>V{{ version.versionNo }}</span><strong>{{ version.operatorName || '-' }}</strong><time>{{ version.createTime }}</time></button><button v-if="activePage" class="view-all-versions" type="button" @click="historyOpen = true">查看全部 {{ recentVersions.length ? activePage.contentVersion : 0 }} 个版本</button></div></section>
        </aside>
      </template>

      <section v-else class="knowledge-editor" v-loading="loading.save">
        <header>
          <div><el-button text icon="ArrowLeft" @click="cancelEdit">返回阅读</el-button><h2>{{ mode === 'create' ? '新建知识' : '编辑知识' }}</h2><p>{{ mode === 'create' ? '首次保存后立即对有查看权限的用户可见' : `基于当前版本 V${editorForm.expectedVersion} 编辑，每次保存生成一个新版本` }}</p></div>
          <div><el-button @click="cancelEdit">取消</el-button><el-button type="primary" icon="Finished" :disabled="!editorForm.title.trim() || !editorForm.content.trim()" @click="savePage">保存新版本</el-button></div>
        </header>
        <div class="editor-layout">
          <main>
            <el-form label-position="top">
              <el-form-item label="知识标题" required><el-input v-model="editorForm.title" maxlength="160" show-word-limit /></el-form-item>
              <el-form-item label="摘要"><el-input v-model="editorForm.summary" maxlength="500" show-word-limit placeholder="用一句话说明这篇知识解决什么问题" /></el-form-item>
              <el-form-item label="正文" required><editor v-model="editorForm.content" :min-height="430" /></el-form-item>
            </el-form>
          </main>
          <aside>
            <h3>知识设置</h3>
            <el-form label-position="top">
              <el-form-item label="所属空间"><el-select v-model="editorForm.spaceId" disabled><el-option v-for="space in spaces" :key="space.spaceId" :label="space.spaceName" :value="Number(space.spaceId)" /></el-select></el-form-item>
              <el-form-item label="所属目录"><el-tree-select v-model="editorForm.parentId" :data="folderSelectTree" node-key="pageId" :props="{ label: 'title', children: 'children' }" check-strictly clearable placeholder="根目录" /></el-form-item>
              <el-form-item label="标签"><el-select v-model="editorForm.tagNames" multiple filterable allow-create default-first-option :multiple-limit="8" placeholder="输入后回车添加标签" /></el-form-item>
              <el-form-item label="修改说明"><el-input v-model="editorForm.changeNote" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可选：说明本次调整内容" /></el-form-item>
            </el-form>
            <section class="editor-document-links">
              <header><h4>关联文档</h4><el-button v-if="canLinkDocuments" link type="primary" @click="documentSelectorOpen = true">选择现有文档</el-button></header>
              <p>只保存文档ID，打开和下载继续使用文档管理权限。</p>
              <div v-for="document in editorDocuments" :key="document.documentId">
                <span class="linked-file-mark">{{ knowledgeFileMark(document.fileType) }}</span><strong>{{ document.title }}</strong><el-button v-if="canLinkDocuments" text circle icon="Close" aria-label="移除关联文档" @click="removeEditorDocument(document.documentId)" />
              </div>
              <el-empty v-if="!editorDocuments.length" description="暂无关联文档" :image-size="52" />
              <el-alert v-if="!canLinkDocuments && editorDocuments.length" title="当前账号无文档管理权限；可以修改正文并原样保留已有附件，但不能调整附件关系。" type="info" :closable="false" show-icon />
            </section>
          </aside>
        </div>
      </section>
    </div>

    <el-dialog v-model="spaceDialog.open" :title="spaceDialog.mode === 'create' ? '新建知识空间' : '编辑知识空间'" width="480px" append-to-body>
      <el-form label-position="top"><el-form-item label="空间名称" required><el-input v-model="spaceDialog.form.spaceName" maxlength="100" /></el-form-item><el-form-item label="空间说明"><el-input v-model="spaceDialog.form.description" type="textarea" :rows="3" maxlength="500" /></el-form-item></el-form>
      <template #footer><el-button @click="spaceDialog.open = false">取消</el-button><el-button type="primary" @click="saveSpace">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="folderDialog.open" :title="folderDialog.mode === 'create' ? '新建知识目录' : '编辑知识目录'" width="480px" append-to-body>
      <el-form label-position="top"><el-form-item label="目录名称" required><el-input v-model="folderDialog.form.title" maxlength="100" /></el-form-item><el-form-item label="上级目录"><el-tree-select v-model="folderDialog.form.parentId" :data="folderSelectTree" node-key="pageId" :props="{ label: 'title', children: 'children' }" check-strictly clearable placeholder="根目录" /></el-form-item></el-form>
      <template #footer><el-button @click="folderDialog.open = false">取消</el-button><el-button type="primary" @click="saveFolder">保存</el-button></template>
    </el-dialog>

    <KnowledgeDocumentSelector v-model="documentSelectorOpen" :selected-documents="editorDocuments" @confirm="applySelectedDocuments" />
    <KnowledgeHistoryDrawer v-model="historyOpen" :page="activePage || {}" :current-content="activePage?.content || ''" :can-write="canWrite" @restored="handleVersionRestored" />
  </div>
</template>

<script setup name="KnowledgeWorkspace">
import { onBeforeRouteLeave } from 'vue-router'
import {
  archiveKnowledgePage,
  createKnowledgeFolder,
  createKnowledgePage,
  createKnowledgeSpace,
  getKnowledgePage,
  listKnowledgeSpaces,
  listKnowledgeTree,
  listKnowledgeVersions,
  removeKnowledgeFolder,
  restoreKnowledgePage,
  searchKnowledgePages,
  trashKnowledgePage,
  updateKnowledgeFolder,
  updateKnowledgePage,
  updateKnowledgeSpace
} from '@/api/knowledge/index.js'
import KnowledgeDocumentSelector from '@/views/knowledge/components/KnowledgeDocumentSelector.vue'
import KnowledgeHistoryDrawer from '@/views/knowledge/components/KnowledgeHistoryDrawer.vue'
import {
  KNOWLEDGE_PERMISSIONS,
  KNOWLEDGE_SCOPES,
  buildKnowledgeTree,
  documentAccessMessage,
  knowledgeFileMark,
  normalizeKnowledgeTags,
  outlineFromHtml,
  resolveKnowledgeDocumentAction
} from './knowledgeWorkspaceRules.js'

const { proxy } = getCurrentInstance()
const router = useRouter()
const spaces = ref([])
const currentSpaceId = ref(null)
const currentScope = ref('ACTIVE')
const rawTree = ref([])
const treeData = ref([])
const treeRef = ref()
const activePageId = ref(null)
const activeDetail = reactive({ page: null, tags: [], documents: [] })
const articleBodyRef = ref()
const outline = ref([])
const recentVersions = ref([])
const searchKeyword = ref('')
const searchResults = ref([])
const searchPanelVisible = ref(false)
const searchTimer = ref(null)
const mode = ref('read')
const historyOpen = ref(false)
const documentSelectorOpen = ref(false)
const loading = reactive({ spaces: false, tree: false, detail: false, search: false, save: false })

const editorForm = reactive({ spaceId: null, parentId: null, title: '', summary: '', content: '', tagNames: [], documentIds: [], expectedVersion: null, changeNote: '' })
const editorDocuments = ref([])
const editorBaseline = ref('')

const spaceDialog = reactive({ open: false, mode: 'create', form: { spaceId: null, spaceName: '', description: '', sortOrder: null } })
const folderDialog = reactive({ open: false, mode: 'create', form: { pageId: null, spaceId: null, parentId: null, title: '', sortOrder: null } })

const currentSpace = computed(() => spaces.value.find((space) => Number(space.spaceId) === Number(currentSpaceId.value)))
const activePage = computed(() => activeDetail.page)
const canWrite = computed(() => Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.WRITE])))
const canManageSpace = computed(() => Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.SPACE_MANAGE])))
const canRemove = computed(() => Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.REMOVE])))
const canLinkDocuments = computed(() => canWrite.value && Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.DOCUMENT])))
const visibleScopes = computed(() => KNOWLEDGE_SCOPES.filter((scope) => scope.value !== 'TRASH' || canRemove.value))
const flatArticles = computed(() => rawTree.value.filter((item) => item.pageType === 'ARTICLE'))
const articleCount = computed(() => flatArticles.value.length)
const folderSelectTree = computed(() => [{ pageId: 0, title: '根目录', children: buildFolderOptions(treeData.value) }])
const relatedArticles = computed(() => flatArticles.value.filter((item) => Number(item.parentId) === Number(activePage.value?.parentId) && Number(item.pageId) !== Number(activePage.value?.pageId)).slice(0, 3))
const siblingArticles = computed(() => flatArticles.value.filter((item) => Number(item.parentId) === Number(activePage.value?.parentId)))
const siblingIndex = computed(() => siblingArticles.value.findIndex((item) => Number(item.pageId) === Number(activePage.value?.pageId)))
const previousArticle = computed(() => siblingIndex.value > 0 ? siblingArticles.value[siblingIndex.value - 1] : null)
const nextArticle = computed(() => siblingIndex.value >= 0 && siblingIndex.value < siblingArticles.value.length - 1 ? siblingArticles.value[siblingIndex.value + 1] : null)
const editorDirty = computed(() => mode.value !== 'read' && editorSnapshot() !== editorBaseline.value)

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  loadInitialData()
})
onBeforeUnmount(() => {
  if (searchTimer.value) clearTimeout(searchTimer.value)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
onBeforeRouteLeave((_to, _from, next) => {
  if (!editorDirty.value || window.confirm('当前知识尚未保存，确认离开吗？')) next()
})

async function loadInitialData() {
  loading.spaces = true
  try {
    const response = await listKnowledgeSpaces()
    spaces.value = response.data || []
    currentSpaceId.value = spaces.value[0] ? Number(spaces.value[0].spaceId) : null
    if (currentSpaceId.value) await loadTree({ selectFirst: true })
  } finally {
    loading.spaces = false
  }
}

async function loadTree({ selectFirst = false, keepSelection = true } = {}) {
  if (!currentSpaceId.value) return
  loading.tree = true
  try {
    const response = await listKnowledgeTree({ spaceId: currentSpaceId.value, lifecycleStatus: currentScope.value })
    rawTree.value = response.data || []
    treeData.value = buildKnowledgeTree(rawTree.value)
    const stillExists = keepSelection && rawTree.value.some((item) => Number(item.pageId) === Number(activePageId.value))
    if (!stillExists && selectFirst) {
      const first = rawTree.value.find((item) => item.pageType === 'ARTICLE')
      if (first) await selectPage(first.pageId)
      else clearActivePage()
    } else if (stillExists) {
      await nextTick()
      treeRef.value?.setCurrentKey(activePageId.value)
    }
  } finally {
    loading.tree = false
  }
}

async function selectPage(pageId) {
  if (!pageId) return
  if (editorDirty.value && !window.confirm('当前知识尚未保存，确认切换吗？')) return
  mode.value = 'read'
  activePageId.value = Number(pageId)
  searchPanelVisible.value = false
  loading.detail = true
  try {
    const response = await getKnowledgePage(pageId)
    applyPageDetail(response.data)
    await loadRecentVersions()
    await nextTick()
    treeRef.value?.setCurrentKey(activePageId.value)
    rebuildOutline()
  } finally {
    loading.detail = false
  }
}

function applyPageDetail(detail = {}) {
  activeDetail.page = detail.page || null
  activeDetail.tags = detail.tags || []
  activeDetail.documents = detail.documents || []
}

function clearActivePage() {
  activePageId.value = null
  activeDetail.page = null
  activeDetail.tags = []
  activeDetail.documents = []
  recentVersions.value = []
  outline.value = []
}

async function handleTreeNodeClick(data) {
  if (data.pageType === 'ARTICLE') await selectPage(data.pageId)
}

async function handleSpaceChange() {
  clearActivePage()
  await loadTree({ selectFirst: true, keepSelection: false })
}

async function handleScopeChange() {
  clearActivePage()
  await loadTree({ selectFirst: true, keepSelection: false })
}

function scheduleSearch() {
  if (searchTimer.value) clearTimeout(searchTimer.value)
  if (!searchKeyword.value.trim()) {
    searchResults.value = []
    searchPanelVisible.value = false
    return
  }
  searchTimer.value = setTimeout(runSearch, 220)
}

async function runSearch() {
  if (!searchKeyword.value.trim()) return
  loading.search = true
  searchPanelVisible.value = true
  try {
    const response = await searchKnowledgePages({ spaceId: currentSpaceId.value, keyword: searchKeyword.value.trim() })
    searchResults.value = response.data || []
  } finally {
    loading.search = false
  }
}

async function selectSearchResult(item) {
  searchKeyword.value = ''
  searchPanelVisible.value = false
  if (currentScope.value !== 'ACTIVE') {
    currentScope.value = 'ACTIVE'
    clearActivePage()
    await loadTree({ keepSelection: false })
  }
  await selectPage(item.pageId)
}

async function startCreatePage() {
  if (!currentSpaceId.value) return
  const parentId = selectedFolderId()
  if (currentScope.value !== 'ACTIVE') {
    currentScope.value = 'ACTIVE'
    clearActivePage()
    await loadTree({ keepSelection: false })
  }
  mode.value = 'create'
  resetEditorForm({ spaceId: currentSpaceId.value, parentId, title: '', summary: '', content: '<h2>知识说明</h2><p>请输入正文内容。</p>', tagNames: [], documents: [], expectedVersion: null })
}

function startEditPage() {
  if (!activePage.value) return
  mode.value = 'edit'
  resetEditorForm({
    spaceId: Number(activePage.value.spaceId), parentId: Number(activePage.value.parentId) || 0,
    title: activePage.value.title, summary: activePage.value.summary || '', content: activePage.value.content || '',
    tagNames: [...activeDetail.tags], documents: [...activeDetail.documents], expectedVersion: activePage.value.contentVersion
  })
}

function resetEditorForm({ spaceId, parentId, title, summary, content, tagNames, documents, expectedVersion }) {
  Object.assign(editorForm, { spaceId, parentId, title, summary, content, tagNames, documentIds: documents.map((item) => Number(item.documentId)), expectedVersion, changeNote: '' })
  editorDocuments.value = documents
  editorBaseline.value = editorSnapshot()
}

function editorSnapshot() {
  return JSON.stringify({ ...editorForm, documents: editorDocuments.value.map((item) => Number(item.documentId)) })
}

async function savePage() {
  if (loading.save) return
  loading.save = true
  const payload = {
    spaceId: editorForm.spaceId,
    parentId: Number(editorForm.parentId) || 0,
    title: editorForm.title.trim(),
    summary: editorForm.summary.trim(),
    content: editorForm.content,
    tagNames: normalizeKnowledgeTags(editorForm.tagNames),
    documentIds: editorDocuments.value.map((item) => Number(item.documentId)),
    expectedVersion: editorForm.expectedVersion,
    changeNote: editorForm.changeNote.trim()
  }
  try {
    const response = mode.value === 'create' ? await createKnowledgePage(payload) : await updateKnowledgePage(activePageId.value, payload)
    applyPageDetail(response.data)
    activePageId.value = Number(response.data?.page?.pageId)
    proxy.$modal.msgSuccess(`保存成功，已生成 V${response.data?.page?.contentVersion}`)
    mode.value = 'read'
    editorBaseline.value = ''
    await loadTree({ keepSelection: true })
    await loadRecentVersions()
    await nextTick()
    rebuildOutline()
  } catch (error) {
    if (String(error?.message || '').includes('其他用户更新')) {
      await proxy.$modal.alert('知识已被其他用户更新，本次保存已停止。你的编辑内容仍保留，请查看修改记录或重新加载最新版本。', '版本冲突', { type: 'warning' })
    }
    if (!String(error?.message || '').includes('其他用户更新')) proxy.$modal.msgError(error?.message || '知识保存失败')
  } finally {
    loading.save = false
  }
}

function cancelEdit() {
  if (editorDirty.value && !window.confirm('当前知识尚未保存，确认返回阅读吗？')) return
  mode.value = 'read'
  editorBaseline.value = ''
}

async function handlePageCommand(command) {
  if (!activePage.value) return
  const data = { expectedVersion: activePage.value.contentVersion, changeNote: '' }
  let restoredPageId = null
  if (command === 'archive') {
    await proxy.$modal.confirm(`确认归档“${activePage.value.title}”吗？`)
    const response = await archiveKnowledgePage(activePage.value.pageId, data)
    applyPageDetail(response.data)
  } else if (command === 'trash') {
    await proxy.$modal.confirm(`确认将“${activePage.value.title}”移入回收站吗？`)
    const response = await trashKnowledgePage(activePage.value.pageId, data)
    applyPageDetail(response.data)
  } else if (command === 'restore') {
    const response = await restoreKnowledgePage(activePage.value.pageId, data)
    applyPageDetail(response.data)
    restoredPageId = Number(response.data?.page?.pageId)
  }
  proxy.$modal.msgSuccess('知识状态已更新')
  if (restoredPageId) {
    currentScope.value = 'ACTIVE'
    await loadTree({ keepSelection: false })
    await selectPage(restoredPageId)
    return
  }
  await loadTree({ selectFirst: true, keepSelection: false })
}

async function loadRecentVersions() {
  if (!activePageId.value) return
  const response = await listKnowledgeVersions(activePageId.value)
  recentVersions.value = (response.data || []).slice(0, 2)
}

function rebuildOutline() {
  outline.value = outlineFromHtml(activePage.value?.content || '')
  nextTick(() => {
    const headings = articleBodyRef.value?.querySelectorAll('h1,h2,h3') || []
    headings.forEach((heading, index) => { heading.id = `knowledge-heading-${index}` })
  })
}

function scrollToHeading(id) {
  articleBodyRef.value?.querySelector(`#${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function openLinkedDocument(document) {
  const action = resolveKnowledgeDocumentAction(document)
  if (action === 'NONE') {
    proxy.$modal.msgWarning(documentAccessMessage(document) || '当前文档无法打开')
    return
  }
  if (action === 'DOWNLOAD') {
    proxy.download(`/document/workspace/documents/${document.documentId}/download`, {}, document.title)
    return
  }
  const routeName = action === 'PREVIEW' ? 'DocumentPreview' : 'DocumentEditor'
  const href = router.resolve({ name: routeName, params: { documentId: document.documentId } }).href
  const target = window.open(href, '_blank')
  if (!target) proxy.$modal.msgWarning('浏览器阻止了新窗口，请允许本站弹出窗口后重试')
  else target.opener = null
}

function permissionLabel(value) {
  return ({ OWNER: '所有者', EDIT: '可编辑', VIEW: '仅查看', ADMIN: '管理员' })[value] || '可访问'
}

function selectedFolderId() {
  const current = rawTree.value.find((item) => Number(item.pageId) === Number(treeRef.value?.getCurrentKey()))
  return current?.pageType === 'FOLDER' ? Number(current.pageId) : Number(activePage.value?.parentId) || 0
}

function handleSpaceCommand(command) {
  if (command === 'create') {
    Object.assign(spaceDialog, { open: true, mode: 'create', form: { spaceId: null, spaceName: '', description: '', sortOrder: null } })
  } else if (command === 'edit' && currentSpace.value) {
    Object.assign(spaceDialog, { open: true, mode: 'edit', form: { spaceId: currentSpace.value.spaceId, spaceName: currentSpace.value.spaceName, description: currentSpace.value.description || '', sortOrder: currentSpace.value.sortOrder } })
  } else if (command === 'folder') {
    openFolderDialog(null, 0)
  }
}

async function saveSpace() {
  if (!spaceDialog.form.spaceName.trim()) return proxy.$modal.msgWarning('空间名称不能为空')
  const request = spaceDialog.mode === 'create' ? createKnowledgeSpace(spaceDialog.form) : updateKnowledgeSpace(spaceDialog.form.spaceId, spaceDialog.form)
  const response = await request
  spaceDialog.open = false
  const spacesResponse = await listKnowledgeSpaces()
  spaces.value = spacesResponse.data || []
  currentSpaceId.value = Number(response.data?.spaceId || currentSpaceId.value)
  await loadTree({ selectFirst: true, keepSelection: false })
  proxy.$modal.msgSuccess('知识空间已保存')
}

function handleFolderCommand(command, folder) {
  if (command === 'child') openFolderDialog(null, folder.pageId)
  else if (command === 'edit') openFolderDialog(folder, folder.parentId)
  else removeFolder(folder)
}

function openFolderDialog(folder, parentId) {
  Object.assign(folderDialog, {
    open: true,
    mode: folder ? 'edit' : 'create',
    form: { pageId: folder?.pageId || null, spaceId: currentSpaceId.value, parentId: Number(parentId) || 0, title: folder?.title || '', sortOrder: folder?.sortOrder || null }
  })
}

async function saveFolder() {
  if (!folderDialog.form.title.trim()) return proxy.$modal.msgWarning('目录名称不能为空')
  if (folderDialog.mode === 'create') await createKnowledgeFolder(folderDialog.form)
  else await updateKnowledgeFolder(folderDialog.form.pageId, folderDialog.form)
  folderDialog.open = false
  await loadTree({ keepSelection: true })
  proxy.$modal.msgSuccess('知识目录已保存')
}

async function removeFolder(folder) {
  await proxy.$modal.confirm(`确认删除空目录“${folder.title}”吗？`)
  await removeKnowledgeFolder(folder.pageId)
  await loadTree({ keepSelection: true })
  proxy.$modal.msgSuccess('知识目录已删除')
}

function buildFolderOptions(nodes = []) {
  return nodes.filter((item) => item.pageType === 'FOLDER').map((item) => ({ ...item, children: buildFolderOptions(item.children || []) }))
}

function applySelectedDocuments(documents) {
  editorDocuments.value = documents
  editorForm.documentIds = documents.map((item) => Number(item.documentId))
}

function removeEditorDocument(documentId) {
  editorDocuments.value = editorDocuments.value.filter((item) => Number(item.documentId) !== Number(documentId))
  editorForm.documentIds = editorDocuments.value.map((item) => Number(item.documentId))
}

async function handleVersionRestored(detail) {
  applyPageDetail(detail)
  await loadTree({ keepSelection: true })
  await loadRecentVersions()
  await nextTick()
  rebuildOutline()
}

function handleBeforeUnload(event) {
  if (!editorDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
</script>

<style scoped lang="scss">
@use './KnowledgeWorkspace.scss';
</style>
