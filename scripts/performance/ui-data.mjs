export const id = n => `79000000-0000-0000-0000-${String(n).padStart(12, '0')}`
export const facility = { id: id(1), ownerId: id(2), name: 'Cơ sở đo hiệu năng', status: 'ACTIVE', timezone: 'Asia/Ho_Chi_Minh', version: 0, amenities: [], addressLine: 'Dữ liệu thử tổng hợp', province: 'Hà Nội' }
const money = { courtCollected: '120000', transferFees: '5000', operatingRevenue: '125000', policyRefunded: '0', afterRefund: '125000', transferCollected: '100000', sellerPaid: '0', sellerDue: '95000', refundDue: '0', technicalRefunded: '0' }
const summary = { bookingCount: 2, fullyPaidBookingCount: 2, bookedMinutes: '120', usedMinutes: '60', cancelledCount: 0, expiredCount: 0, money }
export function responseFor(url, role) {
  const playDate = url.searchParams.get('date') || new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' })
  const path = url.pathname.replace('/api/v1', '')
  const user = { id: id(role === 'OWNER' ? 2 : 3), roles: [role], fullName: `Tài khoản thử ${role}`, email: 'performance@example.invalid', status: 'ACTIVE', emailVerified: true, phoneVerified: false }
  if (path === '/auth/refresh') return role === 'PUBLIC' ? { status: 401, data: null } : { data: { accessToken: 'synthetic-fixture-only', tokenType: 'Bearer', expiresIn: 300, user } }
  if (path === '/sport-categories') return { data: [{ id: id(4), name: 'Cầu lông', active: true }] }
  if (path === '/bookings/search') return { data: { items: [1, 2, 3].map(n => ({ facility, courtId: id(10 + n), courtName: `Sân đo ${n}`, sportCategoryId: id(4), sportName: 'Cầu lông', date: playDate, fromPrice: 120000, currency: 'VND', availableSlots: 8, firstStartsAt: `${playDate}T08:00:00Z`, firstEndsAt: `${playDate}T09:00:00Z`, distanceKm: null })), truncated: false } }
  if (path === '/owner/facilities' || path === '/facilities') return { data: [facility] }
  if (path === `/owner/facilities/${facility.id}` || path === `/facilities/${facility.id}`) return { data: facility }
  if (path === '/users/me/access') return { data: { facilityBindings: [{ facilityId: facility.id, permissions: ['BOOKING_READ', 'BOOKING_CHECK_IN', 'BOOKING_REFUND', 'TRANSFER_PAYOUT'] }] } }
  if (path === '/bookings/management/search') return { data: { items: [], total: 0, totalPages: 0 } }
  if (path.startsWith('/bookings/demo-finance/')) return { data: { mode: 'DEMO', settlements: [], refunds: [], canPaySeller: true, canRefund: true } }
  if (path.includes('/dashboard')) {
    const fromDate = url.searchParams.get('fromDate'), toDate = url.searchParams.get('toDate')
    return { data: { facilityId: facility.id, mode: 'DEMO', label: 'DEMO', asOf: '2026-10-22T10:00:00Z', obligationCutoff: '2026-10-22T10:00:00Z', window: { fromDate, toDate, from: '2026-10-15T00:00:00Z', to: '2026-10-23T00:00:00Z', timezone: facility.timezone, interval: '[from,to)' }, moneyAvailability: 'COMPLETE', reasons: [], summary, daily: [{ date: toDate, ...money }], courts: [{ courtId: id(11), courtName: 'Sân đo 1', ...summary }], transactions: { page: 0, size: 25, total: 0, totalPages: 0, items: [] } } }
  }
  if (path.endsWith('/images')) return { data: [] }
  throw new Error(`Unspecified fixture API: ${path}`)
}
