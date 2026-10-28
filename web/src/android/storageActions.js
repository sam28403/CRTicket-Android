import { native } from './platform.js'
import { readArchive } from './railway/archiveStore.js'
import { loadLltDatabase } from './railway/lltLoader.js'

export function initializeNativeStorage() {
    window.crticketStorage = async (id, action) => {
        try {
            if (action === 'clear') await loadLltDatabase.clear()
            const archive = await readArchive()
            const bytes = archive?.bytes?.byteLength || 0
            native.settingsResult(JSON.stringify({ id, success: true, bytes }))
        } catch {
            native.settingsResult(JSON.stringify({ id, success: false }))
        }
    }
}
