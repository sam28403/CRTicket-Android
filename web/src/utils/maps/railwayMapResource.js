export const railwayProviders = {
  openrailway: {
    origin: 'https://openrailwaymap.app', style: '/style.json', initialZoom: 14,
    attribution: '<a href="https://openrailwaymap.app/">OpenRailwayMap</a>',
  },
}

const templates = value => value.replace(/%7B/gi, '{').replace(/%7D/gi, '}')

export function railwayMapResource(value, source) {
  const provider = railwayProviders[source]
  if (!provider) throw new Error('Unknown railway map source')
  const url = new URL(value, `${provider.origin}/`)
  if (url.origin !== provider.origin) return url.href
  return templates(`/railway-map/${source}${url.pathname}${url.search}`)
}

