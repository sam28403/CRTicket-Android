import { DomUtil, Layer } from 'leaflet'
import { canConnectTransport } from './transportConnection.js'

// Only the viewport is sent to the official embed; recorded points stay local.
export function transportBaseLayer(onError) {
  const TransportLayer = Layer.extend({
    onAdd(map) {
      this.map = map
      this.controller = new AbortController()
      this.frame = DomUtil.create('iframe', 'transport-basemap')
      this.frame.title = 'OSM Transport'
      Object.assign(this.frame.style, {
        position: 'absolute', border: '0', pointerEvents: 'none',
      })
      map.getPane('tilePane').appendChild(this.frame)
      map.on('moveend resize', this.sync, this)
      this.sync()
    },
    sync() {
      const map = this.map
      const bounds = map.getBounds()
      const size = map.getSize()
      DomUtil.setPosition(this.frame, map.containerPointToLayerPoint([0, 0]))
      this.frame.style.width = `${size.x}px`
      this.frame.style.height = `${size.y}px`
      const url = new URL('https://www.openstreetmap.org/export/embed.html')
      url.searchParams.set('layer', 'transportmap')
      // Avoid rounding the embed down to the preceding zoom level.
      url.searchParams.set('bbox', [
        bounds.getWest() + 1e-8, bounds.getSouth() + 1e-8,
        bounds.getEast() - 1e-8, bounds.getNorth() - 1e-8,
      ].join(','))
      clearTimeout(this.timer)
      this.timer = setTimeout(onError, 15000)
      this.frame.onload = () => clearTimeout(this.timer)
      this.frame.onerror = onError
      this.frame.src = url.toString()
      if (!navigator.userAgent.includes('CRTicket')) {
        canConnectTransport(url.toString(), { signal: this.controller.signal })
          .then(connected => { if (!connected && !this.controller.signal.aborted) onError() })
      }
    },
    onRemove(map) {
      map.off('moveend resize', this.sync, this)
      clearTimeout(this.timer)
      this.controller.abort()
      this.frame.onload = null
      this.frame.onerror = null
      this.frame.remove()
    },
    getAttribution() {
      return '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, '
        + '<a href="https://www.thunderforest.com/">Thunderforest</a>'
    },
  })
  return new TransportLayer()
}
