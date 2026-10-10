import express from 'express'
import db from '../db/db.js'
import { defaultSession } from '../defaultUser.js'
import { INSERT_TICKET_SQL } from '../db/ticketQueries.js'
import { validTicket, ticketQuota } from './ticketValidation.js'
import { TICKET_FIELDS, comparableTicketValues, ticketFieldValues } from '../../src/utils/ticketFields.js'

const router = express.Router()
const fingerprint = ticket => JSON.stringify(comparableTicketValues(ticket))

router.post('/import-backup', (req, res) => {
    const { userId } = defaultSession()
    let backup = req.body.backup
    if (typeof backup === 'string') {
        try {
            backup = JSON.parse(backup)
        } catch {
            return res.status(400).json({ success: false, message: '备份文件格式错误' })
        }
    }
    const source = Array.isArray(backup) ? backup : backup?.tickets
    if (!Array.isArray(source) || source.length > 1000) {
        return res.status(400).json({
            success: false,
            message: '未找到车票数据，或超过单次1000条限制',
        })
    }

    const existing = db.prepare('SELECT * FROM tickets WHERE user_id = ?').all(userId)
    const seen = new Set(existing.map(fingerprint))
    const insert = db.prepare(INSERT_TICKET_SQL)
    let imported = 0
    let skipped = 0
    let invalid = 0

    try {
        db.transaction(() => {
            for (const raw of source) {
                if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
                    invalid++
                    continue
                }
                const ticket = Object.fromEntries(TICKET_FIELDS.map(field => [field, raw[field] ?? null]))
                if (!validTicket(ticket)) {
                    invalid++
                    continue
                }
                const key = fingerprint(ticket)
                if (seen.has(key)) {
                    skipped++
                    continue
                }
                if (existing.length + imported >= ticketQuota) {
                    throw new Error('导入后车票数量不可超过10000条')
                }
                insert.run(userId, ...ticketFieldValues(ticket))
                seen.add(key)
                imported++
            }
        })()
    } catch (error) {
        return res.status(409).json({ success: false, message: error.message })
    }

    res.json({
        success: true,
        message: '备份导入完成',
        data: { imported, skipped, invalid, total: source.length },
    })
})

export default router
