import test from 'node:test'
import assert from 'node:assert/strict'
import coordinates from '../web/src/utils/maps/stationCoordinates.js'
import { buildArrivalStations } from './package-arrival-stations.mjs'

test('所有坐标名称都有对应站点，别名合并不改变坐标', () => {
    const stations = buildArrivalStations()
    const byAlias = new Map(stations.flatMap(station =>
        station.aliases.map(name => [name, station])))
    assert.equal(byAlias.size, Object.keys(coordinates).length)
    for (const [name, [longitude, latitude]] of Object.entries(coordinates)) {
        assert.equal(byAlias.get(name).longitude, longitude)
        assert.equal(byAlias.get(name).latitude, latitude)
    }
    for (const name of ['北京南', '香港西九龙', '万象']) {
        assert.equal(byAlias.get(name).name, name)
    }
    assert.equal(new Set(stations.map(station => station.name)).size, stations.length)
})
