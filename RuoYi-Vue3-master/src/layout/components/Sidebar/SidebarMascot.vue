<template>
  <aside
    v-show="isVisible"
    class="sidebar-mascot"
    aria-label="平台看板娘"
    @mouseenter="showMessage('我在左下角值守，菜单收起或移动端会自动让出空间。')"
  >
    <div class="mascot-bubble" :class="{ 'is-refreshing': bubbleRefreshing }">
      {{ activeMessage }}
    </div>

    <div class="mascot-stage">
      <canvas
        id="live2d"
        ref="canvasRef"
        class="mascot-canvas"
        width="800"
        height="800"
        title="点击我换一句提示"
        @click="showNextMessage"
      />
      <div v-if="!modelReady" class="mascot-loading">
        {{ loadError ? '模型加载失败' : '模型加载中' }}
      </div>

      <div class="mascot-actions" aria-label="看板娘操作">
        <button type="button" title="切换提示主题" @click="switchTopic">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3a9 9 0 0 0-8.8 7.1 1 1 0 1 0 2 .4A7 7 0 0 1 17 6.7V9a1 1 0 1 0 2 0V4a1 1 0 0 0-1-1h-5a1 1 0 1 0 0 2h2.4A8.9 8.9 0 0 0 12 3Zm7.6 10.7a1 1 0 0 0-1.2.8A7 7 0 0 1 7 17.3V15a1 1 0 1 0-2 0v5a1 1 0 0 0 1 1h5a1 1 0 1 0 0-2H8.6a8.9 8.9 0 0 0 12-4 1 1 0 0 0-1-1.3Z" />
          </svg>
        </button>
        <button type="button" title="下一句提示" @click="showNextMessage">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M5 5a1 1 0 0 1 1.5-.9l12 7a1 1 0 0 1 0 1.8l-12 7A1 1 0 0 1 5 19V5Z" />
          </svg>
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { useWindowSize } from '@vueuse/core'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})

const WIDTH = 992
const LIVE2D_CORE_ID = 'ry-local-live2d-core'
const MODEL_PATH = 'live2d/models/pio/index.json'
const CORE_PATH = 'live2d/vendor/live2d-widget/live2d.min.js'

const { width } = useWindowSize()
const canvasRef = ref(null)
const modelReady = ref(false)
const loadError = ref(false)
const topicIndex = ref(0)
const messageIndex = ref(0)
const activeMessage = ref(getGreetingMessage())
const bubbleRefreshing = ref(false)

let live2dModel = null
let loadingPromise = null
let ticker = null
let refreshTimer = null

const isMobile = computed(() => width.value < WIDTH)
const isVisible = computed(() => !props.collapsed && !isMobile.value)

const topicMessages = [
  {
    name: '现场融合',
    messages: [
      '现场融合管理建议按“现场、主平台、子平台、服务器”顺序核对。',
      '进入现场配置前，先确认组织、联系人和现场对接人是否完整。',
      '现场融合关系画布可以看清平台和服务器挂载关系，节点异常先从这里找。',
      '新增主平台后，记得补齐子平台入口和服务器归属。',
      '设备资产台账最好补齐型号、序列号、安装位置和质保到期时间。',
      '画布节点太密时，先重置视图，再切换横向或纵向布局。'
    ]
  },
  {
    name: '自动巡检',
    messages: [
      '自动化巡检先选测试目标，再配置工具和巡检步骤。',
      'HTTP 健康检测适合看服务接口，TCP 端口检测适合看基础连通性。',
      '服务器服务状态检测要对准服务器，不要把现场、平台、服务器层级混在一起。',
      '巡检记录里的调用信息很重要，排错时别只看成功或失败。',
      '模板改动不会改写历史报告，报告快照可以放心回溯。',
      '今日异常要优先处理，再看最近 7 天趋势。'
    ]
  },
  {
    name: '平台运维',
    messages: [
      '版本记录中心可以回查菜单、接口、SQL 和前端改动。',
      '如果页面报错，先看网络请求，再看后端日志，最后定位到服务和表。',
      '白名单管理先确认名单状态和车牌规则，再处理导入导出。',
      '表格批量操作前，先确认筛选条件和当前页数据范围。',
      '离线部署时，前端资源必须全部随包发布，不能依赖外网。',
      '变更上线后，记得用真实账号做一次菜单和核心页面烟测。'
    ]
  }
]

