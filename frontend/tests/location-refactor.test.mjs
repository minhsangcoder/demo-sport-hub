import assert from 'node:assert/strict'
import test from 'node:test'
import { moduleLoader, hookDriver, deferred, flush } from './support/load-module.mjs'

const plain = value => JSON.parse(JSON.stringify(value))
test('moved coordinate parser preserves raw, long Google URLs, query parameters and rounding', () => {
  const parse = moduleLoader()('features/facility/location-coordinates.ts').parseCoordinatesLocally
  for (const value of ['21.028000012, 105.78000001', '21.028000012 105.78000001', 'https://www.google.com/maps/@21.028000012,105.78000001,16z', 'https://www.google.com/maps/data=!3d21.028000012!4d105.78000001', 'https://maps.google.com/?q=21.028000012,105.78000001', 'https://maps.google.com/?destination=21.028000012,105.78000001']) {
    assert.deepEqual(plain(parse(value)), { latitude: 21.028, longitude: 105.78 })
  }
  for (const value of ['', 'Không phải tọa độ', '99, 105', '21, 999', 'https://maps.app.goo.gl/needs-resolution']) assert.equal(parse(value), null)
  assert.deepEqual(plain(parse('0, 0')), { latitude: 0, longitude: 0 })
})

test('Google provider keeps read-only interaction, external value/focus and removes all listeners/observers', async () => {
  const maps = [], pins = [], changes = [], removed = []
  let disabled = true
  class Map {
    constructor() { this.listeners = {}; maps.push(this) }
    addListener(name, action) { this.listeners[name] = action; return { remove: () => removed.push(name) } }
    panTo(point) { this.position = point } setZoom(zoom) { this.zoom = zoom } setCenter(point) { this.position = point }
  }
  class Marker {
    constructor(options) { Object.assign(this, options); this.listeners = {}; pins.push(this) }
    addListener(name, action) { this.listeners[name] = action; return { remove: () => removed.push(name) } }
    setPosition(value) { this.position = value } getPosition() { return this.position } setMap(map) { this.map = map } setDraggable(value) { this.draggable = value }
  }
  const load = moduleLoader({ google: { maps: { event: { trigger() {}, clearInstanceListeners: () => removed.push('map-events') } } }, ResizeObserver: class { observe() {} disconnect() { removed.push('observer') } } }, {
    './google-maps': { loadGoogleMaps: async () => ({ maps: { Map }, marker: { Marker } }) },
  })
  const create = load('features/facility/google-location-map.ts').createGoogleLocationMap
  const map = await create({ container: {}, selected: { latitude: 21, longitude: 105 }, signal: new AbortController().signal, disabled: () => disabled, onChange: point => changes.push(point) })
  const point = { lat: () => 21.123456789, lng: () => 105.123456789 }
  maps[0].listeners.click({ latLng: point }); assert.equal(changes.length, 0)
  disabled = false; maps[0].listeners.click({ latLng: point }); assert.deepEqual(plain(changes[0]), { latitude: 21.1234568, longitude: 105.1234568 })
  map.sync(null, true); assert.equal(pins[0].map, null); assert.equal(pins[0].draggable, false)
  map.focus({ latitude: 22, longitude: 106 }); assert.equal(maps[0].zoom, 17)
  map.destroy(); assert.deepEqual(removed.sort(), ['click', 'dragend', 'map-events', 'observer'])
  const aborted = new AbortController(); aborted.abort()
  await assert.rejects(create({ container: {}, signal: aborted.signal }), { name: 'AbortError' }); assert.equal(maps.length, 1)
})

test('Leaflet fallback cleans its map and updates marker drag permissions without a Google key', async () => {
  const changes = [], removed = [], markers = []
  let disabled = false
  const map = { listeners: {}, setView() { return this }, panTo() {}, on(name, cb) { this.listeners[name] = cb }, invalidateSize() {}, off() { removed.push('listeners') }, remove() { removed.push('map') } }
  const leaflet = { map: () => map, tileLayer: () => ({ addTo() {} }), divIcon: () => ({}), marker(position, options) {
    const pin = { position, draggable: options.draggable, dragging: { enable() { pin.draggable = true }, disable() { pin.draggable = false } }, addTo() { return pin }, on() { return pin }, setLatLng(value) { this.position = value }, getLatLng() { return this.position }, off() {}, remove() { removed.push('pin') } }
    markers.push(pin); return pin
  } }
  const load = moduleLoader({ ResizeObserver: class { observe() {} disconnect() { removed.push('observer') } } }, { 'leaflet/dist/leaflet.css': {}, leaflet: { default: leaflet } })
  const create = load('features/facility/leaflet-location-map.ts').createLeafletLocationMap
  const instance = await create({ container: {}, selected: null, signal: new AbortController().signal, disabled: () => disabled, onChange: point => changes.push(point) })
  map.listeners.click({ latlng: { lat: 21, lng: 105 } }); assert.equal(changes.length, 1)
  disabled = true; instance.sync({ latitude: 21, longitude: 105 }, true); assert.equal(markers[0].draggable, false)
  map.listeners.click({ latlng: { lat: 22, lng: 106 } }); assert.equal(changes.length, 1)
  instance.sync(null, true); assert.ok(removed.includes('pin')); instance.destroy(); assert.ok(removed.includes('listeners')); assert.ok(removed.includes('map')); assert.ok(removed.includes('observer'))
})

test('late map initialization and URL resolution cannot update an unmounted/locked picker', async () => {
  const driver = hookDriver(), map = deferred(), calls = [], disposed = []
  const load = moduleLoader({ process: { env: {} } }, { react: driver.react, './leaflet-location-map': { createLeafletLocationMap: options => { calls.push(options); return map.promise } } })
  const hook = load('features/facility/use-location-map.ts').useLocationMap
  driver.render(() => hook(null, null, false, () => {})).container.current = {}
  // Replay mount with a fresh driver so the element is present during the initialization effect.
  driver.unmount()
  const actual = hookDriver()
  const react = { ...actual.react, useRef(initial) { return actual.react.useRef(initial === null ? {} : initial) } }
  const withElement = moduleLoader({ process: { env: {} } }, { react, './leaflet-location-map': { createLeafletLocationMap: options => { calls.push(options); return map.promise } } })('features/facility/use-location-map.ts').useLocationMap
  actual.render(() => withElement(null, null, false, () => {})); await flush(); actual.unmount()
  assert.equal(calls.at(-1).signal.aborted, true)
  map.resolve({ destroy: () => disposed.push('map') }); await flush(); assert.deepEqual(disposed, ['map'])

  const urlDriver = hookDriver(), response = deferred(), selected = []
  const urlHook = moduleLoader({ fetch: () => response.promise }, { react: urlDriver.react })('features/facility/use-location-url.ts').useLocationUrl
  const render = disabled => urlDriver.render(() => urlHook(disabled, point => selected.push(point)))
  render(false).setMapUrlInput('https://maps.app.goo.gl/synthetic')
  const pending = render(false).handleApplyUrl(); render(true)
  response.resolve(Response.json({ latitude: 0, longitude: 0 })); await pending
  assert.deepEqual(selected, []); urlDriver.unmount()
})
