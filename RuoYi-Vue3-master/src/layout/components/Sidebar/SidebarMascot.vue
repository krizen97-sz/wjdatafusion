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
      <span v-if="playMode" class="mascot-bubble__beat" aria-hidden="true">
        <i />
        <i />
        <i />
      </span>
    </div>

    <div class="mascot-stage">
      <canvas
        id="live2d"
        ref="canvasRef"
        class="mascot-canvas"
        width="800"
        height="800"
        :title="canvasTitle"
        :aria-label="canvasTitle"
        role="button"
        tabindex="0"
        @click.stop="handleCanvasClick"
        @keydown.enter.prevent="handleCanvasKeyboard"
        @keydown.space.prevent="handleCanvasKeyboard"
      />
      <div v-if="!modelReady" class="mascot-loading">
        {{ loadError ? '模型加载失败' : '模型加载中' }}
      </div>

      <div class="mascot-actions" aria-label="看板娘操作">
        <button
          v-if="playEnabled"
          type="button"
          :title="playMode ? '缩小看板娘' : '放大看板娘'"
          :aria-label="playMode ? '缩小看板娘' : '放大看板娘'"
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
        <button
          type="button"
          title="隐藏看板娘"
          aria-label="隐藏看板娘"
          @click.stop="hideMascot"
        >
          <Hide aria-hidden="true" />
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { useWindowSize } from '@vueuse/core'
import useUserStore from '@/store/modules/user'
import {
  getMascotGreeting,
  getMascotGuide,
  getMascotInteractions,
  getMascotPlayMessage,
  renderMascotTemplate,
  mascotDialogConfig
} from './mascotDialog'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false
  }
})
const emit = defineEmits(['play-change', 'hide'])

const WIDTH = 992
const LIVE2D_CORE_ID = 'ry-local-live2d-core'
const MODEL_PATH = 'live2d/models/pio/index.json'
const CORE_PATH = 'live2d/vendor/live2d-widget/live2d.min.js'
const INTERACTION_HOVER_INTERVAL = 900

const route = useRoute()
const userStore = useUserStore()
const { width } = useWindowSize()
const canvasRef = ref(null)
const modelReady = ref(false)
const loadError = ref(false)
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
let lastHoverElement = null
let lastHoverAt = 0
let componentAlive = true

const isMobile = computed(() => width.value < WIDTH)
const isVisible = computed(() => !props.collapsed && !isMobile.value)
const dialogEnabled = computed(() => mascotDialogConfig.enabled !== false)
const mascotMessages = mascotDialogConfig.messages || {}
const playConfig = computed(() => mascotDialogConfig.play || {})
const playEnabled = computed(() => playConfig.value.enabled !== false)
const interactiveMessages = computed(() => getMascotInteractions(mascotDialogConfig))
const currentGuide = computed(() => getMascotGuide(route.path, mascotDialogConfig))
const userNickname = computed(() => userStore.nickName || userStore.name || '管理员')
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
      showPagePrompt(true)
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
    if (isVisible.value && dialogEnabled.value && currentGuide.value && !playMode.value) {
      showPagePrompt(true)
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
  componentAlive = false
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

      if (!componentAlive || !isVisible.value || !canvasRef.value) {
        return
      }

      const model = new Cubism2Model()
      await model.init('live2d', getPublicPath(MODEL_PATH), modelSetting)

      if (!model.gl) {
        throw new Error('WebGL context is unavailable')
      }
      if (!componentAlive || !isVisible.value) {
        model.destroy()
        return
      }

      live2dModel = model
      modelReady.value = true
      showPagePrompt(true, mascotMessages.modelReady)
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
  showPagePrompt()
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
  showPagePrompt(true, playConfig.value.exit || mascotMessages.hoverSelf)
}

function hideMascot() {
  playMode.value = false
  tapRegion.value = 'idle'
  emit('hide')
}

function handleBubbleClick() {
  if (playMode.value) {
    showPlayMessage('bubble')
    return
  }
  showPagePrompt()
}

function handleCanvasClick(event) {
  const region = resolveCanvasRegion(event)

  if (!playMode.value) {
    showPagePrompt()
    return
  }

  showPlayMessage(region)
}

function handleCanvasKeyboard() {
  if (playMode.value) {
    showPlayMessage('body')
    return
  }
  showPagePrompt()
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

function showPagePrompt(reset = false, fallback = '') {
  const guide = currentGuide.value
  if (!guide) {
    showMessage(formatMascotMessage(fallback || mascotMessages.hoverSelf))
    return
  }

  if (reset === true) {
    guideStepIndex.value = 0
  } else {
    guideStepIndex.value = (guideStepIndex.value + 1) % guide.steps.length
  }

  showMessage(formatGuideStep(guide, guide.steps[guideStepIndex.value]))
}

function formatGuideStep(guide, step) {
  const routeTitle = route.meta?.title || guide.title || '当前页面'
  return `${guide.title}：${renderMascotTemplate(step, {
    nickname: userNickname.value,
    routeTitle,
    title: guide.title,
    fallback: userNickname.value
  })}`
}

function formatMascotMessage(message = '') {
  return renderMascotTemplate(message, {
    nickname: userNickname.value,
    routeTitle: route.meta?.title || currentGuide.value?.title || '当前页面',
    title: currentGuide.value?.title || '平台提示',
    fallback: userNickname.value
  })
}

function highlightGuidePulse() {
  guideActive.value = true
  if (guideTimer) {
    clearTimeout(guideTimer)
  }
  guideTimer = setTimeout(() => {
    guideActive.value = false
  }, 4200)
}

function startTicker() {
  stopTicker()
  if (dialogEnabled.value) {
    ticker = setInterval(showPagePrompt, mascotDialogConfig.idleInterval || 12000)
  }
}

function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
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
  if (playMode.value || !isVisible.value || !dialogEnabled.value) {
    return
  }

  const result = findInteractiveMessage(event.target)
  if (result && (result.item.event === 'both' || result.item.event === 'hover')) {
    const now = Date.now()
    if (result.element === lastHoverElement && now - lastHoverAt < INTERACTION_HOVER_INTERVAL * 2) {
      return
    }
    if (now - lastHoverAt < INTERACTION_HOVER_INTERVAL) {
      return
    }
    lastHoverElement = result.element
    lastHoverAt = now
    showPagePrompt()
  }
}

