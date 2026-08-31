import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import {
  commitWorkbook,
  getWorkbookCatalog,
  getWorkbookNetworkPage,
  listWorkbookCommunityRows
} from '@/api/ipam/workbook.js'
import {
  WORKBOOK_PAGE_SIZE,
  buildCommunityScopeTree,
  buildCommunityWorkbookRows,
  buildNetworkScopeTree,
  buildNetworkWorkbookRows,
  buildWorkbookCommitBatches,
  filterScopeTree,
  markWorkbookRowsDirty,
  validateWorkbookRows,
  workbookRowMatches
} from './ipamWorkbookRules.js'

const MODE_OPTIONS = [
  { label: '按网段', value: 'network', icon: 'keyline-route' },
  { label: '按小区', value: 'community', icon: 'keyline-building' }
]

const TREE_PROPS = {
  value: 'key',
  label: 'label',
  children: 'children'
}

function firstLeaf(tree) {
  return (tree || []).find((group) => group.children?.length)?.children?.[0] || null
}

function findTreeNode(tree, key) {
  for (const group of tree || []) {
    if (group.key === key) return group
    const child = group.children?.find((item) => item.key === key)
    if (child) return child
  }
  return null
}

function formatLoadedTime() {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(new Date())
}

function editableColumn(prop, name, size, extra = {}) {
  return {
    prop,
    name,
    size,
    minSize: 88,
    sortable: true,
    filter: true,
    readonly: ({ model }) => Boolean(model?._locked),
    cellProperties: ({ model }) => ({
      class: model?._dirty ? 'ipam-workbook-cell is-dirty' : 'ipam-workbook-cell'
    }),
    ...extra
  }
}

function readonlyColumn(prop, name, size, extra = {}) {
  return {
    prop,
    name,
    size,
    minSize: 72,
    sortable: true,
    filter: true,
    readonly: true,
    ...extra
  }
}

