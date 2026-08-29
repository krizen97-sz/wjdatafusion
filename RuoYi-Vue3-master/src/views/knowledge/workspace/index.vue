<template>
  <div class="app-container knowledge-workspace-page">
    <el-container class="knowledge-layout">
      <el-aside class="knowledge-navigation-pane">
        <KnowledgeNavigation
          v-model:current-space-id="currentSpaceId"
          v-model:current-scope="currentScope"
          v-model:search-keyword="searchKeyword"
          :spaces="spaces"
          :current-space="currentSpace"
          :visible-scopes="visibleScopes"
          :tree-data="treeData"
          :active-page-id="activePageId"
          :article-count="articleCount"
          :fetch-suggestions="fetchSearchSuggestions"
          :can-write="canWrite && mode === 'read'"
          :can-manage-space="canManageSpace"
          :disabled="mode !== 'read'"
          :loading="loading.tree || loading.spaces"
          @space-change="handleSpaceChange"
          @space-command="handleSpaceCommand"
          @scope-change="handleScopeChange"
          @create-page="startCreatePage"
          @select-search="selectSearchResult"
          @node-click="handleTreeNodeClick"
          @folder-command="handleFolderCommand"
        />
      </el-aside>

      <el-container class="knowledge-content-pane">
        <el-header class="knowledge-command-bar" height="auto">
          <div class="knowledge-command-bar__context">
            <el-button class="knowledge-navigation-trigger" icon="Menu" @click="navigationDrawerOpen = true">知识目录</el-button>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item>{{ currentSpace?.spaceName || '知识中心' }}</el-breadcrumb-item>
              <el-breadcrumb-item v-if="mode === 'read'">{{ currentScopeLabel }}</el-breadcrumb-item>
              <el-breadcrumb-item v-if="mode === 'read' && activePage">{{ activeDirectoryTitle }}</el-breadcrumb-item>
              <el-breadcrumb-item v-if="mode !== 'read'">{{ mode === 'create' ? '新建知识' : '编辑知识' }}</el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <el-space v-if="mode === 'read' && activePage" wrap>
            <el-button v-if="outline.length" icon="List" @click="outlineDrawerOpen = true">本文目录</el-button>
            <el-button icon="Clock" @click="historyOpen = true">修改记录</el-button>
            <el-button v-if="canWrite && activePage.lifecycleStatus !== 'TRASH'" type="primary" icon="Edit" @click="startEditPage">编辑知识</el-button>
            <el-dropdown trigger="click" @command="handlePageCommand">
              <el-button circle icon="MoreFilled" aria-label="更多知识操作" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="canWrite && activePage.lifecycleStatus === 'ACTIVE'" command="archive">归档知识</el-dropdown-item>
                  <el-dropdown-item v-if="canRemove && activePage.lifecycleStatus !== 'TRASH'" command="trash" divided>移入回收站</el-dropdown-item>
                  <el-dropdown-item v-if="canRemove && activePage.lifecycleStatus === 'ARCHIVED'" command="restore">恢复为当前知识</el-dropdown-item>
                  <el-dropdown-item v-if="canRemove && activePage.lifecycleStatus === 'TRASH'" command="restore">恢复知识</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-space>

          <el-space v-else-if="mode !== 'read'">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button
              type="primary"
              icon="Finished"
              :loading="loading.save"
              :disabled="!editorForm.title.trim() || !editorForm.content.trim()"
              @click="savePage"
            >保存新版本</el-button>
          </el-space>
        </el-header>

        <el-main class="knowledge-main" v-loading="mode === 'read' ? loading.detail : loading.save">
          <template v-if="mode === 'read'">
            <el-empty v-if="!activePage && !loading.detail" description="从左侧目录选择一篇知识开始阅读">
              <el-button v-if="canWrite" type="primary" icon="Plus" @click="startCreatePage">新建知识</el-button>
            </el-empty>

            <el-scrollbar v-else-if="activePage" class="knowledge-reader-scroll">
              <article class="knowledge-article">
                <header class="knowledge-article-heading">
                  <div class="knowledge-article-title">
                    <h1>{{ activePage.title }}</h1>
                    <el-tag effect="plain">V{{ activePage.contentVersion }}</el-tag>
                  </div>
                  <el-space wrap :size="12">
                    <el-text type="info" size="small">{{ activePage.modifierName || activePage.updateBy }} 修改于 {{ activePage.updateTime }}</el-text>
                    <el-text type="info" size="small">创建人 {{ activePage.creatorName || activePage.createBy }}</el-text>
                  </el-space>
                </header>

                <el-space v-if="activeDetail.tags.length" class="knowledge-tags" wrap>
                  <el-tag v-for="tag in activeDetail.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
                </el-space>

                <el-alert
                  v-if="activePage.summary"
                  class="knowledge-summary"
                  :title="activePage.summary"
                  type="info"
                  show-icon
                  :closable="false"
                />

                <div ref="articleBodyRef" class="knowledge-body-html" v-html="activePage.content" />

                <section v-if="activeDetail.documents.length" class="knowledge-section">
                  <div class="knowledge-section-heading">
                    <h2>附件</h2>
                    <el-text type="info" size="small">{{ activeDetail.documents.length }} 份 · 权限实时取自文档管理</el-text>
                  </div>
                  <el-table :data="activeDetail.documents" size="small" table-layout="fixed">
                    <el-table-column label="文档" min-width="260">
                      <template #default="{ row }">
                        <el-space>
                          <el-tag size="small" effect="plain">{{ String(row.fileType || 'FILE').toUpperCase() }}</el-tag>
                          <span class="knowledge-document-title">{{ row.title }}</span>
                        </el-space>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态" width="110">
                      <template #default="{ row }"><el-tag size="small" effect="plain" :type="documentStatusMeta(row).type">{{ documentStatusMeta(row).label }}</el-tag></template>
                    </el-table-column>
                    <el-table-column label="权限" width="100">
                      <template #default="{ row }">{{ permissionLabel(row.accessPermission) }}</template>
                    </el-table-column>
                    <el-table-column label="版本" width="80">
                      <template #default="{ row }">V{{ row.contentVersion || 1 }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="90" align="right">
                      <template #default="{ row }">
                        <el-button link type="primary" :disabled="resolveKnowledgeDocumentAction(row) === 'NONE'" @click="openLinkedDocument(row)">{{ documentActionLabel(row) }}</el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </section>

                <section v-if="relatedArticles.length" class="knowledge-section">
                  <div class="knowledge-section-heading"><h2>同目录知识</h2></div>
                  <el-space wrap>
                    <el-button v-for="item in relatedArticles" :key="item.pageId" plain icon="Document" @click="selectPage(item.pageId)">{{ item.title }}</el-button>
                  </el-space>
                </section>

                <footer class="knowledge-article-footer">
                  <el-button-group>
                    <el-button icon="ArrowLeft" :disabled="!previousArticle" @click="selectPage(previousArticle?.pageId)">{{ previousArticle?.title || '没有上一篇' }}</el-button>
                    <el-button :disabled="!nextArticle" @click="selectPage(nextArticle?.pageId)">{{ nextArticle?.title || '没有下一篇' }}<el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button>
                  </el-button-group>
                </footer>
              </article>
            </el-scrollbar>
          </template>

          <el-scrollbar v-else class="knowledge-editor-scroll">
            <el-form class="knowledge-editor-form" label-position="top">
              <div class="knowledge-editor-heading">
                <div>
                  <h1>{{ mode === 'create' ? '新建知识' : '编辑知识' }}</h1>
                  <el-text type="info">{{ mode === 'create' ? '保存后立即对有查看权限的用户可见' : `基于 V${editorForm.expectedVersion} 编辑，保存后生成新版本` }}</el-text>
                </div>
              </div>

              <el-row :gutter="24">
                <el-col :xs="24" :sm="24" :md="16" :lg="17">
                  <el-form-item label="知识标题" required>
                    <el-input v-model="editorForm.title" maxlength="160" show-word-limit />
                  </el-form-item>
                  <el-form-item label="摘要">
                    <el-input v-model="editorForm.summary" maxlength="500" show-word-limit placeholder="用一句话说明这篇知识解决什么问题" />
                  </el-form-item>
                  <el-form-item label="正文" required>
                    <editor v-model="editorForm.content" :min-height="460" />
                  </el-form-item>
                </el-col>

                <el-col :xs="24" :sm="24" :md="8" :lg="7">
                  <el-collapse v-model="editorPanels">
                    <el-collapse-item title="知识设置" name="settings">
                      <el-form-item label="所属空间">
                        <el-select v-model="editorForm.spaceId" disabled>
                          <el-option v-for="space in spaces" :key="space.spaceId" :label="space.spaceName" :value="Number(space.spaceId)" />
                        </el-select>
                      </el-form-item>
                      <el-form-item label="所属目录">
                        <el-tree-select v-model="editorForm.parentId" :data="folderSelectTree" node-key="pageId" :props="{ label: 'title', children: 'children' }" check-strictly clearable placeholder="根目录" />
                      </el-form-item>
                      <el-form-item label="标签">
                        <el-select v-model="editorForm.tagNames" multiple filterable allow-create default-first-option :multiple-limit="8" placeholder="输入后回车添加标签" />
                      </el-form-item>
                      <el-form-item label="修改说明">
                        <el-input v-model="editorForm.changeNote" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可选：说明本次调整内容" />
                      </el-form-item>
                    </el-collapse-item>

                    <el-collapse-item title="关联文档" name="documents">
                      <el-alert title="这里只保存文档ID，打开和下载继续执行文档管理权限。" type="info" :closable="false" />
                      <el-button v-if="canLinkDocuments" class="knowledge-document-select" icon="Link" @click="documentSelectorOpen = true">选择现有文档</el-button>
                      <el-table v-if="editorDocuments.length" :data="editorDocuments" size="small" :show-header="false">
                        <el-table-column min-width="180">
                          <template #default="{ row }">
                            <el-space><el-tag size="small" effect="plain">{{ String(row.fileType || 'FILE').toUpperCase() }}</el-tag><span class="knowledge-document-title">{{ row.title }}</span></el-space>
                          </template>
                        </el-table-column>
                        <el-table-column v-if="canLinkDocuments" width="48" align="right">
                          <template #default="{ row }"><el-button text circle icon="Delete" aria-label="移除关联文档" @click="removeEditorDocument(row.documentId)" /></template>
                        </el-table-column>
                      </el-table>
                      <el-empty v-else description="暂无关联文档" :image-size="64" />
                      <el-alert v-if="!canLinkDocuments && editorDocuments.length" title="当前账号无文档管理权限，可以修改正文并原样保留附件，但不能调整附件关系。" type="warning" :closable="false" show-icon />
                    </el-collapse-item>
                  </el-collapse>
                </el-col>
              </el-row>
            </el-form>
          </el-scrollbar>
        </el-main>
      </el-container>
    </el-container>

    <el-drawer v-model="navigationDrawerOpen" title="知识目录" direction="ltr" size="320px" append-to-body>
      <KnowledgeNavigation
        v-model:current-space-id="currentSpaceId"
        v-model:current-scope="currentScope"
        v-model:search-keyword="searchKeyword"
        :spaces="spaces"
        :current-space="currentSpace"
        :visible-scopes="visibleScopes"
        :tree-data="treeData"
        :active-page-id="activePageId"
        :article-count="articleCount"
        :fetch-suggestions="fetchSearchSuggestions"
        :can-write="canWrite && mode === 'read'"
        :can-manage-space="canManageSpace"
        :disabled="mode !== 'read'"
        :loading="loading.tree || loading.spaces"
        @space-change="handleSpaceChange"
        @space-command="handleSpaceCommand"
        @scope-change="handleScopeChange"
        @create-page="startCreatePage"
        @select-search="selectSearchResult"
        @node-click="handleTreeNodeClick"
        @folder-command="handleFolderCommand"
      />
    </el-drawer>

    <el-drawer v-model="outlineDrawerOpen" title="本文目录" size="320px" append-to-body>
      <el-empty v-if="!outline.length" description="当前文章没有标题目录" />
      <el-menu v-else :default-active="outline[0]?.id" @select="scrollToHeading">
        <el-menu-item v-for="item in outline" :key="item.id" :index="item.id">
          <span :style="{ paddingLeft: `${(item.level - 1) * 12}px` }">{{ item.text }}</span>
        </el-menu-item>
      </el-menu>
    </el-drawer>

    <el-dialog v-model="spaceDialog.open" :title="spaceDialog.mode === 'create' ? '新建知识空间' : '编辑知识空间'" width="480px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="空间名称" required><el-input v-model="spaceDialog.form.spaceName" maxlength="100" /></el-form-item>
        <el-form-item label="空间说明"><el-input v-model="spaceDialog.form.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="spaceDialog.open = false">取消</el-button><el-button type="primary" :loading="loading.spaceSave" @click="saveSpace">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="folderDialog.open" :title="folderDialog.mode === 'create' ? '新建知识目录' : '编辑知识目录'" width="480px" append-to-body>
      <el-form label-position="top">
        <el-form-item label="目录名称" required><el-input v-model="folderDialog.form.title" maxlength="100" /></el-form-item>
        <el-form-item label="上级目录"><el-tree-select v-model="folderDialog.form.parentId" :data="folderSelectTree" node-key="pageId" :props="{ label: 'title', children: 'children' }" check-strictly clearable placeholder="根目录" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="folderDialog.open = false">取消</el-button><el-button type="primary" :loading="loading.folderSave" @click="saveFolder">保存</el-button></template>
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
import KnowledgeNavigation from '@/views/knowledge/components/KnowledgeNavigation.vue'
import {
  KNOWLEDGE_PERMISSIONS,
  KNOWLEDGE_SCOPES,
  buildKnowledgeTree,
  documentAccessMessage,
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
const navigationSelectionId = ref(null)
const activePageId = ref(null)
const activeDetail = reactive({ page: null, tags: [], documents: [] })
const articleBodyRef = ref()
const outline = ref([])
const searchKeyword = ref('')
const searchSequence = ref(0)
const mode = ref('read')
const historyOpen = ref(false)
const documentSelectorOpen = ref(false)
const navigationDrawerOpen = ref(false)
const outlineDrawerOpen = ref(false)
const loading = reactive({ spaces: false, tree: false, detail: false, search: false, save: false, spaceSave: false, folderSave: false })

const editorForm = reactive({ spaceId: null, parentId: null, title: '', summary: '', content: '', tagNames: [], documentIds: [], expectedVersion: null, changeNote: '' })
const editorDocuments = ref([])
const editorBaseline = ref('')
const editorPanels = ref(['settings', 'documents'])

const spaceDialog = reactive({ open: false, mode: 'create', form: { spaceId: null, spaceName: '', description: '', sortOrder: null } })
const folderDialog = reactive({ open: false, mode: 'create', form: { pageId: null, spaceId: null, parentId: null, title: '', sortOrder: null } })

const currentSpace = computed(() => spaces.value.find((space) => Number(space.spaceId) === Number(currentSpaceId.value)))
const activePage = computed(() => activeDetail.page)
const canWrite = computed(() => Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.WRITE])))
const canManageSpace = computed(() => Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.SPACE_MANAGE])))
const canRemove = computed(() => Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.REMOVE])))
const canLinkDocuments = computed(() => canWrite.value && Boolean(proxy?.$auth?.hasPermi([KNOWLEDGE_PERMISSIONS.DOCUMENT])))
const visibleScopes = computed(() => KNOWLEDGE_SCOPES.filter((scope) => scope.value !== 'TRASH' || canRemove.value))
const currentScopeLabel = computed(() => visibleScopes.value.find((scope) => scope.value === currentScope.value)?.label || '知识')
const flatArticles = computed(() => rawTree.value.filter((item) => item.pageType === 'ARTICLE'))
const articleCount = computed(() => flatArticles.value.length)
const folderSelectTree = computed(() => [{ pageId: 0, title: '根目录', children: buildFolderOptions(treeData.value) }])
const activeDirectoryTitle = computed(() => {
  const parentId = Number(activePage.value?.parentId || 0)
  return parentId ? rawTree.value.find((item) => Number(item.pageId) === parentId)?.title || '知识目录' : '根目录'
})
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
  navigationSelectionId.value = Number(pageId)
  navigationDrawerOpen.value = false
  loading.detail = true
  try {
    const response = await getKnowledgePage(pageId)
    applyPageDetail(response.data)
    await nextTick()
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
  outline.value = []
}