const interactiveMessages = [
  {
    selector: '.sidebar-container .el-sub-menu__title',
    message: (text) => `这里是“${text || '业务'}”目录，展开后再进入具体页面。`
  },
  {
    selector: '.sidebar-container .el-menu-item',
    message: (text) => `准备进入“${text || '当前'}”页面，我继续在左下角值守。`
  },
  {
    selector: '#hamburger-container',
    message: () => '收起左侧菜单后，我会自动隐藏，给工作区让位置。'
  },
  {
    selector: '.tags-view-container',
    message: () => '页签栏可以在已打开页面之间切换，排查问题时很省时间。'
  },
  {
    selector: '.el-table__row',
    message: () => '这行记录可以重点看状态、更新时间和关联现场。'
  },
  {
    selector: '.el-button--primary',
    message: (text) => `即将执行“${text || '主要'}”操作，先确认当前页面和表单内容。`
  },
  {
    selector: '.el-dialog',
    message: () => '弹窗里的配置通常会影响现场、平台或巡检规则，保存前再扫一遍。'
  }
]

watch(
  () => isVisible.value,
  (visible) => {
    if (visible) {
      startTicker()
      initLive2d()
    } else {
      stopTicker()
      destroyLive2d()
    }
  },
  { immediate: true }
)

onMounted(() => {
  document.addEventListener('mouseover', handleDocumentHover, true)
  document.addEventListener('click', handleDocumentClick, true)
  if (isVisible.value) {
    initLive2d()
  }
})

onUnmounted(() => {
  document.removeEventListener('mouseover', handleDocumentHover, true)
  document.removeEventListener('click', handleDocumentClick, true)
  destroyLive2d()
  stopTicker()
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
})

function getPublicPath(path) {
  const base = import.meta.env.BASE_URL || '/'
  return `${base.replace(/\/?$/, '/')}${path.replace(/^\//, '')}`
}

function loadScript(src, id) {
  const existing = document.getElementById(id)
  if (existing) {
    return Promise.resolve()
  }

  return new Promise((resolve, reject) => {
    const tag = document.createElement('script')
    tag.id = id
    tag.src = src
    tag.onload = resolve
    tag.onerror = (error) => {
      tag.remove()
      reject(error)
    }
    document.head.appendChild(tag)
  })
}

async function initLive2d() {
  if (!canvasRef.value || live2dModel || loadingPromise || !isVisible.value) {
    return loadingPromise
  }

  loadError.value = false
  loadingPromise = (async () => {
    try {
      await loadScript(getPublicPath(CORE_PATH), LIVE2D_CORE_ID)
      const [{ default: Cubism2Model }, modelSetting] = await Promise.all([
        import('@/vendor/live2d-widget/cubism2/index.js'),
        fetch(getPublicPath(MODEL_PATH)).then((response) => {
          if (!response.ok) {
            throw new Error(`模型配置加载失败：${response.status}`)
          }
          return response.json()
        })
      ])

      if (!window.Live2D || !window.Live2DModelWebGL) {
        throw new Error('Live2D Cubism2 runtime is unavailable')
      }

      if (!isVisible.value || !canvasRef.value) {
        return
      }

      live2dModel = new Cubism2Model()
      await live2dModel.init('live2d', getPublicPath(MODEL_PATH), modelSetting)

      if (!live2dModel.gl) {
        throw new Error('WebGL context is unavailable')
      }
      if (!isVisible.value) {
        destroyLive2d()
        return
      }

      modelReady.value = true
      showMessage('本地 Live2D 模型已上线，我会继续盯着现场和巡检状态。')
    } catch (error) {
      console.warn('[SidebarMascot] Live2D load failed:', error)
      loadError.value = true
      showMessage('本地模型加载失败，请检查离线包里的 live2d 资源是否完整。')
    } finally {
      loadingPromise = null
    }
  })()

  return loadingPromise
}

function destroyLive2d() {
  if (live2dModel && typeof live2dModel.destroy === 'function') {
    live2dModel.destroy()
  }
  live2dModel = null
  modelReady.value = false
}

function getCurrentMessages() {
  return topicMessages[topicIndex.value].messages
}

function getGreetingMessage() {
  const hour = new Date().getHours()
  if (hour < 6) {
    return '夜间值守中，异常信息优先看巡检记录和服务状态。'
  }
  if (hour < 9) {
    return '早上好，先看今日运行状态和待处理异常。'
  }
  if (hour < 12) {
    return '上午适合核对现场、平台、服务器配置链路。'
  }
  if (hour < 18) {
    return '下午继续盯紧现场融合管理和自动化巡检结果。'
  }
  return '今天的变更记得留痕，版本记录中心以后能帮上忙。'
}

function showMessage(message) {
  if (!message || !isVisible.value) {
    return
  }
  activeMessage.value = message
  bubbleRefreshing.value = true
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
  refreshTimer = setTimeout(() => {
    bubbleRefreshing.value = false
  }, 260)
}

