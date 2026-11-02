export const currentBackupAppVersion = () => globalThis.window?.CRTicketAndroid?.backupAppVersion?.()
  || (typeof __CRTICKET_VERSION__ === 'string' ? __CRTICKET_VERSION__ : undefined)

// Compare application releases independently of the backup schema.
export function newerBackup(backup, current) {
  const parse = value => typeof value === 'string'
    ? /^(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/.exec(value) : null
  const source = parse(backup?.appVersion)
  const target = parse(current)
  if (!source || !target) return false
  for (let i = 1; i <= 3; i++) {
    if (Number(source[i]) !== Number(target[i])) return Number(source[i]) > Number(target[i])
  }
  return false
}

export function checkBackupCompatibility(text, current, parse, validate, updateRequired) {
  const source = JSON.parse(text)
  const newer = newerBackup(source, current)
  try {
    const backup = parse(text)
    validate?.(backup)
    return { backup, newer }
  } catch (error) {
    if (newer) throw new Error(updateRequired, { cause: error })
    throw error
  }
}
