import { test } from 'node:test'
import assert from 'node:assert/strict'
import { quizStationCities, attachQuizStationCities } from './quiz-city-catalog.mjs'

test('only explicitly reviewed stations agreeing with the source city are eligible', () => {
    const cities = quizStationCities([
        { name: '千岛湖', city: '杭州' },
        { name: '杭州东', city: '杭州' },
        { name: '余姚北', city: '杭州' },
        { name: 'Unknown', city: '杭州' },
    ])
    assert.deepEqual([...cities], [['千岛湖', '杭州']])
})

test('enrichment preserves coordinates and unrelated timetable records', () => {
    const original = { stations: [['千岛湖', 119, 29], ['Unknown', 120, 30]], trains: [{ train: 'G1' }] }
    const source = attachQuizStationCities(original, [{ name: '千岛湖', city: '杭州' }])
    assert.deepEqual(source.stations, [['千岛湖', 119, 29, '杭州'], ['Unknown', 120, 30, '']])
    assert.equal(source.trains, original.trains)
    assert.deepEqual(original.stations[0], ['千岛湖', 119, 29])
})
