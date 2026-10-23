import assert from 'node:assert/strict'
import test from 'node:test'
import { moduleLoader, deferred, flush, hookDriver } from './support/load-module.mjs'

function camera() {
  const media = [], frames = new Map(), listeners = new Set(), states = [], scans = []
  let next = 0
  const video = { srcObject: null, play: async () => {} }
  const visibility = { hidden: false, addEventListener: (_, cb) => listeners.add(cb), removeEventListener: (_, cb) => listeners.delete(cb) }
  const environment = {
    visibility, media: { getUserMedia() { const request = deferred(); media.push(request); return request.promise } },
    requestFrame(fn) { const id = ++next; frames.set(id, fn); return id }, cancelFrame: id => frames.delete(id),
    loadDecoder: async () => ({ scanVideoFrame: () => null }),
  }
  const factory = moduleLoader()('features/groups/qr-camera-session.ts').createQrCameraSession
  return { media, frames, listeners, states, scans, video, environment,
    start: () => factory(video, {}, value => scans.push(value), state => states.push(state), environment),
    visible(value) { visibility.hidden = !value; for (const cb of listeners) cb() },
  }
}
function stream() { const track = { ended: false, stop() { this.ended = true } }; return { track, getTracks: () => [track] } }

test('a delayed camera permission grant after close releases its stream and never starts RAF', async () => {
  const c = camera(), session = c.start(), received = stream()
  session.stop(); c.media[0].resolve(received); await flush()
  assert.equal(received.track.ended, true); assert.equal(c.video.srcObject, null)
  assert.equal(c.frames.size, 0); assert.equal(c.listeners.size, 0); assert.equal(c.scans.length, 0)
})
test('camera hides/cleans/resumes, and an older acquisition cannot replace a newer camera session', async () => {
  const c = camera(), session = c.start(), old = stream(), current = stream()
  const retry = session.restart(); c.media[1].resolve(current); await retry
  c.media[0].resolve(old); await flush()
  assert.equal(old.track.ended, true); assert.equal(current.track.ended, false); assert.equal(c.video.srcObject, current)
  assert.equal(c.frames.size, 1)
  c.visible(false); assert.equal(current.track.ended, true); assert.equal(c.frames.size, 0)
  c.visible(true); const resumed = stream(); c.media[2].resolve(resumed); await flush()
  assert.equal(c.frames.size, 1); session.stop(); assert.equal(resumed.track.ended, true); assert.equal(c.listeners.size, 0)
})
test('denied camera permission stays Vietnamese, does not re-prompt, and a decoded frame completes once', async () => {
  const denied = camera(), session = denied.start()
  denied.media[0].reject(new DOMException('denied', 'NotAllowedError')); await flush()
  assert.equal(denied.media.length, 1); assert.match(denied.states.at(-1).errorMessage, /bị từ chối/); session.stop()
  const c = camera(); c.environment.loadDecoder = async () => ({ scanVideoFrame: () => 'raw-token' })
  const active = c.start(), received = stream(); c.media[0].resolve(received); await flush()
  const [id, scan] = [...c.frames][0]; c.frames.delete(id); scan()
  assert.deepEqual(c.scans, ['raw-token']); assert.equal(received.track.ended, true); assert.equal(c.frames.size, 0); active.stop()
})

function imageDecoder() {
  const images = [], revoked = []
  class FixtureUrl extends URL { static createObjectURL() { return 'blob:synthetic' } static revokeObjectURL(value) { revoked.push(value) } }
  class FixtureImage { constructor() { images.push(this); this.width = 100; this.height = 100 } }
  const load = moduleLoader({ window: {}, URL: FixtureUrl, Image: FixtureImage,
    document: { createElement: () => ({ getContext: () => ({ drawImage() {}, getImageData: () => ({ data: [], width: 1, height: 1 }) }) }) },
  }, { jsqr: { default: () => ({ data: 'decoded-token' }) } })
  return { images, revoked, decode: load('features/groups/qr-image-decoder.ts').decodeQrFromImageFile }
}
test('image decoding reports invalid images, releases object URLs and aborts an unfinished image', async () => {
  const fixture = imageDecoder(), controller = new AbortController()
  const pending = fixture.decode(new Blob(['x']), controller.signal); controller.abort()
  await assert.rejects(pending, { name: 'AbortError' }); assert.equal(fixture.revoked.length, 1)
  const valid = fixture.decode(new Blob(['x'])); fixture.images[1].onload()
  assert.equal(await valid, 'decoded-token'); assert.equal(fixture.revoked.length, 2)
  const invalid = fixture.decode(new Blob(['x'])); fixture.images[2].onerror()
  await assert.rejects(invalid, /Không thể tải/); assert.equal(fixture.revoked.length, 3)
})

const bookingId = '79000000-0000-0000-0000-000000000011'
const token = Buffer.from(`${bookingId}|79000000-0000-0000-0000-000000000012|1|1800000000`).toString('base64url') + '.synthetic-signature'
function checkin() {
  const driver = hookDriver(), decode = deferred(), response = deferred(), calls = [], revoked = []
  class FixtureUrl extends URL { static createObjectURL() { return 'blob:preview' } static revokeObjectURL(value) { revoked.push(value) } }
  const load = moduleLoader({ URL: FixtureUrl, document: { hidden: false }, window: { addEventListener() {}, removeEventListener() {} } }, {
    react: driver.react,
    '@/features/groups/qr-image-decoder': { decodeQrFromImageFile: () => decode.promise },
    '@/lib/api': { api: (url, options) => { calls.push({ url, options }); return response.promise } },
  })
  const hook = load('features/operations/use-qr-checkin.ts').useQrCheckin
  return { driver, decode, response, calls, revoked, render: () => driver.render(hook, true) }
}
test('the moved QR controller submits once, awaits server approval and does not abort a submitted mutation', async () => {
  const c = checkin(), model = c.render(), file = new File(['x'], 'qr.png', { type: 'image/png' })
  const first = model.processFile(file); await model.processFile(file)
  c.decode.resolve(token); await flush(); assert.equal(c.calls.length, 1)
  assert.equal(c.calls[0].url, `/bookings/${bookingId}/check-in`)
  assert.deepEqual(JSON.parse(c.calls[0].options.body), { token }); assert.equal(c.calls[0].options.signal, undefined)
  await c.render().executeCheckin(bookingId, token); assert.equal(c.calls.length, 1)
  assert.equal(c.render().checkinSuccess, false)
  c.response.resolve({}); await first; assert.equal(c.render().checkinSuccess, true); c.driver.unmount()
  assert.equal(c.revoked.length, 1)
})
test('closing before image decoding completes cannot submit a stale check-in', async () => {
  const c = checkin(), work = c.render().processFile(new File(['x'], 'qr.png', { type: 'image/png' }))
  c.driver.unmount(); c.decode.resolve(token); await work
  assert.equal(c.calls.length, 0); assert.equal(c.revoked.length, 1)
})
