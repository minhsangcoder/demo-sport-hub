import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader, hookDriver, deferred, flush } from './support/load-module.mjs'

function nodes(tree) {
  return !tree
    ? []
    : Array.isArray(tree)
      ? tree.flatMap(nodes)
      : typeof tree === 'object'
        ? [tree, ...nodes(tree.props?.children)]
        : []
}
function fixture() {
  const driver = hookDriver(),
    paths = []
  const history = Array.from({ length: 1000 }, (_, i) => ({
    id: String(i + 1),
    cursor: i + 1,
    senderId: 'A',
    text: `Tin ${i + 1}`,
    createdAt: '2026-10-27T00:00:00Z',
  }))
  let poll, pending, lastSignal, inFlight
  const jsx = (type, props) => ({ type, props })
  const load = moduleLoader(
    {},
    {
      react: driver.react,
      'react/jsx-runtime': { jsx, jsxs: jsx },
      '@/lib/api': {
        ApiError: class extends Error {},
        api: (path, { signal }) => {
          paths.push(path)
          lastSignal = signal
          const url = new URL(path, 'http://fixture.invalid')
          const after = url.searchParams.get('after'),
            before = url.searchParams.get('before')
          const size = Number(url.searchParams.get('size'))
          const rows =
            after !== null
              ? history.filter((r) => r.cursor > Number(after)).slice(0, size)
              : history.filter((r) => before === null || r.cursor < Number(before)).slice(-size)
          inFlight = pending ? pending.promise : Promise.resolve(rows)
          return inFlight
        },
      },
      '@/lib/use-polling': {
        usePolling: (callback) => {
          poll = callback
        },
      },
      '@/components/ui/button': { Button: 'button' },
      './use-peer-command': {
        usePeerCommand: () => ({ busy: false, blocked: false, run: async () => {} }),
      },
    },
  )
  const { PeerChat } = load('features/transfers/peer-chat.tsx')
  const render = () => driver.render(() => PeerChat({ id: 'room', userId: 'B' }))
  return {
    driver,
    paths,
    history,
    render,
    poll: () => poll(new AbortController().signal),
    older: async () => {
      nodes(render())
        .find((n) => n.type === 'button' && n.props.children === 'Tải tin nhắn cũ hơn')
        .props.onClick()
      await inFlight
      await flush()
    },
    defer: () => {
      pending = deferred()
      return pending
    },
    signal: () => lastSignal,
  }
}

test('chat opens at the latest 50 and only downloads older history on request', async () => {
  const f = fixture()
  f.render()
  await f.poll()
  assert.deepEqual(f.paths, ['/transfers/conversations/room/messages?size=50'])
  assert.equal(nodes(f.render()).filter((n) => n.type === 'li').length, 50)
  await f.poll()
  assert.match(f.paths.at(-1), /after=1000&size=50$/)
  await f.older()
  assert.match(f.paths.at(-1), /before=951&size=50$/)
  assert.equal(nodes(f.render()).filter((n) => n.type === 'li').length, 100)
  f.history.push({ ...f.history.at(-1), id: '1001', cursor: 1001, text: 'Tin mới' })
  f.render()
  await f.poll()
  assert.match(f.paths.at(-1), /after=1000&size=50$/)
  assert.equal(nodes(f.render()).filter((n) => n.type === 'li').length, 101)
  f.driver.unmount()
})

test('manual history read aborts on unmount and does not run alongside polling', async () => {
  const f = fixture()
  f.render()
  await f.poll()
  const pending = f.defer()
  const older = f.older()
  const count = f.paths.length
  await f.poll()
  assert.equal(f.paths.length, count)
  const signal = f.signal()
  f.driver.unmount()
  assert.equal(signal.aborted, true)
  pending.resolve([])
  await older
})
