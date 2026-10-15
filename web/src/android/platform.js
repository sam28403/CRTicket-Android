import { createNativeCalls } from './nativeCalls.js'

export const native = globalThis.window?.CRTicketAndroid
export const configuration = native ? JSON.parse(native.configuration()) : { backend: 'phone' }
export const deviceMode = Boolean(native)

const requests = createNativeCalls('crticketApiResult')
const saves = createNativeCalls('crticketSaveResult')
const binaryChunks = new Map()
if (globalThis.window) {
  window.crticketBinaryChunk = (id, { chunk }) => {
    if (!requests.has(id)) return
    if (!binaryChunks.has(id)) binaryChunks.set(id, [])
    binaryChunks.get(id).push(chunk)
  }
}

export function nativeRequest(method, path, body, { timeout = 45000, signal } = {}) {
  return requests.call(id => {
    const payload = typeof body === 'string' ? body : JSON.stringify(body || {})
    native.request(id, method, path, payload)
  }, {
    signal,
    timeout: timeout > 0 ? timeout : 50000,
    timeoutError: () => Object.assign(new Error('铁路服务请求超时'), { code: 'ECONNABORTED' }),
    onCancel: id => native.cancel?.(id),
    onCleanup: id => binaryChunks.delete(id),
    mapResult: (result, id) => {
      if (result.binary) result.body = (binaryChunks.get(id) || []).join('')
      return result
    },
  })
}

export async function saveFile(blob, filename) {
  if (!native) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    return true
  }
  if (blob.size > 20 * 1024 * 1024) throw new Error('导出文件不能超过20MB')
  const data = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result.split(',')[1])
    reader.onerror = () => reject(new Error('无法读取导出文件'))
    reader.readAsDataURL(blob)
  })
  const result = await saves.call(id => {
    native.save(id, filename, blob.type || 'application/octet-stream', data)
  })
  if (result.cancelled) return false
  if (result.success) return true
  throw new Error(result.message || '文件保存失败')
}

export function initializePlatform(router) {
  if (!native) return
  const hasNativeHome = typeof native.setHomeVisible === 'function'
  window.crticketNavigate = route => router.push(route)
  if (hasNativeHome) {
    router.afterEach((to, from, failure) => {
      if (failure) return
      if (typeof native.setNativePage === 'function') {
        const pages = ['/home', '/about', '/settings']
        if (typeof native.trainListResult === 'function') pages.push('/list')
        native.setNativePage(pages.includes(to.path) ? to.path : '')
      } else native.setHomeVisible(to.path === '/home')
    })
  }
  // Route local windows in this WebView and open public links in the system browser.
  window.open = url => {
    const target = new URL(url, location.href)
    if (target.origin === location.origin) router.push(target.hash.slice(1) || '/')
    else if (['https:', 'http:'].includes(target.protocol)) native.external(target.href)
    return null
  }
  window.crticketBack = () => {
    const closeButton = [...document.querySelectorAll(
      '.el-dialog__headerbtn, .el-message-box__headerbtn',
    )].reverse()
      .find(button => button.getClientRects().length)
    if (closeButton) {
      closeButton.click()
      return true
    }
    const path = router.currentRoute.value.path
    if (hasNativeHome && path === '/home') return false
    if (hasNativeHome || path !== '/') {
      if (history.state?.back) router.back()
      else router.replace(hasNativeHome ? '/home' : '/')
      return true
    }
    return false
  }
}
