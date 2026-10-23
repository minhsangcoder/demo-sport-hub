import { responseFor, facility, id } from './ui-data.mjs'

export function fixture(role = 'PUBLIC') {
  const user = { id: id(role === 'OWNER' ? 2 : 3), fullName: 'Tài khoản thử', roles: [role], email: 'ui@example.invalid', phone: null, status: 'ACTIVE', emailVerified: true, phoneVerified: false }
  const facilities = [facility, { ...facility, id: id(100), name: 'Cơ sở thứ hai' }]
  const permissions = ['BOOKING_READ', 'BOOKING_CREATE_COUNTER', 'BOOKING_CHECK_IN', 'BOOKING_REFUND', 'TRANSFER_PAYOUT', 'SCHEDULE_READ', 'SCHEDULE_MANAGE', 'COURT_MAINTENANCE_MANAGE', 'COURT_STATUS_MANAGE']
  const profile = { id: id(200), facilityId: facility.id, fullName: 'Nhân viên thử', email: 'staff@example.invalid', active: true, linked: true, bindingId: id(201), permissions: [...permissions], version: 3 }
  const courts = [1, 2].map(n => ({ id: id(10 + n), facilityId: facility.id, code: 'S' + n, name: 'Sân đo ' + n, sportCategoryId: id(4), enabled: true, version: 0 }))
  const calls = [], unknown = []
  const account = { ...user, id: id(390), fullName: 'Tài khoản cần quản lý', roles: ['CUSTOMER'], createdAt: '2026-10-01T01:00:00Z' }
  const refund = { id: id(391), payment_id: id(392), requester_id: id(390), beneficiary_id: id(390), state: 'PENDING', reason: 'Kiểm chứng ghi chú', reviews: [] }
  let failPermission = false, reportAvailability = 'COMPLETE', bookings = [], media = [], checkinGate
  async function route(route) {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', ''), method = request.method()
    let body
    try { body = request.postDataJSON() } catch { /* Multipart uploads are exercised without parsing file content. */ }
    calls.push({ path: path + url.search, method, body })
    let data, status = 200, message = 'Dữ liệu thử', code
    if (path === '/auth/refresh') { status = role === 'PUBLIC' ? 401 : 200; data = { accessToken: 'synthetic-ui', user }; code = status === 401 ? 'UNAUTHORIZED' : undefined }
    else if (path === '/auth/login') data = { accessToken: 'synthetic-ui', user: { ...user, roles: ['CUSTOMER'] } }
    else if (path === '/auth/register') { status = 201; data = { verificationChallengeId: 'synthetic-challenge', accountStatus: 'PENDING_VERIFICATION' } }
    else if (['/auth/verify', '/auth/forgot-password', '/auth/reset-password', '/auth/logout'].includes(path)) data = {}
    else if (path === '/users/me') { if (method === 'PATCH') Object.assign(user, body); data = user }
    else if (path === '/users/me/access') data = { facilityBindings: facilities.map(f => ({ facilityId: f.id, permissions })) }
    else if (path === '/users/me/contact-challenges') data = []
    else if (path === '/facilities/favorites') data = { items: [], total: 0, page: 0, size: 20 }
    else if (path === '/owner/facilities' || path === '/facilities') data = facilities
    else if (/^\/(?:owner\/)?facilities\/[^/]+(?:\/operations\/access)?$/.test(path)) data = facilities.find(f => path.includes(f.id)) ?? facility
    else if (path.endsWith('/permission-catalog')) data = permissions
    else if (/^\/staff-profiles\/facilities\/[^/]+$/.test(path)) data = [profile]
    else if (path.endsWith('/permissions')) {
      if (method === 'PUT') {
        if (failPermission) { status = 403; message = 'Bạn không có quyền thực hiện thao tác này.'; code = 'FORBIDDEN' }
        else profile.permissions = body.permissions
      }
      data = { version: 7, permissions: profile.permissions }
    }
    else if (path.endsWith('/accounts') && path.startsWith('/staff-profiles')) { status = 201; data = { ...profile, ...body } }
    else if (path.endsWith('/password')) data = {}
    else if (path.endsWith('/courts')) data = courts
    else if (path.endsWith('/court-types')) data = []
    else if (path.endsWith('/images')) data = media
    else if (path === '/bookings/management/search') data = { items: bookings, total: bookings.length, totalPages: bookings.length ? 1 : 0 }
    else if (method === 'POST' && (path.endsWith('/check-in-by-code') || path.endsWith('/check-in'))) {
      if (checkinGate) await checkinGate
      data = bookings.find(b => path.includes(b.id) || body.bookingCode === b.bookingCode) ?? { id: id(80) }
      data.status = 'CHECKED_IN'
    }
    else if (path.endsWith('/hours')) data = []
    else if (path.endsWith('/exceptions') || path.endsWith('/pricing') || path.endsWith('/maintenance')) data = []
    else if (path === `/admin/accounts/${account.id}/roles`) { account.roles = body.roles; data = {} }
    else if (path === `/admin/accounts/${account.id}`) data = { account, sessions: [], audit: [] }
    else if (path.startsWith('/admin/accounts')) data = [account]
    else if (path === `/admin/payments/refunds/${refund.id}/reviews`) { refund.reviews.push({ id: id(393), note: body.note, created_at: '2026-10-23T01:00:00Z' }); data = {} }
    else if (path === `/admin/payments/refunds/${refund.id}`) data = refund
    else if (path.startsWith('/bookings/reports') && path.endsWith('/dashboard')) {
      data = responseFor(url, 'OWNER').data; data.moneyAvailability = reportAvailability
      const rows = Array.from({ length: 25 }, (_, n) => ({ id: 'FAILED:' + n, bookingId: id(80), courtName: 'Sân đo 1', source: 'ONLINE', kind: 'POLICY_REFUND', cashflow: true, amount: '80000', direction: 'OUT', state: 'FAILED', effectiveAt: '2026-10-23T01:00:00Z' }))
      rows.push({ ...rows[0], id: 'COURT:one', kind: 'COURT_COLLECTION', amount: '120000', direction: 'IN', state: 'SUCCESS' }, { ...rows[0], id: 'FEE:one', kind: 'FACILITY_TRANSFER_FEE', cashflow: false, amount: '5000', direction: 'RECOGNITION', state: 'SUCCESS' })
      const page = Number(url.searchParams.get('page') ?? 0)
      data.transactions = { page, size: 25, total: rows.length, totalPages: 2, items: rows.slice(page * 25, (page + 1) * 25) }
    }
    else {
      try { ({ data, status = 200 } = responseFor(url, role)) }
      catch { unknown.push(path); status = 404; code = 'NOT_FOUND'; message = 'Không có dữ liệu thử cho đường dẫn này.'; data = null }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ data, message, code }) })
  }
  return { user, facilities, permissions, profile, courts, calls, unknown, route, set failPermission(value) { failPermission = value }, set reportAvailability(value) { reportAvailability = value }, set bookings(value) { bookings = value }, set media(value) { media = value }, set checkinGate(value) { checkinGate = value } }
}
