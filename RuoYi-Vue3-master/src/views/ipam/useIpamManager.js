import { computed, getCurrentInstance, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { MAX_CONFIG_SELECTION, appendSelectionRange, buildNetworkStationTree, storeRevealedCredential } from './ipamRules.js'
import {
  DEVICE_TYPE_OPTIONS,
  MANUFACTURER_OPTIONS,
  SCENARIO_TYPE_OPTIONS,
  getStatusMeta,
  getTargetTypeLabel
} from './ipamCatalog.js'
import {
  addNetwork,
  commitConfigSheet,
  delNetwork,
  getAddressCredential,
  getAddressGrid,
  getLatestNetworkScan,
  getScanJob,
  getScenarioSetting,
  listAddress,
  listNetworkTree,
  startNetworkScan as startNetworkScanRequest,
  updateNetwork,
  updateScenarioSetting
} from '@/api/ipam'

const DEFAULT_PASTE_KEYS = [
  'ipAddress',
  'communityName',
  'internalIpAddress',
  'targetType',
  'targetName',
  'manufacturer',
  'loginUsername',
  'loginPassword',
  'accessUnit',
  'ownerName',
  'ownerPhone',
  'mappingAddress',
  'mappingPort',
  'mappingDescription',
  'remark'
]

const HEADER_KEYWORDS = [
  { key: 'ipAddress', words: ['现场ip', '现场地址', 'ip地址', 'ip'] },
  { key: 'communityName', words: ['小区社区', '小区/社区', '小区名称', '项目名称', '社区名称', '小区', '社区', '项目', '点位'] },
  { key: 'internalIpAddress', words: ['内网ip', '小区内网ip', '内网地址', '局域网ip'] },
  { key: 'targetType', words: ['设备类别', '设备类型', '类别', '类型'] },
  { key: 'targetName', words: ['设备名称', '名称', '设备', '对象名称'] },
  { key: 'manufacturer', words: ['品牌', '厂家', '厂商', '制造商'] },
  { key: 'loginUsername', words: ['用户名', '账号', '登录账号', '设备账号'] },
  { key: 'loginPassword', words: ['密码', '登录密码', '设备密码'] },
  { key: 'accessUnit', words: ['接入单位', '单位', '对接单位'] },
  { key: 'ownerName', words: ['联系人', '责任人', '负责人'] },
  { key: 'ownerPhone', words: ['联系电话', '电话', '手机号', '联系方式'] },
  { key: 'mappingAddress', words: ['映射地址', '映射ip', 'nat地址'] },
  { key: 'mappingPort', words: ['映射端口', '端口', 'nat端口'] },
  { key: 'mappingDescription', words: ['映射说明', '映射关系', '映射备注'] },
  { key: 'remark', words: ['备注', '说明'] },
  { key: 'status', words: ['状态'] }
]

const BUSINESS_FIELDS = [
  'communityName',
  'targetType',
  'targetName',
  'manufacturer',
  'internalIpAddress',
  'accessUnit',
  'purpose',
  'loginUsername',
  'loginPassword',
  'mappingAddress',
  'mappingPort',
  'mappingDescription',
  'ownerName',
  'ownerPhone',
  'remark'
]

const TEMPLATE_COPY_FIELDS = [
  'targetType',
  'targetName',
  'manufacturer',
  'loginUsername',
  'loginPassword',
  'accessUnit',
  'ownerName',
  'ownerPhone',
  'mappingAddress',
  'mappingPort',
  'mappingDescription',
  'remark'
]

const DOWNWARD_FILL_FIELDS = [
  'communityName',
  'internalIpAddress',
  ...TEMPLATE_COPY_FIELDS
]

function buildIncrementedDeviceName(name, offset) {
  const deviceName = String(name ?? '').trim()
  if (!deviceName) return null

  const suffixMatch = deviceName.match(/^(.*?)(\d+)$/)
  if (!suffixMatch) return `${deviceName}${offset}`

  const suffix = suffixMatch[2]
  const suffixNumber = Number(suffix)
  if (!Number.isSafeInteger(suffixNumber)) return `${deviceName}${offset}`

  return `${suffixMatch[1]}${String(suffixNumber + offset).padStart(suffix.length, '0')}`
}

export function useIpamManager() {
  const { proxy } = getCurrentInstance()
  clearLegacySensitiveRequestCache()

  const showSearch = ref(true)
  const activeTab = ref('network')
  const loading = reactive({
    network: false,
    grid: false,
    ledger: false,
    scenario: false,
    scan: false,
    submit: false
  })

  const networkRef = ref(null)
  const networkOpen = ref(false)
  const pasteOpen = ref(false)
  const configFullscreen = ref(false)
  const networkTitle = ref('')
  const selectedNetworkId = ref(null)
  const selectedSegment = ref(null)
  const selectedSheetIp = ref(null)
  const scanJob = ref(null)
  const gridDisplayMode = ref('compact')
  const selectionDragStartIp = ref(null)
  const selectionDragging = ref(false)
  const networkList = ref([])
  const stationNameCatalog = ref([])
  const networkTotal = ref(0)
  const globalScenarioType = ref('SOCIAL')
  const addressRows = ref([])
  const selectedIpList = ref([])
  const sheetRows = ref([])
  const ledgerList = ref([])
  const ledgerTotal = ref(0)
  const pasteText = ref('')
  const gridSummary = reactive({
    total: 0,
    FREE: 0,
    RESERVED: 0,
    ALLOCATED: 0,
    ISSUED: 0,
    DISABLED: 0
  })
  const gridPage = reactive({
    pageNum: 1,
    pageSize: 256,
    pageCount: 1,
    rangeStartIp: null,
    rangeEndIp: null
  })

  const networkQuery = reactive({
    keyword: null
  })

  const ledgerQuery = reactive({
    pageNum: 1,
    pageSize: 10,
    networkId: null,
    status: null,
    communityName: null,
    targetType: null,
    manufacturer: null,
    internalIpAddress: null,
    accessUnit: null
  })

  const networkForm = reactive({
    networkId: null,
    networkName: null,
    policeStationName: null,
    gatewayIp: null,
    subnetMask: null,
    status: '0',
    remark: null
  })

  const canIssueAddress = Boolean(proxy?.$auth?.hasPermi(['ipam:address:issue']))
  const statusOptions = [
    { label: '空闲', value: 'FREE' },
    { label: '保留', value: 'RESERVED' },
    { label: '已占用', value: 'ALLOCATED' },
    { label: '已下发', value: 'ISSUED', disabled: !canIssueAddress },
    { label: '禁用', value: 'DISABLED' }
  ]

  const selectedNetwork = computed(() => networkList.value.find((item) => item.networkId === selectedNetworkId.value) || null)
  const networkTree = computed(() => buildNetworkStationTree(networkList.value))
  const stationOptions = computed(() => stationNameCatalog.value
    .map((stationName) => ({ label: stationName, value: stationName })))
  const currentScenarioType = computed(() => globalScenarioType.value)
  const isInternalScenario = computed(() => currentScenarioType.value === 'INTERNAL')
  const subjectNameLabel = computed(() => isInternalScenario.value ? '项目名称' : '小区名称')
  const showInternalIpField = computed(() => !isInternalScenario.value)
  const canEditNetwork = computed(() => Boolean(proxy?.$auth?.hasPermi(['ipam:network:edit'])))
  const canConfigureNetwork = computed(() => Boolean(selectedSegment.value?.segmentId && selectedSegment.value?.gatewayIp && selectedSegment.value?.subnetMask))
  const networkPreview = computed(() => buildNetworkPreview(networkForm.gatewayIp, networkForm.subnetMask))
  const selectedIpSet = computed(() => new Set(selectedIpList.value))
  const selectedIpCount = computed(() => selectedIpList.value.length)
  const sheetChangedCount = computed(() => sheetRows.value.filter((item) => item._dirty).length)
  const sheetErrorCount = computed(() => sheetRows.value.filter((item) => item._error).length)

  const networkRules = {
    policeStationName: [{ required: true, message: '所属派出所不能为空', trigger: 'change' }],
    networkName: [{ required: true, message: '网段名称不能为空', trigger: 'blur' }],
    gatewayIp: [{ validator: validateGatewayIp, trigger: 'blur' }],
    subnetMask: [{ validator: validateSubnetMask, trigger: 'blur' }],
    status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
  }

  let networkSearchTimer = null
  let scanPollTimer = null
  let scenarioSettingLoaded = false
  let selectionDragBaseSet = new Set()
  let selectionDragShouldSelect = true
  let selectionProtectedDirty = false
  let selectionCapped = false
  let selectionPendingEndIp = null
  let selectionFrameId = null
  const selectionPreviewSet = ref(new Set())

  function loadScenarioSetting() {
    loading.scenario = true
    return getScenarioSetting().then((res) => {
      globalScenarioType.value = normalizeScenarioType(res.scenarioType)
      scenarioSettingLoaded = true
    }).finally(() => {
      loading.scenario = false
    })
  }

  async function handleScenarioChange(value) {
    const nextScenarioType = normalizeScenarioType(value)
    if (nextScenarioType === globalScenarioType.value) return
    if (sheetChangedCount.value > 0) {
      proxy.$modal.msgWarning('当前IP配置还有未保存内容，请先保存或重载后再切换使用场景')
      return
    }
    const nextLabel = SCENARIO_TYPE_OPTIONS.find((item) => item.value === nextScenarioType)?.label || nextScenarioType
    try {
      await proxy.$modal.confirm(`切换为“${nextLabel}”后，全部网段将统一使用该场景，是否继续？`)
    } catch {
      return
    }
    loading.scenario = true
    try {
      await updateScenarioSetting({ scenarioType: nextScenarioType })
      globalScenarioType.value = nextScenarioType
      scenarioSettingLoaded = true
      if (nextScenarioType === 'INTERNAL') {
        ledgerQuery.internalIpAddress = null
      }
      proxy.$modal.msgSuccess(`已切换为${nextLabel}`)
      await loadNetworks()
    } catch {
      // 请求层已统一提示错误，这里只保持原场景不变。
    } finally {
      loading.scenario = false
    }
  }

  function loadNetworks() {
    loading.network = true
    return listNetworkTree(networkQuery).then((res) => {
      const previousNetworkId = selectedNetworkId.value
      const networkRows = Array.isArray(res.data) ? res.data : (res.rows || [])
      networkList.value = networkRows.map((item) => ({
        ...item,
        subnetMask: item.subnetMask || prefixLengthToSubnetMask(item.prefixLength)
      }))
      syncStationNameCatalog(networkList.value)
      if (!scenarioSettingLoaded && networkList.value[0]?.scenarioType) {
        globalScenarioType.value = normalizeScenarioType(networkList.value[0].scenarioType)
      }
      networkTotal.value = networkList.value.length
      if (!networkList.value.length) {
        if (sheetChangedCount.value > 0 && selectedNetworkId.value) return
        selectedNetworkId.value = null
        selectedSegment.value = null
        addressRows.value = []
        selectedIpList.value = []
        sheetRows.value = []
        ledgerList.value = []
        networkTotal.value = 0
        resetGridPage()
        return
      }
      const selectedVisible = networkList.value.some((item) => item.networkId === selectedNetworkId.value)
      if (!selectedNetworkId.value || (!selectedVisible && sheetChangedCount.value === 0)) {
        selectedNetworkId.value = networkList.value[0].networkId
      }
      if (!networkList.value.some((item) => item.networkId === selectedNetworkId.value)) return loadLedger()
      if (previousNetworkId && previousNetworkId !== selectedNetworkId.value) {
        clearScanPoll()
        addressRows.value = []
        clearSelectedIps(true)
        scanJob.value = null
        resetGridPage()
      }
      syncSelectedNetworkFilters()
      syncManagedNetwork()
      return loadLedger()
    }).finally(() => {
      loading.network = false
    })
  }

  function syncStationNameCatalog(networks) {
    const incomingNames = networks
      .map((item) => String(item?.policeStationName || '').trim())
      .filter(Boolean)
    const sourceNames = networkQuery.keyword
      ? [...stationNameCatalog.value, ...incomingNames]
      : incomingNames
    stationNameCatalog.value = [...new Set(sourceNames)].sort((left, right) => left.localeCompare(right, 'zh-CN'))
  }

  function syncSelectedNetworkFilters() {
    ledgerQuery.networkId = selectedNetworkId.value
    if (!showInternalIpField.value) {
      ledgerQuery.internalIpAddress = null
    }
    if (!selectedNetworkId.value) {
      selectedSegment.value = null
    }
  }

  function syncManagedNetwork(row = selectedNetwork.value) {
    if (!row || Number(row.segmentCount) !== 1 || !row.segmentId) {
      selectedSegment.value = null
      return
    }
    selectedSegment.value = {
      segmentId: row.segmentId,
      networkId: row.networkId,
      segmentName: row.networkName,
      startIp: row.startIp,
      endIp: row.endIp,
      gatewayIp: row.gatewayIp,
      subnetMask: row.subnetMask || prefixLengthToSubnetMask(row.prefixLength),
      prefixLength: row.prefixLength,
      totalCount: row.totalCount,
      status: row.status,
      reservedCount: row.reservedCount,
      allocatedCount: row.allocatedCount,
      issuedCount: row.issuedCount,
      disabledCount: row.disabledCount,
      freeCount: row.freeCount,
      remark: row.remark
    }
  }

  async function selectNetwork(row) {
    if (!row || row.networkId === selectedNetworkId.value) return
    if (!await confirmDiscardChanges('切换网段')) return
    clearScanPoll()
    loading.scan = false
    selectedNetworkId.value = row.networkId
    selectedSegment.value = null
    selectedSheetIp.value = null
    addressRows.value = []
    clearSelectedIps(true)
    scanJob.value = null
    resetGridPage()
    configFullscreen.value = false
    activeTab.value = 'network'
    syncSelectedNetworkFilters()
    syncManagedNetwork(row)
    loadLedger()
  }

  function openNetworkConfig() {
    if (!selectedSegment.value?.segmentId) {
      proxy.$modal.msgWarning('当前网段尚未完成单层结构初始化')
      return
    }
    if (!selectedSegment.value.gatewayIp || !selectedSegment.value.subnetMask) {
      proxy.$modal.msgWarning('请先编辑网段并配置网关IP和子网掩码')
      return
    }
    if (!sheetRows.value.length) {
      selectedSheetIp.value = null
      selectedIpList.value = []
    }
    configFullscreen.value = false
    activeTab.value = 'config'
    resetGridPage()
    loadAddressGrid()
    loadLatestNetworkScanJob()
  }

  function loadAddressGrid() {
    if (!selectedSegment.value?.segmentId) return Promise.resolve()
    loading.grid = true
    return getAddressGrid(selectedNetworkId.value, gridPage.pageNum, gridPage.pageSize).then((res) => {
      const data = res.data || {}
      selectedSegment.value = data.segment
        ? {
            ...data.segment,
            segmentName: selectedNetwork.value?.networkName || data.segment.segmentName,
            subnetMask: selectedNetwork.value?.subnetMask || prefixLengthToSubnetMask(data.segment.prefixLength)
          }
        : selectedSegment.value
      const rows = data.rows || []
      addressRows.value = rows
      syncSheetRowsFromSelection(true)
      Object.assign(gridPage, {
        pageNum: Number(data.pageNum || gridPage.pageNum),
        pageSize: Number(data.pageSize || gridPage.pageSize),
        pageCount: Number(data.pageCount || 1),
        rangeStartIp: data.rangeStartIp || rows[0]?.ipAddress || null,
        rangeEndIp: data.rangeEndIp || rows[rows.length - 1]?.ipAddress || null
      })
      Object.assign(gridSummary, {
        total: data.summary?.total || 0,
        FREE: data.summary?.FREE || 0,
        RESERVED: data.summary?.RESERVED || 0,
        ALLOCATED: data.summary?.ALLOCATED || 0,
        ISSUED: data.summary?.ISSUED || 0,
        DISABLED: data.summary?.DISABLED || 0
      })
    }).finally(() => {
      loading.grid = false
    })
  }

  function startNetworkScan() {
    if (!selectedNetworkId.value || !selectedSegment.value?.segmentId) {
      proxy.$modal.msgWarning('请先选择一个网段')
      return
    }
    if (sheetChangedCount.value > 0) {
      proxy.$modal.msgWarning('当前IP配置还有未保存内容，请先保存或重载后再扫描')
      return
    }
    if (isActiveScanJob(scanJob.value)) {
      proxy.$modal.msgWarning('当前网段正在扫描')
      return
    }

    loading.scan = true
    startNetworkScanRequest(selectedNetworkId.value).then((res) => {
      scanJob.value = res.data || null
      if (!scanJob.value?.scanId) {
        throw new Error('扫描任务创建失败')
      }
      proxy.$modal.msgSuccess('网段扫描已开始')
      scheduleScanPoll(scanJob.value.scanId, true)
    }).catch(() => {
      loading.scan = false
    })
  }

  function loadLatestNetworkScanJob() {
    clearScanPoll()
    if (!selectedNetworkId.value) {
      scanJob.value = null
      loading.scan = false
      return Promise.resolve()
    }
    const networkId = selectedNetworkId.value
    return getLatestNetworkScan(networkId).then((res) => {
      if (selectedNetworkId.value !== networkId) return
      scanJob.value = res.data || null
      loading.scan = isActiveScanJob(scanJob.value)
      if (loading.scan && scanJob.value?.scanId) {
        scheduleScanPoll(scanJob.value.scanId, false)
      }
    }).catch(() => {
      if (selectedNetworkId.value === networkId) {
        scanJob.value = null
        loading.scan = false
      }
    })
  }

  function scheduleScanPoll(scanId, notifyWhenFinished) {
    clearScanPoll()
    scanPollTimer = window.setTimeout(() => {
      pollScanJob(scanId, notifyWhenFinished)
    }, 1500)
  }

  function pollScanJob(scanId, notifyWhenFinished) {
    getScanJob(scanId).then((res) => {
      const nextJob = res.data || null
      if (!nextJob) {
        loading.scan = false
        return
      }
      if (nextJob.networkId && nextJob.networkId !== selectedNetworkId.value) {
        loading.scan = false
        return
      }
      scanJob.value = nextJob
      if (isActiveScanJob(nextJob)) {
        loading.scan = true
        scheduleScanPoll(scanId, notifyWhenFinished)
        return
      }

      loading.scan = false
      loadAddressGrid()
      if (notifyWhenFinished) {
        notifyScanFinished(nextJob)
      }
    }).catch(() => {
      loading.scan = false
      clearScanPoll()
    })
  }

  function notifyScanFinished(job) {
    if (job.scanStatus === 'COMPLETED') {
      proxy.$modal.msgSuccess(`扫描完成：在线 ${job.onlineCount || 0}，离线 ${job.offlineCount || 0}`)
      return
    }
    if (job.scanStatus === 'PARTIAL') {
      proxy.$modal.msgWarning(`扫描完成，存在 ${job.errorCount || 0} 个检测异常`)
      return
    }
    proxy.$modal.msgError(job.errorMessage || '网段扫描失败')
  }

  function isActiveScanJob(job) {
    return ['QUEUED', 'RUNNING'].includes(job?.scanStatus)
  }

  function clearScanPoll() {
    if (scanPollTimer != null) {
      window.clearTimeout(scanPollTimer)
      scanPollTimer = null
    }
  }

  function changeGridPage(pageNum) {
    gridPage.pageNum = Number(pageNum || 1)
    finishGridSelection()
    return loadAddressGrid()
  }

  function changeGridPageSize(pageSize) {
    gridPage.pageSize = Number(pageSize || 256)
    gridPage.pageNum = 1
    finishGridSelection()
    return loadAddressGrid()
  }

  function resetGridPage() {
    Object.assign(gridPage, {
      pageNum: 1,
      pageSize: 256,
      pageCount: 1,
      rangeStartIp: null,
      rangeEndIp: null
    })
  }

  function loadLedger() {
    loading.ledger = true
    return listAddress(ledgerQuery).then((res) => {
      ledgerList.value = res.rows || []
      ledgerTotal.value = res.total || 0
    }).finally(() => {
      loading.ledger = false
    })
  }

  function handleNetworkQuery() {
    if (networkSearchTimer) {
      clearTimeout(networkSearchTimer)
      networkSearchTimer = null
    }
    networkQuery.keyword = String(networkQuery.keyword || '').trim() || null
    return loadNetworks()
  }

  function handleNetworkSearchInput() {
    if (networkSearchTimer) clearTimeout(networkSearchTimer)
    networkSearchTimer = setTimeout(() => {
      handleNetworkQuery()
    }, 280)
  }

  function resetNetworkQuery() {
    if (networkSearchTimer) {
      clearTimeout(networkSearchTimer)
      networkSearchTimer = null
    }
    Object.assign(networkQuery, { keyword: null })
    return loadNetworks()
  }

  function handleLedgerQuery() {
    ledgerQuery.pageNum = 1
    loadLedger()
  }

  function resetLedgerQuery() {
    Object.assign(ledgerQuery, {
      pageNum: 1,
      pageSize: 10,
      networkId: selectedNetworkId.value,
      status: null,
      communityName: null,
      targetType: null,
      manufacturer: null,
      internalIpAddress: null,
      accessUnit: null
    })
    loadLedger()
  }

  function resetNetworkForm() {
    Object.assign(networkForm, {
      networkId: null,
      networkName: null,
      policeStationName: null,
      gatewayIp: '2.57.1.1',
      subnetMask: '255.255.255.0',
      status: '0',
      remark: null
    })
    nextTick(() => networkRef.value?.clearValidate())
  }

  function openAddNetwork() {
    resetNetworkForm()
    networkTitle.value = '新增网段'
    networkOpen.value = true
  }

  function openEditNetwork(row) {
    if (Number(row.segmentCount) !== 1 || !row.segmentId) {
      proxy.$modal.msgWarning('该历史网段包含多个地址池，请拆分为独立网段后再编辑')
      return
    }
    Object.assign(networkForm, {
      networkId: row.networkId,
      networkName: row.networkName,
      policeStationName: row.policeStationName,
      gatewayIp: row.gatewayIp || null,
      subnetMask: row.subnetMask || prefixLengthToSubnetMask(row.prefixLength),
      status: row.status || '0',
      remark: row.remark
    })
    networkTitle.value = '编辑网段'
    networkOpen.value = true
  }

  function submitNetwork() {
    networkRef.value?.validate((valid) => {
      if (!valid) return
      loading.submit = true
      const req = networkForm.networkId ? updateNetwork(networkForm) : addNetwork(networkForm)
      req.then(() => {
        proxy.$modal.msgSuccess(networkForm.networkId ? '网段已更新' : '网段已创建')
        if (!networkForm.networkId) {
          selectedNetworkId.value = null
        }
        networkOpen.value = false
        loadNetworks()
      }).finally(() => {
        loading.submit = false
      })
    })
  }

  async function removeNetwork(row) {
    if (row.networkId === selectedNetworkId.value && !await confirmDiscardChanges('删除当前网段')) return
    proxy.$modal.confirm(`是否确认删除网段“${row.networkName || formatNetworkRange(row)}”？`).then(() => delNetwork(row.networkId)).then(() => {
      proxy.$modal.msgSuccess('删除成功')
      if (row.networkId === selectedNetworkId.value) clearSelectedIps(true)
      selectedNetworkId.value = null
      loadNetworks()
    }).catch(() => {})
  }

  function startGridSelection(row) {
    if (!canSelectGridAddress(row)) return
    if (selectionDragging.value) finishGridSelection()
    selectionDragStartIp.value = row.ipAddress
    selectionDragging.value = true
    selectionDragBaseSet = new Set(selectedIpList.value)
    selectionDragShouldSelect = !selectionDragBaseSet.has(row.ipAddress)
    selectionProtectedDirty = false
    selectionCapped = false
    selectionPreviewSet.value = new Set(selectionDragBaseSet)
    selectionPendingEndIp = row.ipAddress
    applyGridSelectionPreview(row.ipAddress)
    window.addEventListener('mouseup', finishGridSelection, { once: true })
  }

  function extendGridSelection(row) {
    if (!selectionDragging.value || !selectionDragStartIp.value || !canSelectGridAddress(row)) return
    if (selectionPendingEndIp === row.ipAddress) return
    selectionPendingEndIp = row.ipAddress
    if (selectionFrameId != null) return
    selectionFrameId = window.requestAnimationFrame(flushGridSelectionPreview)
  }

  function finishGridSelection() {
    if (!selectionDragging.value) return
    if (selectionFrameId != null) {
      window.cancelAnimationFrame(selectionFrameId)
      selectionFrameId = null
    }
    if (selectionPendingEndIp) {
      applyGridSelectionPreview(selectionPendingEndIp)
    }
    selectedIpList.value = sortIps(Array.from(selectionPreviewSet.value))
    selectionDragging.value = false
    selectionDragStartIp.value = null
    selectionDragBaseSet = new Set()
    selectionPreviewSet.value = new Set()
    selectionPendingEndIp = null
    window.removeEventListener('mouseup', finishGridSelection)
    syncSheetRowsFromSelection()
    if (selectionProtectedDirty) {
      proxy.$modal.msgWarning('未保存的配置行已保留，请保存或重载后再移出')
    } else if (selectionCapped) {
      proxy.$modal.msgWarning(`单次最多配置 ${MAX_CONFIG_SELECTION} 个IP，请分批保存`)
    }
  }

  function canSelectGridAddress(row) {
    if (!row || row.boundaryAddress || row.reservedReason === '网关') {
      return false
    }
    return true
  }

  function flushGridSelectionPreview() {
    selectionFrameId = null
    if (!selectionDragging.value || !selectionPendingEndIp) return
    applyGridSelectionPreview(selectionPendingEndIp)
  }

  function applyGridSelectionPreview(endIp) {
    const startIndex = addressRows.value.findIndex((row) => row.ipAddress === selectionDragStartIp.value)
    const endIndex = addressRows.value.findIndex((row) => row.ipAddress === endIp)
    if (startIndex < 0 || endIndex < 0) return
    const next = appendSelectionRange(
      selectionDragBaseSet,
      addressRows.value,
      startIndex,
      endIndex,
      selectionDragShouldSelect,
      canSelectGridAddress
    )
    if (selectionDragShouldSelect && next.size >= MAX_CONFIG_SELECTION) {
      const lower = Math.min(startIndex, endIndex)
      const upper = Math.max(startIndex, endIndex)
      selectionCapped = addressRows.value.slice(lower, upper + 1)
        .some((row) => canSelectGridAddress(row) && !next.has(row.ipAddress))
    }
    if (!selectionDragShouldSelect) {
      sheetRows.value.filter((row) => row._dirty).forEach((row) => {
        if (!next.has(row.ipAddress) && selectionDragBaseSet.has(row.ipAddress)) {
          next.add(row.ipAddress)
          selectionProtectedDirty = true
        }
      })
    }
    selectionPreviewSet.value = next
    selectedSheetIp.value = endIp
  }

  async function removeSelectedIp(ipAddress) {
    const row = sheetRows.value.find((item) => item.ipAddress === ipAddress)
    if (row?._dirty && !await confirmDiscardChanges(`移出 ${ipAddress}`)) return
    selectedIpList.value = selectedIpList.value.filter((item) => item !== ipAddress)
    if (selectedSheetIp.value === ipAddress) {
      selectedSheetIp.value = selectedIpList.value[0] || null
    }
    syncSheetRowsFromSelection()
  }

  async function clearSelectedIps(force = false) {
    if (!force && !await confirmDiscardChanges('清空选择')) return false
    selectedIpList.value = []
    selectedSheetIp.value = null
    sheetRows.value = []
    return true
  }

  function openConfigFullscreen() {
    if (!selectedSegment.value?.segmentId) {
      proxy.$modal.msgWarning('请先选择一个网段')
      return
    }
    configFullscreen.value = true
  }

  function closeConfigFullscreen() {
    configFullscreen.value = false
  }

  function setGridDisplayMode(mode) {
    gridDisplayMode.value = mode === 'detailed' ? 'detailed' : 'compact'
  }

  function isGridSelected(ipAddress) {
    const activeSet = selectionDragging.value ? selectionPreviewSet.value : selectedIpSet.value
    return activeSet.has(ipAddress)
  }

  function toggleGridSelection(row) {
    if (!canSelectGridAddress(row)) return
    if (selectedIpSet.value.has(row.ipAddress)) {
      removeSelectedIp(row.ipAddress)
      return
    }
    selectIps([row.ipAddress])
  }

  function syncSheetRowsFromSelection(preserveCurrent = true) {
    if (!selectedIpList.value.length) {
      sheetRows.value = []
      return
    }
    const currentMap = preserveCurrent ? new Map(sheetRows.value.map((row) => [row.ipAddress, row])) : new Map()
    const sourceMap = new Map(addressRows.value.map((row) => [row.ipAddress, row]))
    sheetRows.value = selectedIpList.value.map((ipAddress) => {
      return currentMap.get(ipAddress) || normalizeSheetRow(sourceMap.get(ipAddress))
    }).filter(Boolean)
  }

  function selectIps(ipAddresses) {
    const next = new Set(selectedIpList.value)
    let capped = false
    ipAddresses.forEach((ipAddress) => {
      const row = addressRows.value.find((item) => item.ipAddress === ipAddress)
      if (canSelectGridAddress(row)) {
        if (!next.has(ipAddress) && next.size >= MAX_CONFIG_SELECTION) {
          capped = true
          return
        }
        next.add(ipAddress)
      }
    })
    selectedIpList.value = sortIps(Array.from(next))
    syncSheetRowsFromSelection()
    if (capped) proxy.$modal.msgWarning(`单次最多配置 ${MAX_CONFIG_SELECTION} 个IP，请分批保存`)
  }

  function normalizeSheetRow(row) {
    if (!row) return null
    const reservedReason = row.reservedReason || null
    const readonly = Boolean(row.boundaryAddress) || reservedReason === '网关'
    return {
      addressId: row.addressId || null,
      networkId: row.networkId || selectedNetworkId.value,
      segmentId: row.segmentId || selectedSegment.value?.segmentId,
      ipAddress: row.ipAddress,
      lastOctet: row.lastOctet,
      status: row.status || 'FREE',
      communityName: row.communityName || null,
      targetType: row.targetType || null,
      targetName: row.targetName || null,
      manufacturer: row.manufacturer || null,
      internalIpAddress: row.internalIpAddress || null,
      accessUnit: row.accessUnit || null,
      purpose: row.purpose || null,
      loginUsername: row.loginUsername || null,
      loginPassword: row.loginPassword || null,
      credentialConfigured: Boolean(row.credentialConfigured || row.loginPassword),
      mappingAddress: row.mappingAddress || null,
      mappingPort: row.mappingPort || null,
      mappingDescription: row.mappingDescription || null,
      ownerName: row.ownerName || null,
      ownerPhone: row.ownerPhone || null,
      remark: row.remark || null,
      boundaryAddress: Boolean(row.boundaryAddress),
      reservedReason,
      _readonly: readonly,
      _credentialLoading: false,
      _revealedPassword: null,
      _dirty: false,
      _error: null
    }
  }

  function handleSheetStatusChange(row) {
    if (row._readonly) {
      row.status = 'RESERVED'
      row._dirty = false
      row._error = null
      return
    }
    if (row.status === 'FREE') {
      clearAddressBusinessFields(row)
    }
    markSheetRowDirty(row)
  }

  function markSheetRowDirty(row, autoAllocate = false) {
    if (row._readonly) return
    if (autoAllocate && row.status === 'FREE') {
      row.status = 'ALLOCATED'
    }
    row._dirty = true
    validateSheetRow(row)
  }

  function validateSheetRow(row) {
    if (row._readonly) {
      row._error = row.status === 'RESERVED' ? null : '保留地址'
      return !row._error
    }
    if (row.status === 'ALLOCATED' || row.status === 'ISSUED') {
      if (isBlank(row.communityName)) {
        row._error = `缺${subjectNameLabel.value}`
        return false
      }
    }
    row._error = null
    return true
  }

  function clearAddressBusinessFields(row) {
    BUSINESS_FIELDS.forEach((key) => {
      row[key] = null
    })
  }

  function assignSheetValue(row, field, value) {
    const nextValue = isBlank(value) ? null : value
    if (row[field] === nextValue) return false
    row[field] = nextValue
    return true
  }

  async function generateDownwardRows(template) {
    const templateIndex = sheetRows.value.findIndex((row) => row.ipAddress === template?.ipAddress)
    if (templateIndex < 0) {
      proxy.$modal.msgWarning('未找到生成起始行，请重新选择')
      return
    }
    if (template._readonly) {
      proxy.$modal.msgWarning('保留地址不能作为生成起始行')
      return
    }
    const rowsBelow = sheetRows.value.slice(templateIndex + 1)
    if (!rowsBelow.length) {
      proxy.$modal.msgWarning('当前行下方没有可填充的IP')
      return
    }
    const editableRowsBelow = rowsBelow.filter((row) => !row._readonly)
    if (!editableRowsBelow.length) {
      proxy.$modal.msgWarning('当前行下方没有可填充的IP')
      return
    }
    const hasContentBelow = editableRowsBelow.some((row) => DOWNWARD_FILL_FIELDS.some((field) => {
      if (field === 'internalIpAddress' && !showInternalIpField.value) return false
      return !isBlank(row[field])
    }))
    if (hasContentBelow) {
      try {
        await proxy.$modal.confirm('下方已有内容，是否覆盖')
      } catch {
        return
      }
    }

    const startInternalIp = showInternalIpField.value ? normalizeIp(template.internalIpAddress) : null
    const startInternalValue = startInternalIp ? ipToLong(startInternalIp) : null
    let affected = 0
    let internalSkipped = false

    editableRowsBelow.forEach((row, index) => {
      const offset = index + 1
      let changed = false

      if (hasContentBelow) {
        changed = assignSheetValue(row, 'communityName', template.communityName) || changed
      } else if (!isBlank(template.communityName) && row.communityName !== template.communityName) {
        row.communityName = template.communityName
        changed = true
      }

      if (showInternalIpField.value && startInternalValue != null) {
        const nextInternalIp = longToIp(startInternalValue + offset)
        changed = assignSheetValue(row, 'internalIpAddress', nextInternalIp) || changed
      } else if (showInternalIpField.value && !isBlank(template.internalIpAddress)) {
        internalSkipped = true
      } else if (showInternalIpField.value && hasContentBelow) {
        changed = assignSheetValue(row, 'internalIpAddress', null) || changed
      }

      TEMPLATE_COPY_FIELDS.forEach((field) => {
        const templateValue = field === 'targetName' ? buildIncrementedDeviceName(template.targetName, offset) : template[field]
        if (hasContentBelow) {
          changed = assignSheetValue(row, field, templateValue) || changed
        } else if (isBlank(row[field]) && !isBlank(templateValue)) {
          row[field] = templateValue
          changed = true
        }
      })

      if (changed) {
        markSheetRowDirty(row, true)
        affected++
      }
    })

    if (!affected) {
      proxy.$modal.msgWarning('当前行下方没有需要填充的内容')
      return
    }
    if (internalSkipped) {
      proxy.$modal.msgWarning(`已从 ${template.ipAddress} 向下生成 ${affected} 行，起始行内网IP格式不正确，已跳过内网IP递增`)
      return
    }
    proxy.$modal.msgSuccess(`已从 ${template.ipAddress} 向下生成 ${affected} 行`)
  }

  function submitConfigSheet() {
    if (!selectedSegment.value?.segmentId) {
      proxy.$modal.msgWarning('请先选择一个网段')
      return
    }
    const dirtyRows = sheetRows.value.filter((row) => row._dirty)
    if (!dirtyRows.length) {
      proxy.$modal.msgWarning('没有需要保存的改动')
      return
    }
    const valid = dirtyRows.every(validateSheetRow)
    if (!valid) {
      proxy.$modal.msgWarning('请先处理校验提示')
      return
    }
    loading.submit = true
    commitConfigSheet({
      networkId: selectedNetworkId.value,
      rows: dirtyRows.map(stripSheetMeta)
    }).then(() => {
      proxy.$modal.msgSuccess('配置已保存')
      clearSelectedIps(true)
      refreshAddressViews()
    }).finally(() => {
      loading.submit = false
    })
  }

  async function loadAddressCredential(row) {
    if (!row?.addressId || !row.credentialConfigured) return null
    row._credentialLoading = true
    try {
      const response = await getAddressCredential(row.addressId)
      return storeRevealedCredential(row, response?.password)
    } finally {
      row._credentialLoading = false
    }
  }

  function stripSheetMeta(row) {
    return {
      addressId: row.addressId,
      ipAddress: row.ipAddress,
      status: row.status,
      communityName: row.communityName,
      targetType: row.targetType,
      targetName: row.targetName,
      manufacturer: row.manufacturer,
      internalIpAddress: row.internalIpAddress,
      accessUnit: row.accessUnit,
      purpose: row.purpose,
      loginUsername: row.loginUsername,
      loginPassword: row.loginPassword,
      mappingAddress: row.mappingAddress,
      mappingPort: row.mappingPort,
      mappingDescription: row.mappingDescription,
      ownerName: row.ownerName,
      ownerPhone: row.ownerPhone,
      remark: row.remark
    }
  }

  async function resetConfigSheet() {
    if (!await confirmDiscardChanges('重载配置')) return
    await clearSelectedIps(true)
    return loadAddressGrid()
  }

  async function confirmDiscardChanges(actionLabel) {
    if (sheetChangedCount.value === 0) return true
    try {
      await proxy.$modal.confirm(`当前有 ${sheetChangedCount.value} 行未保存，${actionLabel}后这些内容会丢失，是否继续？`)
      return true
    } catch {
      return false
    }
  }

  function getSheetRowClassName({ row }) {
    if (row._readonly) return 'sheet-row-readonly'
    if (row._error) return 'sheet-row-error'
    if (row._dirty) return 'sheet-row-dirty'
    if (selectedSheetIp.value === row.ipAddress) return 'sheet-row-selected'
    return ''
  }

  function openPasteDialog() {
    if (!selectedSegment.value?.segmentId) {
      proxy.$modal.msgWarning('请先选择一个网段')
      return
    }
    pasteText.value = ''
    pasteOpen.value = true
  }

  function applyPasteRows() {
    const parsedRows = parsePasteText(pasteText.value)
    if (!parsedRows.length) {
      proxy.$modal.msgWarning('没有识别到可导入的行')
      return
    }
    selectIps(parsedRows.map((item) => normalizeIp(item.ipAddress)).filter(Boolean))
    const rowMap = new Map(sheetRows.value.map((row) => [row.ipAddress, row]))
    let applied = 0
    let skipped = 0
    parsedRows.forEach((item) => {
      const ipAddress = normalizeIp(item.ipAddress)
      const target = ipAddress ? rowMap.get(ipAddress) : null
      if (!target || target._readonly) {
        skipped++
        return
      }
      Object.keys(item).forEach((key) => {
        if (key === 'ipAddress' || item[key] == null) return
        target[key] = item[key]
      })
      if (!item.status) {
        target.status = 'ALLOCATED'
      }
      markSheetRowDirty(target)
      applied++
    })
    pasteOpen.value = false
    proxy.$modal.msgSuccess(`已写入 ${applied} 行${skipped ? `，跳过 ${skipped} 行` : ''}`)
  }

  function parsePasteText(text) {
    const rows = String(text || '').split(/\r?\n/).map((line) => line.split('\t').map((cell) => cell.trim())).filter((cells) => cells.some(Boolean))
    if (!rows.length) return []
    let keys = DEFAULT_PASTE_KEYS
    const headerKeys = rows[0].map(resolveHeaderKey)
    if (headerKeys.filter(Boolean).length >= 2 || headerKeys[0] === 'ipAddress') {
      keys = headerKeys
      rows.shift()
    }
    return rows.map((cells) => {
      const item = {}
      cells.forEach((value, index) => {
        const key = keys[index]
        if (!key || !value) return
        if (key === 'targetType') {
          item[key] = resolveTargetType(value)
          return
        }
        if (key === 'manufacturer') {
          item[key] = resolveManufacturer(value)
          return
        }
        if (key === 'status') {
          item[key] = resolveStatus(value)
          return
        }
        item[key] = value
      })
      return item
    }).filter((item) => item.ipAddress)
  }

  function resolveHeaderKey(label) {
    const text = normalizeHeader(label)
    if (!text) return null
    return HEADER_KEYWORDS.find((item) => item.words.includes(text))?.key || null
  }

  function normalizeHeader(label) {
    return String(label || '').toLowerCase().replace(/\s+/g, '').replace(/[()（）:：]/g, '')
  }

  function resolveTargetType(label) {
    const text = String(label || '').trim()
    return DEVICE_TYPE_OPTIONS.find((item) => item.value === text || item.label === text)?.value || 'OTHER'
  }

  function resolveManufacturer(label) {
    const text = String(label || '').trim()
    return MANUFACTURER_OPTIONS.find((item) => item.value === text || item.label === text)?.value || text
  }

  function resolveStatus(label) {
    const text = String(label || '').trim()
    return statusOptions.find((item) => item.value === text || item.label === text)?.value || 'ALLOCATED'
  }

  function exportLedger() {
    proxy.download('/ipam/address/export', { ...ledgerQuery }, `IP地址台账_${Date.now()}.xlsx`)
  }

  function refreshAddressViews() {
    const tasks = [loadLedger()]
    if (selectedSegment.value?.segmentId) {
      tasks.push(loadAddressGrid())
    }
    return Promise.all(tasks).then(() => loadNetworks())
  }

  function getUsagePercent(row) {
    const capacity = getAssignableCapacity(row)
    if (!capacity) return 0
    const occupied = Number(row.allocatedCount || 0) + Number(row.issuedCount || 0)
    return Math.min(100, Math.round((occupied / capacity) * 100))
  }

  function getAssignableCapacity(row) {
    return Math.max(Number(row?.totalCount || 0) - 3, 0)
  }

  function formatNetworkRange(row) {
    if (!row?.startIp || !row?.endIp) return '待计算'
    return `${row.startIp} - ${row.endIp}`
  }

  function getNetworkRangeLabel(networkId) {
    return formatNetworkRange(networkList.value.find((item) => item.networkId === networkId))
  }

  function buildNetworkPreview(gatewayIp, subnetMask) {
    const parsedMask = parseSubnetMask(subnetMask)
    const gatewayValue = ipToLong(gatewayIp)
    if (!parsedMask || parsedMask.prefix < 1 || parsedMask.prefix > 30 || gatewayValue == null) {
      return { valid: false }
    }
    const start = (gatewayValue & parsedMask.value) >>> 0
    const end = (start | parsedMask.inverse) >>> 0
    const gatewayValid = gatewayValue > start && gatewayValue < end
    if (!gatewayValid) return { valid: false }
    const totalCount = end - start + 1
    return {
      valid: true,
      networkAddress: longToIp(start),
      broadcastAddress: longToIp(end),
      gatewayIp: longToIp(gatewayValue),
      subnetMask: longToIp(parsedMask.value),
      totalCount,
      assignableCount: Math.max(totalCount - 3, 0)
    }
  }

  function validateGatewayIp(_rule, value, callback) {
    const gatewayValue = ipToLong(value)
    if (gatewayValue == null) {
      callback(new Error('请输入完整的IPv4网关地址'))
      return
    }
    const parsedMask = parseSubnetMask(networkForm.subnetMask)
    if (parsedMask && parsedMask.prefix >= 1 && parsedMask.prefix <= 30) {
      const start = (gatewayValue & parsedMask.value) >>> 0
      const end = (start | parsedMask.inverse) >>> 0
      if (gatewayValue === start || gatewayValue === end) {
        callback(new Error('网关IP不能使用网络地址或广播地址'))
        return
      }
    }
    callback()
  }

  function validateSubnetMask(_rule, value, callback) {
    if (ipToLong(value) == null) {
      callback(new Error('请输入完整的IPv4子网掩码'))
      return
    }
    const parsed = parseSubnetMask(value)
    if (!parsed) {
      callback(new Error('子网掩码必须由连续的1和连续的0组成'))
      return
    }
    if (parsed.prefix < 1 || parsed.prefix > 30) {
      callback(new Error('子网掩码必须保留网络地址、广播地址和至少一个可分配地址'))
      return
    }
    callback()
  }

  function prefixLengthToSubnetMask(prefixLength) {
    const prefix = Number(prefixLength)
    if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) return null
    const value = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0
    return longToIp(value)
  }

  function normalizeScenarioType(value) {
    return String(value || '').toUpperCase() === 'INTERNAL' ? 'INTERNAL' : 'SOCIAL'
  }

  function parseSubnetMask(subnetMask) {
    const value = ipToLong(subnetMask)
    if (value == null) return null
    const inverse = (~value) >>> 0
    if ((inverse & ((inverse + 1) >>> 0)) !== 0) return null
    let cursor = value >>> 0
    let prefix = 0
    while (cursor) {
      prefix += cursor & 1
      cursor >>>= 1
    }
    return { value: value >>> 0, inverse, prefix }
  }

  function normalizeIp(ip) {
    const value = ipToLong(ip)
    return value == null ? null : longToIp(value)
  }

  function sortIps(ipAddresses) {
    return ipAddresses.filter(Boolean).sort((left, right) => {
      return ipToLong(left) - ipToLong(right)
    })
  }

  function isBlank(value) {
    return value == null || String(value).trim() === ''
  }

  function clearLegacySensitiveRequestCache() {
    try {
      const cached = window.sessionStorage.getItem('sessionObj')
      if (cached?.includes('/ipam/config/commit') && cached.includes('loginPassword')) {
        window.sessionStorage.removeItem('sessionObj')
      }
    } catch {
      // 浏览器禁用会话存储时不影响IPAM业务。
    }
  }

  function ipToLong(ip) {
    const parts = String(ip || '').trim().split('.')
    if (parts.length !== 4) return null
    let result = 0
    for (const part of parts) {
      if (!/^\d+$/.test(part)) return null
      const value = Number(part)
      if (value < 0 || value > 255) return null
      result = ((result << 8) + value) >>> 0
    }
    return result >>> 0
  }

  function longToIp(value) {
    return `${(value >>> 24) & 255}.${(value >>> 16) & 255}.${(value >>> 8) & 255}.${value & 255}`
  }

  onBeforeUnmount(() => {
    if (networkSearchTimer) clearTimeout(networkSearchTimer)
    clearScanPoll()
    if (selectionFrameId != null) window.cancelAnimationFrame(selectionFrameId)
    window.removeEventListener('mouseup', finishGridSelection)
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })

  function handleBeforeUnload(event) {
    if (sheetChangedCount.value === 0) return
    event.preventDefault()
    event.returnValue = ''
  }

  window.addEventListener('beforeunload', handleBeforeUnload)

  onBeforeRouteLeave(async (_to, _from, next) => {
    next(await confirmDiscardChanges('离开IP配置页面'))
  })

  loadScenarioSetting().catch(() => {}).finally(() => loadNetworks())

  return {
    showSearch,
    activeTab,
    loading,
    networkRef,
    networkOpen,
    pasteOpen,
    configFullscreen,
    networkTitle,
    selectedNetworkId,
    selectedNetwork,
    selectedSegment,
    selectedSheetIp,
    scanJob,
    gridDisplayMode,
    selectedIpList,
    selectedIpCount,
    maxConfigSelection: MAX_CONFIG_SELECTION,
    networkList,
    networkTree,
    networkTotal,
    stationOptions,
    globalScenarioType,
    addressRows,
    sheetRows,
    sheetChangedCount,
    sheetErrorCount,
    ledgerList,
    ledgerTotal,
    pasteText,
    gridSummary,
    gridPage,
    networkQuery,
    ledgerQuery,
    networkForm,
    statusOptions,
    targetTypeOptions: DEVICE_TYPE_OPTIONS,
    manufacturerOptions: MANUFACTURER_OPTIONS,
    scenarioTypeOptions: SCENARIO_TYPE_OPTIONS,
    networkRules,
    networkPreview,
    currentScenarioType,
    isInternalScenario,
    subjectNameLabel,
    showInternalIpField,
    canEditNetwork,
    canConfigureNetwork,
    handleScenarioChange,
    loadNetworks,
    selectNetwork,
    openNetworkConfig,
    loadAddressGrid,
    loadLatestNetworkScanJob,
    startNetworkScan,
    changeGridPage,
    changeGridPageSize,
    loadLedger,
    handleNetworkQuery,
    handleNetworkSearchInput,
    resetNetworkQuery,
    handleLedgerQuery,
    resetLedgerQuery,
    openAddNetwork,
    openEditNetwork,
    submitNetwork,
    removeNetwork,
    startGridSelection,
    extendGridSelection,
    toggleGridSelection,
    removeSelectedIp,
    clearSelectedIps,
    openConfigFullscreen,
    closeConfigFullscreen,
    setGridDisplayMode,
    isGridSelected,
    generateDownwardRows,
    handleSheetStatusChange,
    markSheetRowDirty,
    submitConfigSheet,
    loadAddressCredential,
    resetConfigSheet,
    getSheetRowClassName,
    openPasteDialog,
    applyPasteRows,
    exportLedger,
    getStatusMeta,
    getTargetTypeLabel,
    getUsagePercent,
    getAssignableCapacity,
    formatNetworkRange,
    getNetworkRangeLabel
  }
}
