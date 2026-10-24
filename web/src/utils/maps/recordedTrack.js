export function validTrackSegments(value) {
  if (!Array.isArray(value)) return []
  return value.filter(Array.isArray).flatMap(segment => {
    const result = []
    let current = []
    for (const point of segment) {
      if (Array.isArray(point) && (point.length === 2 || point.length === 3)
        && point.slice(0, 2).every(Number.isFinite)
        && Math.abs(point[0]) <= 85.05112878 && Math.abs(point[1]) <= 180) {
        current.push(point)
      } else if (current.length) {
        result.push(current)
        current = []
      }
    }
    if (current.length) result.push(current)
    return result
  })
}
