import { saveFile } from '../android/platform.js'

export const EXPORT_TICKET_WIDTH = 860
export const EXPORT_TICKET_HEIGHT = 540

const BASE_TICKET_WIDTH = 430

function px(value) {
  return typeof value === 'number' ? `${value}px` : value
}

function ensureBody() {
  return document.body || document.documentElement
}

function buildOffscreenHost({ width, height }) {
  const host = document.createElement('div')
  host.style.position = 'fixed'
  host.style.left = '-10000px'
  host.style.top = '0'
  host.style.width = px(width)
  host.style.height = px(height)
  host.style.overflow = 'hidden'
  host.style.transform = 'none'
  host.style.pointerEvents = 'none'
  host.style.zIndex = '-1'
  return host
}

function normalizeTicketNode(ticketNode) {
  const cloned = ticketNode.cloneNode(true)

  if (cloned instanceof HTMLElement) {
    cloned.style.transform = 'none'
    cloned.style.width = px(BASE_TICKET_WIDTH)
  }

  return cloned
}

export async function renderTicketToCanvas(ticketNode) {
  const { default: html2canvas } = await import('html2canvas')
  await document.fonts.ready
  const host = buildOffscreenHost({ width: BASE_TICKET_WIDTH, height: EXPORT_TICKET_HEIGHT })
  const cloned = normalizeTicketNode(ticketNode)

  host.appendChild(cloned)
  ensureBody().appendChild(host)

  try {
    await new Promise((resolve) => requestAnimationFrame(resolve))
    const scale = EXPORT_TICKET_WIDTH / BASE_TICKET_WIDTH
    return await html2canvas(cloned, { scale, backgroundColor: null })
  } finally {
    host.remove()
  }
}

export async function renderTicketImage(ticketNode) {
  const canvas = await renderTicketToCanvas(ticketNode)
  return canvas.toDataURL('image/png')
}

export async function downloadTicketPNG(imageData, number) {
  const blob = await fetch(imageData).then(response => response.blob())
  return saveFile(blob, `${number}.png`)
}

