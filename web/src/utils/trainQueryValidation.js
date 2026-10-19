import { getTrainNumberError } from './ticketShared.js'
import { displayTrainCode } from './trainDisplay.js'

export function getTrainQueryNumberError(value, offline = false, record = '') {
  if (!offline) return getTrainNumberError(value)
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
