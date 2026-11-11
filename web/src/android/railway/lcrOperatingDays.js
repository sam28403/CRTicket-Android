import rules from '../../../../data/lcr-offline-rules.json' with { type: 'json' }

const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

export function hasLcrOperatingRule(train) {
  return Object.hasOwn(rules.trains, train)
}

export function lcrOperatingDays(result) {
  if (result?.dataset !== 'lcr' && result?.timeZone !== 'vientiane') return []
  if (!hasLcrOperatingRule(result.train)) return []
  const rule = rules.trains[result.train]

  const start = Date.parse(rule.startDate ?? rules.startDate)
  const end = Date.parse(rule.endDate ?? rules.endDate)
  const days = []
  for (let timestamp = start; timestamp <= end; timestamp += 86400000) {
    const date = new Date(timestamp)
    const running = !rule.weekdays || rule.weekdays.includes(weekdays[date.getUTCDay()])
    days.push([date.toISOString().slice(0, 10).replaceAll('-', ''), running ? '1' : '0'])
  }
  return days
}
