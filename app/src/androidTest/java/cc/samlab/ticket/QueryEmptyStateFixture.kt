package cc.samlab.ticket

internal fun TabletPageFixture.prepareEmptyStateResponses() {
    val assets = instrumentation.targetContext.assets
    val apiFile = assets.list("web/assets")!!.first { name ->
        name.startsWith("api-") && assets.open("web/assets/$name")
            .bufferedReader().use { it.readText().contains("withCredentials") }
    }
    evaluate("""
        window.emptyStateSavedHistory = Object.fromEntries(
            ['crticket-train-query-history', 'crticket-station-query-history']
                .map(key => [key, localStorage.getItem(key)])
        );
        window.emptyStateMode = 'failure';
        window.emptyStateRequests = 0;
        window.emptyStateReady = false;
        import('https://appassets.androidplatform.net/assets/$apiFile').then(module => {
            const api = Object.values(module).find(value => value?.defaults?.withCredentials === true);
            api.defaults.adapter = async config => {
                let data = {success: true, stations: [], trains: []};
                if (['/station', '/llt-trains/detail', '/train-info'].includes(config.url)) {
                    window.emptyStateRequests++;
                    if (window.emptyStateMode === 'failure') throw new Error('Simulated query failure');
                    data = {
                        success: true, station: config.params.station,
                        train: config.params.train, date: config.params.date,
                        from: '', to: '', total: 0, trains: [], stops: [], source: '路路通'
                    };
                }
                return {status: 200, statusText: 'OK', headers: {}, config, data};
            };
            window.emptyStateReady = true;
        });
    """.trimIndent())
    composeWaitForFixture()
}

private fun TabletPageFixture.composeWaitForFixture() {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (evaluate("window.emptyStateReady") != "true") {
        check(System.nanoTime() < deadline) { "Query fixture did not load" }
        Thread.sleep(50)
    }
}

internal fun TabletPageFixture.restoreEmptyStateHistory() {
    evaluate("""
        Object.entries(window.emptyStateSavedHistory || {}).forEach(([key, value]) => {
            if (value === null) localStorage.removeItem(key);
            else localStorage.setItem(key, value);
        });
    """.trimIndent())
}