export function useIpamWorkbook() {
  const { proxy } = getCurrentInstance()
  const { height: windowHeight } = useWindowSize()

  const scopeMode = ref('network')
  const selectedKeys = reactive({ network: '', community: '' })
  const scopeKeyword = ref('')
  const tableKeyword = ref('')
  const appliedTableKeyword = ref('')
  const scenarioType = ref('SOCIAL')
  const expandedFields = ref(false)
  const fullscreen = ref(false)
  const isEditing = ref(false)
  const rows = ref([])
  const scopeTreeRef = ref()
  const dirtyKeys = ref(new Set())
  const lastLoadedAt = ref('')
  const loadProgress = reactive({ loaded: 0, total: 0 })
  const loading = reactive({ catalog: false, sheet: false, submit: false })
  const catalog = reactive({ networks: [], communities: [] })
  let searchTimer = null
  let loadSequence = 0

  const subjectNameLabel = computed(() => scenarioType.value === 'INTERNAL' ? '项目名称' : '小区名称')
  const showInternalIp = computed(() => scenarioType.value !== 'INTERNAL')
  const selectedScopeKey = computed(() => selectedKeys[scopeMode.value])
  const sourceTree = computed(() => scopeMode.value === 'network'
    ? buildNetworkScopeTree(catalog.networks)
    : buildCommunityScopeTree(catalog.communities))
  const visibleScopeTree = computed(() => filterScopeTree(sourceTree.value, scopeKeyword.value))
  const expandedScopeKeys = computed(() => visibleScopeTree.value.map((item) => item.key))
  const selectedScope = computed(() => findTreeNode(sourceTree.value, selectedScopeKey.value))
  const visibleRows = computed(() => rows.value.filter((row) => workbookRowMatches(row, appliedTableKeyword.value)))
  const dirtyCount = computed(() => dirtyKeys.value.size)
  const scopeCount = computed(() => scopeMode.value === 'network' ? catalog.networks.length : catalog.communities.length)
  const treeHeight = computed(() => Math.max(280, Number(windowHeight.value || 800) - (fullscreen.value ? 176 : 270)))
  const loadPercent = computed(() => loadProgress.total
    ? Math.min(100, Math.round(loadProgress.loaded / loadProgress.total * 100))
    : 0)
  const sheetTitle = computed(() => selectedScope.value?.label || (scopeMode.value === 'network' ? '请选择网段' : `请选择${subjectNameLabel.value}`))
  const sheetDescription = computed(() => {
    if (!selectedScope.value) return ''
    if (scopeMode.value === 'network') {
      const network = selectedScope.value.value
      return `${network.startIp || '-'} - ${network.endIp || '-'} / 网关 ${network.gatewayIp || '-'}`
    }
    return `${selectedScope.value.count || 0} 个IP / ${selectedScope.value.description || '未关联网段'}`
  })

  const columns = computed(() => {
    const base = [
      readonlyColumn('ipAddress', '现场IP', 132, { pin: 'colPinStart' }),
      readonlyColumn('statusLabel', '状态', 92, {
        pin: 'colPinStart',
        cellProperties: ({ model }) => ({ class: `ipam-workbook-status is-${String(model?.statusCode || 'free').toLowerCase()}` })
      }),
      readonlyColumn('connectivityLabel', '连通性', 108),
      readonlyColumn('policeStationName', '派出所', 116),
      readonlyColumn('networkName', '网段', 148),
      editableColumn('communityName', subjectNameLabel.value, 168)
    ]
    if (showInternalIp.value) base.push(editableColumn('internalIpAddress', '小区内网IP', 138))
    base.push(
      editableColumn('targetTypeLabel', '设备类别', 120),
      editableColumn('targetName', '设备名称', 156),
      editableColumn('manufacturer', '品牌', 108),
      editableColumn('loginUsername', '用户名', 128),
      readonlyColumn('credentialState', '密码状态', 100)
    )

    if (expandedFields.value) {
      base.push(
        editableColumn('accessUnit', '接入单位', 132),
        editableColumn('purpose', '用途说明', 168),
        editableColumn('mappingAddress', '映射地址', 138),
        editableColumn('mappingPort', '映射端口', 112),
        editableColumn('mappingDescription', '映射说明', 176),
        editableColumn('ownerName', '联系人', 108),
        editableColumn('ownerPhone', '联系电话', 128),
        editableColumn('remark', '备注', 188),
        readonlyColumn('lastScanTime', '最近扫描', 164)
      )
    }
    return base
  })

  async function confirmDiscard() {
    if (!dirtyCount.value) return true
    try {
      await proxy.$modal.confirm('当前工作表有未保存内容，继续操作将丢失这些修改。是否继续？')
      return true
    } catch {
      return false
    }
  }

  function resetEditingState() {
    dirtyKeys.value = new Set()
    isEditing.value = false
  }

  async function loadNetworkRows(network, sequence) {
    const first = await getWorkbookNetworkPage(network.networkId, 1, WORKBOOK_PAGE_SIZE)
    if (sequence !== loadSequence) return []
    const firstData = first.data || {}
    const pageCount = Number(firstData.pageCount || 1)
    const total = Number(firstData.summary?.total || firstData.rows?.length || 0)
    const result = [...buildNetworkWorkbookRows(firstData.rows || [], network)]
    loadProgress.loaded = result.length
    loadProgress.total = total

    for (let page = 2; page <= pageCount; page += 4) {
      const pageNumbers = Array.from({ length: Math.min(4, pageCount - page + 1) }, (_, index) => page + index)
      const responses = await Promise.all(pageNumbers.map((pageNum) => (
        getWorkbookNetworkPage(network.networkId, pageNum, WORKBOOK_PAGE_SIZE)
      )))
      if (sequence !== loadSequence) return []
      responses.forEach((response) => {
        result.push(...buildNetworkWorkbookRows(response.data?.rows || [], network))
      })
      loadProgress.loaded = result.length
    }
    return result
  }

  async function loadCommunityRows(community, sequence) {
    const pageSize = 1000
    const first = await listWorkbookCommunityRows(community.communityName, 1, pageSize)
    if (sequence !== loadSequence) return []
    const total = Number(first.total || first.rows?.length || 0)
    const result = [...buildCommunityWorkbookRows(first.rows || [])]
    loadProgress.loaded = result.length
    loadProgress.total = total
    const pageCount = Math.ceil(total / pageSize)

    for (let page = 2; page <= pageCount; page += 4) {
      const pageNumbers = Array.from({ length: Math.min(4, pageCount - page + 1) }, (_, index) => page + index)
      const responses = await Promise.all(pageNumbers.map((pageNum) => (
        listWorkbookCommunityRows(community.communityName, pageNum, pageSize)
      )))
      if (sequence !== loadSequence) return []
      responses.forEach((response) => result.push(...buildCommunityWorkbookRows(response.rows || [])))
      loadProgress.loaded = result.length
    }
    return result
  }

  async function loadSelectedRows() {
    const scope = selectedScope.value
    const sequence = ++loadSequence
    loading.sheet = false
    rows.value = []
    loadProgress.loaded = 0
    loadProgress.total = Number(scope?.count || 0)
    resetEditingState()
    if (!scope || scope.kind === 'group') return

    loading.sheet = true
    try {
      const loadedRows = scope.kind === 'network'
        ? await loadNetworkRows(scope.value, sequence)
        : await loadCommunityRows(scope.value, sequence)
      if (sequence !== loadSequence) return
      rows.value = loadedRows
      lastLoadedAt.value = formatLoadedTime()
    } finally {
      if (sequence === loadSequence) loading.sheet = false
    }
  }

  async function loadCatalog() {
    loading.catalog = true
    try {
      const catalogResponse = await getWorkbookCatalog()
      const data = catalogResponse.data || {}
      catalog.networks = data.networks || []
      catalog.communities = data.communities || []
      scenarioType.value = data.scenarioType === 'INTERNAL' ? 'INTERNAL' : 'SOCIAL'

      const currentTree = sourceTree.value
      if (!findTreeNode(currentTree, selectedScopeKey.value)) {
        const leaf = firstLeaf(currentTree)
        selectedKeys[scopeMode.value] = leaf?.key || ''
      }
      await loadSelectedRows()
    } finally {
      loading.catalog = false
    }
  }

  async function changeScopeMode(nextMode) {
    if (nextMode === scopeMode.value) return
    if (!await confirmDiscard()) return
    scopeMode.value = nextMode
    scopeKeyword.value = ''
    tableKeyword.value = ''
    appliedTableKeyword.value = ''
    if (!findTreeNode(sourceTree.value, selectedScopeKey.value)) {
      selectedKeys[nextMode] = firstLeaf(sourceTree.value)?.key || ''
    }
    await loadSelectedRows()
  }

  async function selectScope(node) {
    if (!node || node.kind === 'group' || node.key === selectedScopeKey.value) return
    if (!await confirmDiscard()) return
    selectedKeys[scopeMode.value] = node.key
    tableKeyword.value = ''
    appliedTableKeyword.value = ''
    await loadSelectedRows()
  }

  async function refreshWorkbook() {
    if (!await confirmDiscard()) return
    await loadCatalog()
    proxy.$modal.msgSuccess('工作表数据已刷新')
  }

  function startEditing() {
    if (!rows.value.length) return
    isEditing.value = true
  }

  function handleAfterEdit(event) {
    if (!isEditing.value) return
    const changedKeys = markWorkbookRowsDirty(event?.detail)
    if (!changedKeys.length) return
    const nextKeys = new Set(dirtyKeys.value)
    changedKeys.forEach((key) => nextKeys.add(key))
    dirtyKeys.value = nextKeys
  }

  async function saveWorkbook() {
    if (!dirtyCount.value) return
    const dirtyRows = rows.value.filter((row) => dirtyKeys.value.has(row._rowKey))
    const errors = validateWorkbookRows(dirtyRows, subjectNameLabel.value)
    if (errors.length) {
      proxy.$modal.msgError(errors.length > 1 ? `${errors[0]}，另有${errors.length - 1}行待处理` : errors[0])
      return
    }
    const batches = buildWorkbookCommitBatches(rows.value, dirtyKeys.value)
    if (!batches.length) {
      resetEditingState()
      return
    }
    if (batches.length > 16) {
      proxy.$modal.msgError('单次最多保存4096个IP，请缩小编辑范围后重试')
      return
    }

    try {
      await proxy.$modal.confirm(`确认保存 ${dirtyCount.value} 行工作表修改？`)
    } catch {
      return
    }
    loading.submit = true
    try {
      await commitWorkbook(batches)
      proxy.$modal.msgSuccess('工作表已保存到平台')
      await loadSelectedRows()
    } finally {
      loading.submit = false
    }
  }

  async function reloadSheet() {
    if (!await confirmDiscard()) return
    await loadSelectedRows()
  }

  function toggleFullscreen() {
    fullscreen.value = !fullscreen.value
  }

  function handleEscape(event) {
    if (event.key === 'Escape' && fullscreen.value) fullscreen.value = false
  }

  function handleBeforeUnload(event) {
    if (!dirtyCount.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  watch(tableKeyword, (value) => {
    window.clearTimeout(searchTimer)
    searchTimer = window.setTimeout(() => {
      appliedTableKeyword.value = value
    }, 180)
  })

  watch([visibleScopeTree, scopeKeyword], async () => {
    await nextTick()
    scopeTreeRef.value?.setExpandedKeys(expandedScopeKeys.value)
  })

  watch(fullscreen, (value) => {
    document.body.classList.toggle('ipam-workbook-fullscreen', value)
  })

  onMounted(() => {
    window.addEventListener('keydown', handleEscape)
    window.addEventListener('beforeunload', handleBeforeUnload)
    loadCatalog().catch(() => {})
  })

  onBeforeUnmount(() => {
    loadSequence++
    window.clearTimeout(searchTimer)
    window.removeEventListener('keydown', handleEscape)
    window.removeEventListener('beforeunload', handleBeforeUnload)
    document.body.classList.remove('ipam-workbook-fullscreen')
  })

  onBeforeRouteLeave(async () => confirmDiscard())

  return {
    MODE_OPTIONS,
    TREE_PROPS,
    scopeMode,
    scopeTreeRef,
    selectedScopeKey,
    scopeKeyword,
    tableKeyword,
    scenarioType,
    expandedFields,
    fullscreen,
    isEditing,
    rows,
    visibleRows,
    columns,
    dirtyCount,
    lastLoadedAt,
    loadProgress,
    loadPercent,
    loading,
    subjectNameLabel,
    showInternalIp,
    visibleScopeTree,
    expandedScopeKeys,
    scopeCount,
    treeHeight,
    sheetTitle,
    sheetDescription,
    changeScopeMode,
    selectScope,
    refreshWorkbook,
    reloadSheet,
    startEditing,
    handleAfterEdit,
    saveWorkbook,
    toggleFullscreen
  }
}
