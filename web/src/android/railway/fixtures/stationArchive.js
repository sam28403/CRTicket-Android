import { zipSync } from 'fflate'

function dictionary(names) {
  const parts = names.map(name => {
    const bytes = Buffer.from(name)
    const length = Buffer.alloc(2)
    length.writeUInt16BE(bytes.length)
    return Buffer.concat([length, bytes])
  })
  const count = Buffer.alloc(2)
  count.writeUInt16BE(names.length)
  return Buffer.concat([count, ...parts])
}

function dateBytes(date) {
  let value = Number(date.replaceAll('-', ''))
  return Array.from({ length: 4 }, () => {
    const byte = value % 128
    value = Math.floor(value / 128)
    return byte
  })
}

export function stationArchive({ count = 1, version = '20261016' } = {}) {
  const files = Object.fromEntries(Array.from({ length: 20 }, (_, i) => [`T${i}.dat`, Buffer.alloc(0)]))
  files['t.i'] = dictionary(Array.from({ length: count }, (_, i) => `G${i + 1}`))
  files['s.i'] = dictionary(['北京南', '上海'])
  files['ver.txt'] = Buffer.from(version)
  files['T0.dat'] = Buffer.concat(Array.from({ length: count }, (_, i) => {
    const record = Buffer.alloc(31)
    record.set([Math.floor(i / 255), i % 255], 0)
    record.set(dateBytes('2020-01-01'), 5)
    record.set(dateBytes('2099-12-31'), 9)
    record.writeUInt16BE(2, 15)
    const hour = count === 1 ? 8 : Math.floor(i / 13)
    const minute = i % 13 * 4
    record.set([0, 0, hour, minute, 0, 0, 0], 17)
    record.set([0, 1, (hour + 3) % 24, minute, 0, 5, 43], 24)
    return record
  }))
  return zipSync(files)
}
