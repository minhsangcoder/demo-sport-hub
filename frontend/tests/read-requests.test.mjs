import assert from 'node:assert/strict'
import test from 'node:test'
import { moduleLoader, deferred, flush, hookDriver } from './support/load-module.mjs'

function fixture() {
  const calls = []
  const fetch = (url, options) => {
    const reply = deferred()
    calls.push({ url, options, reply })
    return reply.promise
  }
  const driver = hookDriver()
  const load = moduleLoader({ fetch }, { react: driver.react })
  return {
    load,
    calls,
    driver,
    state: load('lib/read-state.ts'),
    requests: load('lib/read-requests.ts').readRequests,
  }
}
const respond = (call, data, status = 200) =>
  call.reply.resolve(Response.json({ data, message: 'Dữ liệu thử' }, { status }))

test('public discovery reads survive session restoration without duplicating or caching authority reads', async () => {
  const f = fixture(),
    id = '11111111-1111-1111-1111-111111111111'
  for (const path of [
    '/bookings/search?date=2026-10-27',
    '/bookings/availability?courtId=' + id,
    '/facilities/' + id,
    '/facilities/' + id + '/courts',
    '/facilities/' + id + '/refund-policy',
  ]) {
    const initial = f.state.readVersion(path),
      first = f.requests.acquire(path)
    f.state.setReadIdentity('restored-' + path)
    assert.equal(f.state.readVersion(path), initial)
    const joined = f.requests.acquire(path)
    assert.equal(joined.promise, first.promise)
    await flush()
    respond(f.calls.at(-1), { public: true })
    await joined.promise
    first.release()
    joined.release()
    await flush()
    assert.equal(
      f.state.catalogTtl(path),
      0,
      'Only in-flight sharing; fresh on the next independent visit',
    )
  }
  for (const path of [
    '/bookings/admission',
    '/bookings/mine',
    '/payments/bank-profile',
    '/payments/banks',
    '/facilities/favorites',
    '/facilities/' + id + '/operations/access?permission=BOOKING_READ',
    '/owner/facilities/' + id,
    '/transfers/acquisitions/' + id + '/peer',
  ]) {
    const before = f.state.readVersion(path)
    f.state.setReadIdentity('other-' + path)
    assert.notEqual(f.state.readVersion(path), before, path)
  }
})

test('bank/runtime metadata reuse is bounded by TTL and session; permission and profile reads stay fresh', async () => {
  const load = moduleLoader(),
    state = load('lib/read-state.ts')
  let now = 0
  const calls = []
  const requests = load('lib/read-requests.ts').createReadRequests(
    async (path) => {
      calls.push(path)
      return { path }
    },
    () => now,
  )
  async function read(path) {
    const lease = requests.acquire(path)
    await lease.promise
    lease.release()
    await flush()
  }
  state.setReadIdentity('A')
  for (const path of ['/payments/banks', '/payments/runtime']) {
    await read(path)
    await read(path)
    assert.equal(calls.filter((value) => value === path).length, 1)
  }
  now = 5_001
  await read('/payments/runtime')
  assert.equal(calls.filter((value) => value === '/payments/runtime').length, 2)
  state.setReadIdentity('B')
  await read('/payments/banks')
  assert.equal(calls.filter((value) => value === '/payments/banks').length, 2)
  state.invalidateReads('/payments/banks')
  await read('/payments/banks')
  assert.equal(calls.filter((value) => value === '/payments/banks').length, 3)
  now = 65_002
  await read('/payments/banks')
  assert.equal(calls.filter((value) => value === '/payments/banks').length, 4)
  for (const path of ['/payments/bank-profile', '/bookings/admission']) {
    await read(path)
    await read(path)
    assert.equal(calls.filter((value) => value === path).length, 2)
  }
})

test('reading a POST quote does not invalidate active GETs; a real hold still does', async () => {
  const load = moduleLoader({ fetch: async () => Response.json({ data: { amount: 150000 } }) })
  const { api } = load('lib/api.ts'),
    reads = load('lib/read-state.ts')
  const before = reads.readVersion('/bookings/mine')
  await api('/schedules/public/quote', { method: 'POST', body: '{}' })
  await api('/bookings/quote', { method: 'POST', body: '{}' })
  assert.equal(reads.readVersion('/bookings/mine'), before)
  await api('/booking-holds', { method: 'POST', body: '{}' })
  assert.notEqual(reads.readVersion('/bookings/mine'), before)
})