function showNextMessage() {
  const messages = getCurrentMessages()
  messageIndex.value = (messageIndex.value + 1) % messages.length
  showMessage(messages[messageIndex.value])
}

function switchTopic() {
  topicIndex.value = (topicIndex.value + 1) % topicMessages.length
  messageIndex.value = 0
  const topic = topicMessages[topicIndex.value]
  showMessage(`已切换到“${topic.name}”提示：${topic.messages[0]}`)
}

function startTicker() {
  stopTicker()
  ticker = setInterval(showNextMessage, 12000)
}

function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
}

function getElementText(element) {
  return (element?.innerText || element?.textContent || '').replace(/\s+/g, '').slice(0, 18)
}

function findInteractiveMessage(target) {
  if (!(target instanceof Element)) {
    return null
  }
  for (const item of interactiveMessages) {
    const element = target.closest(item.selector)
    if (element) {
      return item.message(getElementText(element))
    }
  }
  return null
}

function handleDocumentHover(event) {
  const message = findInteractiveMessage(event.target)
  if (message) {
    showMessage(message)
  }
}

function handleDocumentClick(event) {
  const message = findInteractiveMessage(event.target)
  if (message) {
    showMessage(`${message} 操作后记得看页面反馈。`)
  }
}
</script>

<style lang="scss" scoped>
.sidebar-mascot {
  position: fixed;
  bottom: 0;
  left: 8px;
  z-index: 1002;
  width: 184px;
  height: 238px;
  font-size: 12px;
  pointer-events: none;
}

.mascot-bubble {
  position: absolute;
  top: 0;
  left: 8px;
  right: 8px;
  z-index: 2;
  min-height: 44px;
  max-height: 74px;
  padding: 7px 9px;
  overflow: hidden;
  color: #35506d;
  line-height: 18px;
  text-align: left;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(119, 170, 226, 0.32);
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(62, 104, 152, 0.12);
  transition: transform 0.24s ease, opacity 0.24s ease;
  pointer-events: auto;

  &.is-refreshing {
    transform: translateY(-2px);
  }
}

.mascot-stage {
  position: absolute;
  right: 0;
  bottom: -10px;
  left: 0;
  height: 190px;
  pointer-events: none;
}

.mascot-canvas {
  position: absolute;
  right: -54px;
  bottom: 0;
  width: 292px;
  height: 292px;
  cursor: pointer;
  filter: drop-shadow(0 12px 18px rgba(45, 126, 247, 0.18));
  pointer-events: auto;
}

.mascot-loading {
  position: absolute;
  right: 24px;
  bottom: 52px;
  display: grid;
  place-items: center;
  width: 130px;
  height: 72px;
  color: #5f7896;
  background: rgba(255, 255, 255, 0.82);
  border: 1px dashed rgba(119, 170, 226, 0.36);
  border-radius: 8px;
  pointer-events: none;
}

.mascot-actions {
  position: absolute;
  top: 88px;
  right: 4px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 5px 4px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(119, 170, 226, 0.28);
  border-radius: 8px;
  box-shadow: 0 8px 18px rgba(62, 104, 152, 0.12);
  opacity: 0;
  transform: translateX(4px);
  transition: opacity 0.2s ease, transform 0.2s ease;
  pointer-events: auto;

  button {
    display: grid;
    place-items: center;
    width: 22px;
    height: 22px;
    padding: 0;
    cursor: pointer;
    background: transparent;
    border: 0;
    border-radius: 6px;

    &:hover {
      background: rgba(45, 126, 247, 0.1);
    }
  }

  svg {
    width: 15px;
    height: 15px;
    fill: #5f7896;
  }
}

.sidebar-mascot:hover {
  .mascot-actions {
    opacity: 1;
    transform: translateX(0);
  }
}

:global(html.dark .sidebar-mascot .mascot-bubble),
:global(html.dark .sidebar-mascot .mascot-actions),
:global(html.dark .sidebar-mascot .mascot-loading) {
  color: var(--app-text, #d8e2f0);
  background: rgba(15, 22, 32, 0.92);
  border-color: rgba(116, 142, 174, 0.32);
  box-shadow: 0 14px 28px rgba(0, 0, 0, 0.32);
}

:global(html.dark .sidebar-mascot .mascot-actions button:hover) {
  background: rgba(45, 126, 247, 0.18);
}

:global(html.dark .sidebar-mascot .mascot-actions svg) {
  fill: var(--app-muted, #8fa4bd);
}

:global(html.dark .sidebar-mascot .mascot-canvas) {
  filter: drop-shadow(0 14px 22px rgba(45, 126, 247, 0.22));
}
</style>
