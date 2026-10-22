export function validRailfanBackup(native) {
  if (native.railfanToolsEnabled !== undefined && typeof native.railfanToolsEnabled !== 'boolean') {
    return false
  }
  if (native.stationVisits !== undefined) {
    if (!Array.isArray(native.stationVisits) || native.stationVisits.length > 10000) return false
    for (const row of native.stationVisits) {
      if (!row || typeof row !== 'object' || Array.isArray(row)
          || typeof row.station !== 'string' || !row.station.trim() || row.station.length > 100
          || /[\u0000-\u001f\u007f-\u009f]/.test(row.station)
          || !Number.isSafeInteger(row.time) || row.time <= 0
          || (row.historical !== undefined && typeof row.historical !== 'boolean')) return false
    }
  }
  if (native.vehicleSearchHistory !== undefined) {
    if (!Array.isArray(native.vehicleSearchHistory) || native.vehicleSearchHistory.length > 10) return false
    if (native.vehicleSearchHistory.some(keyword => typeof keyword !== 'string'
        || keyword.length > 100 || !/^[-+\w@]+$/.test(keyword.trim()))) return false
  }
  return true
}
