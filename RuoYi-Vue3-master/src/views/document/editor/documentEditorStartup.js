// One page can retry or remount while the browser is fetching the local editor API.
export function createEditorApiLoader(host, timeoutMs = 30000) {
  let pending
  return function loadEditorApi(url) {
    if (!url) return Promise.reject(new Error('服务端未提供内网编辑器地址'))
    if (host.DocsAPI?.DocEditor) return Promise.resolve(host.DocsAPI)
    if (pending) return pending

    const script = host.document.createElement('script')
    script.async = true
    script.src = url
    pending = new Promise((resolve, reject) => {
      const finish = (error) => {
        host.clearTimeout(timer)
        script.onload = null
        script.onerror = null
        if (error) {
          script.remove()
          reject(error)
        } else {
          resolve(host.DocsAPI)
        }
      }
      const timer = host.setTimeout(() => finish(new Error('内网编辑器连接超时，请重新连接')), timeoutMs)
      script.onload = () => finish(host.DocsAPI?.DocEditor ? null : new Error('编辑器脚本未能完成初始化，请重新连接'))
      script.onerror = () => finish(new Error('无法加载内网编辑器，请检查网络后重新连接'))
      host.document.head.appendChild(script)
    }).finally(() => { pending = null })
    return pending
  }
}

export async function prepareDocumentEditor(bootstrap, loadEditorApi, getLegacyDetail) {
  // Older servers return only the signed config. Fetch their header info while
  // the browser is already loading the editor API.
  const [api, documentInfo] = await Promise.all([
    loadEditorApi(bootstrap.apiJsUrl),
    bootstrap.documentInfo ?? getLegacyDetail()
  ])
  return { api, documentInfo }
}

let pageApiLoader
export function loadEditorApi(url) {
  if (!pageApiLoader) pageApiLoader = createEditorApiLoader(window)
  return pageApiLoader(url)
}
