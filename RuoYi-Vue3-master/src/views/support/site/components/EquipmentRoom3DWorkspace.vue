<template>
  <section class="room3d-workspace" data-ui-guard="diagram" v-loading="loading">
    <!--
      THESIS: 用可操作的机房数字摆放图统一物理位置与网络上联，拒绝只看模型的展示型3D。
      OWN-WORLD: 继承若依与Element Plus中性色表面，设备类型、光口和电口只承担语义区分。
      STORY: 运维人员先定位机房和机柜，再查看U位设备、IP与上联交换机，并可直接修正摆放和链路。
      FIRST VIEWPORT: 左侧机房导航、中间全幅Three.js场景、右侧上下文检查器，核心操作始终留在顶部。
      FORM: 既有设备管理的Operate型专业可视化扩展；使用UIX-004拓扑例外。
      FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md
    -->
    <header class="room3d-header">
      <div class="room3d-heading">
        <h2>机房三维摆放图</h2>
        <p>{{ siteName || '当前现场' }} · 机柜、U位设备与物理上联实时联动</p>
      </div>
      <div class="room3d-controls">
        <span class="room3d-live-status" :class="{ 'has-error': liveSyncError }">
          <i></i>{{ liveSyncError ? '同步失败' : (syncing ? '同步中' : '10秒自动同步') }}
        </span>
        <el-select v-model="selectedRoomId" class="room3d-room-select" placeholder="选择机房" filterable>
          <el-option
            v-for="room in rooms"
            :key="room.roomId"
            :label="room.roomName"
            :value="room.roomId"
          />
        </el-select>
        <el-segmented
          v-model="workspaceMode"
          :options="workspaceModeOptions"
          :disabled="!selectedRoom"
          v-hasPermi="['support:hardwareAsset:edit']"
        />
        <label class="room3d-switch-label">
          <span>链路</span>
          <el-switch v-model="showLinks" />
        </label>
        <el-tooltip content="恢复鸟瞰视角" placement="bottom">
          <el-button icon="Aim" :disabled="!selectedRoom" aria-label="恢复鸟瞰视角" @click="resetCamera" />
        </el-tooltip>
        <el-tooltip content="刷新实时数据" placement="bottom">
          <el-button icon="Refresh" :loading="loading" aria-label="刷新实时数据" @click="loadTopology" />
        </el-tooltip>
        <el-button icon="Close" @click="emit('close')">关闭</el-button>
      </div>
    </header>

    <div v-if="loadError" class="room3d-error">
      <el-result icon="error" title="三维机房加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadTopology">重新加载</el-button>
        </template>
      </el-result>
    </div>

    <div v-else class="room3d-body">
      <aside class="room3d-room-panel">
        <div class="room3d-panel-title">
          <strong>机房</strong>
          <span>{{ rooms.length }} 个</span>
        </div>
        <el-menu
          v-if="rooms.length"
          :default-active="String(selectedRoomId || '')"
          class="room3d-room-menu"
          @select="handleRoomSelect"
        >
          <el-menu-item v-for="room in rooms" :key="room.roomId" :index="String(room.roomId)">
            <div class="room3d-room-menu__content">
              <strong>{{ room.roomName }}</strong>
              <span>{{ getRoomCabinets(room.roomId).length }} 柜 · {{ getRoomDeviceCount(room.roomId) }} 台</span>
            </div>
          </el-menu-item>
        </el-menu>
        <el-empty v-else description="暂无机房，请先在设备位置图中新增" :image-size="72" />

        <div class="room3d-unplaced">
          <div class="room3d-panel-title room3d-panel-title--sub">
            <strong>未上架设备</strong>
            <span>{{ unplacedDevices.length }} 台</span>
          </div>
          <el-scrollbar max-height="220px">
            <button
              v-for="device in unplacedDevices"
              :key="device.deviceKey"
              type="button"
              class="room3d-unplaced-item"
              :class="{ 'is-active': selectedDeviceKey === device.deviceKey }"
              @click="selectDevice(device)"
            >
              <i :style="{ background: getDeviceColor(device.assetType) }"></i>
              <span>
                <strong>{{ device.assetName || '未命名设备' }}</strong>
                <small>{{ device.ipAddress || '未填写IP' }}</small>
              </span>
            </button>
          </el-scrollbar>
        </div>
      </aside>

      <main class="room3d-scene-panel">
        <div class="room3d-scene-summary">
          <span><strong>{{ currentRoomCabinets.length }}</strong> 个机柜</span>
          <span><strong>{{ currentRoomDevices.length }}</strong> 台上架设备</span>
          <span><strong>{{ currentRoomUsedU }}</strong> / {{ currentRoomCapacityU }}U</span>
          <span><strong>{{ currentRoomLinks.length }}</strong> 条可视链路</span>
          <span v-if="currentRoomCollisionCount" class="room3d-scene-summary__warning">
            <strong>{{ currentRoomCollisionCount }}</strong> 处机柜冲突
          </span>
        </div>

        <div
          ref="sceneHost"
          class="room3d-scene"
          data-testid="equipment-room-3d-canvas"
          :class="{ 'is-layout-mode': workspaceMode === 'layout' }"
          tabindex="0"
          aria-label="机房三维摆放图。方向键切换机柜和设备，回车聚焦，正负号缩放，R恢复鸟瞰，Escape清除选择"
          @keydown="handleSceneKeydown"
        ></div>

        <div v-if="renderError" class="room3d-render-error">
          <el-result icon="warning" title="当前浏览器无法显示三维场景" :sub-title="renderError" />
        </div>
        <div v-else-if="selectedRoom && !currentRoomCabinets.length" class="room3d-empty-overlay">
          <el-empty description="当前机房还没有机柜，请先新增机柜" :image-size="88" />
        </div>
        <div v-else-if="!selectedRoom" class="room3d-empty-overlay">
          <el-empty description="请选择机房" :image-size="88" />
        </div>

        <div class="room3d-legend" aria-label="设备与链路图例">
          <span v-for="item in deviceLegend" :key="item.value">
            <i :style="{ background: item.color }"></i>{{ item.label }}
          </span>
          <span class="room3d-legend__line room3d-legend__line--optical">光口</span>
          <span class="room3d-legend__line room3d-legend__line--electrical">电口</span>
        </div>

        <div v-if="workspaceMode === 'layout'" class="room3d-layout-hint">
          拖动机柜调整位置，按 0.2 米网格自动吸附；鼠标松开后保存。
        </div>
      </main>

      <aside class="room3d-inspector">
        <template v-if="selectedDevice">
          <div class="room3d-inspector-head">
            <div>
              <span>{{ selectedDevice.assetTypeLabel || selectedDevice.assetType }}</span>
              <h3>{{ selectedDevice.assetName || '未命名设备' }}</h3>
            </div>
            <el-button link type="primary" @click="emit('edit-device', selectedDevice)">编辑设备</el-button>
          </div>

          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="设备IP">{{ selectedDevice.ipAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="管理IP">{{ selectedDevice.manageIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="安装位置">{{ formatDeviceLocation(selectedDevice) }}</el-descriptions-item>
            <el-descriptions-item label="运行状态">
              <el-tag :type="selectedDevice.status === '1' ? 'info' : 'success'" size="small">
                {{ selectedDevice.status === '1' ? '停用' : '正常' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <section class="room3d-port-summary">
            <div>
              <span>光口外联</span>
              <strong>{{ selectedPortSummary.optical }}</strong>
            </div>
            <div>
              <span>电口外联</span>
              <strong>{{ selectedPortSummary.electrical }}</strong>
            </div>
          </section>

          <section class="room3d-link-section">
            <div class="room3d-section-head">
              <div>
                <strong>上联关系</strong>
                <span>{{ selectedDeviceLinks.length }} 条</span>
              </div>
              <el-button
                type="primary"
                plain
                size="small"
                icon="Connection"
                :disabled="!switchOptions.length"
                v-hasPermi="['support:hardwareAsset:add', 'support:hardwareAsset:edit']"
                @click="openLinkForm()"
              >新增上联</el-button>
            </div>

            <div v-if="selectedDeviceLinks.length" class="room3d-link-list">
              <article v-for="link in selectedDeviceLinks" :key="link.linkId" class="room3d-link-row">
                <div>
                  <el-tag :type="link.mediumType === 'OPTICAL' ? 'primary' : 'warning'" size="small">
                    {{ formatMedium(link.mediumType) }} {{ link.portCount }}口
                  </el-tag>
                  <strong>{{ getLinkPeerName(link, selectedDevice) }}</strong>
                  <span>{{ formatLinkPorts(link) }}</span>
                </div>
                <div class="room3d-link-actions">
                  <el-button link type="primary" @click="openLinkForm(link)">编辑</el-button>
                  <el-button link type="danger" @click="removeLink(link)">删除</el-button>
                </div>
              </article>
            </div>
            <el-empty v-else description="尚未配置上联交换机" :image-size="64" />

            <p v-if="selectedDevice.legacyUplinkDevice" class="room3d-legacy-note">
              历史上联记录：{{ selectedDevice.legacyUplinkDevice }}
            </p>
          </section>
        </template>

        <template v-else-if="selectedCabinet">
          <div class="room3d-inspector-head">
            <div>
              <span>机柜</span>
              <h3>{{ selectedCabinet.cabinetNo }}</h3>
            </div>
            <el-button link type="primary" @click="focusCabinet(selectedCabinet.cabinetId)">聚焦</el-button>
          </div>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="机柜容量">{{ selectedCabinet.uCapacity || 45 }}U</el-descriptions-item>
            <el-descriptions-item label="设备数量">{{ selectedCabinetDevices.length }} 台</el-descriptions-item>
            <el-descriptions-item label="已使用U位">{{ getCabinetUsedU(selectedCabinet) }}U</el-descriptions-item>
            <el-descriptions-item label="平面坐标">
              X {{ formatMeter(selectedCabinet.positionX) }} / Z {{ formatMeter(selectedCabinet.positionZ) }}
            </el-descriptions-item>
            <el-descriptions-item label="朝向">{{ Number(selectedCabinet.rotationY) || 0 }}°</el-descriptions-item>
          </el-descriptions>
          <section class="room3d-cabinet-devices">
            <div class="room3d-section-head">
              <strong>柜内设备</strong>
              <span>从高 U 位向下排列</span>
            </div>
            <el-scrollbar max-height="420px">
              <button
                v-for="device in selectedCabinetDevices"
                :key="device.deviceKey"
                type="button"
                class="room3d-cabinet-device"
                @click="selectDevice(device)"
              >
                <i :style="{ background: getDeviceColor(device.assetType) }"></i>
                <span>
                  <strong>{{ device.assetName }}</strong>
                  <small>{{ formatU(device) }} · {{ device.ipAddress || '未填写IP' }}</small>
                </span>
              </button>
            </el-scrollbar>
            <el-empty v-if="!selectedCabinetDevices.length" description="当前机柜暂无设备" :image-size="64" />
          </section>
        </template>

        <template v-else>
          <div class="room3d-inspector-head">
            <div>
              <span>当前机房</span>
              <h3>{{ selectedRoom?.roomName || '未选择机房' }}</h3>
            </div>
          </div>
          <el-descriptions v-if="selectedRoom" :column="1" size="small" border>
            <el-descriptions-item label="机房尺寸">
              {{ formatMeter(selectedRoom.roomWidth, 12) }} × {{ formatMeter(selectedRoom.roomDepth, 8) }}
            </el-descriptions-item>
            <el-descriptions-item label="机柜数量">{{ currentRoomCabinets.length }} 个</el-descriptions-item>
            <el-descriptions-item label="上架设备">{{ currentRoomDevices.length }} 台</el-descriptions-item>
            <el-descriptions-item label="可视链路">{{ currentRoomLinks.length }} 条</el-descriptions-item>
          </el-descriptions>
          <div class="room3d-inspector-guide">
            <strong>查看方式</strong>
            <p>点击机柜查看容量和柜内设备；点击设备查看 IP、U 位、光电口数量及上联交换机。</p>
            <p>切换到“调整机柜”后可直接拖动机柜，位置会实时保存。</p>
          </div>
        </template>
      </aside>
    </div>

    <el-dialog v-model="linkFormOpen" :title="linkForm.linkId ? '编辑设备上联' : '新增设备上联'" width="600px" append-to-body>
      <el-form ref="linkFormRef" :model="linkForm" :rules="linkRules" label-width="96px">
        <el-form-item label="源设备">
          <el-input :model-value="linkSourceLabel" disabled />
        </el-form-item>
        <el-form-item label="上联交换机" prop="targetId">
          <el-select v-model="linkForm.targetId" placeholder="请选择同一现场的交换机" filterable>
            <el-option
              v-for="device in switchOptions"
              :key="device.deviceKey"
              :label="`${device.assetName} · ${device.ipAddress || '未填写IP'}`"
              :value="device.sourceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="连接介质" prop="mediumType">
          <el-radio-group v-model="linkForm.mediumType">
            <el-radio-button value="OPTICAL">光口</el-radio-button>
            <el-radio-button value="ELECTRICAL">电口</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="端口数量" prop="portCount">
          <el-input-number v-model="linkForm.portCount" :min="1" :max="256" controls-position="right" />
        </el-form-item>
        <div class="room3d-link-form-grid">
          <el-form-item label="设备端口">
            <el-input v-model="linkForm.sourcePort" placeholder="例如：GE0/0/1" />
          </el-form-item>
          <el-form-item label="交换机端口">
            <el-input v-model="linkForm.targetPort" placeholder="例如：Ten-GigabitEthernet1/0/1" />
          </el-form-item>
        </div>
        <el-form-item label="状态">
          <el-switch v-model="linkForm.status" active-value="0" inactive-value="1" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="linkForm.remark" type="textarea" :rows="3" placeholder="可填写链路用途、汇聚方向等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkFormOpen = false">取消</el-button>
        <el-button type="primary" :loading="linkSaving" @click="submitLink">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { CSS2DObject, CSS2DRenderer } from 'three/addons/renderers/CSS2DRenderer.js'
import {
  addEquipmentLink,
  delEquipmentLink,
  getEquipmentTopology,
  updateEquipmentCabinetLayout,
  updateEquipmentLink
} from '@/api/support/equipmentLocation'
import {
  CABINET_DEPTH,
  CABINET_HEIGHT,
  CABINET_WIDTH,
  clampCabinetPosition,
  countCabinetCollisions,
  findCabinetCollision,
  getDeviceKey,
  getDeviceLinks,
  getDeviceRackTransform,
  isDevicePlaced,
  normalizeRoomSize,
  resolveCabinetLayout,
  summarizeOutgoingPorts
} from './equipmentRoom3d.helpers.js'

const props = defineProps({
  siteId: { type: [Number, String], required: true },
  siteName: { type: String, default: '' }
})

const emit = defineEmits(['close', 'edit-device', 'changed'])
const { proxy } = getCurrentInstance()

const loading = ref(false)
const syncing = ref(false)
const liveSyncError = ref(false)
const loadError = ref('')
const renderError = ref('')
const rooms = ref([])
const cabinets = ref([])
const devices = ref([])
const links = ref([])
const selectedRoomId = ref(null)
const selectedCabinetId = ref(null)
const selectedDeviceKey = ref('')
const showLinks = ref(true)
const workspaceMode = ref('view')
const workspaceModeOptions = [
  { label: '浏览', value: 'view' },
  { label: '调整机柜', value: 'layout' }
]

const sceneHost = ref(null)
const linkFormRef = ref(null)
const linkFormOpen = ref(false)
const linkSaving = ref(false)
const linkForm = reactive(createEmptyLinkForm())
const linkRules = {
  targetId: [{ required: true, message: '请选择上联交换机', trigger: 'change' }],
  mediumType: [{ required: true, message: '请选择光口或电口', trigger: 'change' }],
  portCount: [{ required: true, message: '请填写端口数量', trigger: 'change' }]
}

const deviceLegend = [
  { label: '服务器', value: 'SERVER', color: '#eb5757' },
  { label: '交换机', value: 'SWITCH', color: '#f2994a' },
  { label: '解码器', value: 'DECODER', color: '#2f80ed' },
  { label: '终端', value: 'TERMINAL', color: '#27ae60' },
  { label: '网闸', value: 'GATEWAY', color: '#9b51e0' }
]

const selectedRoom = computed(() => rooms.value.find((room) => Number(room.roomId) === Number(selectedRoomId.value)) || null)
const currentRoomCabinets = computed(() => cabinets.value.filter((cabinet) => Number(cabinet.roomId) === Number(selectedRoomId.value)))
const currentRoomDevices = computed(() => devices.value.filter((device) => Number(device.roomId) === Number(selectedRoomId.value) && isDevicePlaced(device)))
const unplacedDevices = computed(() => devices.value.filter((device) => !isDevicePlaced(device)))
const selectedCabinet = computed(() => cabinets.value.find((cabinet) => Number(cabinet.cabinetId) === Number(selectedCabinetId.value)) || null)
const selectedDevice = computed(() => devices.value.find((device) => device.deviceKey === selectedDeviceKey.value) || null)
const selectedCabinetDevices = computed(() => selectedCabinet.value
  ? devices.value
    .filter((device) => Number(device.cabinetId) === Number(selectedCabinet.value.cabinetId))
    .sort((a, b) => Number(b.rackUEnd || 0) - Number(a.rackUEnd || 0))
  : [])
const selectedDeviceLinks = computed(() => selectedDevice.value ? getDeviceLinks(selectedDevice.value, links.value) : [])
const selectedPortSummary = computed(() => selectedDevice.value ? summarizeOutgoingPorts(selectedDevice.value, links.value) : { optical: 0, electrical: 0 })
const switchOptions = computed(() => devices.value.filter((device) =>
  device.assetType === 'SWITCH' &&
  !(device.sourceType === linkForm.sourceType && Number(device.sourceId) === Number(linkForm.sourceId))
))
const currentRoomLinks = computed(() => {
  const keys = new Set(currentRoomDevices.value.map((device) => device.deviceKey))
  return links.value.filter((link) => keys.has(getDeviceKey(link.sourceType, link.sourceId)) && keys.has(getDeviceKey(link.targetType, link.targetId)))
})
const currentRoomCapacityU = computed(() => currentRoomCabinets.value.reduce((total, cabinet) => total + (Number(cabinet.uCapacity) || 45), 0))
const currentRoomUsedU = computed(() => currentRoomCabinets.value.reduce((total, cabinet) => total + getCabinetUsedU(cabinet), 0))
const currentRoomCollisionCount = computed(() => selectedRoom.value ? countCabinetCollisions(currentRoomCabinets.value, selectedRoom.value) : 0)
const linkSourceLabel = computed(() => {
  const source = devices.value.find((device) =>
    device.sourceType === linkForm.sourceType && Number(device.sourceId) === Number(linkForm.sourceId)
  )
  return source ? `${source.assetName} · ${source.ipAddress || '未填写IP'}` : '-'
})

let scene
let camera
let renderer
let labelRenderer
let controls
let sceneContent
let animationFrame
let resizeObserver
let themeObserver
let raycaster
let pointer
let floorPlane
let interactiveObjects = []
let cabinetObjectMap = new Map()
let deviceObjectMap = new Map()
let deviceLabelElements = []
let linkPulses = []
let dragState = null
let pointerDownPosition = null
let reducedMotion = false
let liveSyncTimer
let deviceLabelsVisible = false
let topologySignature = ''

onMounted(async () => {
  reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
  await nextTick()
  initScene()
  await loadTopology()
  startLiveSync()
})

onBeforeUnmount(() => {
  stopLiveSync()
  disposeScene()
})

watch(selectedRoomId, async () => {
  selectedCabinetId.value = null
  if (selectedDevice.value?.roomId && Number(selectedDevice.value.roomId) !== Number(selectedRoomId.value)) {
    selectedDeviceKey.value = ''
  }
  await nextTick()
  rebuildScene()
  resetCamera()
})

watch(showLinks, rebuildScene)
watch(workspaceMode, () => {
  if (sceneHost.value) sceneHost.value.style.cursor = workspaceMode.value === 'layout' ? 'move' : 'grab'
})

async function loadTopology(options = {}) {
  if (!props.siteId) return
  const silent = options.silent === true
  if (loading.value || syncing.value) return
  if (silent) syncing.value = true
  else loading.value = true
  if (!silent) loadError.value = ''
  try {
    const response = await getEquipmentTopology(props.siteId)
    const data = response.data || {}
    const nextRooms = data.rooms || []
    const nextCabinets = data.cabinets || []
    const nextDevices = data.devices || []
    const nextLinks = data.links || []
    const nextSignature = JSON.stringify([nextRooms, nextCabinets, nextDevices, nextLinks])
    liveSyncError.value = false
    if (silent && nextSignature === topologySignature) return
    topologySignature = nextSignature
    rooms.value = nextRooms
    cabinets.value = nextCabinets
    devices.value = nextDevices
    links.value = nextLinks
    if (!rooms.value.some((room) => Number(room.roomId) === Number(selectedRoomId.value))) {
      selectedRoomId.value = rooms.value[0]?.roomId || null
    }
    if (selectedDeviceKey.value && !devices.value.some((device) => device.deviceKey === selectedDeviceKey.value)) {
      selectedDeviceKey.value = ''
    }
    if (selectedCabinetId.value && !cabinets.value.some((cabinet) => Number(cabinet.cabinetId) === Number(selectedCabinetId.value))) {
      selectedCabinetId.value = null
    }
    await nextTick()
    rebuildScene()
  } catch (error) {
    liveSyncError.value = true
    if (!silent) loadError.value = error?.msg || error?.message || '请检查接口和数据库升级脚本后重试'
  } finally {
    if (silent) syncing.value = false
    else loading.value = false
  }
}

function startLiveSync() {
  stopLiveSync()
  liveSyncTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible' && !linkFormOpen.value) loadTopology({ silent: true })
  }, 10000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
}

function stopLiveSync() {
  if (liveSyncTimer) window.clearInterval(liveSyncTimer)
  liveSyncTimer = null
  document.removeEventListener('visibilitychange', handleVisibilityChange)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') loadTopology({ silent: true })
}

function initScene() {
  if (!sceneHost.value) return
  try {
    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(42, 1, 0.1, 250)
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFShadowMap
    renderer.domElement.setAttribute('aria-hidden', 'true')
    sceneHost.value.appendChild(renderer.domElement)

    labelRenderer = new CSS2DRenderer()
    labelRenderer.domElement.className = 'room3d-label-layer'
    labelRenderer.domElement.style.position = 'absolute'
    labelRenderer.domElement.style.inset = '0'
    labelRenderer.domElement.style.pointerEvents = 'none'
    sceneHost.value.appendChild(labelRenderer.domElement)

    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = !reducedMotion
    controls.dampingFactor = 0.08
    controls.minDistance = 4
    controls.maxDistance = 42
    controls.maxPolarAngle = Math.PI * 0.48
    controls.zoomToCursor = true

    const ambient = new THREE.HemisphereLight(0xe8f3ff, 0x283747, 1.6)
    scene.add(ambient)
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2)
    keyLight.position.set(8, 16, 10)
    keyLight.castShadow = true
    keyLight.shadow.mapSize.set(2048, 2048)
    scene.add(keyLight)
    const fillLight = new THREE.DirectionalLight(0x8db7ff, 0.7)
    fillLight.position.set(-10, 8, -6)
    scene.add(fillLight)

    sceneContent = new THREE.Group()
    scene.add(sceneContent)
    raycaster = new THREE.Raycaster()
    pointer = new THREE.Vector2()
    floorPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)

    sceneHost.value.addEventListener('pointerdown', handlePointerDown)
    sceneHost.value.addEventListener('pointermove', handlePointerMove)
    sceneHost.value.addEventListener('pointerup', handlePointerUp)
    sceneHost.value.addEventListener('pointerleave', handlePointerLeave)
    sceneHost.value.addEventListener('dblclick', handleDoubleClick)

    resizeObserver = new ResizeObserver(resizeScene)
    resizeObserver.observe(sceneHost.value)
    themeObserver = new MutationObserver(() => rebuildScene())
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
    resizeScene()
    resetCamera()
    animate()
  } catch (error) {
    renderError.value = error?.message || 'WebGL 初始化失败'
  }
}

function rebuildScene() {
  if (!sceneContent || !selectedRoom.value) {
    clearSceneContent()
    return
  }
  clearSceneContent()
  interactiveObjects = []
  cabinetObjectMap = new Map()
  deviceObjectMap = new Map()
  deviceLabelElements = []
  linkPulses = []

  const room = selectedRoom.value
  const { width, depth } = normalizeRoomSize(room)
  createRoomShell(width, depth)

  currentRoomCabinets.value.forEach((cabinet, index) => {
    createCabinet(cabinet, index, room)
  })
  sceneContent.updateMatrixWorld(true)
  if (showLinks.value) createLinks()
}

function createRoomShell(width, depth) {
  const floorColor = readCssColor('--el-fill-color', '#e5ecf3')
  const gridColor = readCssColor('--el-border-color', '#93a7bd')
  const floorGeometry = new THREE.PlaneGeometry(width, depth)
  const floorMaterial = new THREE.MeshStandardMaterial({ color: floorColor, roughness: 0.92, metalness: 0.02 })
  const floor = new THREE.Mesh(floorGeometry, floorMaterial)
  floor.rotation.x = -Math.PI / 2
  floor.receiveShadow = true
  floor.userData = { kind: 'floor' }
  sceneContent.add(floor)

  const gridPoints = []
  const gridStep = 0.5
  for (let x = -width / 2; x <= width / 2 + 0.001; x += gridStep) {
    gridPoints.push(new THREE.Vector3(x, 0.008, -depth / 2), new THREE.Vector3(x, 0.008, depth / 2))
  }
  for (let z = -depth / 2; z <= depth / 2 + 0.001; z += gridStep) {
    gridPoints.push(new THREE.Vector3(-width / 2, 0.008, z), new THREE.Vector3(width / 2, 0.008, z))
  }
  const grid = new THREE.LineSegments(
    new THREE.BufferGeometry().setFromPoints(gridPoints),
    new THREE.LineBasicMaterial({ color: gridColor, transparent: true, opacity: 0.3 })
  )
  sceneContent.add(grid)

  const wallMaterial = new THREE.MeshStandardMaterial({ color: gridColor, transparent: true, opacity: 0.32, roughness: 0.8 })
  const walls = [
    { size: [width, 0.12, 0.08], position: [0, 0.06, -depth / 2] },
    { size: [width, 0.12, 0.08], position: [0, 0.06, depth / 2] },
    { size: [0.08, 0.12, depth], position: [-width / 2, 0.06, 0] },
    { size: [0.08, 0.12, depth], position: [width / 2, 0.06, 0] }
  ]
  walls.forEach((item) => {
    const wall = new THREE.Mesh(new THREE.BoxGeometry(...item.size), wallMaterial.clone())
    wall.position.set(...item.position)
    sceneContent.add(wall)
  })
}

function createCabinet(cabinet, index, room) {
  const layout = resolveCabinetLayout(cabinet, index, room)
  const { width, depth } = normalizeRoomSize(room)
  if (cabinet.positionX == null) cabinet.positionX = layout.x
  if (cabinet.positionZ == null) cabinet.positionZ = layout.z
  if (cabinet.rotationY == null) cabinet.rotationY = layout.rotationY

  const group = new THREE.Group()
  group.position.set(layout.x - width / 2, 0, layout.z - depth / 2)
  group.rotation.y = THREE.MathUtils.degToRad(layout.rotationY)
  group.userData = { kind: 'cabinet-group', cabinetId: cabinet.cabinetId }

  const bodyMaterial = new THREE.MeshStandardMaterial({
    color: selectedCabinetId.value === cabinet.cabinetId ? 0x377dff : 0x53657a,
    transparent: true,
    opacity: selectedCabinetId.value === cabinet.cabinetId ? 0.32 : 0.22,
    roughness: 0.34,
    metalness: 0.58
  })
  const body = new THREE.Mesh(new THREE.BoxGeometry(CABINET_WIDTH, CABINET_HEIGHT, CABINET_DEPTH), bodyMaterial)
  body.position.y = CABINET_HEIGHT / 2
  body.castShadow = true
  body.receiveShadow = true
  body.userData = { kind: 'cabinet', cabinetId: cabinet.cabinetId }
  group.add(body)
  interactiveObjects.push(body)

  const frame = new THREE.LineSegments(
    new THREE.EdgesGeometry(body.geometry),
    new THREE.LineBasicMaterial({ color: selectedCabinetId.value === cabinet.cabinetId ? 0x72a7ff : 0xb6c4d4, transparent: true, opacity: 0.88 })
  )
  frame.position.copy(body.position)
  group.add(frame)

  const cabinetDevices = devices.value.filter((device) => Number(device.cabinetId) === Number(cabinet.cabinetId) && isDevicePlaced(device))
  cabinetDevices.forEach((device) => createRackDevice(group, cabinet, device))

  const label = document.createElement('div')
  label.className = 'room3d-cabinet-label'
  label.innerHTML = `<strong>${escapeHtml(cabinet.cabinetNo || '未编号')}</strong><span>${cabinetDevices.length}台 · ${getCabinetUsedU(cabinet)}/${cabinet.uCapacity || 45}U</span>`
  const labelObject = new CSS2DObject(label)
  labelObject.position.set(0, CABINET_HEIGHT + 0.38, 0)
  group.add(labelObject)

  sceneContent.add(group)
  cabinetObjectMap.set(Number(cabinet.cabinetId), group)
}

function createRackDevice(group, cabinet, device) {
  const transform = getDeviceRackTransform(device, cabinet)
  const selected = selectedDeviceKey.value === device.deviceKey
  const color = new THREE.Color(getDeviceColor(device.assetType))
  const material = new THREE.MeshStandardMaterial({
    color,
    emissive: selected ? color.clone().multiplyScalar(0.32) : new THREE.Color(0x000000),
    roughness: 0.5,
    metalness: 0.25
  })
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(CABINET_WIDTH - 0.12, transform.height, 0.13), material)
  mesh.position.set(0, transform.y, CABINET_DEPTH / 2 + 0.035)
  mesh.castShadow = true
  mesh.userData = { kind: 'device', deviceKey: device.deviceKey, cabinetId: cabinet.cabinetId }
  group.add(mesh)
  interactiveObjects.push(mesh)
  deviceObjectMap.set(device.deviceKey, mesh)

  if (Number(selectedCabinetId.value) === Number(cabinet.cabinetId)) {
    const label = document.createElement('div')
    label.className = 'room3d-device-label'
    label.style.display = 'none'
    label.innerHTML = `<strong>${escapeHtml(device.assetName || '未命名设备')}</strong><span>${escapeHtml(formatU(device))}</span>`
    const labelObject = new CSS2DObject(label)
    labelObject.position.set(CABINET_WIDTH / 2 + 0.18, transform.y, CABINET_DEPTH / 2 + 0.08)
    group.add(labelObject)
    deviceLabelElements.push(label)
  }
}

function createLinks() {
  const visibleKeys = new Set(currentRoomDevices.value.map((device) => device.deviceKey))
  const opticalColor = new THREE.Color('#2bb7da')
  const electricalColor = new THREE.Color('#f0a83a')
  links.value.forEach((link, index) => {
    const sourceKey = getDeviceKey(link.sourceType, link.sourceId)
    const targetKey = getDeviceKey(link.targetType, link.targetId)
    if (!visibleKeys.has(sourceKey) || !visibleKeys.has(targetKey)) return
    const sourceObject = deviceObjectMap.get(sourceKey)
    const targetObject = deviceObjectMap.get(targetKey)
    const sourceDevice = devices.value.find((device) => device.deviceKey === sourceKey)
    const targetDevice = devices.value.find((device) => device.deviceKey === targetKey)
    if (!sourceObject || !targetObject) return

    const start = sourceObject.getWorldPosition(new THREE.Vector3())
    const end = targetObject.getWorldPosition(new THREE.Vector3())
    start.y += 0.08
    end.y += 0.08
    const distance = start.distanceTo(end)
    const midpoint = start.clone().lerp(end, 0.5)
    midpoint.y += Math.max(0.65, distance * 0.28)
    if (sourceDevice?.cabinetId && Number(sourceDevice.cabinetId) === Number(targetDevice?.cabinetId)) {
      midpoint.x += 1.05
      midpoint.y += 0.35
    } else if (distance < 0.3) {
      midpoint.x += 0.9
    }
    const curve = new THREE.QuadraticBezierCurve3(start, midpoint, end)
    const color = link.mediumType === 'OPTICAL' ? opticalColor : electricalColor
    const tube = new THREE.Mesh(
      new THREE.TubeGeometry(curve, 28, 0.018 + Math.min(Number(link.portCount) || 1, 8) * 0.002, 6, false),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: link.status === '1' ? 0.22 : 0.72 })
    )
    tube.userData = { kind: 'link', linkId: link.linkId }
    sceneContent.add(tube)

    const pulse = new THREE.Mesh(
      new THREE.SphereGeometry(0.055, 12, 8),
      new THREE.MeshBasicMaterial({ color })
    )
    pulse.position.copy(curve.getPointAt(0.2))
    sceneContent.add(pulse)
    linkPulses.push({ mesh: pulse, curve, offset: (index * 0.19) % 1, speed: link.mediumType === 'OPTICAL' ? 0.14 : 0.1 })
  })
}

