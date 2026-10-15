import { native } from './platform.js'

export function settingsText(key, ...args) {
  const strings = JSON.parse(native.settingsStrings())
  return strings[key].replace(/%(\d+)\$[sd]/g, (_, index) => String(args[Number(index) - 1]))
}
