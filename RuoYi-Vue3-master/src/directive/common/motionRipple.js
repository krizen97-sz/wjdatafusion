const reducedMotionQuery = '(prefers-reduced-motion: reduce)'

export function getRippleGeometry(rect, clientX, clientY) {
  const x = Number.isFinite(clientX) ? clientX - rect.left : rect.width / 2
  const y = Number.isFinite(clientY) ? clientY - rect.top : rect.height / 2
  const radius = Math.max(
    Math.hypot(x, y),
    Math.hypot(rect.width - x, y),
    Math.hypot(x, rect.height - y),
    Math.hypot(rect.width - x, rect.height - y)
  )
  return { x, y, size: Math.ceil(radius * 2) }
}

function canCreateRipple(el, event) {
  if (event.button !== undefined && event.button !== 0) return false
  if (el.disabled || el.classList.contains('is-disabled') || el.classList.contains('is-loading')) return false
  return !window.matchMedia?.(reducedMotionQuery).matches
}

function createRipple(el, event) {
  if (!canCreateRipple(el, event)) return
  el.querySelectorAll('.motion-ripple-layer').forEach((item) => item.remove())
  const rect = el.getBoundingClientRect()
  const geometry = getRippleGeometry(rect, event.clientX, event.clientY)
  const ripple = document.createElement('span')
  ripple.className = 'motion-ripple-layer'
  ripple.style.width = `${geometry.size}px`
  ripple.style.height = `${geometry.size}px`
  ripple.style.left = `${geometry.x - geometry.size / 2}px`
  ripple.style.top = `${geometry.y - geometry.size / 2}px`
  ripple.addEventListener('animationend', () => ripple.remove(), { once: true })
  el.appendChild(ripple)
}

export default {
  mounted(el) {
    const handler = (event) => createRipple(el, event)
    el.__motionRippleHandler = handler
    el.addEventListener('pointerdown', handler)
  },
  unmounted(el) {
    el.removeEventListener('pointerdown', el.__motionRippleHandler)
    delete el.__motionRippleHandler
    el.querySelectorAll('.motion-ripple-layer').forEach((item) => item.remove())
  }
}
