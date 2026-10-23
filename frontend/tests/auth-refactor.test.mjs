import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import ts from 'typescript'
import { moduleLoader, hookDriver } from './support/load-module.mjs'

const administrative = JSON.parse(readFileSync(new URL('../features/facility/vietnam-administrative-divisions.json', import.meta.url), 'utf8'))
const values = {
  identifier: ' guest@example.invalid ', fullName: ' Khách thử ', password: '  unchanged password  ',
  representativeName: ' Đại diện ', identityNumber: 'DEMO-ONLY', businessName: 'Cơ sở thử', businessLicense: '', taxCode: '',
  bankName: 'Ngân hàng thử', bankAccountHolder: 'Người thử', bankAccountNumber: 'DEMO-ACCOUNT',
  facilityName: ' Cơ sở thử ', phone: '0900000000', contactEmail: '', addressLine: ' Địa chỉ tổng hợp ',
  provinceCode: '01', wardCode: '00004', province: 'Tên cũ', ward: 'Tên cũ', amenities: ' đèn, , nước ',
  courtCode: 'A1', courtName: 'Sân thử', sportCategoryId: 'synthetic-category', opensAt: '06:00', closesAt: '22:00', slotMinutes: '60', pricePerSlot: '120000',
}
function form() {
  const data = new FormData()
  for (const [name, value] of Object.entries(values)) data.set(name, value)
  data.append('days', '1'); data.append('days', '7')
  for (const name of ['identityDocument', 'locationDocument', 'facilityImage']) data.set(name, new File(['synthetic'], name + '.png', { type: 'image/png', lastModified: 123 }))
  return data
}
const plain = value => JSON.parse(JSON.stringify(value))

test('pure Customer/Owner payloads preserve contact choice, nullable fields, location, files and retry fingerprint', async () => {
  const load = moduleLoader(), data = form()
  const account = load('features/auth/account-payload.ts').accountFrom(data)
  assert.deepEqual(plain(account), { fullName: 'Khách thử', password: values.password, email: 'guest@example.invalid' })
  const build = load('features/auth/owner-signup-payload.ts').ownerSignupFrom
  const payload = build(data, { latitude: 21, longitude: 105 })
  assert.equal(payload.body.ownerApplication.legal.taxCode, null)
  assert.equal(payload.body.ownerApplication.legal.businessLicense, null)
  assert.equal(payload.body.ownerApplication.facility.province, 'Hà Nội')
  assert.equal(payload.body.ownerApplication.facility.ward, 'Ba Đình')
  assert.equal(payload.body.ownerApplication.facility.contactEmail, null)
  assert.equal(payload.body.ownerApplication.facility.timezone, 'Asia/Ho_Chi_Minh')
  assert.deepEqual(plain(payload.body.ownerApplication.setup.days), [1, 7])
  assert.deepEqual(plain(payload.body.ownerApplication.facility.amenities), ['đèn', 'nước'])
  assert.deepEqual(JSON.parse(await payload.multipart.get('request').text()), plain(payload.body))
  for (const name of ['identityDocument', 'locationDocument', 'facilityImage']) assert.equal(payload.multipart.get(name).name, data.get(name).name)
  assert.equal(build(data, { latitude: 21, longitude: 105 }).fingerprint, payload.fingerprint)
  data.set('pricePerSlot', '120001')
  assert.notEqual(build(data, { latitude: 21, longitude: 105 }).fingerprint, payload.fingerprint)
  data.set('identifier', ' 0900000000 ')
  assert.equal(load('features/auth/account-payload.ts').accountFrom(data).phone, '0900000000')
  data.set('facilityImage', new File(['x'], 'invalid.pdf', { type: 'application/pdf' }))
  assert.throws(() => build(data, { latitude: 21, longitude: 105 }), error => error.field === 'facilityImage')
})

test('light province data matches every source code/name; full ward lookup remains in address forms', () => {
  const load = moduleLoader(), lightweight = load('features/facility/provinces.data.ts'), full = load('features/facility/administrative-divisions.ts')
  assert.deepEqual(plain(lightweight.provinces), administrative.provinces.map(({ code, name }) => ({ code, name })))
  assert.equal(lightweight.attribution, administrative.attribution)
  for (const province of administrative.provinces) {
    assert.equal(full.provinceByCode(province.code).name, province.name)
    assert.equal(full.provinceByName(province.name).code, province.code)
    for (const ward of province.wards) assert.equal(full.wardByCode(province.code, ward.code).name, ward.name)
  }
  assert.equal(full.provinceByName('Thành phố Hà Nội').code, '01')
  assert.equal(full.wardByName('01', 'Phường Ba Đình').code, '00004')
  const legacy = form(); legacy.set('provinceCode', 'legacy'); legacy.set('wardCode', 'legacy')
  const payload = load('features/facility/facility-payload.ts').facilityFrom(legacy, null)
  assert.equal(payload.province, 'Tên cũ'); assert.equal(payload.ward, 'Tên cũ')
})