function animate(time = 0) {
  animationFrame = requestAnimationFrame(animate)
  if (!scene || !camera || !renderer || !labelRenderer) return
  if (controls) controls.update()
  const shouldShowDeviceLabels = Boolean(selectedCabinetId.value) && camera.position.distanceTo(controls.target) <= 10
  if (shouldShowDeviceLabels !== deviceLabelsVisible) {
    deviceLabelsVisible = shouldShowDeviceLabels
    deviceLabelElements.forEach((element) => {
      element.style.display = shouldShowDeviceLabels ? 'inline-flex' : 'none'
    })
  }
  if (!reducedMotion) {
    const seconds = time / 1000
    linkPulses.forEach((item) => item.mesh.position.copy(item.curve.getPointAt((seconds * item.speed + item.offset) % 1)))
  }
  renderer.render(scene, camera)
  labelRenderer.render(scene, camera)
}

function resizeScene() {
  if (!sceneHost.value || !camera || !renderer || !labelRenderer) return
  const width = Math.max(1, sceneHost.value.clientWidth)
  const height = Math.max(1, sceneHost.value.clientHeight)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, false)
  labelRenderer.setSize(width, height)
}

function resetCamera() {
  if (!camera || !controls) return
  const { width, depth } = normalizeRoomSize(selectedRoom.value || {})
  const span = Math.max(width, depth)
  camera.position.set(span * 1.08, Math.max(9, span * 1.12), span * 1.28)
  controls.target.set(0, 0.7, 0)
  controls.update()
}

