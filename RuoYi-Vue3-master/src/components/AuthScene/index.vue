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

      <div class="auth-hero">
        <p class="auth-eyebrow">{{ sceneText.eyebrow }}</p>
        <h1>{{ sceneText.title }}</h1>
        <p>{{ sceneText.description }}</p>
      </div>

      <div class="auth-character" aria-hidden="true">
        <div class="auth-grid"></div>
        <div class="signal signal-a"></div>
        <div class="signal signal-b"></div>
        <div class="device device-primary">
          <div class="device-eyes">
            <span class="eye"><i></i></span>
            <span class="eye"><i></i></span>
          </div>
          <div class="device-bar"></div>
        </div>
        <div class="device device-dark">
          <div class="device-eyes">
            <span class="eye"><i></i></span>
            <span class="eye"><i></i></span>
          </div>
          <div class="device-line"></div>
        </div>
        <div class="device device-alert">
          <div class="device-eyes compact">
            <span class="eye"><i></i></span>
          </div>
        </div>
        <div class="inspection-line line-one"></div>
        <div class="inspection-line line-two"></div>
      </div>

      <ul class="auth-capabilities">
        <li v-for="item in sceneText.capabilities" :key="item.label">
          <span>{{ item.label }}</span>
          <em>{{ item.value }}</em>
        </li>
      </ul>

      <div class="auth-footer">
        <span>现场融合</span>
        <span>自动化巡检</span>
        <span>白名单管理</span>
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

const isRegister = computed(() => props.mode === "register")

const sceneClasses = computed(() => ({
  "is-register": isRegister.value,
  "is-typing": props.isTyping,
  "is-password": props.passwordLength > 0,
  "is-password-visible": props.passwordVisible,
}))

const sceneText = computed(() => {
  if (isRegister.value) {
    return {
      eyebrow: "账户接入",
      title: "把新账号接入统一运维视图。",
      description: "注册入口保持轻量，后续权限与业务范围仍由平台统一分配。",
      capabilities: [
        { label: "账号校验", value: "验证码保护" },
        { label: "接入范围", value: "后台统一分配" },
        { label: "离线部署", value: "本地资源" },
      ],
    }
  }

  return {
    eyebrow: "平台入口",
    title: "让现场、巡检与白名单数据保持同步。",
    description: "面向一线运维的融合管控入口，登录后快速进入现场、巡检和版本记录工作区。",
    capabilities: [
      { label: "现场融合", value: "关系画布" },
      { label: "自动巡检", value: "任务闭环" },
      { label: "白名单", value: "准入管控" },
    ],
  }
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
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 46px 54px;
  color: #f8fbff;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px) 0 0 / 36px 36px,
    linear-gradient(0deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px) 0 0 / 36px 36px,
    linear-gradient(145deg, #56616f 0%, #394350 48%, #171d26 100%);

  &::before {
    content: "";
    position: absolute;
    inset: auto 0 0;
    height: 42%;
    background: linear-gradient(180deg, transparent, rgba(28, 119, 218, 0.22));
    pointer-events: none;
  }
}

.auth-brand,
.auth-hero,
.auth-character,
.auth-capabilities,
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
    background: rgba(255, 255, 255, 0.12);
    box-shadow: 0 18px 40px rgba(0, 0, 0, 0.22);
  }
}

.auth-hero {
  max-width: 520px;
  margin-top: 28px;

  .auth-eyebrow {
    margin: 0 0 16px;
    color: #b9ddff;
    font-size: 14px;
    font-weight: 700;
  }

  h1 {
    margin: 0;
    font-size: clamp(36px, 4vw, 58px);
    line-height: 1.08;
    font-weight: 760;
  }

  p:not(.auth-eyebrow) {
    max-width: 430px;
    margin: 20px 0 0;
    color: rgba(237, 246, 255, 0.78);
    font-size: 15px;
    line-height: 1.8;
  }
}

.auth-character {
  width: min(540px, 86%);
  height: 350px;
  margin: 10px auto 0;
}

