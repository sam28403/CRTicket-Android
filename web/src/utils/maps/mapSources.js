const osmCredit = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
const franceCredit = `${osmCredit}, <a href="https://www.openstreetmap.fr/">OSM France</a>`

export const mapSources = [
  { id: 'auto', name: '自动' },
  { id: 'transport', name: 'OSM交通' },
  {
    id: 'osm',
    name: 'OpenStreetMap',
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    maxZoom: 19,
    attribution: osmCredit,
  },
  {
    id: 'hot',
    name: 'OSM France（HOT）',
    url: 'https://a.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png',
    maxZoom: 19,
    attribution: `${franceCredit}, HOT`,
  },
  {
    id: 'france',
    name: 'OSM France',
    url: 'https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png',
    maxZoom: 20,
    attribution: franceCredit,
  },
  {
    id: 'topo',
    name: 'OpenTopoMap',
    url: 'https://a.tile.opentopomap.org/{z}/{x}/{y}.png',
    maxZoom: 17,
    attribution: `${osmCredit}, SRTM, <a href="https://opentopomap.org">OpenTopoMap</a> (CC-BY-SA)`,
  },
]

export function normalizeMapSource(value) {
  return mapSources.some(source => source.id === value) ? value : 'auto'
}

export function rasterCandidates(value) {
  const id = normalizeMapSource(value)
  return (id === 'auto' ? ['hot', 'osm', 'france', 'topo'] : [id])
    .map(key => mapSources.find(source => source.id === key))
    .filter(source => source?.url)
}
