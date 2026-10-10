import express from 'express'
import db from '../db/db.js'
import { getDefaultUser } from '../defaultUser.js'
import statisticsRoutes from './userStatistics.js'
import backupRoutes from './userBackup.js'

const router = express.Router()

router.get('/session', (req, res) => {
    res.json({ success: true, user: getDefaultUser() })
})

router.post('/update-avatar', (req, res) => {
    const avatar = req.body.avatar
    if (avatar != null && (
        typeof avatar !== 'string'
        || avatar.length > 400000
        || !/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(avatar)
    )) {
        return res.status(400).json({ success: false, message: '头像数据无效' })
    }
    const user = getDefaultUser()
    db.prepare('UPDATE users SET avatar = ? WHERE id = ?')
        .run(avatar || null, user.id)
    res.json({
        success: true,
        message: avatar ? '头像已更新' : '头像已清除',
        user: getDefaultUser(),
    })
})

router.use(statisticsRoutes)
router.use(backupRoutes)

export default router
