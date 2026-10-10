import db from './db/db.js'

const username = '默认用户'

// This Android backend deliberately has one persistent owner and no password login.
db.prepare(`
    INSERT OR IGNORE INTO users (username, password)
    VALUES (?, ?)
`).run(username, '!password-login-disabled')

const ownerId = db.prepare('SELECT id FROM users WHERE username = ?')
    .get(username).id

export function getDefaultUser() {
    return db.prepare('SELECT id, username, avatar FROM users WHERE id = ?')
        .get(ownerId)
}

export function defaultSession() {
    return { userId: ownerId, username }
}
