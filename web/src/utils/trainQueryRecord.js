import { displayTrainCode } from './trainDisplay.js'

export function matchingTrainRecord(train, record) {
  if (typeof record !== 'string' || record.length > 40) return ''
  if (!/^[A-Z]?\d{1,5}[A-Z]?(?:\/[A-Z]?\d{1,5}[A-Z]?)*$/.test(record)) return ''
  const code = displayTrainCode(train)
  const full = displayTrainCode(record)
  const prefix = full.match(/^[A-Z]/)?.[0] || ''
  const codes = full.split('/').map(part => /^\d/.test(part) ? prefix + part : part)
  return code === full || codes.includes(code) ? record : ''
}

export function trainRecordCacheKey(train, record) {
  return record ? `${train}:${record}` : train
}
