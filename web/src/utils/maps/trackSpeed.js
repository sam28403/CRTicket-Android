export function speedColor(speed) {
  if (!Number.isFinite(speed)) return '#8795a8'
  const ratio = Math.max(0, Math.min(400, speed)) / 400
  const stops = [[38, 139, 255], [72, 232, 144], [255, 225, 90], [255, 68, 56]]
  const index = Math.min(2, Math.floor(ratio * 3))
  const fraction = ratio * 3 - index
  const low = stops[index]
  const high = stops[index + 1]
  return '#' + low.map((channel, index) => Math.round(channel + (high[index] - channel) * fraction)
    .toString(16).padStart(2, '0')).join('')
}

export function sectionSpeed(a, b) {
  if (!Number.isFinite(a[2]) || !Number.isFinite(b[2]) || b[2] <= a[2]) return null
  const radians = value => value * Math.PI / 180
  const lat = radians(b[0] - a[0])
  const lon = radians(b[1] - a[1])
  const h = Math.sin(lat / 2) ** 2
    + Math.cos(radians(a[0])) * Math.cos(radians(b[0])) * Math.sin(lon / 2) ** 2
  const meters = 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, h))))
  return meters * 3600 / (b[2] - a[2])
}

// Merge adjacent sections with similar speeds before grouping them into map layers.
export function coloredTrackSections(segments) {
  const result = []
  for (const segment of segments) {
    let current
    for (let index = 1; index < segment.length; index++) {
      const a = segment[index - 1]
      const b = segment[index]
      const speed = sectionSpeed(a, b)
      const bucket = speed === null ? null : Math.round(Math.min(400, speed) / 10) * 10
      const color = speedColor(bucket)
      if (current?.color === color) current.points.push(b.slice(0, 2))
      else {
        current = { color, points: [a.slice(0, 2), b.slice(0, 2)] }
        result.push(current)
      }
    }
  }
  return result
}
