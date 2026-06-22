<template>
  <section class="auth-scene" :class="sceneClasses">
    <div class="auth-mobile-brand">
      <img src="@/assets/logo/platform-logo.svg" alt="华东信息融合平台" />
      <span>华东信息融合平台</span>
    </div>

    <div class="auth-scene__desktop">
      <router-link to="/login" class="auth-brand" aria-label="华东信息融合平台">
        <img src="@/assets/logo/platform-logo.svg" alt="" />
        <span>华东信息融合平台</span>
      </router-link>

      <div class="auth-character-wrap" aria-hidden="true">
        <div ref="characterRoot" class="animated-characters">
          <div ref="purpleRef" class="character purple-character" :style="purpleStyle">
            <div class="eyes white-eyes purple-eyes" :style="purpleEyesStyle">
              <span class="eye-ball" :style="eyeStyle(18, isPurpleBlinking)">
                <i :style="pupilStyle(purplePupil)"></i>
              </span>
              <span class="eye-ball" :style="eyeStyle(18, isPurpleBlinking)">
                <i :style="pupilStyle(purplePupil)"></i>
              </span>
            </div>
          </div>

          <div ref="blackRef" class="character black-character" :style="blackStyle">
            <div class="eyes white-eyes black-eyes" :style="blackEyesStyle">
              <span class="eye-ball" :style="eyeStyle(16, isBlackBlinking)">
                <i :style="pupilStyle(blackPupil)"></i>
              </span>
              <span class="eye-ball" :style="eyeStyle(16, isBlackBlinking)">
                <i :style="pupilStyle(blackPupil)"></i>
              </span>
            </div>
          </div>

          <div ref="orangeRef" class="character orange-character" :style="orangeStyle">
            <div class="eyes pupil-eyes orange-eyes" :style="orangeEyesStyle">
              <span class="pupil-only" :style="pupilOnlyStyle(orangePupil)"></span>
              <span class="pupil-only" :style="pupilOnlyStyle(orangePupil)"></span>
            </div>
          </div>

          <div ref="yellowRef" class="character yellow-character" :style="yellowStyle">
            <div class="eyes pupil-eyes yellow-eyes" :style="yellowEyesStyle">
              <span class="pupil-only" :style="pupilOnlyStyle(yellowPupil)"></span>
              <span class="pupil-only" :style="pupilOnlyStyle(yellowPupil)"></span>
            </div>
            <div class="yellow-mouth" :style="yellowMouthStyle"></div>
          </div>
        </div>
      </div>

      <div class="auth-footer">
        <span>现场融合</span>
        <span>自动化巡检</span>
        <span>白名单</span>
        <span>版本记录</span>
      </div>
    </div>
  </section>
</template>

<script setup>
const props = defineProps({
  mode: {
    type: String,
    default: "login",
  },
  isTyping: {
    type: Boolean,
    default: false,
  },
  passwordVisible: {
    type: Boolean,
    default: false,
  },
  passwordLength: {
    type: Number,
    default: 0,
  },
})

const characterRoot = ref(null)
const purpleRef = ref(null)
const blackRef = ref(null)
const orangeRef = ref(null)
const yellowRef = ref(null)

const mouseX = ref(0)
const mouseY = ref(0)
const isPurpleBlinking = ref(false)
const isBlackBlinking = ref(false)
const isLookingAtEachOther = ref(false)
const isPurplePeeking = ref(false)

let purpleBlinkTimer
let purpleBlinkEndTimer
let blackBlinkTimer
let blackBlinkEndTimer
let lookingTimer
let peekTimer
let peekEndTimer

const isPasswordVisible = computed(() => props.passwordLength > 0 && props.passwordVisible)
const isHidingPassword = computed(() => props.passwordLength > 0 && !props.passwordVisible)

