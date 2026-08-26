const THEME_TRANSITION_DURATION = 620

let transitionRunning = false

export function resolveThemeTransitionGeometry(event, viewport = {}) {
  const width = Number(viewport.width ?? window.innerWidth)
  const height = Number(viewport.height ?? window.innerHeight)
  const x = Number.isFinite(event?.clientX) ? event.clientX : width / 2
  const y = Number.isFinite(event?.clientY) ? event.clientY : height / 2
  const radius = Math.hypot(Math.max(x, width - x), Math.max(y, height - y))
  return { x, y, radius }
}

export async function runThemeTransition({ event, isDark, toggle }) {
  if (transitionRunning) return

  const supportsViewTransition = typeof document !== 'undefined' && typeof document.startViewTransition === 'function'
  const prefersReducedMotion = typeof window !== 'undefined'
    && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

  if (!supportsViewTransition || prefersReducedMotion) {
    toggle()
    return
  }

  transitionRunning = true
  let themeApplied = false
  try {
    const { x, y, radius } = resolveThemeTransitionGeometry(event)
    const clipPath = [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`]
    const transition = document.startViewTransition(async () => {
      toggle()
      themeApplied = true
      await Promise.resolve()
    })

    await transition.ready
    document.documentElement.animate(
      { clipPath: isDark ? clipPath : [...clipPath].reverse() },
      {
        duration: THEME_TRANSITION_DURATION,
        easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
        fill: 'forwards',
        pseudoElement: isDark ? '::view-transition-new(root)' : '::view-transition-old(root)'
      }
    )
    await transition.finished
  } catch {
    if (!themeApplied) toggle()
  } finally {
    transitionRunning = false
  }
}
