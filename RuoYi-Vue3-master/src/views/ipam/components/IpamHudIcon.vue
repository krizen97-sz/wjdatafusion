<template>
  <span
    class="ipam-hud-icon"
    :class="[`ipam-hud-icon--${size}`, `is-${tone}`, `is-${mode}`, { 'is-alert': alert }]"
    :style="ringStyle"
    aria-hidden="true"
  >
    <span class="ipam-hud-icon__frame" />
    <span class="ipam-hud-icon__ring" />
    <span class="ipam-hud-icon__orbit"><i /></span>
    <span class="ipam-hud-icon__scan" />
    <span class="ipam-hud-icon__core"><svg-icon :icon-class="iconClass" /></span>
    <span class="ipam-hud-icon__beacon" />
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  iconClass: { type: String, required: true },
  progress: { type: Number, default: 0 },
  tone: { type: String, default: 'cyan' },
  mode: { type: String, default: 'gauge' },
  size: { type: String, default: 'metric' },
  alert: { type: Boolean, default: false }
})

const ringStyle = computed(() => {
  const progress = Math.min(100, Math.max(0, Number(props.progress || 0)))
  return { '--hud-progress': `${progress * 3.6}deg` }
})
</script>

<style scoped>
.ipam-hud-icon {
  --hud-tone: #39a0ff;
  --hud-tone-soft: rgba(57, 160, 255, 0.16);
  position: relative;
  display: inline-grid;
  flex: 0 0 auto;
  width: 46px;
  height: 46px;
  color: var(--hud-tone);
  isolation: isolate;
  place-items: center;
}

.ipam-hud-icon.is-green {
  --hud-tone: #32c98c;
  --hud-tone-soft: rgba(50, 201, 140, 0.16);
}

.ipam-hud-icon.is-amber {
  --hud-tone: #f2b84b;
  --hud-tone-soft: rgba(242, 184, 75, 0.16);
}

.ipam-hud-icon.is-violet {
  --hud-tone: #a68cff;
  --hud-tone-soft: rgba(166, 140, 255, 0.16);
}

.ipam-hud-icon.is-red,
.ipam-hud-icon.is-alert {
  --hud-tone: #ff6374;
  --hud-tone-soft: rgba(255, 99, 116, 0.16);
}

.ipam-hud-icon__frame,
.ipam-hud-icon__ring,
.ipam-hud-icon__orbit,
.ipam-hud-icon__scan,
.ipam-hud-icon__core,
.ipam-hud-icon__beacon {
  position: absolute;
  display: block;
}

.ipam-hud-icon__frame {
  inset: 4px;
  border: 1px solid color-mix(in srgb, var(--hud-tone) 38%, #273746);
  border-radius: 7px;
  background: var(--hud-tone-soft);
  clip-path: polygon(0 0, calc(100% - 8px) 0, 100% 8px, 100% 100%, 8px 100%, 0 calc(100% - 8px));
}

.ipam-hud-icon__ring {
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(from -90deg, var(--hud-tone) 0deg var(--hud-progress), rgba(111, 139, 164, 0.18) var(--hud-progress) 360deg);
  opacity: 0.85;
  -webkit-mask: radial-gradient(circle, transparent 62%, #000 64% 73%, transparent 75%);
  mask: radial-gradient(circle, transparent 62%, #000 64% 73%, transparent 75%);
  transform: rotate(0.001deg);
  transition: background 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.ipam-hud-icon__orbit {
  inset: 2px;
  border: 1px dashed color-mix(in srgb, var(--hud-tone) 55%, transparent);
  border-radius: 50%;
  opacity: 0.58;
  animation: hud-orbit 7s linear infinite;
  animation-play-state: var(--hud-play-state, running);
}

.ipam-hud-icon__orbit i {
  position: absolute;
  top: -2px;
  left: 50%;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--hud-tone);
  box-shadow: 0 0 8px var(--hud-tone);
  transform: translateX(-50%);
}

.ipam-hud-icon__scan {
  z-index: -1;
  top: 4px;
  left: calc(50% - 1px);
  width: 2px;
  height: calc(50% - 4px);
  opacity: 0;
  background: linear-gradient(var(--hud-tone), transparent);
  filter: drop-shadow(0 0 3px var(--hud-tone));
  transform-origin: 1px 100%;
}

.ipam-hud-icon.is-radar .ipam-hud-icon__scan {
  opacity: 0.72;
  animation: hud-scan 3.8s linear infinite;
  animation-play-state: var(--hud-play-state, running);
}

.ipam-hud-icon__core {
  z-index: 1;
  display: grid;
  width: 28px;
  height: 28px;
  border: 1px solid color-mix(in srgb, var(--hud-tone) 52%, #263644);
  border-radius: 5px;
  color: var(--hud-tone);
  background: #101b24;
  box-shadow: 0 0 14px var(--hud-tone-soft), 0 0 0 3px rgba(8, 14, 20, 0.45);
  place-items: center;
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 180ms ease-out;
}

.ipam-hud-icon__core :deep(.svg-icon) {
  width: 16px;
  height: 16px;
}

.ipam-hud-icon.is-pulse .ipam-hud-icon__core {
  animation: hud-core-pulse 2.4s ease-in-out infinite;
  animation-play-state: var(--hud-play-state, running);
}

.ipam-hud-icon__beacon {
  z-index: 2;
  top: 4px;
  right: 4px;
  width: 5px;
  height: 5px;
  border: 1px solid #0b1117;
  border-radius: 50%;
  background: var(--hud-tone);
  box-shadow: 0 0 7px var(--hud-tone);
}

.ipam-hud-icon.is-alert .ipam-hud-icon__beacon {
  animation: hud-alert 1.4s ease-in-out infinite;
  animation-play-state: var(--hud-play-state, running);
}

.ipam-hud-icon--brand {
  width: 42px;
  height: 42px;
}

.ipam-hud-icon--brand .ipam-hud-icon__core {
  width: 26px;
  height: 26px;
}

.ipam-hud-icon--panel {
  width: 30px;
  height: 30px;
}

.ipam-hud-icon--panel .ipam-hud-icon__frame {
  inset: 3px;
  border-radius: 5px;
  clip-path: polygon(0 0, calc(100% - 6px) 0, 100% 6px, 100% 100%, 6px 100%, 0 calc(100% - 6px));
}

.ipam-hud-icon--panel .ipam-hud-icon__core {
  width: 20px;
  height: 20px;
  border-radius: 4px;
}

.ipam-hud-icon--panel .ipam-hud-icon__core :deep(.svg-icon) {
  width: 12px;
  height: 12px;
}

.ipam-hud-icon--panel .ipam-hud-icon__beacon {
  top: 2px;
  right: 2px;
  width: 4px;
  height: 4px;
}

.ipam-hud-icon:hover .ipam-hud-icon__core {
  box-shadow: 0 0 18px color-mix(in srgb, var(--hud-tone) 28%, transparent), 0 0 0 3px rgba(8, 14, 20, 0.48);
  transform: scale(1.06);
}

@keyframes hud-orbit {
  to { transform: rotate(360deg); }
}

@keyframes hud-scan {
  to { transform: rotate(360deg); }
}

@keyframes hud-core-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.045); }
}

@keyframes hud-alert {
  0%, 100% { opacity: 0.65; transform: scale(0.86); }
  50% { opacity: 1; transform: scale(1.18); }
}

@media (prefers-reduced-motion: reduce) {
  .ipam-hud-icon__orbit,
  .ipam-hud-icon__scan,
  .ipam-hud-icon__core,
  .ipam-hud-icon__beacon {
    animation: none !important;
    transition: none !important;
  }
}
</style>
