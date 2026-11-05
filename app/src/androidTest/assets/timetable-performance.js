(() => {
    let timer
    let previous
    let samples
    window.timetablePerfStart = () => {
        clearInterval(timer)
        previous = performance.now()
        samples = []
        timer = setInterval(() => {
            const now = performance.now()
            samples.push(now - previous)
            previous = now
        }, 16)
    }
    window.timetablePerfStop = () => {
        clearInterval(timer)
        return {
            samples: samples.length,
            maxJsIntervalMs: Math.max(0, ...samples),
            intervalsOver50ms: samples.filter(value => value > 50).length,
        }
    }
})()
