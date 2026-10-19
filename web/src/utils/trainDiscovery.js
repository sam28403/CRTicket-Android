import { displayTrainCode } from './trainDisplay.js'
import { getTrainQueryNumberError } from './trainQueryValidation.js'

export function addTrainHistory(history, code) {
  const train = displayTrainCode(code).trim().toUpperCase().split('/')[0]
  if (!/^[A-Z]?\d{1,5}$/.test(train)) return history
  return [train, ...history.filter(item => item !== train)].slice(0, 10)
}

// A date seed keeps the same picks when the page or app is reopened that day.
export function dailyTrainRecommendations(trains, date) {
  let seed = [...date].reduce((value, char) => Math.imul(value, 31) + char.charCodeAt(0) | 0, 0)
  const seen = new Set()
  const candidates = trains.filter(row => {
    const code = displayTrainCode(row.train).split('/')[0]
    if (getTrainQueryNumberError(code) || seen.has(code)) return false
    seen.add(code)
    return true
  })
  for (let i = candidates.length - 1; i > 0; i--) {
    seed = (Math.imul(seed, 1664525) + 1013904223) | 0
    const j = (seed >>> 0) % (i + 1)
    ;[candidates[i], candidates[j]] = [candidates[j], candidates[i]]
  }
  return candidates.slice(0, 3).map(row => ({
    ...row,
    train: displayTrainCode(row.train),
    queryTrain: displayTrainCode(row.train).split('/')[0],
  }))
}
