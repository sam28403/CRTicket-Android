export function networkEnabled() {
  return globalThis.CRTicketDeveloper?.networkEnabled?.() !== false
}

export function requireNetwork() {
  if (!networkEnabled()) throw new Error('联网功能已关闭')
}

export function timetableAutoUpdate() {
  return networkEnabled() && globalThis.CRTicketDeveloper?.lltAutoUpdate?.() !== false
}
