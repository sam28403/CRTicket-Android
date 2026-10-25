import { normalizeStationName } from './stationName.js'

export function stationLocation(name) {
  return {
    path: '/station',
    query: { station: normalizeStationName(name) },
  }
}

export function stationTrainLocation(row, source, date) {
  return {
    path: '/train',
    query: {
      // 查询页先使用在线车次，完整记录号保留给离线回退。
      train: row.train,
      // 本站当天到发的列车可能在前一天或更早始发。
      date: row.trainDate || date,
    },
  }
}
