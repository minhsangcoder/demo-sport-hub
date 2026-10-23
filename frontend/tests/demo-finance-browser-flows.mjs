// Runs through the production UI and genuine BFF/services owned by the system fixture.
import assert from 'node:assert/strict'
import path from 'node:path'

export async function browserBookingFlows(h) {
  const { actors, fid, courts, slot, call, loginPage, confirmed, pass, baseURL, linkedPhone } = h
  const time = value => new Date(value).toLocaleTimeString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit',
  })
  const day = new Date(new Date(slot.startsAt).getTime() + 7 * 3600000).toISOString().slice(0, 10)
  const staff = await loginPage('staff')

  async function choose(page, court, counter = false) {
    await page.goto(baseURL + (counter
      ? `/staff/counter-booking/${courts[court].id}?facilityId=${fid}`
      : `/facilities/${fid}/courts/${courts[court].id}`))
    await page.getByRole('heading', { name: 'Chọn giờ chơi', exact: true }).waitFor()
    await page.locator('input[type=date]').fill(day)
    await page.getByRole('button').filter({ hasText: `${time(slot.startsAt)}–${time(slot.endsAt)}` }).click()
    await page.getByRole('button', { name: 'Xem tóm tắt và giữ chỗ', exact: true }).click()
    await page.waitForURL('**/customer/booking/holds/**')
    await page.getByRole('heading', { name: 'Kiểm tra lượt đặt', exact: true }).waitFor()
  }

  async function createPayment(page, actor) {
    await page.getByRole('button', { name: 'Xác nhận và thanh toán', exact: true }).click()
    await page.waitForURL('**/customer/payments/**')
    await page.getByRole('button', { name: 'Thanh toán mô phỏng', exact: true }).waitFor()
    const paymentId = new URL(page.url()).pathname.split('/').at(-1)
    return call(actor, 'GET', `/payments/${paymentId}`)
  }

  async function checkIn(id, code) {
    await staff.goto(baseURL + `/staff/bookings/${id}`)
    await staff.getByRole('heading', { name: 'Chi tiết vận hành lượt đặt', exact: true }).waitFor()
    await staff.locator('input[name=token]').first().fill(code)
    const result = staff.waitForResponse(response => /\/check-in(?:-by-code)?$/.test(response.url()) && response.request().method() === 'POST')
    await staff.getByRole('button', { name: 'Xác nhận vào sân lượt đặt', exact: true }).click()
    const response = await result
    assert.equal(response.status(), 200, await response.text())
    await staff.getByRole('heading', { name: 'Đang chơi', exact: true }).waitFor()
  }

  const customer = await loginPage('a')
  await choose(customer, 'uiOnline')
  const onlinePayment = await createPayment(customer, 'a')
  const pending = await call('a', 'GET', `/bookings/${onlinePayment.bookingId}`)
  assert.equal(pending.booking.status, 'PENDING')
  assert.equal(Boolean(pending.checkinToken), false)
  await call('staff', 'POST', `/bookings/${pending.booking.id}/check-in`, { token: 'NO_PAYMENT' }, 409)
  await customer.getByRole('button', { name: 'Thanh toán mô phỏng', exact: true }).click()
  await customer.waitForURL(`**/customer/bookings/${pending.booking.id}`)
  await customer.getByRole('img', { name: 'QR xác nhận vào sân lượt đặt', exact: true }).waitFor()
  const online = await confirmed('a', pending.booking.id)
  assert.equal(online.booking.currentHolderId, actors.a.id)
  await checkIn(online.booking.id, online.checkinToken)
  pass('Production UI online: availability → hold → DEMO payment → Customer QR → Staff CHECKED_IN; unpaid has no QR or usage rights')

  // Reuse the same browser page so the actual AuthProvider/read generation changes A → B.
  await customer.locator('header').getByRole('button', { name: /Demo a/ }).click()
  await customer.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await customer.goto(baseURL + '/login')
  await customer.locator('input[name=identifier]').fill(actors.b.email)
  await customer.locator('input[name=password]').fill(h.password)
  await customer.getByRole('button', { name: 'Đăng nhập ngay', exact: true }).click()
  await customer.waitForURL(url => !url.pathname.startsWith('/login'))
  const forbidden = customer.waitForResponse(response => response.url().endsWith(`/api/v1/bookings/${online.booking.id}`) && response.request().method() === 'GET')
  await customer.goto(baseURL + `/customer/bookings/${online.booking.id}`)
  assert.equal((await forbidden).status(), 403)
  const denial = customer.locator('p[role=alert]')
  await denial.waitFor()
  assert.ok((await denial.innerText()).trim().length > 0)
  assert.equal(await customer.getByRole('img', { name: 'QR xác nhận vào sân lượt đặt', exact: true }).count(), 0)
  pass('Same browser logout A → login B clears private booking/QR and displays the server ownership denial')

  for (const [court, phone, owner] of [
    ['uiGuest', '+84977777777', null], ['uiLinked', linkedPhone, actors.a.id],
  ]) {
    await choose(staff, court, true)
    await staff.locator('input[name=guestName]').fill('Khách tại quầy ' + court)
    await staff.locator('input[name=guestPhone]').fill(phone)
    const payment = await createPayment(staff, 'staff')
    await call('staff', 'POST', `/bookings/${payment.bookingId}/check-in`, { token: 'NO_PAYMENT' }, 409)
    await staff.getByRole('button', { name: 'Thanh toán mô phỏng', exact: true }).click()
    await staff.waitForURL(`**/staff/bookings/${payment.bookingId}`)
    const result = await confirmed('staff', payment.bookingId)
    assert.equal(result.booking.currentHolderId, owner)
    assert.equal(result.booking.customerId, owner)
    assert.equal(result.booking.createdBy, actors.staff.id)
    if (owner) {
      const linked = await loginPage('a')
      await linked.goto(baseURL + `/customer/bookings/${payment.bookingId}`)
      await linked.getByRole('img', { name: 'QR xác nhận vào sân lượt đặt', exact: true }).waitFor()
    }
    const code = await staff.getByTestId('operational-booking-code').innerText()
    await checkIn(payment.bookingId, code)
    const personal = await call('staff', 'GET', '/bookings')
    assert.equal(personal.some(booking => booking.id === payment.bookingId), false)
  }
  pass('Production UI counter Guest and linked Customer: correct creator/nullable owner, full payment, operational code check-in and no Staff personal ownership')

  const organizer = await loginPage('a'), member = await loginPage('b')
  await choose(organizer, 'uiGroup')
  await organizer.getByLabel('Hình thức đặt sân').selectOption('GROUP')
  await organizer.locator('input[name=groupName]').fill('Nhóm kiểm chứng refactor')
  await organizer.locator('input[name=maxMembers]').fill('4')
  await organizer.getByRole('button', { name: 'Tạo lượt đặt nhóm', exact: true }).click()
  await organizer.waitForURL(/\/customer\/groups\/[0-9a-f-]+$/)
  const groupId = new URL(organizer.url()).pathname.split('/').at(-1)
  const invitation = await call('a', 'GET', `/groups/${groupId}/invite`)
  await member.goto(baseURL + `/customer/groups/join?code=${invitation.code}`)
  await member.getByRole('button', { name: 'Kiểm tra lời mời', exact: true }).click()
  await member.getByRole('button', { name: 'Xác nhận tham gia', exact: true }).click()
  await member.waitForURL('**/customer/groups/' + groupId)
  await organizer.goto(baseURL + `/customer/groups/${groupId}/split`)
  await organizer.getByRole('button', { name: 'Xác nhận phân chia và khóa danh sách', exact: true }).click()
  await organizer.waitForURL('**/customer/groups/' + groupId)

  for (const page of [organizer, member]) {
    await page.goto(baseURL + `/customer/groups/${groupId}`)
    await page.getByRole('button', { name: 'Đóng phần của tôi', exact: true }).click()
    await page.waitForURL('**/customer/payments/**')
    await page.getByRole('button', { name: 'Thanh toán mô phỏng', exact: true }).click()
    await page.waitForURL('**/customer/groups/' + groupId)
    if (page === organizer) {
      await call('staff', 'POST', `/bookings/${groupId}/check-in`, { token: 'NO_PAYMENT' }, 409)
      assert.equal(await page.getByRole('heading', { name: 'QR điểm danh của tôi', exact: true }).count(), 0)
    }
  }
  const groupBooking = await confirmed('a', groupId)
  await organizer.getByRole('heading', { name: 'QR điểm danh của tôi', exact: true }).waitFor()
  await organizer.getByLabel('Tin nhắn', { exact: true }).fill('Tin nhắn kiểm chứng refactor')
  await organizer.getByRole('button', { name: 'Gửi tin nhắn', exact: true }).click()
  await member.goto(baseURL + `/customer/groups/${groupId}`)
  await member.getByRole('list', { name: 'Tin nhắn nhóm' }).getByText('Tin nhắn kiểm chứng refactor', { exact: true }).waitFor()
  await checkIn(groupId, groupBooking.checkinToken)
  await organizer.screenshot({ path: path.join(h.output, 'refactor-ui-group.png'), fullPage: true })
  pass('Production UI group: invitation → equal split → two contributions → member QR/chat and Staff check-in only after full collection')
}
