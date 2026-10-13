// 页面只订阅服务状态，离开页面时取消订阅，不停止 Android 服务。
export function createTicketMonitorConnection({
  readStatus,
  start,
  stop,
  update,
  onStatus,
  onError,
  pollInterval = 1500,
  schedule = setTimeout,
  cancel = clearTimeout,
}) {
  let attached = false
  let watching = true
  let restorePending = false
  let generation = 0
  let controller
  let timer

  function cancelPending() {
    generation++
    cancel(timer)
    controller?.abort()
  }

  async function perform(operation, reason) {
    cancelPending()
    const current = generation
    controller = new AbortController()
    try {
      const status = await operation(controller.signal)
      if (!attached || current !== generation) return
      watching = Boolean(status.active)
      const deliveryReason = restorePending &&
        (reason === 'restore' || reason === 'status') ? 'restore' : reason
      if (deliveryReason === 'restore') restorePending = false
      onStatus(status, deliveryReason)
      return status
    } catch (error) {
      if (attached && current === generation && error.code !== 'ERR_CANCELED') {
        onError(error, reason)
      }
    } finally {
      if (attached && current === generation && watching) {
        timer = schedule(() => perform(readStatus, 'status'), pollInterval)
      }
    }
  }

  return {
    restore() {
      attached = true
      watching = true
      restorePending = true
      return perform(readStatus, 'restore')
    },
    start(payload) {
      attached = true
      watching = true
      restorePending = false
      return perform(signal => start(payload, signal), 'start')
    },
    update(payload) {
      if (!attached) return
      restorePending = false
      return perform(signal => update(payload, signal), 'update')
    },
    stop() {
      restorePending = false
      return perform(stop, 'stop')
    },
    detach() {
      attached = false
      restorePending = false
      cancelPending()
    },
  }
}
