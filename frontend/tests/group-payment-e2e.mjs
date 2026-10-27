import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { fixture } from '../../scripts/performance/ui-fixture.mjs'
import { facility, id } from '../../scripts/performance/ui-data.mjs'

const require = createRequire(import.meta.url)
const { chromium } = require(process.env.SPORTHUB_E2E_PLAYWRIGHT || 'playwright')
const baseURL = process.env.SPORTHUB_E2E_BASE_URL || 'http://localhost:3001'
const output = path.resolve(process.env.SPORTHUB_GROUP_E2E_OUTPUT || fileURLToPath(new URL('../../tmp/group-payment-browser', import.meta.url)))
mkdirSync(output, { recursive: true })
const owner = id(701), guest = id(702), groupId = id(703)
const deadline = new Date(Date.now() + 9 * 60000).toISOString()
const member = (userId, name, n) => ({ id: id(n), userId, displayName: name, active: true,
  amountDue: 0, amountPaid: 0, paymentState: 'UNPAID', paymentRequestedAt: null,
  lastReminderAt: null, nextReminderAt: null, reminderEligible: false, reminderBlockedReason: 'NO_ALLOCATION' })
const group = {
  id: groupId, ownerId: owner, name: 'Chia tiền sân', state: 'GROUP_PENDING', deadline,
  maxMembers: 4, allocationsLocked: false, totalPaid: 0, members: [member(owner, 'Người tạo nhóm', 704)],
  booking: { id: groupId, facilityId: facility.id, courtId: id(11), customerId: owner,
    currentHolderId: owner, createdBy: owner, amount: 120000, currency: 'VND', status: 'PENDING', source: 'ONLINE',
    startsAt: new Date(Date.now() + 3600000).toISOString(), endsAt: new Date(Date.now() + 7200000).toISOString(),
    priceSnapshot: { moneyClass: 'DEMO', timezone: 'Asia/Ho_Chi_Minh', segments: [], refundPolicy: { configured: true, rateBps: 9500 } } },
}
const calls = [], errors = []
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
try {
  async function pageFor(userId) {
    const context = await browser.newContext({ baseURL, viewport: { width: 1280, height: 1000 } })
    const fallback = fixture('CUSTOMER')
    await context.route('**/api/v1/**', async route => {
      const request = route.request(), uri = new URL(request.url()).pathname.replace('/api/v1', '')
      const method = request.method()
      const reply = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data }) })
      calls.push({ uri, method, userId })
      if (uri === '/auth/refresh') return reply({ accessToken: 'group-fixture', user: {
        id: userId, fullName: userId === owner ? 'Người tạo nhóm' : 'Bạn cùng chơi', roles: ['CUSTOMER'], status: 'ACTIVE', emailVerified: true,
      } })
      if (uri === `/groups/${groupId}` || uri === '/groups/invitations') return reply(group)
      if (uri === `/groups/${groupId}/invite`) return reply({ code: 'SIGNED-GROUP-INVITE', path: '/customer/groups/join?code=SIGNED-GROUP-INVITE', expiresAt: deadline })
      if (uri === '/groups/join') {
        assert.equal(request.postDataJSON().code, 'SIGNED-GROUP-INVITE')
        if (!group.members.some(m => m.userId === userId)) group.members.push(member(userId, 'Bạn cùng chơi', 705))
        return reply(group)
      }
      if (uri === `/groups/${groupId}/allocations`) {
        assert.equal(userId, owner)
        assert.deepEqual(request.postDataJSON(), { mode: 'EQUAL', allocations: null })
        group.allocationsLocked = true
        group.members.forEach(m => { m.amountDue = 60000; m.paymentState = 'WAITING_FOR_PAYMENT' })
        return reply(group)
      }
      if (uri === `/groups/${groupId}/play-group`) return reply(null)
      if (uri === '/payments/runtime') return reply({ mode: 'DEMO', demoEnabled: true, realEnabled: false })
      if (uri === '/bookings/demo-finance/mine') return reply({ mode: 'DEMO', settlements: [], refunds: [] })
      return fallback.route(route)
    })
    const page = await context.newPage()
    page.on('pageerror', error => errors.push(error.message))
    return page
  }
  const organizer = await pageFor(owner), participant = await pageFor(guest)
  await organizer.goto(`/customer/groups/${groupId}`)
  assert.equal(await organizer.getByLabel('Mã mời nhóm').inputValue(), 'SIGNED-GROUP-INVITE')
  await participant.goto('/customer/groups/join?code=SIGNED-GROUP-INVITE')
  await participant.getByRole('button', { name: 'Kiểm tra lời mời' }).click()
  await participant.getByRole('button', { name: 'Xác nhận tham gia', exact: true }).click()
  await participant.waitForURL(`**/customer/groups/${groupId}`)
  await organizer.getByText('Hiện có 2/4 thành viên.', { exact: false }).waitFor({ timeout: 15000 })
  assert.equal(calls.filter(c => c.uri.endsWith('/allocations')).length, 0)
  await organizer.screenshot({ path: path.join(output, 'invite-and-members.png'), fullPage: true })
  await organizer.getByRole('button', { name: 'Chia đều và bắt đầu thanh toán' }).click()
  await organizer.getByText('Danh sách đã chốt; mỗi người thanh toán phần được phân bổ.').waitFor()
  await participant.getByRole('button', { name: 'Đóng phần của tôi' }).waitFor({ timeout: 15000 })
  assert.equal(await organizer.getByLabel('Mã mời nhóm').count(), 0)
  assert.equal(await participant.getByLabel('Mã mời nhóm').count(), 0)
  assert.deepEqual(group.members.map(m => m.amountDue), [60000, 60000])
  for (const page of [organizer, participant]) {
    assert.equal(await page.getByRole('button', { name: 'Xem khoản hoàn khi hủy' }).count(), 0)
    assert.equal(await page.getByText('Chính sách riêng dành cho người mua lại lượt đặt').count(), 0)
  }
  await participant.screenshot({ path: path.join(output, 'split-payment.png'), fullPage: true })
  assert.deepEqual(errors, [])
  writeFileSync(path.join(output, 'result.json'), JSON.stringify({ productionUI: true, fixtureAPI: true, realMoney: false,
    checks: ['inline invite', 'join from second account', 'owner polling', 'manual split only', '60000 per member', 'invite closed', 'obsolete refund removed'], errors }, null, 2))
  console.log('Group payment browser flow passed; API fixtures, no real data changed.')
} finally { await browser.close() }
