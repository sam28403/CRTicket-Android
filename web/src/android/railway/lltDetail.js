import { findLltTrainCandidates } from './lltList.js'

export function lltTrainDetail(database, train, date) {
  if (typeof train !== 'string' || train.length > 40
    || !/^[A-Z]?\d{1,5}[A-Z]?(?:\/[A-Z]?\d{1,5}[A-Z]?)*$/.test(train)) {
    throw new Error('请选择有效车次')
  }
  const candidates = findLltTrainCandidates(database, train, date)
  if (candidates.length > 1) {
    throw new Error('该日期存在多个同名车次，请从车次列表选择完整车次')
  }
  const item = candidates[0]
  const stops = item?.stops || []
  return {
    success: true,
    train: item?.train || train,
    date,
    source: '路路通',
    mileageVersion: database.version,
    from: stops[0]?.station || '',
    to: stops.at(-1)?.station || '',
    stops,
    warnings: [],
  }
}
