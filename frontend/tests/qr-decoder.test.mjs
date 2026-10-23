import test from 'node:test'
import assert from 'node:assert/strict'
import { extractInviteCode, extractCheckinToken, extractBookingIdFromToken } from '../features/groups/qr-parser.ts'

test('extractInviteCode extracts code from various formats', () => {
  // 1. Full absolute URL
  assert.equal(
    extractInviteCode('http://localhost:3001/customer/groups/join?code=FIXTURE_CODE_1'),
    'FIXTURE_CODE_1'
  )

  // 2. Production URL
  assert.equal(
    extractInviteCode('https://sporthub.vn/customer/groups/join?code=SPORTHUB_INVITE_99'),
    'SPORTHUB_INVITE_99'
  )

  // 3. Relative path
  assert.equal(
    extractInviteCode('/customer/groups/join?code=PATH_CODE_123'),
    'PATH_CODE_123'
  )

  // 4. Multiple query params
  assert.equal(
    extractInviteCode('https://sporthub.vn/customer/groups/join?ref=qr&code=MULTI_PARAM_CODE&date=2026-10-19'),
    'MULTI_PARAM_CODE'
  )

  // 5. URL-encoded code
  assert.equal(
    extractInviteCode('https://sporthub.vn/customer/groups/join?code=hello%2Bworld%3D%3D'),
    'hello+world=='
  )

  // 6. Direct raw code string
  assert.equal(
    extractInviteCode('RAW_CODE_ABC_XYZ'),
    'RAW_CODE_ABC_XYZ'
  )

  // 7. Whitespace trimming
  assert.equal(
    extractInviteCode('   TRIMMED_CODE   '),
    'TRIMMED_CODE'
  )

  // 8. Empty input
  assert.equal(extractInviteCode(''), '')
})

test('extractCheckinToken extracts token from raw, URL, or JSON', () => {
  const token = 'ZXlKaGJHY2lPaUpJVXpVeE1pSXNJblI1Y0NJNklrcFhWQ0o5.sig123'

  // 1. Raw token
  assert.equal(extractCheckinToken(token), token)

  // 2. Full URL with ?token=...
  assert.equal(
    extractCheckinToken(`https://sporthub.vn/staff/bookings/check-in?token=${token}`),
    token
  )

  // 3. Relative URL
  assert.equal(
    extractCheckinToken(`/bookings/check-in?token=${token}&ref=app`),
    token
  )

  // 4. JSON payload
  assert.equal(
    extractCheckinToken(JSON.stringify({ token })),
    token
  )
  assert.equal(
    extractCheckinToken(JSON.stringify({ checkinToken: token })),
    token
  )

  // 5. Empty
  assert.equal(extractCheckinToken(''), '')
})

test('extractBookingIdFromToken extracts valid booking UUID', () => {
  const bookingId = '3f7ab15a-2add-4c34-a1c5-202bda2d65cc'
  const rawPayload = `${bookingId}|user_123|1|1730000000`
  const base64Url = Buffer.from(rawPayload).toString('base64url')
  const token = `${base64Url}.fake_hmac_signature`

  // 1. Raw token
  assert.equal(extractBookingIdFromToken(token), bookingId)

  // 2. URL containing token
  assert.equal(
    extractBookingIdFromToken(`https://sporthub.vn/staff/bookings/check-in?token=${token}`),
    bookingId
  )

  // 3. Invalid or non-booking token
  assert.equal(extractBookingIdFromToken('invalid.token'), null)
  assert.equal(extractBookingIdFromToken(''), null)
})

