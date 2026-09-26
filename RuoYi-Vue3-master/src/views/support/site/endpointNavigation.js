export function normalizeEndpointUrl(value) {
  const input = String(value || '').trim()
  if (!input) throw new Error('当前页面未配置访问地址')
  if (/^[a-z][a-z\d+.-]*:/i.test(input) && !/^https?:\/\//i.test(input) && !/^[\w.-]+:\d+(?:\/|$)/.test(input)) {
    throw new Error('仅支持 HTTP 或 HTTPS 页面地址')
  }
  const url = new URL(/^https?:\/\//i.test(input) ? input : 'http://' + input)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) {
    throw new Error('页面地址不允许包含登录凭据')
  }
  return url.href
}

export async function copyEndpointPassword({ url, readPassword, copy, open }) {
  const target = normalizeEndpointUrl(url)
  const password = await readPassword()
  if (!password) throw new Error('当前页面未配置密码，可使用“跳转页面”')
  if (!await copy(password)) throw new Error('密码复制失败，请检查浏览器剪贴板权限后重试')
  return { url: target, opened: Boolean(open(target)) }
}

export async function writeSecretToClipboard(value) {
  try {
    if (window.isSecureContext && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch { /* Fall back for intranet HTTP and older browsers. */ }
  const previous = document.activeElement
  const input = document.createElement('textarea')
  input.value = value
  input.setAttribute('readonly', '')
  input.style.position = 'fixed'
  input.style.opacity = '0'
  const target = previous?.closest('.el-dialog, .el-drawer') || document.body
  target.appendChild(input)
  let success = false
  try {
    input.focus()
    input.select()
    success = document.execCommand('copy')
  } catch { success = false }
  finally {
    input.value = ''
    input.remove()
    previous?.focus()
  }
  return success
}
