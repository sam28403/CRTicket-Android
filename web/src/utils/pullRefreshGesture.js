const TOUCH_SLOP = 8

export function createPullRefreshGesture() {
  let start
  let distance = 0
  let engaged = false

  function cancel() {
    start = null
    distance = 0
    engaged = false
  }

  return {
    begin(x, y, eligible) {
      cancel()
      if (eligible) start = { x, y }
    },
    move(x, y, touches = 1) {
      if (!start) return { distance: 0, engaged: false }
      const vertical = y - start.y
      const horizontal = Math.abs(x - start.x)
      if (touches !== 1 || vertical < -TOUCH_SLOP
        || (horizontal > TOUCH_SLOP && horizontal > vertical)) {
        cancel()
        return { distance: 0, engaged: false }
      }
      engaged ||= vertical > TOUCH_SLOP && vertical > horizontal
      distance = engaged ? Math.max(0, vertical - TOUCH_SLOP) : 0
      return { distance, engaged }
    },
    finish() {
      const refresh = Boolean(start && engaged && distance > 0)
      cancel()
      return refresh
    },
    cancel,
  }
}