const sceneClasses = computed(() => ({
  "is-register": props.mode === "register",
  "is-typing": props.isTyping,
  "is-password": props.passwordLength > 0,
  "is-password-visible": props.passwordVisible,
}))

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function px(value) {
  return `${value}px`
}

function handleMouseMove(event) {
  mouseX.value = event.clientX
  mouseY.value = event.clientY
}

function calculatePosition(targetRef) {
  if (!targetRef.value) {
    return { faceX: 0, faceY: 0, bodySkew: 0 }
  }

  const rect = targetRef.value.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 3
  const deltaX = mouseX.value - centerX
  const deltaY = mouseY.value - centerY

  return {
    faceX: clamp(deltaX / 20, -15, 15),
    faceY: clamp(deltaY / 30, -10, 10),
    bodySkew: clamp(-deltaX / 120, -6, 6),
  }
}

const purplePos = computed(() => calculatePosition(purpleRef))
const blackPos = computed(() => calculatePosition(blackRef))
const orangePos = computed(() => calculatePosition(orangeRef))
const yellowPos = computed(() => calculatePosition(yellowRef))

function facePupil(pos, maxDistance = 5) {
  return {
    x: clamp(pos.faceX / 2.4, -maxDistance, maxDistance),
    y: clamp(pos.faceY / 2, -maxDistance, maxDistance),
  }
}

const purplePupil = computed(() => {
  if (isPasswordVisible.value) {
    return isPurplePeeking.value ? { x: 4, y: 5 } : { x: -4, y: -4 }
  }
  if (isLookingAtEachOther.value) {
    return { x: 3, y: 4 }
  }
  return facePupil(purplePos.value, 5)
})

const blackPupil = computed(() => {
  if (isPasswordVisible.value) {
    return { x: -4, y: -4 }
  }
  if (isLookingAtEachOther.value) {
    return { x: 0, y: -4 }
  }
  return facePupil(blackPos.value, 4)
})

const orangePupil = computed(() => {
  if (isPasswordVisible.value) {
    return { x: -5, y: -4 }
  }
  return facePupil(orangePos.value, 5)
})

const yellowPupil = computed(() => {
  if (isPasswordVisible.value) {
    return { x: -5, y: -4 }
  }
  return facePupil(yellowPos.value, 5)
})

const purpleStyle = computed(() => {
  const position = purplePos.value
  const shouldLift = props.isTyping || isHidingPassword.value
  const transform = isPasswordVisible.value
    ? "skewX(0deg)"
    : shouldLift
      ? `skewX(${position.bodySkew - 12}deg) translateX(40px)`
      : `skewX(${position.bodySkew}deg)`

  return {
    left: px(70),
    width: px(180),
    height: shouldLift ? px(440) : px(400),
    backgroundColor: "#6C3FF5",
    borderRadius: "10px 10px 0 0",
    zIndex: 1,
    transform,
  }
})

const blackStyle = computed(() => {
  const position = blackPos.value
  const transform = isPasswordVisible.value
    ? "skewX(0deg)"
    : isLookingAtEachOther.value
      ? `skewX(${position.bodySkew * 1.5 + 10}deg) translateX(20px)`
      : (props.isTyping || isHidingPassword.value)
        ? `skewX(${position.bodySkew * 1.5}deg)`
        : `skewX(${position.bodySkew}deg)`

  return {
    left: px(240),
    width: px(120),
    height: px(310),
    backgroundColor: "#2D2D2D",
    borderRadius: "8px 8px 0 0",
    zIndex: 2,
    transform,
  }
})

const orangeStyle = computed(() => ({
  left: px(0),
  width: px(240),
  height: px(200),
  backgroundColor: "#FF9B6B",
  borderRadius: "120px 120px 0 0",
  zIndex: 3,
  transform: isPasswordVisible.value ? "skewX(0deg)" : `skewX(${orangePos.value.bodySkew}deg)`,
}))