async function handleTreeNodeClick(data) {
  navigationSelectionId.value = Number(data.pageId)
  if (data.pageType === 'ARTICLE') await selectPage(data.pageId)
}

async function handleSpaceChange() {
  navigationSelectionId.value = null
  clearActivePage()
  await loadTree({ selectFirst: true, keepSelection: false })
}

async function handleScopeChange() {
  navigationSelectionId.value = null
  clearActivePage()
  await loadTree({ selectFirst: true, keepSelection: false })
}

async function fetchSearchSuggestions(query, callback) {
  const keyword = String(query || '').trim()
  if (!keyword) {
    callback([])
    return
  }
  const requestId = ++searchSequence.value
  loading.search = true
  try {
    const response = await searchKnowledgePages({ spaceId: currentSpaceId.value, keyword })
    callback(requestId === searchSequence.value ? response.data || [] : [])
  } catch (_error) {
    callback([])
  } finally {
    if (requestId === searchSequence.value) loading.search = false
  }
}

async function selectSearchResult(item) {
  searchKeyword.value = ''
  navigationDrawerOpen.value = false
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
  navigationDrawerOpen.value = false
  editorPanels.value = ['settings', 'documents']
  resetEditorForm({ spaceId: currentSpaceId.value, parentId, title: '', summary: '', content: '<h2>知识说明</h2><p>请输入正文内容。</p>', tagNames: [], documents: [], expectedVersion: null })
}

