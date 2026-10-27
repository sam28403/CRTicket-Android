import catalog from './data/quiz-station-cities.json' with { type: 'json' }

export function quizStationCities(stations) {
    const official = new Map(stations.map(row => [row.name, row.city]))
    const cities = new Map()
    for (const [city, names] of Object.entries(catalog)) {
        for (const name of names) {
            if (official.get(name) !== city || name.includes(city)) continue
            if (cities.has(name)) throw new Error(`Conflicting quiz city for ${name}`)
            cities.set(name, city)
        }
    }
    return cities
}

export function attachQuizStationCities(source, stations) {
    const cities = quizStationCities(stations)
    return {
        ...source,
        stationCitySource: 'Existing 12306 station catalog intersected with reviewed city-station pairs',
        stations: source.stations.map(([name, longitude, latitude]) => [
            name, longitude, latitude, cities.get(name) || '',
        ]),
    }
}
