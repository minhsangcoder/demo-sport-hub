import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { facility, id } from '../ui-data.mjs'

export async function qrLifetime(h) {
  const decoderChunks = h.chunkFiles.filter(file => { const source = h.chunkSource(file); return source.includes('inversionAttempts') && source.includes('attemptBoth') })
  assert.ok(decoderChunks.length)
  const { page, context, data } = await h.page('STAFF')
  const requests = []
  page.on('request', request => requests.push(request.url()))
  const booking = { id: id(80), facilityId: facility.id, courtId: id(11), source: 'OFFLINE_COUNTER', currentHolderId: null, customerId: null, createdBy: id(3), guestName: 'Khách vãng lai thử', guestPhone: '0900000000', bookingCode: 'ABCDEF1234', status: 'CONFIRMED', amount: 120000, currency: 'VND', startsAt: '2026-10-23T01:00:00Z', endsAt: '2026-10-23T02:00:00Z', priceSnapshot: { timezone: 'Asia/Ho_Chi_Minh', segments: [] } }
  data.bookings = [booking]
  await page.addInitScript(() => {
    const frames = new Set(), start = requestAnimationFrame.bind(window), stop = cancelAnimationFrame.bind(window)
    window.__pendingFrames = () => frames.size
    window.requestAnimationFrame = callback => { const id = start(time => { frames.delete(id); callback(time) }); frames.add(id); return id }
    window.cancelAnimationFrame = id => { frames.delete(id); stop(id) }
    window.__streams = []
    const media = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
    window.__cameraDenied = false
    navigator.mediaDevices.getUserMedia = async options => {
      if (window.__cameraDenied) throw new DOMException('test-denied', 'NotAllowedError')
      let stream
      if (window.__streams.length === 0) stream = await media(options)
      else {
        // Reopening Chrome's fake capture device is unstable on this Windows host.
        // A native canvas capture still supplies real MediaStreamTracks/RAF for resume cleanup.
        const surface = document.createElement('canvas'); surface.width = surface.height = 320
        surface.getContext('2d').fillRect(0, 0, 320, 320)
        stream = surface.captureStream(10)
        window.__resumeSurface = surface
      }
      window.__streams.push(stream); return stream
    }
  })
  await page.goto(h.base + '/staff/bookings')
  const input = page.locator('input[name=token]')
  await input.waitFor()
  assert.ok(!requests.some(url => decoderChunks.some(file => url.endsWith('/' + file))))
  let release
  data.checkinGate = new Promise(resolve => { release = resolve })
  await input.fill(' abcdef1234 ')
  const pending = page.waitForRequest(request => request.url().endsWith('/check-in-by-code'))
  await page.getByRole('button', { name: 'Xác nhận vào sân', exact: true }).click(); await pending
  assert.equal(await page.getByText('Đã xác nhận khách vào sân.', { exact: true }).count(), 0)
  assert.deepEqual(data.calls.find(call => call.path.endsWith('/check-in-by-code')).body, { bookingCode: 'ABCDEF1234' })
  release(); await page.getByText('Đã xác nhận khách vào sân.', { exact: true }).waitFor()
  h.pass('Manual operational code works without QR decoder and waits for the server CHECKED_IN result')
  await page.getByRole('button', { name: 'Tải ảnh QR xác nhận vào sân', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Xác nhận vào sân bằng mã QR' })
  await dialog.waitFor()
  await dialog.locator('input[type=file]').setInputFiles({ name: 'not-a-qr.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j4U8AAAAASUVORK5CYII=', 'base64') })
  await dialog.getByText('Không thể xác nhận vào sân', { exact: true }).waitFor()
  assert.ok(requests.some(url => decoderChunks.some(file => url.endsWith('/' + file))))
  await page.evaluate(() => { window.__cameraDenied = true })
  await dialog.getByRole('button', { name: 'Mở Camera', exact: true }).click()
  const camera = page.getByRole('dialog', { name: 'Quét mã QR bằng Camera' })
  await camera.getByText(/bị từ chối/).waitFor()
  await camera.getByRole('button', { name: 'Đóng', exact: true }).click()
  await page.evaluate(() => { window.__cameraDenied = false })
  await dialog.getByRole('button', { name: 'Mở Camera', exact: true }).click()
  await page.waitForFunction(() => window.__streams.length === 1)
  await page.waitForFunction(() => document.querySelector('video')?.videoWidth > 0)
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.waitForFunction(() => window.__streams[0].getTracks().every(track => track.readyState === 'ended'))
  await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => false }); document.dispatchEvent(new Event('visibilitychange')) })
  await page.waitForFunction(() => window.__streams.length === 2)
  await camera.getByRole('button', { name: 'Đóng', exact: true }).click()
  await page.waitForFunction(() => window.__streams.every(stream => stream.getTracks().every(track => track.readyState === 'ended')) && window.__pendingFrames() === 0)
  h.pass('Actual QR UI handles bad images/denied camera and releases native camera/canvas MediaStreams and RAF on hide/close')
  data.bookings = [booking, { ...booking, id: id(81), source: 'ONLINE', customerId: id(90), currentHolderId: id(90), createdBy: id(90), status: 'CONFIRMED' }]
  const token = Buffer.from(`${id(81)}|${id(90)}|1|4102444800`).toString('base64url') + '.synthetic'
  const file = path.join(h.directory, 'valid-qr.svg')
  execFileSync(path.join(h.root, 'tmp/fonttools-env/Scripts/python.exe'), [path.join(h.root, 'scripts/performance/create-qr-fixture.py'), token, file], { windowsHide: true })
  await dialog.locator('input[type=file]').setInputFiles(file)
  await dialog.getByRole('heading', { name: 'Xác nhận vào sân thành công!' }).waitFor()
  assert.equal(data.calls.filter(call => call.path === `/bookings/${id(81)}/check-in`).length, 1)
  await context.close()
  h.pass('The real lazy image decoder reads a deterministic QR and submits one check-in')
}
