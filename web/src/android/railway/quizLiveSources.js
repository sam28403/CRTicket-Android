function minutes(clock, day) {
  if (clock == null) return null
  return day * 1440 + Number(clock.slice(0, 2)) * 60 + Number(clock.slice(3))
}

// Only a database returned by the online fresh loader may reach this bridge.
export function quizLiveSources(database) {
  return {
    schema: 1,
    version: database.version,
    stations: [],
    lines: [],
    trains: [...database.trains.values()].map(item => ({
      train: item.train,
      schedule: item.schedule,
      stops: item.stops.map(stop => [
        stop.station,
        minutes(stop.arrival, stop.arrivalDay),
        minutes(stop.departure, stop.departureDay),
        stop.mileage,
      ]),
    })),
    routes: database.routing || [],
  }
}