function focusCabinet(cabinetId, preserveDevice = false) {
  const group = cabinetObjectMap.get(Number(cabinetId))
  if (!group || !camera || !controls) return
  selectedCabinetId.value = cabinetId
  if (!preserveDevice) selectedDeviceKey.value = ''
  const target = group.getWorldPosition(new THREE.Vector3())
  target.y = 1.8
  controls.target.copy(target)
  camera.position.copy(target.clone().add(new THREE.Vector3(4.2, 3.4, 4.6)))
  controls.update()
  rebuildScene()
}

function handlePointerDown(event) {
  pointerDownPosition = { x: event.clientX, y: event.clientY }
  const hit = pickObject(event)
  if (workspaceMode.value !== 'layout' || hit?.object?.userData?.kind !== 'cabinet') return
  const cabinetId = Number(hit.object.userData.cabinetId)
  const group = cabinetObjectMap.get(cabinetId)
  const cabinet = cabinets.value.find((item) => Number(item.cabinetId) === cabinetId)
  if (!group || !cabinet) return
  dragState = { group, cabinet, moved: false }
  controls.enabled = false
  sceneHost.value.setPointerCapture?.(event.pointerId)
}

function handlePointerMove(event) {
  if (dragState) {
    const point = intersectFloor(event)
    if (!point || !selectedRoom.value) return
    const { width, depth } = normalizeRoomSize(selectedRoom.value)
    const position = clampCabinetPosition(
      { x: point.x + width / 2, z: point.z + depth / 2 },
      selectedRoom.value,
      Number(dragState.cabinet.rotationY) || 0
    )
    dragState.group.position.x = position.x - width / 2
    dragState.group.position.z = position.z - depth / 2
    dragState.nextPosition = position
    dragState.collision = findCabinetCollision(
      { ...dragState.cabinet, positionX: position.x, positionZ: position.z },
      currentRoomCabinets.value,
      selectedRoom.value,
      dragState.cabinet.cabinetId
    )
    setCabinetDragCollision(dragState.group, Boolean(dragState.collision))
    dragState.moved = true
    return
  }
  const hit = pickObject(event)
  if (!sceneHost.value) return
  if (workspaceMode.value === 'layout' && hit?.object?.userData?.kind === 'cabinet') {
    sceneHost.value.style.cursor = 'move'
  } else if (hit?.object?.userData?.kind === 'device' || hit?.object?.userData?.kind === 'cabinet') {
    sceneHost.value.style.cursor = 'pointer'
  } else {
    sceneHost.value.style.cursor = workspaceMode.value === 'layout' ? 'move' : 'grab'
  }
}

