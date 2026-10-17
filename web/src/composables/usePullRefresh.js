import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { native } from '@/android/platform.js'
import { createPullRefreshGesture } from '@/utils/pullRefreshGesture.js'
import { canStartPullRefresh, isPullRefreshAtTop } from '@/utils/pullRefreshTarget.js'

export function usePullRefresh() {
  const progress = ref(0)
  const refreshing = ref(false)
  const nativeIndicator = Boolean(native?.setPullRefreshProgress && native?.refreshPage)
  const gesture = createPullRefreshGesture()
  const route = useRoute()
  const armed = computed(() => progress.value >= 1)
  let frame
  let reloadTimer
  let consumedDrag = false
  let suppressClickUntil = 0
  let startTarget

  function showProgress(value) {
    progress.value = Math.min(1.4, value)
    if (!nativeIndicator) return
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(() => native.setPullRefreshProgress(progress.value))
  }

  function cancel() {
    gesture.cancel()
    startTarget = null
    if (!refreshing.value) showProgress(0)
  }

  function start(event) {
    consumedDrag = false
    suppressClickUntil = 0
    if (route.path === '/user' || refreshing.value || event.touches.length !== 1) return cancel()
    const point = event.touches[0]
    startTarget = event.target
    gesture.begin(point.clientX, point.clientY,
      canStartPullRefresh(startTarget, document, window))
  }

  function move(event) {
    if (refreshing.value) return
    if (!isPullRefreshAtTop(startTarget, document) || !event.cancelable) return cancel()
    const point = event.touches[0]
    if (!point) return cancel()
    const state = gesture.move(point.clientX, point.clientY, event.touches.length)
    if (state.engaged) {
      consumedDrag = true
      event.preventDefault()
    }
    showProgress(state.engaged && state.distance > 0 ? 1 + state.distance / 200 : 0)
  }

  function end() {
    if (refreshing.value) return
    if (consumedDrag) suppressClickUntil = performance.now() + 400
    if (!gesture.finish()) return cancel()
    refreshing.value = true
    showProgress(1)
    if (nativeIndicator) native.refreshPage()
    else reloadTimer = setTimeout(() => window.location.reload(), 160)
  }

  function click(event) {
    if (event.detail > 0 && performance.now() < suppressClickUntil) {
      event.preventDefault()
      event.stopImmediatePropagation()
    }
  }

  watch(() => route.fullPath, cancel)
  onMounted(() => {
    window.addEventListener('touchstart', start, { passive: true })
    window.addEventListener('touchmove', move, { capture: true, passive: false })
    window.addEventListener('touchend', end, { passive: true })
    window.addEventListener('touchcancel', cancel, { passive: true })
    window.addEventListener('click', click, { capture: true })
  })
  onBeforeUnmount(() => {
    window.removeEventListener('touchstart', start)
    window.removeEventListener('touchmove', move, { capture: true })
    window.removeEventListener('touchend', end)
    window.removeEventListener('touchcancel', cancel)
    window.removeEventListener('click', click, { capture: true })
    cancelAnimationFrame(frame)
    clearTimeout(reloadTimer)
    if (nativeIndicator) native.setPullRefreshProgress(0)
  })

  return { progress, refreshing, armed, nativeIndicator }
}
