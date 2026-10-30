import { mapSources } from './maps/mapSources.js'

export function validBackupMapSource(value) {
  return typeof value === 'string' && (value === 'cnrail' || mapSources.some(source => source.id === value))
}

// Android owns its preference; mirror it into portable storage for browser restores.
// Older backups with no map field leave the current preference unchanged.
export function portableMapSourceSettings(settings) {
  const hasNative = Object.hasOwn(settings.native || {}, 'mapSource')
  const hasStorage = Object.hasOwn(settings.storage || {}, 'mapSource')
  if (!hasNative && !hasStorage) return settings
  const requested = hasNative ? settings.native.mapSource : settings.storage.mapSource ?? 'auto'
  const source = requested === 'cnrail' ? 'auto' : requested
  return {
    ...settings,
    storage: { ...settings.storage, mapSource: source },
    native: { ...settings.native, mapSource: source },
  }
}
