import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { native, saveFile } from '@/android/platform.js'
import { createDefaultTicket, disableAirSeats, seatOptions, themeOptions } from '@/utils/ticketShared.js'
import { stations } from './useTicketShared.js'
import { renderTicketImage, downloadTicketPNG } from '@/utils/ticketExport.js'
import { validateHistoryTicket } from '@/utils/historyValidation.js'
import { storeGeneratedTicket } from '@/utils/generatorMutations.js'
import { createGeneratorSaveFlow } from '@/utils/generatorSaveFlow.js'

const DRAFT_KEY = 'crticket-generator-draft'
const RECEIPT_KEY = 'crticket-generator-receipt'

export function useTicketGenerator({ ticket, credit, conditioner, ticketRef, owner, prepare }) {
  const busy = ref(false)
  const nativePage = typeof native?.generatorResult === 'function'
  let mounted = true
  let timer
  let revision = 0

  function snapshot() {
    return { ...ticket, useCredit: credit.value ? 1 : 0, hasConditioner: conditioner.value ? 1 : 0 }
  }

  function apply(value) {
    const defaults = createDefaultTicket()
    for (const key of Object.keys(defaults)) ticket[key] = value[key] ?? defaults[key]
    credit.value = Number(value.useCredit) === 1
    conditioner.value = Number(value.hasConditioner) === 1 && !disableAirSeats.includes(ticket.seatType)
  }

  try {
    const saved = nativePage ? native.generatorDraft() : localStorage.getItem(DRAFT_KEY)
    if (saved) apply(JSON.parse(saved))
  } catch {
    // Ignore an obsolete draft while retaining the rest of the account data.
  }

  function send(detail) {
    if (nativePage && (mounted || detail.operation || detail.reset)) native.generatorResult(JSON.stringify(detail))
  }

  function clear() {
    apply(createDefaultTicket())
    localStorage.removeItem(DRAFT_KEY)
    localStorage.removeItem(RECEIPT_KEY)
    send({ draft: snapshot(), reset: true })
  }

  async function exportFile(format, value) {
    await prepare?.()
    await nextTick()
    const image = await renderTicketImage(ticketRef.value)
    if (format === 'image') return downloadTicketPNG(image, value.number)
    const { default: jsPDF } = await import('jspdf')
    const pdf = new jsPDF({ orientation: 'landscape', unit: 'cm', format: [8.6, 5.4] })
    pdf.addImage(image, 'PNG', 0, 0, 8.6, 5.4)
    return saveFile(pdf.output('blob'), `${value.number}.pdf`)
  }

  const save = createGeneratorSaveFlow({
    validate: validateHistoryTicket,
    store: storeGeneratedTicket,
    exportFile,
    readReceipt: () => localStorage.getItem(RECEIPT_KEY),
    writeReceipt: value => localStorage.setItem(RECEIPT_KEY, value),
    clear,
  })

  async function saveFormat(format) {
    if (busy.value) return
    busy.value = true
    try {
      return await save(format, snapshot(), owner())
    } finally {
      busy.value = false
    }
  }

  function schedulePreview(nativeTicket) {
    clearTimeout(timer)
    const token = ++revision
    timer = setTimeout(async () => {
      try {
        await nextTick()
        await prepare?.()
        const image = await renderTicketImage(ticketRef.value)
        if (mounted && token === revision) send({ image, ticket: JSON.stringify(nativeTicket || snapshot()) })
      } catch {
        // Export reports render errors; an incomplete live preview must stay editable.
      }
    }, 240)
  }

  watch([ticket, credit, conditioner], () => {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(snapshot()))
    if (nativePage) schedulePreview()
  }, { deep: true, flush: 'post' })

  async function handleAction(event) {
    const { action, ticket: value, operation } = event.detail || {}
    if (busy.value) return
    apply(value || {})
    if (action === 'change') {
      schedulePreview(value)
      return
    }
    try {
      if (action === 'clear') clear()
      else if (['pdf', 'image'].includes(action)) await saveFormat(action)
      send({ operation, error: '' })
    } catch (error) {
      send({ operation, error: error?.response?.data?.message || error.message || '保存失败，请重试' })
    }
  }

  onMounted(() => {
    if (!nativePage) return
    window.addEventListener('crticket-generator-action', handleAction)
    send({
      draft: snapshot(), seats: seatOptions,
      themes: themeOptions.map(theme => ({ ...theme, value: theme.id })),
      airDisabledSeats: disableAirSeats,
      stations: stations.map(station => [station.name, station.abbr, station.en]),
    })
    schedulePreview()
  })
  onBeforeUnmount(() => {
    mounted = false
    revision++
    clearTimeout(timer)
    window.removeEventListener('crticket-generator-action', handleAction)
  })
  return { busy, saveFormat, clear }
}
