import { coloredTrackSections } from './trackSpeed.js'

export function addVectorTrack(map, segments) {
  const features = coloredTrackSections(segments).map(section => ({
    type: 'Feature', properties: { color: section.color },
    geometry: { type: 'LineString', coordinates: section.points.map(([lat, lon]) => [lon, lat]) },
  }))
  map.addSource('recorded-track', { type: 'geojson', data: { type: 'FeatureCollection', features } })
  map.addLayer({
    id: 'track-outline', type: 'line', source: 'recorded-track',
    paint: { 'line-color': '#f8fafc', 'line-width': 9 },
  })
  map.addLayer({
    id: 'track-speed', type: 'line', source: 'recorded-track',
    paint: { 'line-color': ['get', 'color'], 'line-width': 5 },
  })
}