async function handlePointerUp(event) {
  if (dragState) {
    const current = dragState
    dragState = null
    controls.enabled = true
    sceneHost.value.releasePointerCapture?.(event.pointerId)
    if (current.moved && current.nextPosition) {
      if (current.collision) {
        proxy.$modal.msgWarning(`机柜位置与 ${current.collision.cabinetNo} 重叠，请重新摆放`)
        rebuildScene()
        return
      }
      const payload = {
        cabinetId: current.cabinet.cabinetId,
        positionX: current.nextPosition.x,
        positionZ: current.nextPosition.z,
        rotationY: Number(current.cabinet.rotationY) || 0
      }
      try {
        await updateEquipmentCabinetLayout(payload)
        Object.assign(current.cabinet, payload)
        proxy.$modal.msgSuccess(`机柜 ${current.cabinet.cabinetNo} 位置已保存`)
        emit('changed')
      } catch (error) {
        proxy.$modal.msgError(error?.msg || error?.message || '机柜位置保存失败')
        await loadTopology()
      }
      rebuildScene()
    }
    return
  }

  if (!pointerDownPosition) return
  const moved = Math.hypot(event.clientX - pointerDownPosition.x, event.clientY - pointerDownPosition.y) > 5
  pointerDownPosition = null
  if (moved) return
  const hit = pickObject(event)
  if (!hit) {
    clearSelection()
    return
  }
  const data = hit.object.userData
  if (data.kind === 'device') {
    selectedDeviceKey.value = data.deviceKey
    selectedCabinetId.value = Number(data.cabinetId)
  } else if (data.kind === 'cabinet') {
    selectedCabinetId.value = Number(data.cabinetId)
    selectedDeviceKey.value = ''
  }
  rebuildScene()
}

