import express from 'express'
import db from '../db/db.js'
import { defaultSession } from '../defaultUser.js'

const router = express.Router()

router.get('/statistics', (req, res) => {
    const { userId } = defaultSession()
    const counts = field => db.prepare(`
        SELECT ${field} AS station, COUNT(*) AS count
        FROM tickets
        WHERE user_id = ? AND ${field} IS NOT NULL AND ${field} != ''
        GROUP BY ${field}
        ORDER BY count DESC
    `).all(userId)

    res.json({
        success: true,
        data: {
            departures: counts('departure_station'),
            arrivals: counts('arrival_station'),
        },
    })
})

router.get('/ticket-history', (req, res) => {
    const { userId } = defaultSession()
    const data = db.prepare(`
        SELECT id,
            departure_station AS departureStation,
            arrival_station AS arrivalStation,
            travel_date AS travelDate,
            train_no AS trainNumber
        FROM tickets
        WHERE user_id = ?
        ORDER BY travel_date DESC
        LIMIT 500
    `).all(userId)

    res.json({ success: true, data })
})

router.get('/monthly-tickets', (req, res) => {
    const { userId } = defaultSession()
    const year = Number.parseInt(req.query.year) || new Date().getFullYear()
    const month = Number.parseInt(req.query.month) || new Date().getMonth() + 1
    const prefix = `${year}年${String(month).padStart(2, '0')}月`
    const tickets = db.prepare(`
        SELECT travel_date, COUNT(*) AS count
        FROM tickets
        WHERE user_id = ? AND travel_date LIKE ?
        GROUP BY travel_date
        ORDER BY travel_date
    `).all(userId, `${prefix}%`)

    res.json({ success: true, data: { year, month, tickets } })
})

export default router
