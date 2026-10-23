// UI fixtures only: every /api/v1 request is intercepted in an isolated browser context.
// Actual ownership/grant/migration checks run in the Java PostgreSQL integration tests.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.SPORTHUB_E2E_PLAYWRIGHT || 'playwright')
const base = process.env.SPORTHUB_UI_URL || 'http://127.0.0.1:3001'
assert.match(new URL(base).hostname, /^(localhost|127\.0\.0\.1)$/)
const facilityId = '77000000-0000-0000-0000-000000000001'
const ownerId = '77000000-0000-0000-0000-000000000002'
const bindingId = '77000000-0000-0000-0000-000000000003'
const profileId = '77000000-0000-0000-0000-000000000004'
const permission = 'Hoàn tiền lượt đặt theo chính sách'
const facility = { id: facilityId, ownerId, name: 'UI fixture facility', status: 'ACTIVE', timezone: 'Asia/Ho_Chi_Minh', version: 0, amenities: [] }
const browser = await chromium.launch({ headless: true, ...(process.env.SPORTHUB_E2E_BROWSER_CHANNEL ? { channel: process.env.SPORTHUB_E2E_BROWSER_CHANNEL } : {}) })
const contexts = []
const errors = []

async function fixture({ role = 'OWNER', configured = false, conflict = false, foreign = false, legacyHold = false } = {}) {
  const context = await browser.newContext({ locale: 'vi-VN', timezoneId: 'America/Los_Angeles' })
  contexts.push(context)
  const page = await context.newPage()
  page.on('pageerror', error => errors.push(error.message))
  let policy = { configured, id: configured ? profileId : null, version: configured ? 1 : 0, rateBps: configured ? 8000 : null, currency: configured ? 'VND' : null, rounding: configured ? 'HALF_UP' : null, termsHash: configured ? 'a'.repeat(64) : null, createdAt: null }
  let fee = { configured, id: configured ? profileId : null, version: configured ? 1 : 0, feeBps: configured ? 500 : null, currency: configured ? 'VND' : null, rounding: configured ? 'HALF_UP' : null, termsHash: configured ? 'f'.repeat(64) : null, createdAt: null }
  let permissions = ['BOOKING_READ'], version = 0
  const mutations = [], policyReads = []
  const actor = { id: ownerId, roles: [role], fullName: 'UI fixture actor', email: 'fixture@example.invalid', status: 'ACTIVE', emailVerified: true, phoneVerified: false, phone: null }
  await context.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '')
    const method = request.method()
    const respond = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify({ status, message: 'Fixture response', data }) })
    if (path === '/auth/refresh') return respond({ accessToken: 'fixture-token', tokenType: 'Bearer', expiresIn: 300, user: actor })
    if (path === '/owner/facilities') return respond([facility])
    if (path === `/facilities/${facilityId}`) return respond(facility)
    if (path === `/bookings/holds/${bindingId}`) return respond({ id: bindingId, courtId: profileId, facilityId, holderId: ownerId, startsAt: new Date(Date.now()+3600000).toISOString(), endsAt: new Date(Date.now()+7200000).toISOString(), expiresAt: new Date(Date.now()+600000).toISOString(), state: 'HOLD', quote: { facilityId, courtId: profileId, timezone: facility.timezone, amount: 120000, currency: 'VND', segments: [], ...(legacyHold ? {} : { refundPolicy: { ...policy, configured: true, version: 1, rateBps: 8000 } }) } })
    if (path === `/owner/facilities/${facilityId}`) return respond(foreign ? null : facility, foreign ? 403 : 200)
    if (path === `/owner/facilities/${facilityId}/refund-policy`) {
      if (method === 'GET') { policyReads.push(path); return respond(policy) }
      const body = request.postDataJSON(); mutations.push({ path, body })
      if (conflict) {
        conflict = false
        policy = { ...policy, configured: true, version: 2, rateBps: 6000 }
        return respond(null, 409)
      }
      assert.equal(body.version, policy.version)
      policy = { ...policy, configured: true, version: policy.version + 1, rateBps: body.rateBps, currency: 'VND', rounding: 'HALF_UP', termsHash: 'a'.repeat(64) }
      return respond(policy)
    }
    if (path === `/owner/facilities/${facilityId}/transfer-policy`) return respond({ configured: true, enabled: true, minLeadSeconds: 3600 })
    if (path === `/owner/facilities/${facilityId}/transfer-fee-policy`) {
      if (method === 'GET') { policyReads.push(path); return respond(fee) }
      const body = request.postDataJSON(); mutations.push({ path, body })
      if (conflict) { conflict = false; fee = { ...fee, configured: true, version: 2, feeBps: 600 }; return respond(null, 409) }
      assert.equal(body.version, fee.version)
      assert.equal('rateBps' in body, false)
      fee = { ...fee, configured: true, version: fee.version + 1, feeBps: body.feeBps, currency: 'VND', rounding: 'HALF_UP' }
      return respond(fee)
    }
    if (path === `/staff-bindings/facilities/${facilityId}/permission-catalog`) return respond(['BOOKING_READ', 'BOOKING_REFUND', 'TRANSFER_PAYOUT'])
    if (path === `/staff-profiles/facilities/${facilityId}`) return respond([{ id: profileId, facilityId, fullName: 'Fixture Staff', contactEmail: null, contactPhone: null, active: true, linked: true, bindingId, userId: profileId, email: 'staff@example.invalid', permissions, version: 0 }])
    if (path === `/staff-bindings/facilities/${facilityId}/${bindingId}/permissions`) {
      if (method === 'PUT') {
        const body = request.postDataJSON(); mutations.push({ path, body })
        assert.equal(body.expectedVersion, version)
        permissions = body.permissions; version++
      }
      return respond({ bindingId, permissions, version })
    }
    if (method === 'GET') return respond([])
    throw new Error(`Unexpected fixture mutation: ${method} ${path}`)
  })
  return { page, mutations, policyReads }
}