const yellowStyle = computed(() => ({
  left: px(310),
  width: px(140),
  height: px(230),
  backgroundColor: "#E8D754",
  borderRadius: "70px 70px 0 0",
  zIndex: 4,
  transform: isPasswordVisible.value ? "skewX(0deg)" : `skewX(${yellowPos.value.bodySkew}deg)`,
}))

const purpleEyesStyle = computed(() => ({
  left: isPasswordVisible.value ? px(20) : isLookingAtEachOther.value ? px(55) : px(45 + purplePos.value.faceX),
  top: isPasswordVisible.value ? px(35) : isLookingAtEachOther.value ? px(65) : px(40 + purplePos.value.faceY),
  gap: px(32),
}))

const blackEyesStyle = computed(() => ({
  left: isPasswordVisible.value ? px(10) : isLookingAtEachOther.value ? px(32) : px(26 + blackPos.value.faceX),
  top: isPasswordVisible.value ? px(28) : isLookingAtEachOther.value ? px(12) : px(32 + blackPos.value.faceY),
  gap: px(24),
}))

const orangeEyesStyle = computed(() => ({
  left: isPasswordVisible.value ? px(50) : px(82 + orangePos.value.faceX),
  top: isPasswordVisible.value ? px(85) : px(90 + orangePos.value.faceY),
  gap: px(32),
}))

const yellowEyesStyle = computed(() => ({
  left: isPasswordVisible.value ? px(20) : px(52 + yellowPos.value.faceX),
  top: isPasswordVisible.value ? px(35) : px(40 + yellowPos.value.faceY),
  gap: px(24),
}))

const yellowMouthStyle = computed(() => ({
  left: isPasswordVisible.value ? px(10) : px(40 + yellowPos.value.faceX),
  top: isPasswordVisible.value ? px(88) : px(88 + yellowPos.value.faceY),
}))

function eyeStyle(size, isBlinking) {
  return {
    width: px(size),
    height: isBlinking ? px(2) : px(size),
  }
}

function pupilStyle(offset) {
  return {
    transform: `translate(${offset.x}px, ${offset.y}px)`,
  }
}

function pupilOnlyStyle(offset) {
  return {
    width: px(12),
    height: px(12),
    transform: `translate(${offset.x}px, ${offset.y}px)`,
  }
}

function clearTimer(timer) {
  if (timer) {
    window.clearTimeout(timer)
  }
}

function scheduleBlink(target, timerName, endTimerName) {
  const run = () => {
    const delay = Math.random() * 4000 + 3000
    const timer = window.setTimeout(() => {
      target.value = true
      const endTimer = window.setTimeout(() => {
        target.value = false
        run()
      }, 150)

      if (endTimerName === "purpleBlinkEndTimer") {
        purpleBlinkEndTimer = endTimer
      } else {
        blackBlinkEndTimer = endTimer
      }
    }, delay)

    if (timerName === "purpleBlinkTimer") {
      purpleBlinkTimer = timer
    } else {
      blackBlinkTimer = timer
    }
  }

  run()
}

function schedulePeek() {
  clearTimer(peekTimer)
  clearTimer(peekEndTimer)

  if (!isPasswordVisible.value) {
    isPurplePeeking.value = false
    return
  }

  peekTimer = window.setTimeout(() => {
    isPurplePeeking.value = true
    peekEndTimer = window.setTimeout(() => {
      isPurplePeeking.value = false
      schedulePeek()
    }, 800)
  }, Math.random() * 3000 + 2000)
}

watch(() => props.isTyping, (value) => {
  clearTimer(lookingTimer)
  if (value) {
    isLookingAtEachOther.value = true
    lookingTimer = window.setTimeout(() => {
      isLookingAtEachOther.value = false
    }, 800)
  } else {
    isLookingAtEachOther.value = false
  }
})

watch([() => props.passwordVisible, () => props.passwordLength], schedulePeek)

