import test from 'node:test'
import assert from 'node:assert/strict'
import { moduleLoader } from './support/load-module.mjs'
const jsx = (type, props, key) => ({ type, props, key })
const status = {
  policyVersion: 1,
  maxConcurrent: 1,
  policyAvailability: 'COMPLETE',
  holdBlockedUntil: null,
  onlineBlockedUntil: null,
  marketBlockedUntil: null,
  marketPermanent: false,
  strikeCount: 0,
  resources: [],
}
let user = { id: 'A', roles: ['CUSTOMER'] },
  data = status
const load = moduleLoader(
  {},
  {
    'react/jsx-runtime': { jsx, jsxs: jsx },
    'next/link': { default: 'a' },
    '@/lib/auth-context': { useAuth: () => ({ user }) },
    '@/lib/use-api': { useApi: () => ({ data, loading: false, error: '', reload() {} }) },
  },
)
const { admissionMessages, AdmissionNotice, AdmissionErrorContext } = load(
  'features/booking/admission-notice.tsx',
)
const text = (tree) =>
  !tree
    ? ''
    : Array.isArray(tree)
      ? tree.map(text).join(' ')
      : typeof tree === 'object'
        ? text(tree.props?.children)
        : String(tree)
const nodes = (tree) =>
  !tree
    ? []
    : Array.isArray(tree)
      ? tree.flatMap(nodes)
      : typeof tree === 'object'
        ? [tree, ...nodes(tree.props?.children)]
        : []
test('admission explains server retry times, market-only bans and failure coverage without blocking paid use', () => {
  const now = Date.parse('2026-10-25T00:00:00Z'),
    until = '2026-10-25T00:30:00Z'
  assert.match(
    admissionMessages({ ...status, holdBlockedUntil: until }, false, now).join(),
    /ba lượt liên tiếp/,
  )
  assert.match(
    admissionMessages(
      { ...status, holdBlockedUntil: until, onlineBlockedUntil: until },
      false,
      now,
    ).join(),
    /năm lượt/,
  )
  assert.equal(admissionMessages({ ...status, holdBlockedUntil: until }, false, Date.parse(until)).length,0)
  assert.match(
    admissionMessages({ ...status, marketBlockedUntil: until }, true, now).join(),
    /30 ngày/,
  )
  const permanent = admissionMessages({ ...status, marketPermanent: true }, true, now).join()
  assert.match(permanent, /vĩnh viễn/)
  assert.match(permanent, /Lịch đã thanh toán và hồ sơ cũ vẫn/)
  assert.equal(admissionMessages({ ...status, marketPermanent: true }, false, now).length,0)
  assert.match(
    admissionMessages({ ...status, policyAvailability: 'UNAVAILABLE' }, true, now).join(),
    /lịch đã trả tiền/,
  )
  assert.match(
    text(AdmissionErrorContext({ failure: { details: { retryAt: until } } })),
    /Có thể thử lại sau/,
  )
  assert.equal(AdmissionErrorContext({ failure: { details: { retryAt: 'invalid' } } }), null)
})
test('active claims link to their existing scoped resource and the notice resets with the account', () => {
  data = {
    ...status,
    resources: [
      { kind: 'ONLINE_BOOKING', resourceId: 'hold', expiresAt: null },
      { kind: 'PEER_ACQUISITION', resourceId: 'peer', expiresAt: null },
    ],
  }
  const a = AdmissionNotice({ market: true }),
    tree = a.type(a.props)
  const links = nodes(tree)
    .filter((node) => node.type === 'a')
    .map((node) => node.props.href)
  assert.deepEqual(links, ['/customer/booking/holds/hold', '/customer/transfers/acquisitions/peer'])
  user = { id: 'B', roles: ['CUSTOMER'] }
  data = status
  const b = AdmissionNotice({ market: true })
  assert.notEqual(a.key, b.key)
  assert.equal(b.type(b.props), null)
  user = { id: 'Staff', roles: ['STAFF'] }
  assert.equal(AdmissionNotice({}), null)
})