function setCabinetDragCollision(group, colliding) {
  const body = group.children.find((child) => child.userData?.kind === 'cabinet')
  if (!body?.material) return
  body.material.emissive.set(colliding ? 0xb42318 : 0x000000)
  body.material.emissiveIntensity = colliding ? 0.72 : 0
}

function handleSceneKeydown(event) {
  if (event.key === 'Escape') {
    clearSelection()
    return
  }
  if (event.key.toLowerCase() === 'r') {
    event.preventDefault()
    resetCamera()
    return
  }
  if (event.key === '+' || event.key === '=') {
    event.preventDefault()
    zoomCamera(0.82)
    return
  }
  if (event.key === '-') {
    event.preventDefault()
    zoomCamera(1.18)
    return
  }
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    cycleCabinet(event.key === 'ArrowRight' ? 1 : -1)
    return
  }
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    cycleDevice(event.key === 'ArrowDown' ? 1 : -1)
    return
  }
  if (event.key === 'Enter' && selectedCabinet.value) {
    event.preventDefault()
    if (selectedDevice.value) emit('edit-device', selectedDevice.value)
    else focusCabinet(selectedCabinet.value.cabinetId)
  }
}

function cycleCabinet(direction) {
  if (!currentRoomCabinets.value.length) return
  const index = currentRoomCabinets.value.findIndex((cabinet) => Number(cabinet.cabinetId) === Number(selectedCabinetId.value))
  const nextIndex = (index + direction + currentRoomCabinets.value.length) % currentRoomCabinets.value.length
  focusCabinet(currentRoomCabinets.value[nextIndex].cabinetId)
}

