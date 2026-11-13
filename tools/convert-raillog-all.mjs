import assert from 'node:assert/strict'
import { appendFile, mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { dirname, join, resolve } from 'node:path'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import { parseLltArchive } from '../web/src/android/railway/lltParser.js'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const outputDirectory = join(root, 'data', 'RailLog')
const workDirectory = join(root, 'build', 'research', 'raillog-all')
const progressPath = join(outputDirectory, 'progress.json')
const limitArg = process.argv.find(value => value.startsWith('--limit='))
const limit = limitArg ? Number(limitArg.slice('--limit='.length)) : Infinity
assert(!limitArg || (Number.isInteger(limit) && limit > 0), 'limit 必须是正整数')

await Promise.all([
    mkdir(outputDirectory, { recursive: true }),
    mkdir(workDirectory, { recursive: true }),
    mkdir(join(root, 'logs'), { recursive: true }),
])

let versionsResponse
for (let attempt = 0; ; attempt++) {
    try {
        versionsResponse = await fetch('https://api.raillog.top/api/train-timetables/versions', {
            signal: AbortSignal.timeout(30000),
        })
        if (versionsResponse.ok || attempt >= 5) break
    } catch (error) {
        if (attempt >= 5) throw error
    }
    await delay(Math.min(60000, 2000 * 2 ** attempt))
}
assert(versionsResponse.ok, `RailLog 版本列表请求失败：HTTP ${versionsResponse.status}`)
const versions = await versionsResponse.json()
assert(Array.isArray(versions) && versions.length, 'RailLog 版本列表为空')

let progress = { source: 'https://api.raillog.top/api/train-timetables/versions', completed: [] }
try {
    progress = JSON.parse(await readFile(progressPath, 'utf8'))
} catch (error) {
    if (error.code !== 'ENOENT') throw error
}
const completed = new Set(progress.completed)
const writeProgress = async (current, error = null) => {
    progress = {
        source: 'https://api.raillog.top/api/train-timetables/versions',
        total: versions.length,
        completed: [...completed],
        current,
        updatedAt: new Date().toISOString(),
        ...(error ? { error } : {}),
    }
    const temporary = `${progressPath}.tmp`
    const contents = JSON.stringify(progress, null, 2)
    await writeFile(temporary, contents)
    for (let attempt = 0; ; attempt++) {
        try {
            await rename(temporary, progressPath)
            return
        } catch (error) {
            if (error.code !== 'EPERM') throw error
            if (attempt >= 4) {
                await writeFile(progressPath, contents)
                return
            }
            await delay(100 * (attempt + 1))
        }
    }
}

function runNode(args, logPath) {
    return new Promise((resolveRun, rejectRun) => {
        const child = spawn(process.execPath, args, { cwd: root, windowsHide: true })
        let output = ''
        child.stdout.setEncoding('utf8')
        child.stderr.setEncoding('utf8')
        child.stdout.on('data', chunk => { output += chunk })
        child.stderr.on('data', chunk => { output += chunk })
        child.on('error', rejectRun)
        child.on('close', async code => {
            await appendFile(logPath, output)
            if (code === 0) resolveRun(output)
            else rejectRun(new Error(`命令退出码 ${code}，详情见 ${logPath}`))
        })
    })
}

let converted = 0
for (const version of versions) {
    if (completed.has(version)) continue
    if (converted >= limit) break
    const compact = version.replaceAll('.', '')
    const archive = join(outputDirectory, `${compact}.db`)
    const source = join(workDirectory, `${version}.json`)
    const log = join(root, 'logs', `raillog-${compact}.log`)
    await writeProgress(version)
    try {
        await runNode([
            'tools/download-raillog-timetable.mjs', version, workDirectory,
        ], log)
        await runNode([
            'tools/convert-raillog-timetable.mjs', source, archive,
        ], log)
        const database = parseLltArchive(await readFile(archive))
        assert.equal(database.version, compact, '成品数据库版本不匹配')
        completed.add(version)
        converted++
        await writeProgress(null)
        console.log(`已完成 ${version}，${completed.size}/${versions.length}`)
    } catch (error) {
        await writeProgress(version, error.message)
        throw error
    }
}
if (completed.size === versions.length) await writeProgress(null)
console.log(JSON.stringify({ convertedThisRun: converted, completed: completed.size, total: versions.length }))
