function cancelled() {
  return Object.assign(new Error('请求已取消'), { code: 'ERR_CANCELED' })
}

// 单个页面取消等待时，其他页面仍可使用合并中的同一查询。
export function waitForStation(task, signal) {
  if (!signal) return task
  if (signal.aborted) return Promise.reject(cancelled())
  return new Promise((resolve, reject) => {
    const abort = () => reject(cancelled())
    signal.addEventListener('abort', abort, { once: true })
    task.then(
      value => {
        signal.removeEventListener('abort', abort)
        resolve(value)
      },
      error => {
        signal.removeEventListener('abort', abort)
        reject(error)
      },
    )
  })
}
