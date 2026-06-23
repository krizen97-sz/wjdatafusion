<template>
  <aside
    v-show="isVisible"
    class="sidebar-mascot"
    :class="mascotClasses"
    aria-label="平台看板娘"
    @mouseenter="handleMascotEnter"
  >
    <div
      v-if="dialogEnabled"
      :key="bubbleKey"
      class="mascot-bubble"
      :class="[
        { 'is-refreshing': bubbleRefreshing },
        `is-${bubbleVariant}`
      ]"
      @click.stop="handleBubbleClick"
    >
      <span class="mascot-bubble__text">{{ activeMessage }}</span>
      <span class="mascot-bubble__spark" aria-hidden="true" />
    </div>

    <div class="mascot-stage">
      <canvas
        id="live2d"
        ref="canvasRef"
        class="mascot-canvas"
        width="800"
        height="800"
        :title="canvasTitle"
        @click.stop="handleCanvasClick"
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
        <button
          v-if="playEnabled"
          type="button"
          :title="playMode ? '退出陪玩模式' : '放大陪玩'"
          :aria-pressed="playMode"
          @click="togglePlayMode"
        >
          <svg v-if="!playMode" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 9a1 1 0 0 1-1-1V4.5A1.5 1.5 0 0 1 4.5 3H8a1 1 0 0 1 0 2H6.4l3.3 3.3a1 1 0 1 1-1.4 1.4L5 6.4V8a1 1 0 0 1-1 1Zm12-6h3.5A1.5 1.5 0 0 1 21 4.5V8a1 1 0 1 1-2 0V6.4l-3.3 3.3a1 1 0 1 1-1.4-1.4L17.6 5H16a1 1 0 1 1 0-2ZM9.7 15.7 6.4 19H8a1 1 0 1 1 0 2H4.5A1.5 1.5 0 0 1 3 19.5V16a1 1 0 1 1 2 0v1.6l3.3-3.3a1 1 0 0 1 1.4 1.4Zm6 0a1 1 0 0 1-1.4-1.4l3.3-3.3H16a1 1 0 1 1 0-2h3.5A1.5 1.5 0 0 1 21 10.5V14a1 1 0 1 1-2 0v-1.6l-3.3 3.3Z" />
          </svg>
          <svg v-else viewBox="0 0 24 24" aria-hidden="true">
            <path d="M9 3a1 1 0 0 1 1 1v3.5A1.5 1.5 0 0 1 8.5 9H5a1 1 0 0 1 0-2h1.6L3.3 3.7a1 1 0 1 1 1.4-1.4L8 5.6V4a1 1 0 0 1 1-1Zm6 0a1 1 0 0 1 1 1v1.6l3.3-3.3a1 1 0 1 1 1.4 1.4L17.4 7H19a1 1 0 1 1 0 2h-3.5A1.5 1.5 0 0 1 14 7.5V4a1 1 0 0 1 1-1ZM5 15h3.5a1.5 1.5 0 0 1 1.5 1.5V20a1 1 0 1 1-2 0v-1.6l-3.3 3.3a1 1 0 0 1-1.4-1.4L6.6 17H5a1 1 0 1 1 0-2Zm10.5 0H19a1 1 0 1 1 0 2h-1.6l3.3 3.3a1 1 0 0 1-1.4 1.4L16 18.4V20a1 1 0 1 1-2 0v-3.5a1.5 1.5 0 0 1 1.5-1.5Z" />
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
  getMascotPlayMessage,
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
const emit = defineEmits(['play-change'])

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
const playMode = ref(false)
const tapRegion = ref('idle')
const activeMessage = ref(getMascotGreeting())
const bubbleRefreshing = ref(false)
const bubbleVariant = ref('idle')
const bubbleKey = ref(0)

let live2dModel = null
let loadingPromise = null
let ticker = null
let refreshTimer = null
let guideTimer = null
let pulseTimer = null
let tapTimer = null
let lastInteractionMessage = ''
let lastInteractionAt = 0
let lastPlayRegion = ''
let lastPlayRegionAt = 0

const isMobile = computed(() => width.value < WIDTH)
const isVisible = computed(() => !props.collapsed && !isMobile.value)
const dialogEnabled = computed(() => mascotDialogConfig.enabled !== false)
const mascotMessages = mascotDialogConfig.messages || {}
const playConfig = computed(() => mascotDialogConfig.play || {})
const playEnabled = computed(() => playConfig.value.enabled !== false)
const topicMessages = computed(() => getMascotTopics(mascotDialogConfig))
const interactiveMessages = computed(() => getMascotInteractions(mascotDialogConfig))
const currentGuide = computed(() => getMascotGuide(route.path, mascotDialogConfig))
const mascotClasses = computed(() => ({
  'is-guiding': guideActive.value,
  'is-play-mode': playMode.value,
  [`is-tap-${tapRegion.value}`]: tapRegion.value !== 'idle'
}))
const canvasTitle = computed(() => playMode.value ? '点击不同部位试试' : '点击我换一句提示')

