import { ElLoading } from 'element-plus'

export const PLATFORM_LOADING_CLASS = 'platform-loading-mask'
export const PLATFORM_LOADING_TEXT = '正在处理，请稍候'

export function openPlatformLoading(options = {}) {
  const normalizedOptions = typeof options === 'string' ? { text: options } : options
  const { customClass, text = PLATFORM_LOADING_TEXT, ...serviceOptions } = normalizedOptions

  return ElLoading.service({
    lock: true,
    text,
    background: 'var(--loading-mask-bg)',
    ...serviceOptions,
    customClass: [PLATFORM_LOADING_CLASS, customClass].filter(Boolean).join(' ')
  })
}
