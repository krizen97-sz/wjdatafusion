const PROGRESS_ID = 'platform-route-progress'

let progressTimer
let currentProgress = 0

function ensureProgressElement() {
  let element = document.getElementById(PROGRESS_ID)
  if (element) return element

  element = document.createElement('div')
  element.id = PROGRESS_ID
  element.className = 'platform-route-progress'
  element.setAttribute('role', 'progressbar')
  element.setAttribute('aria-label', '页面正在加载')
  element.innerHTML = '<span class="platform-route-progress__bar"></span>'
  document.body.appendChild(element)
  return element
}

function updateProgress(value) {
  const element = ensureProgressElement()
  currentProgress = Math.max(0, Math.min(100, value))
  element.style.setProperty('--route-progress', String(currentProgress / 100))
  element.setAttribute('aria-valuenow', String(Math.round(currentProgress)))
}

export function startRouteProgress() {
  window.clearInterval(progressTimer)
  const element = ensureProgressElement()
  element.classList.remove('is-complete')
  element.classList.add('is-active')
  updateProgress(18)
  progressTimer = window.setInterval(() => {
    if (currentProgress >= 88) return
    updateProgress(currentProgress + Math.max(1.5, (88 - currentProgress) * 0.12))
  }, 180)
}

export function finishRouteProgress() {
  window.clearInterval(progressTimer)
  const element = document.getElementById(PROGRESS_ID)
  if (!element) return
  updateProgress(100)
  element.classList.add('is-complete')
  window.setTimeout(() => {
    element.classList.remove('is-active', 'is-complete')
    updateProgress(0)
  }, 220)
}