function graph(entry) {
  const root = fileURLToPath(new URL('../', import.meta.url)), visited = new Set()
  function visit(file) {
    if (visited.has(file)) return
    visited.add(file)
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true)
    for (const node of source.statements.filter(ts.isImportDeclaration)) {
      if (node.importClause?.isTypeOnly) continue
      const specifier = node.moduleSpecifier.text
      if (!specifier.startsWith('.') && !specifier.startsWith('@/')) continue
      let target = specifier.startsWith('@/') ? path.join(root, specifier.slice(2)) : path.resolve(path.dirname(file), specifier)
      if (!existsSync(target)) target += existsSync(target + '.ts') ? '.ts' : '.tsx'
      visit(target)
    }
  }
  visit(path.join(root, entry))
  return [...visited].map(file => path.relative(root, file).replaceAll('\\', '/'))
}
test('static login and public discovery graphs exclude Owner location UI and full ward data', () => {
  const login = graph('features/auth/auth-form.tsx')
  assert.ok(!login.some(file => /owner-signup|owner-registration|location-picker|vietnam-administrative/.test(file)))
  assert.ok(login.includes('features/auth/account-payload.ts'))
  for (const entry of ['app/page.tsx', 'features/discovery/search-page.tsx']) {
    assert.ok(!graph(entry).some(file => file.includes('vietnam-administrative-divisions.json')))
  }
  assert.ok(graph('features/auth/owner-signup-payload.ts').every(file => !file.endsWith('.tsx')))
})

class SubmittedForm extends FormData {
  constructor(input) { super(); if (input) for (const [key, value] of input) this.append(key, value) }
}
function controller(mode, query = '') {
  const driver = hookDriver(), calls = [], redirects = [], listeners = new Map()
  const authApi = Object.fromEntries(['login', 'register', 'registerOwner', 'verify', 'forgot', 'reset'].map(method => [method, async (...args) => {
    calls.push({ method, args })
    if (method === 'login') return { user: { roles: ['STAFF'] } }
    return { verificationChallengeId: 'synthetic-challenge', accountStatus: 'PENDING' }
  }]))
  const load = moduleLoader({ FormData: SubmittedForm, crypto: globalThis.crypto, window: { location: { pathname: '/' + mode, search: query }, history: { pushState() {} }, addEventListener: (name, action) => listeners.set(name, action), removeEventListener: name => listeners.delete(name) } }, {
    react: driver.react,
    'next/navigation': { useRouter: () => ({ push: target => redirects.push(target) }), useSearchParams: () => new URLSearchParams(query) },
    '@/lib/api': { authApi, ApiError: class extends Error {} },
  })
  const useForm = load('features/auth/use-auth-form.ts').useAuthForm
  return { calls, redirects, driver, render: () => driver.render(useForm, { mode }) }
}
const submit = async (c, data = form()) => c.render().submit({ preventDefault() {}, currentTarget: data })
test('the moved auth controller keeps login/register/verify/recovery requests and Staff continuation', async () => {
  const login = controller('login'); await submit(login); assert.equal(login.redirects[0], '/staff/bookings')
  const register = controller('register'); await submit(register); assert.equal(register.calls[0].method, 'register'); assert.equal(register.redirects[0], '/verify?challengeId=synthetic-challenge')
  const verify = controller('verify', 'token=signed-token'); await submit(verify); assert.deepEqual(plain(verify.calls[0].args[0]), { verificationToken: 'signed-token' }); assert.match(verify.render().success, /thành công/)
  const forgot = controller('forgot'); await submit(forgot); assert.equal(forgot.calls[0].method, 'forgot')
  const reset = controller('reset'); await submit(reset); assert.equal(reset.calls[0].method, 'reset'); assert.equal(reset.calls[0].args[0].newPassword, values.password)
  for (const c of [login, register, verify, forgot, reset]) c.driver.unmount()
})
test('Owner registration uses the moved builder and preserves an idempotency key for an unchanged retry', async () => {
  const owner = controller('register'); owner.render().setApplyAsOwner(true); owner.render().setLocation({ latitude: 21, longitude: 105 })
  await submit(owner); await submit(owner)
  assert.equal(owner.calls[0].method, 'registerOwner')
  assert.equal(owner.calls[0].args[1], owner.calls[1].args[1])
  const payload = JSON.parse(await owner.calls[0].args[0].get('request').text())
  assert.equal(payload.applyAsOwner, true); assert.equal(payload.ownerApplication.facility.latitude, 21)
  owner.driver.unmount()
})