function cycleDevice(direction) {
  if (!selectedCabinet.value) {
    cycleCabinet(direction)
    return
  }
  if (!selectedCabinetDevices.value.length) return
  const index = selectedCabinetDevices.value.findIndex((device) => device.deviceKey === selectedDeviceKey.value)
  const nextIndex = (index + direction + selectedCabinetDevices.value.length) % selectedCabinetDevices.value.length
  selectDevice(selectedCabinetDevices.value[nextIndex])
}

function zoomCamera(factor) {
  if (!camera || !controls) return
  const offset = camera.position.clone().sub(controls.target)
  const nextLength = THREE.MathUtils.clamp(offset.length() * factor, controls.minDistance, controls.maxDistance)
  camera.position.copy(controls.target.clone().add(offset.normalize().multiplyScalar(nextLength)))
  controls.update()
}

function handlePointerLeave() {
  if (dragState && controls) controls.enabled = true
}

function handleDoubleClick(event) {
  const hit = pickObject(event)
  const cabinetId = hit?.object?.userData?.cabinetId
  if (cabinetId) focusCabinet(cabinetId)
}

function pickObject(event) {
  if (!raycaster || !camera || !sceneHost.value) return null
  const rect = sceneHost.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return raycaster.intersectObjects(interactiveObjects, false)[0] || null
}

function intersectFloor(event) {
  if (!raycaster || !camera || !sceneHost.value) return null
  const rect = sceneHost.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return raycaster.ray.intersectPlane(floorPlane, new THREE.Vector3())
}

function handleRoomSelect(index) {
  selectedRoomId.value = Number(index)
}

