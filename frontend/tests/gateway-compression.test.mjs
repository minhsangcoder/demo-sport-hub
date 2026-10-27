import assert from 'node:assert/strict'
import test from 'node:test'
import { gunzipSync } from 'node:zlib'
import { moduleLoader } from './support/load-module.mjs'

const context = { params: Promise.resolve({ segments: ['bookings', 'booking'] }) }
const request = (encoding, method = 'GET') =>
  Object.assign(
    new Request('http://localhost:3000/api/v1/bookings/booking', {
      method,
      headers: encoding ? { 'Accept-Encoding': encoding } : {},
    }),
    { nextUrl: new URL('http://localhost:3000/api/v1/bookings/booking') },
  )
const payload = JSON.stringify({
  data: {
    amount: '120000',
    checkinToken: 'unchanged-private-token',
    checkinQrSvg: `<svg><path d="${'M24 24h1v1h-1z'.repeat(30000)}"/></svg>`,
  },
})
const load = (response) =>
  moduleLoader({ CompressionStream, process: { env: {} }, fetch: async () => response() })(
    'app/api/v1/[...segments]/route.ts',
  )

test('large private JSON is streamed as gzip without changing QR, money, cookies or privacy headers', async () => {
  const headers = new Headers({
    'Content-Type': 'application/json;charset=UTF-8',
    'Content-Length': String(Buffer.byteLength(payload)),
    ETag: 'old-representation',
    'Content-Digest': 'old-digest',
    Vary: 'Authorization',
    'X-Request-Id': 'trace',
  })
  headers.append('Set-Cookie', 'first=opaque; HttpOnly; SameSite=Lax')
  headers.append('Set-Cookie', 'second=opaque; HttpOnly; SameSite=Lax')
  const proxy = load(() => new Response(payload, { headers }))
  const response = await proxy.GET(request('br, gzip;q=0.8, deflate'), context)
  const bytes = Buffer.from(await response.arrayBuffer())
  assert.equal(response.status, 200)
  assert.equal(gunzipSync(bytes).toString(), payload)
  assert.ok(bytes.length < Buffer.byteLength(payload) / 10)
  assert.equal(response.headers.get('Content-Encoding'), 'gzip')
  assert.equal(response.headers.get('Cache-Control'), 'no-store')
  assert.match(response.headers.get('Vary'), /Authorization/)
  assert.match(response.headers.get('Vary'), /Accept-Encoding/)
  assert.equal(response.headers.getSetCookie().length, 2)
  assert.equal(response.headers.get('X-Request-Id'), 'trace')
  for (const name of ['Content-Length', 'ETag', 'Content-Digest'])
    assert.equal(response.headers.has(name), false)
})

test('clients that do not accept gzip receive the exact original JSON', async () => {
  for (const encoding of [null, 'br', 'gzip;q=0', 'gzip;q=0.000', 'gzip;q=invalid', 'gzip;q=2']) {
    const proxy = load(
      () => new Response(payload, { headers: { 'Content-Type': 'application/json' } }),
    )
    const response = await proxy.GET(request(encoding), context)
    assert.equal(response.headers.has('Content-Encoding'), false)
    assert.equal(await response.text(), payload)
  }
})

test('binary media, redirects, auth, writes, errors and empty responses keep their original transport', async () => {
  const cases = [
    { method: 'GET', status: 200, type: 'image/png', body: 'private-image' },
    { method: 'GET', status: 302, type: 'application/json', body: 'redirect' },
    { method: 'GET', status: 206, type: 'application/json', body: 'partial' },
    { method: 'GET', status: 403, type: 'application/json', body: 'forbidden' },
    {
      method: 'GET',
      status: 200,
      type: 'application/json',
      body: 'auth',
      segments: ['auth', 'refresh'],
    },
    { method: 'POST', status: 200, type: 'application/json', body: 'written' },
    { method: 'HEAD', status: 200, type: 'application/json', body: null },
    { method: 'GET', status: 204, type: 'application/json', body: null },
  ]
  for (const item of cases) {
    const proxy = load(
      () =>
        new Response(item.body, {
          status: item.status,
          headers: { 'Content-Type': item.type, Location: '/unchanged' },
        }),
    )
    const response = await proxy[item.method](
      request('gzip', item.method),
      item.segments ? { params: Promise.resolve({ segments: item.segments }) } : context,
    )
    assert.equal(response.status, item.status)
    assert.equal(response.headers.has('Content-Encoding'), false)
    assert.equal(response.headers.get('Location'), '/unchanged')
    assert.equal(await response.text(), item.body ?? '')
  }
})
