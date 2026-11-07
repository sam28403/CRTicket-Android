import { execFile } from 'node:child_process'
import { mkdtemp, readFile, readdir, rmdir, unlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { basename, dirname, join, resolve } from 'node:path'
import { promisify } from 'node:util'
import { zipSync } from '../web/node_modules/fflate/esm/index.mjs'
import { parseLltArchive } from '../web/src/android/railway/lltParser.js'

const execFileAsync = promisify(execFile)

async function findAapt2() {
    let sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT
    if (!sdk) {
        const properties = await readFile(resolve('local.properties'), 'utf8')
        sdk = /^sdk\.dir=(.+)$/m.exec(properties)?.[1]
            ?.trim().replaceAll('\\:', ':').replaceAll('\\\\', '\\')
    }
    if (!sdk) throw new Error('找不到 Android SDK，请设置 ANDROID_HOME')
    const buildTools = join(sdk, 'build-tools')
    const versions = (await readdir(buildTools)).sort((a, b) =>
        b.localeCompare(a, 'en', { numeric: true }))
    for (const version of versions) {
        const executable = join(buildTools, version, process.platform === 'win32' ? 'aapt2.exe' : 'aapt2')
        try {
            await execFileAsync(executable, ['version'])
            return executable
        } catch {
            // Try the next installed build-tools version.
        }
    }
    throw new Error('Android SDK 中找不到可运行的 aapt2')
}

async function readResourceMap(directory) {
    const app = dirname(directory)
    const resources = await readFile(join(app, 'resources.arsc'))
    const manifest = await readFile(join(app, 'AndroidManifest.xml'))
    const temp = await mkdtemp(join(tmpdir(), 'crticket-llt-res-'))
    const apk = join(temp, 'resources.apk')
    try {
        await writeFile(apk, zipSync({
            'resources.arsc': resources,
            'AndroidManifest.xml': manifest,
        }))
        const aapt2 = await findAapt2()
        const { stdout } = await execFileAsync(aapt2, ['dump', 'resources', apk], {
            maxBuffer: 32 * 1024 * 1024,
        })
        const map = new Map()
        const pattern = /^\s*resource 0x[\da-f]+ raw\/([^\s]+)\r?\n\s*\(\) \(file\) res\/([^\s/\\]+)\s*$/gm
        for (const match of stdout.matchAll(pattern)) {
            if (map.has(match[1])) throw new Error(`资源映射重复：${match[1]}`)
            map.set(match[1], match[2])
        }
        return map
    } finally {
        await unlink(apk).catch(() => {})
        await rmdir(temp)
    }
}

async function convert(directory, output) {
    const map = await readResourceMap(directory)
    const names = new Map([
        ['s_i', 's.i'], ['t_i', 't.i'], ['t_rule', 't.rule'], ['ver', 'ver.txt'],
        ...Array.from({ length: 20 }, (_, index) => [`t${index}`, `T${index}.dat`]),
    ])
    for (const name of ['xw', 'extra', 'jlb']) {
        if (map.has(name)) names.set(name, `${name}.dat`)
    }
    const files = {}
    const actualNames = new Set(await readdir(directory))
    for (const [resource, filename] of names) {
        const source = map.get(resource)
        if (!source || basename(source) !== source) {
            throw new Error(`资源表中找不到唯一的 ${resource} 文件`)
        }
        if (!actualNames.has(source)) {
            throw new Error(`资源文件 ${source} 缺失或大小写不匹配，请从原始 APK 重新解包`)
        }
        files[filename] = await readFile(join(directory, source))
    }
    const bytes = zipSync(files, { level: 9 })
    const database = parseLltArchive(bytes)
    await writeFile(output, bytes, { flag: 'wx' })
    console.log(JSON.stringify({
        output,
        version: database.version,
        trains: database.trains.size,
        stops: [...database.trains.values()].reduce((sum, train) => sum + train.stops.length, 0),
        routing: database.routing?.length ?? 0,
        bytes: bytes.length,
    }))
}

if (process.argv.length < 3 || process.argv.length > 4) {
    throw new Error('用法：node tools/convert-legacy-llt3.mjs <res目录> [输出文件]')
}
const directory = resolve(process.argv[2])
const output = process.argv[3] ? resolve(process.argv[3]) : join(directory, 'an.db')
await convert(directory, output)
