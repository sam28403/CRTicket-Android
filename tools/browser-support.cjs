const { createServer } = require('node:http')
const { readFile } = require('node:fs/promises')
const path = require('node:path')

const root = path.resolve(__dirname, '../app/src/main/assets/web')
const types = {
    '.html': 'text/html',
    '.js': 'application/javascript',
    '.css': 'text/css',
    '.ttf': 'font/ttf',
    '.woff2': 'font/woff2',
    '.jpg': 'image/jpeg',
    '.png': 'image/png',
    '.json': 'application/json',
    '.geojson': 'application/json',
}

async function assetServer() {
    const server = createServer(async (req, res) => {
        try {
            const pathname = new URL(req.url, 'http://localhost').pathname
                .replace(/\/$/, '/index.html')
            const filename = path.resolve(root, '.' + pathname)
            if (!filename.startsWith(root + path.sep)) throw new Error('Invalid path')
            res.setHeader('Content-Type', types[path.extname(filename)] || 'application/octet-stream')
            res.end(await readFile(filename))
        } catch {
            res.writeHead(404)
            res.end('Not found')
        }
    })
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
    return { server, address: `http://127.0.0.1:${server.address().port}` }
}

function getPlaywright() {
    return require(process.env.CRTICKET_PLAYWRIGHT_PACKAGE || 'playwright')
}

module.exports = { assetServer, getPlaywright }