async function selectDevice(device) {
  if (device.roomId && Number(device.roomId) !== Number(selectedRoomId.value)) {
    selectedRoomId.value = device.roomId
    await nextTick()
  }
  selectedDeviceKey.value = device.deviceKey
  selectedCabinetId.value = device.cabinetId || null
  rebuildScene()
  if (device.cabinetId) focusCabinet(device.cabinetId, true)
}

function clearSelection() {
  selectedCabinetId.value = null
  selectedDeviceKey.value = ''
  rebuildScene()
}

function openLinkForm(link = null) {
  if (!selectedDevice.value && !link) return
  Object.assign(linkForm, createEmptyLinkForm())
  if (link) {
    Object.assign(linkForm, {
      ...link,
      targetId: Number(link.targetId)
    })
  } else {
    Object.assign(linkForm, {
      siteId: Number(props.siteId),
      sourceType: selectedDevice.value.sourceType,
      sourceId: selectedDevice.value.sourceId,
      targetType: 'HARDWARE'
    })
  }
  linkFormOpen.value = true
  nextTick(() => linkFormRef.value?.clearValidate())
}

async function submitLink() {
  if (!await linkFormRef.value?.validate().catch(() => false)) return
  linkSaving.value = true
  try {
    const request = linkForm.linkId ? updateEquipmentLink(linkForm) : addEquipmentLink(linkForm)
    await request
    proxy.$modal.msgSuccess(linkForm.linkId ? '上联关系已更新' : '上联关系已新增')
    linkFormOpen.value = false
    emit('changed')
    await loadTopology()
  } finally {
    linkSaving.value = false
  }
}

async function removeLink(link) {
  await proxy.$modal.confirm(`确认删除 ${formatMedium(link.mediumType)} ${link.portCount}口 的设备上联吗？`)
  await delEquipmentLink(link.linkId)
  proxy.$modal.msgSuccess('上联关系已删除')
  emit('changed')
  await loadTopology()
}

function createEmptyLinkForm() {
  return {
    linkId: null,
    siteId: Number(props?.siteId) || null,
    sourceType: '',
    sourceId: null,
    targetType: 'HARDWARE',
    targetId: null,
    mediumType: 'OPTICAL',
    portCount: 1,
    sourcePort: '',
    targetPort: '',
    status: '0',
    remark: ''
  }
}

function getRoomCabinets(roomId) {
  return cabinets.value.filter((cabinet) => Number(cabinet.roomId) === Number(roomId))
}

function getRoomDeviceCount(roomId) {
  return devices.value.filter((device) => Number(device.roomId) === Number(roomId) && isDevicePlaced(device)).length
}

function getCabinetUsedU(cabinet) {
  const occupied = new Set()
  devices.value
    .filter((device) => Number(device.cabinetId) === Number(cabinet.cabinetId) && isDevicePlaced(device))
    .forEach((device) => {
      for (let u = Number(device.rackUStart); u <= Number(device.rackUEnd); u += 1) occupied.add(u)
    })
  return occupied.size
}

function getDeviceColor(assetType) {
  return deviceLegend.find((item) => item.value === assetType)?.color || '#66788a'
}

function formatDeviceLocation(device) {
  if (!isDevicePlaced(device)) return '未上架'
  return `${device.equipmentRoom} / ${device.cabinetNo} / ${formatU(device)}`
}

function formatU(device) {
  if (!device.rackUStart || !device.rackUEnd) return '未配置U位'
  return Number(device.rackUStart) === Number(device.rackUEnd)
    ? `${device.rackUStart}U`
    : `${device.rackUStart}-${device.rackUEnd}U`
}

function formatMeter(value, fallback = 0) {
  return `${Number(value ?? fallback).toFixed(1)}m`
}

function formatMedium(value) {
  return value === 'OPTICAL' ? '光口' : '电口'
}

function getLinkPeerName(link, device) {
  const isSource = getDeviceKey(link.sourceType, link.sourceId) === device.deviceKey
  return isSource
    ? `上联 ${link.targetName || link.targetIp || '交换机'}`
    : `下联 ${link.sourceName || link.sourceIp || '设备'}`
}

function formatLinkPorts(link) {
  const parts = []
  if (link.sourcePort) parts.push(`设备 ${link.sourcePort}`)
  if (link.targetPort) parts.push(`交换机 ${link.targetPort}`)
  return parts.length ? parts.join(' / ') : '未填写端口编号'
}

function readCssColor(variable, fallback) {
  return getComputedStyle(document.documentElement).getPropertyValue(variable).trim() || fallback
}

function escapeHtml(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function clearSceneContent() {
  if (!sceneContent) return
  sceneContent.traverse((object) => {
    if (object.element?.remove) object.element.remove()
    object.geometry?.dispose?.()
    if (Array.isArray(object.material)) object.material.forEach((material) => material.dispose?.())
    else object.material?.dispose?.()
  })
  sceneContent.clear()
  interactiveObjects = []
  cabinetObjectMap.clear()
  deviceObjectMap.clear()
  deviceLabelElements = []
  deviceLabelsVisible = false
  linkPulses = []
}

function disposeScene() {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  if (sceneHost.value) {
    sceneHost.value.removeEventListener('pointerdown', handlePointerDown)
    sceneHost.value.removeEventListener('pointermove', handlePointerMove)
    sceneHost.value.removeEventListener('pointerup', handlePointerUp)
    sceneHost.value.removeEventListener('pointerleave', handlePointerLeave)
    sceneHost.value.removeEventListener('dblclick', handleDoubleClick)
  }
  clearSceneContent()
  controls?.dispose()
  renderer?.dispose()
  renderer?.domElement?.remove()
  labelRenderer?.domElement?.remove()
  scene = null
  renderer = null
  labelRenderer = null
}
</script>

<style scoped>
.room3d-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-height: 0;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.room3d-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 72px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.room3d-heading {
  min-width: 260px;
}

.room3d-heading h2,
.room3d-inspector-head h3 {
  margin: 0;
  letter-spacing: 0;
  color: var(--el-text-color-primary);
}

.room3d-heading h2 {
  font-size: 20px;
  line-height: 1.35;
}

.room3d-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.room3d-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.room3d-room-select {
  width: 190px;
}

.room3d-live-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.room3d-live-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-success);
}

.room3d-live-status.has-error {
  color: var(--el-color-danger);
}

.room3d-live-status.has-error i {
  background: var(--el-color-danger);
}

.room3d-switch-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.room3d-error {
  display: grid;
  place-items: center;
  min-height: 0;
}

.room3d-body {
  display: grid;
  grid-template-columns: 210px minmax(480px, 1fr) 330px;
  min-height: 0;
  overflow: hidden;
}

.room3d-room-panel,
.room3d-inspector {
  min-height: 0;
  overflow: auto;
  background: var(--el-bg-color);
}

.room3d-room-panel {
  border-right: 1px solid var(--el-border-color-light);
}

.room3d-inspector {
  padding: 16px;
  border-left: 1px solid var(--el-border-color-light);
}

.room3d-panel-title,
.room3d-section-head,
.room3d-inspector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.room3d-panel-title {
  padding: 15px 14px 8px;
}

