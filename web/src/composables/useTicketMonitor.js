import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api.js'
import { stations } from './useTicketShared.js'
import { createBrowserTicketMonitor } from './browserTicketMonitor.js'
import {
  publishMonitorNotification,
  requestMonitorNotificationPermission,
} from '../android/monitorNotifications.js'
import {
  getTicketMonitorStatus,
  refreshTicketMonitor,
  resetTicketMonitor,
  startTicketMonitor,
  stopTicketMonitor,
  supportsTicketMonitoring,
  updateTicketMonitor,
} from '../android/ticketMonitor.js'
import { createTicketMonitorConnection } from '../android/ticketMonitorConnection.js'
import { getTrainNumberError } from '../utils/ticketShared.js'
import {
  matchesTicketMonitor,
  rowHasTicket,
  ticketSeatColumns,
  watchedTicketColumns,
} from '../utils/ticketMonitorFilters.js'

const today = () => new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)

export function useTicketMonitor() {
  const nativeMonitoring = supportsTicketMonitoring()
  const restoring = ref(nativeMonitoring)
  const from = ref('')
  const to = ref('')
  const date = ref(today())
  const interval = ref(60)
  const pendingTrainFilter = ref('')
  const onlyAvailable = ref(false)
  const filters = {
    trainFilters: ref([]),
    seatFilters: ref([]),
    timeRange: ref([0, 24]),
    highSpeedOnly: ref(false),
    ordinaryOnly: ref(false),
    selectedDepartureStations: ref([]),
    selectedArrivalStations: ref([]),
  }
  const state = {
    interval,
    monitoring: ref(false),
    systemNotifications: ref(false),
    loading: ref(nativeMonitoring),
    rows: ref([]),
    queriedAt: ref(''),
    lastMonitorAt: ref(''),
    error: ref(''),
    nextAt: ref(''),
    events: ref([]),
  }
  const trainFilterError = computed(() => {
    const values = [...filters.trainFilters.value]
    if (pendingTrainFilter.value) values.push(pendingTrainFilter.value)
    return values.map(getTrainNumberError).find(Boolean) || ''
  })
  let applyingConfig = false
  let pendingRestore = nativeMonitoring
  let nativeStarting = false
  let permissionController
  let generation = 0

  const filterValues = () => Object.fromEntries(
    Object.entries(filters).map(([key, value]) => [key, value.value])
  )
  const matches = row => matchesTicketMonitor(row, filterValues())
  const watchedColumns = computed(() => watchedTicketColumns(filterValues()))
  const hasTicket = row => rowHasTicket(row, watchedColumns.value)
  const filtered = computed(() => state.rows.value.filter(
    row => matches(row) && (!onlyAvailable.value || hasTicket(row))
  ))
  const ticketCount = computed(() => state.rows.value.filter(
    row => matches(row) && hasTicket(row)
  ).length)
  const stationNames = field => computed(() => Array.from(
    new Set(state.rows.value.map(row => row[field]))
  ).filter(Boolean))

  function restoreConfig(config) {
    if (!config) return
    applyingConfig = true
    try {
      from.value = config.fromName || stations.find(
        station => station.telecode === config.from
      )?.name || ''
      to.value = config.toName || stations.find(
        station => station.telecode === config.to
      )?.name || ''
      date.value = config.date
      interval.value = config.interval || 60
      for (const [key, target] of Object.entries(filters)) {
        if (config.filters?.[key] !== undefined) target.value = config.filters[key]
      }
    } finally {
      applyingConfig = false
    }
  }

  const connection = createTicketMonitorConnection({
    readStatus: getTicketMonitorStatus,
    start: startTicketMonitor,
    stop: stopTicketMonitor,
    update: updateTicketMonitor,
    onStatus(status, reason) {
      if (reason === 'restore') restoreConfig(status.config)
      pendingRestore = false
      restoring.value = false
      state.monitoring.value = Boolean(status.active)
      state.loading.value = Boolean(status.loading)
      state.nextAt.value = status.nextAt || ''
      state.error.value = status.error || ''
      if (reason === 'stop') return
      state.rows.value = Array.isArray(status.trains) ? status.trains : []
      state.queriedAt.value = status.queriedAt || ''
      if (status.queriedAt) state.lastMonitorAt.value = status.queriedAt
      state.events.value = Array.isArray(status.events) ? status.events : []
    },
    onError(error) {
      restoring.value = false
      state.loading.value = false
      state.error.value = error.message
    },
  })
  const browser = createBrowserTicketMonitor({
    api, state, matches, watchedColumns, hasTicket,
    publishNotification: publishMonitorNotification,
    message: (type, text) => ElMessage[type](text),
  })

  function parameters() {
    if (trainFilterError.value) throw new Error(trainFilterError.value)
    const start = stations.find(station => station.name === from.value.trim())
    const end = stations.find(station => station.name === to.value.trim())
    if (!start || !end) throw new Error('请输入并选择有效的出发站和到达站')
    if (!date.value) throw new Error('请选择出发日期')
    return { from: start.telecode, to: end.telecode, date: date.value }
  }

  const payload = params => ({
    ...params,
    fromName: from.value.trim(),
    toName: to.value.trim(),
    interval: Math.max(10, Number(interval.value) || 60),
    filters: filterValues(),
  })

  function stop() {
    const stopNative = nativeMonitoring &&
      (state.monitoring.value || nativeStarting || pendingRestore)
    pendingRestore = false
    generation++
    permissionController?.abort()
    browser.stop()
    if (stopNative) return connection.stop()
  }

  function reset() {
    if (applyingConfig) return
    stop()
    state.rows.value = []
    state.queriedAt.value = ''
    state.error.value = ''
    filters.selectedDepartureStations.value = []
    filters.selectedArrivalStations.value = []
    browser.resetReminders()
  }

  watch([from, to, date], reset, { flush: 'sync' })
  watch(trainFilterError, (message, previousMessage) => {
    if (applyingConfig) return
    if (!message) {
      if (state.error.value === previousMessage) state.error.value = ''
      return
    }
    stop()
    state.error.value = message
  }, { flush: 'sync' })
  watch(interval, () => {
    if (!applyingConfig && pendingRestore) stop()
  }, { flush: 'sync' })
  watch(Object.values(filters), () => {
    if (applyingConfig) return
    if (trainFilterError.value) return
    if (pendingRestore) stop()
    browser.resetReminders()
    if (nativeMonitoring && state.monitoring.value) {
      void connection.update(payload(parameters()))
    }
  }, { deep: true, flush: 'sync' })

  async function query(startMonitor = false) {
    if (state.loading.value || state.monitoring.value) return
    let params
    try {
      params = parameters()
    } catch (error) {
      state.error.value = error.message
      return
    }
    if (!startMonitor) return browser.query(params)

    const current = ++generation
    permissionController = new AbortController()
    state.loading.value = true
    state.error.value = ''
    try {
      const permission = await requestMonitorNotificationPermission(permissionController.signal)
      if (current !== generation) return
      if (trainFilterError.value) throw new Error(trainFilterError.value)
      state.systemNotifications.value = permission.granted
      if (permission.message) {
        ElMessage[permission.granted ? 'success' : 'warning'](permission.message)
      }
      if (nativeMonitoring) {
        nativeStarting = true
        await connection.start(payload(params))
      } else {
        await browser.query(params, true)
      }
    } catch (error) {
      if (current === generation && error.code !== 'ERR_CANCELED') {
        state.error.value = error.message || '无法启动余票监控'
      }
    } finally {
      nativeStarting = false
      if (current === generation && !state.monitoring.value) state.loading.value = false
    }
  }

  async function resetAll() {
    await stop()
    if (nativeMonitoring) {
      try {
        await resetTicketMonitor()
      } catch (error) {
        state.error.value = error.message
        return
      }
    }
    applyingConfig = true
    try {
      from.value = ''
      to.value = ''
      date.value = today()
      interval.value = 60
      onlyAvailable.value = false
      for (const [key, target] of Object.entries(filters)) {
        target.value = key === 'timeRange' ? [0, 24]
          : ['highSpeedOnly', 'ordinaryOnly'].includes(key) ? false : []
      }
      state.rows.value = []
      state.queriedAt.value = ''
      state.lastMonitorAt.value = ''
      state.error.value = ''
      state.nextAt.value = ''
      browser.resetReminders()
    } finally {
      applyingConfig = false
    }
  }

  onMounted(() => {
    if (nativeMonitoring) void connection.restore()
  })
  const restoreBackup = () => {
    if (nativeMonitoring) void connection.restore()
  }
  window.addEventListener('crticket-backup-restored', restoreBackup)
  onBeforeUnmount(() => {
    window.removeEventListener('crticket-backup-restored', restoreBackup)
    generation++
    permissionController?.abort()
    connection.detach()
    browser.stop()
  })

  function disabledDate(value) {
    const key = [
      value.getFullYear(),
      String(value.getMonth() + 1).padStart(2, '0'),
      String(value.getDate()).padStart(2, '0'),
    ].join('-')
    const end = new Date(Date.now() + 8 * 3600000 + 14 * 86400000)
      .toISOString().slice(0, 10)
    return key < today() || key > end
  }

  return {
    ...state, ...filters, from, to, date, onlyAvailable,
    pendingTrainFilter, trainFilterError,
    nativeMonitoring, restoring, filtered, ticketCount,
    columns: ticketSeatColumns,
    departureStations: stationNames('from'),
    arrivalStations: stationNames('to'),
    query, stop, disabledDate, resetAll,
    async refresh() {
      if (restoring.value || state.loading.value) return
      if (!state.monitoring.value) return query()
      if (nativeMonitoring) {
        try {
          state.loading.value = true
          await refreshTicketMonitor()
        } catch (error) {
          state.error.value = error.message
          state.loading.value = false
        }
      } else return browser.query(parameters())
    },
    swapStations() {
      const original = from.value
      from.value = to.value
      to.value = original
    },
    selectTrainType(type, enabled) {
      if (!enabled) return
      if (type === 'highSpeed') filters.ordinaryOnly.value = false
      if (type === 'ordinary') filters.highSpeedOnly.value = false
    },
  }
}