test('late profile notifications cannot restore A after B logs in, while a current role change invalidates private reads', async () => {
  const driver = hookDriver(),
    listeners = new Map()
  class Notice {
    constructor(type, { detail }) {
      this.type = type
      this.detail = detail
    }
  }
  const window = {
    addEventListener(type, action) {
      const values = listeners.get(type) ?? new Set()
      values.add(action)
      listeners.set(type, values)
    },
    removeEventListener(type, action) {
      listeners.get(type)?.delete(action)
    },
    dispatchEvent(event) {
      for (const action of listeners.get(event.type) ?? []) action(event)
    },
  }
  const a = { id: 'A', fullName: 'Khách A', roles: ['CUSTOMER'] },
    b = { id: 'B', fullName: 'Khách B', roles: ['STAFF'] }
  const session = (user) => ({ accessToken: 'synthetic', user })
  const load = moduleLoader(
    {
      window,
      CustomEvent: Notice,
      fetch: async (url) =>
        url.endsWith('/logout')
          ? new Response(null, { status: 204 })
          : Response.json({ data: session(url.endsWith('/refresh') ? a : b) }),
    },
    {
      react: { ...driver.react, createContext: () => ({ Provider: 'provider' }) },
      'react/jsx-runtime': { jsx: (type, props) => ({ type, props }) },
    },
  )
  const Provider = load('lib/auth-context.tsx').AuthProvider
  const render = () => driver.render(Provider, { children: null }).props.value
  render()
  await flush()
  assert.equal(render().user.id, 'A')
  const client = load('lib/api.ts')
  await client.authApi.login('B', 'synthetic')
  assert.equal(render().user.id, 'B')
  window.dispatchEvent(new Notice('sporthub-session', { detail: a }))
  assert.equal(render().user.id, 'B')
  const reads = load('lib/read-state.ts'),
    before = reads.readVersion('/bookings/mine')
  window.dispatchEvent(new Notice('sporthub-session', { detail: { ...b, roles: ['CUSTOMER'] } }))
  assert.notEqual(reads.readVersion('/bookings/mine'), before)
  assert.deepEqual(render().user.roles, ['CUSTOMER'])
  await client.authApi.logout()
  assert.equal(render().user, null)
  driver.unmount()
  assert.equal(listeners.get('sporthub-session').size, 0)
})

test('shared GET keeps remaining consumers, aborts only the final release and can replace an aborted flight', async () => {
  const f = fixture(),
    a = f.requests.acquire('/bookings/mine'),
    b = f.requests.acquire('/bookings/mine')
  await flush()
  assert.equal(f.calls.length, 1)
  a.release()
  await flush()
  assert.equal(f.calls[0].options.signal.aborted, false)
  respond(f.calls[0], ['B'])
  assert.deepEqual(await b.promise, ['B'])
  b.release()
  const c = f.requests.acquire('/bookings/mine')
  c.promise.catch(() => {})
  await flush()
  c.release()
  await flush()
  assert.equal(f.calls[1].options.signal.aborted, true)
  const d = f.requests.acquire('/bookings/mine')
  await flush()
  respond(f.calls[1], ['obsolete'])
  await assert.rejects(c.promise, { name: 'AbortError' })
  const e = f.requests.acquire('/bookings/mine')
  await flush()
  assert.equal(f.calls.length, 3)
  respond(f.calls[2], ['new'])
  assert.deepEqual(await d.promise, ['new'])
  await e.promise
  d.release()
  e.release()
})

test('reload and session generations reject older GETs and include the entire query in their keys', async () => {
  const f = fixture(),
    a = f.requests.acquire('/bookings?facility=A&page=0')
  a.promise.catch(() => {})
  await flush()
  f.state.invalidateReads('/bookings?facility=A&page=0')
  const b = f.requests.acquire('/bookings?facility=A&page=0'),
    c = f.requests.acquire('/bookings?facility=B&page=0')
  await flush()
  assert.equal(f.calls.length, 3)
  respond(f.calls[1], 'new A')
  respond(f.calls[2], 'B')
  respond(f.calls[0], 'old A')
  assert.equal(await b.promise, 'new A')
  assert.equal(await c.promise, 'B')
  await assert.rejects(a.promise, { name: 'AbortError' })
  const before = f.state.readVersion('/bookings/mine')
  f.state.setReadIdentity('customer B')
  assert.notEqual(f.state.readVersion('/bookings/mine'), before)
  a.release()
  b.release()
  c.release()
})