.auth-grid {
  position: absolute;
  inset: 34px 14px 0;
  border-bottom: 1px solid rgba(211, 231, 255, 0.25);
  background:
    linear-gradient(90deg, rgba(211, 231, 255, 0.16) 1px, transparent 1px) 0 0 / 42px 42px,
    linear-gradient(0deg, rgba(211, 231, 255, 0.12) 1px, transparent 1px) 0 0 / 42px 42px;
  mask-image: linear-gradient(180deg, transparent, #000 26%, #000 100%);
}

.signal {
  position: absolute;
  width: 74px;
  height: 74px;
  border: 1px solid rgba(168, 212, 255, 0.28);
  border-radius: 50%;
  animation: signalPulse 4.6s ease-in-out infinite;

  &::after {
    content: "";
    position: absolute;
    inset: 18px;
    border-radius: inherit;
    border: 1px solid rgba(168, 212, 255, 0.38);
  }
}

.signal-a {
  top: 74px;
  right: 54px;
}

.signal-b {
  left: 22px;
  bottom: 52px;
  animation-delay: 1.2s;
}

.device {
  position: absolute;
  bottom: 0;
  border-radius: 14px 14px 0 0;
  transform-origin: bottom center;
  transition: transform 0.7s ease, height 0.7s ease, background-color 0.7s ease;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.device-primary {
  left: 78px;
  width: 168px;
  height: 282px;
  background: linear-gradient(180deg, #3d8cff 0%, #195fb3 100%);
  z-index: 2;
}

.device-dark {
  left: 230px;
  width: 126px;
  height: 224px;
  background: linear-gradient(180deg, #222b35 0%, #111821 100%);
  z-index: 3;
}

.device-alert {
  right: 92px;
  width: 130px;
  height: 174px;
  background: linear-gradient(180deg, #73dcc8 0%, #159a86 100%);
  z-index: 1;
}

.device-eyes {
  position: absolute;
  top: 38px;
  left: 34px;
  display: flex;
  gap: 22px;
  transition: transform 0.45s ease, top 0.45s ease, left 0.45s ease;

  &.compact {
    left: 48px;
    gap: 0;
  }
}

.eye {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ffffff;
  overflow: hidden;
  transition: height 0.2s ease, transform 0.45s ease;

  i {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #16202b;
    transition: transform 0.2s ease;
  }
}

.device-bar,
.device-line {
  position: absolute;
  left: 28px;
  right: 28px;
  bottom: 42px;
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.28);
}

.device-line {
  left: 24px;
  right: 24px;
  bottom: 36px;
  height: 6px;
}

.inspection-line {
  position: absolute;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, rgba(183, 223, 255, 0.9), transparent);
  animation: scanLine 3.2s ease-in-out infinite;
}

.line-one {
  left: 40px;
  right: 28px;
  top: 112px;
}

.line-two {
  left: 82px;
  right: 74px;
  top: 186px;
  animation-delay: 1.1s;
}

.is-typing {
  .device-primary {
    height: 310px;
    transform: skewX(-5deg) translateX(22px);
  }

  .device-dark {
    transform: skewX(4deg) translateX(-12px);
  }

  .device-alert {
    transform: translateY(-12px);
  }

  .device-eyes {
    transform: translate(12px, 6px);
  }
}

.is-password:not(.is-password-visible) {
  .device-primary {
    height: 318px;
    transform: translateX(34px) skewX(-8deg);
  }

  .device-primary .eye {
    height: 4px;
  }

  .device-dark .eye i {
    transform: translateX(-4px);
  }
}

.is-password-visible {
  .device-primary {
    transform: translateX(-8px);
  }

  .device-primary .eye i {
    transform: translate(4px, 3px);
  }

  .device-dark .eye i {
    transform: translate(4px, -2px);
  }
}

.auth-capabilities {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  width: min(540px, 100%);
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    border-top: 1px solid rgba(232, 244, 255, 0.28);
    padding-top: 14px;
  }

  span,
  em {
    display: block;
  }

  span {
    color: rgba(248, 251, 255, 0.68);
    font-size: 13px;
  }

  em {
    margin-top: 7px;
    color: #ffffff;
    font-size: 18px;
    font-style: normal;
    font-weight: 720;
  }
}

.auth-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: rgba(235, 245, 255, 0.64);
  font-size: 13px;
}

@keyframes scanLine {
  0%,
  100% {
    opacity: 0;
    transform: translateY(-12px);
  }
  45%,
  55% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes signalPulse {
  0%,
  100% {
    opacity: 0.46;
    transform: scale(0.94);
  }
  50% {
    opacity: 0.92;
    transform: scale(1.04);
  }
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
    padding: 38px;
  }

  .auth-character {
    width: 500px;
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
