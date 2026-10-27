import assert from 'node:assert/strict'
import test from 'node:test'
import { moduleLoader, hookDriver, deferred, flush } from './support/load-module.mjs'

function selection(from, to) {
  return [{ startsAt: `2026-10-26T${from}:00Z`, endsAt: `2026-10-26T${to}:00Z`, amount: 51 }]
}

test('selection uses one continuous 60–240 minute interval rather than adding rounded cells', () => {
  const { selectionInterval } = moduleLoader(
    {},
    { react: hookDriver().react },
  )('features/discovery/use-interval-quote.ts')
  assert.ok(selectionInterval('court', selection('15:30', '16:00')).error)
  assert.ok(selectionInterval('court', selection('15:30', '20:00')).error)
  const slots = [...selection('15:30', '16:00'), ...selection('16:00', '16:30')]
  assert.equal(selectionInterval('court', slots).interval.endsAt, '2026-10-26T16:30:00Z')
  assert.ok(
    selectionInterval('court', [...selection('15:30', '16:00'), ...selection('16:30', '17:00')])
      .error,
  )
})

test('debounced quotes ignore older replies, abort on selection/unmount and display the server total', async () => {
  const driver = hookDriver(),
    timers = new Map(),
    calls = []
  let timerId = 0
  const load = moduleLoader(
    {
      setTimeout(fn) {
        const id = ++timerId
        timers.set(id, fn)
        return id
      },
      clearTimeout(id) {
        timers.delete(id)
      },
      fetch(url, options) {
        const reply = deferred()
        calls.push({ url, options, reply })
        return reply.promise
      },
    },
    { react: driver.react },
  )
  const { useIntervalQuote } = load('features/discovery/use-interval-quote.ts')
  const render = (slots) => driver.render((value) => useIntervalQuote('court', value), slots)
  const run = () => {
    for (const [id, fn] of timers) {
      timers.delete(id)
      fn()
    }
  }
  const first = selection('15:00', '16:00'),
    second = selection('15:30', '16:30')
  assert.equal(render(first).loading, true)
  render(second)
  run()
  await flush()
  assert.equal(calls.length, 1)
  assert.equal(JSON.parse(calls[0].options.body).startsAt, second[0].startsAt)
  render(first)
  assert.equal(calls[0].options.signal.aborted, true)
  run()
  await flush()
  calls[1].reply.resolve(Response.json({ data: { amount: 101 } }))
  await flush()
  assert.equal(render(first).quote.amount, 101)
  calls[0].reply.resolve(Response.json({ data: { amount: 102 } }))
  await flush()
  assert.equal(render(first).quote.amount, 101)
  render(second)
  run()
  await flush()
  driver.unmount()
  assert.equal(calls[2].options.signal.aborted, true)
  calls[2].reply.resolve(Response.json({ data: { amount: 200 } }))
  await flush()
})
