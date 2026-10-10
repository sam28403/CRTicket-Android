const { readFile, mkdir, writeFile } = require('node:fs/promises')
const path = require('node:path')
const { getPlaywright } = require('./browser-support.cjs')

const root = path.resolve(__dirname, '..')

async function vector(name, monochromeColor) {
    const xml = await readFile(path.join(root, 'app/src/main/res/drawable', name), 'utf8')
    const scale = xml.match(/android:scaleX="([^"]+)"/)[1]
    const paths = [...xml.matchAll(/<path\s+([\s\S]*?)\/>/g)].map(([, attributes]) => {
        const data = attributes.match(/android:pathData="([^"]+)"/)[1]
        const fill = monochromeColor || attributes.match(/android:fillColor="([^"]+)"/)[1]
        const rule = attributes.includes('evenOdd') ? 'evenodd' : 'nonzero'
        return `<path fill="${fill}" fill-rule="${rule}" d="${data}"/>`
    }).join('')
    return `<g transform="translate(54 54) scale(${scale}) translate(-54 -54)">${paths}</g>`
}

function circle(background, foreground) {
    return `<svg viewBox="18 18 72 72" xmlns="http://www.w3.org/2000/svg">
        <defs><clipPath id="circle"><circle cx="54" cy="54" r="36"/></clipPath></defs>
        <g clip-path="url(#circle)">
            <rect width="108" height="108" fill="${background}"/>${foreground}
        </g>
    </svg>`
}

async function main() {
    const colored = await vector('ic_launcher_foreground.xml')
    const light = await vector('ic_launcher_monochrome.xml', '#244D37')
    const dark = await vector('ic_launcher_monochrome.xml', '#B5EFCC')
    const html = `<html><head><meta charset="utf-8"><style>
        body{margin:0;padding:32px;font:16px system-ui;background:#f4f6fa;display:flex;gap:32px}
        figure{margin:0;text-align:center;width:180px}svg{width:180px;height:180px}
        figcaption{margin-top:16px;color:#253246}
    </style></head><body>
        <figure>${circle('#1677FF', colored)}<figcaption>圆形彩色图标</figcaption></figure>
        <figure>${circle('#D6F6DF', light)}<figcaption>Monochrome · 亮色</figcaption></figure>
        <figure>${circle('#244D37', dark)}<figcaption>Monochrome · 暗色</figcaption></figure>
    </body></html>`
    const output = path.join(root, 'build/verification')
    await mkdir(output, { recursive: true })
    await writeFile(path.join(output, 'launcher-icons.html'), html)
    const browser = await getPlaywright().chromium.launch({ channel: 'chrome', headless: true })
    try {
        const page = await browser.newPage({ viewport: { width: 668, height: 290 } })
        await page.setContent(html)
        await page.screenshot({ path: path.join(output, 'launcher-icons.png') })
    } finally {
        await browser.close()
    }
}

main().catch(error => {
    console.error(error)
    process.exitCode = 1
})