onMounted(() => {
  window.addEventListener("mousemove", handleMouseMove)
  scheduleBlink(isPurpleBlinking, "purpleBlinkTimer", "purpleBlinkEndTimer")
  scheduleBlink(isBlackBlinking, "blackBlinkTimer", "blackBlinkEndTimer")
  schedulePeek()
})

onBeforeUnmount(() => {
  window.removeEventListener("mousemove", handleMouseMove)
  clearTimer(purpleBlinkTimer)
  clearTimer(purpleBlinkEndTimer)
  clearTimer(blackBlinkTimer)
  clearTimer(blackBlinkEndTimer)
  clearTimer(lookingTimer)
  clearTimer(peekTimer)
  clearTimer(peekEndTimer)
})
</script>

<style lang="scss" scoped>
.auth-scene {
  position: relative;
  min-height: 100vh;
}

.auth-mobile-brand {
  display: none;
}

.auth-scene__desktop {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 46px 54px;
  color: #ffffff;
  background: linear-gradient(145deg, #9ca3af 0%, #6b7280 48%, #4b5563 100%);

  &::before {
    content: "";
    position: absolute;
    inset: 0;
    background:
      linear-gradient(90deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px) 0 0 / 20px 20px,
      linear-gradient(0deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px) 0 0 / 20px 20px;
    pointer-events: none;
  }
}

.auth-brand,
.auth-character-wrap,
.auth-footer {
  position: relative;
  z-index: 1;
}

.auth-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  width: fit-content;
  color: #ffffff;
  text-decoration: none;
  font-size: 18px;
  font-weight: 700;

  img {
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.14);
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.18);
  }
}

.auth-character-wrap {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  min-height: 430px;
  padding-bottom: 26px;
}

.animated-characters {
  position: relative;
  width: 550px;
  height: 400px;
  transform-origin: bottom center;
}

.character {
  position: absolute;
  bottom: 0;
  transform-origin: bottom center;
  transition: transform 0.7s ease-in-out, height 0.7s ease-in-out;
}

.eyes {
  position: absolute;
  display: flex;
}

.white-eyes {
  transition: left 0.7s ease-in-out, top 0.7s ease-in-out;
}

.pupil-eyes {
  transition: left 0.2s ease-out, top 0.2s ease-out;
}

.eye-ball {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 999px;
  background: #ffffff;
  transition: height 0.15s ease, transform 0.1s ease-out;

  i {
    width: 7px;
    height: 7px;
    border-radius: 999px;
    background: #2d2d2d;
    transition: transform 0.1s ease-out;
  }
}

.black-eyes .eye-ball i {
  width: 6px;
  height: 6px;
}

.pupil-only {
  display: inline-block;
  border-radius: 999px;
  background: #2d2d2d;
  transition: transform 0.1s ease-out;
}

.yellow-mouth {
  position: absolute;
  width: 80px;
  height: 4px;
  border-radius: 999px;
  background: #2d2d2d;
  transition: left 0.2s ease-out, top 0.2s ease-out;
}

.auth-footer {
  display: flex;
  gap: 28px;
  color: rgba(255, 255, 255, 0.72);
  font-size: 13px;
}

@media (prefers-reduced-motion: reduce) {
  .auth-scene *,
  .auth-scene *::before,
  .auth-scene *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}

@media (max-width: 1180px) {
  .auth-scene__desktop {
    padding: 40px;
  }

  .animated-characters {
    transform: scale(0.86);
  }
}

@media (max-width: 980px) {
  .auth-scene {
    min-height: auto;
  }

  .auth-scene__desktop {
    display: none;
  }

  .auth-mobile-brand {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    padding: 30px 20px 18px;
    color: #172235;
    font-size: 18px;
    font-weight: 740;

    img {
      width: 38px;
      height: 38px;
      border-radius: 12px;
      box-shadow: 0 14px 32px rgba(19, 58, 102, 0.18);
    }
  }
}
</style>
