import assert from 'node:assert/strict'
import test from 'node:test'
import { moduleLoader, deferred, flush } from './support/load-module.mjs'

function clock() {
  let now = 0, next = 0
  const timers = new Map(), listeners = new Set()
  const visibility = {
    hidden: false,
    addEventListener(type, listener) { assert.equal(type, 'visibilitychange'); listeners.add(listener) },
    removeEventListener(type, listener) { assert.equal(type, 'visibilitychange'); listeners.delete(listener) },
  }
  return {
    timers, listeners,
    environment: { visibility, now: () => now, setTimer(callback, delay) { const id = ++next; timers.set(id, { callback, at: now + delay }); return id }, clearTimer: id => timers.delete(id) },
    async advance(ms) {
      const until = now + ms
      for (;;) {
        const due = [...timers].filter(([, task]) => task.at <= until).sort((a, b) => a[1].at - b[1].at)[0]
        if (!due) break
        now = due[1].at; timers.delete(due[0]); due[1].callback(); await flush()
      }
      now = until; await flush()
    },
    visible(value) { visibility.hidden = !value; for (const listener of listeners) listener() },
  }
}
const { startPolling } = moduleLoader()('lib/polling.ts')

test('polling awaits the read, pauses while hidden and resumes once without concurrent requests', async () => {
  const c = clock(), first = deferred(); let calls = 0, active = 0, maximum = 0, signal
  const stop = startPolling(async current => {
    signal = current; calls++; maximum = Math.max(maximum, ++active)
    if (calls === 1) await first.promise
    active--
  }, { interval: 1000 }, c.environment)
  await c.advance(1000); assert.equal(calls, 1)
  await c.advance(10000); assert.equal(calls, 1)
  c.visible(false); assert.equal(signal.aborted, true)
  await c.advance(20000); assert.equal(calls, 1)
  c.visible(true); c.visible(true); assert.equal(calls, 1)
  first.resolve(); await flush(); assert.equal(calls, 2); assert.equal(maximum, 1)
  stop(); assert.equal(c.timers.size, 0); assert.equal(c.listeners.size, 0)
  await c.advance(60000); assert.equal(calls, 2)
})

test('payment waiting has an absolute deadline while hidden and does not gain permission from a timer', async () => {
  const c = clock(); let calls = 0, timeouts = 0, granted = false
  startPolling(async () => { calls++; return undefined }, { interval: 1000, maxAttempts: 15, deadline: 15000, onTimeout() { timeouts++ } }, c.environment)
  await c.advance(2000); assert.equal(calls, 2)
  c.visible(false); await c.advance(18000); c.visible(true)
  assert.equal(timeouts, 1); assert.equal(calls, 2); assert.equal(granted, false)
  assert.equal(c.timers.size, 0); assert.equal(c.listeners.size, 0)
})

test('successful server confirmation stops the loop, attempts are bounded and unmount leaves no orphan', async () => {
  const c = clock(); let calls = 0, timeouts = 0
  const stop = startPolling(async () => { calls++; return false }, { interval: 1000, onTimeout() { timeouts++ } }, c.environment)
  await c.advance(30000); assert.equal(calls, 1); assert.equal(timeouts, 0); assert.equal(c.timers.size, 0); stop()
  startPolling(async () => { calls++ }, { interval: 1000, maxAttempts: 3, onTimeout() { timeouts++ } }, c.environment)
  await c.advance(10000); assert.equal(calls, 4); assert.equal(timeouts, 1); assert.equal(c.listeners.size, 0)
})
