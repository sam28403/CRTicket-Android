export function createTrainListSnapshot() {
  let rows
  let date
  return snapshot => {
    const next = snapshot.result?.trains
    if (!Array.isArray(next)) return snapshot
    if (next === rows && snapshot.date === date) {
      return { ...snapshot, result: null, reuseRows: true }
    }
    rows = next
    date = snapshot.date
    return snapshot
  }
}
