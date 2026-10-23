import 'leaflet/dist/leaflet.css'
import type { LocationMap, LocationMapOptions, LocationPoint } from './location-types'

export async function createLeafletLocationMap(options: LocationMapOptions): Promise<LocationMap> {
  const L = (await import('leaflet')).default
  options.signal.throwIfAborted()
  const initial: [number, number] = options.selected
    ? [options.selected.latitude, options.selected.longitude]
    : [21.028, 105.78]
  const map = L.map(options.container, { scrollWheelZoom: false }).setView(
    initial,
    options.selected ? 16 : 13,
  )
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap contributors',
    referrerPolicy: 'origin',
  }).addTo(map)
  const icon = L.divIcon({
    className: 'leaflet-custom-marker',
    iconSize: [0, 0],
    html: `<div style="display:flex;align-items:center;justify-content:center;width:34px;height:34px;background:#059669;color:white;border-radius:50%;box-shadow:0 10px 15px -3px rgba(0,0,0,0.3);border:3px solid white;transform:translate(-50%,-100%);"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg></div>`,
  })
  let pin: import('leaflet').Marker | undefined
  function place(point: LocationPoint) {
    const position: [number, number] = [point.latitude, point.longitude]
    if (pin) pin.setLatLng(position)
    else {
      pin = L.marker(position, { icon, draggable: !options.disabled() }).addTo(map)
      pin.on('dragend', () => {
        if (pin) select(pin.getLatLng())
      })
    }
  }
  function select(position: import('leaflet').LatLng) {
    if (options.disabled()) return
    const point = {
      latitude: Number(position.lat.toFixed(7)),
      longitude: Number(position.lng.toFixed(7)),
    }
    place(point)
    map.panTo(position)
    options.onChange(point)
  }
  if (options.selected) place(options.selected)
  map.on('click', (event: import('leaflet').LeafletMouseEvent) => select(event.latlng))
  const observer = new ResizeObserver(() => map.invalidateSize())
  observer.observe(options.container)
  return {
    setPosition(point) {
      place(point)
      map.setView([point.latitude, point.longitude], 16)
    },
    sync(value, disabled) {
      if (value) {
        place(value)
        map.setView([value.latitude, value.longitude], 16)
      } else if (pin) {
        pin.remove()
        pin = undefined
      }
      if (disabled) pin?.dragging?.disable()
      else pin?.dragging?.enable()
    },
    focus(point) {
      map.setView([point.latitude, point.longitude], 16)
    },
    destroy() {
      observer.disconnect()
      pin?.off()
      map.off()
      map.remove()
      pin = undefined
    },
  }
}
