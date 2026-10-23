import { loadGoogleMaps } from './google-maps'
import type { LocationMap, LocationMapOptions, LocationPoint } from './location-types'

export async function createGoogleLocationMap(options: LocationMapOptions): Promise<LocationMap> {
  const { maps, marker } = await loadGoogleMaps()
  options.signal.throwIfAborted()
  const initial = options.selected
    ? { lat: options.selected.latitude, lng: options.selected.longitude }
    : { lat: 21.028, lng: 105.78 }
  const map = new maps.Map(options.container, {
    center: initial,
    zoom: options.selected ? 16 : 13,
    mapTypeControl: false,
    streetViewControl: false,
    fullscreenControl: false,
    clickableIcons: false,
    gestureHandling: 'cooperative',
  })
  const pin = new marker.Marker({
    map: options.selected ? map : null,
    position: options.selected ? initial : null,
    draggable: !options.disabled(),
    title: 'Vị trí cơ sở đã chọn',
  })
  function select(position: google.maps.LatLng) {
    if (options.disabled()) return
    pin.setPosition(position)
    pin.setMap(map)
    map.panTo(position)
    options.onChange({
      latitude: Number(position.lat().toFixed(7)),
      longitude: Number(position.lng().toFixed(7)),
    })
  }
  const click = map.addListener('click', (event: google.maps.MapMouseEvent) => {
    if (event.latLng) select(event.latLng)
  })
  const drag = pin.addListener('dragend', () => {
    const position = pin.getPosition()
    if (position) select(position)
  })
  const observer = new ResizeObserver(() => google.maps.event.trigger(map, 'resize'))
  observer.observe(options.container)
  function setPosition(point: LocationPoint) {
    const position = { lat: point.latitude, lng: point.longitude }
    pin.setPosition(position)
    pin.setMap(map)
    map.panTo(position)
    map.setZoom(16)
  }
  return {
    setPosition,
    sync(value, disabled) {
      if (value) {
        pin.setPosition({ lat: value.latitude, lng: value.longitude })
        pin.setMap(map)
      } else pin.setMap(null)
      pin.setDraggable(!disabled)
    },
    focus(point) {
      map.setCenter({ lat: point.latitude, lng: point.longitude })
      map.setZoom(17)
    },
    destroy() {
      observer.disconnect()
      click.remove()
      drag.remove()
      pin.setMap(null)
      google.maps.event.clearInstanceListeners(map)
    },
  }
}
