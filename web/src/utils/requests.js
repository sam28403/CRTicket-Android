export function cancelledRequest() {
  return Object.assign(new Error('请求已取消'), { code: 'ERR_CANCELED' })
}

// 只取消当前等待者，不影响其他页面正在等待的共享查询。
export function waitForRequest(task, signal) {
  if (!signal) return task
  if (signal.aborted) return Promise.reject(cancelledRequest())
  return new Promise((resolve, reject) => {
    const abort = () => reject(cancelledRequest())
    const cleanup = () => signal.removeEventListener('abort', abort)
    signal.addEventListener('abort', abort, { once: true })
    task.then(
      value => {
        cleanup()
        resolve(value)
      },
      error => {
        cleanup()
        reject(error)
      },
    )
  })
}