function startEditPage() {
  if (!activePage.value) return
  mode.value = 'edit'
  editorPanels.value = ['settings', 'documents']
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

function rebuildOutline() {
  outline.value = outlineFromHtml(activePage.value?.content || '')
  nextTick(() => {
    const headings = articleBodyRef.value?.querySelectorAll('h1,h2,h3') || []
    headings.forEach((heading, index) => { heading.id = `knowledge-heading-${index}` })
  })
}

function scrollToHeading(id) {
  outlineDrawerOpen.value = false
  nextTick(() => articleBodyRef.value?.querySelector(`#${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
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

function documentStatusMeta(document) {
  const status = document?.accessStatus
  if (status === 'AVAILABLE') return { label: '可用', type: 'success' }
  if (status === 'ARCHIVED') return { label: '已归档', type: 'info' }
  if (status === 'TRASH') return { label: '回收站', type: 'warning' }
  if (status === 'NO_MODULE_PERMISSION') return { label: '无模块权限', type: 'warning' }
  return { label: '无权访问', type: 'danger' }
}

function documentActionLabel(document) {
  const action = resolveKnowledgeDocumentAction(document)
  return ({ EDITOR: '打开', PREVIEW: '预览', DOWNLOAD: '下载', NONE: '不可用' })[action] || '打开'
}

function selectedFolderId() {
  const current = rawTree.value.find((item) => Number(item.pageId) === Number(navigationSelectionId.value))
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
  if (loading.spaceSave) return
  loading.spaceSave = true
  try {
    const request = spaceDialog.mode === 'create' ? createKnowledgeSpace(spaceDialog.form) : updateKnowledgeSpace(spaceDialog.form.spaceId, spaceDialog.form)
    const response = await request
    spaceDialog.open = false
    const spacesResponse = await listKnowledgeSpaces()
    spaces.value = spacesResponse.data || []
    currentSpaceId.value = Number(response.data?.spaceId || currentSpaceId.value)
    await loadTree({ selectFirst: true, keepSelection: false })
    proxy.$modal.msgSuccess('知识空间已保存')
  } finally {
    loading.spaceSave = false
  }
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
  if (loading.folderSave) return
  loading.folderSave = true
  try {
    if (folderDialog.mode === 'create') await createKnowledgeFolder(folderDialog.form)
    else await updateKnowledgeFolder(folderDialog.form.pageId, folderDialog.form)
    folderDialog.open = false
    await loadTree({ keepSelection: true })
    proxy.$modal.msgSuccess('知识目录已保存')
  } finally {
    loading.folderSave = false
  }
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
