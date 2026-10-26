import { displayTrainCode } from './trainDisplay.js'
import { isTrainQueryDateInRange, trainQueryRange } from './trainQueryDate.js'
import { matchingTrainRecord } from './trainQueryRecord.js'

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
