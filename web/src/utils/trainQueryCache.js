// Each visited query keeps its completed result; the Query button still fetches afresh.
export function createTrainQueryCache(limit = 16) {
  const entries = new Map()
  const key = (train, date, source) => JSON.stringify([
    String(train || '').trim().toUpperCase(), date, source === 'llt' ? 'llt' : '',
  ])
  return {
    get(train, date, source) {
      return entries.get(key(train, date, source))
    },
    set(train, date, source, snapshot) {
      const id = key(train, date, source)
      entries.delete(id)
      entries.set(id, snapshot)
      if (entries.size > limit) entries.delete(entries.keys().next().value)
    },
  }
}
