import { onBeforeUnmount, watch } from 'vue'
import { native } from '@/android/platform.js'
import { createDefaultTicket, seatOptions, themeOptions, disableAirSeats } from '@/utils/ticketShared.js'
import { stations } from '@/composables/useTicketShared.js'

// Keep persistence and ticket export in the existing web layer; Compose owns the list.
export function useNativeHistory({ rows, loading, error, distance, money, dialog, actions }) {
  if (typeof native?.historyResult !== 'function') return false

  let mounted = true
  let busy = false
  let sentStations = false
  const showPage = () => {
    if (mounted) native.setNativePage(dialog.value || busy ? '' : '/history')
  }
  const stop = watch([rows, loading, error, distance, money], () => {
    native.historyResult(JSON.stringify({
      rows: rows.value,
      loading: loading.value,
      error: error.value,
      distance: distance.value,
      money: money.value,
      defaults: createDefaultTicket(),
      seats: seatOptions,
      themes: themeOptions.map(theme => ({ ...theme, value: theme.id })),
      airDisabledSeats: disableAirSeats,
      ...(sentStations ? {} : { stations: stations.map(station => [station.name, station.abbr, station.en]) }),
    }))
    sentStations = true
  }, { immediate: true, flush: 'post' })
  const stopDialog = watch(dialog, showPage, { flush: 'post' })

  async function handleAction(event) {
    const { action, id, operation } = event.detail || {}
    const handler = actions[action]
    if (!handler) return
    if (busy) {
      if (operation) native.historyOperationResult(JSON.stringify({
        operation, success: false, message: '正在处理其他操作，请稍后重试',
      }))
      return
    }
    const row = rows.value.find(row => String(row.id) === String(id))
    if (!['create', 'refresh', 'save'].includes(action) && !row) {
      if (operation) native.historyOperationResult(JSON.stringify({
        operation, success: false, message: '记录不存在，请刷新后重试',
      }))
      return
    }
    busy = true
    // Only PNG export still needs the WebView renderer and system file picker.
    const webOverlay = action === 'download'
    if (webOverlay) native.setNativePage('')
    try {
      const result = await handler(operation ? event.detail : row)
      if (operation && mounted) native.historyOperationResult(JSON.stringify({ operation, ...result }))
    } catch (error) {
      if (operation && mounted) native.historyOperationResult(JSON.stringify({
        operation, success: false, message: error?.response?.data?.message || error.message || '操作失败，请重试',
      }))
    } finally {
      busy = false
      if (webOverlay) showPage()
    }
  }
  window.addEventListener('crticket-history-action', handleAction)
  onBeforeUnmount(() => {
    mounted = false
    stop()
    stopDialog()
    window.removeEventListener('crticket-history-action', handleAction)
  })
  return true
}
