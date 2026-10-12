import '@/assets/styles/nativeTableScroll.css'

const viewportSelector = '.el-table-v2__body > div:first-child'

function useNativeTouch(event) {
  if (!event.target.closest?.(viewportSelector)) return
  // Let the browser scroll and fling. The grid's scroll listener still updates
  // virtual rows, headers, scrollbars and the saved position.
  // Pull-to-refresh sees the gesture first in window's capture phase.
  event.stopPropagation()
}

export const vNativeTableScroll = {
  mounted(element) {
    element.classList.add('native-table-scroll')
    element.addEventListener('touchmove', useNativeTouch, { capture: true, passive: true })
  },
  beforeUnmount(element) {
    element.removeEventListener('touchmove', useNativeTouch, { capture: true })
  },
}
