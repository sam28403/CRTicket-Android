async function touchDrag(page, client, points, { finish = true } = {}) {
    await client.send('Input.dispatchTouchEvent', {
        type: 'touchStart', touchPoints: [{ x: points[0][0], y: points[0][1] }],
    })
    for (const [x, y] of points.slice(1)) {
        await new Promise(resolve => setTimeout(resolve, 45))
        await client.send('Input.dispatchTouchEvent', {
            type: 'touchMove', touchPoints: [{ x, y }],
        })
    }
    if (finish) {
        await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
        await page.waitForTimeout(200)
    }
}

function verticalPull(x, start, length = 190) {
    return Array.from({ length: 8 }, (_, index) => [x, start + length * index / 7])
}

module.exports = { touchDrag, verticalPull }
