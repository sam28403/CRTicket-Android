import { parseLltArchive } from './lltParser.js'

self.onmessage = ({ data }) => {
    try {
        self.postMessage({ database: parseLltArchive(data) })
    } catch (error) {
        self.postMessage({ error: error.message })
    }
}
