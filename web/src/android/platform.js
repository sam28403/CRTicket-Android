export const native = globalThis.window?.CRTicketAndroid
export const configuration = native ? JSON.parse(native.configuration()) : { backend: 'phone' }
export const deviceMode = Boolean(native)

const requests = new Map()
const saves = new Map()
const binaryChunks = new Map()
if (globalThis.window) {
  window.crticketBinaryChunk = (id, { chunk }) => {
    if (!requests.has(id)) return
    if (!binaryChunks.has(id)) binaryChunks.set(id, [])
    binaryChunks.get(id).push(chunk)
  }
  window.crticketApiResult = (id, result) => {
    if (result.binary) result.body = (binaryChunks.get(id) || []).join('')
    binaryChunks.delete(id)
    requests.get(id)?.(result)
    requests.delete(id)
  }
  window.crticketSaveResult = (id, result) => { saves.get(id)?.(result); saves.delete(id) }
}

export function nativeRequest(method, path, body, { timeout = 45000, signal } = {}) {
  return new Promise((resolve, reject) => {
    const id = crypto.randomUUID()
    const cleanup = () => {
      clearTimeout(timer)
      requests.delete(id)
      binaryChunks.delete(id)
      signal?.removeEventListener('abort', abort)
    }
    const abort = () => {
      cleanup(); native.cancel?.(id)
      reject(Object.assign(new Error('请求已取消'), { code: 'ERR_CANCELED' }))
    }
    const timer = setTimeout(() => {
      cleanup(); native.cancel?.(id)
      reject(Object.assign(new Error('铁路服务请求超时'), { code: 'ECONNABORTED' }))
    }, timeout > 0 ? timeout : 50000)
    requests.set(id, result => { cleanup(); resolve(result) })
    signal?.addEventListener('abort', abort, { once: true })
    if (signal?.aborted) { abort(); return }
    try { native.request(id, method, path, typeof body === 'string' ? body : JSON.stringify(body || {})) }
    catch (error) { cleanup(); reject(error) }
  })
}

export async function saveFile(blob, filename) {
  if (!native) {
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = filename; link.click()
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
  return new Promise((resolve, reject) => {
    const id = crypto.randomUUID()
    saves.set(id, result => {
      if (result.cancelled) resolve(false)
      else if (result.success) resolve(true)
      else reject(new Error(result.message || '文件保存失败'))
    })
    try { native.save(id, filename, blob.type || 'application/octet-stream', data) }
    catch (error) { saves.delete(id); reject(error) }
  })
}

export function initializePlatform(router) {
  if (!native) return
  // Route local windows in this WebView and open public links in the system browser.
  window.open = url => {
    const target = new URL(url, location.href)
    if (target.origin === location.origin) router.push(target.hash.slice(1) || '/')
    else if (['https:', 'http:'].includes(target.protocol)) native.external(target.href)
    return null
  }
  window.crticketBack = () => {
    const closeButton = [...document.querySelectorAll('.el-dialog__headerbtn, .el-message-box__headerbtn')].reverse()
      .find(button => button.getClientRects().length)
    if (closeButton) { closeButton.click(); return true }
    if (router.currentRoute.value.path !== '/') {
      if (history.state?.back) router.back()
      else router.replace('/')
      return true
    }
    return false
  }
}
