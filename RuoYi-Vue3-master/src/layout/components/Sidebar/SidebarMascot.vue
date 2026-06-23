<template>
  <aside
    v-show="isVisible"
    class="sidebar-mascot"
    :class="{ 'is-guiding': guideActive }"
    aria-label="平台看板娘"
    @mouseenter="showMessage(mascotMessages.hoverSelf)"
  >
    <div v-if="dialogEnabled" class="mascot-bubble" :class="{ 'is-refreshing': bubbleRefreshing }">
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

      <div v-if="dialogEnabled" class="mascot-actions" aria-label="看板娘操作">
        <button type="button" title="切换提示主题" @click="switchTopic">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 3a9 9 0 0 0-8.8 7.1 1 1 0 1 0 2 .4A7 7 0 0 1 17 6.7V9a1 1 0 1 0 2 0V4a1 1 0 0 0-1-1h-5a1 1 0 1 0 0 2h2.4A8.9 8.9 0 0 0 12 3Zm7.6 10.7a1 1 0 0 0-1.2.8A7 7 0 0 1 7 17.3V15a1 1 0 1 0-2 0v5a1 1 0 0 0 1 1h5a1 1 0 1 0 0-2H8.6a8.9 8.9 0 0 0 12-4 1 1 0 0 0-1-1.3Z" />
          </svg>
        </button>
        <button type="button" title="当前页面操作指引" @click="showGuideStep()">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2.5a7 7 0 0 0-4 12.8V19a1 1 0 0 0 .6.9l3 1.5a1 1 0 0 0 .8 0l3-1.5a1 1 0 0 0 .6-.9v-3.7A7 7 0 0 0 12 2.5Zm-2 15.9v-1h4v1L12 19.4l-2-1Zm5-4.5-.4.3a1 1 0 0 0-.4.8v.4H9.8V15a1 1 0 0 0-.4-.8l-.4-.3A5 5 0 1 1 15 13.9ZM11 7.5a1 1 0 0 1 2 0V11a1 1 0 1 1-2 0V7.5Zm1 7.2a1.1 1.1 0 1 0 0-2.2 1.1 1.1 0 0 0 0 2.2Z" />
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
import {
  getMascotGreeting,
  getMascotGuide,
  getMascotInteractions,
  getMascotTopics,
  mascotDialogConfig,
  renderMascotTemplate
} from './mascotDialog'

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

const route = useRoute()
const { width } = useWindowSize()
const canvasRef = ref(null)
const modelReady = ref(false)
const loadError = ref(false)
const topicIndex = ref(0)
const messageIndex = ref(0)
const guideStepIndex = ref(-1)
const guideActive = ref(false)
const activeMessage = ref(getMascotGreeting())
const bubbleRefreshing = ref(false)

let live2dModel = null
let loadingPromise = null
let ticker = null
let refreshTimer = null
let guideTimer = null
let pulseTimer = null
let lastInteractionMessage = ''
let lastInteractionAt = 0

const isMobile = computed(() => width.value < WIDTH)
const isVisible = computed(() => !props.collapsed && !isMobile.value)
const dialogEnabled = computed(() => mascotDialogConfig.enabled !== false)
const mascotMessages = mascotDialogConfig.messages || {}
const topicMessages = computed(() => getMascotTopics(mascotDialogConfig))
const interactiveMessages = computed(() => getMascotInteractions(mascotDialogConfig))
const currentGuide = computed(() => getMascotGuide(route.path, mascotDialogConfig))

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

watch(
  () => route.path,
  () => {
    guideStepIndex.value = -1
    guideActive.value = false
    if (isVisible.value && dialogEnabled.value && currentGuide.value) {
      showMessage(`${currentGuide.value.title}已准备好，需要时点“操作指引”。`)
    }
  }
)

onMounted(() => {
  document.addEventListener('mouseover', handleDocumentHover, true)
  document.addEventListener('click', handleDocumentClick, true)
  window.addEventListener('live2d:tapbody', handleBodyTap)
  window.addEventListener('live2d:hoverbody', handleBodyHover)
  if (isVisible.value) {
    initLive2d()
  }
})