try {
  const first = await fixture()
  await first.page.goto(`${base}/owner/facilities/${facilityId}/refund-policy`)
  await first.page.getByRole('heading', { name: 'Chính sách hoàn tiền', exact: true }).waitFor()
  assert.equal(await first.page.getByLabel('Tỷ lệ hoàn (%)').inputValue(), '')
  await first.page.getByLabel('Tỷ lệ hoàn (%)').fill('80.01')
  await first.page.getByRole('button', { name: 'Lưu chính sách hoàn tiền', exact: true }).click()
  await first.page.getByText('Tỷ lệ hiện tại:', { exact: false }).waitFor()
  assert.equal(first.mutations[0].body.rateBps, 8001)
  assert.equal(first.mutations[0].body.version, 0)
  await first.page.getByLabel('Tỷ lệ hoàn (%)').fill('100.01')
  await first.page.getByRole('button', { name: 'Lưu chính sách hoàn tiền', exact: true }).click()
  await first.page.getByRole('alert').filter({ hasText: 'không được vượt quá' }).waitFor()
  assert.equal(first.mutations.length, 1)
  console.log('PASS policy has no default; exact percentage input; out of range makes no request')

  const stale = await fixture({ configured: true, conflict: true })
  await stale.page.goto(`${base}/owner/facilities/${facilityId}/refund-policy`)
  await stale.page.getByLabel('Tỷ lệ hoàn (%)').fill('50')
  await stale.page.getByRole('button', { name: 'Lưu chính sách hoàn tiền', exact: true }).click()
  await stale.page.getByRole('button', { name: 'Tải chính sách mới', exact: true }).click()
  await stale.page.waitForFunction(() => document.querySelector('input[name=percent]')?.value === '60')
  await stale.page.getByLabel('Tỷ lệ hoàn (%)').fill('50')
  await Promise.all([
    stale.page.waitForResponse(response => response.url().endsWith('/refund-policy') && response.request().method() === 'PUT'),
    stale.page.getByRole('button', { name: 'Lưu chính sách hoàn tiền', exact: true }).click(),
  ])
  assert.deepEqual(stale.mutations.map(item => item.body.version), [1, 2])
  console.log('PASS policy version conflict requires reload before a new write')

  const staff = await fixture({ role: 'STAFF' })
  await staff.page.goto(`${base}/owner/facilities/${facilityId}/refund-policy`)
  await staff.page.getByRole('alert').filter({ hasText: 'không có quyền' }).waitFor()
  assert.equal(staff.policyReads.length, 0)
  const foreign = await fixture({ foreign: true })
  await foreign.page.goto(`${base}/owner/facilities/${facilityId}/refund-policy`)
  await foreign.page.getByText('Không thể tải thông tin cơ sở', { exact: true }).waitFor()
  assert.equal(foreign.policyReads.length, 0)
  console.log('PASS Staff and foreign facility cannot open the policy editor')

  const grants = await fixture()
  await grants.page.goto(`${base}/owner/facilities/${facilityId}/staff`)
  const boxes = grants.page.getByRole('checkbox', { name: permission, exact: true })
  await boxes.last().waitFor()
  for (const box of await boxes.all()) assert.equal(await box.isChecked(), false)
  await boxes.last().check()
  await Promise.all([
    grants.page.waitForResponse(response => response.url().endsWith('/permissions') && response.request().method() === 'PUT'),
    grants.page.getByRole('button', { name: 'Lưu quyền hạn', exact: true }).click(),
  ])
  await boxes.last().uncheck()
  await Promise.all([
    grants.page.waitForResponse(response => response.url().endsWith('/permissions') && response.request().method() === 'PUT'),
    grants.page.getByRole('button', { name: 'Lưu quyền hạn', exact: true }).click(),
  ])
  assert.deepEqual(grants.mutations.map(item => item.body.expectedVersion), [0, 1])
  assert.equal(grants.mutations[0].body.permissions.includes('BOOKING_REFUND'), true)
  assert.equal(grants.mutations[1].body.permissions.includes('BOOKING_REFUND'), false)
  console.log('PASS refund permission starts unchecked and Owner can grant/revoke explicitly')
  const fees = await fixture()
  await fees.page.goto(`${base}/owner/facilities/${facilityId}/transfer-policy`)
  await fees.page.getByRole('heading', { name: 'Phí chuyển nhượng của cơ sở', exact: true }).waitFor()
  assert.equal(await fees.page.getByLabel('Phí chuyển nhượng (%)').inputValue(), '')
  await fees.page.getByLabel('Phí chuyển nhượng (%)').fill('5.25')
  await fees.page.getByRole('button', { name: 'Lưu phí chuyển nhượng', exact: true }).click()
  await fees.page.getByText('Phí hiện tại:', { exact: false }).waitFor()
  assert.equal(fees.mutations[0].body.feeBps, 525)
  assert.equal(fees.mutations[0].path.endsWith('/transfer-fee-policy'), true)
  await fees.page.getByLabel('Phí chuyển nhượng (%)').fill('100.01')
  await fees.page.getByRole('button', { name: 'Lưu phí chuyển nhượng', exact: true }).click()
  await fees.page.getByRole('alert').filter({ hasText: 'không được vượt quá' }).waitFor()
  assert.equal(fees.mutations.length, 1)
  const feeConflict = await fixture({ configured: true, conflict: true })
  await feeConflict.page.goto(`${base}/owner/facilities/${facilityId}/transfer-policy`)
  await feeConflict.page.getByLabel('Phí chuyển nhượng (%)').fill('10')
  await feeConflict.page.getByRole('button', { name: 'Lưu phí chuyển nhượng', exact: true }).click()
  await feeConflict.page.getByRole('button', { name: 'Tải phí mới', exact: true }).click()
  await feeConflict.page.waitForFunction(() => document.querySelector('input[name=feePercent]')?.value === '6')
  for (const options of [{ role: 'STAFF' }, { foreign: true }]) {
    const denied = await fixture(options)
    await denied.page.goto(`${base}/owner/facilities/${facilityId}/transfer-policy`)
    if (options.role) await denied.page.getByRole('alert').filter({ hasText: 'không có quyền' }).waitFor()
    else await denied.page.getByText('Không thể tải thông tin cơ sở', { exact: true }).waitFor()
    assert.equal(denied.policyReads.length, 0)
  }
  const payoutGrant = await fixture()
  await payoutGrant.page.goto(`${base}/owner/facilities/${facilityId}/staff`)
  const payoutBoxes = payoutGrant.page.getByRole('checkbox', { name: 'Trả tiền cho người chuyển nhượng', exact: true })
  await payoutBoxes.last().waitFor()
  for (const box of await payoutBoxes.all()) assert.equal(await box.isChecked(), false)
  await payoutBoxes.last().check()
  await Promise.all([payoutGrant.page.waitForResponse(r => r.url().endsWith('/permissions') && r.request().method() === 'PUT'), payoutGrant.page.getByRole('button', { name: 'Lưu quyền hạn', exact: true }).click()])
  assert.equal(payoutGrant.mutations[0].body.permissions.includes('TRANSFER_PAYOUT'), true)
  assert.equal(payoutGrant.mutations[0].body.permissions.includes('BOOKING_REFUND'), false)
  await payoutBoxes.last().uncheck()
  await Promise.all([payoutGrant.page.waitForResponse(r => r.url().endsWith('/permissions') && r.request().method() === 'PUT'), payoutGrant.page.getByRole('button', { name: 'Lưu quyền hạn', exact: true }).click()])
  assert.equal(payoutGrant.mutations[1].body.permissions.includes('TRANSFER_PAYOUT'), false)
  console.log('PASS facility fee has no default, exact percentage/CAS, Owner scope and independent opt-in seller payout grant')
  for (const counter of [false, true]) {
    const hold = await fixture({ role: counter ? 'STAFF' : 'CUSTOMER', configured: true })
    await hold.page.goto(`${base}/customer/booking/holds/${bindingId}${counter ? '?counter=true' : ''}`)
    const notice = hold.page.getByRole('complementary', { name: 'Chính sách hoàn đã chốt' })
    await notice.waitFor(); assert.match(await notice.innerText(), /80%/)
    assert.equal(await hold.page.getByRole('button', { name: 'Xác nhận và thanh toán', exact: true }).isEnabled(), true)
  }
  const legacy = await fixture({ role: 'CUSTOMER', legacyHold: true })
  await legacy.page.goto(`${base}/customer/booking/holds/${bindingId}`)
  await legacy.page.getByText('Lượt đặt hoặc giữ chỗ cũ chưa có chính sách hoàn được chốt.', { exact: false }).waitFor()
  assert.equal(await legacy.page.getByRole('button', { name: 'Xác nhận và thanh toán', exact: true }).isEnabled(), false)
  console.log('PASS online/counter show frozen 80% before payment and legacy hold requires recreation')
  assert.deepEqual(errors, [])
} catch (failure) {
  for (const context of contexts) for (const page of context.pages()) {
    console.error(JSON.stringify({ url: page.url(), title: await page.title(), body: (await page.locator('body').innerText()).slice(0, 1000), errors }))
  }
  throw failure
} finally {
  for (const context of contexts) await context.close()
  await browser.close()
}