function handleDocumentClick(event) {
  if (playMode.value || !isVisible.value || !dialogEnabled.value) {
    return
  }

  const result = findInteractiveMessage(event.target)
  if (result && (result.item.event === 'both' || result.item.event === 'click')) {
    lastHoverElement = null
    lastHoverAt = 0
    showPagePrompt(true)
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
  showPagePrompt(true)
  highlightGuidePulse()
}

function handleBodyHover() {
  if (playMode.value) {
    showInteractionMessage(playConfig.value.hover || playConfig.value.hint)
    return
  }
  showInteractionMessage(formatMascotMessage('{nickname}，点击我可以切到当前页面操作指引。'))
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
  position: absolute;
  bottom: 0;
  left: 8px;
  z-index: 1002;
  width: 184px;
  height: 238px;
  font-size: 12px;
  transition: opacity 0.24s ease, transform 0.24s ease;
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
  color: var(--app-text);
  line-height: 18px;
  text-align: left;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(119, 170, 226, 0.32);
  border-radius: 8px;
  box-shadow: 0 10px 24px rgba(62, 104, 152, 0.12);
  transition: transform 0.24s ease, opacity 0.24s ease;
  animation: mascot-bubble-in 0.24s ease both;
  cursor: pointer;
  will-change: transform, opacity;
  pointer-events: auto;

  &::after {
    position: absolute;
    bottom: -6px;
    left: 42px;
    width: 13px;
    height: 13px;
    content: '';
    background: inherit;
    border-right: inherit;
    border-bottom: inherit;
    border-radius: 3px;
    opacity: 0;
    transform: rotate(45deg) scale(0.72);
    transform-origin: center;
  }

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

.mascot-bubble__beat {
  position: absolute;
  right: 13px;
  bottom: 10px;
  z-index: 1;
  display: none;
  align-items: end;
  gap: 3px;
  height: 12px;
  pointer-events: none;

  i {
    display: block;
    width: 3px;
    height: 5px;
    background: rgba(45, 126, 247, 0.42);
    border-radius: 999px;
    animation: mascot-beat 0.72s ease-in-out infinite;

    &:nth-child(2) {
      animation-delay: 0.1s;
    }

    &:nth-child(3) {
      animation-delay: 0.2s;
    }
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
  transition: transform 0.24s ease, filter 0.24s ease;
  will-change: transform;
  pointer-events: auto;

  &:focus-visible {
    outline: 2px solid rgba(45, 126, 247, 0.72);
    outline-offset: -18px;
  }
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

    &:focus-visible {
      outline: 2px solid rgba(45, 126, 247, 0.72);
      outline-offset: 2px;
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
    overflow: visible;

    &::after {
      opacity: 1;
      animation: mascot-tail 1.6s ease-in-out infinite;
    }
  }

  .mascot-bubble__text {
    max-height: 100px;
    padding-right: 34px;
    overflow: hidden;
  }

  .mascot-bubble__spark {
    animation: mascot-spark 0.62s ease both;
  }

  .mascot-bubble__beat {
    display: flex;
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
  .mascot-bubble {
    transform: rotate(-0.8deg);
  }

  .mascot-canvas {
    animation: mascot-nod 0.62s cubic-bezier(0.2, 0.8, 0.2, 1), mascot-breathe 4.8s ease-in-out infinite;
  }
}

.sidebar-mascot.is-play-mode.is-tap-body {
  .mascot-bubble {
    transform: translateY(-2px) scale(1.01);
  }

  .mascot-canvas {
    animation: mascot-bounce 0.64s cubic-bezier(0.2, 0.8, 0.2, 1), mascot-breathe 4.8s ease-in-out infinite;
  }
}

.sidebar-mascot.is-play-mode.is-tap-hand {
  .mascot-bubble {
    transform: rotate(0.8deg);
  }

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

:global(html.dark .sidebar-mascot.is-play-mode .mascot-bubble__beat i) {
  background: rgba(106, 169, 255, 0.58);
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

@keyframes mascot-tail {
  0%, 100% {
    transform: rotate(45deg) translate(0, 0) scale(0.72);
  }

  50% {
    transform: rotate(45deg) translate(2px, 2px) scale(0.82);
  }
}

@keyframes mascot-beat {
  0%, 100% {
    height: 5px;
    opacity: 0.42;
  }

  50% {
    height: 12px;
    opacity: 0.9;
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
  .mascot-bubble__beat i,
  .mascot-actions {
    transition: none;
    animation: none !important;
  }
}
</style>
