// Each visited query keeps its completed result; the Query button still fetches afresh.
export function createTrainQueryCache(limit = 16) {
  const entries = new Map()
  const key = (train, date, source, dataset) => JSON.stringify([
    String(train || '').trim().toUpperCase(), date, source === 'llt' ? 'llt' : '', dataset || '',
  ])
  return {
    clear() { entries.clear() },
    get(train, date, source, dataset) {
      return entries.get(key(train, date, source, dataset))
    },
    set(train, date, source, snapshot, dataset) {
      const id = key(train, date, source, dataset)
      entries.delete(id)
      entries.set(id, snapshot)
      if (entries.size > limit) entries.delete(entries.keys().next().value)
    },
  }
}
