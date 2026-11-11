import { getTrainNumberError } from './ticketShared.js'
import { displayTrainCode } from './trainDisplay.js'

export function getTrainQueryNumberError(value, offline = false, record = '', imported = false) {
  if (!offline) return getTrainNumberError(value)
  if (imported && typeof value === 'string' &&
    /^[A-Z]?\d{1,5}[A-Z]?(?:\/[A-Z]?\d{1,5}[A-Z]?)*$/i.test(value) && value.length <= 40) {
    return ''
  }
  // List/deep-link entries keep their exact offline record and plan suffix.
  const selectedRecord = record && value === displayTrainCode(record)
  const code = selectedRecord ? displayTrainCode(record) : value
  if (typeof code !== 'string') return getTrainNumberError(code)
  const parts = code.split('/')
  const prefix = parts[0].match(/^[A-Z]/i)?.[0] || ''
  return parts.map((part, index) => getTrainNumberError(
    index > 0 && /^\d/.test(part) ? `${prefix}${part}` : part,
  )).find(Boolean) || ''
}
