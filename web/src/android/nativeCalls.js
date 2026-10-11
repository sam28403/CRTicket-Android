import { cancelledRequest } from '../utils/requests.js'

// HTTP、文件保存和通知共用回调管理，各自保留超时及取消策略。
export function createNativeCalls(callbackName) {
  const pending = new Map()
  if (globalThis.window) {
    window[callbackName] = (id, result) => pending.get(id)?.(result)
  }

  function call(invoke, {
    signal,
    timeout = 0,
    timeoutError = () => new Error('系统操作超时，请重试'),
    onCancel = () => {},
    onCleanup = () => {},
    mapResult = result => result,
  } = {}) {
    return new Promise((resolve, reject) => {
      const id = crypto.randomUUID()
      let timer
      const cleanup = () => {
        clearTimeout(timer)
        pending.delete(id)
        signal?.removeEventListener('abort', abort)
        onCleanup(id)
      }
      const abort = () => {
        cleanup()
        onCancel(id)
        reject(cancelledRequest())
      }
      pending.set(id, result => {
        try {
          const value = mapResult(result, id)
          cleanup()
          resolve(value)
        } catch (error) {
          cleanup()
          reject(error)
        }
      })
      if (timeout > 0) {
        timer = setTimeout(() => {
          cleanup()
          onCancel(id)
          reject(timeoutError())
        }, timeout)
      }
      signal?.addEventListener('abort', abort, { once: true })
      if (signal?.aborted) {
        abort()
        return
      }
      try {
        invoke(id)
      } catch (error) {
        cleanup()
        reject(error)
      }
    })
  }

  return { call, has: id => pending.has(id) }
}

export function supportsNativeMethods(methods) {
  const bridge = globalThis.window?.CRTicketAndroid
  return methods.every(method => typeof bridge?.[method] === 'function')
}
