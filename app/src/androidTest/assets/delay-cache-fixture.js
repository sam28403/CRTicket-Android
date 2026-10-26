async function installDelayCacheFixture(apiFile) {
  const module = await import(`https://appassets.androidplatform.net/assets/${apiFile}`)
  const api = Object.values(module).find(value => value?.defaults?.withCredentials === true)
  const adapter = api.defaults.adapter
  const fixture = window.delayCacheFixture = {
    mode: 'valid', trainRequests: 0, stationRequests: 0, timetableRequests: 0, completed: 0,
  }
  const bridge = window.CRTicketAndroid
  window.CRTicketAndroid = new Proxy(bridge, {
    get(target, key) {
      if (key !== 'queryTrainDelay' && key !== 'queryStationDelay') return target[key]
      return (id, payload) => {
        const data = JSON.parse(payload)
        const isTrain = key === 'queryTrainDelay'
        fixture[isTrain ? 'trainRequests' : 'stationRequests']++
        let response = { success: false }
        if (fixture.mode !== 'failure') {
          const partial = fixture.mode === 'partial'
          const empty = fixture.mode === 'empty'
          const offsets = partial ? { arrivalOffset: 0 } : { arrivalOffset: 7, departureOffset: 9 }
          response = isTrain ? {
            success: true,
            result: {
              ...data, delayAvailable: !empty,
              stops: data.stops.map((row, index) => index === 1 && !empty ? { ...row, ...offsets } : row),
            },
          } : {
            success: true,
            rows: empty ? [] : data.trains.map(row => ({ id: row.id, ...offsets })),
          }
        }
        setTimeout(() => {
          window.crticketNotificationResult(id, response)
          fixture.completed++
        }, 50)
      }
    },
  })
  api.defaults.adapter = async config => {
    if (!['/train-info', '/station'].includes(config.url)) return adapter(config)
    fixture.timetableRequests++
    if (fixture.mode === 'timetableFailure') throw new Error('fixture network unavailable')
    const data = config.url === '/train-info' ? {
      success: true, train: config.params.train, date: config.params.date,
      source: '12306', from: '北京南', to: '上海虹桥', stops: [
        { no: '01', train: 'G1', station: '北京南', departure: '18:00', arrival: null,
          departureDay: 0, arrivalDay: 0, stay: null, mileage: 0 },
        { no: '02', train: 'G1', station: '南京南', departure: '19:56', arrival: '19:53',
          departureDay: 0, arrivalDay: 0, stay: 3, mileage: 1023 },
        { no: '03', train: 'G1', station: '上海虹桥', departure: null, arrival: '21:00',
          arrivalDay: 0, stay: null, mileage: 1318 },
      ],
    } : {
      success: true, station: config.params.station, date: config.params.date,
      source: '路路通', trains: [{
        id: 'cache-G1', train: 'G1', trainDate: config.params.date,
        from: '北京南', to: '上海虹桥', arrival: '19:53', departure: '19:56',
        arrivalLabel: '19:53', departureLabel: '19:56', stay: 3,
      }],
    }
    return { data, config, status: 200, statusText: 'OK', headers: {} }
  }
  fixture.ready = true
}
