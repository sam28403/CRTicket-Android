import { displayTrainCode } from './trainDisplay.js'
import { isTrainQueryDateInRange, trainQueryRange } from './trainQueryDate.js'
import { matchingTrainRecord } from './trainQueryRecord.js'

export function trainQueryEntryLocation(query, now = Date.now()) {
  const date = query.date
  if (query.source === 'llt' || typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return null
  const parsed = new Date(`${date}T00:00:00Z`)
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) return null
  if (isTrainQueryDateInRange(date, now)) return null
  return { path: '/train', query: { ...query, source: 'llt' } }
}

export function requiresOfflineTrainQuery(source, result) {
  return source !== 'llt' && (
    !result.stops?.length || result.offlineFallback === true ||
    String(result.source || '').startsWith('路路通')
  )
}

export function trainQuerySourceLocation({ train, date, offline, offlineRecord = '', record = '' }) {
  const entered = train.trim().toUpperCase()
  const selectedRecord = matchingTrainRecord(entered, record)
  const recordedTrain = displayTrainCode(offlineRecord)
  const matchesRecord = offlineRecord && (
    entered === recordedTrain || entered === recordedTrain.split('/')[0]
  )
  let code = displayTrainCode(entered).split('/')[0]
  if (selectedRecord) code = displayTrainCode(entered)
  else if (offline) code = matchesRecord ? offlineRecord : entered

  return {
    path: '/train',
    query: {
      train: code || undefined,
      date: !offline && !isTrainQueryDateInRange(date) ? trainQueryRange().today : date,
      source: offline ? 'llt' : undefined,
      ...(selectedRecord && selectedRecord !== code ? { record: selectedRecord } : {}),
    },
  }
}