onUnmounted(() => {
  document.removeEventListener('mouseover', handleDocumentHover, true)
  document.removeEventListener('click', handleDocumentClick, true)
  window.removeEventListener('live2d:tapbody', handleBodyTap)
  window.removeEventListener('live2d:hoverbody', handleBodyHover)
  destroyLive2d()
  stopTicker()
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
  if (guideTimer) {
    clearTimeout(guideTimer)
  }
  if (pulseTimer) {
    clearTimeout(pulseTimer)
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
      showMessage(mascotMessages.modelReady)
    } catch (error) {
      console.warn('[SidebarMascot] Live2D load failed:', error)
      loadError.value = true
      showMessage(mascotMessages.modelError)
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
  const topics = topicMessages.value
  return topics[topicIndex.value]?.messages || []
}

function showMessage(message) {
  if (!message || !isVisible.value || !dialogEnabled.value) {
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
  if (!messages.length) {
    showGuideStep()
    return
  }
  messageIndex.value = (messageIndex.value + 1) % messages.length
  showMessage(messages[messageIndex.value])
}

function switchTopic() {
  const topics = topicMessages.value
  if (!topics.length) {
    showGuideStep()
    return
  }
  topicIndex.value = (topicIndex.value + 1) % topics.length
  messageIndex.value = 0
  const topic = topics[topicIndex.value]
  showMessage(`已切换到“${topic.name}”提示：${topic.messages[0]}`)
}

function showGuideStep(reset = false) {
  const guide = currentGuide.value
  if (!guide) {
    showNextMessage()
    return
  }

  if (reset === true) {
    guideStepIndex.value = 0
  } else {
    guideStepIndex.value = (guideStepIndex.value + 1) % guide.steps.length
  }

  guideActive.value = true
  if (guideTimer) {
    clearTimeout(guideTimer)
  }
  guideTimer = setTimeout(() => {
    guideActive.value = false
  }, 4200)
  showMessage(`${guide.title}：${guide.steps[guideStepIndex.value]}`)
}

function startTicker() {
  stopTicker()
  if (dialogEnabled.value) {
    ticker = setInterval(showNextMessage, mascotDialogConfig.idleInterval || 12000)
  }
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
  if (target.closest('.sidebar-mascot')) {
    return null
  }
  for (const item of interactiveMessages.value) {
    const element = target.closest(item.selector)
    if (element) {
      return { element, item }
    }
  }
  return null
}

function handleDocumentHover(event) {
  const result = findInteractiveMessage(event.target)
  if (result && (result.item.event === 'both' || result.item.event === 'hover')) {
    showInteractionMessage(renderMascotTemplate(result.item.template, { text: getElementText(result.element) }))
  }
}

function handleDocumentClick(event) {
  const result = findInteractiveMessage(event.target)
  if (result && (result.item.event === 'both' || result.item.event === 'click')) {
    const message = renderMascotTemplate(result.item.template, { text: getElementText(result.element) })
    showInteractionMessage(`${message} 操作后记得看页面反馈。`, true)
  }
}

function showInteractionMessage(message, immediate = false) {
  const now = Date.now()
  if (!immediate && message === lastInteractionMessage && now - lastInteractionAt < 3000) {
    return
  }
  if (!immediate && now - lastInteractionAt < 700) {
    return
  }
  lastInteractionMessage = message
  lastInteractionAt = now
  showMessage(message)
}

function handleBodyTap() {
  pulseMascot()
  showMessage(mascotMessages.bodyTap)
  showGuideStep(true)
}

function handleBodyHover() {
  showInteractionMessage('点击我可以切到当前页面操作指引。')
}

function pulseMascot() {
  guideActive.value = true
  if (pulseTimer) {
    clearTimeout(pulseTimer)
  }
  pulseTimer = setTimeout(() => {
    guideActive.value = false
  }, 900)
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

.sidebar-mascot.is-guiding {
  .mascot-bubble {
    border-color: rgba(45, 126, 247, 0.42);
    box-shadow: 0 12px 28px rgba(45, 126, 247, 0.18);
  }

  .mascot-canvas {
    filter: drop-shadow(0 14px 22px rgba(45, 126, 247, 0.28));
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