.room3d-panel-title span,
.room3d-section-head span,
.room3d-inspector-head span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-panel-title--sub {
  padding: 14px 0 8px;
}

.room3d-room-menu {
  border-right: 0;
}

.room3d-room-menu :deep(.el-menu-item) {
  height: 58px;
  padding: 0 14px !important;
  line-height: 1.35;
}

.room3d-room-menu__content {
  display: grid;
  gap: 4px;
  width: 100%;
  min-width: 0;
}

.room3d-room-menu__content strong,
.room3d-room-menu__content span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-room-menu__content span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-unplaced {
  margin: 10px 14px 0;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.room3d-unplaced-item,
.room3d-cabinet-device {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 50px;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  color: var(--el-text-color-primary);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.room3d-unplaced-item:hover,
.room3d-unplaced-item.is-active,
.room3d-cabinet-device:hover {
  background: var(--el-fill-color-light);
}

.room3d-unplaced-item i,
.room3d-cabinet-device i {
  flex: 0 0 7px;
  width: 7px;
  height: 30px;
  border-radius: 3px;
}

.room3d-unplaced-item span,
.room3d-cabinet-device span {
  display: grid;
  min-width: 0;
}

.room3d-unplaced-item strong,
.room3d-unplaced-item small,
.room3d-cabinet-device strong,
.room3d-cabinet-device small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-unplaced-item small,
.room3d-cabinet-device small {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
}

.room3d-scene-panel {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--el-fill-color-extra-light);
}

.room3d-scene {
  position: absolute;
  inset: 0;
  min-width: 0;
  min-height: 0;
  outline: none;
  cursor: grab;
  touch-action: none;
  background:
    radial-gradient(circle at 48% 30%, color-mix(in srgb, var(--el-color-primary-light-9) 38%, transparent), transparent 46%),
    var(--el-fill-color-extra-light);
}

.room3d-scene:focus-visible {
  box-shadow: inset 0 0 0 2px var(--el-color-primary);
}

.room3d-scene.is-layout-mode {
  cursor: move;
}

.room3d-scene :deep(canvas),
.room3d-scene :deep(.room3d-label-layer) {
  position: absolute;
  inset: 0;
  display: block;
  width: 100% !important;
  height: 100% !important;
}

.room3d-scene :deep(.room3d-cabinet-label) {
  display: grid;
  gap: 1px;
  min-width: 82px;
  padding: 5px 7px;
  border: 1px solid color-mix(in srgb, var(--el-border-color) 72%, transparent);
  border-radius: 6px;
  color: var(--el-text-color-primary);
  text-align: center;
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  box-shadow: 0 4px 12px rgba(22, 34, 50, 0.14);
  pointer-events: none;
}

.room3d-scene :deep(.room3d-cabinet-label strong) {
  font-size: 12px;
}

.room3d-scene :deep(.room3d-cabinet-label span) {
  color: var(--el-text-color-secondary);
  font-size: 10px;
}

.room3d-scene :deep(.room3d-device-label) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 180px;
  padding: 3px 6px;
  border: 1px solid color-mix(in srgb, var(--el-border-color) 75%, transparent);
  border-radius: 5px;
  color: var(--el-text-color-primary);
  background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
  box-shadow: 0 3px 10px rgba(22, 34, 50, 0.12);
  pointer-events: none;
}

.room3d-scene :deep(.room3d-device-label strong) {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-scene :deep(.room3d-device-label span) {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  font-size: 10px;
}

.room3d-scene-summary,
.room3d-legend,
.room3d-layout-hint {
  position: absolute;
  z-index: 4;
  border: 1px solid color-mix(in srgb, var(--el-border-color-light) 78%, transparent);
  border-radius: 7px;
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  box-shadow: 0 6px 18px rgba(22, 34, 50, 0.12);
  backdrop-filter: blur(8px);
}

.room3d-scene-summary {
  top: 14px;
  left: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding: 8px 11px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  pointer-events: none;
}

.room3d-scene-summary strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.room3d-scene-summary__warning,
.room3d-scene-summary__warning strong {
  color: var(--el-color-danger);
}

.room3d-legend {
  right: 14px;
  bottom: 14px;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px 12px;
  max-width: min(620px, calc(100% - 28px));
  padding: 8px 10px;
  color: var(--el-text-color-regular);
  font-size: 11px;
  pointer-events: none;
}

.room3d-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.room3d-legend i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.room3d-legend__line::before {
  width: 18px;
  height: 2px;
  content: '';
  background: currentColor;
}

.room3d-legend__line--optical {
  color: #08738c;
}

.room3d-legend__line--electrical {
  color: #8a5000;
}

.room3d-layout-hint {
  top: 62px;
  left: 14px;
  padding: 7px 10px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  pointer-events: none;
}

.room3d-render-error,
.room3d-empty-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--el-bg-color) 86%, transparent);
}

.room3d-inspector-head {
  align-items: flex-start;
  margin-bottom: 14px;
}

.room3d-inspector-head h3 {
  max-width: 220px;
  margin-top: 3px;
  overflow-wrap: anywhere;
  font-size: 17px;
}

.room3d-port-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 14px 0 20px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: var(--el-border-color-lighter);
}

.room3d-port-summary div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  background: var(--el-bg-color);
}

.room3d-port-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-port-summary strong {
  font-size: 18px;
}

.room3d-section-head {
  margin: 18px 0 8px;
}

.room3d-section-head > div {
  display: grid;
  gap: 2px;
}

.room3d-link-list {
  border-top: 1px solid var(--el-border-color-lighter);
}

.room3d-link-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.room3d-link-row > div:first-child {
  display: grid;
  justify-items: start;
  gap: 5px;
  min-width: 0;
}

.room3d-link-row strong,
.room3d-link-row span {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.room3d-link-row span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.room3d-link-actions {
  display: flex;
  align-items: flex-start;
}

.room3d-legacy-note,
.room3d-inspector-guide p {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.room3d-legacy-note {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color);
}

.room3d-cabinet-devices {
  margin-top: 18px;
}

.room3d-inspector-guide {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.room3d-inspector-guide p {
  margin: 7px 0 0;
}

.room3d-link-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 1180px) {
  .room3d-header {
    align-items: flex-start;
  }

  .room3d-controls {
    flex-wrap: wrap;
  }

  .room3d-body {
    grid-template-columns: 176px minmax(420px, 1fr) 290px;
  }
}

@media (max-width: 900px) {
  .room3d-workspace {
    overflow: auto;
  }

  .room3d-header {
    position: sticky;
    top: 0;
    z-index: 8;
    flex-direction: column;
  }

  .room3d-controls {
    justify-content: flex-start;
    width: 100%;
  }

  .room3d-room-select {
    flex: 1 1 180px;
  }

  .room3d-body {
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .room3d-room-panel {
    display: none;
  }

  .room3d-scene-panel {
    min-height: 58vh;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .room3d-inspector {
    min-height: 360px;
    border-left: 0;
  }

  .room3d-link-form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .room3d-scene-summary {
    right: 10px;
    left: 10px;
  }

  .room3d-layout-hint {
    top: 94px;
    right: 10px;
    left: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .room3d-workspace *,
  .room3d-workspace *::before,
  .room3d-workspace *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
</style>
