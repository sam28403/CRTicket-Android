import { parseStations } from './stations.js'
import { normalizeStationName } from './stationName.js'

// rail.re/bureau.js and telecode.js: use the final telecode letter, never a city guess.
// Copyright (c) 2017-2023 Arnie97. MIT license: /licenses/moerail.txt.
const bureauGroups = [
  ['BX', '哈局'],
  ['TDL', '沈局'],
  ['P', '京局'],
  ['V', '太局'],
  ['C', '呼局'],
  ['F', '郑局'],
  ['N', '武局'],
  ['Y', '西局'],
  ['K', '济局'],
  ['HU', '上局'],
  ['GS', '南局'],
  ['QA', '广铁'],
  ['Z', '宁局'],
  ['WE', '成局'],
  ['M', '昆局'],
  ['J', '兰局'],
  ['R', '乌局'],
  ['O', '青藏'],
]
const bureaus = new Map(bureauGroups.flatMap(([letters, name]) =>
  [...letters].map(letter => [letter, name])))
let stationCodes

export function bureauForTelecode(telecode) {
  if (!/^[A-Z]{3}$/.test(telecode || '')) return ''
  if (telecode === 'XJA') return '港铁'
  return bureaus.get(telecode.slice(-1)) || ''
}

export function stationBureau(name) {
  stationCodes ??= new Map(parseStations().map(station =>
    [normalizeStationName(station.name), station.telecode]))
  return bureauForTelecode(stationCodes.get(normalizeStationName(name)))
}
