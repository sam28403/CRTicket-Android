const origin = 'https://openrailwaymap.app'

export function railwayVectorStyle(style) {
  const absolute = value => new URL(value, `${origin}/`).href
    .replace(/%7B/gi, '{').replace(/%7D/gi, '}')
  const excluded = new Set(['openhistoricalmap', 'dem'])
  const sources = Object.fromEntries(Object.entries(style.sources)
    .filter(([id]) => !excluded.has(id)).map(([id, source]) => [
    id, source.url ? { ...source, url: absolute(source.url) } : source,
  ]))
  sources.background = {
    type: 'raster', tileSize: 256,
    tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxzoom: 19,
  }
  return {
    ...style, sources,
    ...(style.glyphs ? { glyphs: absolute(style.glyphs) } : {}),
    sprite: style.sprite.map(sprite => ({ ...sprite, url: absolute(sprite.url) })),
    layers: [
      { id: 'background-map', type: 'raster', source: 'background' },
      ...style.layers.filter(layer => !excluded.has(layer.source)),
    ],
  }
}