watch(
  () => isVisible.value,
  (visible) => {
    if (visible) {
      startTicker()
      initLive2d()
    } else {
      playMode.value = false
      tapRegion.value = 'idle'
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

watch(
  () => playMode.value,
  (value) => {
    emit('play-change', value)
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
  emit('play-change', false)
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
  if (tapTimer) {
    clearTimeout(tapTimer)
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

function showMessage(message, options = {}) {
  if (!message || !isVisible.value || !dialogEnabled.value) {
    return
  }
  activeMessage.value = message
  bubbleVariant.value = options.variant || (playMode.value ? 'play' : 'idle')
  bubbleKey.value += 1
  bubbleRefreshing.value = true
  if (refreshTimer) {
    clearTimeout(refreshTimer)
  }
  refreshTimer = setTimeout(() => {
    bubbleRefreshing.value = false
  }, 260)
}

function handleMascotEnter() {
  if (playMode.value) {
    showMessage(playConfig.value.hint || mascotMessages.hoverSelf, { variant: 'play' })
    return
  }
  showMessage(mascotMessages.hoverSelf)
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

function togglePlayMode() {
  if (!playEnabled.value) {
    return
  }

  playMode.value = !playMode.value
  tapRegion.value = 'idle'
  guideActive.value = false

  if (tapTimer) {
    clearTimeout(tapTimer)
  }

  if (playMode.value) {
    stopTicker()
    showMessage(playConfig.value.enter || playConfig.value.hint, { variant: 'play' })
    playModelFeedback('body')
    return
  }

  startTicker()
  showMessage(playConfig.value.exit || mascotMessages.hoverSelf)
}

function handleBubbleClick() {
  if (playMode.value) {
    showPlayMessage('bubble')
    return
  }
  showNextMessage()
}

function handleCanvasClick(event) {
  const region = resolveCanvasRegion(event)

  if (!playMode.value) {
    showNextMessage()
    return
  }

  showPlayMessage(region)
}

function resolveCanvasRegion(event) {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect?.width || !rect?.height) {
    return 'around'
  }

  const x = (event.clientX - rect.left) / rect.width
  const y = (event.clientY - rect.top) / rect.height

  if (x > 0.36 && x < 0.64 && y > 0.18 && y < 0.34) {
    return 'face'
  }
  if (x > 0.3 && x < 0.7 && y > 0.12 && y < 0.43) {
    return 'head'
  }
  if (((x > 0.12 && x < 0.34) || (x > 0.66 && x < 0.88)) && y > 0.42 && y < 0.78) {
    return 'hand'
  }
  if (x > 0.28 && x < 0.72 && y >= 0.38 && y < 0.9) {
    return 'body'
  }
  return 'around'
}

function showPlayMessage(region = 'around') {
  const now = Date.now()
  if (region === lastPlayRegion && now - lastPlayRegionAt < 180) {
    return
  }

  lastPlayRegion = region
  lastPlayRegionAt = now
  tapRegion.value = region
  showMessage(getMascotPlayMessage(region, mascotDialogConfig), { variant: 'play' })
  playModelFeedback(region)

  if (tapTimer) {
    clearTimeout(tapTimer)
  }
  tapTimer = setTimeout(() => {
    tapRegion.value = 'idle'
  }, 760)
}

function playModelFeedback(region) {
  const model = live2dModel?.live2DMgr?.model
  if (!model) {
    return
  }

  try {
    if (region === 'head' || region === 'face') {
      model.setRandomExpression?.()
      model.startRandomMotion?.('flick_head', 2)
      return
    }
    if (region === 'body' || region === 'hand' || region === 'bubble') {
      model.startRandomMotion?.('tap_body', 2)
    }
  } catch (error) {
    console.debug('[SidebarMascot] motion feedback skipped:', error)
  }
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
  if (playMode.value) {
    showPlayMessage('body')
    return
  }
  showMessage(mascotMessages.bodyTap)
  showGuideStep(true)
}

function handleBodyHover() {
  if (playMode.value) {
    showInteractionMessage('陪玩模式里点不同部位，我会给不同提示。')
    return
  }
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
  transition: width 0.24s ease, height 0.24s ease, bottom 0.24s ease, left 0.24s ease;
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
  animation: mascot-bubble-in 0.24s ease both;
  cursor: pointer;
  pointer-events: auto;

  &.is-refreshing {
    transform: translateY(-2px);
  }

  &.is-play {
    animation: mascot-bubble-play 0.32s cubic-bezier(0.2, 0.9, 0.2, 1.1) both;
  }
}

.mascot-bubble__text {
  position: relative;
  z-index: 1;
  display: block;
}

.mascot-bubble__spark {
  position: absolute;
  right: 11px;
  bottom: 8px;
  width: 24px;
  height: 2px;
  background: linear-gradient(90deg, rgba(45, 126, 247, 0), rgba(45, 126, 247, 0.42));
  border-radius: 999px;
  transform-origin: right center;
  opacity: 0;
}

.mascot-stage {
  position: absolute;
  right: 0;
  bottom: -10px;
  left: 0;
  height: 190px;
  transition: height 0.24s ease, bottom 0.24s ease;
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
  transition: right 0.24s ease, bottom 0.24s ease, width 0.24s ease, height 0.24s ease, filter 0.24s ease;
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

    &[aria-pressed="true"] {
      background: rgba(45, 126, 247, 0.16);

      svg {
        fill: #2d7ef7;
      }
    }
  }

  svg {
    width: 15px;
    height: 15px;
    fill: #5f7896;
  }
}

.sidebar-mascot.is-play-mode {
  bottom: 12px;
  left: 16px;
  z-index: 2100;
  width: 360px;
  height: 500px;
  pointer-events: auto;

  .mascot-bubble {
    top: 0;
    right: 18px;
    left: 4px;
    min-height: 60px;
    max-height: 126px;
    padding: 12px 14px;
    font-size: 13px;
    line-height: 20px;
    border-color: rgba(45, 126, 247, 0.42);
    box-shadow: 0 18px 42px rgba(45, 126, 247, 0.16);
  }

  .mascot-bubble__spark {
    animation: mascot-spark 0.62s ease both;
  }

  .mascot-stage {
    bottom: -4px;
    height: 430px;
    pointer-events: auto;
  }

  .mascot-canvas {
    right: -92px;
    bottom: -16px;
    width: 520px;
    height: 520px;
    filter: drop-shadow(0 24px 34px rgba(45, 126, 247, 0.28));
    animation: mascot-breathe 4.8s ease-in-out infinite;
  }

  .mascot-loading {
    right: 78px;
    bottom: 170px;
  }

  .mascot-actions {
    top: 124px;
    right: 6px;
    gap: 6px;
    opacity: 1;
    transform: translateX(0);

    button {
      width: 28px;
      height: 28px;
    }

    svg {
      width: 16px;
      height: 16px;
    }
  }
}

.sidebar-mascot.is-play-mode.is-tap-head,
.sidebar-mascot.is-play-mode.is-tap-face {
  .mascot-canvas {
    animation: mascot-nod 0.62s cubic-bezier(0.2, 0.8, 0.2, 1), mascot-breathe 4.8s ease-in-out infinite;
  }
}

.sidebar-mascot.is-play-mode.is-tap-body {
  .mascot-canvas {
    animation: mascot-bounce 0.64s cubic-bezier(0.2, 0.8, 0.2, 1), mascot-breathe 4.8s ease-in-out infinite;
  }
}

.sidebar-mascot.is-play-mode.is-tap-hand {
  .mascot-canvas {
    animation: mascot-wave 0.72s cubic-bezier(0.2, 0.8, 0.2, 1), mascot-breathe 4.8s ease-in-out infinite;
  }
}

.sidebar-mascot.is-play-mode.is-tap-bubble {
  .mascot-bubble {
    animation: mascot-bubble-play 0.32s cubic-bezier(0.2, 0.9, 0.2, 1.1), mascot-bubble-jump 0.58s ease;
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

:global(html.dark .sidebar-mascot.is-play-mode .mascot-bubble) {
  border-color: rgba(70, 150, 255, 0.5);
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.4);
}

@keyframes mascot-bubble-in {
  0% {
    opacity: 0;
    transform: translateY(5px) scale(0.98);
  }

  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes mascot-bubble-play {
  0% {
    opacity: 0;
    transform: translateY(8px) scale(0.96);
  }

  68% {
    opacity: 1;
    transform: translateY(-3px) scale(1.015);
  }

  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes mascot-bubble-jump {
  0%, 100% {
    transform: translateY(0);
  }

  42% {
    transform: translateY(-6px);
  }
}

@keyframes mascot-spark {
  0% {
    opacity: 0;
    transform: scaleX(0.2);
  }

  40% {
    opacity: 1;
    transform: scaleX(1);
  }

  100% {
    opacity: 0;
    transform: scaleX(0.8) translateX(-12px);
  }
}

@keyframes mascot-breathe {
  0%, 100% {
    transform: translateY(0) scale(1);
  }

  50% {
    transform: translateY(-4px) scale(1.012);
  }
}

@keyframes mascot-nod {
  0%, 100% {
    transform: translateY(0) rotate(0deg) scale(1);
  }

  34% {
    transform: translateY(8px) rotate(-2deg) scale(1.01);
  }

  66% {
    transform: translateY(-5px) rotate(1deg) scale(1.01);
  }
}

@keyframes mascot-bounce {
  0%, 100% {
    transform: translateY(0) scale(1);
  }

  36% {
    transform: translateY(-12px) scale(1.018);
  }

  70% {
    transform: translateY(4px) scale(0.995);
  }
}

@keyframes mascot-wave {
  0%, 100% {
    transform: translateX(0) rotate(0deg) scale(1);
  }

  30% {
    transform: translateX(-9px) rotate(-2.2deg) scale(1.01);
  }

  62% {
    transform: translateX(7px) rotate(1.6deg) scale(1.01);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sidebar-mascot,
  .mascot-stage,
  .mascot-canvas,
  .mascot-bubble,
  .mascot-actions {
    transition: none;
    animation: none !important;
  }
}
</style>