test('only the explicit public catalog has a bounded successful TTL; errors and money are never cached', async () => {
  const f = fixture()
  let clock = 0,
    count = 0,
    fail = false
  const store = f.load('lib/read-requests.ts').createReadRequests(
    async () => {
      count++
      if (fail) throw Error('failed')
      return count
    },
    () => clock,
  )
  const a = store.acquire('/sport-categories')
  assert.equal(await a.promise, 1)
  a.release()
  const b = store.acquire('/sport-categories')
  assert.equal(await b.promise, 1)
  b.release()
  f.state.setReadIdentity('another account')
  const c = store.acquire('/sport-categories')
  assert.equal(await c.promise, 1)
  c.release()
  clock = 60001
  const d = store.acquire('/sport-categories')
  assert.equal(await d.promise, 2)
  d.release()
  for (const path of [
    '/bookings/availability?x=1',
    '/payments/1',
    '/users/me/access',
    '/transfers/quote',
    '/bookings/reports/facility/1/dashboard',
  ]) {
    assert.equal(f.state.catalogTtl(path), 0)
    const x = store.acquire(path)
    await x.promise
    x.release()
    const y = store.acquire(path)
    await y.promise
    y.release()
  }
  f.state.invalidateReads()
  fail = true
  const error = store.acquire('/sport-categories')
  await assert.rejects(error.promise)
  error.release()
  fail = false
  const ok = store.acquire('/sport-categories')
  await ok.promise
  ok.release()
  assert.equal(count, 14)
})

test('the real useApi clears private results synchronously on account/role changes and ignores out-of-order URLs', async () => {
  const f = fixture(),
    hook = f.load('lib/use-api.ts').useApi
  f.state.setReadIdentity('A:OWNER')
  f.driver.render(hook, '/bookings/mine')
  await flush()
  respond(f.calls[0], ['A'])
  await flush()
  assert.deepEqual(f.driver.render(hook, '/bookings/mine').data, ['A'])
  f.state.setReadIdentity('')
  f.state.setReadIdentity('B:STAFF')
  const cleared = f.driver.render(hook, '/bookings/mine')
  assert.equal(cleared.data, null)
  assert.equal(cleared.loading, true)
  await flush()
  f.driver.render(hook, '/bookings?facility=B')
  await flush()
  respond(f.calls[2], ['B facility'])
  await flush()
  respond(f.calls[1], ['B personal old URL'])
  await flush()
  assert.deepEqual(f.driver.render(hook, '/bookings?facility=B').data, ['B facility'])
  const current = f.driver.render(hook, '/bookings?facility=B')
  current.reload()
  assert.deepEqual(f.driver.render(hook, '/bookings?facility=B').data, ['B facility'])
  assert.equal(f.driver.render(hook, '/bookings?facility=B').loading, true)
  await flush()
  respond(f.calls[3], null, 403)
  await flush()
  const denied = f.driver.render(hook, '/bookings?facility=B')
  assert.equal(denied.data, null)
  assert.ok(denied.error)
  f.driver.unmount()
})

test('a mutation invalidation and its explicit reload share one new GET without restarting it', async () => {
  const f = fixture(),
    hook = f.load('lib/use-api.ts').useApi,
    path = '/bookings/one/operations'
  f.driver.render(hook, path)
  await flush()
  respond(f.calls[0], { status: 'CONFIRMED' })
  await flush()
  f.state.invalidateReads()
  f.driver.render(hook, path)
  await flush()
  const refreshed = f.driver.render(hook, path).reload()
  f.driver.render(hook, path)
  await flush()
  assert.equal(
    f.calls.length,
    2,
    'Do not send another GET after the mutation already started a fresh generation',
  )
  assert.equal(f.calls[1].options.signal.aborted, false)
  respond(f.calls[1], { status: 'CHECKED_IN' })
  await refreshed
  await flush()
  assert.equal(f.driver.render(hook, path).data.status, 'CHECKED_IN')
  f.driver.unmount()
})

test('an explicit reload fences the initial pending GET; repeated reloads join and unmount stops new work', async () => {
  const f = fixture(),
    hook = f.load('lib/use-api.ts').useApi,
    path = '/bookings/mine'
  f.driver.render(hook, path)
  await flush()
  const first = f.driver.render(hook, path).reload()
  f.driver.render(hook, path)
  await flush()
  const second = f.driver.render(hook, path).reload()
  f.driver.render(hook, path)
  await flush()
  assert.equal(f.calls.length, 2)
  respond(f.calls[0], ['old'])
  respond(f.calls[1], ['fresh'])
  await Promise.all([first, second])
  await flush()
  const model = f.driver.render(hook, path)
  assert.deepEqual(model.data, ['fresh'])
  f.driver.unmount()
  await model.reload()
  await flush()
  assert.equal(f.calls.length, 2)
})

test('polling joins active reads, cancels only its lease and never publishes a hidden response', async () => {
  const f = fixture(),
    hook = f.load('lib/use-api.ts').useApi,
    path = '/transfers/acquisitions/one/peer'
  f.driver.render(hook, path)
  await flush()
  const joined = new AbortController(),
    firstPoll = f.driver.render(hook, path).poll(joined.signal)
  await flush()
  joined.abort()
  await flush()
  assert.equal(f.calls.length, 1)
  assert.equal(
    f.calls[0].options.signal.aborted,
    false,
    'Initial visible read still owns its lease',
  )
  respond(f.calls[0], { phase: 'WAITING_PAYMENT' })
  await firstPoll
  await flush()
  const version = f.state.readVersion(path),
    hidden = new AbortController()
  const cancelled = f.driver.render(hook, path).poll(hidden.signal)
  await flush()
  hidden.abort()
  await flush()
  assert.equal(f.calls[1].options.signal.aborted, true)
  respond(f.calls[1], { phase: 'COMPLETED' })
  await cancelled
  await flush()
  assert.equal(f.driver.render(hook, path).data.phase, 'WAITING_PAYMENT')
  assert.equal(f.state.readVersion(path), version, 'Poll does not invalidate the subscription')
  const visible = new AbortController(),
    resumed = f.driver.render(hook, path).poll(visible.signal)
  await flush()
  respond(f.calls[2], { phase: 'COMPLETED' })
  await resumed
  await flush()
  assert.equal(f.driver.render(hook, path).data.phase, 'COMPLETED')
  f.driver.unmount()
})

test('transport preserves AbortError, keeps a shared refresh alive, and does not replay a mutation after 401', async () => {
  const f = fixture(),
    { api } = f.load('lib/api.ts'),
    controller = new AbortController()
  const request = api('/bookings/mine', { signal: controller.signal })
  controller.abort()
  f.calls[0].reply.reject(controller.signal.reason)
  await assert.rejects(request, { name: 'AbortError' })
  const first = api('/bookings/one', { signal: new AbortController().signal })
  respond(f.calls[1], null, 401)
  await flush()
  const otherController = new AbortController(),
    second = api('/bookings/two', { signal: otherController.signal })
  second.catch(() => {})
  respond(f.calls[3], null, 401)
  await flush()
  otherController.abort()
  respond(f.calls[2], { accessToken: 'fixture', user: { id: 'A', roles: ['CUSTOMER'] } })
  await flush()
  respond(f.calls[4], ['one'])
  await first
  await assert.rejects(second, { name: 'AbortError' })
  const mutation = api('/payments', { method: 'POST', body: '{}' })
  respond(f.calls[5], null, 401)
  await flush()
  respond(f.calls[6], { accessToken: 'fixture2', user: { id: 'A', roles: ['CUSTOMER'] } })
  await assert.rejects(mutation, (error) => error.status === 401)
  assert.equal(f.calls.filter((call) => call.url === '/api/v1/payments').length, 1)
})

test('a delayed refresh or logout cannot restore A after B signs in', async () => {
  const f = fixture(),
    client = f.load('lib/api.ts')
  const refresh = client.refreshSession()
  refresh.catch(() => {})
  const login = client.authApi.login('B', 'synthetic')
  respond(f.calls[1], { accessToken: 'B', user: { id: 'B', roles: ['STAFF'] } })
  await login
  const bVersion = f.state.readVersion('/bookings/mine')
  respond(f.calls[0], { accessToken: 'A', user: { id: 'A', roles: ['OWNER'] } })
  await assert.rejects(refresh, { name: 'AbortError' })
  assert.equal(f.state.readVersion('/bookings/mine'), bVersion)
  const logout = client.authApi.logout()
  const again = client.authApi.login('B', 'synthetic')
  respond(f.calls[3], { accessToken: 'B2', user: { id: 'B', roles: ['STAFF'] } })
  await again
  const finalVersion = f.state.readVersion('/bookings/mine')
  respond(f.calls[2], null)
  await logout
  assert.equal(f.state.readVersion('/bookings/mine'), finalVersion)
})

test('a committed mutation invalidates an earlier GET and preserves the mutation key', async () => {
  const f = fixture(),
    old = f.requests.acquire('/payments/mine')
  old.promise.catch(() => {})
  await flush()
  const write = f.load('lib/api.ts').api('/payments/one/demo/complete', {
    method: 'POST',
    headers: { 'Idempotency-Key': 'one-attempt' },
    body: '{}',
  })
  respond(f.calls[1], { status: 'SUCCESS' })
  await write
  const fresh = f.requests.acquire('/payments/mine')
  await flush()
  respond(f.calls[2], ['paid'])
  respond(f.calls[0], ['pending'])
  assert.deepEqual(await fresh.promise, ['paid'])
  await assert.rejects(old.promise, { name: 'AbortError' })
  assert.equal(f.calls[1].options.headers.get('Idempotency-Key'), 'one-attempt')
  old.release()
  fresh.release()
})
